package com.devmod.arena.policy;

import com.devmod.arena.registry.ArenaTemplate;

/**
 * Simple compatibility checker between a policy and a template.
 *
 * Mirrors the Versioning Strategy in TODO_ARENA_TEMPLATE v2.3:
 * - template.version must be within [minTemplateVersion, maxTemplateVersion] if set
 * - if template.breakingChange is true, policy.version must be >= template.version
 */
public class VersionCompatibilityChecker {

    public record VersionCheck(boolean compatible, String reason) {
        public static VersionCheck ok() {
            return new VersionCheck(true, null);
        }
        public static VersionCheck fail(String reason) {
            return new VersionCheck(false, reason);
        }
    }

    public VersionCheck check(ArenaTemplate template, ArenaPolicy policy) {
        Integer minVersion = policy.minTemplateVersion();
        Integer maxVersion = policy.maxTemplateVersion();
        if (minVersion != null && template.version() < minVersion) {
            return VersionCheck.fail("template_v" + template.version() + " < policy.minTemplateVersion " + minVersion);
        }
        if (maxVersion != null && template.version() > maxVersion) {
            return VersionCheck.fail("template_v" + template.version() + " > policy.maxTemplateVersion " + maxVersion);
        }
        if (template.breakingChange() && policy.version() < template.version()) {
            return VersionCheck.fail("template breakingChange at v" + template.version() + " but policy v" + policy.version());
        }
        return VersionCheck.ok();
    }
}
