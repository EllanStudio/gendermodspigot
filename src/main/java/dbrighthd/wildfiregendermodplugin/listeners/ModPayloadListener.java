package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConnection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/** Routes both legacy play-stage and modern configuration-stage payloads. */
public final class ModPayloadListener implements PluginMessageListener {
    private final GenderModPlugin plugin;

    public ModPayloadListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Paper 26.3 calls this overload for both joined and configuring players.
     * StandardMessenger then invokes the Player overload for a game connection,
     * so game payloads are intentionally handled only by that overload to avoid
     * processing them twice.
     */
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull PlayerConnection connection,
                                        byte @NotNull [] message) {
        if (connection instanceof PlayerConfigurationConnection configuration
                && ModConstants.MODERN_SERVERBOUND_HELLO.equals(channel)) {
            plugin.getNetworkManager().handleModernHello(configuration, message);
        }
    }

    @SuppressWarnings("deprecation") // Required Bukkit play-stage listener entry point.
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player,
                                        byte @NotNull [] message) {
        switch (channel) {
            case ModConstants.LEGACY_SERVERBOUND_HELLO ->
                    plugin.getNetworkManager().handleHello(player, message);
            case ModConstants.LEGACY_SERVERBOUND_SYNC ->
                    plugin.getNetworkManager().onClientSync(player, message, ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
            case ModConstants.MODERN_SERVERBOUND_SYNC ->
                    plugin.getNetworkManager().onClientSync(player, message, ModConstants.MODERN_SYNC_PROTOCOL_VERSION);
            case ModConstants.LEGACY_PROXY_CHANNEL ->
                    plugin.getNetworkManager().handleProxyMessage(message, ModConstants.LEGACY_SYNC_PROTOCOL_VERSION);
            case ModConstants.MODERN_PROXY_CHANNEL ->
                    plugin.getNetworkManager().handleProxyMessage(message, ModConstants.MODERN_SYNC_PROTOCOL_VERSION);
            default -> {
            }
        }
    }
}
