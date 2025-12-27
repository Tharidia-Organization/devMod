#!/bin/bash
# Script to add @OnlyIn(Dist.CLIENT) annotations to client classes
# Usage: ./tools/add-onlyin-annotations.sh

set -e

CLIENT_DIR="src/main/java/com/devmod/client"
IMPORT_ONLYIN="import net.neoforged.api.distmarker.OnlyIn;"
IMPORT_DIST="import net.neoforged.api.distmarker.Dist;"

# Counter for modified files
modified=0
skipped=0

echo "Adding @OnlyIn(Dist.CLIENT) annotations to client classes..."
echo ""

# Find all Java files in client directory
while IFS= read -r file; do
    # Skip if already has @OnlyIn annotation
    if grep -q "@OnlyIn(Dist.CLIENT)" "$file" 2>/dev/null; then
        ((skipped++))
        continue
    fi

    # Check if file has a public/class declaration (is a class file)
    if ! grep -qE "^public (class|interface|enum|record|abstract class)" "$file" 2>/dev/null; then
        # Try without public
        if ! grep -qE "^(class|interface|enum|record)" "$file" 2>/dev/null; then
            continue
        fi
    fi

    echo "Processing: $file"

    # Create temp file
    tmpfile=$(mktemp)

    # Add imports if not present
    has_onlyin_import=$(grep -c "import net.neoforged.api.distmarker.OnlyIn;" "$file" 2>/dev/null || echo "0")
    has_dist_import=$(grep -c "import net.neoforged.api.distmarker.Dist;" "$file" 2>/dev/null || echo "0")

    # Process file line by line
    added_imports=false
    added_annotation=false

    while IFS= read -r line || [[ -n "$line" ]]; do
        # After the last import line, add our imports if needed
        if [[ "$line" =~ ^import\  ]] && [[ "$added_imports" == false ]]; then
            echo "$line" >> "$tmpfile"
            continue
        fi

        # If we hit a non-import line after imports, add missing imports
        if [[ ! "$line" =~ ^import\  ]] && [[ "$added_imports" == false ]] && [[ "$line" != "" ]]; then
            if [[ "$has_onlyin_import" == "0" ]]; then
                echo "$IMPORT_ONLYIN" >> "$tmpfile"
            fi
            if [[ "$has_dist_import" == "0" ]]; then
                echo "$IMPORT_DIST" >> "$tmpfile"
            fi
            added_imports=true
        fi

        # Add @OnlyIn before class/interface/enum declaration
        if [[ "$added_annotation" == false ]] && [[ "$line" =~ ^(public\ )?(abstract\ )?(final\ )?(class|interface|enum|record)\  ]]; then
            echo "@OnlyIn(Dist.CLIENT)" >> "$tmpfile"
            added_annotation=true
        fi

        echo "$line" >> "$tmpfile"
    done < "$file"

    # Replace original with modified
    mv "$tmpfile" "$file"
    ((modified++))

done < <(find "$CLIENT_DIR" -name "*.java" -type f)

echo ""
echo "Done!"
echo "Modified: $modified files"
echo "Skipped (already annotated): $skipped files"
