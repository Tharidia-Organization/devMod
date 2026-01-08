#!/usr/bin/env python3
"""
Generate simple (non-animated) portal igniter textures based on ender eye.
"""

from PIL import Image
import os
import colorsys

ENDER_EYE_PATH = "/tmp/assets/minecraft/textures/item/ender_eye.png"
OUTPUT_DIR = "../src/main/resources/assets/devmod/textures/item"

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
    return colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)

def hsv_to_rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h, s, v)
    return int(r * 255), int(g * 255), int(b * 255)

def tint_image(img, target_color):
    """Tint image to target color preserving luminosity."""
    img = img.convert('RGBA')
    pixels = img.load()
    target_h, target_s, _ = rgb_to_hsv(*target_color)

    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = pixels[x, y]
            if a > 0:
                _, _, orig_v = rgb_to_hsv(r, g, b)
                new_r, new_g, new_b = hsv_to_rgb(target_h, target_s * 0.9, orig_v)
                pixels[x, y] = (new_r, new_g, new_b, a)
    return img

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    base_eye = Image.open(ENDER_EYE_PATH).convert('RGBA')
    print(f"Loaded ender eye: {base_eye.size}")

    for name, color in COLORS.items():
        # Simple tinted copy, no animation
        tinted = tint_image(base_eye.copy(), color)

        output_path = os.path.join(output_dir, f"portal_igniter_{name}.png")
        tinted.save(output_path)
        print(f"Generated: portal_igniter_{name}.png (16x16)")

        # Remove mcmeta file if exists
        mcmeta_path = output_path + ".mcmeta"
        if os.path.exists(mcmeta_path):
            os.remove(mcmeta_path)
            print(f"Removed: {mcmeta_path}")

    print(f"\nAll simple igniter textures generated!")

if __name__ == "__main__":
    main()
