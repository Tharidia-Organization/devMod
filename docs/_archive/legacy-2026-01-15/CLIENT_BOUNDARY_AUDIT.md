# Client/Server Boundary Audit Report

**Date:** 2025-12-27
**Status:** REMEDIATION IN PROGRESS

## Executive Summary

Audit of the DevMod codebase for client/server separation issues that could cause crashes on dedicated servers.

### Findings

| Category | Status | Count |
|----------|--------|-------|
| Files in wrong package (non-client with client imports) | PASS | 0 |
| Files missing @OnlyIn(Dist.CLIENT) annotation | NEEDS FIX | 190 |
| ClientUiBridge pattern | EXISTS | Properly implemented |
| Mixin separation | PASS | Correctly separated |

## Package Structure Status

**VERIFIED CORRECT** - All client code is already in `*.client.*` packages:

```
com.devmod.client/           # Client-only code (CORRECT)
├── arena/hud/               # Arena HUD overlays
├── attributes/              # Attribute monitoring UI
├── collision/               # Collision debug rendering
├── combat/                  # Combat VFX
├── compat/                  # Mod compatibility (client-side)
├── effects/                 # Visual effects
├── endurance/               # Endurance quest screens
├── events/                  # Client event handlers
├── input/                   # Key bindings
├── network/                 # Client network handlers
├── overlay/                 # HUD overlays
├── panels/                  # Floating panels
├── party/                   # Party UI
├── quest/                   # Quest UI
├── rendering/               # World rendering
├── telemetry/               # Client telemetry
├── testing/                 # QA testing UI
└── ui/                      # All UI screens and components
```

## Missing @OnlyIn Annotations (190 files)

All files below are in correct client packages but lack `@OnlyIn(Dist.CLIENT)`:

### Client Core (2 files)
| File | Client API Used | Impact |
|------|-----------------|--------|
| ClientVFXHelper.java | Minecraft | Classloading risk |
| DevModClient.java | Minecraft | Entry point - critical |

### Arena HUD (1 file)
| File | Client API Used |
|------|-----------------|
| BuildProgressHud.java | Minecraft |

### Attributes (3 files)
| File | Client API Used |
|------|-----------------|
| AttributeHudOverlay.java | DeltaTracker |
| AttributeMonitoringSystem.java | Minecraft |
| AttributeRayVisualizer.java | Minecraft |

### Collision (3 files)
| File | Client API Used |
|------|-----------------|
| OBBDebugRenderer.java | MultiBufferSource |
| ModelPartTransformCapture.java | ModelPart |
| ModelPartTransformExtractor.java | HumanoidModel |

### Combat (1 file)
| File | Client API Used |
|------|-----------------|
| WeaponTrailVFX.java | Minecraft |

### Compat (3 files)
| File | Client API Used |
|------|-----------------|
| ControllingCompat.java | KeyMapping |
| FancyMenuCompat.java | Screen |
| YaclCompat.java | Screen |

### Effects (3 files)
| File | Client API Used |
|------|-----------------|
| PerceptionEventHandler.java | DeltaTracker |
| ShakeManager.java | Minecraft |
| TrailManager.java | Minecraft |

### Events (2 files)
| File | Client API Used |
|------|-----------------|
| ClientModEvents.java | DeltaTracker |
| CombatEvents.java | Minecraft |

### Overlay (24 files)
All overlay files use DeltaTracker, Minecraft, or GuiGraphics.

### Panels (9 files)
All panel files use Minecraft, Camera, or Font.

### Rendering (18 files)
All rendering files use Minecraft, MultiBufferSource, or RenderStateShard.

### Telemetry (3 files)
| File | Client API Used |
|------|-----------------|
| FpsTracker.java | DeltaTracker |
| PerformanceProfiler.java | DeltaTracker |
| TelemetryClientDelegate.java | Minecraft |

### Testing (6 files)
All testing files use Minecraft.

### UI Editor (66 files)
Largest affected subsystem - all editor components.

### UI Hub (6 files)
All hub panel files.

### UI Radial (7 files)
All radial menu files.

### UI Testing (15 files)
All VoxelLab and panel files.

### UI Unified (10 files)
All settings pages.

### Other UI (5 files)
Various UI utility classes.

## Remediation Plan

### Step 1: Add @OnlyIn(Dist.CLIENT) to all 190 files
- Each file in `*.client.*` package needs the annotation
- Annotation goes on the class declaration
- Required import: `import net.neoforged.api.distmarker.OnlyIn;`
- Required import: `import net.neoforged.api.distmarker.Dist;`

### Step 2: Verify ClientUiBridge Pattern
- `com.devmod.bridge.ClientUiBridge` - Interface (common)
- `com.devmod.client.ClientUiBridgeImpl` - Implementation (client-only)
- All common code uses bridge, never direct client imports

### Step 3: Add Regression Guard
- Script to detect client imports outside `*.client.*` packages
- CI integration to prevent future violations

## Files Already Properly Annotated

The following files already have `@OnlyIn(Dist.CLIENT)`:
- KeyInputHandler.java
- QuickTestWizard.java
- ArenaTestWizard.java
- ClientVFXProxy.java
- ClientUiBridgeImpl.java
- All Screen subclasses (verified)

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Dedicated server crash | HIGH | Add @OnlyIn to all client classes |
| Classloading errors | HIGH | Ensure no common→client imports |
| Future regressions | MEDIUM | Add CI guard script |

## Verification Commands

```bash
# Find client imports in non-client packages (should return 0)
grep -r "import net.minecraft.client" src/main/java/com/devmod --include="*.java" | grep -v "/client/"

# Find missing @OnlyIn in client packages
find src/main/java/com/devmod/client -name "*.java" -exec grep -L "@OnlyIn" {} \;

# Verify build
./gradlew build

# Test dedicated server startup
./gradlew runServer
```

## Conclusion

The package structure is correct. The only issue is missing `@OnlyIn(Dist.CLIENT)` annotations on 190 files. This is a mechanical fix that does not require code refactoring.
