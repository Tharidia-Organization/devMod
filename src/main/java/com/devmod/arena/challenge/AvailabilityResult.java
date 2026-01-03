package com.devmod.arena.challenge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public sealed interface AvailabilityResult {

    /**
     * Challenge is available to the player.
     */
    record Available(
        String challengeTemplateId,
        String message
    ) implements AvailabilityResult {}

    /**
     * Player does not meet the minimum level requirement.
     */
    record LevelNotMet(
        String challengeTemplateId,
        int requiredLevel,
        int playerLevel
    ) implements AvailabilityResult {}

    /**
     * Player has not completed required prerequisite challenges.
     */
    record PrerequisitesNotMet(
        String challengeTemplateId,
        List<String> missingPrerequisites
    ) implements AvailabilityResult {}

    /**
     * Challenge is on cooldown from recent completion.
     */
    record OnCooldown(
        String challengeTemplateId,
        Instant cooldownEnds,
        Duration remainingCooldown
    ) implements AvailabilityResult {}

    /**
     * Challenge template is not currently available.
     */
    record TemplateUnavailable(
        String challengeTemplateId,
        String reason
    ) implements AvailabilityResult {}

    /**
     * Challenge is outside its valid time window.
     */
    record OutsideTimeWindow(
        String challengeTemplateId,
        Instant windowStart,
        Instant windowEnd,
        Instant currentTime
    ) implements AvailabilityResult {}

    /**
     * Checks if this result indicates availability.
     *
     * @return true if challenge is available
     */
    default boolean isAvailable() {
        return this instanceof Available;
    }

    /**
     * Gets the challenge template ID.
     *
     * @return the template ID
     */
    default String getChallengeTemplateId() {
        return switch (this) {
            case Available a -> a.challengeTemplateId();
            case LevelNotMet l -> l.challengeTemplateId();
            case PrerequisitesNotMet p -> p.challengeTemplateId();
            case OnCooldown c -> c.challengeTemplateId();
            case TemplateUnavailable t -> t.challengeTemplateId();
            case OutsideTimeWindow o -> o.challengeTemplateId();
        };
    }

    /**
     * Gets a human-readable message explaining the result.
     *
     * @return explanation message
     */
    default String getMessage() {
        return switch (this) {
            case Available a -> a.message();
            case LevelNotMet l -> String.format(
                "Challenge requires level %d, player is level %d",
                l.requiredLevel(), l.playerLevel()
            );
            case PrerequisitesNotMet p -> String.format(
                "Missing prerequisites: %s",
                String.join(", ", p.missingPrerequisites())
            );
            case OnCooldown c -> String.format(
                "Challenge on cooldown for %d more minutes",
                c.remainingCooldown().toMinutes()
            );
            case TemplateUnavailable t -> String.format(
                "Challenge template unavailable: %s",
                t.reason()
            );
            case OutsideTimeWindow o -> String.format(
                "Challenge available from %s to %s",
                o.windowStart(), o.windowEnd()
            );
        };
    }

    /**
     * Gets the check type that produced this result.
     *
     * @return the check type name
     */
    default String getCheckType() {
        if (this instanceof Available) return "AVAILABLE";
        if (this instanceof LevelNotMet) return "LEVEL_CHECK";
        if (this instanceof PrerequisitesNotMet) return "PREREQUISITE_CHECK";
        if (this instanceof OnCooldown) return "COOLDOWN_CHECK";
        if (this instanceof TemplateUnavailable) return "TEMPLATE_CHECK";
        if (this instanceof OutsideTimeWindow) return "TIME_WINDOW_CHECK";
        return "UNKNOWN";
    }

    /**
     * Creates an Available result.
     *
     * @param templateId the challenge template ID
     * @return Available result
     */
    static AvailabilityResult available(String templateId) {
        return new Available(templateId, "Challenge is available");
    }

    /**
     * Creates a LevelNotMet result.
     *
     * @param templateId the challenge template ID
     * @param required the required level
     * @param actual the player's level
     * @return LevelNotMet result
     */
    static AvailabilityResult levelNotMet(String templateId, int required, int actual) {
        return new LevelNotMet(templateId, required, actual);
    }

    /**
     * Creates a PrerequisitesNotMet result.
     *
     * @param templateId the challenge template ID
     * @param missing list of missing prerequisite IDs
     * @return PrerequisitesNotMet result
     */
    static AvailabilityResult prerequisitesNotMet(String templateId, List<String> missing) {
        return new PrerequisitesNotMet(templateId, List.copyOf(missing));
    }

    /**
     * Creates an OnCooldown result.
     *
     * @param templateId the challenge template ID
     * @param cooldownEnds when the cooldown ends
     * @return OnCooldown result
     */
    static AvailabilityResult onCooldown(String templateId, Instant cooldownEnds) {
        Duration remaining = Duration.between(Instant.now(), cooldownEnds);
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }
        return new OnCooldown(templateId, cooldownEnds, remaining);
    }

    /**
     * Creates a TemplateUnavailable result.
     *
     * @param templateId the challenge template ID
     * @param reason why it's unavailable
     * @return TemplateUnavailable result
     */
    static AvailabilityResult templateUnavailable(String templateId, String reason) {
        return new TemplateUnavailable(templateId, reason);
    }

    /**
     * Creates an OutsideTimeWindow result.
     *
     * @param templateId the challenge template ID
     * @param start window start time
     * @param end window end time
     * @return OutsideTimeWindow result
     */
    static AvailabilityResult outsideTimeWindow(String templateId, Instant start, Instant end) {
        return new OutsideTimeWindow(templateId, start, end, Instant.now());
    }
}
