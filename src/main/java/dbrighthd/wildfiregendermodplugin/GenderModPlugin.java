package dbrighthd.wildfiregendermodplugin;

import dbrighthd.wildfiregendermodplugin.listeners.ConnectionListener;
import dbrighthd.wildfiregendermodplugin.listeners.ModPayloadListener;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.networking.TickScheduler;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class GenderModPlugin extends JavaPlugin {
    private final CustomPluginLogger logger = new CustomPluginLogger(this);
    private final UserManager userManager = new UserManager();
    private final TickScheduler tickScheduler = new TickScheduler(this);
    private final NetworkManager networkManager = new NetworkManager(this, tickScheduler);

    @Override
    public void onEnable() {
        logger.info("Female-Gender-Mod-Plugin v{}", getDescription().getVersion());
        saveDefaultConfig();

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        ModPayloadListener listener = new ModPayloadListener(this);

        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_HELLO, listener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_HELLO);
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.SERVERBOUND_SYNC, listener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.CLIENTBOUND_SYNC);

        // Proxy channel (Velocity + BungeeCord)
        getServer().getMessenger().registerIncomingPluginChannel(this, ModConstants.PROXY_CHANNEL, listener);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ModConstants.PROXY_CHANNEL);
    }

    @Override
    public void onDisable() {
        tickScheduler.cancel();
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public CustomPluginLogger getCustomLogger() { return logger; }
    public UserManager getUserManager() { return userManager; }
    public NetworkManager getNetworkManager() { return networkManager; }
    public TickScheduler getTickScheduler() { return tickScheduler; }
}
