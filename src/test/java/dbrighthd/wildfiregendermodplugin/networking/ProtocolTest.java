package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV6;
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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the official, single-profile V6 wire format only. */
class ProtocolTest {
    private final ModSyncPacketV6 codec = new ModSyncPacketV6();

    @Test
    void compactMaleUsesOnlyUuidAndFalsePresentFlag() throws IOException {
        UUID uid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        byte[] bytes = codec.writeClientbound(ModSyncPacketV6.createDefaultUser(uid));

        // Clientbound sync is UUID (16 bytes) + PlayerConfig.COMPACT_STREAM_CODEC.
        assertEquals(17, bytes.length);
        assertEquals(0, bytes[16]);

        ModUser decoded = codec.readClientbound(bytes);
        assertEquals(uid, decoded.userId());
        assertEquals(GenderIdentities.MALE, decoded.configuration().generalOptions().genderIdentity());
        assertTrue(decoded.configuration().physicsOptions().breastPhysics());
    }

    @Test
    void serverboundCompactMaleUsesSenderUuid() throws IOException {
        UUID sender = UUID.randomUUID();
        ModUser user = codec.read(CraftInputStream.ofBytes(new byte[]{0}), sender);

        assertEquals(sender, user.userId());
        assertEquals(GenderIdentities.MALE, user.configuration().generalOptions().genderIdentity());
        assertEquals(0.6F, user.configuration().breastOptions().bustSize());
    }

    @Test
    void roundTripFemaleUsesStandardClientboundFormat() throws IOException {
        UUID uid = UUID.randomUUID();
        GeneralOptions general = new GeneralOptions(GenderIdentities.FEMALE, true, 1.2F, true, false);
        PhysicsOptions physics = new PhysicsOptions(true, true, 0.8F, 0.9F);
        BreastOptions breasts = new BreastOptions(0.7F, 0.1F, 0.2F, 0.3F, true, 0.04F);

        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        quads.put(UVDirection.NORTH, new UVQuad(1, 2, 3, 4));
        UVLayout layout = new UVLayout(quads);
        UVLayouts layouts = new UVLayouts(
                new UVLayouts.Layer(layout, layout),
                new UVLayouts.Layer(layout, layout));
        ModUser original = new ModUser(uid, new ModConfiguration(general, physics, breasts, layouts));

        ModUser decoded = codec.readClientbound(codec.writeClientbound(original));
        assertEquals(uid, decoded.userId());
        assertEquals(GenderIdentities.FEMALE, decoded.configuration().generalOptions().genderIdentity());
        assertEquals(0.7F, decoded.configuration().breastOptions().bustSize());
        assertEquals(0.8F, decoded.configuration().physicsOptions().buoyancy());
        assertFalse(decoded.configuration().generalOptions().holidayThemes());

        UVQuad quad = decoded.configuration().uvLayouts().skin().left().getQuads().get(UVDirection.NORTH);
        assertNotNull(quad);
        assertEquals(1, quad.x1());
        assertEquals(4, quad.y2());
    }

    @Test
    void disabledPhysicsDoesNotReadPhysicsFields() throws IOException {
        UUID uid = UUID.randomUUID();
        ModUser original = new ModUser(uid, new ModConfiguration(
                new GeneralOptions(GenderIdentities.OTHER, false, 0.9F, false, true),
                new PhysicsOptions(false, false, 9.9F, 8.8F),
                new BreastOptions(0.5F, 0F, 0F, 0F, false, 0F),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))));

        ModUser decoded = codec.readClientbound(codec.writeClientbound(original));
        assertFalse(decoded.configuration().physicsOptions().breastPhysics());
        assertEquals(0.333F, decoded.configuration().physicsOptions().buoyancy());
        assertEquals(0.75F, decoded.configuration().physicsOptions().floppiness());
    }

    @Test
    void rejectsMoreUvEntriesThanOfficialCodecAllows() {
        // present, gender, five breast floats, physics disabled, then first UV map count = 6
        byte[] malformed = new byte[]{1, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 6};
        assertThrows(IOException.class, () -> codec.read(CraftInputStream.ofBytes(malformed), UUID.randomUUID()));
    }
}
