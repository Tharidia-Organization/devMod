package com.devmod.client.ui.radial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the radial menu's two allow-lists from contradicting each other.
 *
 * <p>{@code RadialMenuActionLayout.CURATED_ACTIONS} decides which actions enter the menu tree at
 * all; {@code RadialMenuScreen.FOCUSED_ACTION_IDS} decides which of those survive the default
 * FOCUSED profile. The second is therefore a subset of the first by construction -- and it was not.
 * Seven arena actions were declared focused-visible while the curation list omitted them, so the
 * ARENA sector was empty, drew dimmed, and answered "no items in this mode" on click. Because no hub
 * screen opens ArenaHubScreen either, that made the whole arena hub unreachable, which is what the
 * "the arena button does nothing" report turned out to be.
 *
 * <p>Read from source text rather than by reflection because both fields are private static finals
 * in client-only classes that cannot be loaded outside a Minecraft runtime -- the same approach the
 * neighbouring RadialMenuOrderingTest already uses.
 */
@DisplayName("Radial curated and focused lists agree")
class RadialCuratedFocusedConsistencyTest {

    private static final Path LAYOUT_SOURCE = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/RadialMenuActionLayout.java");
    private static final Path SCREEN_SOURCE = Paths.get(
        "src/main/java/com/devmod/client/ui/radial/RadialMenuScreen.java");

    private static String layoutSource;
    private static String screenSource;

    @BeforeAll
    static void readSources() throws IOException {
        layoutSource = Files.readString(LAYOUT_SOURCE);
        screenSource = Files.readString(SCREEN_SOURCE);
    }

    @Test
    @DisplayName("every focused action id is also curated, or it can never be drawn")
    void focusedIsSubsetOfCurated() {
        Set<String> curated = idsInSetLiteral(layoutSource, "CURATED_ACTIONS");
        Set<String> focused = idsInSetLiteral(screenSource, "FOCUSED_ACTION_IDS");

        assertTrue(curated.size() > 20,
            "failed to parse CURATED_ACTIONS; found only " + curated.size() + " ids");
        assertTrue(focused.size() > 10,
            "failed to parse FOCUSED_ACTION_IDS; found only " + focused.size() + " ids");

        Set<String> missing = new LinkedHashSet<>(focused);
        missing.removeAll(curated);
        assertTrue(missing.isEmpty(),
            "declared focused-visible but dropped by the curation filter, so unreachable: " + missing);
    }

    @Test
    @DisplayName("the arena hub is curated, so the arena sector has an entry point")
    void arenaHubIsReachable() {
        Set<String> curated = idsInSetLiteral(layoutSource, "CURATED_ACTIONS");
        Set<String> focused = idsInSetLiteral(screenSource, "FOCUSED_ACTION_IDS");
        // Named explicitly: it is the only way into ArenaHubScreen, which no hub screen opens.
        assertTrue(curated.contains("UI_ARENA_HUB_OPEN"),
            "UI_ARENA_HUB_OPEN must be curated or the arena hub is unreachable");
        assertTrue(focused.contains("UI_ARENA_HUB_OPEN"),
            "UI_ARENA_HUB_OPEN must be focused-visible; the default profile is FOCUSED");
    }

    /**
     * Pull {@code ActionIds.X} names out of one {@code Set.of(...)} initialiser.
     *
     * @param source the java source text
     * @param fieldName the field whose initialiser to read
     * @return the referenced ActionIds constant names
     */
    private static Set<String> idsInSetLiteral(String source, String fieldName) {
        int start = source.indexOf(fieldName + " = Set.of(");
        assertTrue(start >= 0, fieldName + " not found as a Set.of(...) literal");
        int open = source.indexOf('(', start);
        int depth = 0;
        int end = -1;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        assertTrue(end > open, "unbalanced parentheses while reading " + fieldName);

        Set<String> ids = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("ActionIds\\.([A-Z0-9_]+)")
            .matcher(source.substring(open, end));
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }
}
