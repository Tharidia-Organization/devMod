package com.devmod.config.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.devmod.config.component.BooleanComponent;
import com.devmod.config.component.FloatComponent;
import com.devmod.config.component.IntComponent;

@DisplayName("DecomposedConfig")
class DecomposedConfigTest {

    private final FloatComponent damage = new FloatComponent(
        "weapon.damage", "Damage", "weapon", 5.0f, 0.0f, 100.0f);
    private final IntComponent durability = new IntComponent(
        "weapon.durability", "Durability", "weapon", 100, 1, 2000);
    private final BooleanComponent enchantable = new BooleanComponent(
        "weapon.enchantable", "Enchantable", "weapon", true);

    private DecomposedConfig createConfig() {
        return new DecomposedConfig(Set.of(damage, durability, enchantable));
    }

    @Test
    @DisplayName("supportedComponents returns the configured set")
    void supportedComponentsReturnsConfiguredSet() {
        var config = createConfig();
        assertEquals(3, config.supportedComponents().size());
        assertTrue(config.supportedComponents().contains(damage));
        assertTrue(config.supportedComponents().contains(durability));
        assertTrue(config.supportedComponents().contains(enchantable));
    }

    @Test
    @DisplayName("get returns empty when no value set")
    void getReturnsEmptyWhenNotSet() {
        var config = createConfig();
        assertTrue(config.get(damage).isEmpty());
    }

    @Test
    @DisplayName("set and get round-trip correctly")
    void setAndGetRoundTrip() {
        var config = createConfig();
        config.set(damage, 10.0f);
        assertTrue(config.get(damage).isPresent());
        assertEquals(10.0f, config.get(damage).get());
    }

    @Test
    @DisplayName("has returns false when not set")
    void hasReturnsFalseWhenNotSet() {
        var config = createConfig();
        assertFalse(config.has(damage));
    }

    @Test
    @DisplayName("has returns true when set")
    void hasReturnsTrueWhenSet() {
        var config = createConfig();
        config.set(damage, 10.0f);
        assertTrue(config.has(damage));
    }

    @Test
    @DisplayName("remove clears the value")
    void removeClearsValue() {
        var config = createConfig();
        config.set(damage, 10.0f);
        assertTrue(config.has(damage));

        config.remove(damage);
        assertFalse(config.has(damage));
        assertTrue(config.get(damage).isEmpty());
    }

    @Test
    @DisplayName("set with null removes the value")
    void setNullRemovesValue() {
        var config = createConfig();
        config.set(damage, 10.0f);
        assertTrue(config.has(damage));

        config.set(damage, null);
        assertFalse(config.has(damage));
    }

    @Test
    @DisplayName("components returns only set components")
    void componentsReturnsOnlySetComponents() {
        var config = createConfig();
        assertTrue(config.components().isEmpty());

        config.set(damage, 10.0f);
        config.set(durability, 200);

        assertEquals(2, config.components().size());
        assertTrue(config.components().contains(damage));
        assertTrue(config.components().contains(durability));
    }

    @Test
    @DisplayName("getOrDefault returns value when set")
    void getOrDefaultReturnsValueWhenSet() {
        var config = createConfig();
        config.set(damage, 42.0f);
        assertEquals(42.0f, config.getOrDefault(damage));
    }

    @Test
    @DisplayName("getOrDefault returns component default when not set")
    void getOrDefaultReturnsDefaultWhenNotSet() {
        var config = createConfig();
        assertEquals(5.0f, config.getOrDefault(damage));
        assertEquals(100, config.getOrDefault(durability));
        assertTrue(config.getOrDefault(enchantable));
    }

    @Test
    @DisplayName("multiple components can be set independently")
    void multipleComponentsAreIndependent() {
        var config = createConfig();
        config.set(damage, 15.0f);
        config.set(durability, 500);
        config.set(enchantable, false);

        assertEquals(15.0f, config.get(damage).get());
        assertEquals(500, config.get(durability).get());
        assertFalse(config.get(enchantable).get());
    }

    @Test
    @DisplayName("supportedComponents is immutable")
    void supportedComponentsIsImmutable() {
        var config = createConfig();
        assertThrows(UnsupportedOperationException.class,
            () -> config.supportedComponents().add(damage));
    }
}
