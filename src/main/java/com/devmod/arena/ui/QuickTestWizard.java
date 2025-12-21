package com.devmod.arena.ui;

import com.devmod.arena.registry.ArenaTemplate;
import com.devmod.arena.registry.ArenaTemplateRegistry;
import com.frenkvs.devmod.ui.UIConstants;
import com.frenkvs.devmod.ui.editor.components.EditorButton;
import com.frenkvs.devmod.ui.editor.components.EditorButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * DD35: QuickTestWizard - fast arena testing UI.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Template selection with search/filter</li>
 *   <li>Quick config override (size, player count)</li>
 *   <li>One-click test spawn</li>
 *   <li>Recent templates history</li>
 * </ul>
 */
public class QuickTestWizard extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuickTestWizard.class);

    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 350;
    private static final int PADDING = 12;
    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = EditorButton.Size.MEDIUM.height();

    private final ArenaTemplateRegistry registry;
    private final Consumer<TestConfig> onTest;
    private final List<String> recentTemplates;

    // UI State
    private EditBox searchBox;
    private List<TemplateEntry> filteredTemplates = new ArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int maxVisibleRows = 8;

    // Config overrides
    private int overridePlayerCount = -1; // -1 = use template default
    private boolean dryRun = false;
    private boolean forceTemplate = false; // DD29: Force template for session

    // Panel position
    private int panelX;
    private int panelY;

    // Buttons
    private EditorButton testButton;
    private EditorButton dryRunButton;
    private EditorButton forceTemplateButton;

    public QuickTestWizard(ArenaTemplateRegistry registry, Consumer<TestConfig> onTest) {
        super(Component.literal("Quick Test Wizard"));
        this.registry = Objects.requireNonNull(registry, "registry");
        this.onTest = Objects.requireNonNull(onTest, "onTest");
        this.recentTemplates = new ArrayList<>();
    }

    @Override
    protected void init() {
        // Center the panel
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - PANEL_HEIGHT) / 2;

        var safeFont = Objects.requireNonNull(font);

        // Search box
        searchBox = new EditBox(
            safeFont,
            panelX + PADDING,
            panelY + 40,
            PANEL_WIDTH - PADDING * 2,
            18,
            Objects.requireNonNull(Component.literal("Search..."))
        );
        searchBox.setHint(Objects.requireNonNull(Component.literal("Search templates...")));
        searchBox.setResponder(this::onSearchChanged);
        addRenderableWidget(searchBox);

        // Buttons at bottom
        int buttonY = panelY + PANEL_HEIGHT - PADDING - BUTTON_HEIGHT;
        int buttonWidth = (PANEL_WIDTH - PADDING * 3) / 2;

        testButton = EditorButton.builder("arena-test", "Test Arena")
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::startTest)
            .build();
        addRenderableWidget(new EditorButtonWidget(testButton,
            panelX + PADDING, buttonY, buttonWidth, BUTTON_HEIGHT));

        dryRunButton = EditorButton.builder("arena-dry-run", "Dry Run")
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(dryRun)
            .onToggle(value -> dryRun = value)
            .build();
        addRenderableWidget(new EditorButtonWidget(dryRunButton,
            panelX + PADDING * 2 + buttonWidth, buttonY, buttonWidth, BUTTON_HEIGHT));

        // Force Template button (DD29) - row above
        int forceButtonY = buttonY - BUTTON_HEIGHT - 4;
        forceTemplateButton = EditorButton.builder("arena-force-template", "Force Template")
            .style(EditorButton.Style.PRIMARY)
            .size(EditorButton.Size.MEDIUM)
            .toggleable(true)
            .toggled(forceTemplate)
            .onToggle(value -> forceTemplate = value)
            .build();
        addRenderableWidget(new EditorButtonWidget(forceTemplateButton,
            panelX + PADDING, forceButtonY, PANEL_WIDTH - PADDING * 2, BUTTON_HEIGHT));

        // Initialize template list
        refreshTemplateList();
    }

    private void onSearchChanged(String query) {
        refreshTemplateList();
        scrollOffset = 0;
        if (!filteredTemplates.isEmpty() && selectedIndex < 0) {
            selectedIndex = 0;
        }
    }

    private void refreshTemplateList() {
        filteredTemplates.clear();
        String query = searchBox != null ? searchBox.getValue().toLowerCase() : "";

        Collection<ArenaTemplate> all = registry.all();
        for (ArenaTemplate t : all) {
            if (matchesQuery(t, query)) {
                boolean isRecent = recentTemplates.contains(t.id());
                filteredTemplates.add(new TemplateEntry(t, isRecent));
            }
        }

        // Sort: recent first, then alphabetically
        filteredTemplates.sort((a, b) -> {
            if (a.isRecent() != b.isRecent()) {
                return a.isRecent() ? -1 : 1;
            }
            return a.template().id().compareTo(b.template().id());
        });

        // Clamp selection
        if (selectedIndex >= filteredTemplates.size()) {
            selectedIndex = filteredTemplates.size() - 1;
        }
    }

    private boolean matchesQuery(ArenaTemplate t, String query) {
        if (query.isEmpty()) return true;

        // Match ID
        if (t.id().toLowerCase().contains(query)) return true;

        // Match tags
        if (t.tags() != null) {
            for (String tag : t.tags()) {
                if (tag.toLowerCase().contains(query)) return true;
            }
        }

        return false;
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Panel background
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, UIConstants.Background.PANEL());

        // Border
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, UIConstants.Border.DEFAULT());

        // Header
        graphics.drawCenteredString(Objects.requireNonNull(font), "Quick Test Wizard",
            panelX + PANEL_WIDTH / 2, panelY + PADDING, UIConstants.Text.TITLE());

        // Template list
        renderTemplateList(graphics, mouseX, mouseY);

        // Selected template info
        renderSelectedInfo(graphics);

        // Render widgets (buttons, search box)
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTemplateList(GuiGraphics graphics, int mouseX, int mouseY) {
        int listX = panelX + PADDING;
        int listY = panelY + 70;
        int listWidth = PANEL_WIDTH - PADDING * 2;
        int listHeight = 160;

        var safeFont = Objects.requireNonNull(font);

        // List background
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight,
            UIConstants.setAlpha(UIConstants.Background.INPUT(), 0x88));

        // Calculate visible rows
        maxVisibleRows = listHeight / ROW_HEIGHT;

        // Render rows
        for (int i = 0; i < maxVisibleRows && i + scrollOffset < filteredTemplates.size(); i++) {
            int index = i + scrollOffset;
            TemplateEntry entry = filteredTemplates.get(index);
            int rowY = listY + i * ROW_HEIGHT;

            // Selection highlight
            if (index == selectedIndex) {
                graphics.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT - 1,
                    UIConstants.setAlpha(UIConstants.Accent.CYAN(), 0x33));
            }

            // Hover highlight
            if (mouseX >= listX && mouseX < listX + listWidth &&
                mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                graphics.fill(listX, rowY, listX + listWidth, rowY + ROW_HEIGHT - 1,
                    UIConstants.setAlpha(UIConstants.Background.HOVER(), 0x55));
            }

            // Template name
            String prefix = entry.isRecent() ? "\u2605 " : "";
            int textColor = index == selectedIndex ? UIConstants.Accent.CYAN() : UIConstants.Text.PRIMARY();
            graphics.drawString(safeFont, prefix + entry.template().id(), listX + 4, rowY + 6, textColor);

            // Version
            String version = "v" + entry.template().version();
            int versionWidth = safeFont.width(version);
            graphics.drawString(safeFont, version, listX + listWidth - versionWidth - 4, rowY + 6,
                UIConstants.Text.MUTED());
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            graphics.drawCenteredString(safeFont, "\u25B2", listX + listWidth / 2, listY - 8,
                UIConstants.Text.MUTED());
        }
        if (scrollOffset + maxVisibleRows < filteredTemplates.size()) {
            graphics.drawCenteredString(safeFont, "\u25BC", listX + listWidth / 2, listY + listHeight + 2,
                UIConstants.Text.MUTED());
        }
    }

    private void renderSelectedInfo(GuiGraphics graphics) {
        int infoY = panelY + 240;
        var safeFont = Objects.requireNonNull(font);

        if (selectedIndex >= 0 && selectedIndex < filteredTemplates.size()) {
            ArenaTemplate t = filteredTemplates.get(selectedIndex).template();

            // Size
            int sizeX = Objects.requireNonNullElse(t.sizeX(), t.size());
            int sizeZ = Objects.requireNonNullElse(t.sizeZ(), t.size());
            graphics.drawString(safeFont, "Size: " + sizeX + " x " + sizeZ,
                panelX + PADDING, infoY, UIConstants.Text.PRIMARY());

            // Spawn slots
            int slots = t.spawnSlots() != null ? t.spawnSlots().size() : 0;
            graphics.drawString(safeFont, "Spawn Slots: " + slots,
                panelX + PADDING + 150, infoY, UIConstants.Text.PRIMARY());

            // Palette preview (DD35: floor/walls/ceiling materials)
            infoY += 14;
            renderPalettePreview(graphics, t, infoY);

            // Tags
            infoY += 14;
            if (t.tags() != null && !t.tags().isEmpty()) {
                String tags = "Tags: " + String.join(", ", t.tags());
                if (tags.length() > 50) tags = tags.substring(0, 47) + "...";
                graphics.drawString(safeFont, tags, panelX + PADDING, infoY, UIConstants.Text.MUTED());
            }

            // Deprecated warning
            if (t.deprecated()) {
                infoY += 14;
                graphics.drawString(safeFont, "\u26A0 DEPRECATED", panelX + PADDING, infoY,
                    UIConstants.Accent.RED());
            }
        } else {
            graphics.drawString(safeFont, "Select a template to test", panelX + PADDING, infoY,
                UIConstants.Text.MUTED());
        }
    }

    /**
     * DD35: Renders palette preview showing floor/walls/ceiling materials.
     */
    private void renderPalettePreview(GuiGraphics graphics, ArenaTemplate t, int y) {
        var safeFont = Objects.requireNonNull(font);
        int x = panelX + PADDING;

        graphics.drawString(safeFont, "Palette:", x, y, UIConstants.Text.MUTED());
        x += 50;

        // Floor material
        if (t.floor() != null && t.floor().material() != null) {
            String floorMat = shortenMaterial(t.floor().material());
            int floorColor = getMaterialColor(t.floor().material());
            graphics.fill(x, y, x + 10, y + 10, floorColor);
            graphics.drawString(safeFont, floorMat, x + 14, y, UIConstants.Text.PRIMARY());
            x += 80;
        }

        // Walls material
        if (t.walls() != null && t.walls().material() != null) {
            String wallMat = shortenMaterial(t.walls().material());
            int wallColor = getMaterialColor(t.walls().material());
            graphics.fill(x, y, x + 10, y + 10, wallColor);
            graphics.drawString(safeFont, wallMat, x + 14, y, UIConstants.Text.PRIMARY());
            x += 80;
        }

        // Ceiling material
        if (t.ceiling() != null && t.ceiling().material() != null) {
            String ceilMat = shortenMaterial(t.ceiling().material());
            int ceilColor = getMaterialColor(t.ceiling().material());
            graphics.fill(x, y, x + 10, y + 10, ceilColor);
            graphics.drawString(safeFont, ceilMat, x + 14, y, UIConstants.Text.PRIMARY());
        }
    }

    /**
     * Shortens material name for display (removes minecraft: prefix and truncates).
     */
    private String shortenMaterial(String material) {
        if (material == null) return "?";
        String short_ = material.replace("minecraft:", "");
        if (short_.length() > 10) {
            short_ = short_.substring(0, 8) + "..";
        }
        return short_;
    }

    /**
     * Gets a representative color for a material (simplified heuristic).
     */
    private int getMaterialColor(String material) {
        if (material == null) return 0xFF808080;
        String m = material.toLowerCase();

        // Common material colors
        if (m.contains("stone") || m.contains("cobble")) return 0xFF808080;
        if (m.contains("wood") || m.contains("oak") || m.contains("plank")) return 0xFFB87333;
        if (m.contains("grass") || m.contains("moss")) return 0xFF4CAF50;
        if (m.contains("sand")) return 0xFFE8D4A0;
        if (m.contains("dirt") || m.contains("mud")) return 0xFF8B4513;
        if (m.contains("brick")) return 0xFFB22222;
        if (m.contains("iron") || m.contains("metal")) return 0xFFD3D3D3;
        if (m.contains("gold")) return 0xFFFFD700;
        if (m.contains("diamond")) return 0xFF00CED1;
        if (m.contains("emerald")) return 0xFF50C878;
        if (m.contains("obsidian")) return 0xFF1A1A2E;
        if (m.contains("nether") || m.contains("crimson")) return 0xFF8B0000;
        if (m.contains("end")) return 0xFFE8E8A0;
        if (m.contains("prismarine")) return 0xFF5F9EA0;
        if (m.contains("glass")) return 0x80FFFFFF;
        if (m.contains("wool") || m.contains("concrete")) {
            if (m.contains("white")) return 0xFFFFFFFF;
            if (m.contains("black")) return 0xFF1A1A1A;
            if (m.contains("red")) return 0xFFFF0000;
            if (m.contains("blue")) return 0xFF0000FF;
            if (m.contains("green")) return 0xFF00FF00;
            if (m.contains("yellow")) return 0xFFFFFF00;
        }

        // Default gray
        return 0xFF606060;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if clicking in template list
        int listX = panelX + PADDING;
        int listY = panelY + 70;
        int listWidth = PANEL_WIDTH - PADDING * 2;
        int listHeight = 160;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedRow = (int) ((mouseY - listY) / ROW_HEIGHT);
            int clickedIndex = clickedRow + scrollOffset;

            if (clickedIndex >= 0 && clickedIndex < filteredTemplates.size()) {
                if (selectedIndex == clickedIndex && button == 0) {
                    // Double-click to start test
                    startTest();
                } else {
                    selectedIndex = clickedIndex;
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Check if scrolling in template list
        int listX = panelX + PADDING;
        int listY = panelY + 70;
        int listWidth = PANEL_WIDTH - PADDING * 2;
        int listHeight = 160;

        if (mouseX >= listX && mouseX < listX + listWidth &&
            mouseY >= listY && mouseY < listY + listHeight) {

            if (scrollY > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (scrollY < 0 && scrollOffset + maxVisibleRows < filteredTemplates.size()) {
                scrollOffset++;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys for navigation
        if (keyCode == 264) { // Down
            if (selectedIndex < filteredTemplates.size() - 1) {
                selectedIndex++;
                ensureVisible(selectedIndex);
            }
            return true;
        } else if (keyCode == 265) { // Up
            if (selectedIndex > 0) {
                selectedIndex--;
                ensureVisible(selectedIndex);
            }
            return true;
        } else if (keyCode == 257) { // Enter
            startTest();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible(int index) {
        if (index < scrollOffset) {
            scrollOffset = index;
        } else if (index >= scrollOffset + maxVisibleRows) {
            scrollOffset = index - maxVisibleRows + 1;
        }
    }

    @Override
    public void renderBackground(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, UIConstants.setAlpha(UIConstants.Background.SCREEN(), 0x66));
    }

    private void startTest() {
        if (selectedIndex < 0 || selectedIndex >= filteredTemplates.size()) {
            LOGGER.warn("No template selected");
            return;
        }

        ArenaTemplate template = filteredTemplates.get(selectedIndex).template();

        // Add to recent
        recentTemplates.remove(template.id());
        recentTemplates.addFirst(template.id());
        if (recentTemplates.size() > 10) {
            recentTemplates.removeLast();
        }

        // Create test config
        TestConfig config = new TestConfig(
            template.id(),
            overridePlayerCount > 0 ? overridePlayerCount : null,
            dryRun,
            forceTemplate
        );

        LOGGER.info("Starting test for template '{}' (dryRun={}, forceTemplate={})",
            template.id(), dryRun, forceTemplate);

        // Close screen
        onClose();

        // Callback
        onTest.accept(config);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Opens the wizard screen.
     */
    public static void open(ArenaTemplateRegistry registry, Consumer<TestConfig> onTest) {
        Minecraft.getInstance().setScreen(new QuickTestWizard(registry, onTest));
    }

    // ========== Records ==========

    private record TemplateEntry(ArenaTemplate template, boolean isRecent) {}

    /**
     * Test configuration from wizard.
     * DD29: Extended with forceTemplate option for session-scoped forcing.
     */
    public record TestConfig(
        String templateId,
        Integer playerCountOverride,
        boolean dryRun,
        boolean forceTemplate
    ) {
        /**
         * Creates a config without force template (backwards compatible).
         */
        public TestConfig(String templateId, Integer playerCountOverride, boolean dryRun) {
            this(templateId, playerCountOverride, dryRun, false);
        }
    }
}
