package com.devmod.client.notification;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.devmod.notification.NotificationCategory;
import com.devmod.notification.NotificationPriority;

/**
 * Centralized sound management for the Unified Notification Center.
 *
 * <p>Consolidates all notification sounds into a single manager with:
 * <ul>
 *   <li>Sound ID to SoundEvent mapping</li>
 *   <li>Volume scaling per category</li>
 *   <li>Priority-based pitch modulation</li>
 *   <li>User preference integration</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class NotificationSoundManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationSoundManager.class);

    public static final NotificationSoundManager INSTANCE = new NotificationSoundManager();

    /**
     * Sound configuration with volume and pitch.
     */
    public record SoundConfig(SoundEvent sound, float pitch, float volume) {
        public SoundConfig withVolume(float newVolume) {
            return new SoundConfig(sound, pitch, newVolume);
        }
    }

    // ============================================================================
    // SOUND MAPPINGS
    // ============================================================================

    private final Map<String, SoundConfig> soundConfigs = new HashMap<>();

    private NotificationSoundManager() {
        initializeSoundMappings();
    }

    private void initializeSoundMappings() {
        // Badge sounds (by rarity)
        soundConfigs.put("badge.common", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()), 1.2f, 0.6f));
        soundConfigs.put("badge.uncommon", new SoundConfig(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f));
        soundConfigs.put("badge.rare", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.0f, 0.9f));
        soundConfigs.put("badge.epic", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.1f, 1.0f));
        soundConfigs.put("badge.legendary", new SoundConfig(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f));
        soundConfigs.put("badge.mythic", new SoundConfig(
                SoundEvents.TOTEM_USE, 0.8f, 0.7f));

        // Token sounds
        soundConfigs.put("token.gain", new SoundConfig(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.5f));
        soundConfigs.put("token.large", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.0f, 0.7f));

        // Record sounds
        soundConfigs.put("record.new", new SoundConfig(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f));
        soundConfigs.put("record.best", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.2f, 1.0f));

        // Season sounds
        soundConfigs.put("season.tierup", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f));
        soundConfigs.put("season.reward", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()), 1.4f, 0.6f));

        // Combat sounds
        soundConfigs.put("combo.decay", new SoundConfig(
                SoundEvents.SHIELD_BREAK, 0.8f, 0.4f));
        soundConfigs.put("combo.lost", new SoundConfig(
                SoundEvents.ITEM_BREAK, 1.2f, 0.3f));

        // Combat Flow HUD sounds
        soundConfigs.put("combatflow.combo.hit", new SoundConfig(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f, 0.3f));  // Subtle tick
        soundConfigs.put("combatflow.rank.up", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.5f, 0.7f));  // Fanfare
        soundConfigs.put("combatflow.overdrive", new SoundConfig(
                SoundEvents.TOTEM_USE, 1.2f, 0.8f));  // Powerful activation

        // Resonance sounds
        soundConfigs.put("resonance.chain", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BELL.value()), 1.5f, 0.5f));
        soundConfigs.put("resonance.max", new SoundConfig(
                SoundEvents.WITHER_SPAWN, 1.5f, 0.3f));

        // Party sounds
        soundConfigs.put("party.invite", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()), 1.2f, 0.6f));
        soundConfigs.put("party.invite_declined", new SoundConfig(
                SoundEvents.ITEM_BREAK, 0.9f, 0.4f));
        soundConfigs.put("party.invite_expired", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BASS.value()), 0.8f, 0.45f));
        soundConfigs.put("party.join", new SoundConfig(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f));
        soundConfigs.put("party.leave", new SoundConfig(
                SoundEvents.ITEM_BREAK, 0.8f, 0.3f));
        soundConfigs.put("party.kicked", new SoundConfig(
                SoundEvents.SHIELD_BREAK, 0.7f, 0.5f));
        soundConfigs.put("party.disbanded", new SoundConfig(
                SoundEvents.ANVIL_LAND, 0.6f, 0.4f));
        soundConfigs.put("party.leader_changed", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()), 1.0f, 0.5f));
        soundConfigs.put("party.quest_started", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.0f, 0.6f));
        soundConfigs.put("party.quest_finished", new SoundConfig(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f));

        // Quest sounds
        soundConfigs.put("quest.start", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.0f, 0.7f));
        soundConfigs.put("quest.complete", new SoundConfig(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f));
        soundConfigs.put("quest.wave", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BELL.value()), 0.8f, 0.6f));
        soundConfigs.put("quest.boss", new SoundConfig(
                SoundEvents.ENDER_DRAGON_GROWL, 2.0f, 0.4f));

        // Wave-specific sounds (aliases for quest waves)
        soundConfigs.put("wave.start", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BELL.value()), 0.9f, 0.55f));
        soundConfigs.put("wave.boss_start", new SoundConfig(
                SoundEvents.ENDER_DRAGON_GROWL, 1.6f, 0.35f));
        soundConfigs.put("wave.complete", new SoundConfig(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f));

        // Chain offer sounds
        soundConfigs.put("chain.offer", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()), 1.2f, 0.55f));
        soundConfigs.put("chain.step", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()), 1.0f, 0.45f));
        soundConfigs.put("chain.complete", new SoundConfig(
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.85f));
        soundConfigs.put("chain.failed", new SoundConfig(
                SoundEvents.ANVIL_LAND, 0.8f, 0.5f));

        // Admin sounds
        soundConfigs.put("admin.alert", new SoundConfig(
                SoundEvents.ANVIL_LAND, 1.0f, 0.6f));
        soundConfigs.put("admin.warning", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_BASS.value()), 0.5f, 0.8f));

        // Mailbox sounds
        soundConfigs.put("mailbox.new", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()), 1.0f, 0.5f));
        soundConfigs.put("mailbox.claim", new SoundConfig(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f));

        // News sounds
        soundConfigs.put("news.new", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_CHIME.value()), 1.1f, 0.45f));

        // System sounds
        soundConfigs.put("system.info", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()), 1.0f, 0.4f));
        soundConfigs.put("system.error", new SoundConfig(
                SoundEvents.ANVIL_LAND, 0.8f, 0.5f));

        // Epic Fight integration fallback sounds (used when EF sounds unavailable)
        soundConfigs.put("epicfight.parry.perfect", new SoundConfig(
                SoundEvents.PLAYER_LEVELUP, 1.8f, 0.9f));
        soundConfigs.put("epicfight.parry.success", new SoundConfig(
                SoundEvents.SHIELD_BLOCK, 1.4f, 0.6f));
        soundConfigs.put("epicfight.guard.start", new SoundConfig(
                Objects.requireNonNull(SoundEvents.ARMOR_EQUIP_IRON.value()), 1.0f, 0.4f));
        soundConfigs.put("epicfight.guard.impact", new SoundConfig(
                SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f));
        soundConfigs.put("epicfight.stamina.low", new SoundConfig(
                SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.5f));

        // Default fallback
        soundConfigs.put("default", new SoundConfig(
                Objects.requireNonNull(SoundEvents.NOTE_BLOCK_PLING.value()), 1.0f, 0.5f));
    }

    // ============================================================================
    // PLAYBACK
    // ============================================================================

    /**
     * Play a notification sound by ID.
     *
     * @param soundId The sound ID (e.g., "badge.legendary", "token.gain")
     * @param category The notification category for volume lookup
     * @param priority The notification priority for emphasis
     */
    public void play(@Nullable String soundId, NotificationCategory category, NotificationPriority priority) {
        if (soundId == null || soundId.isEmpty()) {
            return;
        }

        // Check user preferences
        ClientNotificationPreferences prefs = ClientNotificationPreferences.INSTANCE;
        if (!prefs.isSoundEnabled(category)) {
            return;
        }

        // Get base sound config
        SoundConfig config = soundConfigs.getOrDefault(soundId, soundConfigs.get("default"));
        if (config == null) {
            LOGGER.debug("[NotificationSound] No config found for sound: {}", soundId);
            return;
        }

        // Calculate final volume with user preference
        float categoryVolume = prefs.getSoundVolume(category);
        float priorityMultiplier = getPriorityVolumeMultiplier(priority);
        float finalVolume = Math.min(1.0f, config.volume() * categoryVolume * priorityMultiplier);

        if (finalVolume <= 0.01f) {
            return; // Too quiet to hear
        }

        // Play the sound
        playSound(config.sound(), config.pitch(), finalVolume);
    }

    /**
     * Play a sound with default category/priority.
     */
    public void play(@Nullable String soundId) {
        play(soundId, NotificationCategory.SYSTEM, NotificationPriority.NORMAL);
    }

    /**
     * Play a raw sound event.
     */
    public void playSound(SoundEvent sound, float pitch, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || sound == null) return;

        SoundManager soundManager = mc.getSoundManager();
        if (soundManager == null) return;

        try {
            soundManager.play(Objects.requireNonNull(
                SimpleSoundInstance.forUI(Objects.requireNonNull(sound), pitch, volume)));
        } catch (Exception e) {
            LOGGER.warn("[NotificationSound] Failed to play sound: {}", e.getMessage());
        }
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    /**
     * Get volume multiplier based on priority.
     * Higher priority = slightly louder.
     */
    private float getPriorityVolumeMultiplier(NotificationPriority priority) {
        return switch (priority) {
            case LOW -> 0.7f;
            case NORMAL -> 1.0f;
            case HIGH -> 1.1f;
            case URGENT -> 1.2f;
            case CRITICAL -> 1.3f;
        };
    }

    /**
     * Register a custom sound configuration.
     *
     * @param soundId The sound ID
     * @param config The sound configuration
     */
    public void registerSound(String soundId, SoundConfig config) {
        soundConfigs.put(soundId, config);
    }

    /**
     * Check if a sound ID is registered.
     */
    public boolean hasSound(String soundId) {
        return soundConfigs.containsKey(soundId);
    }

    /**
     * Get sound config for an ID.
     */
    @Nullable
    public SoundConfig getConfig(String soundId) {
        return soundConfigs.get(soundId);
    }

}
