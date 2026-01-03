package com.devmod.combat;

import com.devmod.shared.SharedColorTokens;

/**
 * Shared color palette for combat systems (server-safe).
 */
public final class CombatColors {

    private CombatColors() {}

    public static final class Text {
        public static final int PRIMARY = SharedColorTokens.Combat.Text.PRIMARY;
        public static final int MUTED = SharedColorTokens.Combat.Text.MUTED;
        public static final int WARNING = SharedColorTokens.Combat.Text.WARNING;

        private Text() {}
    }

    public static final class BodyPart {
        public static final int DEFAULT = SharedColorTokens.Combat.BodyPart.DEFAULT;
        public static final int HEAD = SharedColorTokens.Combat.BodyPart.HEAD;
        public static final int BODY = SharedColorTokens.Combat.BodyPart.BODY;
        public static final int ARMS = SharedColorTokens.Combat.BodyPart.ARMS;
        public static final int LEGS = SharedColorTokens.Combat.BodyPart.LEGS;

        private BodyPart() {}
    }

    public static final class ImprintStage {
        public static final int OWNER = SharedColorTokens.Combat.ImprintStage.OWNER;
        public static final int ENHANCED = SharedColorTokens.Combat.ImprintStage.ENHANCED;
        public static final int LEGENDARY = SharedColorTokens.Combat.ImprintStage.LEGENDARY;
        public static final int ASCENDED = SharedColorTokens.Combat.ImprintStage.ASCENDED;

        private ImprintStage() {}
    }

    public static final class WeaponTrait {
        public static final int EXECUTIONER = SharedColorTokens.Combat.WeaponTrait.EXECUTIONER;
        public static final int TYRANT_SLAYER = SharedColorTokens.Combat.WeaponTrait.TYRANT_SLAYER;
        public static final int STYLISH = SharedColorTokens.Combat.WeaponTrait.STYLISH;
        public static final int BLOODTHIRSTY = SharedColorTokens.Combat.WeaponTrait.BLOODTHIRSTY;
        public static final int HARMONIC = SharedColorTokens.Combat.WeaponTrait.HARMONIC;
        public static final int PRECISION = SharedColorTokens.Combat.WeaponTrait.PRECISION;
        public static final int RELENTLESS = SharedColorTokens.Combat.WeaponTrait.RELENTLESS;
        public static final int GUARDIAN = SharedColorTokens.Combat.WeaponTrait.GUARDIAN;
        public static final int DEVASTATING = SharedColorTokens.Combat.WeaponTrait.DEVASTATING;
        public static final int FINISHER = SharedColorTokens.Combat.WeaponTrait.FINISHER;
        public static final int CLEAVING = SharedColorTokens.Combat.WeaponTrait.CLEAVING;
        public static final int RETALIATING = SharedColorTokens.Combat.WeaponTrait.RETALIATING;

        private WeaponTrait() {}
    }
}
