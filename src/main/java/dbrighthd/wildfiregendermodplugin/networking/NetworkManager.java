package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV6;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Core networking: hello handshake + event-driven batched sync + proxy forwarding.
 * MC 26.2 only (mod sync protocol v2).
 * <p>
 * Supports Velocity and BungeeCord cross-server.
 * <p>
 * Performance: ONE consolidated batch packet per recipient per broadcast event.
 */
public class NetworkManager {
    private final GenderModPlugin plugin;
    private final TickScheduler scheduler;
    private final ConcurrentMap<UUID, Integer> negotiated = new ConcurrentHashMap<>();
    private final ModSyncPacketV6 codec = new ModSyncPacketV6();

    public NetworkManager(GenderModPlugin plugin, TickScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    // ===== Hello handshake =====

    public void handleHello(Player player, byte[] message) {
        try (var in = CraftInputStream.ofBytes(message);
             var out = new ByteArrayOutputStream();
             var cos = new CraftOutputStream(out)) {
            int clientVer = in.readVarInt();
            negotiated.put(player.getUniqueId(), clientVer);
            cos.writeVarInt(ModConstants.SYNC_PROTOCOL_VERSION);
            player.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_HELLO, out.toByteArray());
        } catch (IOException ignored) {}
    }

    public boolean isV6(UUID uuid) {
        return ModConstants.SYNC_PROTOCOL_VERSION == negotiated.getOrDefault(uuid, -1);
    }

    public void removeNegotiation(UUID uuid) {
        negotiated.remove(uuid);
    }

    // ===== Incoming client sync =====

    public void onClientSync(Player sender, byte[] data) {
        ModUser user = deserializeSync(data, sender.getUniqueId());
        if (user == null) return;

        plugin.getUserManager().getUsers().put(user.userId(), user);
        scheduler.markChanged(user.userId());
        forwardToProxy(user);
    }

    // ===== Broadcast (called by TickScheduler) =====

    public void broadcastChanged(Set<UUID> changedUserIds) {
        if (changedUserIds.isEmpty()) return;

        List<ModUser> users = new ArrayList<>();
        for (UUID id : changedUserIds) {
            ModUser u = plugin.getUserManager().getUsers().get(id);
            if (u != null) users.add(u);
        }
        if (users.isEmpty()) return;

        byte[] batch;
        try {
            batch = codec.writeBatch(users);
        } catch (IOException e) {
            return;
        }

        for (Player recipient : plugin.getServer().getOnlinePlayers()) {
            if (!isV6(recipient.getUniqueId())) continue;
            recipient.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_SYNC, batch);
        }
    }

    public void sendFullSnapshot(Player recipient) {
        if (!isV6(recipient.getUniqueId())) return;
        Collection<ModUser> all = plugin.getUserManager().getUsers().values();
        if (all.isEmpty()) return;

        try {
            byte[] batch = codec.writeBatch(new ArrayList<>(all));
            recipient.sendPluginMessage(plugin, ModConstants.CLIENTBOUND_SYNC, batch);
        } catch (IOException ignored) {}
    }

    // ===== Deserialization =====

    public ModUser deserializeSync(byte[] data, UUID senderId) {
        try (var in = CraftInputStream.ofBytes(data)) {
            return codec.read(in, senderId);
        } catch (IOException e) {
            return null;
        }
    }

    // ===== Proxy forwarding (Velocity / BungeeCord) =====

    /**
     * Forward a user's mod data to the proxy for cross-server distribution.
     * Uses the standard BungeeCord channel which both Velocity and BungeeCord understand.
     */
    public void forwardToProxy(ModUser user) {
        Player p = findOnline(user.userId());
        if (p == null) return;
        try {
            byte[] payload = codec.writeClientbound(user);
            sendProxyMessage(p, ModConstants.CROSS_SYNC, payload);
        } catch (Exception ignored) {}
    }

    /**
     * Handle incoming cross-server sync from the proxy.
     */
    public void handleProxySync(Player sender, byte[] message) {
        Map<UUID, ModUser> users = null;
        try {
            users = codec.readBatch(message);
        } catch (IOException ignored) {
            try {
                ModUser single = codec.readClientbound(message);
                if (single != null) {
                    users = new LinkedHashMap<>();
                    users.put(single.userId(), single);
                }
            } catch (IOException ignored2) {}
        }

        if (users == null || users.isEmpty()) return;

        for (ModUser user : users.values()) {
            plugin.getUserManager().getUsers().put(user.userId(), user);
        }
        scheduler.markChanged(new HashSet<UUID>(users.keySet()));
    }

    /**
     * Request all mod data from other servers via the proxy.
     */
    public void requestProxyData(Player sender) {
        try {
            sendProxyMessage(sender, ModConstants.CROSS_REQUEST, new byte[0]);
        } catch (Exception ignored) {}
    }

    private void sendProxyMessage(Player player, String subCmd, byte[] payload) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(subCmd.getBytes(StandardCharsets.UTF_8));
            out.write((byte) 0);
            int len = payload.length;
            out.write((len >>> 24) & 0xFF);
            out.write((len >>> 16) & 0xFF);
            out.write((len >>> 8) & 0xFF);
            out.write(len & 0xFF);
            out.write(payload);
            player.sendPluginMessage(plugin, ModConstants.PROXY_CHANNEL, out.toByteArray());
        }
    }

    private Player findOnline(UUID uuid) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getUniqueId().equals(uuid)) return p;
        }
        return null;
    }
}
