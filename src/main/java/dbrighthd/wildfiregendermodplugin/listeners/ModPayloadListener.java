package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Routes the mod's V6 payloads and the private Velocity bridge channel.
 *
 * Paper 26.2 delivers configuration-stage payloads through PlayerConnection,
 * not Player; V6 hello is therefore handled through that overload.
 */
public final class ModPayloadListener implements PluginMessageListener {
    private final GenderModPlugin plugin;

    public ModPayloadListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    /** Play-stage payloads, including the standard serverbound V6 sync packet. */
    @SuppressWarnings("deprecation") // Paper requires this legacy Player overload for play-stage dispatch.
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        switch (channel) {
            case ModConstants.SERVERBOUND_HELLO ->
                    plugin.getNetworkManager().handleHello(player.getUniqueId(), player, message);
            case ModConstants.SERVERBOUND_SYNC -> plugin.getNetworkManager().onClientSync(player, message);
            case ModConstants.PROXY_CHANNEL -> plugin.getNetworkManager().handleProxyMessage(message);
            default -> {
            }
        }
    }

    /** Configuration-stage payloads; there is no Bukkit Player object yet. */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull PlayerConnection connection,
                                        byte @NotNull [] message) {
        if (ModConstants.SERVERBOUND_HELLO.equals(channel)
                && connection instanceof PlayerConfigurationConnection configuring) {
            var playerId = configuring.getProfile().getId();
            if (playerId != null) {
                plugin.getNetworkManager().handleHello(playerId, configuring, message);
            }
        }
    }
}
