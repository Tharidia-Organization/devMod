package com.devmod.client.ui.components;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Centralized registry for UI components.
 *
 * <p>Provides:
 * <ul>
 *   <li>Component registration by type and ID</li>
 *   <li>Factory pattern for component creation</li>
 *   <li>Component discovery and listing</li>
 *   <li>Theme-aware component creation</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Register a component factory
 * ComponentRegistry.register(ComponentType.BUTTON, "primary", PrimaryButton::new);
 *
 * // Create a component
 * Button btn = ComponentRegistry.create(ComponentType.BUTTON, "primary", Button.class);
 * }</pre>
 */
@OnlyIn(Dist.CLIENT)
public final class ComponentRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ComponentRegistry.class);

    // ============================================================================
    // STORAGE
    // ============================================================================

    /** Component factories by type and ID */
    private static final Map<ComponentKey, Supplier<?>> factories = new ConcurrentHashMap<>();

    /** Component metadata */
    private static final Map<ComponentKey, ComponentMeta> metadata = new ConcurrentHashMap<>();

    private ComponentRegistry() {}

    // ============================================================================
    // REGISTRATION
    // ============================================================================

    /**
     * Register a component factory.
     *
     * @param type The component type
     * @param id The component ID within that type
     * @param factory The factory to create instances
     */
    public static <T> void register(ComponentType type, String id, Supplier<T> factory) {
        register(type, id, factory, null);
    }

    /**
     * Register a component factory with metadata.
     *
     * @param type The component type
     * @param id The component ID within that type
     * @param factory The factory to create instances
     * @param meta Optional metadata about the component
     */
    public static <T> void register(
            ComponentType type,
            String id,
            Supplier<T> factory,
            @Nullable ComponentMeta meta) {
        ComponentKey key = new ComponentKey(type, id);
        Supplier<?> existing = factories.put(key, factory);

        if (existing != null) {
            LOGGER.warn("[ComponentRegistry] Overwriting {} / {}", type, id);
        } else {
            LOGGER.debug("[ComponentRegistry] Registered {} / {}", type, id);
        }

        if (meta != null) {
            metadata.put(key, meta);
        }
    }

    /**
     * Register a component factory with a builder.
     */
    public static <T> RegistrationBuilder<T> builder(ComponentType type, String id) {
        return new RegistrationBuilder<>(type, id);
    }

    // ============================================================================
    // CREATION
    // ============================================================================

    /**
     * Create a component instance.
     *
     * @param type The component type
     * @param id The component ID
     * @param expectedType Expected component class
     * @return The created component, or null if not registered
     */
    @Nullable
    public static <T> T create(ComponentType type, String id, Class<T> expectedType) {
        ComponentKey key = new ComponentKey(type, id);
        Supplier<?> factory = factories.get(key);

        if (factory == null) {
            LOGGER.warn("[ComponentRegistry] No factory for {} / {}", type, id);
            return null;
        }

        try {
            Object value = factory.get();
            if (!expectedType.isInstance(value)) {
                LOGGER.warn("[ComponentRegistry] Type mismatch for {} / {} (expected {}, got {})",
                    type, id, expectedType.getName(),
                    value != null ? value.getClass().getName() : "null");
                return null;
            }
            return expectedType.cast(value);
        } catch (Exception e) {
            LOGGER.error("[ComponentRegistry] Failed to create {} / {}", type, id, e);
            return null;
        }
    }

    /**
     * Create a component instance with fallback.
     *
     * @param type The component type
     * @param id The component ID
     * @param expectedType Expected component class
     * @param fallback Fallback factory if not registered
     * @return The created component
     */
    public static <T> T createOrDefault(ComponentType type, String id, Class<T> expectedType, Supplier<T> fallback) {
        T component = create(type, id, expectedType);
        return component != null ? component : fallback.get();
    }

    /**
     * Get a component as Optional.
     */
    public static <T> Optional<T> tryCreate(ComponentType type, String id, Class<T> expectedType) {
        return Optional.ofNullable(create(type, id, expectedType));
    }

    // ============================================================================
    // QUERY
    // ============================================================================

    /**
     * Check if a component is registered.
     */
    public static boolean has(ComponentType type, String id) {
        return factories.containsKey(new ComponentKey(type, id));
    }

    /**
     * Get metadata for a component.
     */
    public static Optional<ComponentMeta> getMeta(ComponentType type, String id) {
        return Optional.ofNullable(metadata.get(new ComponentKey(type, id)));
    }

    /**
     * Get all registered component IDs for a type.
     */
    public static java.util.List<String> getIds(ComponentType type) {
        return factories.keySet().stream()
            .filter(k -> k.type() == type)
            .map(ComponentKey::id)
            .toList();
    }

    /**
     * Get count of registered components.
     */
    public static int size() {
        return factories.size();
    }

    /**
     * Clear all registrations (for testing).
     */
    public static void clear() {
        factories.clear();
        metadata.clear();
        LOGGER.info("[ComponentRegistry] Cleared all registrations");
    }

    // ============================================================================
    // DIAGNOSTICS
    // ============================================================================

    /**
     * Get diagnostics info.
     */
    public static String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("ComponentRegistry (").append(factories.size()).append(" components):\n");

        for (ComponentType type : ComponentType.values()) {
            long count = factories.keySet().stream()
                .filter(k -> k.type() == type)
                .count();
            if (count > 0) {
                sb.append("  ").append(type).append(": ").append(count).append("\n");
            }
        }

        return sb.toString();
    }

    // ============================================================================
    // TYPES
    // ============================================================================

    /**
     * Component types for categorization.
     */
    public enum ComponentType {
        /** Buttons (primary, secondary, icon, etc.) */
        BUTTON,
        /** Text inputs */
        INPUT,
        /** Sliders */
        SLIDER,
        /** Checkboxes and toggles */
        TOGGLE,
        /** Dropdown selectors */
        DROPDOWN,
        /** Panels and containers */
        PANEL,
        /** List views */
        LIST,
        /** Tab components */
        TAB,
        /** Tooltips */
        TOOLTIP,
        /** Dialogs and modals */
        DIALOG,
        /** Progress indicators */
        PROGRESS,
        /** Icons and badges */
        ICON,
        /** Custom/misc */
        CUSTOM
    }

    /**
     * Component key for map lookup.
     */
    private record ComponentKey(ComponentType type, String id) {}

    /**
     * Component metadata.
     */
    public record ComponentMeta(
        String displayName,
        String description,
        String category,
        boolean experimental
    ) {
        public static ComponentMeta of(String displayName) {
            return new ComponentMeta(displayName, "", "", false);
        }

        public static ComponentMeta of(String displayName, String description) {
            return new ComponentMeta(displayName, description, "", false);
        }
    }

    /**
     * Builder for component registration.
     */
    public static class RegistrationBuilder<T> {
        private final ComponentType type;
        private final String id;
        @Nullable
        private Supplier<T> factory;
        private String displayName;
        private String description = "";
        private String category = "";
        private boolean experimental = false;

        RegistrationBuilder(ComponentType type, String id) {
            this.type = type;
            this.id = id;
            this.displayName = id;
        }

        public RegistrationBuilder<T> factory(Supplier<T> factory) {
            this.factory = factory;
            return this;
        }

        public RegistrationBuilder<T> displayName(String name) {
            this.displayName = name;
            return this;
        }

        public RegistrationBuilder<T> description(String desc) {
            this.description = desc;
            return this;
        }

        public RegistrationBuilder<T> category(String cat) {
            this.category = cat;
            return this;
        }

        public RegistrationBuilder<T> experimental() {
            this.experimental = true;
            return this;
        }

        public void register() {
            if (factory == null) {
                throw new IllegalStateException("Factory is required");
            }
            ComponentMeta meta = new ComponentMeta(displayName, description, category, experimental);
            ComponentRegistry.register(type, id, factory, meta);
        }
    }

    // ============================================================================
    // STANDARD COMPONENTS
    // ============================================================================

    /**
     * Initialize standard DevMod components.
     * Called during client setup.
     */
    public static void initializeStandard() {
        LOGGER.info("[ComponentRegistry] Initializing standard components");

        // Buttons will be registered here as the UI library grows
        // Example:
        // register(ComponentType.BUTTON, "primary", PrimaryButton::new);
        // register(ComponentType.BUTTON, "secondary", SecondaryButton::new);
        // register(ComponentType.BUTTON, "icon", IconButton::new);

        LOGGER.info("[ComponentRegistry] Standard components initialized");
    }
}
