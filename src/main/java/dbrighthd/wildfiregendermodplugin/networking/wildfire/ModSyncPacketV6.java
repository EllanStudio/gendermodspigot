package dbrighthd.wildfiregendermodplugin.networking.wildfire;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.*;

import java.io.IOException;
import java.util.*;

/**
 * V6 packet format — mod sync protocol v2 (MC 26.2, mod 5.0.0-Beta.4+).
 * <p>
 * Single-user formats (for client-server point-to-point):
 * <ul>
 *   <li>{@link #read} — reads serverbound payload (boolean present, then full config or default)</li>
 *   <li>{@link #readClientbound} — reads clientbound payload (UUID + config, for cross-server)</li>
 *   <li>{@link #write} — writes clientbound payload (UUID + config)</li>
 * </ul>
 * <p>
 * Batch formats (for server broadcast, single packet carrying multiple users):
 * <ul>
 *   <li>{@link #writeBatch} — writes multiple users into one consolidated packet</li>
 *   <li>{@link #readBatch} — reads a batch packet containing multiple users</li>
 * </ul>
 */
public final class ModSyncPacketV6 {

    public static final int VERSION = 6;

    public ModSyncPacketV6() {}

    // ===== Single-user serverbound (client → server) =====

    public ModUser read(CraftInputStream input, UUID senderId) throws IOException {
        if (!input.readBoolean()) {
            return createDefaultUser(senderId);
        }
        return readFull(input, senderId);
    }

    // ===== Single-user clientbound (for cross-server forwarding) =====

    public ModUser readClientbound(byte[] data) throws IOException {
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            UUID uuid = input.readUUID();
            if (!input.readBoolean()) {
                return createDefaultUser(uuid);
            }
            return readFull(input, uuid);
        }
    }

    public byte[] writeClientbound(ModUser user) throws IOException {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(baos)) {
            write(user, output);
            return baos.toByteArray();
        }
    }

    /**
     * Writes clientbound payload: UUID + compact config.
     * MALE players use the compact path (boolean=false).
     */
    public void write(ModUser user, CraftOutputStream output) throws IOException {
        ModConfiguration cfg = user.configuration();
        GeneralOptions g = cfg.generalOptions();
        PhysicsOptions p = cfg.physicsOptions();
        BreastOptions b = cfg.breastOptions();
        UVLayouts u = cfg.uvLayouts();

        output.writeUUID(user.userId());
        boolean male = g.genderIdentity() == GenderIdentities.MALE;
        output.writeBoolean(!male);
        if (male) return;

        output.writeEnum(g.genderIdentity());
        output.writeFloat(b.xOffset());
        output.writeFloat(b.yOffset());
        output.writeFloat(b.zOffset());
        output.writeFloat(b.bustSize());
        output.writeFloat(b.cleavage());
        output.writeBoolean(p.breastPhysics());
        if (p.breastPhysics()) {
            output.writeBoolean(b.uniBoob());
            output.writeFloat(p.buoyancy());
            output.writeFloat(p.floppiness());
        }
        writeUVLayouts(u, output);
        output.writeBoolean(g.hurtSounds());
        output.writeFloat(g.voicePitch());
        output.writeBoolean(g.showInArmor());
        output.writeBoolean(g.holidayThemes());
    }

    // ===== Batch broadcast (server → all clients, single packet) =====

    /**
     * Writes multiple users into a single consolidated broadcast packet.
     * Format: VarInt(count) + [UUID + present + data] * count
     */
    public byte[] writeBatch(Collection<ModUser> users) throws IOException {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             CraftOutputStream output = new CraftOutputStream(baos)) {
            output.writeVarInt(users.size());
            for (ModUser user : users) {
                write(user, output);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Reads a batch broadcast packet into a map of UUID → ModUser.
     */
    public Map<UUID, ModUser> readBatch(byte[] data) throws IOException {
        Map<UUID, ModUser> users = new LinkedHashMap<>();
        try (CraftInputStream input = CraftInputStream.ofBytes(data)) {
            int count = input.readVarInt();
            for (int i = 0; i < count; i++) {
                UUID uuid = input.readUUID();
                if (!input.readBoolean()) {
                    users.put(uuid, createDefaultUser(uuid));
                } else {
                    users.put(uuid, readFull(input, uuid));
                }
            }
        }
        return users;
    }

    // ===== Default factory =====

    public ModUser createDefaultUser(UUID uuid) {
        return new ModUser(uuid, new ModConfiguration(
                new GeneralOptions(GenderIdentities.MALE, true, 1.0f, true, true),
                new PhysicsOptions(false, false, 0.333f, 0.75f),
                new BreastOptions(0.6f, 0.0f, 0.0f, 0.0f, true, 0.0f),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))));
    }

    // ===== Private helpers =====

    private ModUser readFull(CraftInputStream input, UUID uuid) throws IOException {
        GenderIdentities gender = input.readEnum(GenderIdentities.class);
        float xOffset = input.readFloat();
        float yOffset = input.readFloat();
        float zOffset = input.readFloat();
        float bustSize = input.readFloat();
        float cleavage = input.readFloat();
        boolean physicsEnabled = input.readBoolean();
        boolean uniboob = physicsEnabled && input.readBoolean();
        float buoyancy = physicsEnabled ? input.readFloat() : 0.333f;
        float floppiness = physicsEnabled ? input.readFloat() : 0.75f;
        UVLayouts uvLayouts = readUVLayouts(input);
        boolean hurtSounds = input.readBoolean();
        float voicePitch = input.readFloat();
        boolean showInArmor = input.readBoolean();
        boolean holidayThemes = input.readBoolean();

        return new ModUser(uuid, new ModConfiguration(
                new GeneralOptions(gender, hurtSounds, voicePitch, showInArmor, holidayThemes),
                new PhysicsOptions(physicsEnabled, physicsEnabled, buoyancy, floppiness),
                new BreastOptions(bustSize, xOffset, yOffset, zOffset, uniboob, cleavage),
                uvLayouts));
    }

    private UVLayouts readUVLayouts(CraftInputStream input) throws IOException {
        return new UVLayouts(readLayer(input), readLayer(input));
    }

    private UVLayouts.Layer readLayer(CraftInputStream input) throws IOException {
        return new UVLayouts.Layer(readUVLayout(input), readUVLayout(input));
    }

    private UVLayout readUVLayout(CraftInputStream input) throws IOException {
        int count = input.readVarInt();
        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        for (int i = 0; i < count; i++) {
            quads.put(UVDirection.byId(input.readVarInt()),
                    new UVQuad(input.readVarInt(), input.readVarInt(),
                               input.readVarInt(), input.readVarInt()));
        }
        return new UVLayout(quads);
    }

    private void writeUVLayouts(UVLayouts uv, CraftOutputStream out) throws IOException {
        writeLayer(uv != null ? uv.skin() : null, out);
        writeLayer(uv != null ? uv.overlay() : null, out);
    }

    private void writeLayer(UVLayouts.Layer layer, CraftOutputStream out) throws IOException {
        writeUVLayout(layer != null ? layer.left() : null, out);
        writeUVLayout(layer != null ? layer.right() : null, out);
    }

    private void writeUVLayout(UVLayout layout, CraftOutputStream out) throws IOException {
        Map<UVDirection, UVQuad> quads = layout != null ? layout.getQuads() : null;
        if (quads == null || quads.isEmpty()) {
            out.writeVarInt(0);
            return;
        }
        out.writeVarInt(quads.size());
        for (Map.Entry<UVDirection, UVQuad> e : quads.entrySet()) {
            out.writeVarInt(e.getKey().ordinal());
            UVQuad q = e.getValue();
            out.writeVarInt(q.x1());
            out.writeVarInt(q.y1());
            out.writeVarInt(q.x2());
            out.writeVarInt(q.y2());
        }
    }
}
