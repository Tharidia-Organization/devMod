#!/bin/bash
#
# DevMod Release Script
# Automates version bumping, tagging, and changelog generation
#
# Usage:
#   ./scripts/release.sh patch     # 0.1.0 -> 0.1.1
#   ./scripts/release.sh minor     # 0.1.0 -> 0.2.0
#   ./scripts/release.sh major     # 0.1.0 -> 1.0.0
#   ./scripts/release.sh 0.2.0     # Set explicit version
#   ./scripts/release.sh --dry-run # Preview changes without applying
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
GRADLE_PROPS="gradle.properties"
VERSION_KEY="mod_version"
CHANGELOG_FILE="CHANGELOG.md"
TAG_PREFIX="v"

# Parse arguments
DRY_RUN=false
BUMP_TYPE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run|-n)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [options] <version-type>"
            echo ""
            echo "Version types:"
            echo "  patch     Bump patch version (0.1.0 -> 0.1.1)"
            echo "  minor     Bump minor version (0.1.0 -> 0.2.0)"
            echo "  major     Bump major version (0.1.0 -> 1.0.0)"
            echo "  X.Y.Z     Set explicit version"
            echo ""
            echo "Options:"
            echo "  --dry-run, -n   Preview changes without applying"
            echo "  --help, -h      Show this help message"
            exit 0
            ;;
        *)
            BUMP_TYPE="$1"
            shift
            ;;
    esac
done

if [[ -z "$BUMP_TYPE" ]]; then
    echo -e "${RED}Error: No version type specified${NC}"
    echo "Usage: $0 [--dry-run] <patch|minor|major|X.Y.Z>"
    exit 1
fi

# Function to get current version from gradle.properties
get_current_version() {
    grep "^${VERSION_KEY}=" "$GRADLE_PROPS" | cut -d'=' -f2
}

# Function to bump version
bump_version() {
    local current="$1"
    local type="$2"

    IFS='.' read -r major minor patch <<< "$current"

    case "$type" in
        patch)
            patch=$((patch + 1))
            ;;
        minor)
            minor=$((minor + 1))
            patch=0
            ;;
        major)
            major=$((major + 1))
            minor=0
            patch=0
            ;;
        *)
            # Explicit version (validate format)
            if [[ "$type" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
                echo "$type"
                return
            else
                echo -e "${RED}Error: Invalid version format: $type${NC}" >&2
                exit 1
            fi
            ;;
    esac

    echo "${major}.${minor}.${patch}"
}

# Function to update gradle.properties
update_gradle_properties() {
    local new_version="$1"

    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}[DRY RUN]${NC} Would update $GRADLE_PROPS: $VERSION_KEY=$new_version"
    else
        sed -i.bak "s/^${VERSION_KEY}=.*/${VERSION_KEY}=${new_version}/" "$GRADLE_PROPS"
        rm -f "${GRADLE_PROPS}.bak"
        echo -e "${GREEN}Updated${NC} $GRADLE_PROPS: $VERSION_KEY=$new_version"
    fi
}

# Function to generate changelog entry
generate_changelog_entry() {
    local version="$1"
    local date=$(date +%Y-%m-%d)

    echo ""
    echo "## [$version] - $date"
    echo ""

    # Get commits since last tag
    local last_tag=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

    if [[ -n "$last_tag" ]]; then
        echo "### Changes since $last_tag"
        echo ""

        # Group commits by type
        local features=$(git log --oneline "$last_tag"..HEAD --grep="^feat" --grep="^add" -i 2>/dev/null || true)
        local fixes=$(git log --oneline "$last_tag"..HEAD --grep="^fix" -i 2>/dev/null || true)
        local refactors=$(git log --oneline "$last_tag"..HEAD --grep="^refactor" -i 2>/dev/null || true)
        local docs=$(git log --oneline "$last_tag"..HEAD --grep="^doc" -i 2>/dev/null || true)
        local other=$(git log --oneline "$last_tag"..HEAD 2>/dev/null | grep -v -i -E "^[a-f0-9]+ (feat|fix|refactor|doc)" || true)

        if [[ -n "$features" ]]; then
            echo "#### Added"
            echo "$features" | while read -r line; do
                echo "- ${line#* }"
            done
            echo ""
        fi

        if [[ -n "$fixes" ]]; then
            echo "#### Fixed"
            echo "$fixes" | while read -r line; do
                echo "- ${line#* }"
            done
            echo ""
        fi

        if [[ -n "$refactors" ]]; then
            echo "#### Changed"
            echo "$refactors" | while read -r line; do
                echo "- ${line#* }"
            done
            echo ""
        fi

        if [[ -n "$docs" ]]; then
            echo "#### Documentation"
            echo "$docs" | while read -r line; do
                echo "- ${line#* }"
            done
            echo ""
        fi

        if [[ -n "$other" ]]; then
            echo "#### Other"
            echo "$other" | head -20 | while read -r line; do
                echo "- ${line#* }"
            done
            echo ""
        fi
    else
        echo "Initial release"
        echo ""
    fi
}

# Function to update changelog
update_changelog() {
    local version="$1"

    if [[ ! -f "$CHANGELOG_FILE" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            echo -e "${YELLOW}[DRY RUN]${NC} Would create $CHANGELOG_FILE"
        else
            echo "# Changelog" > "$CHANGELOG_FILE"
            echo "" >> "$CHANGELOG_FILE"
            echo "All notable changes to DevMod will be documented in this file." >> "$CHANGELOG_FILE"
            echo "" >> "$CHANGELOG_FILE"
            echo "The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)," >> "$CHANGELOG_FILE"
            echo "and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)." >> "$CHANGELOG_FILE"
            echo "" >> "$CHANGELOG_FILE"
            echo -e "${GREEN}Created${NC} $CHANGELOG_FILE"
        fi
    fi

    local entry=$(generate_changelog_entry "$version")

    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}[DRY RUN]${NC} Would add changelog entry:"
        echo "$entry" | head -20
        if [[ $(echo "$entry" | wc -l) -gt 20 ]]; then
            echo "  ... (truncated)"
        fi
    else
        # Insert entry after header (line 6, after "Semantic Versioning" line)
        local temp_file=$(mktemp)
        head -6 "$CHANGELOG_FILE" > "$temp_file"
        echo "$entry" >> "$temp_file"
        tail -n +7 "$CHANGELOG_FILE" >> "$temp_file"
        mv "$temp_file" "$CHANGELOG_FILE"
        echo -e "${GREEN}Updated${NC} $CHANGELOG_FILE with version $version"
    fi
}

# Function to create git tag
create_git_tag() {
    local version="$1"
    local tag="${TAG_PREFIX}${version}"

    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}[DRY RUN]${NC} Would create git tag: $tag"
    else
        git add "$GRADLE_PROPS"
        if [[ -f "$CHANGELOG_FILE" ]]; then
            git add "$CHANGELOG_FILE"
        fi

        git commit -m "chore: release version $version

- Update mod_version to $version
- Update CHANGELOG.md"

        git tag -a "$tag" -m "Release $version"
        echo -e "${GREEN}Created${NC} git tag: $tag"
    fi
}

# Main execution
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}DevMod Release Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check we're in the right directory
if [[ ! -f "$GRADLE_PROPS" ]]; then
    echo -e "${RED}Error: $GRADLE_PROPS not found. Run from project root.${NC}"
    exit 1
fi

# Check for uncommitted changes
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    echo -e "${YELLOW}Warning: You have uncommitted changes${NC}"
    if [[ "$DRY_RUN" != "true" ]]; then
        read -p "Continue anyway? [y/N] " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
fi

# Get current and new versions
CURRENT_VERSION=$(get_current_version)
NEW_VERSION=$(bump_version "$CURRENT_VERSION" "$BUMP_TYPE")

echo -e "Current version: ${BLUE}$CURRENT_VERSION${NC}"
echo -e "New version:     ${GREEN}$NEW_VERSION${NC}"
echo ""

if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "${YELLOW}=== DRY RUN MODE ===${NC}"
    echo ""
fi

# Perform release steps
echo -e "${BLUE}Step 1/3:${NC} Update version in $GRADLE_PROPS"
update_gradle_properties "$NEW_VERSION"

echo ""
echo -e "${BLUE}Step 2/3:${NC} Update $CHANGELOG_FILE"
update_changelog "$NEW_VERSION"

echo ""
echo -e "${BLUE}Step 3/3:${NC} Create git commit and tag"
create_git_tag "$NEW_VERSION"

echo ""
echo -e "${BLUE}========================================${NC}"
if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "${YELLOW}DRY RUN COMPLETE${NC}"
    echo "Run without --dry-run to apply changes"
else
    echo -e "${GREEN}RELEASE COMPLETE${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Review changes: git show HEAD"
    echo "  2. Push to remote: git push && git push --tags"
    echo "  3. Create GitHub release (optional)"
fi
echo -e "${BLUE}========================================${NC}"
