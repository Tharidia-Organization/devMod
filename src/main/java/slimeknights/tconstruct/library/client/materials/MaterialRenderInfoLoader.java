package slimeknights.tconstruct.library.client.materials;

import com.devmod.foundry.client.model.FoundryMaterialRenderInfo;
import com.devmod.foundry.client.model.FoundryMaterialRenderInfoLoader;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adapter to Foundry material render info for TConstruct-style models. */
public final class MaterialRenderInfoLoader {
  public static final MaterialRenderInfoLoader INSTANCE = new MaterialRenderInfoLoader();

  private MaterialRenderInfoLoader() {}

  public Collection<MaterialRenderInfo> getAllRenderInfos() {
    Map<ResourceLocation, FoundryMaterialRenderInfo> foundry = FoundryMaterialRenderInfoLoader.INSTANCE.getAllRenderInfo();
    List<MaterialRenderInfo> list = new ArrayList<>(foundry.size());
    for (FoundryMaterialRenderInfo info : foundry.values()) {
      list.add(convert(info));
    }
    return list;
  }

  public Optional<MaterialRenderInfo> getRenderInfo(MaterialVariantId variantId) {
    FoundryMaterialRenderInfo info = FoundryMaterialRenderInfoLoader.INSTANCE.getRenderInfo(variantId.getId());
    if (info == null) {
      return Optional.empty();
    }
    return Optional.of(convert(info));
  }

  private static MaterialRenderInfo convert(FoundryMaterialRenderInfo info) {
    return new MaterialRenderInfo(
      MaterialVariantId.create(info.materialId(), ""),
      info.texture(),
      info.fallbacks(),
      info.color(),
      info.luminosity()
    );
  }
}
