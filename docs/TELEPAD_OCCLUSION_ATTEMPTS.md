# Telepad Portal Player Occlusion - Failed Attempts Log

## Current Status (2026-01-29)
Player occlusion is now handled by `PlayerRendererMixin` using portal geometry checks.
The attempts below are kept as historical notes.

## Problem
In third-person view, the player is visible through the telepad portal. We want the portal to occlude (hide) the player when they are behind it.

## Portal Specifications
- `PORTAL_WIDTH = 2.457f`
- `PORTAL_HEIGHT = 3.51f`
- `PORTAL_Y_OFFSET = 1.0f` (above telepad block)
- Shape: Oval/ellipse

## Minecraft Render Order (the core issue)
1. Terrain (solid blocks)
2. Terrain (cutout blocks - leaves, etc.)
3. **Entities** (players, mobs)
4. **Block Entities** (the portal visual is rendered here)
5. Translucent terrain

The portal visual renders AFTER entities, so depth-based occlusion from BlockEntityRenderer cannot work.

---

## Failed Attempts

### 1. RenderLevelStageEvent - AFTER_ENTITIES
**File:** `TelepadDepthRenderHandler.java`
**Approach:** Render depth-only oval at AFTER_ENTITIES stage using direct Tesselator
**Result:** PARTIALLY WORKED - oval renders but WITH FLICKERING
**Why it fails:** Entities are already drawn to framebuffer. We can't "un-draw" pixels.
**Note:** This confirmed the rendering code itself works.

### 2. RenderLevelStageEvent - AFTER_CUTOUT_BLOCKS
**File:** `TelepadDepthRenderHandler.java`
**Approach:** Render at earlier stage (before entities) using camera-relative coordinates
**Result:** FAILED - nothing visible
**Why it fails:** Matrix/projection state is different at this stage. Same coordinate system that works at AFTER_ENTITIES doesn't work here.

### 3. RenderLevelStageEvent - AFTER_SOLID_BLOCKS
**File:** `TelepadDepthRenderHandler.java`
**Approach:** Even earlier stage
**Result:** FAILED - nothing visible
**Why it fails:** Same matrix state issue as AFTER_CUTOUT_BLOCKS

### 4. Mixin at HEAD of LevelRenderer.renderLevel
**File:** `TelepadEntityRenderMixin.java`
**Approach:** Inject at very start of renderLevel, manually set up matrices using frustumMatrix and projectionMatrix parameters
**Result:** FAILED - nothing visible
**Why it fails:** OpenGL state not configured yet at HEAD. Matrices from parameters don't produce correct rendering.

### 5. Manual Matrix Setup at AFTER_CUTOUT_BLOCKS
**File:** `TelepadDepthRenderHandler.java`
**Approach:** At AFTER_CUTOUT_BLOCKS, manually apply camera rotation using pitch/yaw
**Result:** FAILED - nothing visible or incorrect position
**Why it fails:** Camera rotation math was incorrect or incomplete

### 6. GL_ALWAYS Depth Function
**File:** `TelepadDepthRenderHandler.java`
**Approach:** Use GL_ALWAYS to always write depth regardless of current depth
**Result:** FAILED - still flickering
**Why it fails:** The issue isn't depth testing, it's that pixels are already in framebuffer

### 7. Stencil Buffer Approach
**File:** `TelepadDepthRenderHandler.java`
**Approach:** Use stencil buffer to mask the portal area
**Result:** FAILED - no visible effect
**Why it fails:** Stencil operations weren't set up correctly, or Minecraft's render pipeline doesn't preserve stencil state

### 8. Entity Culling via LivingEntityRendererMixin (Complex)
**File:** `LivingEntityRendererMixin.java`
**Approach:** Cancel entity rendering if entity is "behind" portal from camera's perspective
**Logic:**
- Check if camera and entity are on opposite sides of portal plane (dot product check)
- Check if entity position falls within portal oval bounds (ellipse equation)
**Result:** FAILED - player still renders
**Why it fails:** Debug logging needed to determine exact failure point. Possible issues:
- Dot product sign check incorrect
- Portal facing direction interpretation wrong
- Entity position (feet vs center) calculation wrong
- Oval bounds check math incorrect

### 9. Entity Culling - Simple Distance Check
**File:** `LivingEntityRendererMixin.java`
**Approach:** Cancel entity rendering if entity is within 1.5 blocks of telepad (no complex math)
**Logic:**
- Check horizontal distance < 1.5 blocks
- Check Y within portal height range
- If true, cancel render (ci.cancel())
**Result:** FAILED - player still renders
**Why it fails:** UNKNOWN - even the simplest check doesn't work. Possible issues:
- TelepadDepthRenderer.getActiveTelepadPositions() returns empty set
- Mixin isn't being applied/called
- BlockState check for ACTIVE is failing
- Some other mod/code is overriding the cancel

### 10. Entity Culling with System.out.println Debug
**File:** `LivingEntityRendererMixin.java`
**Approach:** Same as #9 but with System.out.println to verify mixin is called
**Result:** FAILED - no debug output seen, player still renders
**Why it fails:** Either:
- Mixin isn't being applied (but it's registered in mixins.json)
- TelepadDepthRenderer.getActiveTelepadPositions() is empty
- clientTick() isn't being called on TelepadBlockEntity

### 11. PlayerRenderer-Specific Mixin
**File:** `PlayerRendererMixin.java` (NEW)
**Approach:** Create a mixin specifically targeting `PlayerRenderer.render()` instead of `LivingEntityRenderer`
**Rationale:**
- `PlayerRenderer` extends `LivingEntityRenderer` but might override `render()` without calling `super()`
- In third-person view, player rendering goes through `PlayerRenderer`
- By targeting `PlayerRenderer` directly, we ensure the mixin catches player rendering
**Result:** TESTING - need to verify in logs

---

## Debug Analysis Results

### What the logs show:
1. **LivingEntityRendererMixin IS being applied** - confirmed in mixin debug output
2. **No debug output from TelepadBlockEntity.clientTick()** - "[TELEPAD DEBUG]" never appears
3. **No debug output from LivingEntityRendererMixin** - "[OCCLUSION DEBUG]" never appears

### Root cause analysis:
The issue is likely that **TelepadDepthRenderer.getActiveTelepadPositions() returns an empty set** because:
- `clientTick()` might not be called on the telepad
- OR the telepad state isn't ACTIVE
- OR the telepad isn't registering its position

### Next steps to verify:
1. Add logging at the start of clientTick() (BEFORE any condition checks)
2. Verify the telepad block entity ticker is properly set up
3. Check if the telepad's ACTIVE state is being set correctly on the client

---

## Approaches NOT YET TRIED

### A. Render Portal Visual with Opaque Background
Modify `TelepadBlockEntityRenderer` to render an opaque layer behind the vortex effect.
**Problem:** Block entities render after entities, so this won't help.

### B. Custom Shader
Use a shader that discards fragments based on position relative to portal.
**Complexity:** High - requires shader programming and integration with MC's render system.

### C. Render Player to Separate Framebuffer
Render entities to a separate buffer, then composite with portal masking.
**Complexity:** Very high - major render pipeline modification.

### D. Sodium/Iris Integration
These mods have more control over render order.
**Problem:** Would require mod dependency.

### E. Render Portal at Entity Stage
Instead of using BlockEntityRenderer, register the portal as a fake "entity" that renders at entity stage but before the player.
**Complexity:** Medium - requires understanding entity render priority.

### F. Post-Processing Effect
Apply a post-process shader that samples the portal area and masks the player.
**Complexity:** High - requires post-processing pipeline.

---

## Debug Information Needed

From the entity culling attempt, we need to check the logs for:
1. Is `TelepadDepthRenderer.getActiveTelepadPositions()` returning positions?
2. What are the cameraDot and entityDot values?
3. Is the dot product check passing (opposite sides)?
4. What are the localX, localY, ellipse values?

---

## Key Insight

The ONLY approach that showed the oval rendering was **AFTER_ENTITIES with direct Tesselator**. This means:
- The rendering code is correct
- The coordinate transformation is correct at that stage
- The issue is purely about WHEN we render, not HOW

If we could inject rendering at AFTER_CUTOUT_BLOCKS with the EXACT same matrix state as AFTER_ENTITIES, it would work.

---

## Next Steps to Try

1. **Debug entity culling** - check why the position-based culling isn't triggering
2. **Compare matrix states** - log the actual OpenGL matrices at AFTER_CUTOUT_BLOCKS vs AFTER_ENTITIES
3. **Try BufferSource approach at earlier stage** - maybe direct Tesselator has different requirements than BufferSource
4. **Investigate Minecraft's chunk rendering** - understand how terrain batches set up their matrices
