package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftOutputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.ModSyncPacketV6;
import dbrighthd.wildfiregendermodplugin.wildfire.ModUser;
import dbrighthd.wildfiregendermodplugin.wildfire.setup.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V6 protocol tests — mod sync protocol v2 (MC 26.2).
 */
class ProtocolTest {

    @Test
    void testDefaultUser() {
        UUID uid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        ModUser user = new ModSyncPacketV6().createDefaultUser(uid);

        assertEquals(uid, user.userId());
        assertEquals(GenderIdentities.MALE, user.configuration().generalOptions().genderIdentity());
        assertTrue(user.configuration().generalOptions().showInArmor());
        assertTrue(user.configuration().generalOptions().holidayThemes());
        assertFalse(user.configuration().physicsOptions().breastPhysics());
        assertEquals(0.6f, user.configuration().breastOptions().bustSize());
    }

    @Test
    void testReadCompactMale() throws IOException {
        UUID uid = UUID.randomUUID();
        byte[] data = new byte[]{0}; // false = compact MALE → default user

        ModUser user = new ModSyncPacketV6().read(CraftInputStream.ofBytes(data), uid);

        assertNotNull(user);
        assertEquals(uid, user.userId());
        assertEquals(GenderIdentities.MALE, user.configuration().generalOptions().genderIdentity());
    }

    @Test
    void testRoundTripFemale() throws IOException {
        UUID uid = UUID.randomUUID();
        GeneralOptions general = new GeneralOptions(
                GenderIdentities.FEMALE, true, 1.2f, true, false);
        PhysicsOptions physics = new PhysicsOptions(true, true, 0.8f, 0.9f);
        BreastOptions breast = new BreastOptions(0.7f, 0.1f, 0.2f, 0.3f, true, 0.4f);

        Map<UVDirection, UVQuad> quads = new EnumMap<>(UVDirection.class);
        quads.put(UVDirection.NORTH, new UVQuad(1, 2, 3, 4));
        UVLayout layout = new UVLayout(quads);
        UVLayouts.Layer layer = new UVLayouts.Layer(layout, layout);
        UVLayouts uvLayouts = new UVLayouts(layer, layer);

        ModUser original = new ModUser(uid, new ModConfiguration(general, physics, breast, uvLayouts));
        ModSyncPacketV6 codec = new ModSyncPacketV6();

        // Serialize (clientbound format)
        byte[] bytes;
        try (var baos = new java.io.ByteArrayOutputStream();
             var out = new CraftOutputStream(baos)) {
            codec.write(original, out);
            bytes = baos.toByteArray();
        }

        // Deserialize via clientbound path
        ModUser decoded = codec.readClientbound(bytes);

        assertEquals(uid, decoded.userId());
        assertEquals(GenderIdentities.FEMALE, decoded.configuration().generalOptions().genderIdentity());
        assertEquals(0.7f, decoded.configuration().breastOptions().bustSize());
        assertEquals(0.8f, decoded.configuration().physicsOptions().buoyancy());
        assertFalse(decoded.configuration().generalOptions().holidayThemes());

        UVQuad quad = decoded.configuration().uvLayouts().skin().left()
                .getQuads().get(UVDirection.NORTH);
        assertNotNull(quad);
        assertEquals(1, quad.x1());
        assertEquals(4, quad.y2());
    }

    @Test
    void testBatchRoundTrip() throws IOException {
        UUID uid1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uid2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Create two users
        ModUser user1 = new ModUser(uid1, new ModConfiguration(
                new GeneralOptions(GenderIdentities.FEMALE, true, 1.0f, true, true),
                new PhysicsOptions(true, true, 0.5f, 0.6f),
                new BreastOptions(0.5f, 0f, 0f, 0f, false, 0f),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))));

        ModUser user2 = new ModUser(uid2, new ModConfiguration(
                new GeneralOptions(GenderIdentities.MALE, true, 1.0f, true, true),
                new PhysicsOptions(false, false, 0.333f, 0.75f),
                new BreastOptions(0.6f, 0f, 0f, 0f, true, 0f),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))));

        ModSyncPacketV6 codec = new ModSyncPacketV6();

        // Write batch
        byte[] batch = codec.writeBatch(List.of(user1, user2));

        // Read batch
        Map<UUID, ModUser> result = codec.readBatch(batch);

        assertEquals(2, result.size());
        assertTrue(result.containsKey(uid1));
        assertTrue(result.containsKey(uid2));

        // User 1 (FEMALE) should have full data
        ModUser d1 = result.get(uid1);
        assertEquals(uid1, d1.userId());
        assertEquals(GenderIdentities.FEMALE, d1.configuration().generalOptions().genderIdentity());

        // User 2 (MALE) should be compact
        ModUser d2 = result.get(uid2);
        assertEquals(uid2, d2.userId());
        assertEquals(GenderIdentities.MALE, d2.configuration().generalOptions().genderIdentity());
    }

    @Test
    void testBatchEmpty() throws IOException {
        ModSyncPacketV6 codec = new ModSyncPacketV6();
        byte[] batch = codec.writeBatch(List.of());
        Map<UUID, ModUser> result = codec.readBatch(batch);
        assertTrue(result.isEmpty());
    }

    @Test
    void testClientboundSingle() throws IOException {
        UUID uid = UUID.randomUUID();
        ModUser user = new ModUser(uid, new ModConfiguration(
                new GeneralOptions(GenderIdentities.FEMALE, true, 0.8f, true, true),
                new PhysicsOptions(true, true, 0.5f, 0.6f),
                new BreastOptions(0.5f, 0f, 0f, 0f, false, 0f),
                new UVLayouts(
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                        new UVLayouts.Layer(new UVLayout(), new UVLayout()))));

        ModSyncPacketV6 codec = new ModSyncPacketV6();
        byte[] encoded = codec.writeClientbound(user);
        ModUser decoded = codec.readClientbound(encoded);

        assertEquals(uid, decoded.userId());
        assertEquals(GenderIdentities.FEMALE, decoded.configuration().generalOptions().genderIdentity());
    }
}
