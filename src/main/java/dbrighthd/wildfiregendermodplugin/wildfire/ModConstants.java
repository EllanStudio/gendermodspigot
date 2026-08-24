package dbrighthd.wildfiregendermodplugin.wildfire;

/**
 * V6 protocol constants for the Paper backend plugin.
 *
 * The proxy channel is private to the companion Velocity bridge; it is not a
 * BungeeCord compatibility channel and must never be exposed to clients.
 */
public final class ModConstants {
    public static final String MOD_ID = "wildfire_gender";

    public static final String SERVERBOUND_HELLO = MOD_ID + ":serverbound/hello";
    public static final String CLIENTBOUND_HELLO = MOD_ID + ":clientbound/hello";
    public static final String SERVERBOUND_SYNC = MOD_ID + ":serverbound/sync";
    public static final String CLIENTBOUND_SYNC = MOD_ID + ":clientbound/sync";
    public static final int SYNC_PROTOCOL_VERSION = 2;

    /** Custom backend <-> Velocity bridge channel. */
    public static final String PROXY_CHANNEL = MOD_ID + ":proxy";
    public static final byte PROXY_PROFILE_SYNC = 1;
    public static final byte PROXY_PROFILE_REQUEST = 2;

    public static final int MAX_SYNC_PAYLOAD_BYTES = 32 * 1024;

    private ModConstants() {
    }
}
