package com.devmod.foundry.client;

import java.util.Objects;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import com.devmod.DevMod;
import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfoLoader;
import com.devmod.foundry.client.model.FoundryPartModelLoader;
import com.devmod.foundry.client.model.FoundryTankModelLoader;
import com.devmod.foundry.client.model.FoundryToolModelLoader;
import com.devmod.foundry.client.renderer.FoundryChannelRenderer;
import com.devmod.foundry.client.renderer.FoundryControllerRenderer;
import com.devmod.foundry.client.renderer.FoundryTankRenderer;
import com.devmod.foundry.client.screen.FoundryControllerScreen;
import com.devmod.foundry.client.screen.FoundryPartBuilderScreen;
import com.devmod.foundry.client.screen.FoundryStencilTableScreen;
import com.devmod.foundry.client.screen.FoundryToolAnvilScreen;
import com.devmod.foundry.client.screen.FoundryToolStationScreen;
import com.devmod.foundry.tool.FoundryToolItems;
import slimeknights.mantle.client.model.NBTKeyModel;
import slimeknights.mantle.client.model.RetexturedModel;
import slimeknights.mantle.client.model.connected.ConnectedModel;
import slimeknights.mantle.client.model.util.ColoredBlockModel;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.tconstruct.library.client.model.DynamicTextureLoader;
import slimeknights.tconstruct.library.client.model.FluidContainerModel;
import slimeknights.tconstruct.library.client.model.UniqueGuiModel;
import slimeknights.tconstruct.library.client.model.block.FluidTextureModel;
import slimeknights.tconstruct.library.client.model.block.TankModel;
import slimeknights.tconstruct.library.client.model.tools.MaterialBlockModel;
import slimeknights.tconstruct.library.client.model.tools.MaterialModel;
import slimeknights.tconstruct.library.client.model.tools.ToolModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelManager;

/**
 * Client-side setup for the Foundry module.
 */
@EventBusSubscriber(modid = DevMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FoundryClientSetup {
    private FoundryClientSetup() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CONTROLLER.get()),
            FoundryControllerRenderer::new
        );
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_CHANNEL.get()),
            FoundryChannelRenderer::new
        );
        event.registerBlockEntityRenderer(
            Objects.requireNonNull(FoundryBlockEntities.FOUNDRY_TANK.get()),
            FoundryTankRenderer::new
        );
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_CONTROLLER.get()), FoundryControllerScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_PART_BUILDER.get()), FoundryPartBuilderScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_TOOL_STATION.get()), FoundryToolStationScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_TOOL_ANVIL.get()), FoundryToolAnvilScreen::new);
        event.register(Objects.requireNonNull(FoundryMenus.FOUNDRY_STENCIL_TABLE.get()), FoundryStencilTableScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(FoundryPartModelLoader.ID, FoundryPartModelLoader.INSTANCE);
        event.register(FoundryToolModelLoader.ID, FoundryToolModelLoader.INSTANCE);
        event.register(FoundryTankModelLoader.ID, FoundryTankModelLoader.INSTANCE);

        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "connected"), ConnectedModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "retextured"), RetexturedModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "item_layer"), MantleItemLayerModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "colored_block"), ColoredBlockModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "nbt_key"), NBTKeyModel.LOADER);

        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "tool"), ToolModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "material"), MaterialModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "material_block"), MaterialBlockModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "tank"), TankModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "fluid_texture"), FluidTextureModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "fluid_container"), FluidContainerModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "gui"), UniqueGuiModel.LOADER);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FoundryMaterialRenderInfoLoader.INSTANCE);
        DynamicTextureLoader.init(event);
        ModifierModelManager.init(event);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(ToolModel.COLOR_HANDLER, FoundryToolItems.getToolItems().toArray(new Item[0]));
    }

}
