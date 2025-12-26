package com.devmod.collision.registry;

import javax.annotation.Nonnull;

import net.minecraft.world.entity.EntityType;

import com.devmod.collision.bodypart.BodyPartDefinition;
import com.devmod.collision.bodypart.BodyPartHierarchy;
import com.devmod.combat.HitHelper;

public final class VanillaBodyParts {

    private VanillaBodyParts() {} // Utility class

    // ==================== Default Hierarchies ====================

    /**
     * Standard humanoid body parts (Player, Zombie, Skeleton, etc.)
     *
     * Structure:
     * - body (root)
     *   - head
     *   - left_arm
     *   - right_arm
     * - left_leg (root)
     * - right_leg (root)
     */
    public static final BodyPartHierarchy HUMANOID = createHumanoid();

    /**
     * Quadruped body parts (Pig, Cow, Sheep, Wolf, etc.)
     *
     * Structure:
     * - body (root)
     *   - head
     * - leg_front_left (root)
     * - leg_front_right (root)
     * - leg_back_left (root)
     * - leg_back_right (root)
     */
    public static final BodyPartHierarchy QUADRUPED = createQuadruped();

    /**
     * Horizontal body parts (Dragon, Phantom, Guardian)
     *
     * Structure:
     * - front (head region)
     * - middle (body region)
     * - back (tail region)
     */
    public static final BodyPartHierarchy HORIZONTAL = createHorizontal();

    /**
     * Tall humanoid body parts (Enderman, Iron Golem)
     *
     * Structure similar to humanoid but with adjusted proportions.
     */
    public static final BodyPartHierarchy TALL_HUMANOID = createTallHumanoid();

    /**
     * Spider/Arthropod body parts
     */
    public static final BodyPartHierarchy ARTHROPOD = createArthropod();

    /**
     * Creeper body parts (custom slim torso + four legs)
     */
    public static final BodyPartHierarchy CREEPER = createCreeper();

    // ==================== Hierarchy Builders ====================

    @Nonnull
    private static BodyPartHierarchy createHumanoid() {
        // Standard player/zombie dimensions: 0.6 wide, 1.8 tall
        // Body part offsets are relative to entity origin (feet)

        return BodyPartHierarchy.builder()
            // Body (torso) - center of mass, root for upper body
            .addPart(BodyPartDefinition.builder("body")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 0.9, 0)           // Center at torso height
                .halfExtents(0.2, 0.3, 0.1)   // Torso box
                .bone("body")
                .parent(null)                 // Root part
                .damageMultiplier(1.0f)
                .build())

            // Head - attached to body
            .addPart(BodyPartDefinition.builder("head")
                .type(HitHelper.BodyPart.HEAD)
                .offset(0, 0.5, 0)            // Above body center
                .halfExtents(0.2, 0.2, 0.2)   // Head cube
                .bone("head")
                .parent("body")
                .damageMultiplier(2.0f)       // Headshot bonus
                .build())

            // Left Arm - attached to body
            .addPart(BodyPartDefinition.builder("left_arm")
                .type(HitHelper.BodyPart.ARMS)
                .offset(-0.35, 0.15, 0)       // Left side of body
                .halfExtents(0.1, 0.3, 0.1)   // Arm cylinder approximation
                .bone("leftArm")
                .parent("body")
                .damageMultiplier(0.75f)
                .build())

            // Right Arm - attached to body
            .addPart(BodyPartDefinition.builder("right_arm")
                .type(HitHelper.BodyPart.ARMS)
                .offset(0.35, 0.15, 0)        // Right side of body
                .halfExtents(0.1, 0.3, 0.1)
                .bone("rightArm")
                .parent("body")
                .damageMultiplier(0.75f)
                .build())

            // Left Leg - root part (not attached to body for independent movement)
            .addPart(BodyPartDefinition.builder("left_leg")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.1, 0.4, 0)         // Left side, lower body
                .halfExtents(0.1, 0.35, 0.1)
                .bone("leftLeg")
                .parent(null)                 // Independent root
                .damageMultiplier(0.5f)
                .build())

            // Right Leg - root part
            .addPart(BodyPartDefinition.builder("right_leg")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.1, 0.4, 0)          // Right side, lower body
                .halfExtents(0.1, 0.35, 0.1)
                .bone("rightLeg")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .build();
    }

    @Nonnull
    private static BodyPartHierarchy createQuadruped() {
        // Typical quadruped: ~0.9 wide, ~1.4 tall, ~1.4 long

        return BodyPartHierarchy.builder()
            // Body - horizontal torso
            .addPart(BodyPartDefinition.builder("body")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 0.7, 0)
                .halfExtents(0.25, 0.25, 0.4)  // Wide and long
                .bone("body")
                .parent(null)
                .damageMultiplier(1.0f)
                .build())

            // Head - front of body
            .addPart(BodyPartDefinition.builder("head")
                .type(HitHelper.BodyPart.HEAD)
                .offset(0, 0.3, 0.5)           // In front of body
                .halfExtents(0.2, 0.2, 0.2)
                .bone("head")
                .parent("body")
                .damageMultiplier(1.5f)
                .build())

            // Front Left Leg
            .addPart(BodyPartDefinition.builder("leg_front_left")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.2, 0.3, 0.25)
                .halfExtents(0.1, 0.3, 0.1)
                .bone("leg0")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            // Front Right Leg
            .addPart(BodyPartDefinition.builder("leg_front_right")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.2, 0.3, 0.25)
                .halfExtents(0.1, 0.3, 0.1)
                .bone("leg1")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            // Back Left Leg
            .addPart(BodyPartDefinition.builder("leg_back_left")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.2, 0.3, -0.25)
                .halfExtents(0.1, 0.3, 0.1)
                .bone("leg2")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            // Back Right Leg
            .addPart(BodyPartDefinition.builder("leg_back_right")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.2, 0.3, -0.25)
                .halfExtents(0.1, 0.3, 0.1)
                .bone("leg3")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .build();
    }

    @Nonnull
    private static BodyPartHierarchy createHorizontal() {
        // Horizontal body (dragon-like): long body axis

        return BodyPartHierarchy.builder()
            // Front (head region) - highest priority
            .addPart(BodyPartDefinition.builder("front")
                .type(HitHelper.BodyPart.HEAD)
                .offset(0, 0.5, 1.0)          // Front of entity
                .halfExtents(0.4, 0.4, 0.5)
                .bone("head")
                .parent(null)
                .damageMultiplier(1.5f)
                .build())

            // Middle (body region)
            .addPart(BodyPartDefinition.builder("middle")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 0.5, 0)            // Center
                .halfExtents(0.5, 0.4, 0.6)
                .bone("body")
                .parent(null)
                .damageMultiplier(1.0f)
                .build())

            // Back (tail region)
            .addPart(BodyPartDefinition.builder("back")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0, 0.5, -1.0)         // Back of entity
                .halfExtents(0.3, 0.3, 0.5)
                .bone("tail")
                .parent(null)
                .damageMultiplier(0.75f)
                .build())

            .build();
    }

    @Nonnull
    private static BodyPartHierarchy createTallHumanoid() {
        // Tall humanoid (enderman): ~0.6 wide, ~2.9 tall

        return BodyPartHierarchy.builder()
            // Body - elongated torso
            .addPart(BodyPartDefinition.builder("body")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 1.5, 0)            // Higher center
                .halfExtents(0.15, 0.4, 0.1)  // Thin torso
                .bone("body")
                .parent(null)
                .damageMultiplier(1.0f)
                .build())

            // Head - small relative to body
            .addPart(BodyPartDefinition.builder("head")
                .type(HitHelper.BodyPart.HEAD)
                .offset(0, 0.6, 0)            // Above torso
                .halfExtents(0.15, 0.15, 0.15)
                .bone("head")
                .parent("body")
                .damageMultiplier(2.0f)
                .build())

            // Left Arm - very long
            .addPart(BodyPartDefinition.builder("left_arm")
                .type(HitHelper.BodyPart.ARMS)
                .offset(-0.25, 0, 0)
                .halfExtents(0.1, 0.5, 0.1)   // Long arms
                .bone("leftArm")
                .parent("body")
                .damageMultiplier(0.75f)
                .build())

            // Right Arm - very long
            .addPart(BodyPartDefinition.builder("right_arm")
                .type(HitHelper.BodyPart.ARMS)
                .offset(0.25, 0, 0)
                .halfExtents(0.1, 0.5, 0.1)
                .bone("rightArm")
                .parent("body")
                .damageMultiplier(0.75f)
                .build())

            // Left Leg - very long
            .addPart(BodyPartDefinition.builder("left_leg")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.1, 0.55, 0)
                .halfExtents(0.1, 0.5, 0.1)
                .bone("leftLeg")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            // Right Leg - very long
            .addPart(BodyPartDefinition.builder("right_leg")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.1, 0.55, 0)
                .halfExtents(0.1, 0.5, 0.1)
                .bone("rightLeg")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .build();
    }

    @Nonnull
    private static BodyPartHierarchy createArthropod() {
        // Spider-like: wide, low, many legs

        return BodyPartHierarchy.builder()
            // Head
            .addPart(BodyPartDefinition.builder("head")
                .type(HitHelper.BodyPart.HEAD)
                .offset(0, 0.4, 0.4)
                .halfExtents(0.25, 0.2, 0.25)
                .bone("head")
                .parent(null)
                .damageMultiplier(1.5f)
                .build())

            // Body (thorax)
            .addPart(BodyPartDefinition.builder("body")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 0.4, 0)
                .halfExtents(0.35, 0.2, 0.35)
                .bone("body0")
                .parent(null)
                .damageMultiplier(1.0f)
                .build())

            // Abdomen
            .addPart(BodyPartDefinition.builder("abdomen")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 0.4, -0.5)
                .halfExtents(0.4, 0.25, 0.4)
                .bone("body1")
                .parent(null)
                .damageMultiplier(0.9f)
                .build())

            // Legs are simplified to left/right groups
            .addPart(BodyPartDefinition.builder("legs_left")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.5, 0.25, 0)
                .halfExtents(0.3, 0.2, 0.4)
                .bone(null)  // No single bone, composite
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .addPart(BodyPartDefinition.builder("legs_right")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.5, 0.25, 0)
                .halfExtents(0.3, 0.2, 0.4)
                .bone(null)
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .build();
    }

    @Nonnull
    private static BodyPartHierarchy createCreeper() {
        // Creeper proportions: slim torso, small head, four thin legs
        // Coordinates assume feet at y=0 and model centered on origin.

        return BodyPartHierarchy.builder()
            // Body: tall slim torso
            .addPart(BodyPartDefinition.builder("body")
                .type(HitHelper.BodyPart.BODY)
                .offset(0, 0.9, 0)               // center of torso
                .halfExtents(0.22, 0.45, 0.22)   // ~0.44w x 0.9h x 0.44d
                .bone("body")
                .parent(null)
                .damageMultiplier(1.0f)
                .build())

            // Head: small cube above torso
            .addPart(BodyPartDefinition.builder("head")
                .type(HitHelper.BodyPart.HEAD)
                .offset(0, 1.55, 0)
                .halfExtents(0.2, 0.2, 0.2)
                .bone("head")
                .parent("body")
                .damageMultiplier(1.5f)
                .build())

            // Legs: four independent pillars at base corners
            .addPart(BodyPartDefinition.builder("leg_front_left")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.16, 0.25, 0.16)
                .halfExtents(0.08, 0.25, 0.08)
                .bone("leg0")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .addPart(BodyPartDefinition.builder("leg_front_right")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.16, 0.25, 0.16)
                .halfExtents(0.08, 0.25, 0.08)
                .bone("leg1")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .addPart(BodyPartDefinition.builder("leg_back_left")
                .type(HitHelper.BodyPart.LEGS)
                .offset(-0.16, 0.25, -0.16)
                .halfExtents(0.08, 0.25, 0.08)
                .bone("leg2")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .addPart(BodyPartDefinition.builder("leg_back_right")
                .type(HitHelper.BodyPart.LEGS)
                .offset(0.16, 0.25, -0.16)
                .halfExtents(0.08, 0.25, 0.08)
                .bone("leg3")
                .parent(null)
                .damageMultiplier(0.5f)
                .build())

            .build();
    }

    // ==================== Registration ====================

    /**
     * Registers all vanilla body parts to the registry.
     *
     * @param registry The registry to populate
     */
    public static void registerAll(BodyPartRegistry registry) {
        // Humanoid entities
        registry.register(EntityType.PLAYER, HUMANOID);
        registry.register(EntityType.ZOMBIE, HUMANOID);
        registry.register(EntityType.SKELETON, HUMANOID);
        registry.register(EntityType.HUSK, HUMANOID);
        registry.register(EntityType.DROWNED, HUMANOID);
        registry.register(EntityType.STRAY, HUMANOID);
        registry.register(EntityType.WITHER_SKELETON, TALL_HUMANOID);
        registry.register(EntityType.PIGLIN, HUMANOID);
        registry.register(EntityType.PIGLIN_BRUTE, HUMANOID);
        registry.register(EntityType.ZOMBIFIED_PIGLIN, HUMANOID);
        registry.register(EntityType.VINDICATOR, HUMANOID);
        registry.register(EntityType.PILLAGER, HUMANOID);
        registry.register(EntityType.EVOKER, HUMANOID);
        registry.register(EntityType.ILLUSIONER, HUMANOID);
        registry.register(EntityType.WITCH, HUMANOID);
        registry.register(EntityType.VILLAGER, HUMANOID);
        registry.register(EntityType.WANDERING_TRADER, HUMANOID);
        registry.register(EntityType.CREEPER, CREEPER);

        // Tall entities
        registry.register(EntityType.ENDERMAN, TALL_HUMANOID);
        registry.register(EntityType.IRON_GOLEM, TALL_HUMANOID);

        // Quadrupeds
        registry.register(EntityType.PIG, QUADRUPED);
        registry.register(EntityType.COW, QUADRUPED);
        registry.register(EntityType.SHEEP, QUADRUPED);
        registry.register(EntityType.WOLF, QUADRUPED);
        registry.register(EntityType.FOX, QUADRUPED);
        registry.register(EntityType.HORSE, QUADRUPED);
        registry.register(EntityType.DONKEY, QUADRUPED);
        registry.register(EntityType.MULE, QUADRUPED);
        registry.register(EntityType.LLAMA, QUADRUPED);
        registry.register(EntityType.GOAT, QUADRUPED);
        registry.register(EntityType.MOOSHROOM, QUADRUPED);

        // Horizontal/flying
        registry.register(EntityType.ENDER_DRAGON, HORIZONTAL);
        registry.register(EntityType.PHANTOM, HORIZONTAL);
        registry.register(EntityType.GUARDIAN, HORIZONTAL);
        registry.register(EntityType.ELDER_GUARDIAN, HORIZONTAL);

        // Arthropods
        registry.register(EntityType.SPIDER, ARTHROPOD);
        registry.register(EntityType.CAVE_SPIDER, ARTHROPOD);
    }
}
