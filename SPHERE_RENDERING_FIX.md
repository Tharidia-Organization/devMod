# Sphere Rendering Fix - Depth Write Configuration

## What Was Fixed

The sphere rendering system was executing correctly (render() method was being called hundreds of times), but nothing was visible on screen. This was due to incorrect RenderType configuration.

### Changes Made to CustomRenderTypes.java

**BEFORE (Not Working)**:
```java
.setWriteMaskState(RenderStateShard.COLOR_WRITE) // Only write color, not depth
// Missing depth test
.createCompositeState(false) // No transparency sorting
```

**AFTER (Should Work Now)**:
```java
.setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE) // Write BOTH color AND depth
.setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST) // Enable depth testing
.createCompositeState(true) // Enable sorting for proper transparency
```

## Why This Matters

1. **COLOR_DEPTH_WRITE**: Without writing to the depth buffer, the GPU doesn't know where the sphere surfaces are in 3D space. This likely caused all vertices to be rejected or culled.

2. **LEQUAL_DEPTH_TEST**: Enables proper depth testing so spheres render correctly behind/in front of other objects in the world.

3. **createCompositeState(true)**: Enables proper sorting of transparent faces, preventing transparency artifacts.

## How to Test

1. **Launch the game** (already running in background)
2. **Load a world** or create a new one
3. **Find a mob** (zombie, skeleton, creeper, etc.)
4. **Press G key** to enable the debug overlay
5. **Look at the mob** - you should now see:
   - A semi-transparent **CYAN sphere** around the mob (aggro range)
   - Body part hitboxes (colored boxes)
   - Mob stats overlay

## Expected Visual Result

You should see **PhantomShapes-style rendering**:
- ✅ Semi-transparent filled surface (NOT wireframe)
- ✅ Cyan color (#00FFFF) at 50% opacity
- ✅ Smooth sphere appearance from all angles
- ✅ Proper depth sorting (sphere behind blocks looks correct)

## Current Implementation

- **Sphere generation**: Using PhantomShapes midpoint circle algorithm
- **Rendering**: Small filled cubes (0.3 block size) at each surface point
- **Vertex format**: POSITION_COLOR only (no UV/normals needed)
- **Transparency**: Full alpha blending with TRANSLUCENT_TRANSPARENCY

## If It Still Doesn't Work

If spheres still don't render after this fix, the next step would be to investigate:
1. Matrix transformation issues in the PoseStack
2. BufferSource rendering pipeline
3. Possible need for custom shader instead of POSITION_COLOR_SHADER

## Logs to Check

When you press G to toggle debug overlay, you should see:
```
[DebugRenderer] toggle() called
[DebugSphere] render() called! center=Vec3(...) radius=...
```

If you see these logs but no sphere, that confirms the rendering pipeline issue (not logic).
If you DON'T see these logs, the problem is earlier in the event system.

---

**Status**: ✅ Compiled and running
**Next**: Test in-game by pressing G near a mob
