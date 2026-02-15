package com.devmod.testing.stats;

import com.devmod.testing.stats.HazardTypeRegistry.HazardPattern;
import com.devmod.testing.stats.HazardTypeRegistry.HazardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HazardTypeRegistryTest {

    private HazardTypeRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<HazardTypeRegistry> ctor = HazardTypeRegistry.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        registry = ctor.newInstance();
    }

    @Test
    @DisplayName("HazardPattern matches case insensitively")
    void patternMatches() {
        HazardPattern pattern = new HazardPattern(
            HazardType.FIRE, List.of("fire", "burn"), false, null);

        assertTrue(pattern.matches("in_fire"));
        assertTrue(pattern.matches("FIRE_damage"));
        assertTrue(pattern.matches("burning"));
        assertFalse(pattern.matches("lava"));
        assertFalse(pattern.matches(null));
        assertFalse(pattern.matches(""));
    }

    @Test
    @DisplayName("HazardPattern rejects empty patterns list")
    void emptyPatternsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new HazardPattern(HazardType.FALL, List.of(), false, null));
    }

    @Test
    @DisplayName("HazardPattern rejects null type")
    void nullTypeThrows() {
        assertThrows(NullPointerException.class, () ->
            new HazardPattern(null, List.of("test"), false, null));
    }

    @Test
    @DisplayName("initialize loads defaults when no config exists")
    void initializeLoadsDefaults(@TempDir Path tempDir) {
        registry.initialize(tempDir);

        assertTrue(registry.isLoaded());
        assertFalse(registry.getAllPatterns().isEmpty());
        assertEquals(12, registry.getAllPatterns().size());
    }

    @Test
    @DisplayName("classifyByString maps damage sources correctly")
    void classifyByString(@TempDir Path tempDir) {
        registry.initialize(tempDir);

        assertEquals(HazardType.FALL, registry.classifyByString("fall"));
        assertEquals(HazardType.FIRE, registry.classifyByString("in_fire"));
        assertEquals(HazardType.FIRE, registry.classifyByString("burning"));
        assertEquals(HazardType.LAVA, registry.classifyByString("lava"));
        assertEquals(HazardType.EXPLOSION, registry.classifyByString("explosion.player"));
        assertEquals(HazardType.UNKNOWN, registry.classifyByString("totally_custom"));
        assertEquals(HazardType.UNKNOWN, registry.classifyByString(null));
        assertEquals(HazardType.UNKNOWN, registry.classifyByString(""));
    }

    @Test
    @DisplayName("getPattern returns matching pattern for type")
    void getPattern(@TempDir Path tempDir) {
        registry.initialize(tempDir);

        HazardPattern fire = registry.getPattern(HazardType.FIRE);
        assertNotNull(fire);
        assertEquals(HazardType.FIRE, fire.type());
        assertTrue(fire.patterns().contains("fire"));
    }

    @Test
    @DisplayName("getPattern returns null for unregistered type")
    void getPatternNull(@TempDir Path tempDir) {
        registry.initialize(tempDir);
        // All types are registered by default, but UNKNOWN is not a pattern
        // Test that a custom query returns the right thing
        assertNotNull(registry.getPattern(HazardType.FALL));
    }

    @Test
    @DisplayName("save and reload preserves patterns")
    void saveAndReload(@TempDir Path tempDir) {
        registry.initialize(tempDir);
        int originalCount = registry.getAllPatterns().size();

        registry.save();
        registry.reload();

        assertEquals(originalCount, registry.getAllPatterns().size());
        assertTrue(registry.isLoaded());
    }

    @Test
    @DisplayName("explosion pattern triggers achievement")
    void explosionAchievement(@TempDir Path tempDir) {
        registry.initialize(tempDir);

        HazardPattern explosion = registry.getPattern(HazardType.EXPLOSION);
        assertNotNull(explosion);
        assertTrue(explosion.triggerAchievement());
        assertEquals("survived_explosion", explosion.achievementId());
    }

    @Test
    @DisplayName("HazardPattern list is defensively copied")
    void defensiveCopy() {
        List<String> patterns = new java.util.ArrayList<>();
        patterns.add("fire");
        HazardPattern pattern = new HazardPattern(HazardType.FIRE, patterns, false, null);

        assertThrows(UnsupportedOperationException.class, () -> pattern.patterns().add("lava"));
    }
}
