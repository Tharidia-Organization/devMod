package com.devmod.hologram.block.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.devmod.hologram.HologramBlockEntities;
import com.devmod.hologram.client.renderer.HologramEntityData;
import com.devmod.hologram.client.renderer.HologramMesh;
import com.devmod.hologram.client.renderer.HologramVBO;
import com.devmod.hologram.data.EntityFilterType;
import com.devmod.hologram.data.HologramFilter;

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

    // Scan size options (expanded for larger areas)
    private static final int[] SCAN_SIZES = {16, 32, 48, 64, 96, 128, 192, 256};

    // Persisted settings
    private int scanSize = DEFAULT_SCAN_SIZE;
    private int blockSize = DEFAULT_BLOCK_SIZE;
    private boolean transparentMode = true;
    private boolean rotationEnabled = true;
    private boolean filterHighlightOnly = false; // Show only filtered blocks
    private EnumSet<HologramFilter> activeFilters = HologramFilter.getDefaultFilters();
    private boolean texturedMode = false; // Render with real block textures

    // Entity rendering settings
    private boolean showEntities = false;
    private boolean fullEntityModels = true; // Default: 3D models
    private int maxEntityModels = 50; // Configurable limit (range: 10-200)
    private EnumSet<EntityFilterType> activeEntityFilters = EntityFilterType.getDefaultFilters();

    // Y-Slice settings (X-ray mode)
    private boolean ySliceEnabled = false;
    private int ySliceLevel = 64; // Default Y level
    private int ySliceThickness = 1; // 1 = single layer, 3/5/7 = multiple layers

    // Configuration presets (5 slots, individual elements may be null)
    private static final int MAX_PRESETS = 5;
    private final CompoundTag[] presets = new CompoundTag[MAX_PRESETS];

    // Entity cache (transient, client-side only)
    @Nullable
    private transient List<HologramEntityData> cachedEntities = null;
    private transient long lastEntityCacheTime = -1;
    private static final long ENTITY_CACHE_TTL_TICKS = 2;

    // Scan region bounds (calculated from scanSize and block position)
    private int scanMinX;
    private int scanMaxX;
    private int scanMinZ;
    private int scanMaxZ;
    private boolean regionValid = false;

    // Transient render state (client-side only)
    private transient BuildState buildState = BuildState.EMPTY;
    @Nullable
    private transient CompletableFuture<HologramMesh> buildTask = null;
    @Nullable
    private transient HologramMesh mesh = null;
    @Nullable
    private transient HologramVBO vbo = null;

    // Animation state (client-side only)
    private transient long animationStartTime = -1;
    private transient float meshMaxY = 64.0f;
    private transient int meshOriginY = 0; // Y origin used by mesh (for entity coordinate alignment)
    private static final float FADE_IN_DURATION = 2.0f; // seconds

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
        invalidateEntityCache(); // Entity positions are relative to scan origin

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
        animationStartTime = -1; // Reset animation
        if (vbo != null) {
            vbo.close();
            vbo = null;
        }
    }

    // === Animation State ===

    /**
     * Start the fade-in animation. Called when mesh is uploaded.
     */
    public void startAnimation(long currentTime) {
        this.animationStartTime = currentTime;
    }

    /**
     * Get the fade-in progress (0 = just started, 1 = complete).
     */
    public float getFadeInProgress(long currentTime, float partialTick) {
        if (animationStartTime < 0) {
            return 1.0f; // No animation, fully visible
        }
        float elapsed = (currentTime - animationStartTime + partialTick) / 20.0f; // Convert ticks to seconds
        return Math.min(1.0f, elapsed / FADE_IN_DURATION);
    }

    /**
     * Check if fade-in animation is complete.
     */
    public boolean isAnimationComplete(long currentTime) {
        if (animationStartTime < 0) return true;
        float elapsed = (currentTime - animationStartTime) / 20.0f;
        return elapsed >= FADE_IN_DURATION;
    }

    /**
     * Set the maximum Y height of the mesh (for shader normalization).
     */
    public void setMeshMaxY(float maxY) {
        this.meshMaxY = maxY;
    }

    /**
     * Get the maximum Y height of the mesh.
     */
    public float getMeshMaxY() {
        return meshMaxY;
    }

    /**
     * Set the Y origin of the mesh (minimum Y of actual terrain).
     * Used to align entity coordinates with mesh coordinates.
     */
    public void setMeshOriginY(int originY) {
        this.meshOriginY = originY;
    }

    /**
     * Get the Y origin of the mesh.
     */
    public int getMeshOriginY() {
        return meshOriginY;
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

    // === Filter Settings ===

    /**
     * Get the active filters for highlighting blocks.
     */
    public EnumSet<HologramFilter> getActiveFilters() {
        return activeFilters;
    }

    /**
     * Set the active filters.
     */
    public void setActiveFilters(EnumSet<HologramFilter> filters) {
        this.activeFilters = filters != null ? filters : EnumSet.noneOf(HologramFilter.class);
        triggerRebuild();
    }

    /**
     * Toggle a specific filter on/off.
     */
    public void toggleFilter(HologramFilter filter) {
        if (activeFilters.contains(filter)) {
            activeFilters.remove(filter);
        } else {
            activeFilters.add(filter);
        }
        triggerRebuild();
    }

    /**
     * Check if only filtered blocks should be shown.
     */
    public boolean isFilterHighlightOnly() {
        return filterHighlightOnly;
    }

    /**
     * Set whether to show only filtered blocks or highlight them.
     */
    public void setFilterHighlightOnly(boolean highlightOnly) {
        this.filterHighlightOnly = highlightOnly;
        triggerRebuild();
    }

    /**
     * Toggle between highlight mode and filter-only mode.
     */
    public void toggleFilterMode() {
        filterHighlightOnly = !filterHighlightOnly;
        triggerRebuild();
    }

    // === Textured Mode Settings ===

    /**
     * Check if textured mode is enabled (render with real block textures).
     */
    public boolean isTexturedMode() {
        return texturedMode;
    }

    /**
     * Set textured mode.
     */
    public void setTexturedMode(boolean textured) {
        if (this.texturedMode != textured) {
            this.texturedMode = textured;
            triggerRebuild();
        }
    }

    /**
     * Toggle textured mode.
     */
    public void toggleTexturedMode() {
        setTexturedMode(!texturedMode);
    }

    // === Entity Rendering Settings ===

    /**
     * Check if entity rendering is enabled.
     */
    public boolean isShowEntities() {
        return showEntities;
    }

    /**
     * Set whether to show entities in the hologram.
     */
    public void setShowEntities(boolean show) {
        if (this.showEntities != show) {
            this.showEntities = show;
            invalidateEntityCache();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            }
        }
    }

    /**
     * Check if full 3D entity models are enabled.
     */
    public boolean isFullEntityModels() {
        return fullEntityModels;
    }

    /**
     * Set whether to render full 3D entity models.
     */
    public void setFullEntityModels(boolean full) {
        if (this.fullEntityModels != full) {
            this.fullEntityModels = full;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            }
        }
    }

    /**
     * Get the maximum number of entities to render as 3D models.
     */
    public int getMaxEntityModels() {
        return maxEntityModels;
    }

    /**
     * Set the maximum number of entities to render as 3D models.
     * Clamped to range 10-200.
     */
    public void setMaxEntityModels(int max) {
        int clamped = Mth.clamp(max, 10, 200);
        if (this.maxEntityModels != clamped) {
            this.maxEntityModels = clamped;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
            }
        }
    }

    /**
     * Get the active entity type filters.
     */
    public EnumSet<EntityFilterType> getActiveEntityFilters() {
        return activeEntityFilters;
    }

    /**
     * Set the active entity type filters.
     */
    public void setActiveEntityFilters(EnumSet<EntityFilterType> filters) {
        this.activeEntityFilters = filters != null ? filters : EnumSet.noneOf(EntityFilterType.class);
        invalidateEntityCache();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    /**
     * Toggle a specific entity filter on/off.
     */
    public void toggleEntityFilter(EntityFilterType filter) {
        if (activeEntityFilters.contains(filter)) {
            activeEntityFilters.remove(filter);
        } else {
            activeEntityFilters.add(filter);
        }
        invalidateEntityCache();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    /**
     * Invalidate the entity cache (forces refresh on next query).
     */
    public void invalidateEntityCache() {
        cachedEntities = null;
        lastEntityCacheTime = -1;
    }

    // === Y-Slice Settings ===

    /**
     * Check if Y-slice mode is enabled.
     */
    public boolean isYSliceEnabled() {
        return ySliceEnabled;
    }

    /**
     * Set Y-slice mode enabled state.
     */
    public void setYSliceEnabled(boolean enabled) {
        if (this.ySliceEnabled != enabled) {
            this.ySliceEnabled = enabled;
            triggerRebuild();
        }
    }

    /**
     * Get the Y-slice level.
     */
    public int getYSliceLevel() {
        return ySliceLevel;
    }

    /**
     * Set the Y-slice level.
     */
    public void setYSliceLevel(int level) {
        if (this.ySliceLevel != level) {
            this.ySliceLevel = level;
            if (ySliceEnabled) {
                triggerRebuild();
            }
        }
    }

    /**
     * Get the Y-slice thickness (number of layers to show).
     */
    public int getYSliceThickness() {
        return ySliceThickness;
    }

    /**
     * Set the Y-slice thickness.
     * Valid values: 1, 3, 5, 7 (odd numbers for symmetric layers).
     */
    public void setYSliceThickness(int thickness) {
        int clamped = Mth.clamp(thickness, 1, 7);
        // Snap to odd numbers
        if (clamped % 2 == 0) clamped++;
        if (this.ySliceThickness != clamped) {
            this.ySliceThickness = clamped;
            if (ySliceEnabled) {
                triggerRebuild();
            }
        }
    }

    // === Configuration Presets ===

    /**
     * Save current configuration to a preset slot.
     *
     * @param slot Preset slot (0 to MAX_PRESETS-1)
     */
    public void savePreset(int slot) {
        if (slot < 0 || slot >= MAX_PRESETS) return;

        CompoundTag tag = new CompoundTag();
        tag.putInt("ScanSize", scanSize);
        tag.putInt("BlockSize", blockSize);
        tag.putBoolean("TransparentMode", transparentMode);
        tag.putBoolean("RotationEnabled", rotationEnabled);
        tag.putBoolean("FilterHighlightOnly", filterHighlightOnly);
        tag.putInt("ActiveFilters", filtersToInt(activeFilters));
        tag.putBoolean("TexturedMode", texturedMode);
        tag.putBoolean("ShowEntities", showEntities);
        tag.putBoolean("FullEntityModels", fullEntityModels);
        tag.putInt("MaxEntityModels", maxEntityModels);
        tag.putInt("ActiveEntityFilters", EntityFilterType.toBitmask(activeEntityFilters));
        tag.putBoolean("YSliceEnabled", ySliceEnabled);
        tag.putInt("YSliceLevel", ySliceLevel);
        tag.putInt("YSliceThickness", ySliceThickness);

        presets[slot] = tag;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 2);
        }
    }

    /**
     * Load configuration from a preset slot.
     *
     * @param slot Preset slot (0 to MAX_PRESETS-1)
     * @return true if preset was loaded, false if slot is empty or invalid
     */
    public boolean loadPreset(int slot) {
        if (slot < 0 || slot >= MAX_PRESETS || presets[slot] == null) {
            return false;
        }

        CompoundTag tag = presets[slot];

        // Track if scan size changed (requires region rebuild)
        int oldScanSize = scanSize;

        scanSize = tag.getInt("ScanSize");
        blockSize = tag.getInt("BlockSize");
        transparentMode = tag.getBoolean("TransparentMode");
        rotationEnabled = tag.getBoolean("RotationEnabled");
        filterHighlightOnly = tag.getBoolean("FilterHighlightOnly");
        activeFilters = intToFilters(tag.getInt("ActiveFilters"));
        texturedMode = tag.getBoolean("TexturedMode");
        showEntities = tag.getBoolean("ShowEntities");
        fullEntityModels = tag.getBoolean("FullEntityModels");
        maxEntityModels = Mth.clamp(tag.getInt("MaxEntityModels"), 10, 200);
        activeEntityFilters = EntityFilterType.fromBitmask(tag.getInt("ActiveEntityFilters"));
        ySliceEnabled = tag.getBoolean("YSliceEnabled");
        ySliceLevel = tag.getInt("YSliceLevel");
        ySliceThickness = Mth.clamp(tag.getInt("YSliceThickness"), 1, 7);

        // Rebuild scan region if scan size changed
        if (oldScanSize != scanSize) {
            setupScanRegion();
        } else {
            // Just trigger rebuild for other changes
            triggerRebuild();
        }

        return true;
    }

    /**
     * Check if a preset slot has saved data.
     *
     * @param slot Preset slot (0 to MAX_PRESETS-1)
     * @return true if the slot contains a saved preset
     */
    public boolean hasPreset(int slot) {
        return slot >= 0 && slot < MAX_PRESETS && presets[slot] != null;
    }

    /**
     * Get the maximum number of preset slots.
     */
    public static int getMaxPresets() {
        return MAX_PRESETS;
    }

    /**
     * Get entities within the scan region for rendering.
     * Uses caching to avoid per-frame queries.
     *
     * @param level    The level to query entities from
     * @param gameTime Current game time in ticks
     * @return List of entity data for rendering, or null if entities are disabled
     */
    @Nullable
    public List<HologramEntityData> getEntitiesForRendering(Level level, long gameTime) {
        if (!showEntities) {
            return null;
        }

        // Check cache validity
        if (cachedEntities != null && gameTime - lastEntityCacheTime < ENTITY_CACHE_TTL_TICKS) {
            return cachedEntities;
        }

        // Build AABB for the scan region
        AABB scanBox = new AABB(
            scanMinX, level.getMinBuildHeight(), scanMinZ,
            scanMaxX + 1, level.getMaxBuildHeight(), scanMaxZ + 1
        );

        // Query entities in the region
        List<Entity> entities = level.getEntities((Entity) null, scanBox, Entity::isAlive);

        // DEBUG: Log entity query results (use debug level to avoid performance impact)
        if (gameTime % 100 == 0) {
            org.slf4j.LoggerFactory.getLogger("Hologram").debug(
                "[Entities] AABB query: {} raw entities in scan region, activeFilters={}",
                entities.size(), activeEntityFilters
            );
        }

        // Convert to lightweight data with relative coordinates
        // CRITICAL: Use meshOriginY to match mesh coordinate system
        // The mesh uses actual terrain minY, not level.getMinBuildHeight()
        double originX = scanMinX;
        double originY = meshOriginY;
        double originZ = scanMinZ;

        // Calculate Y-slice bounds if enabled
        int ySliceMin = ySliceEnabled ? ySliceLevel - ySliceThickness / 2 : Integer.MIN_VALUE;
        int ySliceMax = ySliceEnabled ? ySliceLevel + ySliceThickness / 2 : Integer.MAX_VALUE;

        List<HologramEntityData> result = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            // Apply Y-slice filter (check entity's actual Y position)
            if (ySliceEnabled) {
                int entityY = (int) entity.getY();
                if (entityY < ySliceMin || entityY > ySliceMax) {
                    continue;
                }
            }

            HologramEntityData data = HologramEntityData.fromEntity(entity, originX, originY, originZ);
            // Apply entity type filter
            if (activeEntityFilters.contains(data.filterType())) {
                result.add(data);
            }
        }

        // Sort by distance from center (for consistent rendering order when limited)
        float centerX = (scanMaxX - scanMinX + 1) / 2.0f;
        float centerZ = (scanMaxZ - scanMinZ + 1) / 2.0f;
        result.sort((a, b) -> {
            float distA = (a.relativeX() - centerX) * (a.relativeX() - centerX) +
                          (a.relativeZ() - centerZ) * (a.relativeZ() - centerZ);
            float distB = (b.relativeX() - centerX) * (b.relativeX() - centerX) +
                          (b.relativeZ() - centerZ) * (b.relativeZ() - centerZ);
            return Float.compare(distA, distB);
        });

        // Cache the result
        cachedEntities = result;
        lastEntityCacheTime = gameTime;

        return cachedEntities;
    }

    /**
     * Trigger a mesh rebuild (for filter changes).
     */
    private void triggerRebuild() {
        cancelBuildTask();
        clearAllMeshData();
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
        tag.putBoolean("FilterHighlightOnly", filterHighlightOnly);
        tag.putInt("ActiveFilters", filtersToInt(activeFilters));
        tag.putBoolean("TexturedMode", texturedMode);
        tag.putBoolean("ShowEntities", showEntities);
        tag.putBoolean("FullEntityModels", fullEntityModels);
        tag.putInt("MaxEntityModels", maxEntityModels);
        tag.putInt("ActiveEntityFilters", EntityFilterType.toBitmask(activeEntityFilters));
        tag.putBoolean("YSliceEnabled", ySliceEnabled);
        tag.putInt("YSliceLevel", ySliceLevel);
        tag.putInt("YSliceThickness", ySliceThickness);

        // Save presets
        CompoundTag presetsTag = new CompoundTag();
        for (int i = 0; i < MAX_PRESETS; i++) {
            if (presets[i] != null) {
                presetsTag.put("Preset" + i, presets[i].copy());
            }
        }
        tag.put("Presets", presetsTag);
    }

    private static int filtersToInt(EnumSet<HologramFilter> filters) {
        int result = 0;
        for (HologramFilter filter : filters) {
            result |= (1 << filter.ordinal());
        }
        return result;
    }

    private static EnumSet<HologramFilter> intToFilters(int value) {
        EnumSet<HologramFilter> result = EnumSet.noneOf(HologramFilter.class);
        for (HologramFilter filter : HologramFilter.values()) {
            if ((value & (1 << filter.ordinal())) != 0) {
                result.add(filter);
            }
        }
        return result;
    }

    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        int oldScanSize = scanSize;
        scanSize = tag.contains("ScanSize") ? tag.getInt("ScanSize") : DEFAULT_SCAN_SIZE;
        blockSize = tag.contains("BlockSize") ? tag.getInt("BlockSize") : DEFAULT_BLOCK_SIZE;
        transparentMode = !tag.contains("TransparentMode") || tag.getBoolean("TransparentMode");
        rotationEnabled = !tag.contains("RotationEnabled") || tag.getBoolean("RotationEnabled");
        filterHighlightOnly = tag.contains("FilterHighlightOnly") && tag.getBoolean("FilterHighlightOnly");
        activeFilters = tag.contains("ActiveFilters") ? intToFilters(tag.getInt("ActiveFilters")) : HologramFilter.getDefaultFilters();
        texturedMode = tag.contains("TexturedMode") && tag.getBoolean("TexturedMode");
        showEntities = tag.contains("ShowEntities") && tag.getBoolean("ShowEntities");
        fullEntityModels = !tag.contains("FullEntityModels") || tag.getBoolean("FullEntityModels");
        maxEntityModels = tag.contains("MaxEntityModels") ? Mth.clamp(tag.getInt("MaxEntityModels"), 10, 200) : 50;
        activeEntityFilters = tag.contains("ActiveEntityFilters")
            ? EntityFilterType.fromBitmask(tag.getInt("ActiveEntityFilters"))
            : EntityFilterType.getDefaultFilters();
        ySliceEnabled = tag.contains("YSliceEnabled") && tag.getBoolean("YSliceEnabled");
        ySliceLevel = tag.contains("YSliceLevel") ? tag.getInt("YSliceLevel") : 64;
        ySliceThickness = tag.contains("YSliceThickness") ? Mth.clamp(tag.getInt("YSliceThickness"), 1, 7) : 1;

        // Load presets
        if (tag.contains("Presets", CompoundTag.TAG_COMPOUND)) {
            CompoundTag presetsTag = tag.getCompound("Presets");
            for (int i = 0; i < MAX_PRESETS; i++) {
                String key = "Preset" + i;
                if (presetsTag.contains(key, CompoundTag.TAG_COMPOUND)) {
                    presets[i] = presetsTag.getCompound(key).copy();
                } else {
                    presets[i] = null;
                }
            }
        }

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

        // Invalidate entity cache after loading settings (ensures fresh data on sync)
        invalidateEntityCache();
    }

    @Override
    @Nonnull
    public CompoundTag getUpdateTag(@Nonnull HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("ScanSize", scanSize);
        tag.putInt("BlockSize", blockSize);
        tag.putBoolean("TransparentMode", transparentMode);
        tag.putBoolean("RotationEnabled", rotationEnabled);
        tag.putBoolean("FilterHighlightOnly", filterHighlightOnly);
        tag.putInt("ActiveFilters", filtersToInt(activeFilters));
        tag.putBoolean("TexturedMode", texturedMode);
        // Entity rendering settings - CRITICAL: must sync to client for entity rendering
        tag.putBoolean("ShowEntities", showEntities);
        tag.putBoolean("FullEntityModels", fullEntityModels);
        tag.putInt("MaxEntityModels", maxEntityModels);
        tag.putInt("ActiveEntityFilters", EntityFilterType.toBitmask(activeEntityFilters));
        // Y-slice settings
        tag.putBoolean("YSliceEnabled", ySliceEnabled);
        tag.putInt("YSliceLevel", ySliceLevel);
        tag.putInt("YSliceThickness", ySliceThickness);
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
