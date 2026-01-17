package com.devmod.clone.block.entity;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.devmod.clone.CloneBlockEntities;
import com.devmod.clone.data.BioscanData;
import com.devmod.clone.util.NeurolinkConnector;

/**
 * Block entity for the Imprinter.
 *
 * <p>Workflow (Hologenica-style):
 * <ol>
 *   <li>Entity stands on the Imprinter block</li>
 *   <li>Imprinter searches for connected Neurocell with empty Bioscanner via Neurolink</li>
 *   <li>5-second scan with particle effects, entity pulled to center</li>
 *   <li>Entity DNA is imprinted onto the Bioscanner</li>
 *   <li>Entity DIES after scan completes</li>
 * </ol>
 */
public class ImprinterBlockEntity extends BlockEntity {

    private static final int SCAN_DURATION = 100; // 5 seconds (100 ticks)

    private int imprintProgress = 0;
    private boolean isImprinting = false;
    private boolean hasEntityNearby = false;
    private String targetName = "";

    @Nullable
    private LivingEntity currentTarget = null;
    @Nullable
    private NeurocellAccess connectedNeurocell = null;

    public ImprinterBlockEntity(BlockPos pos, BlockState state) {
        super(CloneBlockEntities.IMPRINTER.get(), pos, state);
    }

    /**
     * Called from ImprinterBlock.entityInside() when an entity enters the block.
     */
    public void setHasEntity(boolean hasEntity) {
        this.hasEntityNearby = hasEntity;
    }

    /**
     * Returns true if the block entity needs to tick (for performance optimization).
     */
    public boolean needsTicking() {
        return hasEntityNearby || isImprinting;
    }

    /**
     * Returns true if currently scanning an entity.
     */
    public boolean isScanning() {
        return isImprinting;
    }

    /**
     * Returns the current imprint progress as a float 0.0 to 1.0.
     */
    public float getImprintProgress() {
        if (!isImprinting) {
            return 0.0f;
        }
        return (float) imprintProgress / SCAN_DURATION;
    }

    public void tick() {
        var lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        // Reset entity flag each tick (will be re-set by entityInside if still present)
        hasEntityNearby = false;

        if (!isImprinting) {
            // Look for entity standing on the imprinter
            LivingEntity entity = findEntityOnImprinter();
            if (entity != null) {
                // Find connected Neurocell with empty Bioscanner
                NeurocellAccess neurocell = NeurolinkConnector.findConnectedNeurocellWithEmptyBioscanner(
                    lvl, Objects.requireNonNull(worldPosition));
                if (neurocell != null) {
                    startImprinting(entity, neurocell);
                }
            }
        } else {
            processImprinting();
        }
    }

    /**
     * Find a living entity standing on the imprinter.
     * Uses a tight AABB directly above the block.
     */
    @Nullable
    private LivingEntity findEntityOnImprinter() {
        var lvl = level;
        if (lvl == null) {
            return null;
        }

        BlockPos pos = Objects.requireNonNull(worldPosition);
        // AABB directly above the imprinter block (entity must stand ON it)
        AABB checkBox = new AABB(
            pos.getX(),
            pos.getY() + 0.5,
            pos.getZ(),
            pos.getX() + 1,
            pos.getY() + 3.0,
            pos.getZ() + 1
        );

        List<LivingEntity> entities = lvl.getEntitiesOfClass(LivingEntity.class, checkBox);
        return entities.isEmpty() ? null : entities.get(0);
    }

    private void startImprinting(LivingEntity entity, NeurocellAccess neurocell) {
        currentTarget = entity;
        connectedNeurocell = neurocell;
        isImprinting = true;
        imprintProgress = 0;
        targetName = Objects.requireNonNull(entity.getDisplayName().getString());
        setChanged();

        // Start sound
        var lvl = level;
        if (lvl != null) {
            lvl.playSound(null, Objects.requireNonNull(worldPosition),
                Objects.requireNonNull(SoundEvents.BEACON_ACTIVATE), SoundSource.BLOCKS, 0.5f, 1.5f);
        }

        // Notify player if it's them being scanned
        if (entity instanceof Player player) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.imprinter.scanning_self")
                    .withStyle(ChatFormatting.GOLD)),
                true
            );
        }
    }

    private void processImprinting() {
        if (level == null) {
            resetImprinting();
            return;
        }

        // Check if target is still valid
        LivingEntity target = currentTarget;
        if (target == null || !target.isAlive()) {
            resetImprinting();
            return;
        }

        // Check if target moved away
        LivingEntity entityOnBlock = findEntityOnImprinter();
        if (entityOnBlock == null || !entityOnBlock.equals(target)) {
            // Target stepped off - abort
            if (target instanceof Player player) {
                player.displayClientMessage(
                    Objects.requireNonNull(Component.translatable("message.devmod.imprinter.aborted")
                        .withStyle(ChatFormatting.RED)),
                    true
                );
            }
            resetImprinting();
            return;
        }

        // Pull entity to center
        pullEntityToCenter(target);

        // Spawn effects
        spawnImprintingEffects(target);

        imprintProgress++;
        setChanged();

        // Complete when done
        if (imprintProgress >= SCAN_DURATION) {
            completeImprinting();
        }
    }

    /**
     * Pull the entity gently toward the center of the block.
     */
    private void pullEntityToCenter(LivingEntity entity) {
        BlockPos pos = Objects.requireNonNull(worldPosition);
        double centerX = pos.getX() + 0.5;
        double centerZ = pos.getZ() + 0.5;
        double targetY = pos.getY() + 0.5;

        double dx = centerX - entity.getX();
        double dz = centerZ - entity.getZ();
        double dy = targetY - entity.getY();

        entity.setDeltaMovement(dx * 0.1, dy * 0.1, dz * 0.1);
        entity.hurtMarked = true;
    }

    /**
     * Spawn particle effects during imprinting.
     */
    private void spawnImprintingEffects(LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = Objects.requireNonNull(worldPosition);

        // Orbit particles (every 2 ticks)
        if (imprintProgress % 2 == 0) {
            double angle = imprintProgress * 0.2 % (Math.PI * 2);
            double x = entity.getX() + Math.cos(angle) * 1.2;
            double z = entity.getZ() + Math.sin(angle) * 1.2;
            double y = entity.getY() + serverLevel.random.nextDouble() * entity.getBbHeight();
            serverLevel.sendParticles(Objects.requireNonNull(ParticleTypes.ENCHANT), x, y, z, 1, 0, 0, 0, 0.1);
        }

        // Helix particles (every 3 ticks)
        if (imprintProgress % 3 == 0) {
            double helixAngle = imprintProgress * 0.3 % (Math.PI * 2);
            double x = entity.getX() + Math.cos(helixAngle) * 0.4;
            double z = entity.getZ() + Math.sin(helixAngle) * 0.4;
            double y = pos.getY() + 0.5 + (imprintProgress % 40) * 0.05;
            serverLevel.sendParticles(Objects.requireNonNull(ParticleTypes.PORTAL), x, y, z, 1, 0, 0, 0, 0);
        }

        // Ambient sound (every 10 ticks)
        if (imprintProgress % 10 == 0) {
            float pitch = 1.5f + (float) imprintProgress / 60.0f * 0.5f;
            serverLevel.playSound(null, pos, Objects.requireNonNull(SoundEvents.BEACON_AMBIENT),
                SoundSource.BLOCKS, 0.3f, pitch);
        }
    }

    private void completeImprinting() {
        var lvl = level;
        LivingEntity target = currentTarget;
        NeurocellAccess neurocell = connectedNeurocell;
        if (lvl == null || target == null || neurocell == null) {
            resetImprinting();
            return;
        }

        // Imprint entity DNA onto the bioscanner in the neurocell
        imprintEntityDNA(neurocell, target);

        // Spawn completion effects
        spawnCompletionEffects(target);

        // CRITICAL: Kill/discard the entity after imprinting
        applyImprintingConsequences(target);

        resetImprinting();
    }

    /**
     * Apply consequences of imprinting - the entity dies.
     * Players are killed (with normal death mechanics).
     * Mobs are discarded (removed without drops).
     */
    private void applyImprintingConsequences(LivingEntity entity) {
        if (entity instanceof Player) {
            // Player: full death with drops, respawn mechanics
            entity.kill();
        } else {
            // Mob: silent removal without drops
            entity.discard();
        }
    }

    /**
     * Spawn burst of particles on successful imprint completion.
     */
    private void spawnCompletionEffects(LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos pos = Objects.requireNonNull(worldPosition);

        // Burst of enchanted hit particles around entity
        for (int i = 0; i < 25; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.8;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.8;
            double offsetY = serverLevel.random.nextDouble() * 0.5;
            serverLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.ENCHANTED_HIT),
                entity.getX() + offsetX,
                entity.getY() + offsetY,
                entity.getZ() + offsetZ,
                1, 0, 0.5, 0, 0.05
            );
        }

        // Glow particles at imprinter
        for (int i = 0; i < 15; i++) {
            double offsetX = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 0.5;
            serverLevel.sendParticles(
                Objects.requireNonNull(ParticleTypes.GLOW),
                pos.getX() + 0.5 + offsetX,
                pos.getY() + 0.5,
                pos.getZ() + 0.5 + offsetZ,
                1, 0, 0.3, 0, 0.02
            );
        }

        // Success sounds
        BlockPos entityPos = Objects.requireNonNull(entity.blockPosition());
        serverLevel.playSound(null, entityPos, Objects.requireNonNull(SoundEvents.EXPERIENCE_ORB_PICKUP),
            SoundSource.BLOCKS, 0.8f, 1.5f);
        serverLevel.playSound(null, entityPos, Objects.requireNonNull(SoundEvents.AMETHYST_BLOCK_CHIME),
            SoundSource.BLOCKS, 0.6f, 1.8f);
    }

    /**
     * Write entity data to the bioscanner in the connected neurocell.
     */
    private void imprintEntityDNA(NeurocellAccess neurocell, LivingEntity entity) {
        // Use DevMod's enhanced BioscanData system
        BioscanData data = BioscanData.fromEntity(Objects.requireNonNull(entity));
        neurocell.fillBioscannerFromImprinter(data);
    }

    private void resetImprinting() {
        if (isImprinting) {
            isImprinting = false;
            imprintProgress = 0;
            currentTarget = null;
            connectedNeurocell = null;
            setChanged();
        }
    }

    /**
     * Manually trigger a scan (from player interaction).
     * Shows status messages about the imprinting system.
     */
    public void triggerManualScan(@Nonnull Player player) {
        var lvl = level;
        if (lvl == null || lvl.isClientSide) {
            return;
        }

        if (isImprinting) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.imprinter.busy")
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
            return;
        }

        // Check for connected neurocell with empty bioscanner
        NeurocellAccess neurocell = NeurolinkConnector.findConnectedNeurocellWithEmptyBioscanner(
            lvl, Objects.requireNonNull(worldPosition));
        if (neurocell == null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.imprinter.no_neurocell")
                    .withStyle(ChatFormatting.RED)),
                true
            );
            return;
        }

        // Check for entity on block
        LivingEntity entity = findEntityOnImprinter();
        if (entity == null) {
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.imprinter.no_target")
                    .withStyle(ChatFormatting.YELLOW)),
                true
            );
            return;
        }

        // If player is on the block, start scanning them
        if (entity.equals(player)) {
            startImprinting(player, neurocell);
        } else {
            startImprinting(entity, neurocell);
            player.displayClientMessage(
                Objects.requireNonNull(Component.translatable("message.devmod.imprinter.scanning", entity.getName())
                    .withStyle(ChatFormatting.GREEN)),
                true
            );
        }
    }

    // NBT Persistence

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("ImprintProgress", imprintProgress);
        tag.putBoolean("IsImprinting", isImprinting);
        tag.putString("TargetName", Objects.requireNonNull(targetName));
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        imprintProgress = tag.getInt("ImprintProgress");
        isImprinting = tag.getBoolean("IsImprinting");
        targetName = Objects.requireNonNull(tag.getString("TargetName"));
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("ImprintProgress", imprintProgress);
        tag.putBoolean("IsImprinting", isImprinting);
        tag.putString("TargetName", Objects.requireNonNull(targetName));
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
