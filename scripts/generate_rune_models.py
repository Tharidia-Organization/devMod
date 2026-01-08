#!/usr/bin/env python3
"""
Generate rune block models and blockstates for DevMod portal system.
Creates floor, ceiling, and wall variants with proper rotations.
"""

import json
import os

RUNES = ["haste", "gate", "enhancer", "strong_enhancer", "infinity"]

MODELS_DIR = "../src/main/resources/assets/devmod/models/block"
BLOCKSTATES_DIR = "../src/main/resources/assets/devmod/blockstates"
ITEM_MODELS_DIR = "../src/main/resources/assets/devmod/models/item"

def create_floor_model(rune_name):
    """Floor model - 1px thick on bottom."""
    return {
        "render_type": "cutout",
        "textures": {
            "rune": f"devmod:block/rune_{rune_name}",
            "particle": f"devmod:block/rune_{rune_name}"
        },
        "elements": [
            {
                "from": [0, 0, 0],
                "to": [16, 1, 16],
                "faces": {
                    "up": {"texture": "#rune"},
                    "down": {"texture": "#rune", "cullface": "down"}
                }
            }
        ]
    }

def create_ceiling_model(rune_name):
    """Ceiling model - 1px thick on top."""
    return {
        "render_type": "cutout",
        "textures": {
            "rune": f"devmod:block/rune_{rune_name}",
            "particle": f"devmod:block/rune_{rune_name}"
        },
        "elements": [
            {
                "from": [0, 15, 0],
                "to": [16, 16, 16],
                "faces": {
                    "up": {"texture": "#rune", "cullface": "up"},
                    "down": {"texture": "#rune"}
                }
            }
        ]
    }

def create_wall_model(rune_name):
    """Wall model - 1px thick attached to the block behind it.

    When facing=north, the rune attaches to the block at north,
    so it renders on the SOUTH side of its own blockspace (z=15-16).
    Rotations in blockstate handle other directions.
    """
    return {
        "render_type": "cutout",
        "textures": {
            "rune": f"devmod:block/rune_{rune_name}",
            "particle": f"devmod:block/rune_{rune_name}"
        },
        "elements": [
            {
                "from": [0, 0, 15],
                "to": [16, 16, 16],
                "faces": {
                    "north": {"texture": "#rune"},
                    "south": {"texture": "#rune", "cullface": "south"}
                }
            }
        ]
    }

def create_blockstate(rune_name):
    """Create blockstate with all face/facing variants."""
    return {
        "variants": {
            # Floor facing variants (all use floor model, just rotated)
            "face=floor,facing=north": {"model": f"devmod:block/rune_{rune_name}_floor"},
            "face=floor,facing=south": {"model": f"devmod:block/rune_{rune_name}_floor", "y": 180},
            "face=floor,facing=east": {"model": f"devmod:block/rune_{rune_name}_floor", "y": 90},
            "face=floor,facing=west": {"model": f"devmod:block/rune_{rune_name}_floor", "y": 270},

            # Ceiling facing variants
            "face=ceiling,facing=north": {"model": f"devmod:block/rune_{rune_name}_ceiling"},
            "face=ceiling,facing=south": {"model": f"devmod:block/rune_{rune_name}_ceiling", "y": 180},
            "face=ceiling,facing=east": {"model": f"devmod:block/rune_{rune_name}_ceiling", "y": 90},
            "face=ceiling,facing=west": {"model": f"devmod:block/rune_{rune_name}_ceiling", "y": 270},

            # Wall facing variants
            # facing=north means attached to block at north, so model faces south (no rotation)
            "face=wall,facing=north": {"model": f"devmod:block/rune_{rune_name}_wall"},
            "face=wall,facing=south": {"model": f"devmod:block/rune_{rune_name}_wall", "y": 180},
            "face=wall,facing=east": {"model": f"devmod:block/rune_{rune_name}_wall", "y": 270},
            "face=wall,facing=west": {"model": f"devmod:block/rune_{rune_name}_wall", "y": 90}
        }
    }

def create_item_model(rune_name):
    """Create item model that shows the rune texture."""
    return {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"devmod:block/rune_{rune_name}"
        }
    }

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    models_dir = os.path.join(script_dir, MODELS_DIR)
    blockstates_dir = os.path.join(script_dir, BLOCKSTATES_DIR)
    item_models_dir = os.path.join(script_dir, ITEM_MODELS_DIR)

    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(blockstates_dir, exist_ok=True)
    os.makedirs(item_models_dir, exist_ok=True)

    for rune in RUNES:
        # Create block models
        floor_model = create_floor_model(rune)
        ceiling_model = create_ceiling_model(rune)
        wall_model = create_wall_model(rune)

        with open(os.path.join(models_dir, f"rune_{rune}_floor.json"), 'w') as f:
            json.dump(floor_model, f, indent=2)
        with open(os.path.join(models_dir, f"rune_{rune}_ceiling.json"), 'w') as f:
            json.dump(ceiling_model, f, indent=2)
        with open(os.path.join(models_dir, f"rune_{rune}_wall.json"), 'w') as f:
            json.dump(wall_model, f, indent=2)

        print(f"Generated models for rune_{rune}")

        # Create blockstate
        blockstate = create_blockstate(rune)
        with open(os.path.join(blockstates_dir, f"rune_{rune}.json"), 'w') as f:
            json.dump(blockstate, f, indent=2)

        print(f"Generated blockstate for rune_{rune}")

        # Create item model
        item_model = create_item_model(rune)
        with open(os.path.join(item_models_dir, f"rune_{rune}.json"), 'w') as f:
            json.dump(item_model, f, indent=2)

        print(f"Generated item model for rune_{rune}")

    print(f"\nAll rune models and blockstates generated!")

if __name__ == "__main__":
    main()
