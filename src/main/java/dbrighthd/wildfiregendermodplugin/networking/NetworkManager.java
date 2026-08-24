package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV6;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Paper-side V6 synchronisation.
 *
 * <p>Each config change produces exactly one standard V6 clientbound profile
 * packet for each player currently tracking that entity. The source client is
 * never sent its own profile: the official mod explicitly ignores it because
 * it already owns the authoritative local configuration.</p>
 */
public final class NetworkManager {
    private final GenderModPlugin plugin;
    private final ModSyncPacketV6 codec = new ModSyncPacketV6();
    private final ConcurrentMap<UUID, Integer> negotiatedVersions = new ConcurrentHashMap<>();
    private final Set<UUID> waitingForJoinSync = ConcurrentHashMap.newKeySet();
    /** Players whose local client has supplied a newer authoritative profile this session. */
    private final Set<UUID> locallyOwnedProfiles = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, byte[]> encodedProfiles = new ConcurrentHashMap<>();
    private volatile byte[] velocitySharedSecret = new byte[0];

    public NetworkManager(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    /** Loads the optional shared secret used to authenticate Velocity bridge traffic. */
    public void enable() {
        String secret = plugin.getConfig().getString("velocity.shared-secret", "").trim();
        velocitySharedSecret = secret.isEmpty() ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (velocitySharedSecret.length == 0) {
            plugin.getCustomLogger().info("Velocity bridge disabled: velocity.shared-secret is blank");
        }
    }

    /** Handles the V6 hello packet during Paper's configuration stage. */
    public void handleHello(UUID playerId, PluginMessageRecipient connection, byte[] message) {
        if (message.length > 5) {
            return;
        }

        try (CraftInputStream input = CraftInputStream.ofBytes(message);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            int clientVersion = input.readVarInt();
            if (clientVersion == ModConstants.SYNC_PROTOCOL_VERSION) {
                negotiatedVersions.put(playerId, clientVersion);
            } else {
                negotiatedVersions.remove(playerId);
            }

            output.writeVarInt(ModConstants.SYNC_PROTOCOL_VERSION);
            connection.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_HELLO, bytes.toByteArray());
        } catch (IOException ignored) {
            return;
        }

        // If a join event happened first, finish its one-shot initial sync now.
        Player player = findOnline(playerId);
        if (player != null && isV6(playerId) && waitingForJoinSync.remove(playerId)) {
            scheduleInitialSync(player);
        }
    }

    /** Called once from PlayerJoinEvent; no recurring task is created. */
    public void onPlayerJoined(Player player) {
        if (isV6(player.getUniqueId())) {
            scheduleInitialSync(player);
        } else {
            waitingForJoinSync.add(player.getUniqueId());
        }
    }

    /**
     * Sends the tracked entity's one normal V6 profile packet to the tracking
     * player. This mirrors Fabric's EntityTrackingEvents.START_TRACKING path.
     */
    public void onStartTracking(Player recipient, Player tracked) {
        sendProfile(recipient, tracked.getUniqueId());
    }

    /** Decodes, deduplicates, stores and relays a client-originated profile update. */
    public void onClientSync(Player sender, byte[] data) {
        if (!isV6(sender.getUniqueId()) || data.length > ModConstants.MAX_SYNC_PAYLOAD_BYTES) {
            return;
        }

        ModUser user = deserializeServerbound(data, sender.getUniqueId());
        if (user == null || !sender.getUniqueId().equals(user.userId())) {
            return;
        }

        locallyOwnedProfiles.add(user.userId());

        byte[] clientbound;
        try {
            clientbound = codec.writeClientbound(user);
        } catch (IOException ignored) {
            return;
        }

        byte[] previous = encodedProfiles.put(user.userId(), clientbound);
        plugin.getUserManager().getUsers().put(user.userId(), user);
        if (Arrays.equals(previous, clientbound)) {
            return; // The mod sent the same state again; no network work needed.
        }

        forwardToVelocity(sender, clientbound);
        broadcastToTrackers(sender, clientbound);
    }

    /** Handles a message that originated from the dedicated Velocity bridge. */
    public void handleProxyMessage(byte[] message) {
        byte[] verified = verifyVelocityMessage(message);
        if (verified == null || verified.length < 2 || verified[0] != ModConstants.PROXY_PROFILE_SYNC) {
            return;
        }

        int payloadLength = verified.length - 1;
        if (payloadLength > ModConstants.MAX_SYNC_PAYLOAD_BYTES) {
            return;
        }

        byte[] profile = Arrays.copyOfRange(verified, 1, verified.length);
        ModUser user;
        try {
            user = codec.readClientbound(profile);
        } catch (IOException ignored) {
            return;
        }

        // A delayed Velocity response must not overwrite a profile the local client
        // has already sent after joining this backend.
        if (locallyOwnedProfiles.contains(user.userId())) {
            return;
        }

        byte[] previous = encodedProfiles.put(user.userId(), profile);
        plugin.getUserManager().getUsers().put(user.userId(), user);
        if (Arrays.equals(previous, profile)) {
            return;
        }

        // A remote profile matters only when its owner is actually on this backend.
        Player source = findOnline(user.userId());
        if (source != null) {
            broadcastToTrackers(source, profile);
        }
    }

    /** Requests the joining player's cached profile from Velocity. */
    public void requestProfileFromVelocity(Player player) {
        if (!isV6(player.getUniqueId()) || !isVelocityBridgeEnabled()) {
            return;
        }

        byte[] request = new byte[17];
        request[0] = ModConstants.PROXY_PROFILE_REQUEST;
        writeUuid(request, 1, player.getUniqueId());
        player.sendPluginMessage(plugin, ModConstants.PROXY_CHANNEL, signVelocityMessage(request));
    }

    /** Cleans all Paper-side state for a disconnected player. */
    public void removePlayer(UUID userId) {
        negotiatedVersions.remove(userId);
        waitingForJoinSync.remove(userId);
        locallyOwnedProfiles.remove(userId);
        encodedProfiles.remove(userId);
        plugin.getUserManager().getUsers().remove(userId);
    }

    public boolean isV6(UUID userId) {
        return ModConstants.SYNC_PROTOCOL_VERSION == negotiatedVersions.getOrDefault(userId, -1);
    }

    private void scheduleInitialSync(Player player) {
        // One one-shot task ensures the client is in play phase and has registered
        // its clientbound sync receiver. This is not polling or per-tick work.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !isV6(player.getUniqueId())) {
                return;
            }
            sendInitialTrackedProfiles(player);
            requestProfileFromVelocity(player);
        });
    }

    /** Sends one standard packet for each currently tracked *other* player. */
    private void sendInitialTrackedProfiles(Player recipient) {
        for (Player source : plugin.getServer().getOnlinePlayers()) {
            if (!source.equals(recipient) && source.getTrackedBy().contains(recipient)) {
                sendProfile(recipient, source.getUniqueId());
            }
        }
    }

    private void broadcastToTrackers(Player source, byte[] clientboundProfile) {
        for (Player recipient : source.getTrackedBy()) {
            if (!recipient.equals(source) && isV6(recipient.getUniqueId())) {
                recipient.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_SYNC, clientboundProfile);
            }
        }
    }

    private void sendProfile(Player recipient, UUID sourceId) {
        if (recipient.getUniqueId().equals(sourceId) || !isV6(recipient.getUniqueId())) {
            return;
        }

        byte[] payload = encodedProfiles.get(sourceId);
        if (payload == null) {
            ModUser user = plugin.getUserManager().getUsers().get(sourceId);
            if (user == null) {
                return;
            }
            try {
                payload = codec.writeClientbound(user);
                byte[] existing = encodedProfiles.putIfAbsent(sourceId, payload);
                if (existing != null) {
                    payload = existing;
                }
            } catch (IOException ignored) {
                return;
            }
        }
        recipient.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_SYNC, payload);
    }

    private void forwardToVelocity(Player carrier, byte[] clientboundProfile) {
        if (!isVelocityBridgeEnabled()) {
            return;
        }
        byte[] message = new byte[clientboundProfile.length + 1];
        message[0] = ModConstants.PROXY_PROFILE_SYNC;
        System.arraycopy(clientboundProfile, 0, message, 1, clientboundProfile.length);
        carrier.sendPluginMessage(plugin, ModConstants.PROXY_CHANNEL, signVelocityMessage(message));
    }

    private boolean isVelocityBridgeEnabled() {
        return velocitySharedSecret.length > 0;
    }

    private byte[] signVelocityMessage(byte[] unsignedMessage) {
        byte[] signature = hmac(unsignedMessage);
        byte[] signed = Arrays.copyOf(unsignedMessage, unsignedMessage.length + signature.length);
        System.arraycopy(signature, 0, signed, unsignedMessage.length, signature.length);
        return signed;
    }

    private byte[] verifyVelocityMessage(byte[] signedMessage) {
        final int signatureLength = 32;
        if (!isVelocityBridgeEnabled() || signedMessage.length <= signatureLength
                || signedMessage.length > 1 + ModConstants.MAX_SYNC_PAYLOAD_BYTES + signatureLength) {
            return null;
        }

        int unsignedLength = signedMessage.length - signatureLength;
        byte[] unsigned = Arrays.copyOf(signedMessage, unsignedLength);
        byte[] actualSignature = Arrays.copyOfRange(signedMessage, unsignedLength, signedMessage.length);
        return MessageDigest.isEqual(hmac(unsigned), actualSignature) ? unsigned : null;
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(velocitySharedSecret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private ModUser deserializeServerbound(byte[] data, UUID senderId) {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            return codec.read(input, senderId);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Player findOnline(UUID userId) {
        return plugin.getServer().getPlayer(userId);
    }

    private static void writeUuid(byte[] output, int offset, UUID userId) {
        long most = userId.getMostSignificantBits();
        long least = userId.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            output[offset + i] = (byte) (most >>> (56 - i * 8));
            output[offset + 8 + i] = (byte) (least >>> (56 - i * 8));
        }
    }
}
