package com.frenkvs.devmod;

import com.frenkvs.devmod.integration.ModIntegrationManager;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import java.util.Objects;
import com.frenkvs.devmod.debug.DebugNetworkHandler;

@Mod("devmod")
public class DevMod {

    // Logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Mod ID (all lowercase as required)
    public static final String MODID = "devmod";

    // 1. ITEMS REGISTRY
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // 2. CREATIVE TABS REGISTRY
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Objects.requireNonNull(Registries.CREATIVE_MODE_TAB),
            MODID
    );
    public static final DeferredRegister<Attribute> ATTRIBUTES = ModAttributes.ATTRIBUTES;
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> ARMOR_COMPONENTS = ArmorComponents.COMPONENTS;
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> RANGED_COMPONENTS = RangedComponents.COMPONENTS;
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> WEAPON_COMPONENTS = WeaponComponents.COMPONENTS;

    // 3. "VIEWER_ITEM" ITEM
    public static final DeferredHolder<Item, Item> VIEWER_ITEM = ITEMS.register("viewer_item", () -> new Item(new Item.Properties()));

    // 4. CREATIVE TAB (FIXED)
    // The error was here: inside < > must be "CreativeModeTab", not "EXAMPLE_TAB"
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Objects.requireNonNull(Component.translatable("itemGroup." + MODID)))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> Objects.requireNonNull(VIEWER_ITEM.get().getDefaultInstance()))
            .displayItems((parameters, output) -> {
                output.accept(Objects.requireNonNull(VIEWER_ITEM.get()));
            }).build());

    public DevMod(IEventBus modEventBus, ModContainer modContainer) {
        IEventBus eventBus = Objects.requireNonNull(modEventBus);
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
        ATTRIBUTES.register(eventBus);
        ARMOR_COMPONENTS.register(eventBus);
        RANGED_COMPONENTS.register(eventBus);
        WEAPON_COMPONENTS.register(eventBus);

        // Register configuration
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Initialize external mod integration (Pehkui, Better Combat, etc.)
        ModIntegrationManager.init();

        // Network payload registration (mod bus)
        eventBus.addListener(DebugNetworkHandler::registerPayloads);

        // Register keybinds only on client side
        if (FMLEnvironment.dist == Dist.CLIENT) {
            eventBus.addListener(DevMod::registerKeyMappings);
            LOGGER.info("[DevMod] Client keybind registration scheduled");
        }

        LOGGER.info("DevMod loaded successfully!");
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        LOGGER.info("[DevMod] Registering keybinds including N for QA Testing");
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_SETTINGS_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_WEAPON_EDITOR_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_DEBUG_OVERLAY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_LIGHT_OVERLAY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_HEATMAP_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_ROOM_BOUNDS_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_PATHFINDING_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_LOS_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_VERTICAL_LEVELS_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_SAFE_SPOT_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_DASHBOARD_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_ATTRIBUTE_MONITOR_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_FPS_TRACKER_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_PROFILER_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_QA_TESTING_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_TESTING_HUB_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_BOSS_PHASE_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_ENTITY_DENSITY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_SKILL_EFFICACY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_SPAWNABILITY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_QUEST_HUD_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.QUEST_COMPLETE_TASK_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_QUEST_EDITOR_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_ECONOMY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_CHUNK_PERF_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_ENDURANCE_QUEST_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.QUEST_CONTINUE_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.QUEST_EXIT_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_PARTY_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TOGGLE_HELP_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.OPEN_RADIAL_MENU_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.INSPECT_MOB_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.TEST_SCREEN_SHAKE_KEY));
        // Ability system keybinds
        event.register(Objects.requireNonNull(KeyInputHandler.DASH_KEY));
        event.register(Objects.requireNonNull(KeyInputHandler.DODGE_KEY));
        LOGGER.info("[DevMod] All keybinds registered successfully");
    }
}
