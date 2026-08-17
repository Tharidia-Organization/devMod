package com.devmod.endurance;

import java.util.Locale;

import net.minecraft.resources.ResourceLocation;

/**
 * Turns a mob's registry id into something readable.
 *
 * <p>There were two copies of this, in {@code EnduranceQuestRegistry.MobQuestConfig} and in
 * {@code EnduranceMobConfig}, and both split the path on {@code '_'} only. A ResourceLocation path
 * may legally contain {@code '/'}, and modded mobs use it to namespace a family: Age of Fight
 * registers its seven Ashen Court entities as {@code ashen_court/bonebound_vanguard} and friends.
 * Both copies therefore produced <i>"Ashen Court/bonebound Vanguard"</i> -- the slash kept, the word
 * after it not capitalised -- in the quest UI, the mob picker and every log line.
 *
 * <p>One implementation, so the two cannot drift, and it treats {@code '/'} as a separator like
 * {@code '_'}.
 */
public final class MobDisplayNames {

    private MobDisplayNames() {}

    /**
     * Build a display name from a mob's registry id.
     *
     * @param mobId the mob's registry id, may be null
     * @return the display name, or an empty string when the id is null
     */
    public static String of(ResourceLocation mobId) {
        return mobId == null ? "" : fromPath(mobId.getPath());
    }

    /**
     * Build a display name from a registry path.
     *
     * @param path the path part of a registry id, may be null
     * @return the display name, or an empty string when the path is null or has no words
     */
    public static String fromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(path.length());
        boolean startOfWord = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_' || c == '/' || c == '.' || c == '-') {
                // Only emit a separator once we have something to separate, so a leading or
                // doubled separator cannot produce a leading or doubled space.
                if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                startOfWord = true;
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = false;
        }
        return out.toString().strip();
    }

    /**
     * Lower-case the path for the keyword matching the registry does on mob ids.
     *
     * @param mobId the mob's registry id, may be null
     * @return the lower-cased path, or an empty string when the id is null
     */
    public static String matchablePath(ResourceLocation mobId) {
        return mobId == null ? "" : mobId.getPath().toLowerCase(Locale.ROOT);
    }
}
