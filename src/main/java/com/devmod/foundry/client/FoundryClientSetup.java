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
import net.minecraft.world.item.Item;

import com.devmod.DevMod;
import com.devmod.client.model.mantle.MantleNbtKeyModelLoader;
import com.devmod.client.model.mantle.MantlePassthroughModelLoader;
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
import com.devmod.foundry.tool.FoundryPartItem;
import com.devmod.foundry.tool.FoundryToolData;
import com.devmod.foundry.tool.FoundryToolItems;
import com.devmod.foundry.tool.material.FoundryMaterialDefinition;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;

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
        event.register(MantlePassthroughModelLoader.CONNECTED_ID, MantlePassthroughModelLoader.INSTANCE);
        event.register(MantlePassthroughModelLoader.RETEXTURED_ID, MantlePassthroughModelLoader.INSTANCE);
        event.register(MantlePassthroughModelLoader.ITEM_LAYER_ID, MantlePassthroughModelLoader.INSTANCE);
        event.register(MantlePassthroughModelLoader.COLORED_BLOCK_ID, MantlePassthroughModelLoader.INSTANCE);
        event.register(MantleNbtKeyModelLoader.ID, MantleNbtKeyModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FoundryMaterialRenderInfoLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof FoundryPartItem part)) {
                return 0xFFFFFFFF;
            }
            return part.getMaterialId(stack)
                .map(FoundryClientSetup::resolveMaterialColor)
                .orElse(0xFFFFFFFF);
        }, FoundryToolItems.getPartItems().toArray(new Item[0]));

        event.register((stack, tintIndex) -> {
            if (tintIndex < 0) {
                return 0xFFFFFFFF;
            }
            return FoundryToolData.fromStack(stack)
                .filter(data -> tintIndex < data.materials().size())
                .map(data -> resolveMaterialColor(data.materials().get(tintIndex)))
                .orElse(0xFFFFFFFF);
        }, FoundryToolItems.getToolItems().toArray(new Item[0]));
    }

    private static int resolveMaterialColor(net.minecraft.resources.ResourceLocation materialId) {
        FoundryMaterialDefinition material = FoundryMaterialRegistry.get(materialId);
        if (material == null) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | material.color();
    }
}
