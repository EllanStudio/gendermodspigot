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
 * Exact V6 / sync-protocol-v2 codec used by Female Gender Mod for MC 26.2.
 *
 * <p>This class intentionally supports only the mod's standard single-profile
 * payloads. A normal clientbound sync packet is {@code UUID + compact config};
 * inventing a batch prefix would make the official client reject the packet.</p>
 */
public final class ModSyncPacketV6 {
    private static final float DEFAULT_BUOYANCY = 0.333F;
    private static final float DEFAULT_FLOPPINESS = 0.75F;
    private static final int MAX_UV_QUADS = UVDirection.values().length;

    /** Reads the mod's serverbound compact config (the UUID comes from Bukkit). */
    public ModUser read(CraftInputStream input, UUID senderId) throws IOException {
        if (!input.readBoolean()) {
            return createDefaultUser(senderId);
        }
        return readFull(input, senderId);
    }

    /** Reads a normal clientbound V6 packet: UUID followed by compact config. */
    public ModUser readClientbound(byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            UUID userId = input.readUUID();
            return read(input, userId);
        }
    }

    /** Encodes the exact normal clientbound V6 packet expected by the mod. */
    public byte[] writeClientbound(ModUser user) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(bytes)) {
            write(user, output);
            return bytes.toByteArray();
        }
    }

    /** Writes the exact normal clientbound V6 packet: UUID plus compact config. */
    public void write(ModUser user, CraftOutputStream output) throws IOException {
        output.writeUUID(user.userId());
        writeCompactConfiguration(user.configuration(), output);
    }

    /** Official compact-MALE default used when the packet present flag is false. */
    public static ModUser createDefaultUser(UUID userId) {
        return new ModUser(userId, new ModConfiguration(
                new GeneralOptions(GenderIdentities.MALE, true, 1.0F, true, true),
                new PhysicsOptions(true, true, DEFAULT_BUOYANCY, DEFAULT_FLOPPINESS),
                new BreastOptions(0.6F, 0.0F, 0.0F, 0.0F, true, 0.0F),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))
        ));
    }

    private static void writeCompactConfiguration(ModConfiguration config, CraftOutputStream output) throws IOException {
        GeneralOptions general = config.generalOptions();
        if (general.genderIdentity() == GenderIdentities.MALE) {
            output.writeBoolean(false);
            return;
        }

        output.writeBoolean(true);
        output.writeEnum(general.genderIdentity());

        BreastOptions breasts = config.breastOptions();
        output.writeFloat(breasts.xOffset());
        output.writeFloat(breasts.yOffset());
        output.writeFloat(breasts.zOffset());
        output.writeFloat(breasts.bustSize());
        output.writeFloat(breasts.cleavage());

        PhysicsOptions physics = config.physicsOptions();
        output.writeBoolean(physics.breastPhysics());
        if (physics.breastPhysics()) {
            output.writeBoolean(breasts.uniBoob());
            output.writeFloat(physics.buoyancy());
            output.writeFloat(physics.floppiness());
        }

        writeUVLayouts(config.uvLayouts(), output);
        output.writeBoolean(general.hurtSounds());
        output.writeFloat(general.voicePitch());
        output.writeBoolean(general.showInArmor());
        output.writeBoolean(general.holidayThemes());
    }

    private static ModUser readFull(CraftInputStream input, UUID userId) throws IOException {
        GenderIdentities gender = readGender(input);
        float xOffset = input.readFloat();
        float yOffset = input.readFloat();
        float zOffset = input.readFloat();
        float bustSize = input.readFloat();
        float cleavage = input.readFloat();

        boolean physicsEnabled = input.readBoolean();
        boolean uniBoob = physicsEnabled && input.readBoolean();
        float buoyancy = physicsEnabled ? input.readFloat() : DEFAULT_BUOYANCY;
        float floppiness = physicsEnabled ? input.readFloat() : DEFAULT_FLOPPINESS;

        UVLayouts uvLayouts = readUVLayouts(input);
        boolean hurtSounds = input.readBoolean();
        float voicePitch = input.readFloat();
        boolean showInArmor = input.readBoolean();
        boolean holidayThemes = input.readBoolean();

        return new ModUser(userId, new ModConfiguration(
                new GeneralOptions(gender, hurtSounds, voicePitch, showInArmor, holidayThemes),
                new PhysicsOptions(physicsEnabled, physicsEnabled, buoyancy, floppiness),
                new BreastOptions(bustSize, xOffset, yOffset, zOffset, uniBoob, cleavage),
                uvLayouts
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
}
