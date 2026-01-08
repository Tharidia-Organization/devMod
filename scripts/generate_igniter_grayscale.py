#!/usr/bin/env python3
"""
Generate a single grayscale portal igniter texture.
The color handler will tint it to match each portal color.
"""

from PIL import Image
import os

ENDER_EYE_PATH = "/tmp/assets/minecraft/textures/item/ender_eye.png"
OUTPUT_DIR = "../src/main/resources/assets/devmod/textures/item"

def to_grayscale(img):
    """Convert image to grayscale while preserving alpha."""
    img = img.convert('RGBA')
    pixels = img.load()

    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = pixels[x, y]
            # Standard grayscale conversion
            gray = int(0.299 * r + 0.587 * g + 0.114 * b)
            # Brighten a bit for better tinting
            gray = min(255, int(gray * 1.2))
            pixels[x, y] = (gray, gray, gray, a)

    return img

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    # Load and convert ender eye to grayscale
    base_eye = Image.open(ENDER_EYE_PATH).convert('RGBA')
    grayscale = to_grayscale(base_eye)

    # Save as the base igniter texture
    output_path = os.path.join(output_dir, "portal_igniter.png")
    grayscale.save(output_path)
    print(f"Generated: portal_igniter.png (grayscale base)")

    # Remove old colored textures
    colors = [
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black"
    ]
    for color in colors:
        old_path = os.path.join(output_dir, f"portal_igniter_{color}.png")
        if os.path.exists(old_path):
            os.remove(old_path)
            print(f"Removed: portal_igniter_{color}.png")

    print("\nDone! Now update models to use 'devmod:item/portal_igniter'")

if __name__ == "__main__":
    main()
