package com.devmod.actions.client;

import java.util.Objects;

import net.minecraft.world.item.Items;

import com.devmod.DevMod;
import com.devmod.actions.ActionCategory;
import com.devmod.actions.ActionContext;
import com.devmod.actions.ActionIds;
import com.devmod.actions.ActionPreconditions;
import com.devmod.actions.ActionRegistry;
import com.devmod.actions.RadialAction;
import com.devmod.client.overlay.ImpactHudController;
import com.devmod.client.overlay.ImpactHudOverlay;
import com.devmod.client.overlay.ImpactHudPresets;
import com.devmod.config.Config;
import com.devmod.telemetry.TelemetryService;
import com.devmod.util.I18n;

/**
 * Config-related client actions: config toggles, HUD position/offset, VFX settings,
 * game design config, telemetry exports.
 */
public final class ClientConfigActions {

    private ClientConfigActions() {}

    // ── Registration ──

    static void registerConfigActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_BODY_PART_DETECTION_TOGGLE)
            .labelKey("devmod.configuration.combat.bodyPartDetection")
            .descriptionKey("devmod.configuration.combat.bodyPartDetection.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Combat/Body Part Detection")
            .icon(Items.TARGET)
            .toggle(context -> Config.BODY_PART_DETECTION_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.BODY_PART_DETECTION_ENABLED.set(!Config.BODY_PART_DETECTION_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_TOGGLE)
            .labelKey("devmod.configuration.telemetry.enabled")
            .descriptionKey("devmod.configuration.telemetry.enabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Recording")
            .icon(Items.WRITABLE_BOOK)
            .toggle(context -> Config.TELEMETRY_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_ENABLED.set(!Config.TELEMETRY_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_HITS_TOGGLE)
            .labelKey("devmod.configuration.telemetry.logHits")
            .descriptionKey("devmod.configuration.telemetry.logHits.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Hits")
            .icon(Items.IRON_SWORD)
            .toggle(context -> Config.TELEMETRY_HITS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_HITS_ENABLED.set(!Config.TELEMETRY_HITS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_DEATHS_TOGGLE)
            .labelKey("devmod.configuration.telemetry.logDeaths")
            .descriptionKey("devmod.configuration.telemetry.logDeaths.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Deaths")
            .icon(Items.SKELETON_SKULL)
            .toggle(context -> Config.TELEMETRY_DEATHS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_DEATHS_ENABLED.set(!Config.TELEMETRY_DEATHS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TELEMETRY_SPAWNS_TOGGLE)
            .labelKey("devmod.configuration.telemetry.logSpawns")
            .descriptionKey("devmod.configuration.telemetry.logSpawns.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Telemetry/Spawns")
            .icon(Items.EGG)
            .toggle(context -> Config.TELEMETRY_SPAWNS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.TELEMETRY_SPAWNS_ENABLED.set(!Config.TELEMETRY_SPAWNS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_HISTORY_TOGGLE)
            .labelKey("devmod.configuration.debug.impactHudHistoryEnabled")
            .descriptionKey("devmod.configuration.debug.impactHudHistoryEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/History")
            .icon(Items.CLOCK)
            .toggle(context -> Config.IMPACT_HUD_HISTORY_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_HUD_HISTORY_ENABLED.set(!Config.IMPACT_HUD_HISTORY_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_DPS_TOGGLE)
            .labelKey("devmod.configuration.debug.impactHudDpsEnabled")
            .descriptionKey("devmod.configuration.debug.impactHudDpsEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/DPS")
            .icon(Items.REDSTONE)
            .toggle(context -> Config.IMPACT_HUD_DPS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_HUD_DPS_ENABLED.set(!Config.IMPACT_HUD_DPS_ENABLED.get()))
            .build());

        // HUD position
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_LEFT)
            .labelKey("devmod.config.impact_hud.position.top_left")
            .descriptionKey("devmod.config.impact_hud.position.top_left.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Top Left")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.TOP_LEFT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_TOP_RIGHT)
            .labelKey("devmod.config.impact_hud.position.top_right")
            .descriptionKey("devmod.config.impact_hud.position.top_right.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Top Right")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.TOP_RIGHT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_LEFT)
            .labelKey("devmod.config.impact_hud.position.center_left")
            .descriptionKey("devmod.config.impact_hud.position.center_left.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Center Left")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.CENTER_LEFT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_CENTER_RIGHT)
            .labelKey("devmod.config.impact_hud.position.center_right")
            .descriptionKey("devmod.config.impact_hud.position.center_right.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Center Right")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.CENTER_RIGHT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_LEFT)
            .labelKey("devmod.config.impact_hud.position.bottom_left")
            .descriptionKey("devmod.config.impact_hud.position.bottom_left.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Bottom Left")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.BOTTOM_LEFT))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_POSITION_BOTTOM_RIGHT)
            .labelKey("devmod.config.impact_hud.position.bottom_right")
            .descriptionKey("devmod.config.impact_hud.position.bottom_right.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Position/Bottom Right")
            .icon(Items.MAP)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setHudPosition(Config.HudPosition.BOTTOM_RIGHT))
            .build());

        // HUD offsets
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_MINUS)
            .labelKey("devmod.config.impact_hud.offset_x.minus")
            .descriptionKey("devmod.config.impact_hud.offset_x.minus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/X-")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(true, -10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_X_PLUS)
            .labelKey("devmod.config.impact_hud.offset_x.plus")
            .descriptionKey("devmod.config.impact_hud.offset_x.plus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/X+")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(true, 10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_MINUS)
            .labelKey("devmod.config.impact_hud.offset_y.minus")
            .descriptionKey("devmod.config.impact_hud.offset_y.minus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/Y-")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(false, -10))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_OFFSET_Y_PLUS)
            .labelKey("devmod.config.impact_hud.offset_y.plus")
            .descriptionKey("devmod.config.impact_hud.offset_y.plus.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Impact HUD/Offset/Y+")
            .icon(Items.ARROW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> adjustHudOffset(false, 10))
            .build());

        // HUD presets
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_PRESET_EXPORT)
            .labelKey("devmod.action.config.impact_hud.export")
            .descriptionKey("devmod.action.config.impact_hud.export.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Presets/Export")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var path = ImpactHudPresets.getDefaultPresetFile();
                boolean success = ImpactHudPresets.exportToDefault();
                if (success) {
                    context.sendSuccess(I18n.translate("devmod.testing.impact_hud_preset_exported", path.toString()), true);
                } else {
                    context.sendFailure(I18n.translate("devmod.testing.impact_hud_preset_export_failed", path.toString()));
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_PRESET_IMPORT)
            .labelKey("devmod.action.config.impact_hud.import")
            .descriptionKey("devmod.action.config.impact_hud.import.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Presets/Import")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var path = ImpactHudPresets.getDefaultPresetFile();
                boolean success = ImpactHudPresets.importFromDefault();
                if (success) {
                    ImpactHudOverlay.setEnabled(Config.IMPACT_HUD_ENABLED.get());
                    context.sendSuccess(I18n.translate("devmod.testing.impact_hud_preset_imported", path.toString()), true);
                } else {
                    context.sendFailure(I18n.translate("devmod.testing.impact_hud_preset_import_failed", path.toString()));
                }
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_HUD_RESET_DEFAULTS)
            .labelKey("devmod.action.config.impact_hud.reset")
            .descriptionKey("devmod.action.config.impact_hud.reset.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/HUD/Reset Defaults")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> resetImpactHudDefaults())
            .build());

        // VFX toggles
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Enabled")
            .icon(Items.BLAZE_POWDER)
            .toggle(context -> Config.IMPACT_VFX_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_ENABLED.set(!Config.IMPACT_VFX_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_VORTEX_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxVortexEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxVortexEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Vortex")
            .icon(Items.ENDER_EYE)
            .toggle(context -> Config.IMPACT_VFX_VORTEX_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_VORTEX_ENABLED.set(!Config.IMPACT_VFX_VORTEX_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_SLASH_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxSlashEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxSlashEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Slash")
            .icon(Items.IRON_SWORD)
            .toggle(context -> Config.IMPACT_VFX_SLASH_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_SLASH_ENABLED.set(!Config.IMPACT_VFX_SLASH_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_LINES_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxLinesEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxLinesEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Lines")
            .icon(Items.STRING)
            .toggle(context -> Config.IMPACT_VFX_LINES_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_LINES_ENABLED.set(!Config.IMPACT_VFX_LINES_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxGlyphsEnabled")
            .descriptionKey("devmod.configuration.debug.impactVfxGlyphsEnabled.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Glyphs")
            .icon(Items.AMETHYST_SHARD)
            .toggle(context -> Config.IMPACT_VFX_GLYPHS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_GLYPHS_ENABLED.set(!Config.IMPACT_VFX_GLYPHS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_GLYPHS_EXCLUSIVE_TOGGLE)
            .labelKey("devmod.configuration.debug.impactVfxGlyphsExclusive")
            .descriptionKey("devmod.configuration.debug.impactVfxGlyphsExclusive.tooltip")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Glyphs Exclusive")
            .icon(Items.TINTED_GLASS)
            .toggle(context -> Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.set(!Config.IMPACT_VFX_GLYPHS_EXCLUSIVE.get()))
            .build());

        // VFX presets
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE)
            .labelKey("devmod.action.config.impact_vfx.preset.combat_language")
            .descriptionKey("devmod.action.config.impact_vfx.preset.combat_language.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Presets/Combat Language")
            .icon(Items.AMETHYST_SHARD)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                applyCombatLanguageVfxPreset();
                context.sendSuccess(I18n.translate("devmod.impact_vfx.preset_applied", "Combat Language"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_PRESET_COMBAT_LANGUAGE_LINES)
            .labelKey("devmod.action.config.impact_vfx.preset.combat_language_lines")
            .descriptionKey("devmod.action.config.impact_vfx.preset.combat_language_lines.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Presets/Combat Language + Lines")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                applyCombatLanguageLinesVfxPreset();
                context.sendSuccess(I18n.translate("devmod.impact_vfx.preset_applied", "Combat Language + Lines"), true);
            })
            .build());

        // VFX intensity
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_LOW)
            .labelKey("devmod.config.impact_vfx.intensity.low")
            .descriptionKey("devmod.config.impact_vfx.intensity.low.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/Low")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(0.5))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MED)
            .labelKey("devmod.config.impact_vfx.intensity.med")
            .descriptionKey("devmod.config.impact_vfx.intensity.med.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/Normal")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(1.0))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_HIGH)
            .labelKey("devmod.config.impact_vfx.intensity.high")
            .descriptionKey("devmod.config.impact_vfx.intensity.high.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/High")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(1.5))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_INTENSITY_MAX)
            .labelKey("devmod.config.impact_vfx.intensity.max")
            .descriptionKey("devmod.config.impact_vfx.intensity.max.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Impact VFX/Intensity/Max")
            .icon(Items.GLOWSTONE_DUST)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> setVfxIntensity(2.0))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_IMPACT_VFX_RESET_DEFAULTS)
            .labelKey("devmod.action.config.impact_vfx.reset")
            .descriptionKey("devmod.action.config.impact_vfx.reset.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Reset Defaults")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> resetImpactVfxDefaults())
            .build());

        // Misc config toggles
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_SCREEN_SHAKE_TOGGLE)
            .labelKey("devmod.config.screen_shake.toggle")
            .descriptionKey("devmod.config.screen_shake.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Screen Shake")
            .icon(Items.NOTE_BLOCK)
            .toggle(context -> Config.SCREEN_SHAKE_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.SCREEN_SHAKE_ENABLED.set(!Config.SCREEN_SHAKE_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_PROJECTILE_TRAILS_TOGGLE)
            .labelKey("devmod.config.projectile_trails.toggle")
            .descriptionKey("devmod.config.projectile_trails.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Projectile Trails")
            .icon(Items.SPECTRAL_ARROW)
            .toggle(context -> Config.PROJECTILE_TRAILS_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.PROJECTILE_TRAILS_ENABLED.set(!Config.PROJECTILE_TRAILS_ENABLED.get()))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_BADGE_POPUPS_TOGGLE)
            .labelKey("devmod.config.badge_popups.toggle")
            .descriptionKey("devmod.config.badge_popups.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Effects/Badge Popups")
            .icon(Items.NAME_TAG)
            .toggle(context -> Config.BADGE_POPUP_ENABLED.get())
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> Config.BADGE_POPUP_ENABLED.set(!Config.BADGE_POPUP_ENABLED.get()))
            .build());

        // Game Design Config
        registerGameDesignConfigActions();
    }

    static void registerTelemetryActions() {
        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_DEATH)
            .labelKey("devmod.action.telemetry.export.heatmap.death")
            .descriptionKey("devmod.action.telemetry.export.heatmap.death.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Death")
            .icon(Items.SKELETON_SKULL)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportDeathHeatmap,
                "devmod.telemetry.exported_heatmap.death"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_MOVEMENT)
            .labelKey("devmod.action.telemetry.export.heatmap.movement")
            .descriptionKey("devmod.action.telemetry.export.heatmap.movement.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Movement")
            .icon(Items.FEATHER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportMovementHeatmap,
                "devmod.telemetry.exported_heatmap.movement"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_CAMPING)
            .labelKey("devmod.action.telemetry.export.heatmap.camping")
            .descriptionKey("devmod.action.telemetry.export.heatmap.camping.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Camping")
            .icon(Items.CAMPFIRE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportCampingHeatmap,
                "devmod.telemetry.exported_heatmap.camping"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_STUCK)
            .labelKey("devmod.action.telemetry.export.heatmap.stuck")
            .descriptionKey("devmod.action.telemetry.export.heatmap.stuck.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Stuck")
            .icon(Items.COBWEB)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportStuckHeatmap,
                "devmod.telemetry.exported_heatmap.stuck"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_AGGRO_DROP)
            .labelKey("devmod.action.telemetry.export.heatmap.aggro_drop")
            .descriptionKey("devmod.action.telemetry.export.heatmap.aggro_drop.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Aggro Drop")
            .icon(Items.ENDER_EYE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportAggroDropHeatmap,
                "devmod.telemetry.exported_heatmap.aggro_drop"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_KITING)
            .labelKey("devmod.action.telemetry.export.heatmap.kiting")
            .descriptionKey("devmod.action.telemetry.export.heatmap.kiting.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Kiting")
            .icon(Items.BOW)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportKitingHeatmap,
                "devmod.telemetry.exported_heatmap.kiting"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_CHOKE_POINTS)
            .labelKey("devmod.action.telemetry.export.heatmap.choke_points")
            .descriptionKey("devmod.action.telemetry.export.heatmap.choke_points.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Choke Points")
            .icon(Items.CHAIN)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportChokePointHeatmap,
                "devmod.telemetry.exported_heatmap.choke_points"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_HEATMAP_PARKOUR_FALLS)
            .labelKey("devmod.action.telemetry.export.heatmap.parkour_falls")
            .descriptionKey("devmod.action.telemetry.export.heatmap.parkour_falls.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Heatmaps/Parkour Falls")
            .icon(Items.HONEY_BOTTLE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportParkourFallHeatmap,
                "devmod.telemetry.exported_heatmap.parkour_falls"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.TELEMETRY_EXPORT_DAMAGE_STATS)
            .labelKey("devmod.action.telemetry.export.damage_stats")
            .descriptionKey("devmod.action.telemetry.export.damage_stats.desc")
            .category(ActionCategory.TELEMETRY)
            .menuPath("Root/Telemetry/Export/Damage Stats")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> exportTelemetry(context, TelemetryService.INSTANCE::exportDamageStats,
                "devmod.telemetry.exported_damage_stats"))
            .build());
    }

    // ── Private helpers ──

    private static void registerGameDesignConfigActions() {
        var configMgr = com.devmod.config.gamedesign.GameDesignConfigManager.INSTANCE;

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_RELOAD)
            .labelKey("devmod.gamedesign.reload")
            .descriptionKey("devmod.gamedesign.reload.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Reload")
            .icon(Items.COMPASS)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                configMgr.reload();
                context.sendSuccess(I18n.translate("devmod.gamedesign.reloaded"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_SAVE)
            .labelKey("devmod.gamedesign.save")
            .descriptionKey("devmod.gamedesign.save.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Save")
            .icon(Items.WRITABLE_BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                configMgr.save();
                context.sendSuccess(I18n.translate("devmod.gamedesign.saved"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_RESET)
            .labelKey("devmod.gamedesign.reset")
            .descriptionKey("devmod.gamedesign.reset.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Reset")
            .icon(Items.BARRIER)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                configMgr.resetToDefaults();
                context.sendSuccess(I18n.translate("devmod.gamedesign.reset_done"), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_RESONANCE_TOGGLE)
            .labelKey("devmod.gamedesign.resonance.toggle")
            .descriptionKey("devmod.gamedesign.resonance.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Systems/Resonance")
            .icon(Items.LIGHTNING_ROD)
            .toggle(context -> configMgr.getGlobalConfig().getResonance().enabled)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var config = configMgr.getGlobalConfig();
                var resonance = config.getResonance();
                resonance.enabled = !resonance.enabled;
                configMgr.markDirty();
                String status = resonance.enabled ? "enabled" : "disabled";
                context.sendSuccess(I18n.translate("devmod.gamedesign.resonance." + status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_CONTRACTS_TOGGLE)
            .labelKey("devmod.gamedesign.contracts.toggle")
            .descriptionKey("devmod.gamedesign.contracts.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Systems/Contracts")
            .icon(Items.PAPER)
            .toggle(context -> configMgr.getGlobalConfig().getContracts().enabled)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var config = configMgr.getGlobalConfig();
                var contracts = config.getContracts();
                contracts.enabled = !contracts.enabled;
                configMgr.markDirty();
                String status = contracts.enabled ? "enabled" : "disabled";
                context.sendSuccess(I18n.translate("devmod.gamedesign.contracts." + status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_SIGNATURE_WEAPONS_TOGGLE)
            .labelKey("devmod.gamedesign.signature_weapons.toggle")
            .descriptionKey("devmod.gamedesign.signature_weapons.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Systems/Signature Weapons")
            .icon(Items.DIAMOND_SWORD)
            .toggle(context -> configMgr.getGlobalConfig().getSignatureWeapons().enabled)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var config = configMgr.getGlobalConfig();
                var signatureWeapons = config.getSignatureWeapons();
                signatureWeapons.enabled = !signatureWeapons.enabled;
                configMgr.markDirty();
                String status = signatureWeapons.enabled ? "enabled" : "disabled";
                context.sendSuccess(I18n.translate("devmod.gamedesign.signature_weapons." + status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_NEMESIS_TOGGLE)
            .labelKey("devmod.gamedesign.nemesis.toggle")
            .descriptionKey("devmod.gamedesign.nemesis.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Systems/Nemesis")
            .icon(Items.WITHER_SKELETON_SKULL)
            .toggle(context -> configMgr.getGlobalConfig().getNemesis().enabled)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var config = configMgr.getGlobalConfig();
                var nemesis = config.getNemesis();
                nemesis.enabled = !nemesis.enabled;
                configMgr.markDirty();
                String status = nemesis.enabled ? "enabled" : "disabled";
                context.sendSuccess(I18n.translate("devmod.gamedesign.nemesis." + status), true);
            })
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_TIDE_TOGGLE)
            .labelKey("devmod.gamedesign.tide.toggle")
            .descriptionKey("devmod.gamedesign.tide.toggle.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Systems/Tide")
            .icon(Items.PRISMARINE_SHARD)
            .toggle(context -> configMgr.getGlobalConfig().getTide().enabled)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> {
                var config = configMgr.getGlobalConfig();
                var tide = config.getTide();
                tide.enabled = !tide.enabled;
                configMgr.markDirty();
                String status = tide.enabled ? "enabled" : "disabled";
                context.sendSuccess(I18n.translate("devmod.gamedesign.tide." + status), true);
            })
            .build());

        // Presets
        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_PRESET_EASY)
            .labelKey("devmod.gamedesign.preset.easy")
            .descriptionKey("devmod.gamedesign.preset.easy.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Presets/Easy")
            .icon(Items.GOLDEN_APPLE)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> applyGameDesignPreset(configMgr, context, "easy", "Easy"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_PRESET_HARD)
            .labelKey("devmod.gamedesign.preset.hard")
            .descriptionKey("devmod.gamedesign.preset.hard.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Presets/Hard")
            .icon(Items.NETHERITE_SWORD)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> applyGameDesignPreset(configMgr, context, "hard", "Hard"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_PRESET_CHAOS)
            .labelKey("devmod.gamedesign.preset.chaos")
            .descriptionKey("devmod.gamedesign.preset.chaos.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Presets/Chaos")
            .icon(Items.TNT)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> applyGameDesignPreset(configMgr, context, "chaos", "Chaos"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_PRESET_TUTORIAL)
            .labelKey("devmod.gamedesign.preset.tutorial")
            .descriptionKey("devmod.gamedesign.preset.tutorial.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Presets/Tutorial")
            .icon(Items.BOOK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> applyGameDesignPreset(configMgr, context, "tutorial", "Tutorial"))
            .build());

        ActionRegistry.register(RadialAction.builder(ActionIds.CONFIG_GAMEDESIGN_PRESET_SPEEDRUN)
            .labelKey("devmod.gamedesign.preset.speedrun")
            .descriptionKey("devmod.gamedesign.preset.speedrun.desc")
            .category(ActionCategory.CONFIG)
            .menuPath("Root/Config/Game Design/Presets/Speedrun")
            .icon(Items.CLOCK)
            .precondition(ActionPreconditions.clientOnly())
            .handler(context -> applyGameDesignPreset(configMgr, context, "speedrun", "Speedrun"))
            .build());
    }

    private static void applyGameDesignPreset(
            com.devmod.config.gamedesign.GameDesignConfigManager configMgr,
            ActionContext context, String presetId, String displayName) {
        var override = com.devmod.config.gamedesign.InstanceOverride.fromPreset(presetId);
        configMgr.setGlobalConfig(Objects.requireNonNull(override.applyTo(configMgr.getGlobalConfig().copy())));
        configMgr.markDirty();
        context.sendSuccess(I18n.translate("devmod.gamedesign.preset_applied", displayName), true);
    }

    private static void setHudPosition(Config.HudPosition position) {
        if (position == null) {
            return;
        }
        try {
            Config.IMPACT_HUD_POSITION.set(position);
        } catch (Exception e) {
            DevMod.LOGGER.debug("[DevMod] Failed to set HUD position {}", position, e);
        }
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
        try {
            Config.IMPACT_VFX_INTENSITY.set(value);
        } catch (Exception e) {
            DevMod.LOGGER.debug("[DevMod] Failed to set VFX intensity {}", value, e);
        }
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
