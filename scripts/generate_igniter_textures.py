#!/usr/bin/env python3
"""
Generate portal igniter textures in End Eye style for DevMod.
Creates animated orb textures with energy effect.
"""

from PIL import Image, ImageDraw
import os
import json

OUTPUT_DIR = "../src/main/resources/assets/devmod/textures/item"

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

# Animation frames for energy effect
ANIMATION_FRAMES = 8

def lerp_color(c1, c2, t):
    """Linear interpolation between two colors."""
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

def draw_orb(draw, cx, cy, radius, color, frame=0):
    """Draw an End Eye style orb with given color and frame."""
    # Calculate animation offset for energy effect
    phase = frame / ANIMATION_FRAMES

    # Outer glow (darker shade)
    dark_color = tuple(int(c * 0.4) for c in color)
    for i in range(radius, radius - 2, -1):
        draw.ellipse(
            [cx - i, cy - i, cx + i, cy + i],
            fill=dark_color + (255,)
        )

    # Main orb body (medium shade)
    medium_color = tuple(int(c * 0.7) for c in color)
    draw.ellipse(
        [cx - radius + 2, cy - radius + 2, cx + radius - 2, cy + radius - 2],
        fill=medium_color + (255,)
    )

    # Inner glow (bright)
    inner_radius = radius - 3
    draw.ellipse(
        [cx - inner_radius, cy - inner_radius, cx + inner_radius, cy + inner_radius],
        fill=color + (255,)
    )

    # Central bright spot (white-ish highlight)
    highlight = tuple(min(255, int(c * 1.5)) for c in color)
    spot_offset_x = int(1 * (0.5 - phase) * 2)  # Slight movement
    draw.ellipse(
        [cx - 1 + spot_offset_x, cy - 2, cx + 1 + spot_offset_x, cy],
        fill=highlight + (255,)
    )

    # Pupil (dark center that appears sometimes)
    if frame % 4 < 2:  # Blink effect
        draw.ellipse(
            [cx - 1, cy - 1, cx + 1, cy + 1],
            fill=(20, 20, 30, 255)
        )

def draw_stick(draw, color):
    """Draw the igniter stick/handle."""
    # Stick color (dark wood)
    stick_color = (101, 67, 33, 255)
    stick_highlight = (139, 90, 43, 255)

    # Main stick body
    draw.line([(7, 8), (7, 15)], fill=stick_color, width=1)
    draw.line([(8, 9), (8, 15)], fill=stick_highlight, width=1)

    # Binding near orb (uses the portal color)
    binding_color = tuple(int(c * 0.6) for c in color) + (255,)
    draw.point((7, 7), fill=binding_color)
    draw.point((8, 7), fill=binding_color)

def generate_igniter_texture(name, color):
    """Generate a single igniter texture with animation frames."""
    # Create sprite sheet: 16x16 * ANIMATION_FRAMES high
    width = 16
    height = 16 * ANIMATION_FRAMES

    img = Image.new('RGBA', (width, height), (0, 0, 0, 0))

    for frame in range(ANIMATION_FRAMES):
        # Create frame
        frame_img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        draw = ImageDraw.Draw(frame_img)

        # Draw stick first
        draw_stick(draw, color)

        # Draw orb (centered at x=7.5, y=4, radius=4)
        draw_orb(draw, 7, 4, 4, color, frame)

        # Paste frame into sprite sheet
        img.paste(frame_img, (0, frame * 16))

    return img

def generate_mcmeta():
    """Generate animation metadata file."""
    return {
        "animation": {
            "frametime": 4,
            "interpolate": True
        }
    }

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    for name, color in COLORS.items():
        # Generate texture
        img = generate_igniter_texture(name, color)

        output_path = os.path.join(output_dir, f"portal_igniter_{name}.png")
        img.save(output_path)
        print(f"Generated: portal_igniter_{name}.png")

        # Generate mcmeta for animation
        mcmeta_path = os.path.join(output_dir, f"portal_igniter_{name}.png.mcmeta")
        with open(mcmeta_path, 'w') as f:
            json.dump(generate_mcmeta(), f, indent=2)
        print(f"Generated: portal_igniter_{name}.png.mcmeta")

    print(f"\nAll igniter textures generated in: {output_dir}")

if __name__ == "__main__":
    main()
