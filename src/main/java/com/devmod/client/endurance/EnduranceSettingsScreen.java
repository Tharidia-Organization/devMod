package com.devmod.client.endurance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.ModConfigSpec;

import com.devmod.client.notification.ClientNotificationManager;
import com.devmod.client.ui.editor.components.EditorButton;
import com.devmod.client.ui.editor.core.DesignTokens;
import com.devmod.config.GameMechanicsConfig;
import com.devmod.endurance.EnduranceConfigSyncPayload;
import com.devmod.endurance.config.ConfigScope;
import com.devmod.notification.Notification;
import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;
import com.devmod.util.I18n;

import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class EnduranceSettingsScreen extends Screen {

    private static final int HEADER_HEIGHT = 40;
    private static final int SIDEBAR_WIDTH = 160;
    private static final int PADDING = 12;
    private static final int SLIDER_HEIGHT = 20;
    private static final int SLIDER_SPACING = 6;

    // Section colors - using DesignTokens
    private static final int COLOR_RED = DesignTokens.Semantic.ERROR;
    private static final int COLOR_ORANGE = EnduranceUiTheme.Accent.ORANGE;   // No direct token
    private static final int COLOR_GOLD = DesignTokens.Semantic.WARNING;
    private static final int COLOR_CYAN = DesignTokens.Accent.PRIMARY;
    private static final int COLOR_PURPLE = EnduranceUiTheme.Accent.PURPLE;   // No direct token
    private static final int COLOR_GREEN = DesignTokens.Semantic.SUCCESS;
    private static final int COLOR_BLUE = DesignTokens.Semantic.INFO;

    private static final int FOOTER_HEIGHT = 50;
    private static final int COLOR_WARNING = DesignTokens.Semantic.WARNING; // Orange for modified
    private static final int COLOR_SAVED = DesignTokens.Semantic.SUCCESS; // Green for saved
    private static final long SAVE_FEEDBACK_DURATION_MS = 2000;

    private final Screen parent;
    private final List<ConfigSection> sections = new ArrayList<>();
    private int activeSection = 0;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    // State tracking for UX feedback
    private final Map<String, Object> originalValues = new HashMap<>();
    private final Set<String> modifiedKeys = new HashSet<>();
    private final Set<String> recentlySavedKeys = new HashSet<>();
    private long lastSaveTime = 0;
    private int pendingChangesCount = 0;

    @Nullable
    private EditorButton backButton;
    @Nullable
    private EditorButton applyGlobalButton;
    @Nullable
    private EditorButton applySessionButton;
    @Nullable
    private EditorButton proposeButton;
    @Nullable
    private EditorButton resetSectionButton;
    @Nullable
    private EditorButton resetAllButton;

    public EnduranceSettingsScreen(Screen parent) {
        super(I18n.translate("devmod.endurance.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        initSections();
        initButtons();
        captureOriginalValues();
        calculateMaxScroll();
    }

    /**
     * Capture original values for all config items to track modifications.
     */
    private void captureOriginalValues() {
        originalValues.clear();
        for (ConfigSection section : sections) {
            for (ConfigItem item : section.items) {
                originalValues.put(item.getConfigKey(), item.getCurrentValue());
            }
        }
    }

    private void initSections() {
        sections.clear();

        // Execution System
        var execution = new ConfigSection(I18n.translate("devmod.endurance.settings.section.execution").getString(), COLOR_RED);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.hp_threshold").getString(), GameMechanicsConfig.EXECUTION_HP_THRESHOLD, 0.01, 0.50, "%.0f%%", 100);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.duration_ticks").getString(), GameMechanicsConfig.EXECUTION_DURATION_TICKS, 10, 100);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.cooldown_ticks").getString(), GameMechanicsConfig.EXECUTION_COOLDOWN_TICKS, 0, 200);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.style_reward").getString(), GameMechanicsConfig.EXECUTION_STYLE_REWARD, 0, 1000);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.hp_regen_percent").getString(), GameMechanicsConfig.EXECUTION_HP_REGEN_PERCENT, 0.0, 0.50, "%.0f%%", 100);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.drop_boost").getString(), GameMechanicsConfig.EXECUTION_DROP_BOOST, 0.0, 2.0, "%.0f%%", 100);
        execution.addSlider(I18n.translate("devmod.endurance.settings.execution.range_blocks").getString(), GameMechanicsConfig.EXECUTION_RANGE, 1.0, 10.0, "%.1f", 1);
        sections.add(execution);

        // Combo System
        var combo = new ConfigSection(I18n.translate("devmod.endurance.settings.section.combo").getString(), COLOR_ORANGE);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.timeout_ticks").getString(), GameMechanicsConfig.COMBO_TIMEOUT_TICKS, 20, 200);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.base_points").getString(), GameMechanicsConfig.COMBO_BASE_POINTS, 1, 100);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.multiplier_inc").getString(), GameMechanicsConfig.COMBO_MULTIPLIER_INCREMENT, 0.01, 0.5, "%.2f", 1);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.max_multiplier").getString(), GameMechanicsConfig.COMBO_MAX_MULTIPLIER, 1.0, 10.0, "%.1fx", 1);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.finisher_threshold").getString(), GameMechanicsConfig.COMBO_FINISHER_THRESHOLD, 3, 50);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.juggle_bonus").getString(), GameMechanicsConfig.COMBO_JUGGLE_BONUS, 0, 200);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.headshot_bonus").getString(), GameMechanicsConfig.COMBO_HEADSHOT_BONUS, 0, 200);
        combo.addSlider(I18n.translate("devmod.endurance.settings.combo.execution_bonus").getString(), GameMechanicsConfig.COMBO_EXECUTION_BONUS, 0, 500);
        sections.add(combo);

        // Style Rank System
        var styleRank = new ConfigSection(I18n.translate("devmod.endurance.settings.section.style_rank").getString(), COLOR_GOLD);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.c_threshold").getString(), GameMechanicsConfig.STYLE_RANK_C_THRESHOLD, 50, 500);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.b_threshold").getString(), GameMechanicsConfig.STYLE_RANK_B_THRESHOLD, 100, 1000);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.a_threshold").getString(), GameMechanicsConfig.STYLE_RANK_A_THRESHOLD, 200, 2000);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.s_threshold").getString(), GameMechanicsConfig.STYLE_RANK_S_THRESHOLD, 400, 5000);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.ss_threshold").getString(), GameMechanicsConfig.STYLE_RANK_SS_THRESHOLD, 600, 8000);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.sss_threshold").getString(), GameMechanicsConfig.STYLE_RANK_SSS_THRESHOLD, 1000, 15000);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.decay_rate").getString(), GameMechanicsConfig.STYLE_DECAY_RATE, 0.0, 50.0, "%.1f/s", 1);
        styleRank.addSlider(I18n.translate("devmod.endurance.settings.style_rank.decay_delay").getString(), GameMechanicsConfig.STYLE_DECAY_DELAY_TICKS, 0, 400);
        sections.add(styleRank);

        // Momentum / Flow State
        var momentum = new ConfigSection(I18n.translate("devmod.endurance.settings.section.momentum").getString(), COLOR_CYAN);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.decay_rate").getString(), GameMechanicsConfig.MOMENTUM_DECAY_RATE, 0.0, 0.5, "%.0f%%/s", 100);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.kill_boost").getString(), GameMechanicsConfig.MOMENTUM_KILL_BOOST, 0.0, 1.0, "%.0f%%", 100);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.hit_boost").getString(), GameMechanicsConfig.MOMENTUM_HIT_BOOST, 0.0, 0.5, "%.0f%%", 100);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.overdrive_threshold").getString(), GameMechanicsConfig.MOMENTUM_OVERDRIVE_THRESHOLD, 0.5, 1.0, "%.0f%%", 100);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.overdrive_duration").getString(), GameMechanicsConfig.MOMENTUM_OVERDRIVE_DURATION_TICKS, 60, 600);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.stale_threshold").getString(), GameMechanicsConfig.FLOW_STALE_THRESHOLD_TICKS, 20, 400);
        momentum.addSlider(I18n.translate("devmod.endurance.settings.momentum.fresh_bonus").getString(), GameMechanicsConfig.FLOW_FRESH_BONUS_MULTIPLIER, 1.0, 3.0, "%.1fx", 1);
        sections.add(momentum);

        // Devil's Bargain
        var bargain = new ConfigSection(I18n.translate("devmod.endurance.settings.section.devils_bargain").getString(), COLOR_PURPLE);
        bargain.addToggle(I18n.translate("devmod.endurance.settings.devils_bargain.enabled").getString(), GameMechanicsConfig.BARGAIN_ENABLED);
        bargain.addSlider(I18n.translate("devmod.endurance.settings.devils_bargain.first_altar_wave").getString(), GameMechanicsConfig.BARGAIN_ALTAR_SPAWN_WAVE, 1, 20);
        bargain.addSlider(I18n.translate("devmod.endurance.settings.devils_bargain.altar_interval").getString(), GameMechanicsConfig.BARGAIN_ALTAR_INTERVAL_WAVES, 1, 10);
        bargain.addSlider(I18n.translate("devmod.endurance.settings.devils_bargain.max_curses").getString(), GameMechanicsConfig.BARGAIN_MAX_CURSES_PER_RUN, 1, 20);
        bargain.addSlider(I18n.translate("devmod.endurance.settings.devils_bargain.choice_timeout").getString(), GameMechanicsConfig.BARGAIN_CHOICE_TIMEOUT_TICKS, 100, 1200);
        bargain.addSlider(I18n.translate("devmod.endurance.settings.devils_bargain.curse_power").getString(), GameMechanicsConfig.BARGAIN_CURSE_POWER_MULTIPLIER, 0.1, 3.0, "%.1fx", 1);
        bargain.addSlider(I18n.translate("devmod.endurance.settings.devils_bargain.boon_power").getString(), GameMechanicsConfig.BARGAIN_BOON_POWER_MULTIPLIER, 0.1, 3.0, "%.1fx", 1);
        sections.add(bargain);

        // Arena Hazards
        var hazards = new ConfigSection(I18n.translate("devmod.endurance.settings.section.hazards").getString(), COLOR_GREEN);
        hazards.addToggle(I18n.translate("devmod.endurance.settings.hazards.enabled").getString(), GameMechanicsConfig.HAZARD_ENABLED);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.floor_crumble_wave").getString(), GameMechanicsConfig.HAZARD_FLOOR_CRUMBLE_WAVE, 1, 50);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.blood_moon_wave").getString(), GameMechanicsConfig.HAZARD_BLOOD_MOON_WAVE, 1, 50);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.arena_shrink_wave").getString(), GameMechanicsConfig.HAZARD_ARENA_SHRINK_WAVE, 1, 50);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.lightning_wave").getString(), GameMechanicsConfig.HAZARD_LIGHTNING_STORM_WAVE, 1, 50);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.void_rifts_wave").getString(), GameMechanicsConfig.HAZARD_VOID_RIFTS_WAVE, 1, 50);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.lightning_damage").getString(), GameMechanicsConfig.HAZARD_LIGHTNING_DAMAGE, 1.0, 20.0, "%.1f", 1);
        hazards.addSlider(I18n.translate("devmod.endurance.settings.hazards.blood_moon_buff").getString(), GameMechanicsConfig.HAZARD_BLOOD_MOON_MOB_BUFF, 1.0, 3.0, "%.1fx", 1);
        sections.add(hazards);

        // Wave Configuration
        var waves = new ConfigSection(I18n.translate("devmod.endurance.settings.section.waves").getString(), COLOR_BLUE);
        waves.addSlider(I18n.translate("devmod.endurance.settings.waves.base_mob_count").getString(), GameMechanicsConfig.WAVE_BASE_MOB_COUNT, 1, 50);
        waves.addSlider(I18n.translate("devmod.endurance.settings.waves.mob_scaling").getString(), GameMechanicsConfig.WAVE_MOB_SCALING, 1.0, 3.0, "%.1fx", 1);
        waves.addSlider(I18n.translate("devmod.endurance.settings.waves.intermission").getString(), GameMechanicsConfig.WAVE_INTERMISSION_TICKS, 20, 600);
        waves.addSlider(I18n.translate("devmod.endurance.settings.waves.elite_chance").getString(), GameMechanicsConfig.WAVE_ELITE_CHANCE_BASE, 0.0, 1.0, "%.0f%%", 100);
        waves.addSlider(I18n.translate("devmod.endurance.settings.waves.elite_scaling").getString(), GameMechanicsConfig.WAVE_ELITE_CHANCE_SCALING, 0.0, 0.1, "%.1f%%", 100);
        waves.addSlider(I18n.translate("devmod.endurance.settings.waves.boss_interval").getString(), GameMechanicsConfig.WAVE_BOSS_INTERVAL, 1, 20);
        sections.add(waves);

        // Rewards
        var rewards = new ConfigSection(I18n.translate("devmod.endurance.settings.section.rewards").getString(), COLOR_GOLD);
        rewards.addSlider(I18n.translate("devmod.endurance.settings.rewards.xp_multiplier").getString(), GameMechanicsConfig.REWARD_XP_MULTIPLIER, 0.1, 10.0, "%.1fx", 1);
        rewards.addSlider(I18n.translate("devmod.endurance.settings.rewards.style_multiplier").getString(), GameMechanicsConfig.REWARD_STYLE_MULTIPLIER, 0.1, 10.0, "%.1fx", 1);
        rewards.addSlider(I18n.translate("devmod.endurance.settings.rewards.drop_rate_bonus").getString(), GameMechanicsConfig.REWARD_DROP_RATE_BONUS, 0.0, 5.0, "%.0f%%", 100);
        rewards.addSlider(I18n.translate("devmod.endurance.settings.rewards.bonus_chest_interval").getString(), GameMechanicsConfig.REWARD_BONUS_CHEST_WAVE_INTERVAL, 1, 20);
        sections.add(rewards);

        // Season Pass System
        var seasonPass = new ConfigSection(I18n.translate("devmod.endurance.settings.section.season_pass").getString(), COLOR_PURPLE);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.max_tier").getString(), GameMechanicsConfig.SEASON_MAX_TIER, 10, 500);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_per_tier").getString(), GameMechanicsConfig.SEASON_XP_PER_TIER, 100, 10000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.duration_days").getString(), GameMechanicsConfig.SEASON_DURATION_DAYS, 7, 365);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_per_kill").getString(), GameMechanicsConfig.SEASON_XP_PER_KILL, 0, 100);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_per_wave").getString(), GameMechanicsConfig.SEASON_XP_PER_WAVE, 0, 500);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_per_boss").getString(), GameMechanicsConfig.SEASON_XP_PER_BOSS, 0, 1000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_daily_challenge").getString(), GameMechanicsConfig.SEASON_XP_DAILY_CHALLENGE, 0, 5000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_weekly_challenge").getString(), GameMechanicsConfig.SEASON_XP_WEEKLY_CHALLENGE, 0, 20000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_style_s").getString(), GameMechanicsConfig.SEASON_XP_STYLE_S, 0, 1000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_style_ss").getString(), GameMechanicsConfig.SEASON_XP_STYLE_SS, 0, 2000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_style_sss").getString(), GameMechanicsConfig.SEASON_XP_STYLE_SSS, 0, 5000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_flawless_wave").getString(), GameMechanicsConfig.SEASON_XP_FLAWLESS_WAVE, 0, 500);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_high_combo_50_plus").getString(), GameMechanicsConfig.SEASON_XP_HIGH_COMBO_50, 0, 1000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_high_combo_100_plus").getString(), GameMechanicsConfig.SEASON_XP_HIGH_COMBO_100, 0, 2000);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.xp_boost_multiplier").getString(), GameMechanicsConfig.SEASON_XP_BOOST_MULTIPLIER, 1.0, 5.0, "%.1fx", 1);
        seasonPass.addSlider(I18n.translate("devmod.endurance.settings.season_pass.boost_duration_min").getString(), GameMechanicsConfig.SEASON_XP_BOOST_DURATION_MINUTES, 5, 1440);
        sections.add(seasonPass);

        // Guild System
        var guild = new ConfigSection(I18n.translate("devmod.endurance.settings.section.guild").getString(), COLOR_CYAN);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.max_size").getString(), GameMechanicsConfig.GUILD_MAX_SIZE, 5, 200);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.max_level").getString(), GameMechanicsConfig.GUILD_MAX_LEVEL, 5, 100);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.create_cost").getString(), GameMechanicsConfig.GUILD_CREATE_COST, 0, 100000);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.xp_per_level_base").getString(), GameMechanicsConfig.GUILD_XP_PER_LEVEL_BASE, 100, 10000);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.xp_scaling").getString(), GameMechanicsConfig.GUILD_XP_SCALING, 1.0, 3.0, "%.1fx", 1);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.bank_tax_percent").getString(), GameMechanicsConfig.GUILD_BANK_TAX_PERCENT, 0, 50);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.obj_kills_target").getString(), GameMechanicsConfig.GUILD_OBJECTIVE_KILLS_TARGET, 100, 100000);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.obj_waves_target").getString(), GameMechanicsConfig.GUILD_OBJECTIVE_WAVES_TARGET, 10, 5000);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.obj_bosses_target").getString(), GameMechanicsConfig.GUILD_OBJECTIVE_BOSSES_TARGET, 5, 1000);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.obj_tokens_target").getString(), GameMechanicsConfig.GUILD_OBJECTIVE_TOKENS_TARGET, 1000, 1000000);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.obj_challenges").getString(), GameMechanicsConfig.GUILD_OBJECTIVE_CHALLENGES_TARGET, 5, 500);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.perk_unlock_lv_1").getString(), GameMechanicsConfig.GUILD_PERK_UNLOCK_LEVEL_1, 1, 20);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.perk_unlock_lv_2").getString(), GameMechanicsConfig.GUILD_PERK_UNLOCK_LEVEL_2, 2, 30);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.perk_unlock_lv_3").getString(), GameMechanicsConfig.GUILD_PERK_UNLOCK_LEVEL_3, 3, 40);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.token_bonus_tier_1").getString(), GameMechanicsConfig.GUILD_BONUS_TOKENS_5, 0.0, 0.5, "%.0f%%", 100);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.token_bonus_tier_2").getString(), GameMechanicsConfig.GUILD_BONUS_TOKENS_10, 0.0, 0.5, "%.0f%%", 100);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.token_bonus_tier_3").getString(), GameMechanicsConfig.GUILD_BONUS_TOKENS_15, 0.0, 0.5, "%.0f%%", 100);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.xp_bonus_tier_1").getString(), GameMechanicsConfig.GUILD_BONUS_XP_5, 0.0, 0.5, "%.0f%%", 100);
        guild.addSlider(I18n.translate("devmod.endurance.settings.guild.xp_bonus_tier_2").getString(), GameMechanicsConfig.GUILD_BONUS_XP_10, 0.0, 0.5, "%.0f%%", 100);
        sections.add(guild);

        // Ascension/Prestige System
        var ascension = new ConfigSection(I18n.translate("devmod.endurance.settings.section.ascension").getString(), COLOR_GOLD);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.max_level").getString(), GameMechanicsConfig.ASCENSION_MAX_LEVEL, 1, 50);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.min_prestige").getString(), GameMechanicsConfig.ASCENSION_MIN_PRESTIGE, 10, 1000);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.cost_scaling").getString(), GameMechanicsConfig.ASCENSION_COST_SCALING, 0, 500);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.damage_bonus_per_lv").getString(), GameMechanicsConfig.ASCENSION_DAMAGE_BONUS_PER_LEVEL, 0.0, 0.5, "%.0f%%", 100);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.defense_bonus_per_lv").getString(), GameMechanicsConfig.ASCENSION_DEFENSE_BONUS_PER_LEVEL, 0.0, 0.3, "%.0f%%", 100);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.token_multi_per_lv").getString(), GameMechanicsConfig.ASCENSION_TOKEN_MULTIPLIER_PER_LEVEL, 0.0, 1.0, "%.0f%%", 100);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.lifesteal_per_lv").getString(), GameMechanicsConfig.ASCENSION_LIFESTEAL_BONUS_PER_LEVEL, 0.0, 0.1, "%.1f%%", 100);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.crit_bonus_per_lv").getString(), GameMechanicsConfig.ASCENSION_CRIT_BONUS_PER_LEVEL, 0.0, 0.2, "%.0f%%", 100);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.combo_decay_reduce").getString(), GameMechanicsConfig.ASCENSION_COMBO_DECAY_REDUCTION_PER_LEVEL, 0.0, 0.5, "%.0f%%", 100);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.extra_perk_slots_per_lv").getString(), GameMechanicsConfig.ASCENSION_EXTRA_PERK_SLOTS_PER_LEVEL, 0, 3);
        ascension.addSlider(I18n.translate("devmod.endurance.settings.ascension.starting_hp_per_lv").getString(), GameMechanicsConfig.ASCENSION_STARTING_HP_BONUS_PER_LEVEL, 0.0, 0.5, "%.0f%%", 100);
        sections.add(ascension);

        // Tension System (Boss Spawning)
        var tension = new ConfigSection(I18n.translate("devmod.endurance.settings.section.tension").getString(), COLOR_RED);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.base_wave_gain").getString(), GameMechanicsConfig.TENSION_BASE_WAVE_GAIN, 0.0, 1.0, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.no_hit_bonus").getString(), GameMechanicsConfig.TENSION_NO_HIT_BONUS, 0.0, 1.0, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.min_threshold").getString(), GameMechanicsConfig.TENSION_MIN_THRESHOLD, 0.1, 1.0, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.max_threshold").getString(), GameMechanicsConfig.TENSION_MAX_THRESHOLD, 0.5, 2.0, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.elite_kill_bonus").getString(), GameMechanicsConfig.TENSION_ELITE_KILL_BONUS, 0.0, 0.5, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.combo_bonus").getString(), GameMechanicsConfig.TENSION_COMBO_BONUS_THRESHOLD, 0.0, 0.2, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.style_s_bonus").getString(), GameMechanicsConfig.TENSION_STYLE_S_BONUS, 0.0, 0.5, "%.0f%%", 100);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.min_waves_before_boss").getString(), GameMechanicsConfig.TENSION_MIN_WAVES_BEFORE_BOSS, 1, 20);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.max_waves_no_boss").getString(), GameMechanicsConfig.TENSION_MAX_WAVES_WITHOUT_BOSS, 3, 30);
        tension.addSlider(I18n.translate("devmod.endurance.settings.tension.decay_after_boss").getString(), GameMechanicsConfig.TENSION_DECAY_AFTER_BOSS, 0.0, 1.0, "%.0f%%", 100);
        sections.add(tension);

        // Perk Rarity System
        var perkRarity = new ConfigSection(I18n.translate("devmod.endurance.settings.section.perk_rarity").getString(), COLOR_PURPLE);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.common_weight").getString(), GameMechanicsConfig.PERK_COMMON_WEIGHT, 0, 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.uncommon_weight").getString(), GameMechanicsConfig.PERK_UNCOMMON_WEIGHT, 0, 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.rare_weight").getString(), GameMechanicsConfig.PERK_RARE_WEIGHT, 0, 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.epic_weight").getString(), GameMechanicsConfig.PERK_EPIC_WEIGHT, 0, 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.legendary_weight").getString(), GameMechanicsConfig.PERK_LEGENDARY_WEIGHT, 0, 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.choices_per_select").getString(), GameMechanicsConfig.PERK_CHOICES_PER_SELECTION, 2, 6);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.reroll_cost_base").getString(), GameMechanicsConfig.PERK_REROLL_COST_BASE, 0, 500);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.reroll_cost_inc").getString(), GameMechanicsConfig.PERK_REROLL_COST_INCREMENT, 0, 200);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.dmg_bonus_common").getString(), GameMechanicsConfig.PERK_DAMAGE_BONUS_COMMON, 0.0, 0.5, "%.0f%%", 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.dmg_bonus_uncommon").getString(), GameMechanicsConfig.PERK_DAMAGE_BONUS_UNCOMMON, 0.0, 0.5, "%.0f%%", 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.dmg_bonus_rare").getString(), GameMechanicsConfig.PERK_DAMAGE_BONUS_RARE, 0.0, 0.5, "%.0f%%", 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.dmg_bonus_epic").getString(), GameMechanicsConfig.PERK_DAMAGE_BONUS_EPIC, 0.0, 0.5, "%.0f%%", 100);
        perkRarity.addSlider(I18n.translate("devmod.endurance.settings.perk_rarity.dmg_bonus_legend").getString(), GameMechanicsConfig.PERK_DAMAGE_BONUS_LEGENDARY, 0.0, 1.0, "%.0f%%", 100);
        sections.add(perkRarity);

        // Challenge System
        var challenges = new ConfigSection(I18n.translate("devmod.endurance.settings.section.challenges").getString(), COLOR_GREEN);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.daily_count").getString(), GameMechanicsConfig.CHALLENGE_DAILY_COUNT, 1, 10);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.weekly_count").getString(), GameMechanicsConfig.CHALLENGE_WEEKLY_COUNT, 1, 5);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.daily_reset_hour").getString(), GameMechanicsConfig.CHALLENGE_DAILY_RESET_HOUR, 0, 23);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.weekly_reset_day").getString(), GameMechanicsConfig.CHALLENGE_WEEKLY_RESET_DAY, 1, 7);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.kills_target_min").getString(), GameMechanicsConfig.CHALLENGE_KILLS_TARGET_MIN, 5, 200);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.kills_target_max").getString(), GameMechanicsConfig.CHALLENGE_KILLS_TARGET_MAX, 50, 1000);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.waves_target_min").getString(), GameMechanicsConfig.CHALLENGE_WAVES_TARGET_MIN, 1, 20);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.waves_target_max").getString(), GameMechanicsConfig.CHALLENGE_WAVES_TARGET_MAX, 5, 50);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.combo_target_min").getString(), GameMechanicsConfig.CHALLENGE_COMBO_TARGET_MIN, 5, 50);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.combo_target_max").getString(), GameMechanicsConfig.CHALLENGE_COMBO_TARGET_MAX, 50, 500);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.daily_token_reward").getString(), GameMechanicsConfig.CHALLENGE_DAILY_TOKEN_REWARD, 0, 1000);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.weekly_token_reward").getString(), GameMechanicsConfig.CHALLENGE_WEEKLY_TOKEN_REWARD, 0, 5000);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.daily_xp_reward").getString(), GameMechanicsConfig.CHALLENGE_DAILY_XP_REWARD, 0, 5000);
        challenges.addSlider(I18n.translate("devmod.endurance.settings.challenges.weekly_xp_reward").getString(), GameMechanicsConfig.CHALLENGE_WEEKLY_XP_REWARD, 0, 20000);
        sections.add(challenges);

        // Mob Configuration
        var mobConfig = new ConfigSection(I18n.translate("devmod.endurance.settings.section.mob_config").getString(), COLOR_CYAN);
        String mobEditorLabel = Objects.requireNonNull(I18n.translate("devmod.endurance.settings.mob_pool_editor").getString());
        mobConfig.addButton(mobEditorLabel, this::openMobPoolEditor);
        sections.add(mobConfig);
    }

    /**
     * Open the Mob Pool Editor screen.
     */
    private void openMobPoolEditor() {
        if (minecraft != null) {
            minecraft.setScreen(new MobPoolEditorScreen(this));
        }
    }

    private void initButtons() {
        backButton = EditorButton.builder("back", "< " + I18n.ui("back").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.MEDIUM)
            .onClick(this::onClose)
            .build();

        // 3 scope buttons for config sync
        applyGlobalButton = EditorButton.builder("apply-global", I18n.translate("devmod.settings.button.apply_global").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> applyChanges(ConfigScope.GLOBAL))
            .build();

        applySessionButton = EditorButton.builder("apply-session", I18n.translate("devmod.settings.button.apply_session").getString())
            .style(EditorButton.Style.SUCCESS)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> applyChanges(ConfigScope.SESSION))
            .build();

        proposeButton = EditorButton.builder("propose", I18n.translate("devmod.settings.button.propose").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(() -> applyChanges(ConfigScope.PROPOSAL))
            .build();

        resetSectionButton = EditorButton.builder("reset-section", I18n.translate("devmod.settings.button.reset_section").getString())
            .style(EditorButton.Style.NORMAL)
            .size(EditorButton.Size.SMALL)
            .onClick(this::resetCurrentSection)
            .build();

        resetAllButton = EditorButton.builder("reset-all", I18n.translate("devmod.settings.button.reset_all").getString())
            .style(EditorButton.Style.DANGER)
            .size(EditorButton.Size.SMALL)
            .onClick(this::resetAllToDefaults)
            .build();
    }

    // ========================================
    // Apply and Reset Methods
    // ========================================

    private void applyChanges(ConfigScope scope) {
        if (pendingChangesCount == 0) {
            return;
        }

        // Collect modified config entries
        List<EnduranceConfigSyncPayload.ConfigEntry> entries = new ArrayList<>();
        for (ConfigSection section : sections) {
            for (ConfigItem item : section.items) {
                if (modifiedKeys.contains(item.getConfigKey())) {
                    entries.add(new EnduranceConfigSyncPayload.ConfigEntry(
                        item.getConfigKey(),
                        item.getValueType(),
                        item.getCurrentValueAsString()
                    ));
                }
            }
        }

        if (entries.isEmpty()) {
            return;
        }

        // Send to server
        EnduranceConfigSyncPayload payload = new EnduranceConfigSyncPayload(entries, scope);
        PacketDistributor.sendToServer(payload);

        // Mark all modified as saved
        recentlySavedKeys.addAll(modifiedKeys);
        lastSaveTime = System.currentTimeMillis();

        int savedCount = pendingChangesCount;

        // Clear modified tracking
        modifiedKeys.clear();
        pendingChangesCount = 0;

        // Show toast notification based on scope
        String messageKey = switch (scope) {
            case GLOBAL -> "devmod.settings.applied.global";
            case SESSION -> "devmod.settings.applied.session";
            case PROPOSAL -> "devmod.settings.applied.proposal";
        };

        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.applied.title")
            .messageKey(messageKey)
            .param("count", String.valueOf(savedCount))
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2500)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void resetCurrentSection() {
        if (activeSection < 0 || activeSection >= sections.size()) {
            return;
        }

        ConfigSection section = sections.get(activeSection);
        for (ConfigItem item : section.items) {
            item.resetToDefault();
        }

        // Recalculate pending changes
        recalculatePendingChanges();

        // Show toast
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.reset.title")
            .messageKey("devmod.settings.reset.section")
            .param("section", section.name)
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2000)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void resetAllToDefaults() {
        for (ConfigSection section : sections) {
            for (ConfigItem item : section.items) {
                item.resetToDefault();
            }
        }

        // Clear all tracking
        modifiedKeys.clear();
        pendingChangesCount = 0;

        // Show toast
        Notification notification = Notification.builder(NotificationCategory.SYSTEM)
            .titleKey("devmod.settings.reset.title")
            .messageKey("devmod.settings.reset.all")
            .priority(NotificationPriority.NORMAL)
            .displayDurationMs(2000)
            .build();
        ClientNotificationManager.INSTANCE.handleNotification(notification);
    }

    private void recalculatePendingChanges() {
        modifiedKeys.clear();
        for (ConfigSection section : sections) {
            for (ConfigItem item : section.items) {
                if (item.isModified()) {
                    modifiedKeys.add(item.getConfigKey());
                }
            }
        }
        pendingChangesCount = modifiedKeys.size();
    }

    /**
     * Called when a config value changes to track modifications.
     */
    void onConfigValueChanged(String configKey, Object originalValue, Object newValue) {
        if (Objects.equals(originalValue, newValue)) {
            modifiedKeys.remove(configKey);
        } else {
            modifiedKeys.add(configKey);
        }
        pendingChangesCount = modifiedKeys.size();

        // Clear from recently saved if modified again
        recentlySavedKeys.remove(configKey);
    }

    /**
     * Check if a config key was recently saved (for visual feedback).
     */
    boolean wasRecentlySaved(String configKey) {
        if (!recentlySavedKeys.contains(configKey)) {
            return false;
        }
        // Check if within feedback duration
        if (System.currentTimeMillis() - lastSaveTime > SAVE_FEEDBACK_DURATION_MS) {
            recentlySavedKeys.clear();
            return false;
        }
        return true;
    }

    /**
     * Check if a config key has pending modifications.
     */
    boolean isModified(String configKey) {
        return modifiedKeys.contains(configKey);
    }

    private void calculateMaxScroll() {
        if (activeSection >= 0 && activeSection < sections.size()) {
            ConfigSection section = sections.get(activeSection);
            int contentHeight = section.getContentHeight();
            int viewportHeight = height - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;
            maxScroll = Math.max(0, contentHeight - viewportHeight);
        } else {
            maxScroll = 0;
        }
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    @Override
    public void render(@Nonnull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(0, 0, width, height, DesignTokens.Bg.LEVEL_0);

        var safeFont = Objects.requireNonNull(font);

        // Header
        renderHeader(graphics, safeFont, mouseX, mouseY);

        // Sidebar (section tabs)
        renderSidebar(graphics, safeFont, mouseX, mouseY);

        // Content area
        renderContent(graphics, safeFont, mouseX, mouseY);

        // Footer with buttons
        renderFooter(graphics, safeFont, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        // Header background
        graphics.fill(0, 0, width, HEADER_HEIGHT, DesignTokens.Bg.LEVEL_1);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, DesignTokens.Accent.PRIMARY);

        // Title
        graphics.drawString(font, I18n.translate("devmod.endurance.settings.title").getString(),
            PADDING, 12, DesignTokens.Text.PRIMARY, false);

        // Subtitle
        if (activeSection >= 0 && activeSection < sections.size()) {
            String sectionName = sections.get(activeSection).name;
            graphics.drawString(font, I18n.ui("category", sectionName).getString(),
                PADDING, 26, DesignTokens.Text.SECONDARY, false);
        }

        // Back button
        int btnW = 70;
        int btnH = 24;
        if (backButton != null) {
            backButton.render(graphics, width - btnW - PADDING, 8, btnW, btnH, mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        int sidebarX = 0;
        int sidebarY = HEADER_HEIGHT;
        int sidebarH = height - HEADER_HEIGHT;

        // Sidebar background
        graphics.fill(sidebarX, sidebarY, sidebarX + SIDEBAR_WIDTH, sidebarY + sidebarH, DesignTokens.Bg.LEVEL_1);
        graphics.fill(sidebarX + SIDEBAR_WIDTH - 1, sidebarY, sidebarX + SIDEBAR_WIDTH, sidebarY + sidebarH, DesignTokens.Accent.PRIMARY);

        int y = sidebarY + PADDING;
        int tabH = 28;

        for (int i = 0; i < sections.size(); i++) {
            ConfigSection section = sections.get(i);
            boolean isActive = i == activeSection;
            boolean isHovered = mouseX >= sidebarX && mouseX < sidebarX + SIDEBAR_WIDTH - 1 &&
                               mouseY >= y && mouseY < y + tabH;

            // Tab background
            if (isActive) {
                graphics.fill(sidebarX + 4, y, sidebarX + SIDEBAR_WIDTH - 1, y + tabH, section.color);
            } else if (isHovered) {
                graphics.fill(sidebarX + 4, y, sidebarX + SIDEBAR_WIDTH - 1, y + tabH, DesignTokens.Surface.LEVEL_1);
            }

            // Left accent bar for active
            if (isActive) {
                graphics.fill(sidebarX, y, sidebarX + 4, y + tabH, section.color);
            }

            // Section name
            int textColor = isActive ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
            graphics.drawString(font, section.name, sidebarX + 12, y + 10, textColor, false);

            // Item count badge
            String countStr = String.valueOf(section.items.size());
            int countX = sidebarX + SIDEBAR_WIDTH - 20;
            graphics.drawString(font, countStr, countX, y + 10, DesignTokens.Text.MUTED, false);

            y += tabH + 2;
        }
    }

    private void renderContent(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        int contentX = SIDEBAR_WIDTH + PADDING;
        int contentY = HEADER_HEIGHT + PADDING;
        int contentW = width - SIDEBAR_WIDTH - PADDING * 2;
        int contentH = height - HEADER_HEIGHT - FOOTER_HEIGHT - PADDING * 2;

        if (activeSection < 0 || activeSection >= sections.size()) return;

        ConfigSection section = sections.get(activeSection);

        // Enable scissor for scrolling
        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int y = contentY - scrollOffset;

        for (ConfigItem item : section.items) {
            if (y + item.getHeight() > contentY - 50 && y < contentY + contentH + 50) {
                item.render(graphics, font, contentX, y, contentW, mouseX, mouseY);

                // Show checkmark for recently saved items
                if (wasRecentlySaved(item.getConfigKey())) {
                    graphics.drawString(font, "\u2713", contentX + contentW - 20, y + 2, COLOR_SAVED, false);
                }
                // Show warning indicator for modified items
                else if (isModified(item.getConfigKey())) {
                    graphics.drawString(font, "\u25CF", contentX + contentW - 20, y + 2, COLOR_WARNING, false);
                }
            }
            y += item.getHeight() + SLIDER_SPACING;
        }

        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = contentX + contentW - 4;
            int scrollbarH = Math.max(20, (int) ((float) contentH / (contentH + maxScroll) * contentH));
            int scrollbarY = contentY + (int) ((float) scrollOffset / maxScroll * (contentH - scrollbarH));
            graphics.fill(scrollbarX, contentY, scrollbarX + 4, contentY + contentH, DesignTokens.Surface.LEVEL_0);
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarH, DesignTokens.Accent.PRIMARY);
        }
    }

    private void renderFooter(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
        int footerY = height - FOOTER_HEIGHT;
        int footerX = SIDEBAR_WIDTH;

        // Footer background
        graphics.fill(footerX, footerY, width, height, DesignTokens.Bg.LEVEL_1);
        graphics.fill(footerX, footerY, width, footerY + 1, DesignTokens.Accent.PRIMARY);

        // Pending changes indicator
        int textY = footerY + 18;
        if (pendingChangesCount > 0) {
            String pendingMsg = I18n.translate("devmod.settings.pending", pendingChangesCount).getString();
            graphics.drawString(font, pendingMsg, footerX + PADDING, textY, COLOR_WARNING, false);
        } else {
            String noChangesMsg = I18n.translate("devmod.settings.no_changes").getString();
            // Show green if recently saved, otherwise muted
            boolean recentlySaved = !recentlySavedKeys.isEmpty() &&
                (System.currentTimeMillis() - lastSaveTime <= SAVE_FEEDBACK_DURATION_MS);
            int statusColor = recentlySaved ? COLOR_SAVED : DesignTokens.Text.MUTED;
            graphics.drawString(font, noChangesMsg, footerX + PADDING, textY, statusColor, false);
        }

        // Buttons - right aligned
        int btnY = footerY + 12;
        int btnSpacing = 6;
        int btnH = 26;
        int btnW = 90;

        // Apply Global button (rightmost, OP only)
        int globalBtnX = width - PADDING - btnW;
        if (applyGlobalButton != null) {
            applyGlobalButton.render(graphics, globalBtnX, btnY, btnW, btnH, mouseX, mouseY);
        }

        // Apply Session button
        int sessionBtnX = globalBtnX - btnSpacing - btnW;
        if (applySessionButton != null) {
            applySessionButton.render(graphics, sessionBtnX, btnY, btnW, btnH, mouseX, mouseY);
        }

        // Propose button
        int proposeBtnX = sessionBtnX - btnSpacing - btnW;
        if (proposeButton != null) {
            proposeButton.render(graphics, proposeBtnX, btnY, btnW, btnH, mouseX, mouseY);
        }

        // Reset All button
        int resetAllBtnW = 70;
        int resetAllBtnX = proposeBtnX - btnSpacing - resetAllBtnW;
        if (resetAllButton != null) {
            resetAllButton.render(graphics, resetAllBtnX, btnY, resetAllBtnW, btnH, mouseX, mouseY);
        }

        // Reset Section button
        int resetSectionBtnW = 90;
        int resetSectionBtnX = resetAllBtnX - btnSpacing - resetSectionBtnW;
        if (resetSectionButton != null) {
            resetSectionButton.render(graphics, resetSectionBtnX, btnY, resetSectionBtnW, btnH, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Back button
            if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            // Footer buttons - scope apply buttons
            if (applyGlobalButton != null && applyGlobalButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (applySessionButton != null && applySessionButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (proposeButton != null && proposeButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (resetSectionButton != null && resetSectionButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (resetAllButton != null && resetAllButton.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            // Sidebar tabs
            int sidebarY = HEADER_HEIGHT + PADDING;
            int tabH = 28;
            if (mouseX < SIDEBAR_WIDTH) {
                for (int i = 0; i < sections.size(); i++) {
                    int tabTop = sidebarY + i * (tabH + 2);
                    if (mouseY >= tabTop && mouseY < tabTop + tabH) {
                        if (activeSection != i) {
                            activeSection = i;
                            scrollOffset = 0;
                            calculateMaxScroll();
                        }
                        return true;
                    }
                }
            }

            // Content area sliders
            int contentX = SIDEBAR_WIDTH + PADDING;
            int contentY = HEADER_HEIGHT + PADDING;
            int contentW = width - SIDEBAR_WIDTH - PADDING * 2;

            if (activeSection >= 0 && activeSection < sections.size()) {
                ConfigSection section = sections.get(activeSection);
                int y = contentY - scrollOffset;
                for (ConfigItem item : section.items) {
                    if (item.mouseClicked(mouseX, mouseY, contentX, y, contentW)) {
                        return true;
                    }
                    y += item.getHeight() + SLIDER_SPACING;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (backButton != null) {
            backButton.mouseReleased(mouseX, mouseY, button);
        }
        if (applyGlobalButton != null) {
            applyGlobalButton.mouseReleased(mouseX, mouseY, button);
        }
        if (applySessionButton != null) {
            applySessionButton.mouseReleased(mouseX, mouseY, button);
        }
        if (proposeButton != null) {
            proposeButton.mouseReleased(mouseX, mouseY, button);
        }
        if (resetSectionButton != null) {
            resetSectionButton.mouseReleased(mouseX, mouseY, button);
        }
        if (resetAllButton != null) {
            resetAllButton.mouseReleased(mouseX, mouseY, button);
        }

        if (activeSection >= 0 && activeSection < sections.size()) {
            ConfigSection section = sections.get(activeSection);
            for (ConfigItem item : section.items) {
                item.mouseReleased();
            }
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int contentX = SIDEBAR_WIDTH + PADDING;
        int contentY = HEADER_HEIGHT + PADDING;
        int contentW = width - SIDEBAR_WIDTH - PADDING * 2;

        if (activeSection >= 0 && activeSection < sections.size()) {
            ConfigSection section = sections.get(activeSection);
            int y = contentY - scrollOffset;
            for (ConfigItem item : section.items) {
                if (item.mouseDragged(mouseX, mouseY, contentX, y, contentW)) {
                    return true;
                }
                y += item.getHeight() + SLIDER_SPACING;
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX > SIDEBAR_WIDTH) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) (scrollY * 20)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ========================================
    // Config Section and Item Classes
    // ========================================

    private static class ConfigSection {
        final String name;
        final int color;
        final List<ConfigItem> items = new ArrayList<>();

        ConfigSection(String name, int color) {
            this.name = name;
            this.color = color;
        }

        void addSlider(String label, ModConfigSpec.DoubleValue config, double min, double max, String format, double displayScale) {
            items.add(new DoubleSliderItem(label, config, min, max, format, displayScale));
        }

        void addSlider(String label, ModConfigSpec.IntValue config, int min, int max) {
            items.add(new IntSliderItem(label, config, min, max));
        }

        void addToggle(String label, ModConfigSpec.BooleanValue config) {
            items.add(new ToggleItem(label, config));
        }

        void addButton(@Nonnull String label, @Nonnull Runnable onClick) {
            items.add(new ButtonItem(label, onClick));
        }

        int getContentHeight() {
            int h = 0;
            for (ConfigItem item : items) {
                h += item.getHeight() + SLIDER_SPACING;
            }
            return h;
        }
    }

    /**
     * Extracts the simple config key from a NeoForge config path.
     * Example: "[wave, baseMobCount]" -> "baseMobCount"
     */
    private static String normalizeConfigKey(List<String> path) {
        if (path == null || path.isEmpty()) {
            return "unknown";
        }
        // Return the last element (the actual key name)
        return path.get(path.size() - 1);
    }

    private interface ConfigItem {
        void render(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int x, int y, int width, int mouseX, int mouseY);
        int getHeight();
        boolean mouseClicked(double mouseX, double mouseY, int sliderX, int sliderY, int sliderW);
        void mouseReleased();
        boolean mouseDragged(double mouseX, double mouseY, int sliderX, int sliderY, int sliderW);

        // Methods for UX feedback
        String getConfigKey();
        @Nullable Object getDefaultValue();
        @Nullable Object getCurrentValue();
        void resetToDefault();
        boolean isModified();

        // Methods for network sync
        String getValueType();
        String getCurrentValueAsString();
    }

    private static class DoubleSliderItem implements ConfigItem {
        final String label;
        final String configKey;
        final ModConfigSpec.DoubleValue config;
        final double min, max;
        final String format;
        final double displayScale;
        final double defaultValue;
        boolean dragging = false;

        DoubleSliderItem(String label, ModConfigSpec.DoubleValue config, double min, double max, String format, double displayScale) {
            this.label = label;
            this.configKey = normalizeConfigKey(config.getPath());
            this.config = config;
            this.min = min;
            this.max = max;
            this.format = format;
            this.displayScale = displayScale;
            this.defaultValue = config.getDefault();
        }

        @Override
        public void render(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int x, int y, int width, int mouseX, int mouseY) {
            // Label
            graphics.drawString(font, label, x, y + 2, DesignTokens.Text.PRIMARY, false);

            // Slider track
            int sliderX = x + 140;
            int sliderW = width - 200;
            int sliderY = y + 5;
            int sliderH = 10;

            graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, DesignTokens.Surface.LEVEL_0);

            // Slider fill
            double value = config.get();
            double ratio = (value - min) / (max - min);
            int fillW = (int) (ratio * sliderW);
            graphics.fill(sliderX, sliderY, sliderX + fillW, sliderY + sliderH, COLOR_BLUE);

            // Slider handle
            int handleX = sliderX + fillW - 3;
            graphics.fill(handleX, sliderY - 2, handleX + 6, sliderY + sliderH + 2, DesignTokens.Text.WHITE);

            // Value display
            String valueStr = String.format(format, value * displayScale);
            graphics.drawString(font, valueStr, sliderX + sliderW + 8, y + 2, DesignTokens.Text.SECONDARY, false);
        }

        @Override
        public int getHeight() {
            return SLIDER_HEIGHT;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            int sliderX = baseX + 140;
            int sliderW = baseW - 200;
            int sliderY = baseY + 5;

            if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= sliderY - 2 && mouseY <= sliderY + 14) {
                dragging = true;
                updateValue(mouseX, sliderX, sliderW);
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased() {
            dragging = false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            if (dragging) {
                int sliderX = baseX + 140;
                int sliderW = baseW - 200;
                updateValue(mouseX, sliderX, sliderW);
                return true;
            }
            return false;
        }

        private void updateValue(double mouseX, int sliderX, int sliderW) {
            double ratio = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
            double newValue = min + ratio * (max - min);
            config.set(newValue);
        }

        @Override
        public String getConfigKey() {
            return configKey;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public Object getCurrentValue() {
            return config.get();
        }

        @Override
        public void resetToDefault() {
            config.set(defaultValue);
        }

        @Override
        public boolean isModified() {
            return Math.abs(config.get() - defaultValue) > 0.0001;
        }

        @Override
        public String getValueType() {
            return "double";
        }

        @Override
        public String getCurrentValueAsString() {
            return String.valueOf(config.get());
        }
    }

    private static class IntSliderItem implements ConfigItem {
        final String label;
        final String configKey;
        final ModConfigSpec.IntValue config;
        final int min, max;
        final int defaultValue;
        boolean dragging = false;

        IntSliderItem(String label, ModConfigSpec.IntValue config, int min, int max) {
            this.label = label;
            this.configKey = normalizeConfigKey(config.getPath());
            this.config = config;
            this.min = min;
            this.max = max;
            this.defaultValue = config.getDefault();
        }

        @Override
        public void render(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int x, int y, int width, int mouseX, int mouseY) {
            // Label
            graphics.drawString(font, label, x, y + 2, DesignTokens.Text.PRIMARY, false);

            // Slider track
            int sliderX = x + 140;
            int sliderW = width - 200;
            int sliderY = y + 5;
            int sliderH = 10;

            graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, DesignTokens.Surface.LEVEL_0);

            // Slider fill
            int value = config.get();
            double ratio = (double) (value - min) / (max - min);
            int fillW = (int) (ratio * sliderW);
            graphics.fill(sliderX, sliderY, sliderX + fillW, sliderY + sliderH, COLOR_BLUE);

            // Slider handle
            int handleX = sliderX + fillW - 3;
            graphics.fill(handleX, sliderY - 2, handleX + 6, sliderY + sliderH + 2, DesignTokens.Text.WHITE);

            // Value display
            graphics.drawString(font, String.valueOf(value), sliderX + sliderW + 8, y + 2, DesignTokens.Text.SECONDARY, false);
        }

        @Override
        public int getHeight() {
            return SLIDER_HEIGHT;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            int sliderX = baseX + 140;
            int sliderW = baseW - 200;
            int sliderY = baseY + 5;

            if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= sliderY - 2 && mouseY <= sliderY + 14) {
                dragging = true;
                updateValue(mouseX, sliderX, sliderW);
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased() {
            dragging = false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            if (dragging) {
                int sliderX = baseX + 140;
                int sliderW = baseW - 200;
                updateValue(mouseX, sliderX, sliderW);
                return true;
            }
            return false;
        }

        private void updateValue(double mouseX, int sliderX, int sliderW) {
            double ratio = Math.max(0, Math.min(1, (mouseX - sliderX) / sliderW));
            int newValue = (int) Math.round(min + ratio * (max - min));
            config.set(newValue);
        }

        @Override
        public String getConfigKey() {
            return configKey;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public Object getCurrentValue() {
            return config.get();
        }

        @Override
        public void resetToDefault() {
            config.set(defaultValue);
        }

        @Override
        public boolean isModified() {
            return !config.get().equals(defaultValue);
        }

        @Override
        public String getValueType() {
            return "int";
        }

        @Override
        public String getCurrentValueAsString() {
            return String.valueOf(config.get());
        }
    }

    private static class ToggleItem implements ConfigItem {
        final String label;
        final String configKey;
        final ModConfigSpec.BooleanValue config;
        final boolean defaultValue;

        ToggleItem(String label, ModConfigSpec.BooleanValue config) {
            this.label = label;
            this.configKey = normalizeConfigKey(config.getPath());
            this.config = config;
            this.defaultValue = config.getDefault();
        }

        @Override
        public void render(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int x, int y, int width, int mouseX, int mouseY) {
            // Label
            graphics.drawString(font, label, x, y + 2, DesignTokens.Text.PRIMARY, false);

            // Toggle box
            int toggleX = x + 140;
            int toggleY = y + 3;
            int toggleW = 40;
            int toggleH = 14;

            boolean enabled = config.get();
            int bgColor = enabled ? COLOR_GREEN : DesignTokens.Surface.LEVEL_0;
            graphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, bgColor);

            // Toggle indicator
            int indicatorX = enabled ? toggleX + toggleW - 16 : toggleX + 2;
            graphics.fill(indicatorX, toggleY + 2, indicatorX + 14, toggleY + toggleH - 2, DesignTokens.Text.WHITE);

            // Status text
            String status = enabled
                ? I18n.translate("devmod.overlay.on").getString()
                : I18n.translate("devmod.overlay.off").getString();
            graphics.drawString(font, status, toggleX + toggleW + 8, y + 2, DesignTokens.Text.SECONDARY, false);
        }

        @Override
        public int getHeight() {
            return SLIDER_HEIGHT;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            int toggleX = baseX + 140;
            int toggleY = baseY + 3;
            int toggleW = 40;
            int toggleH = 14;

            if (mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= toggleY && mouseY <= toggleY + toggleH) {
                config.set(!config.get());
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased() {
            // No-op for toggle
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            return false;
        }

        @Override
        public String getConfigKey() {
            return configKey;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public Object getCurrentValue() {
            return config.get();
        }

        @Override
        public void resetToDefault() {
            config.set(defaultValue);
        }

        @Override
        public boolean isModified() {
            return !config.get().equals(defaultValue);
        }

        @Override
        public String getValueType() {
            return "boolean";
        }

        @Override
        public String getCurrentValueAsString() {
            return String.valueOf(config.get());
        }
    }

    /**
     * A button item that executes an action when clicked.
     * Used for opening sub-screens or triggering actions.
     */
    private static class ButtonItem implements ConfigItem {
        @Nonnull final String label;
        @Nonnull final Runnable onClick;

        ButtonItem(@Nonnull String label, @Nonnull Runnable onClick) {
            this.label = label;
            this.onClick = onClick;
        }

        @Override
        public void render(GuiGraphics graphics, @Nonnull net.minecraft.client.gui.Font font, int x, int y, int width, int mouseX, int mouseY) {
            int btnX = x;
            int btnY = y;
            int btnW = Math.min(200, width - 20);
            int btnH = 22;

            boolean hovered = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            // Button background
            int bgColor = hovered ? DesignTokens.Surface.LEVEL_2 : DesignTokens.Surface.LEVEL_1;
            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgColor);
            graphics.renderOutline(btnX, btnY, btnW, btnH, hovered ? DesignTokens.Accent.PRIMARY : DesignTokens.Border.DEFAULT);

            // Label (centered)
            int textX = btnX + (btnW - font.width(label)) / 2;
            int textColor = hovered ? DesignTokens.Text.WHITE : DesignTokens.Text.PRIMARY;
            graphics.drawString(font, label, textX, btnY + 7, textColor, false);
        }

        @Override
        public int getHeight() {
            return 26; // Slightly taller than sliders
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            int btnX = baseX;
            int btnY = baseY;
            int btnW = Math.min(200, baseW - 20);
            int btnH = 22;

            if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                onClick.run();
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased() {
            // No-op
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int baseX, int baseY, int baseW) {
            return false;
        }

        @Override
        public String getConfigKey() {
            return "button_" + label.toLowerCase(Locale.ROOT).replace(" ", "_");
        }

        @Override
        @Nullable
        public Object getDefaultValue() {
            return null;
        }

        @Override
        @Nullable
        public Object getCurrentValue() {
            return null;
        }

        @Override
        public void resetToDefault() {
            // No-op - buttons don't have values
        }

        @Override
        public boolean isModified() {
            return false; // Buttons are never "modified"
        }

        @Override
        public String getValueType() {
            return "button";
        }

        @Override
        public String getCurrentValueAsString() {
            return "";
        }
    }
}
