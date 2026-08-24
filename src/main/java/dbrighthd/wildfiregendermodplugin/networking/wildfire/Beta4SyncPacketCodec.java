package dbrighthd.wildfiregendermodplugin.networking.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.BreastOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GeneralOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.GenderIdentities;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.ModConfiguration;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.PhysicsOptions;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVDirection;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVLayout;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVLayouts;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.UVQuad;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Exact packet codec used by Female Gender Mod 5.0.0-Beta.4 on MC 26.2.
 *
 * <p>Both directions use the same complete profile layout:</p>
 * <pre>
 * UUID, gender, bustSize, hurtSounds, voicePitch,
 * breastPhysics, showInArmor, bounceMultiplier, floppiness,
 * xOffset, yOffset, zOffset, uniboob, cleavage,
 * skin-left UV, skin-right UV, overlay-left UV, overlay-right UV
 * </pre>
 *
 * <p>There is no compact-MALE/present flag in Beta.4 and no custom batch
 * framing. The protocol version advertised by its hello packet is 1.</p>
 */
public final class Beta4SyncPacketCodec {
    private static final float DEFAULT_BUST_SIZE = 0.6F;
    private static final float DEFAULT_VOICE_PITCH = 1.0F;
    private static final float DEFAULT_BOUNCE_MULTIPLIER = 0.333F;
    private static final float DEFAULT_FLOPPINESS = 0.75F;
    private static final int MAX_UV_QUADS = UVDirection.values().length;

    /** Reads a serverbound Beta.4 packet and verifies its embedded UUID. */
    public ModUser read(CraftInputStream input, UUID senderId) throws IOException {
        UUID embeddedId = input.readUUID();
        if (!senderId.equals(embeddedId)) {
            throw new IOException("Profile UUID does not match sending player");
        }
        ModUser user = readBody(input, senderId);
        ensureFullyRead(input);
        return user;
    }

    /** Reads a clientbound (or proxy-cached) Beta.4 packet. */
    public ModUser readClientbound(byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            UUID userId = input.readUUID();
            ModUser user = readBody(input, userId);
            ensureFullyRead(input);
            return user;
        }
    }

    /** Encodes the complete Beta.4 clientbound profile. */
    public byte[] writeClientbound(ModUser user) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            write(user, output);
            return bytes.toByteArray();
        }
    }

    /** Writes the complete Beta.4 profile shared by both packet directions. */
    public void write(ModUser user, CraftOutputStream output) throws IOException {
        ModConfiguration config = user.configuration();
        GeneralOptions general = config.generalOptions();
        PhysicsOptions physics = config.physicsOptions();
        BreastOptions breasts = config.breastOptions();

        output.writeUUID(user.userId());
        output.writeEnum(general.genderIdentity());
        output.writeFloat(breasts.bustSize());
        output.writeBoolean(general.hurtSounds());
        output.writeFloat(general.voicePitch());

        output.writeBoolean(physics.breastPhysics());
        output.writeBoolean(physics.showInArmor());
        output.writeFloat(physics.bounceMultiplier());
        output.writeFloat(physics.floppiness());

        output.writeFloat(breasts.xOffset());
        output.writeFloat(breasts.yOffset());
        output.writeFloat(breasts.zOffset());
        output.writeBoolean(breasts.uniBoob());
        output.writeFloat(breasts.cleavage());

        writeUVLayouts(config.uvLayouts(), output);
    }

    /** Official default profile used by Beta.4's server-side PlayerConfig. */
    public static ModUser createDefaultUser(UUID userId) {
        return new ModUser(userId, new ModConfiguration(
                new GeneralOptions(GenderIdentities.MALE, true, DEFAULT_VOICE_PITCH),
                new PhysicsOptions(true, true, DEFAULT_BOUNCE_MULTIPLIER, DEFAULT_FLOPPINESS),
                new BreastOptions(DEFAULT_BUST_SIZE, 0.0F, 0.0F, 0.0F, true, 0.0F),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))
        ));
    }

    private static ModUser readBody(CraftInputStream input, UUID userId) throws IOException {
        GenderIdentities gender = readGender(input);
        float bustSize = input.readFloat();
        boolean hurtSounds = input.readBoolean();
        float voicePitch = input.readFloat();

        boolean physicsEnabled = input.readBoolean();
        boolean showInArmor = input.readBoolean();
        float bounceMultiplier = input.readFloat();
        float floppiness = input.readFloat();

        float xOffset = input.readFloat();
        float yOffset = input.readFloat();
        float zOffset = input.readFloat();
        boolean uniBoob = input.readBoolean();
        float cleavage = input.readFloat();
        UVLayouts layouts = readUVLayouts(input);

        validateRange("bustSize", bustSize, 0.0F, 0.8F);
        validateRange("voicePitch", voicePitch, 0.8F, 1.2F);
        validateRange("bounceMultiplier", bounceMultiplier, 0.0F, 0.5F);
        validateRange("floppiness", floppiness, 0.25F, 1.0F);
        validateRange("xOffset", xOffset, -1.0F, 1.0F);
        validateRange("yOffset", yOffset, -1.0F, 1.0F);
        validateRange("zOffset", zOffset, -1.0F, 0.0F);
        validateRange("cleavage", cleavage, 0.0F, 0.1F);

        return new ModUser(userId, new ModConfiguration(
                new GeneralOptions(gender, hurtSounds, voicePitch),
                new PhysicsOptions(physicsEnabled, showInArmor, bounceMultiplier, floppiness),
                new BreastOptions(bustSize, xOffset, yOffset, zOffset, uniBoob, cleavage),
                layouts
        ));
    }

    private static GenderIdentities readGender(CraftInputStream input) throws IOException {
        GenderIdentities[] values = GenderIdentities.values();
        return values[Math.floorMod(input.readVarInt(), values.length)];
    }

    private static UVLayouts readUVLayouts(CraftInputStream input) throws IOException {
        return new UVLayouts(readLayer(input), readLayer(input));
    }

    private static UVLayouts.Layer readLayer(CraftInputStream input) throws IOException {
        return new UVLayouts.Layer(readUVLayout(input), readUVLayout(input));
    }

    private static UVLayout readUVLayout(CraftInputStream input) throws IOException {
        int count = input.readVarInt();
        if (count < 0 || count > MAX_UV_QUADS) {
            throw new IOException("Invalid UV quad count: " + count);
        }

        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        for (int i = 0; i < count; i++) {
            UVDirection direction = readDirection(input);
            quads.put(direction, new UVQuad(
                    input.readVarInt(), input.readVarInt(),
                    input.readVarInt(), input.readVarInt()
            ));
        }
        return new UVLayout(quads);
    }

    private static UVDirection readDirection(CraftInputStream input) throws IOException {
        UVDirection[] values = UVDirection.values();
        return values[Math.floorMod(input.readVarInt(), values.length)];
    }

    private static void writeUVLayouts(UVLayouts layouts, CraftOutputStream output) throws IOException {
        writeLayer(layouts == null ? null : layouts.skin(), output);
        writeLayer(layouts == null ? null : layouts.overlay(), output);
    }

    private static void writeLayer(UVLayouts.Layer layer, CraftOutputStream output) throws IOException {
        writeUVLayout(layer == null ? null : layer.left(), output);
        writeUVLayout(layer == null ? null : layer.right(), output);
    }

    private static void writeUVLayout(UVLayout layout, CraftOutputStream output) throws IOException {
        Map<UVDirection, UVQuad> quads = layout == null ? null : layout.getQuads();
        if (quads == null || quads.isEmpty()) {
            output.writeVarInt(0);
            return;
        }
        if (quads.size() > MAX_UV_QUADS) {
            throw new IOException("Too many UV quads: " + quads.size());
        }

        output.writeVarInt(quads.size());
        for (Map.Entry<UVDirection, UVQuad> entry : quads.entrySet()) {
            output.writeVarInt(entry.getKey().ordinal());
            UVQuad quad = entry.getValue();
            output.writeVarInt(quad.x1());
            output.writeVarInt(quad.y1());
            output.writeVarInt(quad.x2());
            output.writeVarInt(quad.y2());
        }
    }

    private static void validateRange(String name, float value, float minimum, float maximum) throws IOException {
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw new IOException("Invalid " + name + ": " + value);
        }
    }

    private static void ensureFullyRead(CraftInputStream input) throws IOException {
        if (input.available() != 0) {
            throw new IOException("Trailing bytes in sync payload: " + input.available());
        }
    }
}
