package slimeknights.tconstruct.library.modifiers;

/** Minimal modifier instance for rendering. */
public class Modifier {
  private final ModifierId id;

  public Modifier(ModifierId id) {
    this.id = id;
  }

  public ModifierId getId() {
    return id;
  }
}
