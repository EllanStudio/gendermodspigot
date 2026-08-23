package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Event-driven debouncer for mod sync broadcasts.
 * <p>
 * Instead of polling every tick, only schedules a broadcast when:
 * <ul>
 *   <li>A player sends mod data change → markChanged(uid)</li>
 *   <li>A cross-server message arrives → markChanged(uid)</li>
 *   <li>A player joins → markNeedsSnapshot(player)</li>
 * </ul>
 * Changes within the debounce window (1 tick) are coalesced into one
 * consolidated batch packet per recipient — no packet explosion.
 */
public final class TickScheduler {
    private static final long DEBOUNCE_TICKS = 1;

    private final GenderModPlugin plugin;
    private final Set<UUID> dirtyUsers = ConcurrentHashMap.newKeySet();
    private final Set<Player> dirtySnapshots = ConcurrentHashMap.newKeySet();

    private volatile BukkitTask pendingTask;

    public TickScheduler(GenderModPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Mark a user as changed. If no broadcast is scheduled, queue one
     * after {@link #DEBOUNCE_TICKS} ticks (coalesces rapid changes).
     */
    public void markChanged(UUID userId) {
        dirtyUsers.add(userId);
        scheduleDebounced();
    }

    /**
     * Batch variant for cross-server sync (multiple users at once).
     */
    public void markChanged(Set<UUID> userIds) {
        dirtyUsers.addAll(userIds);
        scheduleDebounced();
    }

    /**
     * Mark a player for full snapshot (on join).
     */
    public void markNeedsSnapshot(Player player) {
        dirtySnapshots.add(player);
        // Snapshot should be sent promptly — no debounce
        scheduleDebounced();
    }

    public void cancel() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
        dirtyUsers.clear();
        dirtySnapshots.clear();

    }

    private void scheduleDebounced() {
        if (pendingTask != null) return; // already scheduled
        pendingTask = plugin.getServer().getScheduler().runTaskLater(
                plugin, this::flush, DEBOUNCE_TICKS);
    }

    private void flush() {
        pendingTask = null;

        // 1. Snapshot new players first (they need full state)
        Set<Player> snapshots = new HashSet<>(dirtySnapshots);
        dirtySnapshots.clear();
        for (Player p : snapshots) {
            if (p.isOnline()) {
                plugin.getNetworkManager().sendFullSnapshot(p);
            }
        }

        // 2. Broadcast changed users to all online V6 players
        Set<UUID> changed = new HashSet<>(dirtyUsers);
        dirtyUsers.clear();
        if (!changed.isEmpty()) {
            // Debounce (1 tick) already coalesces rapid changes from same user
            plugin.getNetworkManager().broadcastChanged(changed);
        }
    }
}
