package dbrighthd.wildfiregendermodplugin.wildfire;

/** Exact protocol constants for Female Gender Mod 5.0.0-Beta.4 on MC 26.2. */
public final class ModConstants {
    public static final String MOD_ID = "wildfire_gender";

    /** Informational play-phase hello introduced by Beta.2. */
    public static final String SERVERBOUND_HELLO = MOD_ID + ":serverbound/hello";
    public static final String CLIENTBOUND_HELLO = MOD_ID + ":clientbound/hello";

    /** Beta.4 play-phase profile channels. */
    public static final String SERVERBOUND_SYNC = MOD_ID + ":send_gender_info";
    public static final String CLIENTBOUND_SYNC = MOD_ID + ":sync";
    public static final int SYNC_PROTOCOL_VERSION = 1;

    /** Custom backend <-> Velocity bridge channel. */
    public static final String PROXY_CHANNEL = MOD_ID + ":proxy";
    public static final byte PROXY_PROFILE_SYNC = 1;
    public static final byte PROXY_PROFILE_REQUEST = 2;

    public static final int MAX_SYNC_PAYLOAD_BYTES = 32 * 1024;

    private ModConstants() {
    }
}
