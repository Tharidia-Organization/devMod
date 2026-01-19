package slimeknights.tconstruct.library.modifiers;

import slimeknights.tconstruct.TConstruct;

/** Minimal modifier entry with a level. */
public class ModifierEntry {
  public static final ModifierEntry EMPTY = new ModifierEntry(new ModifierId(TConstruct.getResource("empty")), 0);

  private final ModifierId id;
  private final int level;

  public ModifierEntry(ModifierId id, int level) {
    this.id = id;
    this.level = level;
  }

  public ModifierEntry(Modifier modifier, int level) {
    this(modifier.getId(), level);
  }

  public ModifierId getId() {
    return id;
  }

  public Modifier getModifier() {
    return new Modifier(id);
  }

  public int getLevel() {
    return level;
  }
}
