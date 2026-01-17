package com.devmod.template.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import com.devmod.DevMod;

/**
 * Registry for room templates. Persists to world data.
 */
public class TemplateRegistry extends SavedData {
    private static final String DATA_NAME = "devmod_room_templates";

    private final Map<UUID, RoomTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, RoomTemplate> templatesByStringId = new ConcurrentHashMap<>();
    private final Map<String, ComponentDefinition> components = new ConcurrentHashMap<>();

    private static final Codec<Map<String, RoomTemplate>> TEMPLATES_CODEC =
        Codec.unboundedMap(Codec.STRING, RoomTemplate.CODEC);

    @SuppressWarnings("this-escape")
    public TemplateRegistry() {
        // Initialize built-in components
        initializeBuiltinComponents();
    }

    // =========================================================================
    // TEMPLATE CRUD
    // =========================================================================

    /**
     * Register a new template.
     */
    public void registerTemplate(@Nonnull RoomTemplate template) {
        Objects.requireNonNull(template, "template");
        templates.put(template.id(), template);
        templatesByStringId.put(template.templateId(), template);
        setDirty();
        DevMod.LOGGER.debug("[Template] Registered template: {}", template.templateId());
    }

    /**
     * Update an existing template (with optimistic locking).
     */
    public boolean updateTemplate(@Nonnull UUID id, @Nonnull RoomTemplate template, int expectedRevision) {
        RoomTemplate existing = templates.get(id);
        if (existing == null) {
            return false;
        }
        if (existing.revision() != expectedRevision) {
            DevMod.LOGGER.warn("[Template] Revision conflict for {}: expected {}, got {}",
                template.templateId(), expectedRevision, existing.revision());
            return false;
        }
        templates.put(id, template.withNewRevision());
        templatesByStringId.put(template.templateId(), template.withNewRevision());
        setDirty();
        return true;
    }

    /**
     * Delete a template.
     */
    public boolean deleteTemplate(@Nonnull UUID id) {
        RoomTemplate removed = templates.remove(id);
        if (removed != null) {
            templatesByStringId.remove(removed.templateId());
            setDirty();
            DevMod.LOGGER.debug("[Template] Deleted template: {}", removed.templateId());
            return true;
        }
        return false;
    }

    /**
     * Get a template by UUID.
     */
    public Optional<RoomTemplate> getTemplate(@Nonnull UUID id) {
        return Optional.ofNullable(templates.get(id));
    }

    /**
     * Get a template by string ID.
     */
    public Optional<RoomTemplate> getTemplate(@Nonnull String templateId) {
        return Optional.ofNullable(templatesByStringId.get(templateId));
    }

    /**
     * Get all templates for a zone type.
     */
    public List<RoomTemplate> getTemplatesForZone(@Nonnull String zoneId) {
        List<RoomTemplate> result = new ArrayList<>();
        for (RoomTemplate template : templates.values()) {
            if (template.zoneId().equals(zoneId)) {
                result.add(template);
            }
        }
        return result;
    }

    /**
     * Get all preset (non-custom) templates.
     */
    public List<RoomTemplate> getPresets() {
        List<RoomTemplate> result = new ArrayList<>();
        for (RoomTemplate template : templates.values()) {
            if (!template.isCustom()) {
                result.add(template);
            }
        }
        return result;
    }

    /**
     * Get all custom templates.
     */
    public List<RoomTemplate> getCustomTemplates() {
        List<RoomTemplate> result = new ArrayList<>();
        for (RoomTemplate template : templates.values()) {
            if (template.isCustom()) {
                result.add(template);
            }
        }
        return result;
    }

    /**
     * Get all templates.
     */
    public Collection<RoomTemplate> getAllTemplates() {
        return templates.values();
    }

    /**
     * Get template count.
     */
    public int getTemplateCount() {
        return templates.size();
    }

    // =========================================================================
    // COMPONENT MANAGEMENT
    // =========================================================================

    /**
     * Register a component definition.
     */
    public void registerComponent(@Nonnull ComponentDefinition component) {
        Objects.requireNonNull(component, "component");
        components.put(component.id(), component);
    }

    /**
     * Get a component by ID.
     */
    public Optional<ComponentDefinition> getComponent(@Nonnull String componentId) {
        return Optional.ofNullable(components.get(componentId));
    }

    /**
     * Get all components for a zone.
     */
    public List<ComponentDefinition> getComponentsForZone(@Nonnull String zoneId) {
        List<ComponentDefinition> result = new ArrayList<>();
        for (ComponentDefinition comp : components.values()) {
            if (comp.isCompatibleWith(zoneId)) {
                result.add(comp);
            }
        }
        return result;
    }

    /**
     * Get components by category.
     */
    public List<ComponentDefinition> getComponentsByCategory(@Nonnull ComponentCategory category) {
        List<ComponentDefinition> result = new ArrayList<>();
        for (ComponentDefinition comp : components.values()) {
            if (comp.category() == category) {
                result.add(comp);
            }
        }
        return result;
    }

    /**
     * Get all component definitions.
     */
    public Collection<ComponentDefinition> getAllComponents() {
        return components.values();
    }

    // =========================================================================
    // BUILT-IN COMPONENTS
    // =========================================================================

    @SuppressWarnings("this-escape")
    private void initializeBuiltinComponents() {
        // Display components
        registerComponent(new ComponentDefinition("display_screen_z", "Screen Panel (Z-axis)",
            ComponentCategory.DISPLAY, new net.minecraft.core.Vec3i(4, 3, 1), 12, true, List.of("*")));
        registerComponent(new ComponentDefinition("display_screen_x", "Screen Panel (X-axis)",
            ComponentCategory.DISPLAY, new net.minecraft.core.Vec3i(1, 3, 4), 12, true, List.of("*")));
        registerComponent(new ComponentDefinition("display_hologram", "Hologram Display",
            ComponentCategory.DISPLAY, new net.minecraft.core.Vec3i(3, 4, 3), 16, false, List.of("*")));

        // Console components
        registerComponent(new ComponentDefinition("console_ns", "Console (N-S)",
            ComponentCategory.CONSOLE, new net.minecraft.core.Vec3i(3, 2, 2), 12, false, List.of("*")));
        registerComponent(new ComponentDefinition("console_ew", "Console (E-W)",
            ComponentCategory.CONSOLE, new net.minecraft.core.Vec3i(2, 2, 3), 12, false, List.of("*")));

        // Combat components
        registerComponent(new ComponentDefinition("combat_target_wall", "Target Wall",
            ComponentCategory.COMBAT, new net.minecraft.core.Vec3i(5, 3, 1), 20, true,
            List.of("tutorial", "war_arena", "war_hub")));
        registerComponent(new ComponentDefinition("combat_training_post", "Training Post",
            ComponentCategory.COMBAT, new net.minecraft.core.Vec3i(1, 3, 1), 12, false,
            List.of("tutorial", "war_arena")));
        registerComponent(new ComponentDefinition("combat_arena_ring", "Arena Ring",
            ComponentCategory.COMBAT, new net.minecraft.core.Vec3i(9, 1, 9), 24, false,
            List.of("war_arena", "eventi_torneo")));
        registerComponent(new ComponentDefinition("combat_dummy_pad", "Dummy Pad",
            ComponentCategory.COMBAT, new net.minecraft.core.Vec3i(3, 1, 3), 12, false,
            List.of("tutorial", "war_arena")));

        // Storage components
        registerComponent(new ComponentDefinition("storage_server_rack", "Server Rack",
            ComponentCategory.STORAGE, new net.minecraft.core.Vec3i(2, 3, 1), 12, true,
            List.of("dm_mod", "town_gestionale")));
        registerComponent(new ComponentDefinition("storage_chest_row", "Chest Row",
            ComponentCategory.STORAGE, new net.minecraft.core.Vec3i(4, 2, 1), 12, true,
            List.of("economy_bank", "economy_shop")));

        // Utility components
        registerComponent(new ComponentDefinition("utility_landing_pad", "Landing Pad",
            ComponentCategory.UTILITY, new net.minecraft.core.Vec3i(3, 1, 3), 8, false, List.of("*")));
        registerComponent(new ComponentDefinition("utility_bench_row", "Utility Bench Row",
            ComponentCategory.UTILITY, new net.minecraft.core.Vec3i(6, 2, 2), 16, true, List.of("*")));
        registerComponent(new ComponentDefinition("utility_crafting_station", "Crafting Station",
            ComponentCategory.UTILITY, new net.minecraft.core.Vec3i(3, 2, 3), 12, false,
            List.of("class_crafting", "lab_forging")));

        // Decorative components
        registerComponent(new ComponentDefinition("decorative_pillar", "Corner Pillar",
            ComponentCategory.DECORATIVE, new net.minecraft.core.Vec3i(1, 4, 1), 8, false, List.of("*")));
        registerComponent(new ComponentDefinition("decorative_plinth", "Display Plinth",
            ComponentCategory.DECORATIVE, new net.minecraft.core.Vec3i(3, 2, 3), 12, false, List.of("*")));
        registerComponent(new ComponentDefinition("decorative_banner", "Banner",
            ComponentCategory.DECORATIVE, new net.minecraft.core.Vec3i(1, 3, 1), 8, true, List.of("*")));

        // Functional components
        registerComponent(new ComponentDefinition("functional_portal_frame", "Portal Frame",
            ComponentCategory.FUNCTIONAL, new net.minecraft.core.Vec3i(5, 5, 1), 16, true,
            List.of("gate_progression", "transport")));
        registerComponent(new ComponentDefinition("functional_spawn_pad", "Spawn Pad",
            ComponentCategory.FUNCTIONAL, new net.minecraft.core.Vec3i(5, 1, 5), 12, false,
            List.of("spawn", "tutorial")));

        // Quest components
        registerComponent(new ComponentDefinition("quest_board", "Quest Board",
            ComponentCategory.QUEST, new net.minecraft.core.Vec3i(3, 3, 1), 12, true,
            List.of("quest_hub", "quest_board")));
        registerComponent(new ComponentDefinition("quest_npc_spot", "Quest NPC Spot",
            ComponentCategory.QUEST, new net.minecraft.core.Vec3i(2, 1, 2), 8, false,
            List.of("quest_hub")));

        // Shop components
        registerComponent(new ComponentDefinition("shop_vendor_stall", "Vendor Stall",
            ComponentCategory.SHOP, new net.minecraft.core.Vec3i(4, 3, 2), 12, false,
            List.of("economy_shop")));
        registerComponent(new ComponentDefinition("shop_auction_block", "Auction Block",
            ComponentCategory.SHOP, new net.minecraft.core.Vec3i(3, 2, 3), 12, false,
            List.of("economy_auction")));
        registerComponent(new ComponentDefinition("shop_bank_counter", "Bank Counter",
            ComponentCategory.SHOP, new net.minecraft.core.Vec3i(5, 2, 2), 16, true,
            List.of("economy_bank")));

        // Transport components
        registerComponent(new ComponentDefinition("transport_waypoint", "Transport Waypoint",
            ComponentCategory.TRANSPORT, new net.minecraft.core.Vec3i(3, 4, 3), 12, false,
            List.of("*")));
        registerComponent(new ComponentDefinition("transport_caravan_stop", "Caravan Stop",
            ComponentCategory.TRANSPORT, new net.minecraft.core.Vec3i(5, 1, 5), 16, false,
            List.of("economy_caravan")));

        DevMod.LOGGER.debug("[Template] Initialized {} built-in components", components.size());
    }

    // =========================================================================
    // PERSISTENCE
    // =========================================================================

    @Override
    @Nonnull
    public CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        // Save templates
        Map<String, RoomTemplate> templatesMap = new java.util.HashMap<>();
        for (RoomTemplate template : templates.values()) {
            templatesMap.put(template.id().toString(), template);
        }

        TEMPLATES_CODEC.encodeStart(NbtOps.INSTANCE, templatesMap)
            .resultOrPartial(error -> DevMod.LOGGER.error("[Template] Failed to save templates: {}", error))
            .ifPresent(nbt -> tag.put("templates", Objects.requireNonNull(nbt)));

        tag.putInt("version", 1);
        return tag;
    }

    public static TemplateRegistry load(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries) {
        TemplateRegistry registry = new TemplateRegistry();

        if (tag.contains("templates")) {
            TEMPLATES_CODEC.parse(NbtOps.INSTANCE, tag.get("templates"))
                .resultOrPartial(error -> DevMod.LOGGER.error("[Template] Failed to load templates: {}", error))
                .ifPresent(templatesMap -> {
                    for (RoomTemplate template : templatesMap.values()) {
                        registry.templates.put(template.id(), template);
                        registry.templatesByStringId.put(template.templateId(), template);
                    }
                });
        }

        DevMod.LOGGER.info("[Template] Loaded {} templates", registry.templates.size());
        return registry;
    }

    // =========================================================================
    // STATIC ACCESS
    // =========================================================================

    private static final SavedData.Factory<TemplateRegistry> FACTORY =
        new SavedData.Factory<>(TemplateRegistry::new, TemplateRegistry::load);

    /**
     * Get the template registry for a server level.
     */
    public static TemplateRegistry get(@Nonnull ServerLevel level) {
        DimensionDataStorage storage = level.getServer().overworld().getDataStorage();
        return storage.computeIfAbsent(Objects.requireNonNull(FACTORY), DATA_NAME);
    }

    /**
     * Get the template registry for a server.
     */
    public static TemplateRegistry get(@Nonnull MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(Objects.requireNonNull(FACTORY), DATA_NAME);
    }
}
