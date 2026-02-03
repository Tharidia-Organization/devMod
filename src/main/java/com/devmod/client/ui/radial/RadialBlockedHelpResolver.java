package com.devmod.client.ui.radial;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import com.devmod.client.panels.context.ContextDetector;
import com.devmod.util.I18n;

public final class RadialBlockedHelpResolver {
    private RadialBlockedHelpResolver() {
        // Utility class
    }

    public static final class BlockedHelp {
        @Nonnull
        private final String key;
        @Nonnull
        private final Object[] args;

        public BlockedHelp(@Nonnull String key, @Nonnull Object[] args) {
            this.key = Objects.requireNonNull(key, "key");
            this.args = Objects.requireNonNull(args, "args").clone();
        }

        @Nonnull
        public String key() {
            return key;
        }

        @Nonnull
        public Object[] args() {
            return args.clone();
        }
    }

    @Nullable
    public static BlockedHelp resolve(@Nullable Component reasonComponent, @Nullable String reasonText) {
        String reasonKey = resolveReasonKey(reasonComponent);

        if (reasonKey != null) {
            if ("devmod.action.requires_not_in_combat".equals(reasonKey)) {
                if (ContextDetector.INSTANCE.isCurrentlyCombat()) {
                    long remainingMs = ContextDetector.INSTANCE.getRemainingCombatTime();
                    if (remainingMs > 0) {
                        int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                        return new BlockedHelp("devmod.action.blocked.combat", new Object[]{remainingSec});
                    }
                }
                return null;
            }
            switch (reasonKey) {
                case "devmod.action.screen_open" ->
                    { return new BlockedHelp("devmod.action.blocked.screen", new Object[0]); }
                case "devmod.action.requires_player",
                    "devmod.action.requires_server_player" ->
                    { return new BlockedHelp("devmod.action.blocked.world", new Object[0]); }
                case "devmod.action.requires_developer_mode" ->
                    { return new BlockedHelp("devmod.action.blocked.developer_mode", new Object[0]); }
                case "devmod.action.requires_tester" ->
                    { return new BlockedHelp("devmod.action.blocked.tester", new Object[0]); }
                case "devmod.action.testing.session_inactive",
                    "devmod.action.testing.session_missing" ->
                    { return new BlockedHelp("devmod.action.blocked.qa_session", new Object[0]); }
                case "devmod.action.requires_permission" ->
                    { return new BlockedHelp("devmod.action.blocked.permission", new Object[0]); }
                case "devmod.action.requires_active_task" ->
                    { return new BlockedHelp("devmod.action.blocked.active_task", new Object[0]); }
                case "devmod.action.requires_active_quest" ->
                    { return new BlockedHelp("devmod.action.blocked.active_quest", new Object[0]); }
                case "devmod.action.requires_respawn" ->
                    { return new BlockedHelp("devmod.action.blocked.respawn", new Object[0]); }
                case "devmod.action.requires_respawn_or_checkpoint" ->
                    { return new BlockedHelp("devmod.action.blocked.respawn_or_checkpoint", new Object[0]); }
                case "devmod.action.client_only" ->
                    { return new BlockedHelp("devmod.action.blocked.client_only", new Object[0]); }
                case "devmod.action.server_only" ->
                    { return new BlockedHelp("devmod.action.blocked.server_only", new Object[0]); }
                case "devmod.action.onboarding.inactive" ->
                    { return new BlockedHelp("devmod.action.blocked.onboarding", new Object[0]); }
                case "devmod.action.requires_confirm" ->
                    { return new BlockedHelp("devmod.action.blocked.confirm_required", new Object[0]); }
                case "devmod.action.party_invite.missing" ->
                    { return new BlockedHelp("devmod.action.blocked.party_invite", new Object[0]); }
                case "devmod.action.perk_selection.missing" ->
                    { return new BlockedHelp("devmod.action.blocked.perk_selection", new Object[0]); }
                case "devmod.action.quest_completion.missing" ->
                    { return new BlockedHelp("devmod.action.blocked.quest_completion", new Object[0]); }
                case "devmod.action.testing.requires_active_test" ->
                    { return new BlockedHelp("devmod.action.blocked.active_test", new Object[0]); }
                case "devmod.action.testing.requires_auto_test" ->
                    { return new BlockedHelp("devmod.action.blocked.auto_test", new Object[0]); }
                default -> {
                    // fall through
                }
            }
            if (reasonKey.endsWith(".missing")) {
                return new BlockedHelp("devmod.action.blocked.payload_missing", new Object[0]);
            }
        }

        if (reasonText == null || reasonText.isBlank()) {
            return new BlockedHelp("devmod.action.blocked.generic", new Object[0]);
        }

        // Fallback matching by translated text (for non-translatable reasons)
        if (reasonText.equals(I18n.translate("devmod.action.requires_not_in_combat").getString())) {
            long remainingMs = ContextDetector.INSTANCE.getRemainingCombatTime();
            if (remainingMs > 0) {
                int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                return new BlockedHelp("devmod.action.blocked.combat", new Object[]{remainingSec});
            }
            return new BlockedHelp("devmod.action.blocked.combat", new Object[]{0});
        }
        if (reasonText.equals(I18n.translate("devmod.action.screen_open").getString())) {
            return new BlockedHelp("devmod.action.blocked.screen", new Object[0]);
        }

        if (reasonText.equals(I18n.translate("devmod.action.requires_player").getString())
            || reasonText.equals(I18n.translate("devmod.action.requires_server_player").getString())) {
            return new BlockedHelp("devmod.action.blocked.world", new Object[0]);
        }

        if (matchesPermissionReason(reasonText)) {
            return new BlockedHelp("devmod.action.blocked.permission", new Object[0]);
        }

        return null;
    }

    @Nullable
    private static String resolveReasonKey(@Nullable Component reasonComponent) {
        if (reasonComponent == null) {
            return null;
        }
        if (reasonComponent.getContents() instanceof TranslatableContents translatable) {
            return translatable.getKey();
        }
        return null;
    }

    private static boolean matchesPermissionReason(@Nonnull String reason) {
        String sample = I18n.translate("devmod.action.requires_permission", 0).getString();
        return stripDigits(reason).equals(stripDigits(sample));
    }

    private static String stripDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString().trim();
    }
}
