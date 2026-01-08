#!/usr/bin/env python3
"""Generate portal block models and update blockstate with linked property."""

import os
import json

COLORS = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime",
    "pink", "gray", "light_gray", "cyan", "purple", "blue",
    "brown", "green", "red", "black"
]

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    models_dir = os.path.join(script_dir, "..", "src", "main", "resources",
                              "assets", "devmod", "models", "block")
    blockstates_dir = os.path.join(script_dir, "..", "src", "main", "resources",
                                   "assets", "devmod", "blockstates")

    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(blockstates_dir, exist_ok=True)

    # Generate models for each color (active and inactive)
    print("Generating portal models...")
    for color in COLORS:
        for linked in [True, False]:
            suffix = "" if linked else "_off"
            texture_name = f"custom_portal_{color}" if linked else f"custom_portal_{color}_off"

            # NS model
            ns_model = {
                "parent": "minecraft:block/nether_portal_ns",
                "render_type": "translucent",
                "textures": {
                    "portal": f"devmod:block/{texture_name}",
                    "particle": f"devmod:block/{texture_name}"
                }
            }
            ns_path = os.path.join(models_dir, f"custom_portal_{color}{suffix}_ns.json")
            with open(ns_path, 'w') as f:
                json.dump(ns_model, f, indent=2)

            # EW model
            ew_model = {
                "parent": "minecraft:block/nether_portal_ew",
                "render_type": "translucent",
                "textures": {
                    "portal": f"devmod:block/{texture_name}",
                    "particle": f"devmod:block/{texture_name}"
                }
            }
            ew_path = os.path.join(models_dir, f"custom_portal_{color}{suffix}_ew.json")
            with open(ew_path, 'w') as f:
                json.dump(ew_model, f, indent=2)

        print(f"  Created models for {color} (active + inactive)")

    # Generate blockstate with linked property
    print("\nGenerating blockstate with linked property...")
    variants = {}
    for color in COLORS:
        for linked in [True, False]:
            suffix = "" if linked else "_off"
            linked_str = "true" if linked else "false"

            variants[f"axis=x,color={color},linked={linked_str}"] = {
                "model": f"devmod:block/custom_portal_{color}{suffix}_ns"
            }
            variants[f"axis=z,color={color},linked={linked_str}"] = {
                "model": f"devmod:block/custom_portal_{color}{suffix}_ew"
            }

    blockstate = {"variants": variants}
    blockstate_path = os.path.join(blockstates_dir, "custom_portal.json")
    with open(blockstate_path, 'w') as f:
        json.dump(blockstate, f, indent=2)

    print(f"Updated blockstate: custom_portal.json")
    print(f"Total variants: {len(variants)}")
    print("\nDone!")

if __name__ == "__main__":
    main()
