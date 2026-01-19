package com.devmod.foundry.tool;

import net.minecraft.resources.ResourceLocation;

/**
 * Definition for a tool part type.
 */
public record FoundryPartType(ResourceLocation id, String statKey, int materialCost) {}
