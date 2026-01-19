package slimeknights.tconstruct.library.tools.nbt;

import com.devmod.foundry.tool.FoundryToolData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Minimal tool stack parser for model rendering. */
public final class ToolStack implements IToolStackView {
  private final Item item;
  private final ModifierNBT modifiers;
  private final ModifierNBT upgrades;
  private final ModDataNBT persistentData;

  private ToolStack(Item item, ModifierNBT modifiers, ModifierNBT upgrades, ModDataNBT persistentData) {
    this.item = item;
    this.modifiers = modifiers;
    this.upgrades = upgrades;
    this.persistentData = persistentData;
  }

  public static ToolStack from(ItemStack stack) {
    ModifierNBT modifiers = ModifierNBT.EMPTY;
    ModifierNBT upgrades = ModifierNBT.EMPTY;
    if (!stack.isEmpty()) {
      modifiers = buildModifiers(stack);
      upgrades = modifiers;
    }
    return new ToolStack(stack.getItem(), modifiers, upgrades, ModDataNBT.fromStack(stack));
  }

  private static ModifierNBT buildModifiers(ItemStack stack) {
    return FoundryToolData.fromStack(stack)
      .map(data -> {
        Map<ResourceLocation, Integer> map = data.modifiersWithEmbossment();
        if (map.isEmpty()) {
          return ModifierNBT.EMPTY;
        }
        List<ModifierEntry> list = new ArrayList<>(map.size());
        for (Map.Entry<ResourceLocation, Integer> entry : map.entrySet()) {
          list.add(new ModifierEntry(new ModifierId(entry.getKey()), entry.getValue()));
        }
        return new ModifierNBT(list);
      })
      .orElse(ModifierNBT.EMPTY);
  }

  @Override
  public Item getItem() {
    return item;
  }

  @Override
  public ModifierNBT getModifiers() {
    return modifiers;
  }

  @Override
  public ModifierNBT getUpgrades() {
    return upgrades;
  }

  @Override
  public ModDataNBT getPersistentData() {
    return persistentData;
  }
}
