package com.devmod.mailbox.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.devmod.client.ui.AxiomRenderer;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.compat.Compat;
import com.devmod.mailbox.MessageType;

public final class MailboxUiSkin {
    private static final String FEATHERED_MOD_ID = "featheredfriend";

    private static final ResourceLocation GOTHIC_FONT_ID =
        ResourceLocation.fromNamespaceAndPath(FEATHERED_MOD_ID, "gothic12");

    private static final ResourceLocation SCROLL_OPENING_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(FEATHERED_MOD_ID, "textures/gui/scrollscreen/scroll_opening.png");

    private static final ResourceLocation PEARL_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(FEATHERED_MOD_ID, "textures/gui/scrollscreen/pearl.png");

    private static final int SCROLL_FRAME_WIDTH = 240;
    private static final int SCROLL_FRAME_HEIGHT = 208;
    private static final int SCROLL_TOTAL_FRAMES = 7;

    private static final int PEARL_FRAME_WIDTH = 32;
    private static final int PEARL_FRAME_HEIGHT = 32;
    private static final int PEARL_TOTAL_FRAMES = 11;
    private static final int PEARL_STATIC_FRAME = 6;

    private static final int PAPER_BG = DesignTokens.Mailbox.Feathered.PAPER_BG;
    private static final int PAPER_INSET = DesignTokens.Mailbox.Feathered.PAPER_INSET;
    private static final int PAPER_DETAIL = DesignTokens.Mailbox.Feathered.PAPER_DETAIL;
    private static final int PAPER_BORDER = DesignTokens.Mailbox.Feathered.PAPER_BORDER;
    private static final int PAPER_HOVER = DesignTokens.Mailbox.Feathered.PAPER_HOVER;
    private static final int PAPER_SELECTED = DesignTokens.Mailbox.Feathered.PAPER_SELECTED;

    private static final int TEXT_PRIMARY = DesignTokens.Mailbox.Feathered.TEXT_PRIMARY;
    private static final int TEXT_SECONDARY = DesignTokens.Mailbox.Feathered.TEXT_SECONDARY;
    private static final int TEXT_MUTED = DesignTokens.Mailbox.Feathered.TEXT_MUTED;
    private static final int TEXT_ACCENT = DesignTokens.Mailbox.Feathered.TEXT_ACCENT;

    private static final int TYPE_PLAYER = DesignTokens.Mailbox.Feathered.TYPE_PLAYER;
    private static final int TYPE_SYSTEM = DesignTokens.Mailbox.Feathered.TYPE_SYSTEM;
    private static final int TYPE_ADMIN = DesignTokens.Mailbox.Feathered.TYPE_ADMIN;
    private static final int TYPE_REWARD = DesignTokens.Mailbox.Feathered.TYPE_REWARD;

    @Nullable
    private static Item sealedScrollItem;
    @Nullable
    private static Item openedScrollItem;
    private static boolean itemsResolved = false;

    private MailboxUiSkin() {}

    public static boolean isFeatheredTheme() {
        return Compat.isLoaded(FEATHERED_MOD_ID);
    }

    public static int panelBg() {
        return isFeatheredTheme() ? PAPER_BG : MailboxUiTheme.Panel.BG;
    }

    public static int listBg() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(PAPER_INSET, 0xE0)
            : DesignTokens.setAlpha(DesignTokens.Background.DARKER(), 0x80);
    }

    public static int detailBg() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(PAPER_DETAIL, 0xE5)
            : DesignTokens.setAlpha(DesignTokens.Background.CONTENT(), 0x70);
    }

    public static int border() {
        return isFeatheredTheme() ? PAPER_BORDER : DesignTokens.Border.DEFAULT();
    }

    public static int divider() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(TEXT_MUTED, DesignTokens.Alpha.A33)
            : MailboxUiTheme.Divider.LINE;
    }

    public static int listHover() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(PAPER_HOVER, 0xA0)
            : MailboxUiTheme.List.HOVER_BG;
    }

    public static int listSelected() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(PAPER_SELECTED, 0xC0)
            : MailboxUiTheme.List.SELECTED_BG;
    }

    public static int scrollbarTrack() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(PAPER_BORDER, 0x90)
            : MailboxUiTheme.Scrollbar.TRACK;
    }

    public static int scrollbarThumb() {
        return isFeatheredTheme()
            ? DesignTokens.withAlpha(TEXT_MUTED, 0xAA)
            : MailboxUiTheme.Scrollbar.THUMB;
    }

    public static int textPrimary() {
        return isFeatheredTheme() ? TEXT_PRIMARY : DesignTokens.Text.PRIMARY();
    }

    public static int textSecondary() {
        return isFeatheredTheme() ? TEXT_SECONDARY : DesignTokens.Text.SECONDARY();
    }

    public static int textMuted() {
        return isFeatheredTheme() ? TEXT_MUTED : DesignTokens.Text.MUTED();
    }

    public static int textAccent() {
        return isFeatheredTheme() ? TEXT_ACCENT : DesignTokens.Text.ACCENT();
    }

    public static int unreadAccent() {
        return isFeatheredTheme() ? TEXT_ACCENT : DesignTokens.Accent.BLUE();
    }

    public static int messageTypeColor(int typeOrdinal) {
        MessageType[] values = MessageType.values();
        int safeIndex = Math.max(0, Math.min(typeOrdinal, values.length - 1));
        MessageType type = values[safeIndex];
        if (!isFeatheredTheme()) {
            return switch (type) {
                case PLAYER -> DesignTokens.Accent.BLUE();
                case SYSTEM -> DesignTokens.Text.SECONDARY();
                case ADMIN -> DesignTokens.Accent.GOLD();
                case REWARD -> DesignTokens.Status.SUCCESS();
            };
        }
        return switch (type) {
            case PLAYER -> TYPE_PLAYER;
            case SYSTEM -> TYPE_SYSTEM;
            case ADMIN -> TYPE_ADMIN;
            case REWARD -> TYPE_REWARD;
        };
    }

    public static void drawText(GuiGraphics graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        if (!isFeatheredTheme()) {
            graphics.drawString(font, text, x, y, color, shadow);
            return;
        }
        graphics.drawString(font, stylize(text), x, y, color, shadow);
    }

    public static Component styledComponent(String text) {
        return stylize(text);
    }

    public static int textWidth(Font font, String text) {
        if (!isFeatheredTheme()) {
            return font.width(text);
        }
        return font.width(stylize(text));
    }

    public static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(font, text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = textWidth(font, ellipsis);
        int allowed = Math.max(0, maxWidth - ellipsisWidth);
        if (allowed <= 0) {
            return sliceToWidth(font, text, maxWidth);
        }
        return sliceToWidth(font, text, allowed) + ellipsis;
    }

    public static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return lines;
        }

        String[] paragraphs = text.split("\\r?\\n", -1);
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            List<String> words = splitOnSpace(paragraph);
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                if (current.isEmpty()) {
                    current.append(word);
                    continue;
                }
                String candidate = current + " " + word;
                if (textWidth(font, candidate) <= maxWidth) {
                    current.append(" ").append(word);
                } else {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }

        return lines;
    }

    private static List<String> splitOnSpace(String text) {
        List<String> words = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                words.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            words.add(text.substring(start));
        }
        return words;
    }

    public static void renderScrollPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        if (!isFeatheredTheme()) {
            return;
        }
        int frameIndex = SCROLL_TOTAL_FRAMES - 1;
        int u = frameIndex * SCROLL_FRAME_WIDTH;
        int textureWidth = SCROLL_FRAME_WIDTH * SCROLL_TOTAL_FRAMES;
        int textureHeight = SCROLL_FRAME_HEIGHT;

        graphics.blit(
            SCROLL_OPENING_TEXTURE,
            x,
            y,
            (float) u,
            0.0f,
            width,
            height,
            textureWidth,
            textureHeight
        );
    }

    public static void renderPearlIcon(GuiGraphics graphics, int x, int y, int size) {
        if (!isFeatheredTheme()) {
            return;
        }
        int frameIndex = Math.min(PEARL_STATIC_FRAME, PEARL_TOTAL_FRAMES - 1);
        int u = 0;
        int v = frameIndex * PEARL_FRAME_HEIGHT;
        int textureWidth = PEARL_FRAME_WIDTH;
        int textureHeight = PEARL_FRAME_HEIGHT * PEARL_TOTAL_FRAMES;

        graphics.blit(
            PEARL_TEXTURE,
            x,
            y,
            (float) u,
            (float) v,
            size,
            size,
            textureWidth,
            textureHeight
        );
    }

    public static void drawInputBackground(GuiGraphics graphics, int x, int y, int width, int height, boolean focused) {
        if (!isFeatheredTheme()) {
            AxiomRenderer.drawInputBackground(graphics, x, y, width, height, focused);
            return;
        }
        int borderColor = focused ? TEXT_ACCENT : PAPER_BORDER;
        int bgColor = DesignTokens.withAlpha(PAPER_DETAIL, 0xF0);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        graphics.fill(x, y, x + width, y + height, bgColor);
    }

    @Nullable
    public static ItemStack scrollIcon(boolean sealed) {
        if (!isFeatheredTheme()) {
            return null;
        }
        resolveItems();
        Item item = sealed ? sealedScrollItem : openedScrollItem;
        if (item == null) {
            return null;
        }
        return new ItemStack(item);
    }

    private static void resolveItems() {
        if (itemsResolved) {
            return;
        }
        itemsResolved = true;
        sealedScrollItem = BuiltInRegistries.ITEM.getOptional(
            ResourceLocation.fromNamespaceAndPath(FEATHERED_MOD_ID, "scroll_sealed")).orElse(null);
        openedScrollItem = BuiltInRegistries.ITEM.getOptional(
            ResourceLocation.fromNamespaceAndPath(FEATHERED_MOD_ID, "scroll_opened")).orElse(null);
    }

    private static Component stylize(String text) {
        MutableComponent base = Component.literal(Objects.requireNonNull(text, "text"));
        if (!isFeatheredTheme()) {
            return base;
        }
        return base.withStyle(style -> style.withFont(GOTHIC_FONT_ID));
    }

    private static String sliceToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        String trimmed = text;
        while (!trimmed.isEmpty() && textWidth(font, trimmed) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
