package slimeknights.tconstruct.library.client.model;

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import slimeknights.mantle.data.listener.ResourceValidator;
import slimeknights.tconstruct.common.config.Config;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;

/** Handles dynamic texture validation for modifier models. */
public class DynamicTextureLoader extends ResourceValidator {
  private static final DynamicTextureLoader INSTANCE = new DynamicTextureLoader();

  private DynamicTextureLoader() {
    super("textures/item", "textures", ".png");
  }

  @Override
  public void onReloadSafe(ResourceManager manager) {
    if (!Config.CLIENT.logMissingModifierTextures.get()) {
      super.onReloadSafe(manager);
    }
  }

  @Override
  public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
    return super.reload(stage, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor)
      .thenRunAsync(this::clear);
  }

  public static void init(RegisterClientReloadListenersEvent event) {
    event.registerReloadListener(INSTANCE);
  }

  public static Predicate<Material> getTextureValidator(Function<Material, TextureAtlasSprite> spriteGetter, boolean logMissingTextures) {
    if (logMissingTextures || INSTANCE.resources.isEmpty()) {
      return mat -> !MissingTextureAtlasSprite.getLocation().equals(spriteGetter.apply(mat).contents().name());
    }
    return mat -> {
      if (InventoryMenu.BLOCK_ATLAS.equals(mat.atlasLocation())) {
        ResourceLocation texture = mat.texture();
        if (texture.getPath().startsWith("item/")) {
          return INSTANCE.test(mat.texture());
        }
      }
      return !MissingTextureAtlasSprite.getLocation().equals(spriteGetter.apply(mat).contents().name());
    };
  }
}
