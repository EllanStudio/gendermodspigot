package dbrighthd.wildfiregendermodplugin.wildfire;

/**
 * Channel and protocol constants for MC 26.2 / mod 5.0.0-Beta.4+.
 * Uses sync protocol v2 with hello handshake during configuration phase.
 * <p>
 * Cross-server forwarding supports both Velocity and BungeeCord proxies
 * via the standard "BungeeCord" plugin messaging channel (server→proxy).
 * <p>
 * The dedicated Velocity plugin uses Velocity's own proxy messaging API
 * for server→proxy→server routing.
 */
public final class ModConstants {
    public static final String MOD_ID = "wildfire_gender";

    // V6 channels (mod sync protocol v2)
    public static final String SERVERBOUND_HELLO = MOD_ID + ":serverbound/hello";
    public static final String CLIENTBOUND_HELLO = MOD_ID + ":clientbound/hello";
    public static final String SERVERBOUND_SYNC = MOD_ID + ":serverbound/sync";
    public static final String CLIENTBOUND_SYNC = MOD_ID + ":clientbound/sync";

    public static final int SYNC_PROTOCOL_VERSION = 2;

    // Proxy→server channel (BungeeCord/Velocity standard)
    public static final String PROXY_CHANNEL = "BungeeCord";

    // Cross-server sub-commands
    public static final String CROSS_SYNC = "ModSync";
    public static final String CROSS_REQUEST = "ModSync:RequestAll";

    // Velocity plugin channel (plugin messages between proxies and servers)
    public static final String VELOCITY_CHANNEL = "wildfire_gender";

    private ModConstants() {}
}
