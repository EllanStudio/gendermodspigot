package dbrighthd.wildfiregendermodplugin.listeners;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles join/quit: request full snapshot on join, cleanup on quit.
 */
public class ConnectionListener implements Listener {
    private final GenderModPlugin plugin;

    public ConnectionListener(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getTickScheduler().markNeedsSnapshot(player);
        plugin.getNetworkManager().requestProxyData(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getUserManager().getUsers().remove(uuid);
        plugin.getNetworkManager().removeNegotiation(uuid);
    }
}
