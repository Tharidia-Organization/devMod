package com.devmod.clone.client.renderer;

import com.devmod.clone.block.entity.CloneMachineBlockEntity;
import com.devmod.clone.client.model.CloneMachineModel;

import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * GeckoLib renderer for Clone machine blocks.
 * Renders Bedrock Edition models with animations.
 * Dynamically selects the correct model based on block type.
 */
public class CloneMachineRenderer extends GeoBlockRenderer<CloneMachineBlockEntity> {

    public CloneMachineRenderer() {
        super(new CloneMachineModel());
    }
}
