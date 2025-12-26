package com.devmod.arena.challenge;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public final class ChallengeGenerator {

    /**
     * Challenge type categories.
     */
    public enum ChallengeType {
        DAILY("Daily Challenge", Duration.ofDays(1)),
        WEEKLY("Weekly Challenge", Duration.ofDays(7)),
        SPECIAL("Special Event Challenge", Duration.ofHours(4)),
        TUTORIAL("Tutorial Challenge", Duration.ZERO),
        COMPETITIVE("Competitive Challenge", Duration.ofHours(2)),
        GENERIC("Generic Challenge", Duration.ofMinutes(30));

        private final String displayName;
        private final Duration cooldown;

        ChallengeType(String displayName, Duration cooldown) {
            this.displayName = displayName;
            this.cooldown = cooldown;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Duration getCooldown() {
            return cooldown;
        }
    }

    /**
     * Represents a challenge template.
     */
    public record ChallengeTemplate(
        String id,
        String name,
        String description,
        ChallengeType type,
        int requiredLevel,
        List<String> prerequisites,
        Instant availableFrom,
        Instant availableUntil,
        boolean isActive,
        Map<String, Object> config
    ) {
        public ChallengeTemplate {
            prerequisites = prerequisites != null ? List.copyOf(prerequisites) : List.of();
            config = config != null ? Map.copyOf(config) : Map.of();
        }
    }

    /**
     * Represents a player's challenge state.
     */
    public record PlayerChallengeState(
        UUID playerId,
        int level,
        Set<String> completedChallenges,
        Map<String, Instant> challengeCooldowns
    ) {
        public PlayerChallengeState {
            completedChallenges = completedChallenges != null ?
                Set.copyOf(completedChallenges) : Set.of();
            challengeCooldowns = challengeCooldowns != null ?
                Map.copyOf(challengeCooldowns) : Map.of();
        }
    }

    /**
     * Result of challenge generation.
     */
    public record GenerationResult(
        ChallengeTemplate challenge,
        boolean isFallback,
        List<AvailabilityResult> checkResults
    ) {
        public GenerationResult {
            checkResults = checkResults != null ? List.copyOf(checkResults) : List.of();
        }
    }

    // Fallback challenges for each type
    private static final Map<ChallengeType, ChallengeTemplate> FALLBACK_CHALLENGES = Map.of(
        ChallengeType.DAILY, new ChallengeTemplate(
            "fallback_daily",
            "Daily Practice",
            "Play any match to earn rewards",
            ChallengeType.DAILY,
            1,
            List.of(),
            Instant.EPOCH,
            Instant.MAX,
            true,
            Map.of("reward", 50)
        ),
        ChallengeType.WEEKLY, new ChallengeTemplate(
            "fallback_weekly",
            "Weekly Activity",
            "Complete 5 matches this week",
            ChallengeType.WEEKLY,
            1,
            List.of(),
            Instant.EPOCH,
            Instant.MAX,
            true,
            Map.of("reward", 200, "matchesRequired", 5)
        ),
        ChallengeType.SPECIAL, new ChallengeTemplate(
            "fallback_special",
            "Quick Challenge",
            "Win a match to earn bonus rewards",
            ChallengeType.SPECIAL,
            1,
            List.of(),
            Instant.EPOCH,
            Instant.MAX,
            true,
            Map.of("reward", 100)
        ),
        ChallengeType.TUTORIAL, new ChallengeTemplate(
            "fallback_tutorial",
            "Getting Started",
            "Complete the tutorial match",
            ChallengeType.TUTORIAL,
            1,
            List.of(),
            Instant.EPOCH,
            Instant.MAX,
            true,
            Map.of("reward", 25)
        ),
        ChallengeType.COMPETITIVE, new ChallengeTemplate(
            "fallback_competitive",
            "Ranked Match",
            "Play a ranked match",
            ChallengeType.COMPETITIVE,
            5,
            List.of(),
            Instant.EPOCH,
            Instant.MAX,
            true,
            Map.of("reward", 150)
        ),
        ChallengeType.GENERIC, new ChallengeTemplate(
            "fallback_generic",
            "General Challenge",
            "Participate in any game activity",
            ChallengeType.GENERIC,
            1,
            List.of(),
            Instant.EPOCH,
            Instant.MAX,
            true,
            Map.of("reward", 10)
        )
    );

    private final Map<String, ChallengeTemplate> templateRegistry;

    /**
     * Creates a ChallengeGenerator with an empty template registry.
     */
    public ChallengeGenerator() {
        this.templateRegistry = new ConcurrentHashMap<>();
    }

    /**
     * Registers a challenge template.
     *
     * @param template the template to register
     */
    public void registerTemplate(ChallengeTemplate template) {
        templateRegistry.put(template.id(), template);
    }

    /**
     * Unregisters a challenge template.
     *
     * @param templateId the template ID to remove
     */
    public void unregisterTemplate(String templateId) {
        templateRegistry.remove(templateId);
    }

    /**
     * Gets all registered templates.
     *
     * @return list of registered templates
     */
    public List<ChallengeTemplate> getAllTemplates() {
        return new ArrayList<>(templateRegistry.values());
    }

    /**
     * Generates a challenge for a player.
     * Implements DD55: 5 availability checks, fallback generic.
     *
     * @param playerState the player's current state
     * @param preferredType preferred challenge type (optional)
     * @return generation result with challenge and check details
     */
    public GenerationResult generateChallenge(
            PlayerChallengeState playerState,
            ChallengeType preferredType) {

        List<AvailabilityResult> allCheckResults = new ArrayList<>();

        // Try to find an available challenge of the preferred type
        List<ChallengeTemplate> candidates = templateRegistry.values().stream()
            .filter(t -> preferredType == null || t.type() == preferredType)
            .toList();

        for (ChallengeTemplate template : candidates) {
            AvailabilityResult result = checkAvailability(template, playerState);
            allCheckResults.add(result);

            if (result.isAvailable()) {
                return new GenerationResult(template, false, allCheckResults);
            }
        }

        // No available challenge found - use fallback (DD55)
        ChallengeType fallbackType = preferredType != null ? preferredType : ChallengeType.GENERIC;
        ChallengeTemplate fallback = FALLBACK_CHALLENGES.get(fallbackType);

        // Check if fallback is available
        AvailabilityResult fallbackResult = checkAvailability(fallback, playerState);
        allCheckResults.add(fallbackResult);

        if (fallbackResult.isAvailable()) {
            return new GenerationResult(fallback, true, allCheckResults);
        }

        // Even fallback not available - return generic fallback
        ChallengeTemplate genericFallback = FALLBACK_CHALLENGES.get(ChallengeType.GENERIC);
        allCheckResults.add(AvailabilityResult.available(genericFallback.id()));

        return new GenerationResult(genericFallback, true, allCheckResults);
    }

    /**
     * Checks if a challenge template is available for a player.
     * Implements DD55: 5 availability checks.
     *
     * @param template the challenge template
     * @param playerState the player's state
     * @return availability result
     */
    public AvailabilityResult checkAvailability(
            ChallengeTemplate template,
            PlayerChallengeState playerState) {

        // Check 1: Level check (DD55)
        AvailabilityResult levelResult = checkLevel(template, playerState);
        if (!levelResult.isAvailable()) {
            return levelResult;
        }

        // Check 2: Prerequisite check (DD55)
        AvailabilityResult prereqResult = checkPrerequisites(template, playerState);
        if (!prereqResult.isAvailable()) {
            return prereqResult;
        }

        // Check 3: Cooldown check (DD55)
        AvailabilityResult cooldownResult = checkCooldown(template, playerState);
        if (!cooldownResult.isAvailable()) {
            return cooldownResult;
        }

        // Check 4: Template availability check (DD55)
        AvailabilityResult templateResult = checkTemplateAvailability(template);
        if (!templateResult.isAvailable()) {
            return templateResult;
        }

        // Check 5: Time window check (DD55)
        AvailabilityResult timeResult = checkTimeWindow(template);
        if (!timeResult.isAvailable()) {
            return timeResult;
        }

        // All checks passed
        return AvailabilityResult.available(template.id());
    }

    /**
     * Check 1: Level check.
     * Verifies player meets minimum level requirement.
     *
     * @param template the challenge template
     * @param playerState the player's state
     * @return availability result
     */
    public AvailabilityResult checkLevel(
            ChallengeTemplate template,
            PlayerChallengeState playerState) {

        if (playerState.level() < template.requiredLevel()) {
            return AvailabilityResult.levelNotMet(
                template.id(),
                template.requiredLevel(),
                playerState.level()
            );
        }
        return AvailabilityResult.available(template.id());
    }

    /**
     * Check 2: Prerequisite check.
     * Verifies player has completed required challenges.
     *
     * @param template the challenge template
     * @param playerState the player's state
     * @return availability result
     */
    public AvailabilityResult checkPrerequisites(
            ChallengeTemplate template,
            PlayerChallengeState playerState) {

        if (template.prerequisites().isEmpty()) {
            return AvailabilityResult.available(template.id());
        }

        List<String> missing = template.prerequisites().stream()
            .filter(prereq -> !playerState.completedChallenges().contains(prereq))
            .toList();

        if (!missing.isEmpty()) {
            return AvailabilityResult.prerequisitesNotMet(template.id(), missing);
        }

        return AvailabilityResult.available(template.id());
    }

    /**
     * Check 3: Cooldown check.
     * Verifies challenge is not on cooldown from recent completion.
     *
     * @param template the challenge template
     * @param playerState the player's state
     * @return availability result
     */
    public AvailabilityResult checkCooldown(
            ChallengeTemplate template,
            PlayerChallengeState playerState) {

        Instant lastCompletion = playerState.challengeCooldowns().get(template.id());
        if (lastCompletion == null) {
            return AvailabilityResult.available(template.id());
        }

        Duration cooldown = template.type().getCooldown();
        if (cooldown.isZero()) {
            return AvailabilityResult.available(template.id());
        }

        Instant cooldownEnds = lastCompletion.plus(cooldown);
        Instant now = Instant.now();

        if (now.isBefore(cooldownEnds)) {
            return AvailabilityResult.onCooldown(template.id(), cooldownEnds);
        }

        return AvailabilityResult.available(template.id());
    }

    /**
     * Check 4: Template availability check.
     * Verifies the challenge template is currently active.
     *
     * @param template the challenge template
     * @return availability result
     */
    public AvailabilityResult checkTemplateAvailability(ChallengeTemplate template) {
        if (!template.isActive()) {
            return AvailabilityResult.templateUnavailable(
                template.id(),
                "Template is disabled"
            );
        }
        return AvailabilityResult.available(template.id());
    }

    /**
     * Check 5: Time window check.
     * Verifies current time is within the challenge's valid window.
     *
     * @param template the challenge template
     * @return availability result
     */
    public AvailabilityResult checkTimeWindow(ChallengeTemplate template) {
        Instant now = Instant.now();

        if (now.isBefore(template.availableFrom())) {
            return AvailabilityResult.outsideTimeWindow(
                template.id(),
                template.availableFrom(),
                template.availableUntil()
            );
        }

        if (now.isAfter(template.availableUntil())) {
            return AvailabilityResult.outsideTimeWindow(
                template.id(),
                template.availableFrom(),
                template.availableUntil()
            );
        }

        return AvailabilityResult.available(template.id());
    }

    /**
     * Gets the fallback challenge for a specific type.
     *
     * @param type the challenge type
     * @return the fallback template
     */
    public ChallengeTemplate getFallback(ChallengeType type) {
        return FALLBACK_CHALLENGES.getOrDefault(type, FALLBACK_CHALLENGES.get(ChallengeType.GENERIC));
    }

    /**
     * Gets all available challenges for a player.
     *
     * @param playerState the player's state
     * @return list of available challenge templates
     */
    public List<ChallengeTemplate> getAvailableChallenges(PlayerChallengeState playerState) {
        return templateRegistry.values().stream()
            .filter(template -> checkAvailability(template, playerState).isAvailable())
            .toList();
    }

    /**
     * Gets templates by type.
     *
     * @param type the challenge type
     * @return list of templates of that type
     */
    public List<ChallengeTemplate> getTemplatesByType(ChallengeType type) {
        return templateRegistry.values().stream()
            .filter(template -> template.type() == type)
            .toList();
    }

    /**
     * Gets a template by ID.
     *
     * @param templateId the template ID
     * @return optional template
     */
    public Optional<ChallengeTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(templateRegistry.get(templateId));
    }
}
