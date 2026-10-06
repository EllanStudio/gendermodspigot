package dbrighthd.wildfiregendermodplugin;

import dbrighthd.wildfiregendermodplugin.listeners.ConnectionListener;
import dbrighthd.wildfiregendermodplugin.listeners.ModPayloadListener;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.networking.NetworkManager;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import dbrighthd.wildfiregendermodplugin.wildfire.UserManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class GenderModPlugin extends JavaPlugin {
    private final CustomPluginLogger logger = new CustomPluginLogger(this);
    private final UserManager userManager = new UserManager();
    private final NetworkManager networkManager = new NetworkManager(this);

    @Override
    public void onEnable() {
        logger.info("Female-Gender-Mod-Paper v%s", getPluginMeta().getVersion());
        saveDefaultConfig();
        networkManager.enable();

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        ModPayloadListener listener = new ModPayloadListener(this);
        registerIncoming(ModConstants.LEGACY_SERVERBOUND_HELLO, listener);
        registerOutgoing(ModConstants.LEGACY_CLIENTBOUND_HELLO);
        registerIncoming(ModConstants.LEGACY_SERVERBOUND_SYNC, listener);
        registerOutgoing(ModConstants.LEGACY_CLIENTBOUND_SYNC);

        // Paper 26.3's PlayerConnection overload delivers this during CONFIG.
        registerIncoming(ModConstants.MODERN_SERVERBOUND_HELLO, listener);
        registerOutgoing(ModConstants.MODERN_CLIENTBOUND_HELLO);
        registerIncoming(ModConstants.MODERN_SERVERBOUND_SYNC, listener);
        registerOutgoing(ModConstants.MODERN_CLIENTBOUND_SYNC);

        registerIncoming(ModConstants.LEGACY_PROXY_CHANNEL, listener);
        registerOutgoing(ModConstants.LEGACY_PROXY_CHANNEL);
        registerIncoming(ModConstants.MODERN_PROXY_CHANNEL, listener);
        registerOutgoing(ModConstants.MODERN_PROXY_CHANNEL);
    }

    private void registerIncoming(String channel, ModPayloadListener listener) {
        getServer().getMessenger().registerIncomingPluginChannel(this, channel, listener);
    }

    private void registerOutgoing(String channel) {
        getServer().getMessenger().registerOutgoingPluginChannel(this, channel);
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
    }

    public CustomPluginLogger getCustomLogger() { return logger; }
    public UserManager getUserManager() { return userManager; }
    public NetworkManager getNetworkManager() { return networkManager; }
}
