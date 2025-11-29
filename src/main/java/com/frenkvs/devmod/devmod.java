package com.frenkvs.devmod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod("devmod")
public class devmod {

    // Logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // ID della mod (tutto minuscolo come richiesto)
    public static final String MODID = "devmod";

    // 1. REGISTRO DEGLI OGGETTI
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // 2. REGISTRO DELLE TAB CREATIVE
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 3. OGGETTO "VIEWER_ITEM"
    public static final DeferredHolder<Item, Item> VIEWER_ITEM = ITEMS.register("viewer_item", () -> new Item(new Item.Properties()));

    // 4. TAB CREATIVA (CORRETTA)
    // L'errore era qui: dentro < > deve esserci "CreativeModeTab", non "EXAMPLE_TAB"
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> VIEWER_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(VIEWER_ITEM.get());
            }).build());

    public devmod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);

        LOGGER.info("Mob Config Viewer caricato correttamente!");
    }
}
