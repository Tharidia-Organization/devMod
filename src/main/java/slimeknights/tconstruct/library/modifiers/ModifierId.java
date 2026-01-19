package slimeknights.tconstruct.library.modifiers;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/** Minimal modifier identifier for rendering. */
public final class ModifierId {
  private final ResourceLocation id;

  public ModifierId(ResourceLocation id) {
    this.id = id;
  }

  @Nullable
  public static ModifierId tryParse(String text) {
    ResourceLocation id = ResourceLocation.tryParse(text);
    return id == null ? null : new ModifierId(id);
  }

  public ResourceLocation asResourceLocation() {
    return id;
  }

  public String getNamespace() {
    return id.getNamespace();
  }

  public String getPath() {
    return id.getPath();
  }

  @Override
  public String toString() {
    return id.toString();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ModifierId other)) {
      return false;
    }
    return id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
