package com.devmod.foundry.client;

import java.util.Objects;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.foundry.FoundryBlockEntities;
import com.devmod.foundry.FoundryMenus;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfoLoader;
import com.devmod.foundry.client.model.FoundryNBTKeyModel;
import com.devmod.foundry.client.model.FoundryPartModelLoader;
import com.devmod.foundry.client.model.FoundryRetexturedModel;
import com.devmod.foundry.client.model.FoundryTankModelLoader;
import com.devmod.foundry.client.model.FoundryToolModelLoader;
import com.devmod.foundry.client.model.FoundryDynamicTextureLoader;
import com.devmod.foundry.client.model.FoundryFluidContainerModel;
import com.devmod.foundry.client.model.FoundryUniqueGuiModel;
import com.devmod.foundry.client.model.block.FoundryFluidTextureModel;
import com.devmod.foundry.client.model.block.FoundryTankModel;
import com.devmod.foundry.client.model.connected.FoundryConnectedModel;
import com.devmod.foundry.client.model.util.FoundryColoredBlockModel;
import com.devmod.foundry.client.model.util.FoundryItemLayerModel;
import com.devmod.foundry.client.model.util.FoundryModelHelper;
import com.devmod.foundry.client.model.tools.FoundryMaterialBlockModel;
import com.devmod.foundry.client.model.tools.FoundryMaterialModel;
import com.devmod.foundry.client.renderer.FoundryChannelRenderer;
import com.devmod.foundry.client.renderer.FoundryControllerRenderer;
import com.devmod.foundry.client.renderer.FoundryTankRenderer;
import com.devmod.foundry.client.screen.FoundryControllerScreen;
import com.devmod.foundry.client.screen.FoundryPartBuilderScreen;
import com.devmod.foundry.client.screen.FoundryStencilTableScreen;
import com.devmod.foundry.client.screen.FoundryToolAnvilScreen;
import com.devmod.foundry.client.screen.FoundryToolStationScreen;

/**
 * Client-side setup for the Foundry module.
 */
@SuppressWarnings("removal")
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
        event.register(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "foundry_connected"), FoundryConnectedModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "retextured"), FoundryRetexturedModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "item_layer"), FoundryItemLayerModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "colored_block"), FoundryColoredBlockModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "nbt_key"), FoundryNBTKeyModel.LOADER);

        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "connected"), FoundryConnectedModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "retextured"), FoundryRetexturedModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "item_layer"), FoundryItemLayerModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "colored_block"), FoundryColoredBlockModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("mantle", "nbt_key"), FoundryNBTKeyModel.LOADER);

        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "tool"), FoundryToolModelLoader.INSTANCE);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "material"), FoundryMaterialModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "material_block"), FoundryMaterialBlockModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "tank"), FoundryTankModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "fluid_texture"), FoundryFluidTextureModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "fluid_container"), FoundryFluidContainerModel.LOADER);
        event.register(ResourceLocation.fromNamespaceAndPath("tconstruct", "gui"), FoundryUniqueGuiModel.LOADER);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FoundryMaterialRenderInfoLoader.INSTANCE);
        event.registerReloadListener(FoundryModelHelper.LISTENER);
        FoundryDynamicTextureLoader.init(event);
    }

}
