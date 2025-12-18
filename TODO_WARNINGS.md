## Warning Cleanup Plan

Tracking the remaining compiler warnings reported by the IDE. Tackle in this order:

1) Combat & Damage ✅  
   - ShieldDeflector null-safety hardened (requireNonNull + @SuppressWarnings).  
   - DamageHandler impact position guarded.

2) Networking & Mixin ✅  
   - RecipeManagerMixin cleaned unchecked suppressions and null handling.  
   - ShieldImpact/Shatter/State payload types now non-null.

3) Recipe Reload ✅  
   - RecipeReloadListener guards player/payload nullability.

4) Rendering (partially done)  
   - HeatmapVisualizer and EnergyShieldRenderer suppressed for API null annotations.  
   - ShaderManager import cleaned; ResourceLocation null-guarded.  
   - RenderEvents level null-safe.  
   - VFXShaderRegistry suppression added.  
   - TODO: confirm HexagonalShieldMesh/SphereRenderer/ShieldShaderRegistry suppressions silence remaining warnings; revisit if IDE still reports.

5) UI / Editor ✅ (await IDE check)  
   - ItemPickerOverlay null guards + suppression; unused imports/fields removed.  
   - ArmorModule unused import removed.  
   - RecipeModule payload cast guarded.  
   - HubSectionHeader null guards; ProgressFooter unused fields removed.

General approach: replace blanket suppressions with explicit null checks or `Objects.requireNonNull`, simplify unused imports/fields, and update generics to precise types.
