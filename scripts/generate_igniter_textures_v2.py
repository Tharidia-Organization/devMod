#!/usr/bin/env python3
"""
Generate portal igniter textures based on the real Minecraft ender eye.
Tints the eye to match each portal color and adds animated energy overlay.
"""

from PIL import Image, ImageDraw, ImageEnhance
import os
import json
import colorsys

# Paths
ENDER_EYE_PATH = "/tmp/assets/minecraft/textures/item/ender_eye.png"
OUTPUT_DIR = "../src/main/resources/assets/devmod/textures/item"

# Animation frames for energy effect
ANIMATION_FRAMES = 8

# Minecraft dye colors (RGB)
COLORS = {
    "white": (249, 255, 254),
    "orange": (249, 128, 29),
    "magenta": (199, 78, 189),
    "light_blue": (58, 179, 218),
    "yellow": (254, 216, 61),
    "lime": (128, 199, 31),
    "pink": (243, 139, 170),
    "gray": (71, 79, 82),
    "light_gray": (157, 157, 151),
    "cyan": (22, 156, 156),
    "purple": (137, 50, 184),
    "blue": (60, 68, 170),
    "brown": (131, 84, 50),
    "green": (94, 124, 22),
    "red": (176, 46, 38),
    "black": (29, 29, 33),
}

def rgb_to_hsv(r, g, b):
    """Convert RGB to HSV."""
    return colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)

def hsv_to_rgb(h, s, v):
    """Convert HSV to RGB."""
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)

def tint_image(img, target_color):
    """Tint an image to match target color while preserving luminosity."""
    img = img.convert('RGBA')
    pixels = img.load()

    target_h, target_s, _ = rgb_to_hsv(*target_color)

    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = pixels[x, y]
            if a > 0:  # Only process non-transparent pixels
                # Get original luminosity
                _, _, orig_v = rgb_to_hsv(r, g, b)

                # Apply target hue/saturation with original value
                new_r, new_g, new_b = hsv_to_rgb(target_h, target_s * 0.9, orig_v)
                pixels[x, y] = (new_r, new_g, new_b, a)

    return img

def add_energy_particles(img, color, frame):
    """Add animated energy particles around the eye."""
    draw = ImageDraw.Draw(img)

    # Phase for animation
    phase = frame / ANIMATION_FRAMES

    # Energy particle positions that rotate
    import math
    center_x, center_y = 7, 7
    radius = 6

    for i in range(4):
        angle = (i / 4 + phase) * 2 * math.pi
        px = int(center_x + radius * math.cos(angle))
        py = int(center_y + radius * math.sin(angle))

        # Only draw if in bounds and with some randomness
        if 0 <= px < 16 and 0 <= py < 16:
            # Bright particle
            bright = tuple(min(255, int(c * 1.3)) for c in color)
            if (frame + i) % 3 != 0:  # Flicker
                draw.point((px, py), fill=bright + (200,))

    # Inner glow pulse
    glow_alpha = int(100 + 50 * math.sin(phase * 2 * math.pi))
    glow_color = color + (glow_alpha,)

    # Small glow at center
    for ox, oy in [(0, -1), (0, 1), (-1, 0), (1, 0)]:
        gx, gy = 7 + ox, 6 + oy
        if 0 <= gx < 16 and 0 <= gy < 16:
            existing = img.getpixel((gx, gy))
            if existing[3] > 0:
                # Blend with existing
                blended = tuple(
                    int(existing[i] * 0.7 + glow_color[i] * 0.3)
                    for i in range(3)
                ) + (existing[3],)
                draw.point((gx, gy), fill=blended)

    return img

def generate_igniter_texture(name, color, base_eye):
    """Generate animated igniter texture from ender eye base."""
    # Create sprite sheet
    width = 16
    height = 16 * ANIMATION_FRAMES

    sprite_sheet = Image.new('RGBA', (width, height), (0, 0, 0, 0))

    # Tint the base eye once
    tinted_eye = tint_image(base_eye.copy(), color)

    for frame in range(ANIMATION_FRAMES):
        # Start with tinted eye
        frame_img = tinted_eye.copy()

        # Add energy particles for this frame
        frame_img = add_energy_particles(frame_img, color, frame)

        # Paste into sprite sheet
        sprite_sheet.paste(frame_img, (0, frame * 16))

    return sprite_sheet

def generate_mcmeta():
    """Generate animation metadata."""
    return {
        "animation": {
            "frametime": 3,
            "interpolate": True
        }
    }

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    # Load base ender eye
    if not os.path.exists(ENDER_EYE_PATH):
        print(f"ERROR: Ender eye texture not found at {ENDER_EYE_PATH}")
        print("Please extract it first from minecraft client jar")
        return

    base_eye = Image.open(ENDER_EYE_PATH).convert('RGBA')
    print(f"Loaded ender eye: {base_eye.size}")

    for name, color in COLORS.items():
        # Generate texture
        img = generate_igniter_texture(name, color, base_eye)

        output_path = os.path.join(output_dir, f"portal_igniter_{name}.png")
        img.save(output_path)
        print(f"Generated: portal_igniter_{name}.png")

        # Generate mcmeta
        mcmeta_path = os.path.join(output_dir, f"portal_igniter_{name}.png.mcmeta")
        with open(mcmeta_path, 'w') as f:
            json.dump(generate_mcmeta(), f, indent=2)

    print(f"\nAll igniter textures generated in: {output_dir}")

if __name__ == "__main__":
    main()
