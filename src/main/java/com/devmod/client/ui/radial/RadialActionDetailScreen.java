package com.devmod.client.ui.radial;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionOrigin;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.client.ActionKeybindRegistry;
import com.devmod.actions.client.ClientActionContexts;
@OnlyIn(Dist.CLIENT)
public class RadialActionDetailScreen extends Screen {
    private static final int PADDING = 14;
    private static final int BUTTON_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final Screen parent;
    private final RadialMenuItem item;
    @Nullable
    private final String actionId;
    @Nullable
    private final com.devmod.actions.RadialAction registryAction;
    @Nullable
    private final ActionKeybindRegistry.KeybindHint keybindHint;
    private List<FormattedCharSequence> descriptionLines = List.of();

    public RadialActionDetailScreen(Screen parent, RadialMenuItem item) {
        super(java.util.Objects.requireNonNull(Component.translatable("devmod.radial.details.title"), "title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.item = Objects.requireNonNull(item, "item");
        this.actionId = item.getAction().getRegistryId();
        this.registryAction = actionId != null ? ActionRegistry.getAction(actionId) : null;
        this.keybindHint = actionId != null ? ActionKeybindRegistry.getKeybindHint(actionId) : null;
    }

    @Override
    protected void init() {
        Font font = requireFont();
        int maxTextWidth = width - (PADDING * 2);
        descriptionLines = buildDescriptionLines(font, maxTextWidth);

        List<ButtonSpec> buttons = new ArrayList<>();
        if (registryAction != null && registryAction.requiresConfirm()) {
            buttons.add(new ButtonSpec(Component.translatable("devmod.radial.details.confirm_execute"),
                () -> executeAction(true)));
        } else {
            buttons.add(new ButtonSpec(Component.translatable("devmod.radial.details.execute"),
                () -> executeAction(false)));
        }

        if (keybindHint != null) {
            buttons.add(new ButtonSpec(Component.translatable("devmod.radial.details.keybinds"),
                this::openKeybinds));
        }

        buttons.add(new ButtonSpec(CommonComponents.GUI_BACK, this::returnToParent));

        int totalWidth = buttons.size() * BUTTON_WIDTH + (buttons.size() - 1) * BUTTON_GAP;
        int startX = (width - totalWidth) / 2;
        int y = height - PADDING - BUTTON_HEIGHT;

        for (int i = 0; i < buttons.size(); i++) {
            ButtonSpec spec = buttons.get(i);
            int x = startX + i * (BUTTON_WIDTH + BUTTON_GAP);
            Component label = Objects.requireNonNull(spec.label, "label");
            Button rendered = Objects.requireNonNull(Button.builder(label, button -> spec.onPress.run())
                .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build(), "button");
            addRenderableWidget(rendered);
        }
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        GuiGraphics safeGraphics = Objects.requireNonNull(graphics, "graphics");
        renderBackground(safeGraphics, mouseX, mouseY, partialTicks);

        Font font = requireFont();
        String itemName = Objects.requireNonNullElse(item.getName(), "");
        Component fallbackTitle = Component.literal(Objects.requireNonNull(itemName, "itemName"));
        Component title = registryAction != null
            ? Objects.requireNonNullElse(registryAction.getLabel(), fallbackTitle)
            : fallbackTitle;
        Component safeTitle = Objects.requireNonNull(title, "title");
        safeGraphics.drawCenteredString(font, safeTitle, width / 2, PADDING, 0xFFFFFF);

        ItemStack icon = item.getIconStack();
        int iconY = PADDING + 16;
        int textStartY = iconY;
        if (icon != null && !icon.isEmpty()) {
            int iconX = (width / 2) - 8;
            safeGraphics.renderItem(icon, iconX, iconY);
            textStartY += 22;
        }

        int y = textStartY;
        for (FormattedCharSequence line : descriptionLines) {
            FormattedCharSequence safeLine = Objects.requireNonNull(line, "description line");
            safeGraphics.drawString(font, safeLine, PADDING, y, 0xFFFFFF, false);
            y += font.lineHeight + 2;
        }

        super.render(safeGraphics, mouseX, mouseY, partialTicks);
    }

    private List<FormattedCharSequence> buildDescriptionLines(Font font, int maxWidth) {
        @Nonnull Font safeFont = Objects.requireNonNull(font, "font");
        String description = item.getDescription();
        if (description == null || description.isBlank()) {
            return List.of();
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String rawLine : description.split("\\n")) {
            String safeLine = Objects.requireNonNull(rawLine, "description line");
            Component lineComponent = Component.literal(safeLine);
            lines.addAll(safeFont.split(Objects.requireNonNull(lineComponent, "lineComponent"), maxWidth));
        }
        if (actionId != null) {
            lines.add(FormattedCharSequence.EMPTY);
            lines.add(Component.literal("Id: " + actionId).getVisualOrderText());
        }
        return lines;
    }

    private void executeAction(boolean confirmed) {
        if (actionId != null && registryAction != null) {
            ActionContext context = ClientActionContexts.forClient(ActionOrigin.UI);
            if (confirmed) {
                context = context.withConfirmed(true);
            }
            ActionRegistry.invoke(actionId, context);
            if (Minecraft.getInstance().screen == this) {
                returnToParent();
            }
            return;
        }

        item.execute();
        if (Minecraft.getInstance().screen == this) {
            returnToParent();
        }
    }

    private void openKeybinds() {
        ActionRegistry.invoke(ActionIds.UI_KEYBINDS_OPEN, ClientActionContexts.forClient(ActionOrigin.UI));
    }

    private void returnToParent() {
        Minecraft.getInstance().setScreen(parent);
    }

    private @Nonnull Font requireFont() {
        return Objects.requireNonNull(font, "font");
    }

    private static final class ButtonSpec {
        private final @Nonnull Component label;
        private final @Nonnull Runnable onPress;

        private ButtonSpec(Component label, Runnable onPress) {
            this.label = Objects.requireNonNull(label, "label");
            this.onPress = Objects.requireNonNull(onPress, "onPress");
        }
    }
}
