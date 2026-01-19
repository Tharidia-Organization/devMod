#!/bin/bash
# Script to copy all TConstruct assets to DevMod

TC="/Users/erik/Desktop/DevMod/devMod/tmp/tinkersconstruct/src/main/resources/assets/tconstruct"
DM="/Users/erik/Desktop/DevMod/devMod/src/main/resources/assets/devmod"

# Copy GUI subdirectories
mkdir -p "$DM/textures/gui/tconstruct"
cp -r "$TC/textures/gui/book" "$DM/textures/gui/tconstruct/" 2>/dev/null
cp -r "$TC/textures/gui/jei" "$DM/textures/gui/tconstruct/" 2>/dev/null
cp -r "$TC/textures/gui/modifiers" "$DM/textures/gui/tconstruct/" 2>/dev/null
cp -r "$TC/textures/gui/crosshair" "$DM/textures/gui/tconstruct/" 2>/dev/null
echo "GUI copied"

# Copy item slot textures
mkdir -p "$DM/textures/item/tconstruct/slot"
cp -r "$TC/textures/item/slot"/* "$DM/textures/item/tconstruct/slot/" 2>/dev/null
echo "Slot textures copied"

# Copy gadget textures
mkdir -p "$DM/textures/item/tconstruct/gadgets"
cp -r "$TC/textures/item/gadgets"/* "$DM/textures/item/tconstruct/gadgets/" 2>/dev/null
echo "Gadget textures copied"

# Copy food textures
mkdir -p "$DM/textures/item/tconstruct/food"
cp -r "$TC/textures/item/food"/* "$DM/textures/item/tconstruct/food/" 2>/dev/null
echo "Food textures copied"

# Copy bottle textures
mkdir -p "$DM/textures/item/tconstruct/bottle"
cp -r "$TC/textures/item/bottle"/* "$DM/textures/item/tconstruct/bottle/" 2>/dev/null
echo "Bottle textures copied"

# Copy arrow textures
mkdir -p "$DM/textures/item/tconstruct/arrow"
cp -r "$TC/textures/item/arrow"/* "$DM/textures/item/tconstruct/arrow/" 2>/dev/null
echo "Arrow textures copied"

# Copy wood textures
mkdir -p "$DM/textures/item/tconstruct/wood"
cp -r "$TC/textures/item/wood"/* "$DM/textures/item/tconstruct/wood/" 2>/dev/null
echo "Wood textures copied"

# Copy slime textures
mkdir -p "$DM/textures/block/tconstruct/slime"
cp -r "$TC/textures/block/slime"/* "$DM/textures/block/tconstruct/slime/" 2>/dev/null
echo "Slime textures copied"

# Copy scorched textures
mkdir -p "$DM/textures/block/tconstruct/foundry"
cp -r "$TC/textures/block/foundry"/* "$DM/textures/block/tconstruct/foundry/" 2>/dev/null
echo "Scorched/Foundry textures copied"

# Copy storage textures
mkdir -p "$DM/textures/block/tconstruct/storage"
cp -r "$TC/textures/block/storage"/* "$DM/textures/block/tconstruct/storage/" 2>/dev/null
echo "Storage textures copied"

# Copy wood block textures
mkdir -p "$DM/textures/block/tconstruct/wood"
cp -r "$TC/textures/block/wood"/* "$DM/textures/block/tconstruct/wood/" 2>/dev/null
echo "Wood block textures copied"

# Copy geode textures
mkdir -p "$DM/textures/block/tconstruct/geode"
cp -r "$TC/textures/block/geode"/* "$DM/textures/block/tconstruct/geode/" 2>/dev/null
echo "Geode textures copied"

echo "All remaining assets copied!"
