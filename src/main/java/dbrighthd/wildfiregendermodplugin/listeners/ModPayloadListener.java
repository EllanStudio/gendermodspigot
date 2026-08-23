package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Routes plugin messages: V6 sync + proxy (Velocity/BungeeCord) cross-server.
 */
public class ModPayloadListener implements PluginMessageListener {
    private final GenderModPlugin plugin;

    public ModPayloadListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        switch (channel) {
            case ModConstants.SERVERBOUND_HELLO:
                plugin.getNetworkManager().handleHello(player, message);
                break;
            case ModConstants.SERVERBOUND_SYNC:
                plugin.getNetworkManager().onClientSync(player, message);
                break;
            case ModConstants.PROXY_CHANNEL:
                handleProxyMessage(player, message);
                break;
        }
    }

    private void handleProxyMessage(Player sender, byte[] message) {
        int sep = indexOf(message, (byte) 0);
        if (sep < 0 || message.length <= sep + 4) return;

        String subCmd = new String(message, 0, sep, StandardCharsets.UTF_8);
        byte[] payload = new byte[message.length - sep - 1];
        System.arraycopy(message, sep + 1, payload, 0, payload.length);

        if (ModConstants.CROSS_SYNC.equals(subCmd)) {
            if (payload.length < 4) return;
            int len = ((payload[0] & 0xFF) << 24) | ((payload[1] & 0xFF) << 16)
                    | ((payload[2] & 0xFF) << 8) | (payload[3] & 0xFF);
            if (payload.length < 4 + len) return;
            byte[] data = new byte[len];
            System.arraycopy(payload, 4, data, 0, len);
            plugin.getNetworkManager().handleProxySync(sender, data);
        } else if (ModConstants.CROSS_REQUEST.equals(subCmd)) {
            for (ModUser user : plugin.getUserManager().getUsers().values()) {
                if (user != null) plugin.getNetworkManager().forwardToProxy(user);
            }
        }
    }

    private static int indexOf(byte[] haystack, byte needle) {
        for (int i = 0; i < haystack.length; i++) {
            if (haystack[i] == needle) return i;
        }
        return -1;
    }
}
