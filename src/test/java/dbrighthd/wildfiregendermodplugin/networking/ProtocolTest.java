package dbrighthd.wildfiregendermodplugin.networking;

import dbrighthd.wildfiregendermodplugin.networking.minecraft.CraftInputStream;
import dbrighthd.wildfiregendermodplugin.networking.wildfire.Beta4SyncPacketCodec;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
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

/** Regression tests copied from the 5.0.0-Beta.4 AbstractSyncPacket field order. */
class ProtocolTest {
    private final Beta4SyncPacketCodec codec = new Beta4SyncPacketCodec();

    @Test
    void usesExactBeta4ChannelsAndHelloVersion() {
        assertEquals("wildfire_gender:serverbound/hello", ModConstants.SERVERBOUND_HELLO);
        assertEquals("wildfire_gender:clientbound/hello", ModConstants.CLIENTBOUND_HELLO);
        assertEquals("wildfire_gender:send_gender_info", ModConstants.SERVERBOUND_SYNC);
        assertEquals("wildfire_gender:sync", ModConstants.CLIENTBOUND_SYNC);
        assertEquals(1, ModConstants.SYNC_PROTOCOL_VERSION);
    }

    @Test
    void defaultPacketMatchesOfficialFixedFieldOrder() throws IOException {
        UUID uid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        byte[] bytes = codec.writeClientbound(Beta4SyncPacketCodec.createDefaultUser(uid));

        // 53 fixed bytes plus four empty UV-map VarInts. Beta.4 has no compact flag.
        assertEquals(57, bytes.length);
        try (CraftInputStream input = CraftInputStream.ofBytes(bytes)) {
            assertEquals(uid, input.readUUID());
            assertEquals(GenderIdentities.MALE.ordinal(), input.readVarInt());
            assertEquals(0.6F, input.readFloat());
            assertTrue(input.readBoolean());
            assertEquals(1.0F, input.readFloat());
            assertTrue(input.readBoolean());
            assertTrue(input.readBoolean());
            assertEquals(0.333F, input.readFloat());
            assertEquals(0.75F, input.readFloat());
            assertEquals(0.0F, input.readFloat());
            assertEquals(0.0F, input.readFloat());
            assertEquals(0.0F, input.readFloat());
            assertTrue(input.readBoolean());
            assertEquals(0.0F, input.readFloat());
            assertEquals(0, input.readVarInt());
            assertEquals(0, input.readVarInt());
            assertEquals(0, input.readVarInt());
            assertEquals(0, input.readVarInt());
            assertEquals(0, input.available());
        }
    }

    @Test
    void roundTripFemaleProfileIncludingUvLayouts() throws IOException {
        UUID uid = UUID.randomUUID();
        GeneralOptions general = new GeneralOptions(GenderIdentities.FEMALE, true, 1.2F);
        PhysicsOptions physics = new PhysicsOptions(true, true, 0.4F, 0.9F);
        BreastOptions breasts = new BreastOptions(0.7F, 0.1F, 0.2F, -0.3F, true, 0.04F);

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
        assertEquals(-0.3F, decoded.configuration().breastOptions().zOffset());
        assertEquals(0.4F, decoded.configuration().physicsOptions().bounceMultiplier());
        assertEquals(0.9F, decoded.configuration().physicsOptions().floppiness());
        assertTrue(decoded.configuration().physicsOptions().showInArmor());


        UVQuad quad = decoded.configuration().uvLayouts().skin().left().getQuads().get(UVDirection.NORTH);
        assertNotNull(quad);
        assertEquals(1, quad.x1());
        assertEquals(4, quad.y2());
    }

    @Test
    void physicsFieldsAreAlwaysPresentEvenWhenDisabled() throws IOException {
        UUID uid = UUID.randomUUID();
        ModUser original = new ModUser(uid, new ModConfiguration(
                new GeneralOptions(GenderIdentities.OTHER, false, 0.9F),
                new PhysicsOptions(false, false, 0.2F, 0.5F),
                new BreastOptions(0.5F, 0F, 0F, -0.2F, false, 0F),
                emptyLayouts()));

        byte[] bytes = codec.writeClientbound(original);
        assertEquals(57, bytes.length);
        ModUser decoded = codec.readClientbound(bytes);
        assertFalse(decoded.configuration().physicsOptions().breastPhysics());
        assertFalse(decoded.configuration().physicsOptions().showInArmor());
        assertEquals(0.2F, decoded.configuration().physicsOptions().bounceMultiplier());
        assertEquals(0.5F, decoded.configuration().physicsOptions().floppiness());
    }

    @Test
    void serverboundPacketContainsAndMustMatchSenderUuid() throws IOException {
        UUID sender = UUID.randomUUID();
        byte[] bytes = codec.writeClientbound(Beta4SyncPacketCodec.createDefaultUser(sender));

        ModUser decoded = codec.read(CraftInputStream.ofBytes(bytes), sender);
        assertEquals(sender, decoded.userId());
        assertThrows(IOException.class,
                () -> codec.read(CraftInputStream.ofBytes(bytes), UUID.randomUUID()));
    }

    @Test
    void rejectsProtocol2CompactPayload() {
        assertThrows(IOException.class,
                () -> codec.read(CraftInputStream.ofBytes(new byte[]{0}), UUID.randomUUID()));
    }

    @Test
    void rejectsMoreUvEntriesThanOfficialCodecAllows() throws IOException {
        byte[] malformed = codec.writeClientbound(Beta4SyncPacketCodec.createDefaultUser(UUID.randomUUID()));
        malformed[53] = 6; // First UV map count; UVDirection has exactly five values.
        assertThrows(IOException.class, () -> codec.readClientbound(malformed));
    }

    private static UVLayouts emptyLayouts() {
        return new UVLayouts(
                new UVLayouts.Layer(new UVLayout(), new UVLayout()),
                new UVLayouts.Layer(new UVLayout(), new UVLayout()));
    }
}
