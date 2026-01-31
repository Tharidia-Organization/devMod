package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Splitter;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.components.CountdownTimer;
import com.devmod.client.ui.core.UIScaleManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.WaveDirectiveSelectionPayload;
import com.devmod.util.I18n;

@OnlyIn(Dist.CLIENT)
public class WaveDirectiveScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int CARD_HEIGHT = 64;
    private static final int CARD_GAP = 10;
    private static final int PANEL_PADDING = 16;
    private static final Splitter SPACE_SPLITTER = Splitter.on(' ');

    private static final int COUNTDOWN_WARN_SECONDS = 10;
    private static final int COUNTDOWN_URGENT_SECONDS = 5;
    private static final int COUNTDOWN_AUDIO_SECONDS = 5;
    private static final int COUNTDOWN_PADDING = 10;

    @Nullable
    private final WaveDirectiveChoicesPayload payload;
    private final List<EditorButton> selectButtons = new ArrayList<>();
    private boolean selectionSent = false;
    private final CountdownTimer countdown;

    public WaveDirectiveScreen() {
        this(EnduranceUiCache.getLastDirectiveChoices());
    }

    public WaveDirectiveScreen(@Nullable WaveDirectiveChoicesPayload payload) {
        super(java.util.Objects.requireNonNull(Component.literal("Wave Directives"), "title"));
        this.payload = payload;
        long expiresAt = payload != null ? payload.expiresAt() : 0L;
        this.countdown = new CountdownTimer(expiresAt, COUNTDOWN_WARN_SECONDS, COUNTDOWN_URGENT_SECONDS, COUNTDOWN_AUDIO_SECONDS);
    }

    @Override
    public void tick() {
        super.tick();
        countdown.tick();
    }

    @Override
    protected void init() {
        super.init();
        selectButtons.clear();
        final var safePayload = payload;
        if (safePayload == null) {
            return;
        }
        final var choices = safePayload.choices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        for (int i = 0; i < choices.size(); i++) {
            int index = i;
            EditorButton btn = EditorButton.builder("directive-" + i, I18n.translate("devmod.ui.select").getString())
                .style(EditorButton.Style.PRIMARY)
                .size(EditorButton.Size.MEDIUM)
                .onClick(() -> selectDirective(index))
                .build();
            selectButtons.add(btn);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UIScaleManager.update();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        final var safePayload = payload;
        if (safePayload == null) {
            return;
        }
        final var choices = safePayload.choices();
        if (choices == null || choices.isEmpty()) {
            return;
        }

        Font font = Objects.requireNonNull(Objects.requireNonNull(minecraft, "minecraft").font, "font");
        int choicesCount = choices.size();
        int panelHeight = PANEL_PADDING * 2 + 24 + choicesCount * CARD_HEIGHT + (choicesCount - 1) * CARD_GAP;
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - panelHeight) / 2;

        int bgTop = DesignTokens.Bg.LEVEL_1;
        int bgBottom = DesignTokens.Bg.LEVEL_0;
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + panelHeight + 2, DesignTokens.Stroke.DEFAULT);
        for (int i = 0; i < panelHeight; i++) {
            float t = (float) i / panelHeight;
            int lineColor = lerpColor(bgTop, bgBottom, t);
            graphics.fill(panelX, panelY + i, panelX + PANEL_WIDTH, panelY + i + 1, lineColor);
        }

        UIScaleManager.drawScaledCenteredString(graphics, font, Objects.requireNonNull(I18n.translate("devmod.endurance.directive.choose_title").getString()),
            width / 2, panelY + 6, DesignTokens.Text.PRIMARY);
        renderCountdown(graphics, font, panelX, panelY, PANEL_WIDTH);

        int cardWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int cardX = panelX + PANEL_PADDING;
        int cardY = panelY + PANEL_PADDING + 18;

        for (int i = 0; i < choices.size(); i++) {
            WaveDirectiveChoicesPayload.DirectiveChoice choice = choices.get(i);
            int y = cardY + i * (CARD_HEIGHT + CARD_GAP);
            int borderColor = DesignTokens.Stroke.MUTED;
            int cardColor = DesignTokens.Surface.LEVEL_0;
            graphics.fill(cardX - 1, y - 1, cardX + cardWidth + 1, y + CARD_HEIGHT + 1, borderColor);
            graphics.fill(cardX, y, cardX + cardWidth, y + CARD_HEIGHT, cardColor);

            var safeFont = Objects.requireNonNull(font, "font");
            String name = choice.name() != null ? choice.name()
                : Objects.requireNonNull(I18n.translate("devmod.endurance.directive.fallback_name").getString());
            UIScaleManager.drawScaledString(graphics, safeFont, name, cardX + 8, y + 6,
                DesignTokens.Text.PRIMARY, false);

            String desc = choice.description() != null ? choice.description() : "";
            List<String> lines = wrapText(font, desc, cardWidth - 120);
            int lineHeight = UIScaleManager.getScaledLineHeight();
            for (int lineIndex = 0; lineIndex < Math.min(2, lines.size()); lineIndex++) {
                UIScaleManager.drawScaledString(graphics, safeFont,
                    Objects.requireNonNull(lines.get(lineIndex), "lineText"),
                    cardX + 8, y + 20 + lineIndex * lineHeight, DesignTokens.Text.SECONDARY, false);
            }

            String rewardText = Objects.requireNonNull(I18n.translate(
                "devmod.endurance.directive.reward_multiplier",
                String.format("%.2f", choice.rewardMultiplier())
            ).getString());
            UIScaleManager.drawScaledString(graphics, safeFont, rewardText,
                cardX + 8, y + CARD_HEIGHT - 16,
                DesignTokens.Semantic.WARNING, false);

            EditorButton btn = selectButtons.size() > i ? selectButtons.get(i) : null;
            if (btn != null) {
                int btnWidth = 80;
                int btnHeight = 20;
                btn.render(graphics, cardX + cardWidth - btnWidth - 8, y + CARD_HEIGHT / 2 - btnHeight / 2,
                    btnWidth, btnHeight, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (EditorButton btn : selectButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (EditorButton btn : selectButtons) {
            if (btn.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        final var safePayload = payload;
        if (!selectionSent && safePayload != null) {
            PacketDistributor.sendToServer(new WaveDirectiveSelectionPayload("", safePayload.waveNumber()));
        }
        EnduranceUiCache.setLastDirectiveChoices(null);
        super.onClose();
    }

    private void selectDirective(int index) {
        final var safePayload = payload;
        if (safePayload == null) {
            return;
        }
        final var choices = safePayload.choices();
        if (choices == null || index < 0 || index >= choices.size()) {
            return;
        }
        WaveDirectiveChoicesPayload.DirectiveChoice choice = choices.get(index);
        selectionSent = true;
        PacketDistributor.sendToServer(new WaveDirectiveSelectionPayload(choice.id(), safePayload.waveNumber()));
        EnduranceUiCache.setLastDirectiveChoices(null);
        onClose();
    }

    private List<String> wrapText(Font font, String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : SPACE_SPLITTER.split(text)) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            String candidate = current + " " + word;
            if (UIScaleManager.getScaledStringWidth(font, candidate) > maxWidth) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current.append(" ").append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private int lerpColor(int color1, int color2, float t) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderCountdown(GuiGraphics graphics, Font font, int panelX, int panelY, int panelWidth) {
        if (!countdown.isActive()) {
            return;
        }
        int remaining = countdown.getRemainingSeconds();
        if (remaining < 0) {
            return;
        }
        String text = Objects.requireNonNull(I18n.ui("selection_time_remaining", remaining).getString());
        int textWidth = UIScaleManager.getScaledStringWidth(font, text);
        int x = panelX + panelWidth - textWidth - COUNTDOWN_PADDING;
        int y = panelY + 6;

        int bgColor = DesignTokens.withAlpha(DesignTokens.Bg.LEVEL_0, 160);
        graphics.fill(x - 4, y - 2, x + textWidth + 4, y + font.lineHeight + 2, bgColor);

        float pulse = countdown.getPulse();
        int textColor = DesignTokens.withAlpha(countdown.getColor(), (int) (255 * pulse));
        UIScaleManager.drawScaledString(graphics, font, text, x, y, textColor, false);
    }
}
