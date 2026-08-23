package dbrighthd.wildfiregendermodplugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Velocity plugin for cross-server mod sync forwarding.
 * Receives mod data from one MC server and forwards it to all other MC servers.
 * Works alongside the Paper plugin installed on each backend server.
 *
 * <p>Protocol: BungeeCord plugin message format — subCmd + \0 + length(4) + data
 */
@Plugin(id = "wildfire-gender-mod-plugin", name = "Female-Gender-Mod-Plugin",
        version = "1.6.0", authors = {"dbrighthd"})
public class VelocityPlugin {

    private static final String CROSS_SYNC = "ModSync";
    private static final String CROSS_REQUEST = "ModSync:RequestAll";

    private final ProxyServer proxy;
    private final Logger logger;
    private final MinecraftChannelIdentifier bungeeChannel;

    // playerId -> serverName -> payload
    private final ConcurrentMap<UUID, ConcurrentMap<String, byte[]>> userDataCache = new ConcurrentHashMap<>();

    @Inject
    public VelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.bungeeChannel = MinecraftChannelIdentifier.from("bungeecord");
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        logger.info("Female-Gender-Mod-Plugin (Velocity) v1.6.0 enabled");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        userDataCache.clear();
    }

    /**
     * Handle plugin messages from backend MC servers.
     * The source is a RegisteredServer (backend) or Player.
     */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!bungeeChannel.getId().equalsIgnoreCase(event.getIdentifier().getId())) {
            return;
        }

        String sourceServerName = resolveSourceName(event.getSource());
        byte[] data = event.getData();

        handleBungeeCordMessage(sourceServerName, data);
        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    private String resolveSourceName(com.velocitypowered.api.proxy.messages.ChannelMessageSource source) {
        if (source instanceof RegisteredServer) {
            return ((RegisteredServer) source).getServerInfo().getName();
        } else if (source instanceof Player) {
            return ((Player) source).getCurrentServer()
                    .map(s -> s.getServerInfo().getName())
                    .orElse("player-" + ((Player) source).getUniqueId());
        }
        return "unknown";
    }

    private void handleBungeeCordMessage(String source, byte[] message) {
        int sep = indexOf(message, (byte) 0);
        if (sep < 0 || message.length <= sep + 4) return;

        String subCmd = new String(message, 0, sep, StandardCharsets.UTF_8);
        byte[] payload = new byte[message.length - sep - 1];
        System.arraycopy(message, sep + 1, payload, 0, payload.length);

        if (CROSS_SYNC.equals(subCmd)) {
            if (payload.length < 4) return;
            int len = ((payload[0] & 0xFF) << 24) | ((payload[1] & 0xFF) << 16)
                    | ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
            if (payload.length < 4 + len) return;
            byte[] data = new byte[len];
            System.arraycopy(payload, 4, data, 0, len);
            handleCrossSync(source, data);
        } else if (CROSS_REQUEST.equals(subCmd)) {
            handleCrossRequest(source);
        }
    }

    private void handleCrossSync(String sourceServer, byte[] data) {
        UUID playerId = extractUUID(data);
        if (playerId == null) return;

        // Cache
        userDataCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(sourceServer, data);

        // Forward to all OTHER registered servers
        for (RegisteredServer server : proxy.getAllServers()) {
            String name = server.getServerInfo().getName();
            if (name.equals(sourceServer)) continue;
            sendToServer(server, CROSS_SYNC, data);
        }
    }

    private void handleCrossRequest(String requestingServer) {
        for (Map.Entry<UUID, ConcurrentMap<String, byte[]>> entry : userDataCache.entrySet()) {
            for (Map.Entry<String, byte[]> sEntry : entry.getValue().entrySet()) {
                if (!sEntry.getKey().equals(requestingServer)) {
                    sendToServer(requestingServer, CROSS_SYNC, sEntry.getValue());
                }
            }
        }
    }

    private void sendToServer(String serverName, String subCmd, byte[] payload) {
        RegisteredServer server = proxy.getServer(serverName).orElse(null);
        if (server == null) return;
        sendToServer(server, subCmd, payload);
    }

    private void sendToServer(RegisteredServer server, String subCmd, byte[] payload) {
        try {
            byte[] message = encodeBungeeCord(subCmd, payload);
            server.sendPluginMessage(bungeeChannel, message);
        } catch (Exception e) {
            logger.debug("Failed to send mod data to server: {}", e.getMessage());
        }
    }

    private byte[] encodeBungeeCord(String subCmd, byte[] payload) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            out.write(subCmd.getBytes(StandardCharsets.UTF_8));
            out.write((byte) 0);
            int len = payload.length;
            out.write((len >>> 24) & 0xFF);
            out.write((len >>> 16) & 0xFF);
            out.write((len >>> 8) & 0xFF);
            out.write(len & 0xFF);
            out.write(payload);
            return out.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private UUID extractUUID(byte[] data) {
        if (data.length < 16) return null;
        long msb = 0, lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data[i] & 0xFF);
            lsb = (lsb << 8) | (data[8 + i] & 0xFF);
        }
        return new UUID(msb, lsb);
    }

    private static int indexOf(byte[] haystack, byte needle) {
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i] == needle) return i;
        }
        return -1;
    }
}
