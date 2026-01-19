package slimeknights.tconstruct.library.tools.nbt;

import slimeknights.tconstruct.library.modifiers.ModifierEntry;

import java.util.Iterator;
import java.util.List;

/** Minimal modifier list for rendering. */
public final class ModifierNBT implements Iterable<ModifierEntry> {
  public static final ModifierNBT EMPTY = new ModifierNBT(List.of());

  private final List<ModifierEntry> modifiers;

  public ModifierNBT(List<ModifierEntry> modifiers) {
    this.modifiers = List.copyOf(modifiers);
  }

  public List<ModifierEntry> getModifiers() {
    return modifiers;
  }

  public boolean isEmpty() {
    return modifiers.isEmpty();
  }

  @Override
  public Iterator<ModifierEntry> iterator() {
    return modifiers.iterator();
  }
}
