#!/usr/bin/env python3
"""
Patch Effekseer .efkefc files to fix texture paths.
Changes '../../TestData/Effects/Textures/' to 'Textures/' (padded with nulls).
"""
import sys
import os

def patch_efkefc(input_path, output_path):
    with open(input_path, 'rb') as f:
        data = bytearray(f.read())

    # Old path in UTF-16LE: ../../TestData/Effects/Textures/
    old_path = '../../TestData/Effects/Textures/'.encode('utf-16-le')
    # New path in UTF-16LE: Textures/ (padded to same length)
    new_path_base = 'Textures/'
    # Pad with null bytes to match length
    padding_chars = (len(old_path) // 2) - len(new_path_base)
    new_path = new_path_base.encode('utf-16-le') + (b'\x00\x00' * padding_chars)

    # Find and replace
    count = 0
    pos = 0
    while True:
        idx = data.find(old_path, pos)
        if idx == -1:
            break
        data[idx:idx+len(old_path)] = new_path
        count += 1
        pos = idx + len(new_path)

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
        print("Usage: patch_efkefc.py <input.efkefc> [output.efkefc]")
        sys.exit(1)

    input_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else input_file

    if patch_efkefc(input_file, output_file):
        print(f"Successfully patched: {output_file}")
    else:
        print("No patching needed or failed")
