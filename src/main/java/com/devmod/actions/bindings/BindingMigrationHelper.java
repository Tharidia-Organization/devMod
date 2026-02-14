package com.devmod.actions.bindings;

import java.util.Collection;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.actions.catalog.ActionSpec.BindingMeta;
import com.devmod.actions.catalog.ActionSpec.UiMeta;

/**
 * Utility that scans ActionSpec metadata and auto-creates bindings
 * in a BindingRegistry. Used during migration to populate bindings
 * from the declarative ActionSpec catalog.
 *
 * <p>Creates bindings based on:
 * <ul>
 *   <li>{@link UiMeta#menuPath()} -> {@link RadialBinding}</li>
 *   <li>{@link BindingMeta#keybindId()} -> {@link KeybindBinding}</li>
 *   <li>{@link BindingMeta#commandHint()} -> {@link CommandBinding}</li>
 *   <li>{@link BindingMeta#networkChannel()} -> {@link NetworkBinding}</li>
 * </ul>
 */
public final class BindingMigrationHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(BindingMigrationHelper.class);

    private BindingMigrationHelper() {}

    /**
     * Populates the given BindingRegistry with bindings derived from the
     * given ActionSpec collection.
     *
     * @param specs    all action specs to process
     * @param registry the binding registry to populate
     * @return the number of bindings created
     */
    public static int populateBindings(Collection<ActionSpec> specs, BindingRegistry registry) {
        Objects.requireNonNull(specs, "specs");
        Objects.requireNonNull(registry, "registry");

        int count = 0;

        for (ActionSpec spec : specs) {
            count += createBindingsForSpec(spec, registry);
        }

        LOGGER.info("[BindingMigrationHelper] Created {} bindings from {} specs",
            count, specs.size());
        return count;
    }

    /**
     * Creates all applicable bindings for a single ActionSpec.
     *
     * @return the number of bindings created for this spec
     */
    static int createBindingsForSpec(ActionSpec spec, BindingRegistry registry) {
        int count = 0;

        // Radial binding from menuPath
        String menuPath = spec.uiMeta().menuPath();
        if (menuPath != null && !menuPath.isEmpty()) {
            registry.register(new RadialBinding(
                spec.id(),
                menuPath,
                spec.uiMeta().iconItemId()
            ));
            count++;
        }

        // Keybind binding from keybindId
        String keybindId = spec.bindingMeta().keybindId();
        if (keybindId != null && !keybindId.isEmpty()) {
            registry.register(new KeybindBinding(
                spec.id(),
                keybindId
            ));
            count++;
        }

        // Command binding from commandHint
        String commandHint = spec.bindingMeta().commandHint();
        if (commandHint != null && !commandHint.isEmpty()) {
            registry.register(new CommandBinding(
                spec.id(),
                commandHint
            ));
            count++;
        }

        // Network binding from networkChannel
        String networkChannel = spec.bindingMeta().networkChannel();
        if (networkChannel != null && !networkChannel.isEmpty()) {
            registry.register(new NetworkBinding(
                spec.id(),
                networkChannel
            ));
            count++;
        }

        return count;
    }

    /**
     * Validates that all specs with a menuPath have a corresponding
     * RadialBinding, and returns the count of orphaned specs (specs
     * with menuPath but no binding).
     *
     * @return the number of specs with menuPath but missing radial binding
     */
    public static int countOrphanedRadialSpecs(Collection<ActionSpec> specs, BindingRegistry registry) {
        int orphans = 0;
        for (ActionSpec spec : specs) {
            String menuPath = spec.uiMeta().menuPath();
            if (menuPath != null && !menuPath.isEmpty()) {
                boolean hasRadial = registry.getBindings(spec.id()).stream()
                    .anyMatch(b -> b instanceof RadialBinding);
                if (!hasRadial) {
                    orphans++;
                }
            }
        }
        return orphans;
    }
}
