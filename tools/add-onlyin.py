#!/usr/bin/env python3
"""Add @OnlyIn(Dist.CLIENT) annotations to client classes."""
import os
import re
import sys

CLIENT_DIR = "src/main/java/com/devmod/client"
ONLYIN_IMPORT = "import net.neoforged.api.distmarker.OnlyIn;"
DIST_IMPORT = "import net.neoforged.api.distmarker.Dist;"

def process_file(filepath):
    """Process a single Java file."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Skip if already annotated
    if '@OnlyIn(Dist.CLIENT)' in content:
        return False
    
    # Skip package-info files
    if 'package-info.java' in filepath:
        return False
    
    lines = content.split('\n')
    result = []
    imports_added = False
    annotation_added = False
    last_import_idx = -1
    
    # Find last import line
    for i, line in enumerate(lines):
        if line.strip().startswith('import '):
            last_import_idx = i
    
    for i, line in enumerate(lines):
        # After last import, add our imports
        if i == last_import_idx and not imports_added:
            result.append(line)
            if ONLYIN_IMPORT not in content:
                result.append(ONLYIN_IMPORT)
            if DIST_IMPORT not in content:
                result.append(DIST_IMPORT)
            imports_added = True
            continue
        
        # Before class/interface/enum declaration, add annotation
        stripped = line.strip()
        if not annotation_added:
            # Match class, interface, enum, record declarations
            if re.match(r'^(public\s+)?(abstract\s+)?(final\s+)?(class|interface|enum|record)\s+\w+', stripped):
                result.append('@OnlyIn(Dist.CLIENT)')
                annotation_added = True
        
        result.append(line)
    
    if annotation_added:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write('\n'.join(result))
        return True
    return False

def main():
    modified = 0
    skipped = 0
    
    for root, dirs, files in os.walk(CLIENT_DIR):
        for filename in files:
            if not filename.endswith('.java'):
                continue
            filepath = os.path.join(root, filename)
            if process_file(filepath):
                print(f"Modified: {filepath}")
                modified += 1
            else:
                skipped += 1
    
    print(f"\nDone! Modified: {modified}, Skipped: {skipped}")

if __name__ == '__main__':
    main()
