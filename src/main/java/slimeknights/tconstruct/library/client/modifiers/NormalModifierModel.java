package slimeknights.tconstruct.library.client.modifiers;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.google.gson.JsonElement;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.util.GsonHelper;
import slimeknights.mantle.client.model.util.MantleItemLayerModel;
import slimeknights.mantle.util.ItemLayerPixels;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default modifier model loader, loads a single texture from the standard path
 */
public class NormalModifierModel implements IBakedModifierModel {
  /** Constant unbaked model instance, as they are all the same */
  public static final IUnbakedModifierModel UNBAKED_INSTANCE = new Unbaked(-1, 0);

  /** Textures to show */
  @Nullable
  private final Material small;
  @Nullable
  private final Material large;
  /** Color to apply to the texture */
  private final int color;
  /** Luminosity to apply to the texture */
  private final int luminosity;

  public NormalModifierModel(@Nullable Material small, @Nullable Material large, int color, int luminosity) {
    this.small = small;
    this.large = large;
    this.color = color;
    this.luminosity = luminosity;
  }

  public NormalModifierModel(@Nullable Material smallTexture, @Nullable Material largeTexture) {
    this(smallTexture, largeTexture, -1, 0);
  }

  @Override
  public void addQuads(IToolStackView tool, ModifierEntry entry, Function<Material,TextureAtlasSprite> spriteGetter, Transformation transforms, boolean isLarge, int startTintIndex, Consumer<Collection<BakedQuad>> quadConsumer, @Nullable ItemLayerPixels pixels) {
    Material spriteName = isLarge ? large : small;
    if (spriteName != null) {
      quadConsumer.accept(MantleItemLayerModel.getQuadsForSprite(color, -1, spriteGetter.apply(spriteName), transforms, luminosity, pixels));
    }
  }

  private record Unbaked(int color, int luminosity) implements IUnbakedModifierModel {
    @Nullable
    @Override
    public IBakedModifierModel forTool(Function<String,Material> smallGetter, Function<String,Material> largeGetter) {
      Material smallTexture = smallGetter.apply("");
      Material largeTexture = largeGetter.apply("");
      if (smallTexture != null || largeTexture != null) {
        return new NormalModifierModel(smallTexture, largeTexture, color, luminosity);
      }
      return null;
    }

    @Override
    public IUnbakedModifierModel configure(JsonObject data) {
      // parse the two keys, if we ended up with something new create an instance
      int color = parseColor(data, "color", -1);
      int luminosity = GsonHelper.getAsInt(data, "luminosity", 0);
      if (color != this.color || luminosity != this.luminosity) {
        return new Unbaked(color, luminosity);
      }
      return this;
    }

    private static int parseColor(JsonObject data, String key, int fallback) {
      if (!data.has(key)) {
        return fallback;
      }
      JsonElement element = data.get(key);
      if (element.isJsonPrimitive()) {
        if (element.getAsJsonPrimitive().isString()) {
          String value = element.getAsString();
          if (value.startsWith("#")) {
            value = value.substring(1);
          }
          try {
            int parsed = (int) Long.parseLong(value, 16);
            if ((parsed & 0xFF000000) == 0) {
              parsed |= 0xFF000000;
            }
            return parsed;
          } catch (NumberFormatException ignored) {
            return fallback;
          }
        }
        return element.getAsInt();
      }
      return fallback;
    }
  }
}
