package dbrighthd.wildfiregendermodplugin.wildfire;

/** Wire constants for the legacy Beta.4 and modern 5.0.0 sync protocols. */
public final class ModConstants {
    /** Female Gender Mod 5.0.0-Beta.4 / MC 26.2. */
    public static final String LEGACY_MOD_ID = "wildfire_gender";
    public static final String LEGACY_SERVERBOUND_HELLO = LEGACY_MOD_ID + ":serverbound/hello";
    public static final String LEGACY_CLIENTBOUND_HELLO = LEGACY_MOD_ID + ":clientbound/hello";
    public static final String LEGACY_SERVERBOUND_SYNC = LEGACY_MOD_ID + ":send_gender_info";
    public static final String LEGACY_CLIENTBOUND_SYNC = LEGACY_MOD_ID + ":sync";
    public static final int LEGACY_SYNC_PROTOCOL_VERSION = 1;

    /** Female Gender Mod 5.0.0-Beta.5+ / MC 26.3. */
    public static final String MODERN_MOD_ID = "female_gender_mod";
    public static final String MODERN_SERVERBOUND_HELLO = MODERN_MOD_ID + ":serverbound/hello";
    public static final String MODERN_CLIENTBOUND_HELLO = MODERN_MOD_ID + ":clientbound/hello";
    public static final String MODERN_SERVERBOUND_SYNC = MODERN_MOD_ID + ":serverbound/sync";
    public static final String MODERN_CLIENTBOUND_SYNC = MODERN_MOD_ID + ":clientbound/sync";
    public static final int MODERN_SYNC_PROTOCOL_VERSION = 2;

    /** Backwards-compatible aliases for code and integrations using the Beta.4 names. */
    public static final String MOD_ID = LEGACY_MOD_ID;
    public static final String SERVERBOUND_HELLO = LEGACY_SERVERBOUND_HELLO;
    public static final String CLIENTBOUND_HELLO = LEGACY_CLIENTBOUND_HELLO;
    public static final String SERVERBOUND_SYNC = LEGACY_SERVERBOUND_SYNC;
    public static final String CLIENTBOUND_SYNC = LEGACY_CLIENTBOUND_SYNC;
    public static final int SYNC_PROTOCOL_VERSION = LEGACY_SYNC_PROTOCOL_VERSION;

    /** Private backend <-> Velocity bridge channels, one per wire protocol. */
    public static final String LEGACY_PROXY_CHANNEL = LEGACY_MOD_ID + ":proxy";
    public static final String MODERN_PROXY_CHANNEL = MODERN_MOD_ID + ":proxy";
    public static final byte PROXY_PROFILE_SYNC = 1;
    public static final byte PROXY_PROFILE_REQUEST = 2;

    public static final int MAX_SYNC_PAYLOAD_BYTES = 32 * 1024;

    private ModConstants() {
    }
}
