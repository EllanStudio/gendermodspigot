package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/** Routes the exact Beta.4 play-phase hello, sync, and private proxy payloads. */
public final class ModPayloadListener implements PluginMessageListener {
    private final GenderModPlugin plugin;

    public ModPayloadListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation") // Required Paper play-stage PluginMessageListener entry point.
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player,
                                        byte @NotNull [] message) {
        switch (channel) {
            case ModConstants.SERVERBOUND_HELLO -> plugin.getNetworkManager().handleHello(player, message);
            case ModConstants.SERVERBOUND_SYNC -> plugin.getNetworkManager().onClientSync(player, message);
            case ModConstants.PROXY_CHANNEL -> plugin.getNetworkManager().handleProxyMessage(message);
            default -> {
            }
        }
    }
}
