package slimeknights.tconstruct.library.modifiers.modules.capacity;

import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/** Stub overslime module for model rendering. */
public final class OverslimeModule {
  public static final OverslimeModule INSTANCE = new OverslimeModule();

  private OverslimeModule() {}

  public int getAmount(IToolStackView tool) {
    return 0;
  }
}
