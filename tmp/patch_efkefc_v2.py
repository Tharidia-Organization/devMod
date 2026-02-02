#!/usr/bin/env python3
"""
Patch Effekseer .efkefc files to fix texture paths.
Replaces problematic paths with equivalent-length valid paths.
"""
import sys
import os

def patch_efkefc(input_path, output_path):
    with open(input_path, 'rb') as f:
        data = bytearray(f.read())

    # Old path patterns (UTF-16LE)
    # Pattern 1: ../../TestData/Effects/Textures/Particle03.png (46 chars)
    old_path1 = '../../TestData/Effects/Textures/Particle03.png'.encode('utf-16-le')
    # New path: same length using slashes prefix (46 chars)
    # //////////////////////Textures/Particle03.png
    new_path1 = '//////////////////////Textures/Particle03.png'.encode('utf-16-le')

    # Pattern 2: Texture/Particle01.png (23 chars) - might need adjustment too
    # This one should work as-is since it doesn't have ..

    count = 0
    pos = 0
    while True:
        idx = data.find(old_path1, pos)
        if idx == -1:
            break
        print(f"  Found pattern at offset 0x{idx:X}")
        data[idx:idx+len(old_path1)] = new_path1
        count += 1
        pos = idx + len(new_path1)

    if count > 0:
        with open(output_path, 'wb') as f:
            f.write(data)
        print(f"Patched {count} occurrence(s) in {os.path.basename(input_path)}")
        return True
    else:
        print(f"No matches found in {os.path.basename(input_path)}")
        return False

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: patch_efkefc_v2.py <input.efkefc> [output.efkefc]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else input_file

    if patch_efkefc(input_file, output_file):
        print(f"Successfully patched: {output_file}")
    else:
        print("No patching needed or failed")
