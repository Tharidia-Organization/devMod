package com.devmod.collision.registry;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.devmod.collision.bodypart.BodyPartDefinition;
import com.devmod.collision.bodypart.BodyPartHierarchy;
import com.devmod.combat.HitHelper;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VanillaBodyParts")
class VanillaBodyPartsTest {

    @Nested
    @DisplayName("HUMANOID hierarchy")
    class HumanoidTests {

        @Test
        @DisplayName("has 6 parts")
        void partCount() {
            assertEquals(6, VanillaBodyParts.HUMANOID.getPartCount());
        }

        @Test
        @DisplayName("has body, head, left_arm, right_arm, left_leg, right_leg")
        void hasExpectedParts() {
            BodyPartHierarchy h = VanillaBodyParts.HUMANOID;
            assertTrue(h.hasPart("body"));
            assertTrue(h.hasPart("head"));
            assertTrue(h.hasPart("left_arm"));
            assertTrue(h.hasPart("right_arm"));
            assertTrue(h.hasPart("left_leg"));
            assertTrue(h.hasPart("right_leg"));
        }

        @Test
        @DisplayName("head has 2.0x damage multiplier")
        void headDamage() {
            BodyPartDefinition head = VanillaBodyParts.HUMANOID.getPart("head");
            assertNotNull(head);
            assertEquals(2.0f, head.damageMultiplier());
        }

        @Test
        @DisplayName("body has 1.0x damage multiplier")
        void bodyDamage() {
            BodyPartDefinition body = VanillaBodyParts.HUMANOID.getPart("body");
            assertNotNull(body);
            assertEquals(1.0f, body.damageMultiplier());
        }

        @Test
        @DisplayName("arms have 0.75x damage multiplier")
        void armDamage() {
            BodyPartDefinition leftArm = VanillaBodyParts.HUMANOID.getPart("left_arm");
            assertNotNull(leftArm);
            assertEquals(0.75f, leftArm.damageMultiplier());
        }

        @Test
        @DisplayName("legs have 0.5x damage multiplier")
        void legDamage() {
            BodyPartDefinition leftLeg = VanillaBodyParts.HUMANOID.getPart("left_leg");
            assertNotNull(leftLeg);
            assertEquals(0.5f, leftLeg.damageMultiplier());
        }

        @Test
        @DisplayName("body is root, head is child of body")
        void hierarchyStructure() {
            BodyPartHierarchy h = VanillaBodyParts.HUMANOID;
            List<String> roots = h.getRootPartIds();
            assertTrue(roots.contains("body"));
            assertFalse(roots.contains("head"));

            List<String> bodyChildren = h.getChildren("body");
            assertTrue(bodyChildren.contains("head"));
            assertTrue(bodyChildren.contains("left_arm"));
            assertTrue(bodyChildren.contains("right_arm"));
        }

        @Test
        @DisplayName("head is HEAD type, arms are ARMS type")
        void bodyPartTypes() {
            assertEquals(HitHelper.BodyPart.HEAD, VanillaBodyParts.HUMANOID.getPart("head").bodyPartType());
            assertEquals(HitHelper.BodyPart.ARMS, VanillaBodyParts.HUMANOID.getPart("left_arm").bodyPartType());
            assertEquals(HitHelper.BodyPart.BODY, VanillaBodyParts.HUMANOID.getPart("body").bodyPartType());
            assertEquals(HitHelper.BodyPart.LEGS, VanillaBodyParts.HUMANOID.getPart("left_leg").bodyPartType());
        }
    }

    @Nested
    @DisplayName("QUADRUPED hierarchy")
    class QuadrupedTests {

        @Test
        @DisplayName("has 6 parts (body, head, 4 legs)")
        void partCount() {
            assertEquals(6, VanillaBodyParts.QUADRUPED.getPartCount());
        }

        @Test
        @DisplayName("has expected parts")
        void hasExpectedParts() {
            BodyPartHierarchy h = VanillaBodyParts.QUADRUPED;
            assertTrue(h.hasPart("body"));
            assertTrue(h.hasPart("head"));
            assertTrue(h.hasPart("leg_front_left"));
            assertTrue(h.hasPart("leg_front_right"));
            assertTrue(h.hasPart("leg_back_left"));
            assertTrue(h.hasPart("leg_back_right"));
        }

        @Test
        @DisplayName("head has 1.5x damage multiplier")
        void headDamage() {
            BodyPartDefinition head = VanillaBodyParts.QUADRUPED.getPart("head");
            assertNotNull(head);
            assertEquals(1.5f, head.damageMultiplier());
        }
    }

    @Nested
    @DisplayName("HORIZONTAL hierarchy")
    class HorizontalTests {

        @Test
        @DisplayName("has 3 parts (front, middle, back)")
        void partCount() {
            assertEquals(3, VanillaBodyParts.HORIZONTAL.getPartCount());
        }

        @Test
        @DisplayName("front is HEAD type")
        void frontType() {
            assertEquals(HitHelper.BodyPart.HEAD,
                VanillaBodyParts.HORIZONTAL.getPart("front").bodyPartType());
        }

        @Test
        @DisplayName("back has reduced damage multiplier")
        void backDamage() {
            BodyPartDefinition back = VanillaBodyParts.HORIZONTAL.getPart("back");
            assertNotNull(back);
            assertEquals(0.75f, back.damageMultiplier());
        }
    }

    @Nested
    @DisplayName("TALL_HUMANOID hierarchy")
    class TallHumanoidTests {

        @Test
        @DisplayName("has 6 parts like humanoid")
        void partCount() {
            assertEquals(6, VanillaBodyParts.TALL_HUMANOID.getPartCount());
        }

        @Test
        @DisplayName("head has 2.0x damage multiplier")
        void headDamage() {
            assertEquals(2.0f, VanillaBodyParts.TALL_HUMANOID.getPart("head").damageMultiplier());
        }
    }

    @Nested
    @DisplayName("ARTHROPOD hierarchy")
    class ArthropodTests {

        @Test
        @DisplayName("has 5 parts")
        void partCount() {
            assertEquals(5, VanillaBodyParts.ARTHROPOD.getPartCount());
        }

        @Test
        @DisplayName("has head, body, abdomen, legs_left, legs_right")
        void hasExpectedParts() {
            BodyPartHierarchy h = VanillaBodyParts.ARTHROPOD;
            assertTrue(h.hasPart("head"));
            assertTrue(h.hasPart("body"));
            assertTrue(h.hasPart("abdomen"));
            assertTrue(h.hasPart("legs_left"));
            assertTrue(h.hasPart("legs_right"));
        }
    }

    @Nested
    @DisplayName("CREEPER hierarchy")
    class CreeperTests {

        @Test
        @DisplayName("has 6 parts (body, head, 4 legs)")
        void partCount() {
            assertEquals(6, VanillaBodyParts.CREEPER.getPartCount());
        }

        @Test
        @DisplayName("head has 1.5x damage multiplier")
        void headDamage() {
            assertEquals(1.5f, VanillaBodyParts.CREEPER.getPart("head").damageMultiplier());
        }

        @Test
        @DisplayName("legs have 0.5x damage multiplier")
        void legDamage() {
            assertEquals(0.5f, VanillaBodyParts.CREEPER.getPart("leg_front_left").damageMultiplier());
        }
    }

    @Test
    @DisplayName("all hierarchies have at least one HEAD type part")
    void allHaveHead() {
        BodyPartHierarchy[] hierarchies = {
            VanillaBodyParts.HUMANOID,
            VanillaBodyParts.QUADRUPED,
            VanillaBodyParts.HORIZONTAL,
            VanillaBodyParts.TALL_HUMANOID,
            VanillaBodyParts.ARTHROPOD,
            VanillaBodyParts.CREEPER
        };

        for (BodyPartHierarchy h : hierarchies) {
            assertNotNull(h.findByType(HitHelper.BodyPart.HEAD),
                "Hierarchy should have at least one HEAD part: " + h);
        }
    }

    @Test
    @DisplayName("all hierarchies have at least one BODY type part")
    void allHaveBody() {
        BodyPartHierarchy[] hierarchies = {
            VanillaBodyParts.HUMANOID,
            VanillaBodyParts.QUADRUPED,
            VanillaBodyParts.HORIZONTAL,
            VanillaBodyParts.TALL_HUMANOID,
            VanillaBodyParts.ARTHROPOD,
            VanillaBodyParts.CREEPER
        };

        for (BodyPartHierarchy h : hierarchies) {
            assertNotNull(h.findByType(HitHelper.BodyPart.BODY),
                "Hierarchy should have at least one BODY part: " + h);
        }
    }
}
