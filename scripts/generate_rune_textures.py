#!/usr/bin/env python3
"""
Generate thin magical rune textures for DevMod portal system.
Each rune is 16x16 pixels with transparent background and 1px thick lines.
"""

from PIL import Image, ImageDraw
import os

# Output directory
OUTPUT_DIR = "../src/main/resources/assets/devmod/textures/block"

# Rune colors (from RuneType.java)
RUNE_COLORS = {
    "haste": (0x55, 0xFF, 0xFF, 255),         # Cyan
    "gate": (0xAA, 0x00, 0xAA, 255),           # Purple
    "enhancer": (0xFF, 0xAA, 0x00, 255),       # Orange
    "strong_enhancer": (0xFF, 0x55, 0x00, 255), # Red-orange
    "infinity": (0x00, 0xFF, 0x00, 255),       # Green
}

def draw_haste_rune(draw, color):
    """Lightning bolt symbol for speed/haste."""
    # Lightning bolt shape
    points = [
        (10, 1), (6, 7), (9, 7), (5, 15), (11, 8), (8, 8), (10, 1)
    ]
    draw.line(points, fill=color, width=1)

def draw_gate_rune(draw, color):
    """Portal/gateway symbol - diamond with inner lines."""
    # Outer diamond
    draw.line([(8, 1), (15, 8)], fill=color, width=1)
    draw.line([(15, 8), (8, 15)], fill=color, width=1)
    draw.line([(8, 15), (1, 8)], fill=color, width=1)
    draw.line([(1, 8), (8, 1)], fill=color, width=1)
    # Inner cross
    draw.line([(8, 4), (8, 12)], fill=color, width=1)
    draw.line([(4, 8), (12, 8)], fill=color, width=1)

def draw_enhancer_rune(draw, color):
    """Upward arrow/enhancement symbol."""
    # Upward arrow
    draw.line([(8, 2), (8, 14)], fill=color, width=1)  # Vertical shaft
    draw.line([(4, 6), (8, 2)], fill=color, width=1)   # Left arrow head
    draw.line([(12, 6), (8, 2)], fill=color, width=1)  # Right arrow head
    # Enhancement lines
    draw.line([(5, 10), (11, 10)], fill=color, width=1)

def draw_strong_enhancer_rune(draw, color):
    """Double upward arrow for strong enhancement."""
    # First arrow
    draw.line([(8, 1), (8, 7)], fill=color, width=1)
    draw.line([(5, 4), (8, 1)], fill=color, width=1)
    draw.line([(11, 4), (8, 1)], fill=color, width=1)
    # Second arrow
    draw.line([(8, 8), (8, 14)], fill=color, width=1)
    draw.line([(5, 11), (8, 8)], fill=color, width=1)
    draw.line([(11, 11), (8, 8)], fill=color, width=1)

def draw_infinity_rune(draw, color):
    """Infinity/endless symbol - figure 8 on its side."""
    # Simplified infinity symbol using lines (1px)
    # Left loop
    draw.line([(3, 8), (5, 5)], fill=color, width=1)
    draw.line([(5, 5), (6, 5)], fill=color, width=1)
    draw.line([(6, 5), (8, 8)], fill=color, width=1)
    draw.line([(3, 8), (5, 11)], fill=color, width=1)
    draw.line([(5, 11), (6, 11)], fill=color, width=1)
    draw.line([(6, 11), (8, 8)], fill=color, width=1)
    # Right loop
    draw.line([(8, 8), (10, 5)], fill=color, width=1)
    draw.line([(10, 5), (11, 5)], fill=color, width=1)
    draw.line([(11, 5), (13, 8)], fill=color, width=1)
    draw.line([(8, 8), (10, 11)], fill=color, width=1)
    draw.line([(10, 11), (11, 11)], fill=color, width=1)
    draw.line([(11, 11), (13, 8)], fill=color, width=1)

RUNE_DRAWERS = {
    "haste": draw_haste_rune,
    "gate": draw_gate_rune,
    "enhancer": draw_enhancer_rune,
    "strong_enhancer": draw_strong_enhancer_rune,
    "infinity": draw_infinity_rune,
}

def generate_rune_texture(name, color, drawer):
    """Generate a 16x16 rune texture with transparent background."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    drawer(draw, color)

    return img

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    for name, color in RUNE_COLORS.items():
        drawer = RUNE_DRAWERS[name]
        img = generate_rune_texture(name, color, drawer)

        output_path = os.path.join(output_dir, f"rune_{name}.png")
        img.save(output_path)
        print(f"Generated: rune_{name}.png")

    print(f"\nAll rune textures generated in: {output_dir}")

if __name__ == "__main__":
    main()
