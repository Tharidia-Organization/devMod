package com.devmod.collision.bodypart;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import com.devmod.collision.obb.OrientedBoundingBox;
import com.devmod.shared.BodyPart;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BodyPartDefinition")
class BodyPartDefinitionTest {

    private static final double EPS = 1e-6;

    private static BodyPartDefinition sampleDef() {
        return new BodyPartDefinition(
            "test_part", BodyPart.BODY,
            new Vec3(0, 0.9, 0), new Vec3(0.2, 0.3, 0.1),
            "body", null, 0xFF00FF00, 1.0f
        );
    }

    @Nested
    @DisplayName("record fields")
    class RecordFieldsTests {

        @Test
        @DisplayName("stores all fields correctly")
        void basicFields() {
            BodyPartDefinition def = sampleDef();
            assertEquals("test_part", def.id());
            assertEquals(BodyPart.BODY, def.bodyPartType());
            assertEquals(new Vec3(0, 0.9, 0), def.localOffset());
            assertEquals(new Vec3(0.2, 0.3, 0.1), def.halfExtents());
            assertEquals("body", def.parentBoneId());
            assertNull(def.parentPartId());
            assertEquals(0xFF00FF00, def.renderColor());
            assertEquals(1.0f, def.damageMultiplier());
        }

        @Test
        @DisplayName("null parentBoneId and parentPartId are allowed")
        void nullParentFields() {
            BodyPartDefinition def = new BodyPartDefinition(
                "root", BodyPart.BODY,
                Vec3.ZERO, new Vec3(1, 1, 1),
                null, null, 0, 1.0f
            );
            assertNull(def.parentBoneId());
            assertNull(def.parentPartId());
        }
    }

    @Nested
    @DisplayName("isRoot / hasBone")
    class RootBoneTests {

        @Test
        @DisplayName("isRoot returns true when parentPartId is null")
        void isRootTrue() {
            BodyPartDefinition def = sampleDef();
            assertTrue(def.isRoot());
        }

        @Test
        @DisplayName("isRoot returns false when parentPartId is set")
        void isRootFalse() {
            BodyPartDefinition def = new BodyPartDefinition(
                "child", BodyPart.HEAD,
                Vec3.ZERO, new Vec3(0.2, 0.2, 0.2),
                "head", "body", 0, 2.0f
            );
            assertFalse(def.isRoot());
        }

        @Test
        @DisplayName("hasBone returns true when parentBoneId is set")
        void hasBoneTrue() {
            assertTrue(sampleDef().hasBone());
        }

        @Test
        @DisplayName("hasBone returns false when parentBoneId is null")
        void hasBoneFalse() {
            BodyPartDefinition def = new BodyPartDefinition(
                "root", BodyPart.BODY,
                Vec3.ZERO, new Vec3(1, 1, 1),
                null, null, 0, 1.0f
            );
            assertFalse(def.hasBone());
        }
    }

    @Nested
    @DisplayName("getFullSize")
    class FullSizeTests {

        @Test
        @DisplayName("returns double of halfExtents")
        void fullSize() {
            BodyPartDefinition def = sampleDef();
            Vec3 full = def.getFullSize();
            assertEquals(0.4, full.x, EPS);
            assertEquals(0.6, full.y, EPS);
            assertEquals(0.2, full.z, EPS);
        }
    }

    @Nested
    @DisplayName("copy methods")
    class CopyTests {

        @Test
        @DisplayName("withDamageMultiplier creates copy with new multiplier")
        void withDamageMultiplier() {
            BodyPartDefinition original = sampleDef();
            BodyPartDefinition modified = original.withDamageMultiplier(2.5f);

            assertEquals(2.5f, modified.damageMultiplier());
            assertEquals(original.id(), modified.id());
            assertEquals(original.bodyPartType(), modified.bodyPartType());
            assertEquals(original.localOffset(), modified.localOffset());
            assertEquals(original.halfExtents(), modified.halfExtents());
        }

        @Test
        @DisplayName("withHalfExtents creates copy with new half extents")
        void withHalfExtents() {
            BodyPartDefinition original = sampleDef();
            Vec3 newExtents = new Vec3(0.5, 0.5, 0.5);
            BodyPartDefinition modified = original.withHalfExtents(newExtents);

            assertEquals(newExtents, modified.halfExtents());
            assertEquals(original.id(), modified.id());
            assertEquals(original.damageMultiplier(), modified.damageMultiplier());
        }

        @Test
        @DisplayName("withOffset creates copy with new offset")
        void withOffset() {
            BodyPartDefinition original = sampleDef();
            Vec3 newOffset = new Vec3(1, 2, 3);
            BodyPartDefinition modified = original.withOffset(newOffset);

            assertEquals(newOffset, modified.localOffset());
            assertEquals(original.id(), modified.id());
            assertEquals(original.halfExtents(), modified.halfExtents());
        }
    }

    @Nested
    @DisplayName("createBaseOBB / createOriginOBB")
    class OBBCreationTests {

        @Test
        @DisplayName("createBaseOBB uses localOffset as center")
        void baseOBB() {
            BodyPartDefinition def = sampleDef();
            OrientedBoundingBox obb = def.createBaseOBB();

            assertEquals(def.localOffset(), obb.getCenter());
            assertEquals(def.halfExtents(), obb.getHalfExtents());
            assertFalse(obb.isRotated());
        }

        @Test
        @DisplayName("createOriginOBB uses Vec3.ZERO as center")
        void originOBB() {
            BodyPartDefinition def = sampleDef();
            OrientedBoundingBox obb = def.createOriginOBB();

            assertEquals(Vec3.ZERO, obb.getCenter());
            assertEquals(def.halfExtents(), obb.getHalfExtents());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builder creates definition with all fields")
        void fullBuilder() {
            BodyPartDefinition def = BodyPartDefinition.builder("head")
                .type(BodyPart.HEAD)
                .offset(0, 1.5, 0)
                .halfExtents(0.2, 0.2, 0.2)
                .bone("head")
                .parent("body")
                .color(0xFFFF0000)
                .damageMultiplier(2.0f)
                .build();

            assertEquals("head", def.id());
            assertEquals(BodyPart.HEAD, def.bodyPartType());
            assertEquals(new Vec3(0, 1.5, 0), def.localOffset());
            assertEquals(new Vec3(0.2, 0.2, 0.2), def.halfExtents());
            assertEquals("head", def.parentBoneId());
            assertEquals("body", def.parentPartId());
            assertEquals(0xFFFF0000, def.renderColor());
            assertEquals(2.0f, def.damageMultiplier());
        }

        @Test
        @DisplayName("builder defaults")
        void builderDefaults() {
            BodyPartDefinition def = BodyPartDefinition.builder("test").build();

            assertEquals("test", def.id());
            assertEquals(BodyPart.BODY, def.bodyPartType());
            assertEquals(Vec3.ZERO, def.localOffset());
            assertEquals(new Vec3(0.25, 0.25, 0.25), def.halfExtents());
            assertNull(def.parentBoneId());
            assertNull(def.parentPartId());
            assertEquals(1.0f, def.damageMultiplier());
        }

        @Test
        @DisplayName("builder throws on empty id")
        void emptyIdThrows() {
            assertThrows(IllegalStateException.class, () ->
                BodyPartDefinition.builder("").build());
        }

        @Test
        @DisplayName("type sets default color for HEAD")
        void typeDefaultColorHead() {
            BodyPartDefinition def = BodyPartDefinition.builder("head")
                .type(BodyPart.HEAD)
                .build();
            assertEquals(BodyPartDefinition.COLOR_HEAD, def.renderColor());
        }

        @Test
        @DisplayName("type sets default color for LEGS")
        void typeDefaultColorLegs() {
            BodyPartDefinition def = BodyPartDefinition.builder("leg")
                .type(BodyPart.LEGS)
                .build();
            assertEquals(BodyPartDefinition.COLOR_LEGS, def.renderColor());
        }

        @Test
        @DisplayName("size sets halfExtents to half of full size")
        void sizeMethod() {
            BodyPartDefinition def = BodyPartDefinition.builder("test")
                .size(2.0, 4.0, 6.0)
                .build();
            assertEquals(new Vec3(1.0, 2.0, 3.0), def.halfExtents());
        }

        @Test
        @DisplayName("size with Vec3 sets halfExtents to half")
        void sizeMethodVec3() {
            BodyPartDefinition def = BodyPartDefinition.builder("test")
                .size(new Vec3(2.0, 4.0, 6.0))
                .build();
            assertEquals(new Vec3(1.0, 2.0, 3.0), def.halfExtents());
        }
    }

    @Nested
    @DisplayName("presets")
    class PresetTests {

        @Test
        @DisplayName("head preset has correct defaults")
        void headPreset() {
            Vec3 offset = new Vec3(0, 1.5, 0);
            Vec3 half = new Vec3(0.2, 0.2, 0.2);
            BodyPartDefinition head = BodyPartDefinition.head(offset, half, "headBone");

            assertEquals("head", head.id());
            assertEquals(BodyPart.HEAD, head.bodyPartType());
            assertEquals(offset, head.localOffset());
            assertEquals(half, head.halfExtents());
            assertEquals("headBone", head.parentBoneId());
            assertEquals("body", head.parentPartId());
            assertEquals(2.0f, head.damageMultiplier());
        }

        @Test
        @DisplayName("body preset has 1.0x damage multiplier")
        void bodyPreset() {
            BodyPartDefinition body = BodyPartDefinition.body(Vec3.ZERO, new Vec3(0.2, 0.3, 0.1), "body");
            assertEquals("body", body.id());
            assertEquals(1.0f, body.damageMultiplier());
            assertNull(body.parentPartId());
        }

        @Test
        @DisplayName("arm presets have 0.75x damage multiplier")
        void armPresets() {
            BodyPartDefinition leftArm = BodyPartDefinition.leftArm(Vec3.ZERO, new Vec3(0.1, 0.3, 0.1), "leftArm");
            BodyPartDefinition rightArm = BodyPartDefinition.rightArm(Vec3.ZERO, new Vec3(0.1, 0.3, 0.1), "rightArm");

            assertEquals(0.75f, leftArm.damageMultiplier());
            assertEquals(0.75f, rightArm.damageMultiplier());
            assertEquals("left_arm", leftArm.id());
            assertEquals("right_arm", rightArm.id());
        }

        @Test
        @DisplayName("leg presets have 0.5x damage multiplier")
        void legPresets() {
            BodyPartDefinition leftLeg = BodyPartDefinition.leftLeg(Vec3.ZERO, new Vec3(0.1, 0.35, 0.1), "leftLeg");
            BodyPartDefinition rightLeg = BodyPartDefinition.rightLeg(Vec3.ZERO, new Vec3(0.1, 0.35, 0.1), "rightLeg");

            assertEquals(0.5f, leftLeg.damageMultiplier());
            assertEquals(0.5f, rightLeg.damageMultiplier());
        }
    }
}
