package com.devmod.hologram.block.entity;

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.devmod.hologram.HologramBlockEntities;
import com.devmod.hologram.client.renderer.HologramMesh;
import com.devmod.hologram.client.renderer.HologramVBO;

/**
 * Block entity for the hologram projector.
 * Stores scan settings and holds transient mesh/VBO data for rendering.
 *
 * <p>Settings are persisted in NBT. Mesh and VBO are rebuilt on load.
 */
public class HologramProjectorBlockEntity extends BlockEntity {
    // Default settings
    private static final int DEFAULT_SCAN_SIZE = 32;
    private static final int DEFAULT_BLOCK_SIZE = 1;

    // Scan size options
    private static final int[] SCAN_SIZES = {16, 32, 48, 64};

    // Persisted settings
    private int scanSize = DEFAULT_SCAN_SIZE;
    private int blockSize = DEFAULT_BLOCK_SIZE;
    private boolean transparentMode = true;
    private boolean rotationEnabled = true;

    // Scan region bounds (calculated from scanSize and block position)
    private int scanMinX;
    private int scanMaxX;
    private int scanMinZ;
    private int scanMaxZ;
    private boolean regionValid = false;

    // Transient render state (client-side only)
    private transient BuildState buildState = BuildState.EMPTY;
    private transient CompletableFuture<HologramMesh> buildTask = null;
    private transient HologramMesh mesh = null;
    private transient HologramVBO vbo = null;

    public HologramProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(HologramBlockEntities.HOLOGRAM_PROJECTOR.get(), pos, state);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        cancelBuildTask();
        clearAllMeshData();
    }

    /**
     * Setup the scan region based on current position and scan size.
     * Called on block placement and when scan size changes.
     */
    public void setupScanRegion() {
        BlockPos center = getBlockPos();
        int halfSize = scanSize / 2;

        scanMinX = center.getX() - halfSize;
        scanMaxX = center.getX() + halfSize - 1;
        scanMinZ = center.getZ() - halfSize;
        scanMaxZ = center.getZ() + halfSize - 1;

        int actualWidth = scanMaxX - scanMinX + 1;
        int actualDepth = scanMaxZ - scanMinZ + 1;
        regionValid = actualWidth == scanSize && actualDepth == scanSize;

        // Reset mesh state to trigger rebuild
        cancelBuildTask();
        clearAllMeshData();

        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    // === Build State Management ===

    public BuildState getBuildState() {
        return buildState;
    }

    public void setBuildState(BuildState state) {
        this.buildState = state;
    }

    @Nullable
    public CompletableFuture<HologramMesh> getBuildTask() {
        return buildTask;
    }

    public void setBuildTask(@Nullable CompletableFuture<HologramMesh> task) {
        this.buildTask = task;
    }

    @Nullable
    public HologramMesh getMesh() {
        return mesh;
    }

    public void setMesh(@Nullable HologramMesh mesh) {
        this.mesh = mesh;
    }

    @Nullable
    public HologramVBO getVBO() {
        return vbo;
    }

    public void setVBO(@Nullable HologramVBO vbo) {
        this.vbo = vbo;
    }

    private void cancelBuildTask() {
        if (buildTask != null && !buildTask.isDone()) {
            buildTask.cancel(true);
            buildTask = null;
        }
    }

    private void clearAllMeshData() {
        buildState = BuildState.EMPTY;
        mesh = null;
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
    }

    // === Settings ===

    public boolean isTransparentMode() {
        return transparentMode;
    }

    public void toggleTransparency() {
        transparentMode = !transparentMode;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    public boolean isRotationEnabled() {
        return rotationEnabled;
    }

    public void toggleRotation() {
        rotationEnabled = !rotationEnabled;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    public int getScanSize() {
        return scanSize;
    }

    public void setScanSize(int newSize) {
        scanSize = newSize;
        setupScanRegion();
    }

    /**
     * Cycle to the next scan size option.
     */
    public void cycleScanSize() {
        int currentIndex = 0;
        for (int i = 0; i < SCAN_SIZES.length; i++) {
            if (SCAN_SIZES[i] == scanSize) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % SCAN_SIZES.length;
        setScanSize(SCAN_SIZES[nextIndex]);
    }

    public int getBlockSize() {
        return blockSize;
    }

    public void setBlockSize(int newSize) {
        blockSize = newSize;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    // === Scan Region ===

    public int getScanMinX() { return scanMinX; }
    public int getScanMaxX() { return scanMaxX; }
    public int getScanMinZ() { return scanMinZ; }
    public int getScanMaxZ() { return scanMaxZ; }
    public boolean hasValidRegion() { return regionValid; }

    // === NBT Persistence ===

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ScanSize", scanSize);
        tag.putInt("BlockSize", blockSize);
        tag.putBoolean("TransparentMode", transparentMode);
        tag.putBoolean("RotationEnabled", rotationEnabled);
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        int oldScanSize = scanSize;
        scanSize = tag.contains("ScanSize") ? tag.getInt("ScanSize") : DEFAULT_SCAN_SIZE;
        blockSize = tag.contains("BlockSize") ? tag.getInt("BlockSize") : DEFAULT_BLOCK_SIZE;
        transparentMode = !tag.contains("TransparentMode") || tag.getBoolean("TransparentMode");
        rotationEnabled = !tag.contains("RotationEnabled") || tag.getBoolean("RotationEnabled");

        // Recalculate region bounds
        if (oldScanSize != scanSize) {
            setupScanRegion();
        } else {
            BlockPos center = getBlockPos();
            int halfSize = scanSize / 2;
            scanMinX = center.getX() - halfSize;
            scanMaxX = center.getX() + halfSize - 1;
            scanMinZ = center.getZ() - halfSize;
            scanMaxZ = center.getZ() + halfSize - 1;
            regionValid = (scanMaxX - scanMinX + 1) == scanSize && (scanMaxZ - scanMinZ + 1) == scanSize;
        }
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("ScanSize", scanSize);
        tag.putInt("BlockSize", blockSize);
        tag.putBoolean("TransparentMode", transparentMode);
        tag.putBoolean("RotationEnabled", rotationEnabled);
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Build state for the hologram rendering pipeline.
     */
    public enum BuildState {
        /** No mesh built yet */
        EMPTY,
        /** Mesh is being built async */
        BUILDING,
        /** Mesh is ready, needs VBO upload */
        READY,
        /** VBO uploaded and ready to render */
        UPLOADED
    }
}
