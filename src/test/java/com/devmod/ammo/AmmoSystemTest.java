package com.devmod.ammo;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.devmod.TestBootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AmmoSystemTest {

    @BeforeAll
    static void bootstrap() {
        TestBootstrap.init();
    }

    @Test
    void isValidFilterStringAcceptsItemId() {
        assertTrue(AmmoSystem.isValidFilterString("minecraft:arrow"));
    }

    @Test
    void isValidFilterStringRejectsInvalidValues() {
        assertFalse(AmmoSystem.isValidFilterString("not a valid id"));
        assertFalse(AmmoSystem.isValidFilterString("minecraft:does_not_exist"));
    }

    @Test
    void getItemsFromTagStringResolvesItemId() {
        List<ItemStack> items = AmmoSystem.getItemsFromTagString("minecraft:arrow");
        assertEquals(1, items.size());
        assertEquals(Items.ARROW, items.get(0).getItem());
    }

    @Test
    void getItemsFromTagStringResolvesTagWhenAvailable() {
        ResourceLocation tagId = ResourceLocation.tryParse("minecraft:arrows");
        Assumptions.assumeTrue(tagId != null, "Tag id should parse");
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        boolean hasEntries = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext();
        Assumptions.assumeTrue(hasEntries, "Tag registry not populated in this test environment");

        List<ItemStack> items = AmmoSystem.getItemsFromTagString("#minecraft:arrows");
        assertFalse(items.isEmpty());
    }

    @Test
    void resolveFilterStateHandlesBlankAndInvalid() {
        assertEquals(AmmoSystem.FilterState.BLANK, AmmoSystem.resolveFilterState(null));
        assertEquals(AmmoSystem.FilterState.BLANK, AmmoSystem.resolveFilterState(" "));
        assertEquals(AmmoSystem.FilterState.INVALID_FORMAT, AmmoSystem.resolveFilterState("not a valid id"));
    }

    @Test
    void resolveFilterStateHandlesItemValues() {
        assertEquals(AmmoSystem.FilterState.VALID_ITEM, AmmoSystem.resolveFilterState("minecraft:arrow"));
        assertEquals(AmmoSystem.FilterState.MISSING_ITEM, AmmoSystem.resolveFilterState("minecraft:does_not_exist"));
    }

    @Test
    void resolveFilterStateHandlesTagValuesWhenAvailable() {
        ResourceLocation tagId = ResourceLocation.tryParse("minecraft:arrows");
        Assumptions.assumeTrue(tagId != null, "Tag id should parse");
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        boolean hasEntries = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext();
        Assumptions.assumeTrue(hasEntries, "Tag registry not populated in this test environment");

        assertEquals(AmmoSystem.FilterState.VALID_TAG, AmmoSystem.resolveFilterState("#minecraft:arrows"));
    }

    @Test
    void matchesFilterMatchesItemId() {
        ItemStack arrow = new ItemStack(Items.ARROW);
        assertTrue(AmmoSystem.matchesFilter(arrow, "minecraft:arrow"));
        assertTrue(AmmoSystem.matchesFilter(arrow, "arrow"));
        assertFalse(AmmoSystem.matchesFilter(arrow, "minecraft:spectral_arrow"));
    }

    @Test
    void matchesFilterMatchesTagWhenAvailable() {
        ResourceLocation tagId = ResourceLocation.tryParse("minecraft:arrows");
        Assumptions.assumeTrue(tagId != null, "Tag id should parse");
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        boolean hasEntries = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext();
        Assumptions.assumeTrue(hasEntries, "Tag registry not populated in this test environment");

        ItemStack arrow = new ItemStack(Items.ARROW);
        assertTrue(AmmoSystem.matchesFilter(arrow, "#minecraft:arrows"));
    }

    @Test
    void matchesFilterHandlesNullOrBlank() {
        ItemStack arrow = new ItemStack(Items.ARROW);
        assertFalse(AmmoSystem.matchesFilter(arrow, null));
        assertFalse(AmmoSystem.matchesFilter(arrow, " "));
        assertFalse(AmmoSystem.matchesFilter(ItemStack.EMPTY, "minecraft:arrow"));
    }
}
