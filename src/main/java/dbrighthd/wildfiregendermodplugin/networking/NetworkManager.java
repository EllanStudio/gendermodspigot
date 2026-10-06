package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.Beta4SyncPacketCodec;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.Protocol2SyncPacketCodec;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import org.bukkit.entity.Player;

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
 * Paper-side synchronization for both official Female Gender Mod wire formats.
 *
 * <p>Protocol 1 is the MC 26.2 Beta.4 play-phase format. Protocol 2 is the
 * MC 26.3 5.0.0-Beta.5+ configuration-hello and compact AvatarConfig format.
 * Each recipient is encoded using the protocol it negotiated; this also keeps
 * mixed-version players and the authenticated Velocity bridge isolated.</p>
 */
public final class NetworkManager {
    private final GenderModPlugin plugin;
    private final Beta4SyncPacketCodec legacyCodec = new Beta4SyncPacketCodec();
    private final Protocol2SyncPacketCodec modernCodec = new Protocol2SyncPacketCodec();
    private final ConcurrentMap<UUID, Integer> negotiatedVersions = new ConcurrentHashMap<>();
    private final Set<UUID> waitingForJoinSync = ConcurrentHashMap.newKeySet();
    /** Players whose local client has supplied a newer authoritative profile this session. */
    private final Set<UUID> locallyOwnedProfiles = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<UUID, ModUser> profiles = new ConcurrentHashMap<>();
    /** Tagged raw payloads are used only for duplicate suppression. */
    private final ConcurrentMap<UUID, byte[]> profileFingerprints = new ConcurrentHashMap<>();
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

    /**
     * Starts the protocol-2 handshake in Paper's configuration phase. Paper
     * exposes this phase through PlayerConfigurationConnection and its plugin
     * message recipient API; no NMS packet injection is needed.
     */
    public void onInitialConfiguration(PlayerConfigurationConnection connection) {
        UUID playerId = connection.getProfile().getId();
        if (playerId == null) {
            return;
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            // ClientboundSyncHelloPacket encodes an IntArrayList containing [2]:
            // collection length followed by the supported protocol VarInt.
            output.writeVarInt(1);
            output.writeVarInt(ModConstants.MODERN_SYNC_PROTOCOL_VERSION);
            connection.sendPluginMessage(plugin, ModConstants.MODERN_CLIENTBOUND_HELLO, bytes.toByteArray());
        } catch (IOException exception) {
            plugin.getCustomLogger().debug(exception, "Could not send protocol-2 hello to %s", playerId);
        }
    }

    /** Handles the protocol-2 serverbound hello during configuration. */
    public void handleModernHello(PlayerConfigurationConnection connection, byte[] message) {
        UUID playerId = connection.getProfile().getId();
        if (playerId == null || message.length > 5) {
            return;
        }
        try (CraftInputStream input = CraftInputStream.ofBytes(message)) {
            int clientVersion = input.readVarInt();
            if (input.available() != 0 || clientVersion != ModConstants.MODERN_SYNC_PROTOCOL_VERSION) {
                negotiatedVersions.remove(playerId);
                plugin.getCustomLogger().debug(
                        "Unsupported Female Gender Mod protocol-2 hello from %s (version %s)",
                        playerId, clientVersion);
                return;
            }
            negotiatedVersions.put(playerId, ModConstants.MODERN_SYNC_PROTOCOL_VERSION);
        } catch (IOException exception) {
            negotiatedVersions.remove(playerId);
        }
    }

    /** Handles Beta.4's informational hello in the play phase. */
    public void handleHello(Player player, byte[] message) {
        UUID playerId = player.getUniqueId();
        if (message.length > 5) {
            return;
        }

        try (CraftInputStream input = CraftInputStream.ofBytes(message);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            int clientVersion = input.readVarInt();
            if (input.available() != 0) {
                return;
            }
            if (clientVersion == ModConstants.LEGACY_SYNC_PROTOCOL_VERSION) {
                negotiatedVersions.put(playerId, ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
            } else {
                negotiatedVersions.remove(playerId);
                plugin.getCustomLogger().warning(
                        "Unsupported Female Gender Mod legacy protocol %s from %s; expected Beta.4 protocol %s",
                        clientVersion, player.getName(), ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
            }

            output.writeVarInt(ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
            player.sendPluginMessage(plugin, ModConstants.LEGACY_CLIENTBOUND_HELLO, bytes.toByteArray());
        } catch (IOException ignored) {
            return;
        }

        finishJoinSyncIfReady(player);
    }

    /** Called once from PlayerJoinEvent; no recurring task is created. */
    public void onPlayerJoined(Player player) {
        if (isSupportedClient(player.getUniqueId())) {
            scheduleInitialSync(player);
        } else {
            waitingForJoinSync.add(player.getUniqueId());
        }
    }

    /** Sends the tracked entity's one profile packet to the tracking player. */
    public void onStartTracking(Player recipient, Player tracked) {
        sendProfile(recipient, tracked.getUniqueId());
    }

    /** Backwards-compatible entry point: a play-phase plugin message is protocol 1. */
    public void onClientSync(Player sender, byte[] data) {
        onClientSync(sender, data, ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
    }

    /** Decodes, deduplicates, stores and relays a client-originated profile update. */
    public void onClientSync(Player sender, byte[] data, int protocol) {
        if (data.length > ModConstants.MAX_SYNC_PAYLOAD_BYTES) {
            return;
        }

        UUID senderId = sender.getUniqueId();
        ModUser user = deserializeServerbound(data, senderId, protocol);
        if (user == null || !senderId.equals(user.userId())) {
            return;
        }

        // A valid payload proves support even if the hello raced behind it.
        negotiatedVersions.put(senderId, protocol);
        locallyOwnedProfiles.add(senderId);
        if (waitingForJoinSync.remove(senderId)) {
            scheduleInitialSync(sender);
        }

        if (!storeProfile(user, protocol, data)) {
            return;
        }
        forwardToVelocity(sender, user, protocol);
        broadcastToTrackers(sender);
    }

    /** Handles a message from the protocol-matched authenticated Velocity channel. */
    public void handleProxyMessage(byte[] message, int protocol) {
        byte[] verified = verifyVelocityMessage(message);
        if (verified == null || verified.length < 2 || verified[0] != ModConstants.PROXY_PROFILE_SYNC) {
            return;
        }

        byte[] profile = Arrays.copyOfRange(verified, 1, verified.length);
        if (profile.length > ModConstants.MAX_SYNC_PAYLOAD_BYTES) {
            return;
        }

        ModUser user;
        try {
            user = protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                    ? modernCodec.readClientbound(profile)
                    : legacyCodec.readClientbound(profile);
        } catch (IOException ignored) {
            return;
        }

        // A delayed Velocity response must not overwrite a profile the local
        // client has already sent after joining this backend.
        if (locallyOwnedProfiles.contains(user.userId())) {
            return;
        }
        if (!storeProfile(user, protocol, profile)) {
            return;
        }

        Player source = findOnline(user.userId());
        if (source != null) {
            broadcastToTrackers(source);
        }
    }

    /** Legacy bridge entry point retained for callers compiled against v1. */
    public void handleProxyMessage(byte[] message) {
        handleProxyMessage(message, ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
    }

    /** Requests the joining player's cached profile from the matching bridge channel. */
    public void requestProfileFromVelocity(Player player) {
        int protocol = protocolFor(player.getUniqueId());
        if (protocol < 0 || !isVelocityBridgeEnabled()) {
            return;
        }

        byte[] request = new byte[17];
        request[0] = ModConstants.PROXY_PROFILE_REQUEST;
        writeUuid(request, 1, player.getUniqueId());
        String channel = protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                ? ModConstants.MODERN_PROXY_CHANNEL : ModConstants.LEGACY_PROXY_CHANNEL;
        player.sendPluginMessage(plugin, channel, signVelocityMessage(request));
    }

    /** Cleans all Paper-side state for a disconnected player. */
    public void removePlayer(UUID userId) {
        negotiatedVersions.remove(userId);
        waitingForJoinSync.remove(userId);
        locallyOwnedProfiles.remove(userId);
        profiles.remove(userId);
        profileFingerprints.remove(userId);
        plugin.getUserManager().getUsers().remove(userId);
    }

    public boolean isSupportedClient(UUID userId) {
        return protocolFor(userId) >= 0;
    }

    public int protocolFor(UUID userId) {
        int version = negotiatedVersions.getOrDefault(userId, -1);
        return version == ModConstants.LEGACY_SYNC_PROTOCOL_VERSION
                || version == ModConstants.MODERN_SYNC_PROTOCOL_VERSION ? version : -1;
    }

    private void finishJoinSyncIfReady(Player player) {
        if (isSupportedClient(player.getUniqueId()) && waitingForJoinSync.remove(player.getUniqueId())) {
            scheduleInitialSync(player);
        }
    }

    private boolean storeProfile(ModUser user, int protocol, byte[] payload) {
        byte[] tagged = new byte[payload.length + 1];
        tagged[0] = (byte) protocol;
        System.arraycopy(payload, 0, tagged, 1, payload.length);
        byte[] previous = profileFingerprints.put(user.userId(), tagged);
        profiles.put(user.userId(), user);
        plugin.getUserManager().getUsers().put(user.userId(), user);
        return !Arrays.equals(previous, tagged);
    }

    private void scheduleInitialSync(Player player) {
        // One one-shot task ensures the client is in play phase and has
        // registered its clientbound sync receiver. This is not polling.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !isSupportedClient(player.getUniqueId())) {
                return;
            }
            sendInitialTrackedProfiles(player);
            requestProfileFromVelocity(player);
        });
    }

    private void sendInitialTrackedProfiles(Player recipient) {
        for (Player source : plugin.getServer().getOnlinePlayers()) {
            if (!source.equals(recipient) && source.getTrackedBy().contains(recipient)) {
                sendProfile(recipient, source.getUniqueId());
            }
        }
    }

    private void broadcastToTrackers(Player source) {
        for (Player recipient : source.getTrackedBy()) {
            if (!recipient.equals(source) && isSupportedClient(recipient.getUniqueId())) {
                sendProfile(recipient, source.getUniqueId());
            }
        }
    }

    private void sendProfile(Player recipient, UUID sourceId) {
        if (recipient.getUniqueId().equals(sourceId)) {
            return;
        }
        ModUser user = profiles.get(sourceId);
        int protocol = protocolFor(recipient.getUniqueId());
        if (user == null || protocol < 0) {
            return;
        }
        try {
            byte[] payload = protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                    ? modernCodec.writeClientbound(user) : legacyCodec.writeClientbound(user);
            String channel = protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                    ? ModConstants.MODERN_CLIENTBOUND_SYNC : ModConstants.LEGACY_CLIENTBOUND_SYNC;
            recipient.sendPluginMessage(plugin, channel, payload);
        } catch (IOException ignored) {
            // Invalid server-side state is never sent to a client.
        }
    }

    private void forwardToVelocity(Player carrier, ModUser user, int protocol) {
        if (!isVelocityBridgeEnabled()) {
            return;
        }
        try {
            byte[] profile = protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                    ? modernCodec.writeClientbound(user) : legacyCodec.writeClientbound(user);
            byte[] message = new byte[profile.length + 1];
            message[0] = ModConstants.PROXY_PROFILE_SYNC;
            System.arraycopy(profile, 0, message, 1, profile.length);
            String channel = protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                    ? ModConstants.MODERN_PROXY_CHANNEL : ModConstants.LEGACY_PROXY_CHANNEL;
            carrier.sendPluginMessage(plugin, channel, signVelocityMessage(message));
        } catch (IOException ignored) {
            // Invalid server-side state is never forwarded.
        }
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

    private ModUser deserializeServerbound(byte[] data, UUID senderId, int protocol) {
        try {
            return protocol == ModConstants.MODERN_SYNC_PROTOCOL_VERSION
                    ? modernCodec.readServerbound(data, senderId)
                    : legacyCodec.read(CraftInputStream.ofBytes(data), senderId);
        } catch (IOException exception) {
            plugin.getCustomLogger().debug(exception,
                    "Rejected malformed Female Gender Mod protocol %s payload from %s", protocol, senderId);
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
