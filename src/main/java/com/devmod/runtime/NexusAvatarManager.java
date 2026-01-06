package com.devmod.runtime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.compat.mods.easynpc.EasyNpcCompat;
import com.devmod.config.Config;
import com.mojang.math.Transformation;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Spawns and maintains the Nexus AI avatar.
 */
public final class NexusAvatarManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(NexusAvatarManager.class);
    private static final String AVATAR_ID = "nexus_ai";
    public static final String AVATAR_TAG = "devmod_nexus_avatar";
    private static final String AVATAR_ANCHOR_TAG = "devmod_nexus_avatar_anchor";
    private static final String AVATAR_VISUAL_TAG = "devmod_nexus_avatar_visual";
    private static final String AVATAR_HEAD_TAG = "devmod_nexus_avatar_head";
    private static final String AVATAR_CORE_TAG = "devmod_nexus_avatar_core";
    private static final String AVATAR_LABEL_TAG = "devmod_nexus_avatar_label";
    private static final String AVATAR_FALLBACK_TAG = "devmod_nexus_avatar_fallback";
    private static final int BASE_Y_OFFSET = 2;
    private static final int CLEANUP_RADIUS = 40;
    private static final float ANCHOR_WIDTH = 1.0f;
    private static final float ANCHOR_HEIGHT = 2.2f;
    private static final float HEAD_SCALE = 1.15f;
    private static final float CORE_SCALE = 0.45f;
    private static final Vec3 HEAD_OFFSET = new Vec3(0.0, 0.75, 0.0);
    private static final Vec3 CORE_OFFSET = new Vec3(0.0, 0.25, 0.0);
    private static final Vec3 LABEL_OFFSET = new Vec3(0.0, 1.45, 0.0);

    // Animator instance for floating/particles
    private static final NexusAvatarAnimator animator = new NexusAvatarAnimator();

    private NexusAvatarManager() {}

    public static void spawn(ServerLevel level, BlockPos hubOrigin) {
        if (level == null || hubOrigin == null) {
            return;
        }
        if (!Config.NEXUS_AVATAR_ENABLED.get()) {
            remove(level, hubOrigin);
            return;
        }

        remove(level, hubOrigin);
        resetAnimator();

        BlockPos spawnPos = resolveSpawnPos(hubOrigin);
        String name = resolveName();
        ItemStack headItem = buildHeadItem(Config.NEXUS_AVATAR_SKIN.get());

        if (EasyNpcCompat.isAvailable()) {
            UUID npcId = EasyNpcCompat.spawnNpc(level, spawnPos, AVATAR_ID, name);
            if (npcId != null) {
                Entity entity = level.getEntity(npcId);
                if (entity != null) {
                    tuneEntity(entity, name, headItem);
                    return;
                }
            }
        }

        spawnFallback(level, spawnPos, name, headItem);
    }

    public static void remove(ServerLevel level, BlockPos hubOrigin) {
        if (level == null) {
            return;
        }
        if (EasyNpcCompat.isAvailable()) {
            EasyNpcCompat.removeNpc(level, AVATAR_ID);
        }
        AABB bounds = Objects.requireNonNull(resolveCleanupBounds(hubOrigin));
        List<Entity> tagged = level.getEntities((Entity) null, bounds,
            entity -> entity.getTags().contains(AVATAR_TAG));
        for (Entity entity : tagged) {
            entity.discard();
        }
    }

    public static boolean hasAvatar(ServerLevel level, BlockPos hubOrigin) {
        if (level == null) {
            return false;
        }
        AABB bounds = Objects.requireNonNull(resolveCleanupBounds(hubOrigin));
        if (!level.getEntities((Entity) null, bounds, entity -> entity.getTags().contains(AVATAR_ANCHOR_TAG)).isEmpty()) {
            return true;
        }
        return !level.getEntities((Entity) null, bounds, entity -> entity.getTags().contains(AVATAR_TAG)).isEmpty();
    }

    /**
     * Find the avatar entity in the level.
     */
    public static Entity findAvatar(ServerLevel level, BlockPos hubOrigin) {
        if (level == null) {
            return null;
        }
        AABB bounds = Objects.requireNonNull(resolveCleanupBounds(hubOrigin));
        List<Entity> avatars = level.getEntities((Entity) null, bounds,
            entity -> entity.getTags().contains(AVATAR_ANCHOR_TAG));
        if (!avatars.isEmpty()) {
            return avatars.get(0);
        }
        avatars = level.getEntities((Entity) null, bounds,
            entity -> entity.getTags().contains(AVATAR_TAG));
        return avatars.isEmpty() ? null : avatars.get(0);
    }

    /**
     * Called every server tick to animate the avatar.
     */
    public static void tick(ServerLevel level, BlockPos hubOrigin) {
        if (level == null || !Config.NEXUS_AVATAR_ENABLED.get()) {
            return;
        }
        Entity avatar = findAvatar(level, hubOrigin);
        if (avatar != null) {
            animator.tick(avatar, Objects.requireNonNull(level));
            if (avatar.getTags().contains(AVATAR_FALLBACK_TAG)) {
                syncFallbackVisuals(level, hubOrigin, avatar);
            }
        }
    }

    /**
     * Reset the animator state (call when avatar is respawned).
     */
    public static void resetAnimator() {
        animator.reset();
    }

    /**
     * Handle player interaction with the avatar (right-click).
     * Opens the dialog screen.
     */
    public static void handleInteraction(net.minecraft.server.level.ServerPlayer player, Entity avatar) {
        if (player == null || avatar == null) {
            return;
        }
        if (!avatar.getTags().contains(AVATAR_TAG)) {
            return;
        }

        LOGGER.debug("[NexusAvatar] Player {} interacted with avatar", player.getName().getString());

        // Open greeting dialog
        com.devmod.runtime.network.NexusNetworkHandler.openGreetingDialog(player);
    }

    private static BlockPos resolveSpawnPos(BlockPos hubOrigin) {
        BlockPos anchor = resolveAnchor(hubOrigin);
        int y = anchor.getY() + BASE_Y_OFFSET + Config.NEXUS_AVATAR_FLOAT_OFFSET.get();
        return new BlockPos(anchor.getX(), y, anchor.getZ());
    }

    private static BlockPos resolveAnchor(BlockPos hubOrigin) {
        BlockPos offset = Objects.requireNonNull(NexusSpawnManager.getDefaultSpawnOffset());
        return hubOrigin.offset(offset);
    }

    private static AABB resolveCleanupBounds(BlockPos hubOrigin) {
        BlockPos anchor = hubOrigin == null ? BlockPos.ZERO : resolveAnchor(hubOrigin);
        int minX = anchor.getX() - CLEANUP_RADIUS;
        int maxX = anchor.getX() + CLEANUP_RADIUS;
        int minZ = anchor.getZ() - CLEANUP_RADIUS;
        int maxZ = anchor.getZ() + CLEANUP_RADIUS;
        int minY = anchor.getY() - 8;
        int maxY = anchor.getY() + 40;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static String resolveName() {
        String name = Config.NEXUS_AVATAR_NAME.get();
        if (name == null || name.isBlank()) {
            return "NEXA";
        }
        return name.trim();
    }

    private static void tuneEntity(Entity entity, String name, ItemStack headItem) {
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setCustomNameVisible(true);
        entity.setGlowingTag(Config.NEXUS_AVATAR_GLOW.get());
        entity.addTag(AVATAR_TAG);
        entity.addTag(AVATAR_ANCHOR_TAG);

        if (name != null && !name.isBlank()) {
            entity.setCustomName(Component.literal(Objects.requireNonNull(name.trim())));
        }

        if (!headItem.isEmpty()) {
            if (entity instanceof Mob mob) {
                mob.setItemSlot(EquipmentSlot.HEAD, headItem);
                mob.setDropChance(EquipmentSlot.HEAD, 0.0f);
                mob.setNoAi(true);
                mob.setPersistenceRequired();
            } else if (entity instanceof LivingEntity living) {
                living.setItemSlot(EquipmentSlot.HEAD, headItem);
            }
        }
    }

    private static void spawnFallback(ServerLevel level, BlockPos pos, String name, ItemStack headItem) {
        Vec3 anchorPos = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Interaction anchor = new Interaction(EntityType.INTERACTION, Objects.requireNonNull(level));
        anchor.setPos(anchorPos.x, anchorPos.y, anchorPos.z);
        anchor.setNoGravity(true);
        anchor.setInvulnerable(true);
        anchor.setSilent(true);
        anchor.addTag(AVATAR_TAG);
        anchor.addTag(AVATAR_ANCHOR_TAG);
        anchor.addTag(AVATAR_FALLBACK_TAG);

        CompoundTag anchorNbt = new CompoundTag();
        anchor.saveWithoutId(anchorNbt);
        anchorNbt.putFloat("width", ANCHOR_WIDTH);
        anchorNbt.putFloat("height", ANCHOR_HEIGHT);
        anchorNbt.putBoolean("response", true);
        anchor.load(anchorNbt);
        anchor.setNoGravity(true);
        anchor.setInvulnerable(true);
        anchor.setSilent(true);
        anchor.setGlowingTag(Config.NEXUS_AVATAR_GLOW.get());

        level.addFreshEntity(anchor);
        spawnFallbackVisuals(level, anchorPos, name, headItem);
    }

    private static void spawnFallbackVisuals(ServerLevel level, Vec3 anchorPos, String name, ItemStack headItem) {
        if (!headItem.isEmpty()) {
            createHeadDisplay(level, anchorPos.add(HEAD_OFFSET), headItem);
        }
        createCoreDisplay(level, anchorPos.add(CORE_OFFSET));
        if (name != null && !name.isBlank()) {
            createNameDisplay(level, anchorPos.add(LABEL_OFFSET), name.trim());
        }
    }

    private static void createHeadDisplay(ServerLevel level, Vec3 pos, ItemStack headItem) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        display.setPos(pos.x, pos.y, pos.z);

        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);
        nbt.put("item", headItem.save(level.registryAccess()));
        nbt.putString("item_display", ItemDisplayContext.HEAD.getSerializedName());
        nbt.putString("billboard", "center");
        applyDisplayScale(nbt, HEAD_SCALE, HEAD_SCALE, HEAD_SCALE);
        display.load(nbt);

        tuneDisplay(display, AVATAR_HEAD_TAG);
        level.addFreshEntity(display);
    }

    private static void createCoreDisplay(ServerLevel level, Vec3 pos) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(pos.x, pos.y, pos.z);

        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);
        nbt.put(Display.BlockDisplay.TAG_BLOCK_STATE,
            NbtUtils.writeBlockState(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()));
        nbt.putString("billboard", "fixed");
        applyDisplayScale(nbt, CORE_SCALE, CORE_SCALE * 1.2f, CORE_SCALE);
        display.load(nbt);

        tuneDisplay(display, AVATAR_CORE_TAG);
        level.addFreshEntity(display);
    }

    private static void createNameDisplay(ServerLevel level, Vec3 pos, String name) {
        Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        display.setPos(pos.x, pos.y, pos.z);

        CompoundTag nbt = new CompoundTag();
        display.saveWithoutId(nbt);
        Component label = Component.literal(name)
            .withStyle(Style.EMPTY.withBold(true).withColor(DesignTokens.Nexus.AVATAR_LABEL));
        String json = Component.Serializer.toJson(label, level.registryAccess());
        nbt.putString("text", json);
        nbt.putInt("line_width", 120);
        nbt.putInt("background", 0);
        nbt.putByte("text_opacity", (byte) -1);
        nbt.putBoolean("see_through", true);
        nbt.putBoolean("shadow", false);
        nbt.putString("alignment", "center");
        nbt.putString("billboard", "center");
        display.load(nbt);

        tuneDisplay(display, AVATAR_LABEL_TAG);
        level.addFreshEntity(display);
    }

    private static void tuneDisplay(Entity entity, String typeTag) {
        entity.setNoGravity(true);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setGlowingTag(Config.NEXUS_AVATAR_GLOW.get());
        entity.addTag(AVATAR_TAG);
        entity.addTag(AVATAR_VISUAL_TAG);
        entity.addTag(AVATAR_FALLBACK_TAG);
        if (typeTag != null && !typeTag.isBlank()) {
            entity.addTag(typeTag);
        }
    }

    private static void applyDisplayScale(CompoundTag nbt, float x, float y, float z) {
        float sx = Mth.clamp(x, 0.05f, 5.0f);
        float sy = Mth.clamp(y, 0.05f, 5.0f);
        float sz = Mth.clamp(z, 0.05f, 5.0f);
        Transformation transform = new Transformation(new Vector3f(0.0f, 0.0f, 0.0f),
            new Quaternionf(), new Vector3f(sx, sy, sz), new Quaternionf());
        DataResult<Tag> encoded = Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, transform);
        encoded.resultOrPartial(msg -> LOGGER.warn("[NexusAvatar] Failed to encode display transform: {}", msg))
            .ifPresent(tag -> nbt.put("transformation", tag));
    }

    private static void syncFallbackVisuals(ServerLevel level, BlockPos hubOrigin, Entity anchor) {
        AABB bounds = Objects.requireNonNull(resolveCleanupBounds(hubOrigin));
        List<Entity> visuals = level.getEntities((Entity) null, bounds,
            entity -> entity.getTags().contains(AVATAR_VISUAL_TAG));
        if (visuals.isEmpty()) {
            return;
        }
        Vec3 base = anchor.position();
        float yaw = anchor.getYRot();
        for (Entity entity : visuals) {
            Vec3 offset = resolveVisualOffset(entity);
            entity.setPos(base.x + offset.x, base.y + offset.y, base.z + offset.z);
            entity.setYRot(yaw);
            entity.setXRot(0.0f);
        }
    }

    private static Vec3 resolveVisualOffset(Entity entity) {
        if (entity.getTags().contains(AVATAR_HEAD_TAG)) {
            return HEAD_OFFSET;
        }
        if (entity.getTags().contains(AVATAR_CORE_TAG)) {
            return CORE_OFFSET;
        }
        if (entity.getTags().contains(AVATAR_LABEL_TAG)) {
            return LABEL_OFFSET;
        }
        return Vec3.ZERO;
    }

    private static ItemStack buildHeadItem(String skinSpec) {
        ItemStack head = new ItemStack(Objects.requireNonNull(Items.PLAYER_HEAD));
        if (skinSpec == null) {
            return head;
        }
        String trimmed = skinSpec.trim();
        if (trimmed.isEmpty()) {
            return head;
        }
        if ("none".equalsIgnoreCase(trimmed) || "off".equalsIgnoreCase(trimmed)) {
            return ItemStack.EMPTY;
        }

        if (trimmed.startsWith("player:")) {
            String name = trimmed.substring("player:".length()).trim();
            if (!name.isEmpty()) {
                setSkullOwner(head, name);
            }
            return head;
        }

        if (trimmed.startsWith("texture:") || trimmed.startsWith("base64:")) {
            String value = trimmed.substring(trimmed.indexOf(':') + 1).trim();
            if (!value.isEmpty()) {
                applySkullTexture(head, value);
            }
            return head;
        }

        setSkullOwner(head, trimmed);
        return head;
    }

    private static void setSkullOwner(ItemStack head, String name) {
        // MC 1.21 uses DataComponents instead of NBT
        head.set(Objects.requireNonNull(DataComponents.PROFILE), new ResolvableProfile(
            Objects.requireNonNull(Optional.of(name)),
            Objects.requireNonNull(Optional.empty()),
            new PropertyMap()
        ));
    }

    private static void applySkullTexture(ItemStack head, String textureValue) {
        // MC 1.21 uses DataComponents - for custom textures we create a profile with properties
        PropertyMap properties = new PropertyMap();
        properties.put("textures", new com.mojang.authlib.properties.Property("textures", textureValue));

        head.set(Objects.requireNonNull(DataComponents.PROFILE), new ResolvableProfile(
            Objects.requireNonNull(Optional.empty()),
            Objects.requireNonNull(Optional.empty()),
            properties
        ));
    }
}
