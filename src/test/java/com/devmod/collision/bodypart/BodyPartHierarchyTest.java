package com.devmod.collision.bodypart;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import com.devmod.shared.BodyPart;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BodyPartHierarchy")
class BodyPartHierarchyTest {

    private static BodyPartDefinition bodyDef() {
        return BodyPartDefinition.builder("body")
            .type(BodyPart.BODY)
            .offset(0, 0.9, 0)
            .halfExtents(0.2, 0.3, 0.1)
            .parent(null)
            .build();
    }

    private static BodyPartDefinition headDef() {
        return BodyPartDefinition.builder("head")
            .type(BodyPart.HEAD)
            .offset(0, 0.5, 0)
            .halfExtents(0.2, 0.2, 0.2)
            .parent("body")
            .build();
    }

    private static BodyPartDefinition legDef(String id) {
        return BodyPartDefinition.builder(id)
            .type(BodyPart.LEGS)
            .offset(0, 0.4, 0)
            .halfExtents(0.1, 0.35, 0.1)
            .parent(null)
            .build();
    }

    private static BodyPartHierarchy sampleHierarchy() {
        return BodyPartHierarchy.builder()
            .addPart(bodyDef())
            .addPart(headDef())
            .addPart(legDef("left_leg"))
            .addPart(legDef("right_leg"))
            .build();
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("builds hierarchy with correct part count")
        void partCount() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            assertEquals(4, hierarchy.getPartCount());
        }

        @Test
        @DisplayName("throws when building with no parts")
        void emptyThrows() {
            assertThrows(IllegalStateException.class, () ->
                BodyPartHierarchy.builder().build());
        }

        @Test
        @DisplayName("throws when child references non-existent parent")
        void invalidParentThrows() {
            assertThrows(IllegalStateException.class, () ->
                BodyPartHierarchy.builder()
                    .addPart(headDef()) // references "body" which doesn't exist
                    .build());
        }

        @Test
        @DisplayName("addParts varargs works")
        void addPartsVarargs() {
            BodyPartHierarchy hierarchy = BodyPartHierarchy.builder()
                .addParts(bodyDef(), headDef())
                .build();
            assertEquals(2, hierarchy.getPartCount());
        }

        @Test
        @DisplayName("setParent overrides definition parentPartId")
        void setParentOverride() {
            BodyPartHierarchy hierarchy = BodyPartHierarchy.builder()
                .addPart(bodyDef())
                .addPart(headDef())
                .addPart(legDef("left_leg"))
                .setParent("left_leg", "body")
                .build();

            List<String> bodyChildren = hierarchy.getChildren("body");
            assertTrue(bodyChildren.contains("left_leg"));
        }
    }

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("getPart returns correct definition")
        void getPart() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            BodyPartDefinition body = hierarchy.getPart("body");
            assertNotNull(body);
            assertEquals("body", body.id());
            assertEquals(BodyPart.BODY, body.bodyPartType());
        }

        @Test
        @DisplayName("getPart returns null for unknown id")
        void getPartNull() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            assertNull(hierarchy.getPart("nonexistent"));
        }

        @Test
        @DisplayName("hasPart returns true for existing part")
        void hasPartTrue() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            assertTrue(hierarchy.hasPart("body"));
            assertTrue(hierarchy.hasPart("head"));
        }

        @Test
        @DisplayName("hasPart returns false for non-existing part")
        void hasPartFalse() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            assertFalse(hierarchy.hasPart("nonexistent"));
        }

        @Test
        @DisplayName("getAllParts returns all definitions")
        void getAllParts() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            Collection<BodyPartDefinition> parts = hierarchy.getAllParts();
            assertEquals(4, parts.size());
        }

        @Test
        @DisplayName("getPartArray returns correct length array")
        void getPartArray() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            BodyPartDefinition[] array = hierarchy.getPartArray();
            assertEquals(4, array.length);
        }

        @Test
        @DisplayName("getPartArray returns defensive copy")
        void getPartArrayDefensiveCopy() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            BodyPartDefinition[] a = hierarchy.getPartArray();
            BodyPartDefinition[] b = hierarchy.getPartArray();
            assertNotSame(a, b);
        }
    }

    @Nested
    @DisplayName("Hierarchy queries")
    class HierarchyTests {

        @Test
        @DisplayName("getRootPartIds returns parts with no parent")
        void rootPartIds() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<String> roots = hierarchy.getRootPartIds();
            assertTrue(roots.contains("body"));
            assertTrue(roots.contains("left_leg"));
            assertTrue(roots.contains("right_leg"));
            assertFalse(roots.contains("head")); // head has parent "body"
        }

        @Test
        @DisplayName("getChildren returns child parts")
        void getChildren() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<String> bodyChildren = hierarchy.getChildren("body");
            assertTrue(bodyChildren.contains("head"));
        }

        @Test
        @DisplayName("getChildren returns empty list for leaf parts")
        void getChildrenLeaf() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<String> headChildren = hierarchy.getChildren("head");
            assertTrue(headChildren.isEmpty());
        }

        @Test
        @DisplayName("getChildren returns empty list for unknown part")
        void getChildrenUnknown() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<String> children = hierarchy.getChildren("nonexistent");
            assertTrue(children.isEmpty());
        }

        @Test
        @DisplayName("getTransformChain returns path from root to part")
        void transformChain() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<String> chain = hierarchy.getTransformChain("head");
            assertEquals(List.of("body", "head"), chain);
        }

        @Test
        @DisplayName("getTransformChain for root returns single element")
        void transformChainRoot() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<String> chain = hierarchy.getTransformChain("body");
            assertEquals(List.of("body"), chain);
        }
    }

    @Nested
    @DisplayName("findByType")
    class FindByTypeTests {

        @Test
        @DisplayName("findByType returns first match")
        void findByType() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            BodyPartDefinition head = hierarchy.findByType(BodyPart.HEAD);
            assertNotNull(head);
            assertEquals("head", head.id());
        }

        @Test
        @DisplayName("findByType returns null when no match")
        void findByTypeNull() {
            BodyPartHierarchy hierarchy = BodyPartHierarchy.builder()
                .addPart(bodyDef())
                .build();
            assertNull(hierarchy.findByType(BodyPart.HEAD));
        }

        @Test
        @DisplayName("findAllByType returns all matches")
        void findAllByType() {
            BodyPartHierarchy hierarchy = sampleHierarchy();
            List<BodyPartDefinition> legs = hierarchy.findAllByType(BodyPart.LEGS);
            assertEquals(2, legs.size());
        }

        @Test
        @DisplayName("findAllByType returns empty list when no matches")
        void findAllByTypeEmpty() {
            BodyPartHierarchy hierarchy = BodyPartHierarchy.builder()
                .addPart(bodyDef())
                .build();
            List<BodyPartDefinition> arms = hierarchy.findAllByType(BodyPart.ARMS);
            assertTrue(arms.isEmpty());
        }
    }

    @Nested
    @DisplayName("flat factory")
    class FlatTests {

        @Test
        @DisplayName("flat hierarchy makes all parts root")
        void flatMakesAllRoot() {
            BodyPartDefinition headWithParent = BodyPartDefinition.builder("head")
                .type(BodyPart.HEAD)
                .parent("body")
                .build();
            BodyPartHierarchy flat = BodyPartHierarchy.flat(bodyDef(), headWithParent);

            List<String> roots = flat.getRootPartIds();
            assertEquals(2, roots.size());
            assertTrue(roots.contains("body"));
            assertTrue(roots.contains("head")); // parent was removed
        }
    }

    @Test
    @DisplayName("toString includes part count")
    void toStringTest() {
        BodyPartHierarchy hierarchy = sampleHierarchy();
        String str = hierarchy.toString();
        assertTrue(str.contains("4"), "Should include part count");
    }
}
