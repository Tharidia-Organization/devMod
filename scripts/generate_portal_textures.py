#!/usr/bin/env python3
"""
Generate colored portal textures based on existing nexus portal textures.
Creates both active (linked) and inactive (unlinked) versions for each color.
"""

import os
from PIL import Image
import colorsys

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

def rgb_to_hsl(r, g, b):
    """Convert RGB to HSL."""
    r, g, b = r / 255.0, g / 255.0, b / 255.0
    h, l, s = colorsys.rgb_to_hls(r, g, b)
    return h, s, l

def hsl_to_rgb(h, s, l):
    """Convert HSL to RGB."""
    r, g, b = colorsys.hls_to_rgb(h, l, s)
    return int(r * 255), int(g * 255), int(b * 255)

def colorize_image(img, target_color, active=True):
    """
    Colorize an image to the target color while preserving luminosity.
    If active=False, creates a darker, less saturated version.
    """
    target_h, target_s, _ = rgb_to_hsl(*target_color)

    # For inactive portals: reduce saturation and brightness
    if not active:
        target_s *= 0.3  # 30% saturation
        brightness_mult = 0.4  # 40% brightness
    else:
        brightness_mult = 1.0

    result = Image.new('RGBA', img.size, (0, 0, 0, 0))
    src_pixels = img.load()
    dst_pixels = result.load()

    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src_pixels[x, y]

            if a == 0:
                dst_pixels[x, y] = (0, 0, 0, 0)
                continue

            # Get luminosity from source
            _, _, src_l = rgb_to_hsl(r, g, b)

            # Apply brightness multiplier for inactive
            src_l *= brightness_mult

            # Apply target hue and saturation with source luminosity
            new_r, new_g, new_b = hsl_to_rgb(target_h, target_s, src_l)

            # For inactive, also reduce alpha
            new_a = int(a * 0.6) if not active else a

            dst_pixels[x, y] = (new_r, new_g, new_b, new_a)

    return result

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    textures_dir = os.path.join(script_dir, "..", "src", "main", "resources",
                                "assets", "devmod", "textures", "block")

    # Use nexus_portal_sandbox as base
    base_path = os.path.join(textures_dir, "nexus_portal_sandbox.png")

    if not os.path.exists(base_path):
        print(f"Error: Base texture not found at {base_path}")
        return

    print(f"Loading base texture: {base_path}")
    base_img = Image.open(base_path).convert('RGBA')
    print(f"Base texture size: {base_img.size}")

    print("\nGenerating colored portal textures (active + inactive)...")
    for color_name, rgb in COLORS.items():
        # Active/linked version
        active = colorize_image(base_img, rgb, active=True)
        active_path = os.path.join(textures_dir, f"custom_portal_{color_name}.png")
        active.save(active_path)

        with open(active_path + ".mcmeta", 'w') as f:
            f.write('{"animation": {}}\n')

        # Inactive/unlinked version
        inactive = colorize_image(base_img, rgb, active=False)
        inactive_path = os.path.join(textures_dir, f"custom_portal_{color_name}_off.png")
        inactive.save(inactive_path)

        with open(inactive_path + ".mcmeta", 'w') as f:
            f.write('{"animation": {"frametime": 3}}\n')  # Slower animation when inactive

        print(f"  Created: custom_portal_{color_name}.png + _off.png")

    print("\nDone! Generated portal textures for all 16 colors (active + inactive).")

if __name__ == "__main__":
    main()
