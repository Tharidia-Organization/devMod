#!/usr/bin/env python3
"""
Generate portal igniter item models.
All colors use the same grayscale texture - color handler does the tinting.
"""

import json
import os

OUTPUT_DIR = "../src/main/resources/assets/devmod/models/item"

COLORS = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime",
    "pink", "gray", "light_gray", "cyan", "purple", "blue",
    "brown", "green", "red", "black"
]

def create_igniter_model(color):
    """Create item model with color-specific texture."""
    return {
        "parent": "minecraft:item/handheld",
        "textures": {
            "layer0": f"devmod:item/portal_igniter_{color}"
        }
    }

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_dir = os.path.join(script_dir, OUTPUT_DIR)
    os.makedirs(output_dir, exist_ok=True)

    for color in COLORS:
        model = create_igniter_model(color)
        output_path = os.path.join(output_dir, f"portal_igniter_{color}.json")
        with open(output_path, 'w') as f:
            json.dump(model, f, indent=2)
        print(f"Generated: portal_igniter_{color}.json -> devmod:item/portal_igniter_{color}")

    print(f"\nAll igniter models updated!")

if __name__ == "__main__":
    main()
