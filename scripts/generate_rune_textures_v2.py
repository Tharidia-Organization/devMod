#!/usr/bin/env python3
"""
Generate detailed magical rune textures with Minecraft-style shading.
Each rune has proper depth, glow effects, and magical details.
"""

from PIL import Image, ImageDraw, ImageFilter
import os

OUTPUT_DIR = "../src/main/resources/assets/devmod/textures/block"

# Rune colors with dark/mid/bright variants for shading
RUNE_COLORS = {
    "haste": {
        "dark": (0, 100, 100),
        "mid": (0x55, 0xFF, 0xFF),
        "bright": (200, 255, 255),
    },
    "gate": {
        "dark": (60, 0, 60),
        "mid": (0xAA, 0x00, 0xAA),
        "bright": (220, 100, 220),
    },
    "enhancer": {
        "dark": (120, 60, 0),
        "mid": (0xFF, 0xAA, 0x00),
        "bright": (255, 220, 100),
    },
    "strong_enhancer": {
        "dark": (120, 30, 0),
        "mid": (0xFF, 0x55, 0x00),
        "bright": (255, 180, 100),
    },
    "infinity": {
        "dark": (0, 80, 0),
        "mid": (0x00, 0xFF, 0x00),
        "bright": (150, 255, 150),
    },
}

def draw_pixel(img, x, y, color):
    """Draw a single pixel with bounds checking."""
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), color + (255,))

def draw_line_shaded(img, points, colors):
    """Draw a line with dark border and bright center."""
    dark, mid, bright = colors["dark"], colors["mid"], colors["bright"]

    # Draw dark outline first
    for i in range(len(points) - 1):
        x1, y1 = points[i]
        x2, y2 = points[i + 1]
        # Bresenham-ish for dark border
        dx = abs(x2 - x1)
        dy = abs(y2 - y1)
        sx = 1 if x1 < x2 else -1
        sy = 1 if y1 < y2 else -1
        err = dx - dy
        x, y = x1, y1
        while True:
            # Dark border pixels
            for ox, oy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                draw_pixel(img, x + ox, y + oy, dark)
            if x == x2 and y == y2:
                break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy
                x += sx
            if e2 < dx:
                err += dx
                y += sy

    # Draw mid color line
    for i in range(len(points) - 1):
        x1, y1 = points[i]
        x2, y2 = points[i + 1]
        dx = abs(x2 - x1)
        dy = abs(y2 - y1)
        sx = 1 if x1 < x2 else -1
        sy = 1 if y1 < y2 else -1
        err = dx - dy
        x, y = x1, y1
        while True:
            draw_pixel(img, x, y, mid)
            if x == x2 and y == y2:
                break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy
                x += sx
            if e2 < dx:
                err += dx
                y += sy

def draw_haste_rune(img, colors):
    """Lightning bolt - speed symbol."""
    # Main bolt shape
    points = [(9, 1), (5, 7), (8, 7), (4, 14), (6, 14), (10, 8), (7, 8), (9, 1)]
    draw_line_shaded(img, points, colors)
    # Bright highlight on top
    draw_pixel(img, 9, 2, colors["bright"])
    draw_pixel(img, 8, 3, colors["bright"])
    # Sparkle details
    draw_pixel(img, 12, 4, colors["mid"])
    draw_pixel(img, 3, 10, colors["mid"])

def draw_gate_rune(img, colors):
    """Portal/gateway - diamond with inner glyph."""
    # Outer diamond
    diamond = [(8, 1), (14, 8), (8, 14), (2, 8), (8, 1)]
    draw_line_shaded(img, diamond, colors)
    # Inner cross
    draw_line_shaded(img, [(8, 4), (8, 12)], colors)
    draw_line_shaded(img, [(4, 8), (12, 8)], colors)
    # Corner dots
    for x, y in [(5, 5), (11, 5), (5, 11), (11, 11)]:
        draw_pixel(img, x, y, colors["bright"])

def draw_enhancer_rune(img, colors):
    """Upward arrow - enhancement."""
    # Arrow shaft
    draw_line_shaded(img, [(8, 3), (8, 13)], colors)
    # Arrow head
    draw_line_shaded(img, [(4, 7), (8, 3), (12, 7)], colors)
    # Base bar
    draw_line_shaded(img, [(5, 13), (11, 13)], colors)
    # Plus signs for enhancement
    draw_pixel(img, 4, 10, colors["mid"])
    draw_pixel(img, 12, 10, colors["mid"])

def draw_strong_enhancer_rune(img, colors):
    """Double arrow - strong enhancement."""
    # First arrow
    draw_line_shaded(img, [(8, 1), (8, 6)], colors)
    draw_line_shaded(img, [(5, 4), (8, 1), (11, 4)], colors)
    # Second arrow
    draw_line_shaded(img, [(8, 7), (8, 12)], colors)
    draw_line_shaded(img, [(5, 10), (8, 7), (11, 10)], colors)
    # Base
    draw_line_shaded(img, [(5, 14), (11, 14)], colors)
    # Bright highlights
    draw_pixel(img, 8, 2, colors["bright"])
    draw_pixel(img, 8, 8, colors["bright"])

def draw_infinity_rune(img, colors):
    """Infinity symbol - endless."""
    # Left loop
    left_loop = [(8, 8), (6, 5), (4, 5), (2, 7), (2, 9), (4, 11), (6, 11), (8, 8)]
    draw_line_shaded(img, left_loop, colors)
    # Right loop
    right_loop = [(8, 8), (10, 5), (12, 5), (14, 7), (14, 9), (12, 11), (10, 11), (8, 8)]
    draw_line_shaded(img, right_loop, colors)
    # Center bright
    draw_pixel(img, 8, 8, colors["bright"])
    # Loop highlights
    draw_pixel(img, 3, 7, colors["bright"])
    draw_pixel(img, 13, 7, colors["bright"])

RUNE_DRAWERS = {
    "haste": draw_haste_rune,
    "gate": draw_gate_rune,
    "enhancer": draw_enhancer_rune,
    "strong_enhancer": draw_strong_enhancer_rune,
    "infinity": draw_infinity_rune,
}

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    for name, colors in RUNE_COLORS.items():
        img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

        drawer = RUNE_DRAWERS[name]
        drawer(img, colors)

        output_path = os.path.join(output_dir, f"rune_{name}.png")
        img.save(output_path)
        print(f"Generated: rune_{name}.png")

    print(f"\nAll rune textures generated in: {output_dir}")

if __name__ == "__main__":
    main()
