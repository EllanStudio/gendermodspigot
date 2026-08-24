package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Paper lifecycle and tracking hooks.
 *
 * Profile delivery is scoped to Paper's entity tracking graph, exactly as the
 * official Fabric implementation does. No all-online-player broadcast occurs.
 */
public final class ConnectionListener implements Listener {
    private final GenderModPlugin plugin;

    public ConnectionListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getNetworkManager().onPlayerJoined(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onStartTracking(PlayerTrackEntityEvent event) {
        if (event.getEntity() instanceof Player tracked) {
            plugin.getNetworkManager().onStartTracking(event.getPlayer(), tracked);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getNetworkManager().removePlayer(event.getPlayer().getUniqueId());
    }
}
