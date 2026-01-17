package com.devmod.clone.client.renderer;

import com.devmod.clone.client.model.CloneMachineItemModel;
import com.devmod.clone.item.CloneMachineBlockItem;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Item renderer for Clone machine block items.
 */
public class CloneMachineItemRenderer extends GeoItemRenderer<CloneMachineBlockItem> {

    private static final float ITEM_SCALE = 0.4f;

    @SuppressWarnings("this-escape")
    public CloneMachineItemRenderer() {
        super(new CloneMachineItemModel());
        withScale(ITEM_SCALE);
        useAlternateGuiLighting();
    }
}
