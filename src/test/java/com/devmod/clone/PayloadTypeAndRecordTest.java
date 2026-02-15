package com.devmod.clone;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import com.devmod.clone.network.MannequinRotationPayload;
import com.devmod.clone.network.MannequinSkinPayload;
import com.devmod.clone.network.TelepadConfigPayload;
import com.devmod.clone.network.TelepadOpenScreenPayload;

/**
 * Tests for payload TYPE constants, record accessors, and STREAM_CODEC presence.
 */
class PayloadTypeAndRecordTest {

    @Nested
    @DisplayName("MannequinRotationPayload")
    class MannequinRotationTests {

        @Test
        @DisplayName("TYPE is not null and has correct id")
        void typeNotNull() {
            assertNotNull(MannequinRotationPayload.TYPE);
            assertTrue(MannequinRotationPayload.TYPE.id().toString().contains("mannequin_rotation"));
        }

        @Test
        @DisplayName("STREAM_CODEC is not null")
        void streamCodecNotNull() {
            assertNotNull(MannequinRotationPayload.STREAM_CODEC);
        }

        @Test
        @DisplayName("Record accessors work")
        void recordAccessors() {
            var pos = new BlockPos(1, 2, 3);
            var payload = new MannequinRotationPayload(pos, 45.0f);
            assertEquals(pos, payload.pos());
            assertEquals(45.0f, payload.rotationDelta(), 0.001f);
        }

        @Test
        @DisplayName("type() returns TYPE")
        void typeMethod() {
            var payload = new MannequinRotationPayload(BlockPos.ZERO, 0f);
            assertSame(MannequinRotationPayload.TYPE, payload.type());
        }

        @Test
        @DisplayName("Record equality")
        void equality() {
            var a = new MannequinRotationPayload(new BlockPos(1, 2, 3), 10.0f);
            var b = new MannequinRotationPayload(new BlockPos(1, 2, 3), 10.0f);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }

    @Nested
    @DisplayName("MannequinSkinPayload")
    class MannequinSkinTests {

        @Test
        @DisplayName("TYPE is not null and has correct id")
        void typeNotNull() {
            assertNotNull(MannequinSkinPayload.TYPE);
            assertTrue(MannequinSkinPayload.TYPE.id().toString().contains("mannequin_skin"));
        }

        @Test
        @DisplayName("STREAM_CODEC is not null")
        void streamCodecNotNull() {
            assertNotNull(MannequinSkinPayload.STREAM_CODEC);
        }

        @Test
        @DisplayName("Record accessors with UUID")
        void recordWithUuid() {
            UUID uuid = UUID.randomUUID();
            var payload = new MannequinSkinPayload(new BlockPos(5, 10, 15), uuid);
            assertEquals(uuid, payload.skinUUID());
            assertEquals(new BlockPos(5, 10, 15), payload.pos());
        }

        @Test
        @DisplayName("Record accessors with null UUID")
        void recordWithNullUuid() {
            var payload = new MannequinSkinPayload(BlockPos.ZERO, null);
            assertNull(payload.skinUUID());
        }

        @Test
        @DisplayName("type() returns TYPE")
        void typeMethod() {
            var payload = new MannequinSkinPayload(BlockPos.ZERO, null);
            assertSame(MannequinSkinPayload.TYPE, payload.type());
        }
    }

    @Nested
    @DisplayName("TelepadConfigPayload")
    class TelepadConfigTests {

        @Test
        @DisplayName("TYPE is not null and has correct id")
        void typeNotNull() {
            assertNotNull(TelepadConfigPayload.TYPE);
            assertTrue(TelepadConfigPayload.TYPE.id().toString().contains("telepad_config"));
        }

        @Test
        @DisplayName("MAX_TELEPAD_NAME_LENGTH is 32")
        void maxNameLength() {
            assertEquals(32, TelepadConfigPayload.MAX_TELEPAD_NAME_LENGTH);
        }

        @Test
        @DisplayName("STREAM_CODEC is not null")
        void streamCodecNotNull() {
            assertNotNull(TelepadConfigPayload.STREAM_CODEC);
        }

        @Test
        @DisplayName("Record accessors")
        void recordAccessors() {
            var payload = new TelepadConfigPayload(new BlockPos(100, 200, 300), "base");
            assertEquals(new BlockPos(100, 200, 300), payload.pos());
            assertEquals("base", payload.telepadName());
        }

        @Test
        @DisplayName("type() returns TYPE")
        void typeMethod() {
            var payload = new TelepadConfigPayload(BlockPos.ZERO, "");
            assertSame(TelepadConfigPayload.TYPE, payload.type());
        }
    }

    @Nested
    @DisplayName("TelepadOpenScreenPayload")
    class TelepadOpenScreenTests {

        @Test
        @DisplayName("TYPE is not null and has correct id")
        void typeNotNull() {
            assertNotNull(TelepadOpenScreenPayload.TYPE);
            assertTrue(TelepadOpenScreenPayload.TYPE.id().toString().contains("telepad_open_screen"));
        }

        @Test
        @DisplayName("STREAM_CODEC is not null")
        void streamCodecNotNull() {
            assertNotNull(TelepadOpenScreenPayload.STREAM_CODEC);
        }

        @Test
        @DisplayName("Record accessors")
        void recordAccessors() {
            var payload = new TelepadOpenScreenPayload(new BlockPos(-5, 0, 5), "lobby");
            assertEquals(new BlockPos(-5, 0, 5), payload.pos());
            assertEquals("lobby", payload.telepadName());
        }

        @Test
        @DisplayName("type() returns TYPE")
        void typeMethod() {
            var payload = new TelepadOpenScreenPayload(BlockPos.ZERO, "");
            assertSame(TelepadOpenScreenPayload.TYPE, payload.type());
        }
    }

    @Nested
    @DisplayName("Cross-payload consistency")
    class CrossPayloadTests {

        @Test
        @DisplayName("All payload TYPE ids contain devmod namespace")
        void allTypesHaveDevmodNamespace() {
            assertTrue(MannequinRotationPayload.TYPE.id().getNamespace().equals("devmod"));
            assertTrue(MannequinSkinPayload.TYPE.id().getNamespace().equals("devmod"));
            assertTrue(TelepadConfigPayload.TYPE.id().getNamespace().equals("devmod"));
            assertTrue(TelepadOpenScreenPayload.TYPE.id().getNamespace().equals("devmod"));
        }

        @Test
        @DisplayName("All TYPE ids are unique")
        void allTypesUnique() {
            var ids = java.util.Set.of(
                MannequinRotationPayload.TYPE.id(),
                MannequinSkinPayload.TYPE.id(),
                TelepadConfigPayload.TYPE.id(),
                TelepadOpenScreenPayload.TYPE.id()
            );
            assertEquals(4, ids.size(), "All payload TYPE ids should be unique");
        }
    }
}
