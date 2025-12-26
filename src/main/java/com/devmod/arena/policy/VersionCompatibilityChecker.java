package com.devmod.arena.policy;

import com.devmod.arena.registry.ArenaTemplate;

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
