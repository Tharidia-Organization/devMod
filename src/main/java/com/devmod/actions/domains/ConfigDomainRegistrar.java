package com.devmod.actions.domains;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.DevMod;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionType;
import com.devmod.actions.catalog.ActionSpec;
import com.devmod.actions.catalog.ActionSpec.ActionChannel;
import com.devmod.client.overlay.ImpactHudController;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.overlay.ImpactHudPresets;
import com.devmod.config.Config;
import com.devmod.telemetry.TelemetryService;
import com.devmod.util.I18n;

/**
 * V2 domain registrar for config actions (config toggles, VFX settings,
 * game design config, telemetry exports).
 *
 * <p>Parallel to the existing {@link com.devmod.actions.client.ClientConfigActions}
 * registration. Does NOT replace it yet.
 *
 * <p>Client-only: guarded by {@code FMLEnvironment.dist.isClient()} in
 * {@link DomainRegistryBootstrap#createAllRegistrars()}.
 */
@OnlyIn(Dist.CLIENT)
public final class ConfigDomainRegistrar implements DomainRegistrar {

    @Override
    public String domainName() {
        return "config";
    }

    @Override
    public List<ActionSpec> getActionSpecs() {
        List<ActionSpec> specs = new ArrayList<>();

        // --- Basic config toggles ---
        specs.add(toggle(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.combat.bodyPartDetection", "devmod.configuration.combat.bodyPartDetection.tooltip",
            "minecraft:target", "Root/Config/Combat/Body Part Detection"));
        specs.add(toggle(ActionIds.CONFIG_TELEMETRY_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.telemetry.enabled", "devmod.configuration.telemetry.enabled.tooltip",
            "minecraft:writable_book", "Root/Config/Telemetry/Recording"));
        specs.add(toggle(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.telemetry.logHits", "devmod.configuration.telemetry.logHits.tooltip",
            "minecraft:iron_sword", "Root/Config/Telemetry/Hits"));
        specs.add(toggle(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.telemetry.logDeaths", "devmod.configuration.telemetry.logDeaths.tooltip",
            "minecraft:skeleton_skull", "Root/Config/Telemetry/Deaths"));
        specs.add(toggle(ActionIds.CONFIG_TELEMETRY_SPAWNS_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.telemetry.logSpawns", "devmod.configuration.telemetry.logSpawns.tooltip",
            "minecraft:egg", "Root/Config/Telemetry/Spawns"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactHudHistoryEnabled", "devmod.configuration.debug.impactHudHistoryEnabled.tooltip",
            "minecraft:clock", "Root/Config/HUD/Impact HUD/History"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactHudDpsEnabled", "devmod.configuration.debug.impactHudDpsEnabled.tooltip",
            "minecraft:redstone", "Root/Config/HUD/Impact HUD/DPS"));

        // --- HUD position ---
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_LEFT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.position.top_left", "devmod.config.impact_hud.position.top_left.desc",
            "minecraft:map", "Root/Config/HUD/Impact HUD/Position/Top Left"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.position.top_right", "devmod.config.impact_hud.position.top_right.desc",
            "minecraft:map", "Root/Config/HUD/Impact HUD/Position/Top Right"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.position.center_left", "devmod.config.impact_hud.position.center_left.desc",
            "minecraft:map", "Root/Config/HUD/Impact HUD/Position/Center Left"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.position.center_right", "devmod.config.impact_hud.position.center_right.desc",
            "minecraft:map", "Root/Config/HUD/Impact HUD/Position/Center Right"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.position.bottom_left", "devmod.config.impact_hud.position.bottom_left.desc",
            "minecraft:map", "Root/Config/HUD/Impact HUD/Position/Bottom Left"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.position.bottom_right", "devmod.config.impact_hud.position.bottom_right.desc",
            "minecraft:map", "Root/Config/HUD/Impact HUD/Position/Bottom Right"));

        // --- HUD offsets ---
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.offset_x.minus", "devmod.config.impact_hud.offset_x.minus.desc",
            "minecraft:arrow", "Root/Config/HUD/Impact HUD/Offset/X-"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.offset_x.plus", "devmod.config.impact_hud.offset_x.plus.desc",
            "minecraft:arrow", "Root/Config/HUD/Impact HUD/Offset/X+"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.offset_y.minus", "devmod.config.impact_hud.offset_y.minus.desc",
            "minecraft:arrow", "Root/Config/HUD/Impact HUD/Offset/Y-"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_hud.offset_y.plus", "devmod.config.impact_hud.offset_y.plus.desc",
            "minecraft:arrow", "Root/Config/HUD/Impact HUD/Offset/Y+"));

        // --- HUD presets ---
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.action.config.impact_hud.export", "devmod.action.config.impact_hud.export.desc",
            "minecraft:writable_book", "Root/Config/HUD/Presets/Export"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.action.config.impact_hud.import", "devmod.action.config.impact_hud.import.desc",
            "minecraft:book", "Root/Config/HUD/Presets/Import"));
        specs.add(action(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.action.config.impact_hud.reset", "devmod.action.config.impact_hud.reset.desc",
            "minecraft:barrier", "Root/Config/HUD/Reset Defaults"));

        // --- VFX toggles ---
        specs.add(toggle(ActionIds.CONFIG_IMPACT_VFX_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactVfxEnabled", "devmod.configuration.debug.impactVfxEnabled.tooltip",
            "minecraft:blaze_powder", "Root/Config/Effects/Impact VFX/Enabled"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactVfxVortexEnabled", "devmod.configuration.debug.impactVfxVortexEnabled.tooltip",
            "minecraft:ender_eye", "Root/Config/Effects/Impact VFX/Vortex"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactVfxSlashEnabled", "devmod.configuration.debug.impactVfxSlashEnabled.tooltip",
            "minecraft:iron_sword", "Root/Config/Effects/Impact VFX/Slash"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactVfxLinesEnabled", "devmod.configuration.debug.impactVfxLinesEnabled.tooltip",
            "minecraft:string", "Root/Config/Effects/Impact VFX/Lines"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactVfxGlyphsEnabled", "devmod.configuration.debug.impactVfxGlyphsEnabled.tooltip",
            "minecraft:amethyst_shard", "Root/Config/Effects/Impact VFX/Glyphs"));
        specs.add(toggle(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_EXCLUSIVE_TOGGLE, ActionCategory.CONFIG,
            "devmod.configuration.debug.impactVfxGlyphsExclusive", "devmod.configuration.debug.impactVfxGlyphsExclusive.tooltip",
            "minecraft:tinted_glass", "Root/Config/Effects/Impact VFX/Glyphs Exclusive"));

        // --- VFX presets ---
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.action.config.impact_vfx.preset.combat_language", "devmod.action.config.impact_vfx.preset.combat_language.desc",
            "minecraft:amethyst_shard", "Root/Config/Effects/Impact VFX/Presets/Combat Language"));
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE_LINES, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.action.config.impact_vfx.preset.combat_language_lines", "devmod.action.config.impact_vfx.preset.combat_language_lines.desc",
            "minecraft:glowstone_dust", "Root/Config/Effects/Impact VFX/Presets/Combat Language + Lines"));

        // --- VFX intensity ---
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_vfx.intensity.low", "devmod.config.impact_vfx.intensity.low.desc",
            "minecraft:glowstone_dust", "Root/Config/Effects/Impact VFX/Intensity/Low"));
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_vfx.intensity.med", "devmod.config.impact_vfx.intensity.med.desc",
            "minecraft:glowstone_dust", "Root/Config/Effects/Impact VFX/Intensity/Normal"));
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_vfx.intensity.high", "devmod.config.impact_vfx.intensity.high.desc",
            "minecraft:glowstone_dust", "Root/Config/Effects/Impact VFX/Intensity/High"));
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.config.impact_vfx.intensity.max", "devmod.config.impact_vfx.intensity.max.desc",
            "minecraft:glowstone_dust", "Root/Config/Effects/Impact VFX/Intensity/Max"));
        specs.add(action(ActionIds.CONFIG_IMPACT_VFX_RESET_DEFAULTS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.action.config.impact_vfx.reset", "devmod.action.config.impact_vfx.reset.desc",
            "minecraft:barrier", "Root/Config/Effects/Reset Defaults"));

        // --- Misc config ---
        specs.add(toggle(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE, ActionCategory.CONFIG,
            "devmod.config.screen_shake.toggle", "devmod.config.screen_shake.toggle.desc",
            "minecraft:note_block", "Root/Config/Effects/Screen Shake"));
        specs.add(toggle(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE, ActionCategory.CONFIG,
            "devmod.config.projectile_trails.toggle", "devmod.config.projectile_trails.toggle.desc",
            "minecraft:spectral_arrow", "Root/Config/Effects/Projectile Trails"));
        specs.add(toggle(ActionIds.CONFIG_BADGE_POPUPS_TOGGLE, ActionCategory.CONFIG,
            "devmod.config.badge_popups.toggle", "devmod.config.badge_popups.toggle.desc",
            "minecraft:name_tag", "Root/Config/Effects/Badge Popups"));

        // --- Game Design Config ---
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_RELOAD, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.reload", "devmod.gamedesign.reload.desc",
            "minecraft:compass", "Root/Config/Game Design/Reload"));
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_SAVE, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.save", "devmod.gamedesign.save.desc",
            "minecraft:writable_book", "Root/Config/Game Design/Save"));
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_RESET, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.reset", "devmod.gamedesign.reset.desc",
            "minecraft:barrier", "Root/Config/Game Design/Reset"));
        specs.add(toggle(ActionIds.CONFIG_RESONANCE_TOGGLE, ActionCategory.CONFIG,
            "devmod.gamedesign.resonance.toggle", "devmod.gamedesign.resonance.toggle.desc",
            "minecraft:lightning_rod", "Root/Config/Game Design/Systems/Resonance"));
        specs.add(toggle(ActionIds.CONFIG_CONTRACTS_TOGGLE, ActionCategory.CONFIG,
            "devmod.gamedesign.contracts.toggle", "devmod.gamedesign.contracts.toggle.desc",
            "minecraft:paper", "Root/Config/Game Design/Systems/Contracts"));
        specs.add(toggle(ActionIds.CONFIG_SIGNATURE_WEAPONS_TOGGLE, ActionCategory.CONFIG,
            "devmod.gamedesign.signature_weapons.toggle", "devmod.gamedesign.signature_weapons.toggle.desc",
            "minecraft:diamond_sword", "Root/Config/Game Design/Systems/Signature Weapons"));
        specs.add(toggle(ActionIds.CONFIG_NEMESIS_TOGGLE, ActionCategory.CONFIG,
            "devmod.gamedesign.nemesis.toggle", "devmod.gamedesign.nemesis.toggle.desc",
            "minecraft:wither_skeleton_skull", "Root/Config/Game Design/Systems/Nemesis"));
        specs.add(toggle(ActionIds.CONFIG_TIDE_TOGGLE, ActionCategory.CONFIG,
            "devmod.gamedesign.tide.toggle", "devmod.gamedesign.tide.toggle.desc",
            "minecraft:prismarine_shard", "Root/Config/Game Design/Systems/Tide"));

        // --- Game Design Presets ---
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_PRESET_EASY, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.preset.easy", "devmod.gamedesign.preset.easy.desc",
            "minecraft:golden_apple", "Root/Config/Game Design/Presets/Easy"));
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_PRESET_HARD, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.preset.hard", "devmod.gamedesign.preset.hard.desc",
            "minecraft:netherite_sword", "Root/Config/Game Design/Presets/Hard"));
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_PRESET_CHAOS, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.preset.chaos", "devmod.gamedesign.preset.chaos.desc",
            "minecraft:tnt", "Root/Config/Game Design/Presets/Chaos"));
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_PRESET_TUTORIAL, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.preset.tutorial", "devmod.gamedesign.preset.tutorial.desc",
            "minecraft:book", "Root/Config/Game Design/Presets/Tutorial"));
        specs.add(action(ActionIds.CONFIG_GAMEDESIGN_PRESET_SPEEDRUN, ActionCategory.CONFIG, ActionType.TRIGGER_EVENT,
            "devmod.gamedesign.preset.speedrun", "devmod.gamedesign.preset.speedrun.desc",
            "minecraft:clock", "Root/Config/Game Design/Presets/Speedrun"));

        // --- Telemetry exports ---
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.death", "devmod.action.telemetry.export.heatmap.death.desc",
            "minecraft:skeleton_skull", "Root/Telemetry/Export/Heatmaps/Death"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.movement", "devmod.action.telemetry.export.heatmap.movement.desc",
            "minecraft:feather", "Root/Telemetry/Export/Heatmaps/Movement"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.camping", "devmod.action.telemetry.export.heatmap.camping.desc",
            "minecraft:campfire", "Root/Telemetry/Export/Heatmaps/Camping"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.stuck", "devmod.action.telemetry.export.heatmap.stuck.desc",
            "minecraft:cobweb", "Root/Telemetry/Export/Heatmaps/Stuck"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.aggro_drop", "devmod.action.telemetry.export.heatmap.aggro_drop.desc",
            "minecraft:ender_eye", "Root/Telemetry/Export/Heatmaps/Aggro Drop"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.kiting", "devmod.action.telemetry.export.heatmap.kiting.desc",
            "minecraft:bow", "Root/Telemetry/Export/Heatmaps/Kiting"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.choke_points", "devmod.action.telemetry.export.heatmap.choke_points.desc",
            "minecraft:chain", "Root/Telemetry/Export/Heatmaps/Choke Points"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.heatmap.parkour_falls", "devmod.action.telemetry.export.heatmap.parkour_falls.desc",
            "minecraft:honey_bottle", "Root/Telemetry/Export/Heatmaps/Parkour Falls"));
        specs.add(action(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS, ActionCategory.TELEMETRY, ActionType.TRIGGER_EVENT,
            "devmod.action.telemetry.export.damage_stats", "devmod.action.telemetry.export.damage_stats.desc",
            "minecraft:writable_book", "Root/Telemetry/Export/Damage Stats"));

        return specs;
    }

    @Override
    public void registerHandlers(HandlerRegistry registry) {
        // Basic config toggles
        registry.register(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE, ctx ->
            Config.BODY_PART_DETECTION_ENABLED.set(!Config.BODY_PART_DETECTION_ENABLED.get()));
        registry.register(ActionIds.CONFIG_TELEMETRY_TOGGLE, ctx ->
            Config.TELEMETRY_ENABLED.set(!Config.TELEMETRY_ENABLED.get()));
        registry.register(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE, ctx ->
            Config.TELEMETRY_HITS_ENABLED.set(!Config.TELEMETRY_HITS_ENABLED.get()));
        registry.register(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE, ctx ->
            Config.TELEMETRY_DEATHS_ENABLED.set(!Config.TELEMETRY_DEATHS_ENABLED.get()));
        registry.register(ActionIds.CONFIG_TELEMETRY_SPAWNS_TOGGLE, ctx ->
            Config.TELEMETRY_SPAWNS_ENABLED.set(!Config.TELEMETRY_SPAWNS_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE, ctx ->
            Config.IMPACT_HUD_HISTORY_ENABLED.set(!Config.IMPACT_HUD_HISTORY_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE, ctx ->
            Config.IMPACT_HUD_DPS_ENABLED.set(!Config.IMPACT_HUD_DPS_ENABLED.get()));

        // HUD position
        registry.register(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_LEFT, ctx -> setHudPosition(Config.HudPosition.TOP_LEFT));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT, ctx -> setHudPosition(Config.HudPosition.TOP_RIGHT));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT, ctx -> setHudPosition(Config.HudPosition.CENTER_LEFT));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT, ctx -> setHudPosition(Config.HudPosition.CENTER_RIGHT));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT, ctx -> setHudPosition(Config.HudPosition.BOTTOM_LEFT));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT, ctx -> setHudPosition(Config.HudPosition.BOTTOM_RIGHT));

        // HUD offsets
        registry.register(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS, ctx -> adjustHudOffset(true, -10));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS, ctx -> adjustHudOffset(true, 10));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS, ctx -> adjustHudOffset(false, -10));
        registry.register(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS, ctx -> adjustHudOffset(false, 10));

        // HUD presets
        registry.register(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT, ctx -> {
            var path = ImpactHudPresets.getDefaultPresetFile();
            boolean success = ImpactHudPresets.exportToDefault();
            if (success) {
                ctx.sendSuccess(I18n.translate("devmod.testing.impact_hud_preset_exported", path.toString()), true);
            } else {
                ctx.sendFailure(I18n.translate("devmod.testing.impact_hud_preset_export_failed", path.toString()));
            }
        });
        registry.register(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT, ctx -> {
            var path = ImpactHudPresets.getDefaultPresetFile();
            boolean success = ImpactHudPresets.importFromDefault();
            if (success) {
                ImpactHudOverlay.setEnabled(Config.IMPACT_HUD_ENABLED.get());
                ctx.sendSuccess(I18n.translate("devmod.testing.impact_hud_preset_imported", path.toString()), true);
            } else {
                ctx.sendFailure(I18n.translate("devmod.testing.impact_hud_preset_import_failed", path.toString()));
            }
        });
        registry.register(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS, ctx -> resetImpactHudDefaults());

        // VFX toggles
        registry.register(ActionIds.CONFIG_IMPACT_VFX_TOGGLE, ctx ->
            Config.IMPACT_VFX_ENABLED.set(!Config.IMPACT_VFX_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE, ctx ->
            Config.IMPACT_VFX_VORTEX_ENABLED.set(!Config.IMPACT_VFX_VORTEX_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE, ctx ->
            Config.IMPACT_VFX_SLASH_ENABLED.set(!Config.IMPACT_VFX_SLASH_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE, ctx ->
            Config.IMPACT_VFX_LINES_ENABLED.set(!Config.IMPACT_VFX_LINES_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_TOGGLE, ctx ->
            Config.IMPACT_VFX_GLYPHS_ENABLED.set(!Config.IMPACT_VFX_GLYPHS_ENABLED.get()));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_EXCLUSIVE_TOGGLE, ctx ->
            Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.set(!Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.get()));

        // VFX presets
        registry.register(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE, ctx -> {
            applyCombatLanguageVfxPreset();
            ctx.sendSuccess(I18n.translate("devmod.impact_vfx.preset_applied", "Combat Language"), true);
        });
        registry.register(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE_LINES, ctx -> {
            applyCombatLanguageLinesVfxPreset();
            ctx.sendSuccess(I18n.translate("devmod.impact_vfx.preset_applied", "Combat Language + Lines"), true);
        });

        // VFX intensity
        registry.register(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW, ctx -> setVfxIntensity(0.5));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED, ctx -> setVfxIntensity(1.0));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH, ctx -> setVfxIntensity(1.5));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX, ctx -> setVfxIntensity(2.0));
        registry.register(ActionIds.CONFIG_IMPACT_VFX_RESET_DEFAULTS, ctx -> resetImpactVfxDefaults());

        // Misc config
        registry.register(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE, ctx ->
            Config.SCREEN_SHAKE_ENABLED.set(!Config.SCREEN_SHAKE_ENABLED.get()));
        registry.register(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE, ctx ->
            Config.PROJECTILE_TRAILS_ENABLED.set(!Config.PROJECTILE_TRAILS_ENABLED.get()));
        registry.register(ActionIds.CONFIG_BADGE_POPUPS_TOGGLE, ctx ->
            Config.BADGE_POPUP_ENABLED.set(!Config.BADGE_POPUP_ENABLED.get()));

        // Game Design Config
        var configMgr = com.devmod.config.gamedesign.GameDesignConfigManager.INSTANCE;
        registry.register(ActionIds.CONFIG_GAMEDESIGN_RELOAD, ctx -> {
            configMgr.reload();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.reloaded"), true);
        });
        registry.register(ActionIds.CONFIG_GAMEDESIGN_SAVE, ctx -> {
            configMgr.save();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.saved"), true);
        });
        registry.register(ActionIds.CONFIG_GAMEDESIGN_RESET, ctx -> {
            configMgr.resetToDefaults();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.reset_done"), true);
        });
        registry.register(ActionIds.CONFIG_RESONANCE_TOGGLE, ctx -> {
            var resonance = configMgr.getGlobalConfig().getResonance();
            resonance.enabled = !resonance.enabled;
            configMgr.markDirty();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.resonance." + (resonance.enabled ? "enabled" : "disabled")), true);
        });
        registry.register(ActionIds.CONFIG_CONTRACTS_TOGGLE, ctx -> {
            var contracts = configMgr.getGlobalConfig().getContracts();
            contracts.enabled = !contracts.enabled;
            configMgr.markDirty();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.contracts." + (contracts.enabled ? "enabled" : "disabled")), true);
        });
        registry.register(ActionIds.CONFIG_SIGNATURE_WEAPONS_TOGGLE, ctx -> {
            var sw = configMgr.getGlobalConfig().getSignatureWeapons();
            sw.enabled = !sw.enabled;
            configMgr.markDirty();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.signature_weapons." + (sw.enabled ? "enabled" : "disabled")), true);
        });
        registry.register(ActionIds.CONFIG_NEMESIS_TOGGLE, ctx -> {
            var nemesis = configMgr.getGlobalConfig().getNemesis();
            nemesis.enabled = !nemesis.enabled;
            configMgr.markDirty();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.nemesis." + (nemesis.enabled ? "enabled" : "disabled")), true);
        });
        registry.register(ActionIds.CONFIG_TIDE_TOGGLE, ctx -> {
            var tide = configMgr.getGlobalConfig().getTide();
            tide.enabled = !tide.enabled;
            configMgr.markDirty();
            ctx.sendSuccess(I18n.translate("devmod.gamedesign.tide." + (tide.enabled ? "enabled" : "disabled")), true);
        });

        // Game Design Presets
        registry.register(ActionIds.CONFIG_GAMEDESIGN_PRESET_EASY, ctx -> applyGameDesignPreset(configMgr, ctx, "easy", "Easy"));
        registry.register(ActionIds.CONFIG_GAMEDESIGN_PRESET_HARD, ctx -> applyGameDesignPreset(configMgr, ctx, "hard", "Hard"));
        registry.register(ActionIds.CONFIG_GAMEDESIGN_PRESET_CHAOS, ctx -> applyGameDesignPreset(configMgr, ctx, "chaos", "Chaos"));
        registry.register(ActionIds.CONFIG_GAMEDESIGN_PRESET_TUTORIAL, ctx -> applyGameDesignPreset(configMgr, ctx, "tutorial", "Tutorial"));
        registry.register(ActionIds.CONFIG_GAMEDESIGN_PRESET_SPEEDRUN, ctx -> applyGameDesignPreset(configMgr, ctx, "speedrun", "Speedrun"));

        // Telemetry exports
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportDeathHeatmap, "devmod.telemetry.exported_heatmap.death"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportMovementHeatmap, "devmod.telemetry.exported_heatmap.movement"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportCampingHeatmap, "devmod.telemetry.exported_heatmap.camping"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportStuckHeatmap, "devmod.telemetry.exported_heatmap.stuck"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportAggroDropHeatmap, "devmod.telemetry.exported_heatmap.aggro_drop"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportKitingHeatmap, "devmod.telemetry.exported_heatmap.kiting"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportChokePointHeatmap, "devmod.telemetry.exported_heatmap.choke_points"));
        registry.register(ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportParkourFallHeatmap, "devmod.telemetry.exported_heatmap.parkour_falls"));
        registry.register(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS, ctx ->
            exportTelemetry(ctx, TelemetryService.INSTANCE::exportDamageStats, "devmod.telemetry.exported_damage_stats"));
    }

    // ── Spec helpers ──

    private static ActionSpec toggle(String id, ActionCategory category,
                                      String labelKey, String descKey,
                                      String icon, String menuPath) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(category)
            .actionType(ActionType.TOGGLE_SETTING)
            .ui(labelKey, descKey, icon, menuPath, true)
            .handlerRef(id)
            .build();
    }

    private static ActionSpec action(String id, ActionCategory category, ActionType type,
                                      String labelKey, String descKey,
                                      String icon, String menuPath) {
        return ActionSpec.builder(id)
            .channel(ActionChannel.CLIENT)
            .category(category)
            .actionType(type)
            .ui(labelKey, descKey, icon, menuPath, false)
            .handlerRef(id)
            .build();
    }

    // ── Private helpers ──

    private static void applyGameDesignPreset(
            com.devmod.config.gamedesign.GameDesignConfigManager configMgr,
            ActionContext context, String presetId, String displayName) {
        var override = com.devmod.config.gamedesign.InstanceOverride.fromPreset(presetId);
        configMgr.setGlobalConfig(Objects.requireNonNull(override.applyTo(configMgr.getGlobalConfig().copy())));
        configMgr.markDirty();
        context.sendSuccess(I18n.translate("devmod.gamedesign.preset_applied", displayName), true);
    }

    private static void setHudPosition(Config.HudPosition position) {
        if (position == null) return;
        try { Config.IMPACT_HUD_POSITION.set(position); }
        catch (Exception e) { DevMod.LOGGER.debug("[DevMod] Failed to set HUD position {}", position, e); }
    }

    private static void adjustHudOffset(boolean isX, int delta) {
        try {
            if (isX) {
                int current = Config.IMPACT_HUD_OFFSET_X.get();
                Config.IMPACT_HUD_OFFSET_X.set(Math.max(0, Math.min(200, current + delta)));
            } else {
                int current = Config.IMPACT_HUD_OFFSET_Y.get();
                Config.IMPACT_HUD_OFFSET_Y.set(Math.max(0, Math.min(200, current + delta)));
            }
        } catch (Exception e) {
            DevMod.LOGGER.debug("[DevMod] Failed to adjust HUD offset (isX={}, delta={})", isX, delta, e);
        }
    }

    private static void setVfxIntensity(double value) {
        try { Config.IMPACT_VFX_INTENSITY.set(value); }
        catch (Exception e) { DevMod.LOGGER.debug("[DevMod] Failed to set VFX intensity {}", value, e); }
    }

    private static void resetImpactHudDefaults() {
        Config.IMPACT_HUD_ENABLED.set(true);
        Config.IMPACT_HUD_POSITION.set(Config.HudPosition.TOP_RIGHT);
        Config.IMPACT_HUD_OFFSET_X.set(10);
        Config.IMPACT_HUD_OFFSET_Y.set(10);
        Config.IMPACT_HUD_HISTORY_ENABLED.set(true);
        Config.IMPACT_HUD_DPS_ENABLED.set(true);
        ImpactHudOverlay.setEnabled(true);
        ImpactHudController.INSTANCE.set3dPanelsEnabled(true);
    }

    private static void resetImpactVfxDefaults() {
        Config.IMPACT_VFX_ENABLED.set(true);
        Config.IMPACT_VFX_VORTEX_ENABLED.set(true);
        Config.IMPACT_VFX_SLASH_ENABLED.set(true);
        Config.IMPACT_VFX_LINES_ENABLED.set(true);
        Config.IMPACT_VFX_GLYPHS_ENABLED.set(true);
        Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.set(false);
        Config.IMPACT_VFX_INTENSITY.set(1.0);
    }

    private static void applyCombatLanguageVfxPreset() {
        Config.IMPACT_VFX_ENABLED.set(true);
        Config.IMPACT_VFX_GLYPHS_ENABLED.set(true);
        Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.set(true);
        Config.IMPACT_VFX_VORTEX_ENABLED.set(false);
        Config.IMPACT_VFX_SLASH_ENABLED.set(false);
        Config.IMPACT_VFX_LINES_ENABLED.set(false);
        Config.IMPACT_VFX_INTENSITY.set(0.8);
    }

    private static void applyCombatLanguageLinesVfxPreset() {
        Config.IMPACT_VFX_ENABLED.set(true);
        Config.IMPACT_VFX_GLYPHS_ENABLED.set(true);
        Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.set(false);
        Config.IMPACT_VFX_VORTEX_ENABLED.set(false);
        Config.IMPACT_VFX_SLASH_ENABLED.set(false);
        Config.IMPACT_VFX_LINES_ENABLED.set(true);
        Config.IMPACT_VFX_INTENSITY.set(0.9);
    }

    private static void exportTelemetry(ActionContext context, Runnable export, String messageKey) {
        export.run();
        context.sendSuccess(I18n.translate(messageKey), true);
    }
}
