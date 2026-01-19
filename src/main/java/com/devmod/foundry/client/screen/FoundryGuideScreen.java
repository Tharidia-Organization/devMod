package com.devmod.foundry.client.screen;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.foundry.progression.FoundryPlayerProgress;
import com.devmod.foundry.progression.FoundryProgressAttachment;
import com.devmod.foundry.progression.FoundrySpecialization;
import com.devmod.foundry.progression.FoundryTier;
import com.devmod.foundry.tool.material.FoundryMaterialRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierRegistry;
import com.devmod.foundry.tool.modifier.FoundryModifierSlot;

/**
 * Simple in-game guidebook for the Foundry module.
 */
@OnlyIn(Dist.CLIENT)
public class FoundryGuideScreen extends Screen {
    private static final int BOOK_WIDTH = 256;
    private static final int BOOK_HEIGHT = 180;
    private static final int PADDING = 12;
    private static final int FOOTER_HEIGHT = 24;

    private static final List<GuidePage> PAGES = List.of(
        new GuidePage("gui.devmod.foundry_guide.page1.title", "gui.devmod.foundry_guide.page1.body", false),
        new GuidePage("gui.devmod.foundry_guide.page2.title", "gui.devmod.foundry_guide.page2.body", false),
        new GuidePage("gui.devmod.foundry_guide.page3.title", "gui.devmod.foundry_guide.page3.body", false),
        new GuidePage("gui.devmod.foundry_guide.page4.title", "gui.devmod.foundry_guide.page4.body", false),
        new GuidePage("gui.devmod.foundry_guide.page5.title", "gui.devmod.foundry_guide.page5.body", false),
        new GuidePage("gui.devmod.foundry_guide.progress.title", "gui.devmod.foundry_guide.progress.body", true)
    );

    private int leftPos;
    private int topPos;
    private int pageIndex = 0;
    @Nullable
    private Button prevButton;
    @Nullable
    private Button nextButton;

    public FoundryGuideScreen() {
        super(Component.translatable("gui.devmod.foundry_guide.title"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new FoundryGuideScreen());
    }

    @Override
    protected void init() {
        leftPos = (width - BOOK_WIDTH) / 2;
        topPos = (height - BOOK_HEIGHT) / 2;
        pageIndex = Mth.clamp(pageIndex, 0, PAGES.size() - 1);

        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> turnPage(-1))
            .pos(leftPos + 6, topPos + BOOK_HEIGHT - FOOTER_HEIGHT + 4)
            .size(20, 16)
            .build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> turnPage(1))
            .pos(leftPos + BOOK_WIDTH - 26, topPos + BOOK_HEIGHT - FOOTER_HEIGHT + 4)
            .size(20, 16)
            .build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
            .pos(leftPos + (BOOK_WIDTH / 2) - 40, topPos + BOOK_HEIGHT - FOOTER_HEIGHT + 4)
            .size(80, 16)
            .build());

        updateButtons();
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + BOOK_WIDTH, topPos + BOOK_HEIGHT, DesignTokens.Foundry.Guide.BOOK_BORDER);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + BOOK_WIDTH - 2, topPos + BOOK_HEIGHT - 2, DesignTokens.Foundry.Guide.BOOK_INTERIOR);

        GuidePage page = PAGES.get(pageIndex);
        graphics.drawString(font, page.title(), leftPos + PADDING, topPos + 8, DesignTokens.Text.TITLE(), false);

        int textWidth = BOOK_WIDTH - (PADDING * 2);
        int textY = topPos + 26;
        int maxY = topPos + BOOK_HEIGHT - FOOTER_HEIGHT - 4;
        if (page.dynamic()) {
            renderProgressPage(graphics, leftPos + PADDING, textY, maxY);
        } else {
            for (var line : font.split(page.body(), textWidth)) {
                if (textY > maxY) {
                    break;
                }
                graphics.drawString(font, line, leftPos + PADDING, textY, DesignTokens.Text.PRIMARY(), false);
                textY += font.lineHeight + 2;
            }
        }

        String pageLabel = (pageIndex + 1) + "/" + PAGES.size();
        graphics.drawString(
            font,
            pageLabel,
            leftPos + BOOK_WIDTH - PADDING - font.width(pageLabel),
            topPos + BOOK_HEIGHT - FOOTER_HEIGHT - 2,
            DesignTokens.Text.MUTED(),
            false
        );

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void turnPage(int delta) {
        pageIndex = Mth.clamp(pageIndex + delta, 0, PAGES.size() - 1);
        updateButtons();
    }

    private void updateButtons() {
        if (prevButton != null) {
            prevButton.active = pageIndex > 0;
        }
        if (nextButton != null) {
            nextButton.active = pageIndex < PAGES.size() - 1;
        }
    }

    private void renderProgressPage(GuiGraphics graphics, int x, int y, int maxY) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            graphics.drawString(font, Component.translatable("gui.devmod.foundry_progress.specialization.none"),
                x, y, DesignTokens.Text.MUTED(), false);
            return;
        }

        FoundryPlayerProgress progress = FoundryProgressAttachment.get(player);
        FoundryTier tier = progress.getTier();
        ResourceLocation specId = progress.getSpecialization();
        FoundrySpecialization spec = FoundrySpecialization.fromId(specId);
        Component specName = spec == null
            ? Component.translatable("gui.devmod.foundry_progress.specialization.none")
            : spec.getDisplayName();

        int totalMaterials = FoundryMaterialRegistry.all().size();
        int unlockedMaterials = progress.getUnlockedMaterials().size();
        int totalModifiers = (int) FoundryModifierRegistry.all().stream()
            .filter(def -> def.slotType() != FoundryModifierSlot.TRAIT)
            .count();
        int unlockedModifiers = (int) progress.getUnlockedModifiers().stream()
            .map(FoundryModifierRegistry::get)
            .filter(def -> def != null && def.slotType() != FoundryModifierSlot.TRAIT)
            .count();

        List<ProgressLine> lines = new ArrayList<>();
        lines.add(new ProgressLine(Component.translatable("gui.devmod.foundry_progress.tier", tier.getDisplayName()),
            DesignTokens.Text.PRIMARY()));
        lines.add(new ProgressLine(Component.translatable("gui.devmod.foundry_progress.specialization", specName),
            DesignTokens.Text.PRIMARY()));
        lines.add(new ProgressLine(Component.translatable("gui.devmod.foundry_progress.materials", unlockedMaterials, totalMaterials),
            DesignTokens.Text.PRIMARY()));
        lines.add(new ProgressLine(Component.translatable("gui.devmod.foundry_progress.modifiers", unlockedModifiers, totalModifiers),
            DesignTokens.Text.PRIMARY()));

        switch (tier) {
            case PRIMITIVE -> {
                int current = progress.getTotalMetalMelted();
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.metal", current, 1000),
                    current >= 1000 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
            }
            case BASIC -> {
                int tools = progress.getTotalToolsCrafted();
                int alloys = progress.getTotalAlloysCreated();
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.tools", tools, 3),
                    tools >= 3 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.alloys", alloys, 1),
                    alloys >= 1 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
            }
            case IRON_AGE -> {
                int tools = progress.getTotalToolsCrafted();
                int mastery = progress.countMasteredMaterials(10);
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.tools", tools, 10),
                    tools >= 10 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.mastery", mastery, 3),
                    mastery >= 3 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
            }
            case ADVANCED -> {
                int alloys = progress.getTotalAlloysCreated();
                int incidents = progress.getTotalIncidentsSurvived();
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.alloys", alloys, 10),
                    alloys >= 10 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.incidents", incidents, 5),
                    incidents >= 5 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
            }
            case NETHER -> {
                int tools = progress.getTotalToolsCrafted();
                lines.add(new ProgressLine(
                    Component.translatable("gui.devmod.foundry_progress.tools", tools, 50),
                    tools >= 50 ? DesignTokens.Semantic.SUCCESS : DesignTokens.Text.MUTED()));
            }
            case COSMIC -> {
                // Max tier, no requirements to show.
            }
        }

        int lineY = y;
        for (ProgressLine line : lines) {
            if (lineY > maxY) {
                break;
            }
            graphics.drawString(font, line.text, x, lineY, line.color, false);
            lineY += font.lineHeight + 2;
        }
    }

    private record ProgressLine(Component text, int color) {}

    private record GuidePage(String titleKey, String bodyKey, boolean dynamic) {
        @Nonnull
        public Component title() {
            return Component.translatable(titleKey);
        }

        @Nonnull
        public Component body() {
            return Component.translatable(bodyKey);
        }
    }
}
