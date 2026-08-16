#!/bin/bash
# Script to copy only essential mods to mods_clean directory
MODS_DIR="/home/amp/.ampdata/instances/TharidiaDevModTest01/Minecraft/mods"
CLEAN_DIR="/home/amp/.ampdata/instances/TharidiaDevModTest01/Minecraft/mods_clean"

mkdir -p "$CLEAN_DIR"

# List of mods to KEEP
KEEP_MODS=(
  # === CORE: DevMod and hard dependencies ===
  "devmod-0.1.0.jar"
  "geckolib-neoforge-1.21.1-4.8.2.jar"
  "duckdb-jdbc-1.4.3.0.jar"

  # === COMPAT: Mods DevMod has compat modules for ===
  "elixirum-neoforge-1.21.1-0.2.2.jar"
  "epicfight-21.14.4-mc1.21.1-neoforge.jar"
  "irons_spellbooks-1.21.1-3.14.8.jar"
  "curios-neoforge-9.5.1+1.21.1.jar"
  "emotecraft-for-MC1.21.1-2.4.12-neoforge.jar"
  "mowziesmobs-1.21.1-1.7.5.jar"
  "SmartBrainLib-neoforge-1.21.1-1.16.11.jar"
  "player-animation-lib-forge-2.0.1+1.21.1.jar"
  "azurelib-neo-1.21.1-3.1.1.jar"
  "azurelibarmor-neo-1.21.1-3.1.1.jar"
  "entityculling-neoforge-1.9.4-mc1.21.1.jar"
  "entity_model_features_1.21-neoforge-3.0.7.jar"
  "Controlling-neoforge-1.21.1-19.0.5.jar"
  "emi-1.1.22+1.21.1+neoforge.jar"
  "journeymap-neoforge-1.21.1-6.0.0-beta.53.jar"
  "ApothicAttributes-1.21.1-2.9.0.jar"
  "ranged_weapon_api-neoforge-2.3.3+1.21.1.jar"
  "shield_api-neoforge-2.2.0.jar"
  "cloth-config-15.0.140-neoforge.jar"
  "yet_another_config_lib_v3-3.7.1+1.21.1-neoforge.jar"
  "spark-1.10.124-neoforge.jar"
  "TerraBlender-neoforge-1.21.1-4.1.0.8.jar"
  "tharidia_easydiet-0.1.0-alpha.jar"
  "easy_npc-neoforge-1.21.1-6.6.0.jar"
  "easy_npc_bundle-neoforge-1.21.1-6.6.0.jar"
  "easy_npc_config_ui-neoforge-1.21.1-6.6.0.jar"
  "gametechbcs_spellbooks-3.0.0-1.21.1.jar"
  "gtbcs_spell_lib-1.3.1-1.21.1.jar"
  "gtbcs_geomancy_plus-1.1.0-1.21.1.jar"
  "aces_spell_utils-1.1.19-1.21.1.jar"

  # === PERFORMANCE ===
  "ferritecore-7.0.2-neoforge.jar"
  "lithium-neoforge-0.15.1+mc1.21.1.jar"
  "modernfix-neoforge-5.25.1+mc1.21.1.jar"
  "servercore-neoforge-1.5.10+1.21.1.jar"
  "ScalableLux-0.1.0.1+neoforge.1cb1e91-all.jar"

  # === LIBRARY DEPS (needed by above mods) ===
  "forgified-fabric-api-0.115.6+2.1.4+1.21.1.jar"
  "kotlinforforge-5.10.0-all.jar"
  "architectury-13.0.8-neoforge.jar"
  "Placebo-1.21.1-9.9.1.jar"
  "GlitchCore-neoforge-1.21.1-2.1.0.0.jar"
  "Necronomicon-NeoForge-1.6.0+1.21.jar"
  "Pehkui-3.8.3+1.21-neoforge.jar"
  "PuzzlesLib-v21.1.39-1.21.1-NeoForge.jar"
  "ForgeConfigAPIPort-v21.1.6-1.21.1-NeoForge.jar"
  "bookshelf-neoforge-1.21.1-21.1.80.jar"
  "balm-neoforge-1.21.1-21.0.55.jar"
  "Corgilib-NeoForge-1.21.1-5.0.0.7.jar"
  "resourcefullib-neoforge-1.21-3.0.12.jar"
  "moonlight-1.21-2.28.2-neoforge.jar"
  "Kiwi-1.21.1-NeoForge-15.8.2.jar"
  "craftedcore-5.8.jar"
  "EpheroLib-1.21.1-NEO-FORGE-1.2.0.jar"
  "lionfishapi-2.6.jar"
  "gaboulibs-neoforge-1.4.jar"
  "badpackets-neo-0.8.2.jar"
  "fzzy_config-0.7.4+1.21+neoforge.jar"
  "platform-neoforge-1.21.1-1.2.11.3.jar"
  "resourcelibrary-neoforge-1.21.1-2.10.0.jar"
  "resourceconfigapi-neoforge-1.21.1-3.9.2.jar"
  "structure_pool_api-neoforge-1.2.1+1.21.1.jar"
  "midnightlib-neoforge-1.9.2+1.21.1.jar"
  "mru-1.0.19+LTS+1.21.1+neoforge.jar"
  "sdmuilibrary-neoforge-1.21-1.9.2.jar"
  "CreativeCore_NEOFORGE_v2.13.23_mc1.21.1.jar"

  # === UTILITY ===
  "CrashAssistant-neoforge-1.20.6-1.21.4-1.10.27.jar"
  "SimpleBackups-1.21-4.0.24.jar"
  "disconnect-packet-fix-neoforge-2.0.1.jar"
  "packetfixer-3.3.1-1.20.5-1.21.X-merged.jar"
)

kept=0
skipped=0
for mod in "${KEEP_MODS[@]}"; do
  if [ -f "$MODS_DIR/$mod" ]; then
    cp "$MODS_DIR/$mod" "$CLEAN_DIR/$mod"
    echo "KEPT: $mod"
    ((kept++))
  else
    echo "NOT FOUND: $mod"
    ((skipped++))
  fi
done

echo ""
echo "=== SUMMARY ==="
echo "Kept: $kept mods"
echo "Not found: $skipped mods"
echo "Original: $(ls $MODS_DIR | wc -l) mods"
echo "Removed: $(($(ls $MODS_DIR | wc -l) - kept)) mods"
