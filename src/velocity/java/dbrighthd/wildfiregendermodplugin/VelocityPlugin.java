package dbrighthd.wildfiregendermodplugin;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticated Velocity bridge for both Female Gender Mod profile protocols.
 * Profiles remain protocol-specific in the proxy cache, preserving the exact
 * legacy Beta.4 bridge while allowing independent modern 26.3 backends.
 */
@Plugin(
        id = "female-gender-velocity",
        name = "Female Gender Mod Velocity Bridge",
        version = "1.6.2",
        authors = {"EllanStudio"}
)
public final class VelocityPlugin {
    private static final MinecraftChannelIdentifier LEGACY_CHANNEL =
            MinecraftChannelIdentifier.from("wildfire_gender:proxy");
    private static final MinecraftChannelIdentifier MODERN_CHANNEL =
            MinecraftChannelIdentifier.from("female_gender_mod:proxy");
    private static final byte PROFILE_SYNC = 1;
    private static final byte PROFILE_REQUEST = 2;
    private static final int UUID_BYTES = 16;
    private static final int SIGNATURE_BYTES = 32;
    private static final int MAX_PROFILE_BYTES = 32 * 1024;
    private static final String CONFIG_FILE = "config.properties";

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<UUID, byte[]> legacyProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, byte[]> modernProfiles = new ConcurrentHashMap<>();
    private volatile byte[] sharedSecret = new byte[0];

    @Inject
    public VelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (!loadSharedSecret()) {
            logger.warn("Female Gender Mod Velocity Bridge is disabled: set shared-secret in {}/{}", dataDirectory, CONFIG_FILE);
            return;
        }
        proxy.getChannelRegistrar().register(LEGACY_CHANNEL, MODERN_CHANNEL);
        logger.info("Female Gender Mod Velocity Bridge v1.6.2 enabled (protocols 1 and 2)");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        legacyProfiles.clear();
        modernProfiles.clear();
        if (isBridgeEnabled()) {
            proxy.getChannelRegistrar().unregister(LEGACY_CHANNEL, MODERN_CHANNEL);
        }
    }

    /** Handles only authenticated messages from a backend connection. */
    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        MinecraftChannelIdentifier channel = channel(event);
        if (channel == null) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection source)) {
            return;
        }
        if (event.getData().length > 1 + MAX_PROFILE_BYTES + SIGNATURE_BYTES) {
            return;
        }

        byte[] message = verifyEnvelope(event.getData());
        if (message == null || message.length < 1) {
            return;
        }

        Map<UUID, byte[]> profiles = channel.equals(MODERN_CHANNEL) ? modernProfiles : legacyProfiles;
        switch (message[0]) {
            case PROFILE_SYNC -> handleProfileSync(source, channel, profiles, message);
            case PROFILE_REQUEST -> handleProfileRequest(source, channel, profiles, message);
            default -> { }
        }
    }

    private MinecraftChannelIdentifier channel(PluginMessageEvent event) {
        if (LEGACY_CHANNEL.equals(event.getIdentifier())) {
            return LEGACY_CHANNEL;
        }
        if (MODERN_CHANNEL.equals(event.getIdentifier())) {
            return MODERN_CHANNEL;
        }
        return null;
    }

    private void handleProfileSync(ServerConnection source, MinecraftChannelIdentifier channel,
                                   Map<UUID, byte[]> profiles, byte[] message) {
        int profileLength = message.length - 1;
        if (profileLength < UUID_BYTES || profileLength > MAX_PROFILE_BYTES) {
            return;
        }

        byte[] profile = Arrays.copyOfRange(message, 1, message.length);
        UUID playerId = readUuid(profile, 0);
        byte[] previous = profiles.put(playerId, profile);
        if (Arrays.equals(previous, profile)) {
            return;
        }

        String sourceName = source.getServerInfo().getName();
        byte[] outgoing = signEnvelope(PROFILE_SYNC, profile);
        for (RegisteredServer server : proxy.getAllServers()) {
            if (!server.getServerInfo().getName().equals(sourceName)) {
                server.sendPluginMessage(channel, outgoing);
            }
        }
    }

    private void handleProfileRequest(ServerConnection source, MinecraftChannelIdentifier channel,
                                      Map<UUID, byte[]> profiles, byte[] message) {
        if (message.length != 1 + UUID_BYTES) {
            return;
        }

        byte[] profile = profiles.get(readUuid(message, 1));
        if (profile != null) {
            source.sendPluginMessage(channel, signEnvelope(PROFILE_SYNC, profile));
        }
    }

    private boolean loadSharedSecret() {
        Path config = dataDirectory.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(config)) {
                Files.writeString(config,
                        "# Use the same value as velocity.shared-secret on every Paper backend.\n"
                                + "shared-secret=\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }

            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            String secret = properties.getProperty("shared-secret", "").trim();
            if (secret.isEmpty()) {
                return false;
            }
            sharedSecret = secret.getBytes(StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            logger.error("Cannot load Female Gender Mod Velocity bridge configuration", exception);
            return false;
        }
    }

    private boolean isBridgeEnabled() {
        return sharedSecret.length > 0;
    }

    /** type + payload + HMAC-SHA256(type + payload). */
    private byte[] signEnvelope(byte type, byte[] payload) {
        byte[] unsigned = new byte[payload.length + 1];
        unsigned[0] = type;
        System.arraycopy(payload, 0, unsigned, 1, payload.length);
        byte[] signature = hmac(unsigned);
        byte[] signed = Arrays.copyOf(unsigned, unsigned.length + signature.length);
        System.arraycopy(signature, 0, signed, unsigned.length, signature.length);
        return signed;
    }

    private byte[] verifyEnvelope(byte[] signed) {
        if (!isBridgeEnabled() || signed.length <= SIGNATURE_BYTES) {
            return null;
        }
        int unsignedLength = signed.length - SIGNATURE_BYTES;
        byte[] unsigned = Arrays.copyOf(signed, unsignedLength);
        byte[] signature = Arrays.copyOfRange(signed, unsignedLength, signed.length);
        return MessageDigest.isEqual(hmac(unsigned), signature) ? unsigned : null;
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sharedSecret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }

    private static UUID readUuid(byte[] bytes, int offset) {
        long most = 0L;
        long least = 0L;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (bytes[offset + i] & 0xFFL);
            least = (least << 8) | (bytes[offset + 8 + i] & 0xFFL);
        }
        return new UUID(most, least);
    }
}
