package slimeknights.tconstruct.library.materials.definition;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import javax.annotation.Nullable;
import java.util.Objects;

/** Minimal material variant identifier for model rendering. */
public final class MaterialVariantId {
  public static final String DEFAULT_VARIANT = "default";

  private final ResourceLocation id;
  private final String variant;

  private MaterialVariantId(ResourceLocation id, String variant) {
    this.id = id;
    this.variant = variant == null ? "" : variant;
  }

  public static MaterialVariantId create(String namespace, String path, String variant) {
    return create(ResourceLocation.fromNamespaceAndPath(namespace, path), variant);
  }

  public static MaterialVariantId create(ResourceLocation id, String variant) {
    return new MaterialVariantId(id, variant);
  }

  @Nullable
  public static MaterialVariantId tryParse(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String base = text;
    String variant = "";
    int index = text.indexOf('#');
    if (index >= 0) {
      base = text.substring(0, index);
      variant = text.substring(index + 1);
    }
    ResourceLocation id = ResourceLocation.tryParse(base);
    if (id == null) {
      return null;
    }
    return new MaterialVariantId(id, variant);
  }

  public static MaterialVariantId fromJson(JsonObject json, String key) {
    String text = GsonHelper.getAsString(json, key);
    MaterialVariantId id = tryParse(text);
    if (id == null) {
      throw new JsonSyntaxException("Expected " + key + " to be a material variant ID, was '" + text + "'");
    }
    return id;
  }

  public ResourceLocation getId() {
    return id;
  }

  public String getVariant() {
    return variant;
  }

  public boolean hasVariant() {
    return !variant.isEmpty();
  }

  public ResourceLocation getLocation(char separator) {
    if (variant.isEmpty()) {
      return id;
    }
    return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + separator + variant);
  }

  public String getSuffix() {
    return hasVariant() ? (id.getPath() + "_" + variant) : id.getPath();
  }

  @Override
  public String toString() {
    return variant.isEmpty() ? id.toString() : id + "#" + variant;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MaterialVariantId other)) {
      return false;
    }
    return id.equals(other.id) && variant.equals(other.variant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, variant);
  }
}
