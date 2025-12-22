package com.frenkvs.devmod;

import com.frenkvs.devmod.actions.DevModActions;
import com.frenkvs.devmod.integration.ModIntegrationManager;
import com.devmod.arena.registry.TemplateRegistryBootstrap;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.devmod.arena.config.ArenaTemplateConfig;
import com.devmod.arena.telemetry.ArenaTelemetry;
import com.frenkvs.devmod.endurance.EnduranceQuestManager;
import com.frenkvs.devmod.ui.editor.core.EditorConfig;
import com.frenkvs.devmod.ui.editor.systems.PresetRegistry;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
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
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> USABLE_COMPONENTS = UsableComponents.COMPONENTS;
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> FOOD_COMPONENTS = FoodComponents.COMPONENTS;
    public static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> FUEL_COMPONENTS = FuelComponents.COMPONENTS;

    // 3. "VIEWER_ITEM" ITEM
    public static final DeferredHolder<Item, Item> VIEWER_ITEM = ITEMS.register("viewer_item", () -> new Item(new Item.Properties()));

    // Arena Template registry (bootstrap)
    private static ArenaTemplateRegistry ARENA_TEMPLATE_REGISTRY;
    private static TemplateRegistryBootstrap ARENA_BOOTSTRAP;

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
        USABLE_COMPONENTS.register(eventBus);
        FOOD_COMPONENTS.register(eventBus);
        FUEL_COMPONENTS.register(eventBus);

        // Register configuration
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, EditorClientConfig.SPEC);

        // Register config reload listener for runtime updates
        eventBus.addListener(DevMod::onConfigReload);
        eventBus.addListener(DevMod::onConfigLoading);

        // Initialize external mod integration (Pehkui, Better Combat, etc.)
        ModIntegrationManager.init();

        // Initialize PresetRegistry (hierarchical preset system)
        PresetRegistry.INSTANCE.loadFromConfig();

        DevModActions.registerCommon();

        // Network payload registration (mod bus)
        eventBus.addListener(DebugNetworkHandler::registerPayloads);

        // NOTE: Keybinds are now registered in DevModClient (client-only class)

        // Arena Template bootstrap (L1 registry)
        initArenaTemplateRegistry();

        LOGGER.info("DevMod loaded successfully!");
    }

    /**
     * Handle config loading events to initialize caches.
     * Called when config is first loaded.
     */
    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == EditorClientConfig.SPEC) {
            LOGGER.debug("[DevMod] Client config loaded, initializing EditorConfig cache...");
            EditorConfig.initCache();
        }
    }

    /**
     * Handle config reload events for runtime config updates.
     * Called when any devmod config file is reloaded.
     */
    private static void onConfigReload(ModConfigEvent.Reloading event) {
        // Only handle our client config for editor settings
        if (event.getConfig().getSpec() == EditorClientConfig.SPEC) {
            LOGGER.debug("[DevMod] Client config reloaded, checking for changes...");
            EditorConfig.onConfigReload();
        }
        // Refresh arena template config snapshot on common config reloads
        if (event.getConfig().getSpec() == Config.SPEC && ARENA_BOOTSTRAP != null) {
            ArenaTemplateConfig newConfig = ArenaTemplateConfig.load();
            ARENA_BOOTSTRAP.applyConfig(newConfig);
            EnduranceQuestManager.INSTANCE.applyArenaConfig(newConfig);
            LOGGER.info("[DevMod] ArenaTemplateConfig reloaded and applied");
            com.frenkvs.devmod.arena.ArenaCommandEvents.onArenaConfigReload(newConfig);
        }
    }

    /**
     * Initializes the arena template registry using default bootstrap.
     */
    private void initArenaTemplateRegistry() {
        try {
            ArenaTelemetry telemetry = new ArenaTelemetry();
            ARENA_BOOTSTRAP = TemplateRegistryBootstrap.createDefault(telemetry);
            ARENA_BOOTSTRAP.initialize();
            ARENA_TEMPLATE_REGISTRY = ARENA_BOOTSTRAP.registry();
            LOGGER.info("[DevMod] ArenaTemplateRegistry initialized from {}", ARENA_BOOTSTRAP.templateDirectory());
        } catch (Exception e) {
            LOGGER.error("[DevMod] Failed to initialize ArenaTemplateRegistry", e);
        }
    }

    /**
     * Exposes the arena template registry for other components.
     */
    public static ArenaTemplateRegistry getArenaTemplateRegistry() {
        return ARENA_TEMPLATE_REGISTRY;
    }

    /**
     * Exposes the template bootstrap (config + registry + flags) for components
     * that need config-aware reloads or access to the current snapshot.
     */
    public static TemplateRegistryBootstrap getArenaTemplateBootstrap() {
        return ARENA_BOOTSTRAP;
    }
}
