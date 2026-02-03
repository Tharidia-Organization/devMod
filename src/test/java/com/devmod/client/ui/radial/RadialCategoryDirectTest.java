package com.devmod.client.ui.radial;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialCategoryDirectTest {

    @Test
    @DisplayName("Subcategory creation links parent and adds navigation item")
    void subcategoryCreationLinksParent() {
        RadialCategory root = RadialCategory.builder("root").name("Root").build();
        root.addItem(new RadialMenuItem("Action", new TestAction("Action", "desc", true, true), "*", null));

        RadialCategory sub = root.addSubcategory("sub", "Sub", 0xFF00FF00, "*");

        assertTrue(sub.hasParent());
        assertEquals(root, sub.getParent());
        assertEquals(2, root.getItemCount());

        boolean hasLink = root.getItems().stream().anyMatch(RadialMenuItem::isSubcategoryLink);
        assertTrue(hasLink);
    }

    @Test
    @DisplayName("Visible items honor action visibility")
    void visibleItemsHonorActionVisibility() {
        RadialCategory root = new RadialCategory("root", "Root", 0xFFFFFFFF, "*");
        root.addItem(new RadialMenuItem("Visible", new TestAction("Visible", "desc", true, true), "*", null));
        root.addItem(new RadialMenuItem("Hidden", new TestAction("Hidden", "desc", false, true), "*", null));

        List<RadialMenuItem> visibleItems = root.getVisibleItems();
        assertEquals(1, visibleItems.size());
        assertEquals("Visible", visibleItems.get(0).getName());
    }

    @Test
    @DisplayName("Toggle actions switch state when executed")
    void toggleActionSwitchesState() {
        AtomicBoolean state = new AtomicBoolean(false);
        RadialMenuItem item = RadialMenuItem.toggle("Toggle", "*", state::get, v -> state.set(v), "toggle test");

        assertTrue(item.isToggle());
        item.execute();
        assertTrue(state.get());
    }

    private static class TestAction extends RadialAction {
        @Nonnull
        private final String name;
        private final String description;
        private final boolean visible;
        private final boolean executable;

        private TestAction(String name, String description, boolean visible, boolean executable) {
            this.name = Objects.requireNonNull(name, "name");
            this.description = description;
            this.visible = visible;
            this.executable = executable;
        }

        @Override
        public void execute() {
            // no-op
        }

        @Override
        public Component getLabel() {
            return Component.literal(name);
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public boolean canExecute() {
            return executable;
        }
    }
}
