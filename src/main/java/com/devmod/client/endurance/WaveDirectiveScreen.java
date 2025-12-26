package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import com.devmod.client.ui.components.CountdownTimer;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.UIConstants;
import com.devmod.endurance.WaveDirectiveChoicesPayload;
import com.devmod.endurance.WaveDirectiveSelectionPayload;
import com.devmod.util.I18n;
@OnlyIn(Dist.CLIENT)
public class WaveDirectiveScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int CARD_HEIGHT = 64;
    private static final int CARD_GAP = 10;
    private static final int PANEL_PADDING = 16;

    private static final int COUNTDOWN_WARN_SECONDS = 10;
    private static final int COUNTDOWN_URGENT_SECONDS = 5;
    private static final int COUNTDOWN_AUDIO_SECONDS = 5;
    private static final int COUNTDOWN_PADDING = 10;

    private final WaveDirectiveChoicesPayload payload;
    private final List<EditorButton> selectButtons = new ArrayList<>();
    private boolean selectionSent = false;
    private final CountdownTimer countdown;

    public WaveDirectiveScreen() {
        this(EnduranceUiCache.getLastDirectiveChoices());
    }

    public WaveDirectiveScreen(WaveDirectiveChoicesPayload payload) {
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
        if (payload == null || payload.choices() == null || payload.choices().isEmpty()) {
            return;
        }
        for (int i = 0; i < payload.choices().size(); i++) {
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
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (payload == null || payload.choices() == null || payload.choices().isEmpty()) {
            return;
        }

        Font font = Objects.requireNonNull(Objects.requireNonNull(minecraft, "minecraft").font, "font");
        int choicesCount = payload.choices().size();
        int panelHeight = PANEL_PADDING * 2 + 24 + choicesCount * CARD_HEIGHT + (choicesCount - 1) * CARD_GAP;
        int panelX = (width - PANEL_WIDTH) / 2;
        int panelY = (height - panelHeight) / 2;

        int bgTop = UIConstants.Background.PANEL();
        int bgBottom = UIConstants.Background.INPUT();
        graphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + panelHeight + 2, UIConstants.Border.DEFAULT());
        for (int i = 0; i < panelHeight; i++) {
            float t = (float) i / panelHeight;
            int lineColor = lerpColor(bgTop, bgBottom, t);
            graphics.fill(panelX, panelY + i, panelX + PANEL_WIDTH, panelY + i + 1, lineColor);
        }

        graphics.drawCenteredString(font, "Choose Your Directive", width / 2, panelY + 6,
            UIConstants.Text.PRIMARY());
        renderCountdown(graphics, font, panelX, panelY, PANEL_WIDTH);

        int cardWidth = PANEL_WIDTH - PANEL_PADDING * 2;
        int cardX = panelX + PANEL_PADDING;
        int cardY = panelY + PANEL_PADDING + 18;

        for (int i = 0; i < payload.choices().size(); i++) {
            WaveDirectiveChoicesPayload.DirectiveChoice choice = payload.choices().get(i);
            int y = cardY + i * (CARD_HEIGHT + CARD_GAP);
            int borderColor = UIConstants.Border.SEPARATOR();
            int cardColor = UIConstants.Background.PANEL();
            graphics.fill(cardX - 1, y - 1, cardX + cardWidth + 1, y + CARD_HEIGHT + 1, borderColor);
            graphics.fill(cardX, y, cardX + cardWidth, y + CARD_HEIGHT, cardColor);

            String name = choice.name() != null ? choice.name() : "Directive";
            graphics.drawString(Objects.requireNonNull(font, "font"), name, cardX + 8, y + 6,
                UIConstants.Text.PRIMARY(), false);

            String desc = choice.description() != null ? choice.description() : "";
            List<String> lines = wrapText(font, desc, cardWidth - 120);
            for (int lineIndex = 0; lineIndex < Math.min(2, lines.size()); lineIndex++) {
                graphics.drawString(Objects.requireNonNull(font, "font"),
                    Objects.requireNonNull(lines.get(lineIndex), "lineText"),
                    cardX + 8, y + 20 + lineIndex * 10, UIConstants.Text.SECONDARY(), false);
            }

            String rewardText = "Reward x" + String.format("%.2f", choice.rewardMultiplier());
            graphics.drawString(Objects.requireNonNull(font, "font"), rewardText,
                cardX + 8, y + CARD_HEIGHT - 16,
                UIConstants.Accent.GOLD(), false);

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
        if (!selectionSent && payload != null) {
            PacketDistributor.sendToServer(new WaveDirectiveSelectionPayload("", payload.waveNumber()));
        }
        EnduranceUiCache.setLastDirectiveChoices(null);
        super.onClose();
    }

    private void selectDirective(int index) {
        if (payload == null || index < 0 || index >= payload.choices().size()) {
            return;
        }
        WaveDirectiveChoicesPayload.DirectiveChoice choice = payload.choices().get(index);
        selectionSent = true;
        PacketDistributor.sendToServer(new WaveDirectiveSelectionPayload(choice.id(), payload.waveNumber()));
        EnduranceUiCache.setLastDirectiveChoices(null);
        onClose();
    }

    private List<String> wrapText(Font font, String text, int maxWidth) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }
            String candidate = current + " " + word;
            if (font.width(candidate) > maxWidth) {
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
        String text = I18n.ui("selection_time_remaining", remaining).getString();
        int textWidth = font.width(text);
        int x = panelX + panelWidth - textWidth - COUNTDOWN_PADDING;
        int y = panelY + 6;

        int bgColor = UIConstants.setAlpha(UIConstants.Background.INPUT(), 160);
        graphics.fill(x - 4, y - 2, x + textWidth + 4, y + font.lineHeight + 2, bgColor);

        float pulse = countdown.getPulse();
        int textColor = UIConstants.setAlpha(countdown.getColor(), (int) (255 * pulse));
        graphics.drawString(font, text, x, y, textColor, false);
    }
}
