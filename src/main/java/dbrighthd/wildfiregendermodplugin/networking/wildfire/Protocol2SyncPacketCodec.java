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
 * Female Gender Mod 5.0.0-Beta.5 / 5.0.0 compact sync protocol (version 2).
 *
 * <p>The official stream codec is UUID + compact AvatarConfig for clientbound
 * packets and compact AvatarConfig only for serverbound packets. A compact
 * config starts with a present flag; a male config writes only {@code false}.
 * For non-male configs the field order is gender, breasts, UVs, sounds, and
 * show-in-armor, with the physics payload itself conditionally omitted when
 * physics is disabled.</p>
 */
public final class Protocol2SyncPacketCodec {
    private static final float DEFAULT_BUST_SIZE = 0.6F;
    private static final float DEFAULT_VOICE_PITCH = 1.0F;
    private static final float DEFAULT_BOUNCE_MULTIPLIER = 0.333F;
    private static final float DEFAULT_FLOPPINESS = 0.75F;
    private static final int MAX_UV_QUADS = UVDirection.values().length;

    /** Reads a protocol-2 serverbound compact profile for the given sender. */
    public ModUser readServerbound(byte[] data, UUID senderId) throws IOException {
        if (data.length > 32 * 1024) {
            throw new IOException("Sync payload is too large");
        }
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            ModUser user = readCompact(input, senderId);
            ensureFullyRead(input);
            return user;
        }
    }

    /** Reads a protocol-2 clientbound UUID-first compact profile. */
    public ModUser readClientbound(byte[] data) throws IOException {
        if (data.length > 32 * 1024) {
            throw new IOException("Sync payload is too large");
        }
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            UUID userId = input.readUUID();
            ModUser user = readCompact(input, userId);
            ensureFullyRead(input);
            return user;
        }
    }

    /** Encodes a protocol-2 clientbound UUID-first compact profile. */
    public byte[] writeClientbound(ModUser user) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            output.writeUUID(user.userId());
            writeCompact(output, user);
            return bytes.toByteArray();
        }
    }

    /** Encodes a protocol-2 serverbound compact profile (without UUID). */
    public byte[] writeServerbound(ModUser user) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            writeCompact(output, user);
            return bytes.toByteArray();
        }
    }

    private static void writeCompact(CraftOutputStream output, ModUser user) throws IOException {
        ModConfiguration config = user.configuration();
        GeneralOptions general = config.generalOptions();
        if (general.genderIdentity() == GenderIdentities.MALE) {
            output.writeBoolean(false);
            return;
        }

        BreastOptions breasts = config.breastOptions();
        PhysicsOptions physics = config.physicsOptions();
        output.writeBoolean(true);
        output.writeEnum(general.genderIdentity());
        output.writeFloat(breasts.xOffset());
        output.writeFloat(breasts.yOffset());
        output.writeFloat(breasts.zOffset());
        output.writeFloat(breasts.bustSize());
        output.writeFloat(breasts.cleavage());

        // Official Breasts.Physics.STREAM_CODEC: a disabled physics block
        // contains only false; the remaining three values are omitted.
        output.writeBoolean(physics.breastPhysics());
        if (physics.breastPhysics()) {
            output.writeBoolean(breasts.uniBoob());
            output.writeFloat(physics.bounceMultiplier());
            output.writeFloat(physics.floppiness());
        }

        writeUVLayouts(config.uvLayouts(), output);
        output.writeBoolean(general.hurtSounds());
        output.writeFloat(general.voicePitch());
        output.writeBoolean(physics.showInArmor());
    }

    private static ModUser readCompact(CraftInputStream input, UUID userId) throws IOException {
        if (!input.readBoolean()) {
            return createDefaultUser(userId);
        }

        GenderIdentities gender = readGender(input);
        float xOffset = input.readFloat();
        float yOffset = input.readFloat();
        float zOffset = input.readFloat();
        float bustSize = input.readFloat();
        float cleavage = input.readFloat();

        boolean physicsEnabled = input.readBoolean();
        boolean uniBoob = true;
        float bounceMultiplier = DEFAULT_BOUNCE_MULTIPLIER;
        float floppiness = DEFAULT_FLOPPINESS;
        if (physicsEnabled) {
            uniBoob = input.readBoolean();
            bounceMultiplier = input.readFloat();
            floppiness = input.readFloat();
        }

        UVLayouts layouts = readUVLayouts(input);
        boolean hurtSounds = input.readBoolean();
        float voicePitch = input.readFloat();
        boolean showInArmor = input.readBoolean();

        validateRange("bustSize", bustSize, 0.0F, 0.8F);
        validateRange("voicePitch", voicePitch, 0.8F, 1.2F);
        validateRange("xOffset", xOffset, -1.0F, 1.0F);
        validateRange("yOffset", yOffset, -1.0F, 1.0F);
        validateRange("zOffset", zOffset, -1.0F, 0.0F);
        validateRange("cleavage", cleavage, 0.0F, 0.1F);
        validateRange("bounceMultiplier", bounceMultiplier, 0.0F, 0.5F);
        validateRange("floppiness", floppiness, 0.25F, 1.0F);

        return new ModUser(userId, new ModConfiguration(
                new GeneralOptions(gender, hurtSounds, voicePitch),
                new PhysicsOptions(physicsEnabled, showInArmor, bounceMultiplier, floppiness),
                new BreastOptions(bustSize, xOffset, yOffset, zOffset, uniBoob, cleavage),
                layouts));
    }

    public static ModUser createDefaultUser(UUID userId) {
        return new ModUser(userId, new ModConfiguration(
                new GeneralOptions(GenderIdentities.MALE, true, DEFAULT_VOICE_PITCH),
                new PhysicsOptions(true, true, DEFAULT_BOUNCE_MULTIPLIER, DEFAULT_FLOPPINESS),
                new BreastOptions(DEFAULT_BUST_SIZE, 0.0F, 0.0F, 0.0F, true, 0.0F),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))));
    }

    private static GenderIdentities readGender(CraftInputStream input) throws IOException {
        int ordinal = input.readVarInt();
        GenderIdentities[] values = GenderIdentities.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IOException("Invalid gender ordinal: " + ordinal);
        }
        return values[ordinal];
    }

    private static UVLayouts readUVLayouts(CraftInputStream input) throws IOException {
        return new UVLayouts(readLayer(input), readLayer(input));
    }

    private static UVLayouts.Layer readLayer(CraftInputStream input) throws IOException {
        return new UVLayouts.Layer(readUVLayout(input), readUVLayout(input));
    }

    /** Matches ByteBufCodecs.map with EnumMap iteration and a five-entry cap. */
    private static UVLayout readUVLayout(CraftInputStream input) throws IOException {
        int count = input.readVarInt();
        if (count < 0 || count > MAX_UV_QUADS) {
            throw new IOException("Invalid UV map size: " + count);
        }
        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        for (int i = 0; i < count; i++) {
            int ordinal = input.readVarInt();
            UVDirection[] values = UVDirection.values();
            if (ordinal < 0 || ordinal >= values.length || quads.containsKey(values[ordinal])) {
                throw new IOException("Invalid or duplicate UV direction: " + ordinal);
            }
            quads.put(values[ordinal], new UVQuad(
                    input.readVarInt(), input.readVarInt(), input.readVarInt(), input.readVarInt()));
        }
        return new UVLayout(quads);
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
            throw new IOException("Too many UV entries: " + quads.size());
        }
        output.writeVarInt(quads.size());
        // EnumMap iteration is ordinal order, the same order used by the client.
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
