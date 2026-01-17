package com.devmod.npc.dialog;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;

/**
 * Schedule constraints for dialog availability.
 * Dialogs can be restricted by in-game time, real-world days, and date ranges.
 *
 * <p>A dialog is available when ALL specified constraints are met.
 * Null/empty constraints are ignored (always pass).
 *
 * <p>Example usage:
 * <pre>
 * // Only during Minecraft daytime (6000-18000 ticks)
 * new DialogSchedule(6000L, 18000L, null, null, null)
 *
 * // Only on weekends (real time)
 * new DialogSchedule(null, null, EnumSet.of(SATURDAY, SUNDAY), null, null)
 *
 * // Event dialog: Dec 20-31, 2024
 * new DialogSchedule(null, null, null,
 *     LocalDate.of(2024, 12, 20),
 *     LocalDate.of(2024, 12, 31))
 * </pre>
 */
public record DialogSchedule(
    @Nullable Long minGameTime,
    @Nullable Long maxGameTime,
    @Nullable Set<DayOfWeek> realWorldDays,
    @Nullable LocalDate startDate,
    @Nullable LocalDate endDate
) {
    /** Minecraft ticks per day (24000) */
    public static final long TICKS_PER_DAY = 24000L;

    /** Dawn time in ticks */
    public static final long DAWN = 0L;

    /** Noon time in ticks */
    public static final long NOON = 6000L;

    /** Dusk time in ticks */
    public static final long DUSK = 12000L;

    /** Midnight time in ticks */
    public static final long MIDNIGHT = 18000L;

    // ========================================================================
    // Presets
    // ========================================================================

    /** Always available (no restrictions) */
    public static final DialogSchedule ALWAYS = new DialogSchedule(null, null, null, null, null);

    /** Only during Minecraft daytime (roughly 6AM to 6PM) */
    public static final DialogSchedule DAYTIME_ONLY = new DialogSchedule(0L, 12000L, null, null, null);

    /** Only during Minecraft nighttime (roughly 6PM to 6AM) */
    public static final DialogSchedule NIGHTTIME_ONLY = new DialogSchedule(12000L, 24000L, null, null, null);

    /** Only on weekends (Saturday and Sunday real time) */
    public static final DialogSchedule WEEKENDS_ONLY = new DialogSchedule(
        null, null, EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), null, null
    );

    /** Only on weekdays (Monday through Friday real time) */
    public static final DialogSchedule WEEKDAYS_ONLY = new DialogSchedule(
        null, null, EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        ), null, null
    );

    // ========================================================================
    // Codec
    // ========================================================================

    private static final Codec<DayOfWeek> DAY_OF_WEEK_CODEC = Codec.STRING.xmap(
        s -> DayOfWeek.valueOf(s.toUpperCase(Locale.ROOT)),
        d -> d.name().toLowerCase(Locale.ROOT)
    );

    private static final Codec<LocalDate> LOCAL_DATE_CODEC = Codec.STRING.xmap(
        LocalDate::parse,
        LocalDate::toString
    );

    public static final Codec<DialogSchedule> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.LONG.optionalFieldOf("minGameTime").forGetter(s -> Optional.ofNullable(s.minGameTime)),
            Codec.LONG.optionalFieldOf("maxGameTime").forGetter(s -> Optional.ofNullable(s.maxGameTime)),
            DAY_OF_WEEK_CODEC.listOf().optionalFieldOf("realWorldDays")
                .forGetter(s -> s.realWorldDays == null ? Optional.empty() : Optional.of(s.realWorldDays.stream().toList())),
            LOCAL_DATE_CODEC.optionalFieldOf("startDate").forGetter(s -> Optional.ofNullable(s.startDate)),
            LOCAL_DATE_CODEC.optionalFieldOf("endDate").forGetter(s -> Optional.ofNullable(s.endDate))
        ).apply(instance, (min, max, days, start, end) ->
            new DialogSchedule(
                min.orElse(null),
                max.orElse(null),
                days.map(list -> list.isEmpty() ? null : EnumSet.copyOf(list)).orElse(null),
                start.orElse(null),
                end.orElse(null)
            )
        )
    );

    public static final StreamCodec<FriendlyByteBuf, DialogSchedule> STREAM_CODEC = StreamCodec.of(
        (buf, schedule) -> {
            buf.writeBoolean(schedule.minGameTime != null);
            if (schedule.minGameTime != null) buf.writeLong(schedule.minGameTime);

            buf.writeBoolean(schedule.maxGameTime != null);
            if (schedule.maxGameTime != null) buf.writeLong(schedule.maxGameTime);

            buf.writeBoolean(schedule.realWorldDays != null && !schedule.realWorldDays.isEmpty());
            if (schedule.realWorldDays != null && !schedule.realWorldDays.isEmpty()) {
                buf.writeVarInt(schedule.realWorldDays.size());
                for (DayOfWeek day : schedule.realWorldDays) {
                    buf.writeVarInt(day.getValue());
                }
            }

            buf.writeBoolean(schedule.startDate != null);
            if (schedule.startDate != null) buf.writeUtf(schedule.startDate.toString());

            buf.writeBoolean(schedule.endDate != null);
            if (schedule.endDate != null) buf.writeUtf(schedule.endDate.toString());
        },
        buf -> {
            Long minGameTime = buf.readBoolean() ? buf.readLong() : null;
            Long maxGameTime = buf.readBoolean() ? buf.readLong() : null;

            Set<DayOfWeek> realWorldDays = null;
            if (buf.readBoolean()) {
                int count = buf.readVarInt();
                realWorldDays = EnumSet.noneOf(DayOfWeek.class);
                for (int i = 0; i < count; i++) {
                    realWorldDays.add(DayOfWeek.of(buf.readVarInt()));
                }
            }

            LocalDate startDate = buf.readBoolean() ? LocalDate.parse(buf.readUtf()) : null;
            LocalDate endDate = buf.readBoolean() ? LocalDate.parse(buf.readUtf()) : null;

            return new DialogSchedule(minGameTime, maxGameTime, realWorldDays, startDate, endDate);
        }
    );

    // ========================================================================
    // Availability Check
    // ========================================================================

    /**
     * Checks if the dialog is available in the given level based on all constraints.
     *
     * @param level The Minecraft level (for game time)
     * @return true if all constraints pass
     */
    public boolean isAvailable(@Nonnull Level level) {
        return checkGameTime(level.getDayTime())
            && checkRealWorldDay()
            && checkDateRange();
    }

    /**
     * Checks if the dialog is available with explicit values.
     * Useful for testing or when level is not available.
     *
     * @param gameTime The current game time in ticks
     * @param today    Today's date
     * @return true if all constraints pass
     */
    public boolean isAvailable(long gameTime, @Nonnull LocalDate today) {
        return checkGameTime(gameTime)
            && checkRealWorldDay(today.getDayOfWeek())
            && checkDateRange(today);
    }

    /**
     * Checks if the in-game time constraint passes.
     *
     * @param dayTime Current day time in ticks (0-24000)
     * @return true if within range or no range specified
     */
    public boolean checkGameTime(long dayTime) {
        if (minGameTime == null && maxGameTime == null) {
            return true;
        }

        // Normalize to 0-24000 range
        long normalizedTime = dayTime % TICKS_PER_DAY;
        if (normalizedTime < 0) normalizedTime += TICKS_PER_DAY;

        long min = minGameTime != null ? minGameTime : 0;
        long max = maxGameTime != null ? maxGameTime : TICKS_PER_DAY;

        // Handle wraparound (e.g., night: 18000-6000)
        if (min <= max) {
            return normalizedTime >= min && normalizedTime < max;
        } else {
            return normalizedTime >= min || normalizedTime < max;
        }
    }

    /**
     * Checks if the current real-world day constraint passes.
     *
     * @return true if today is in allowed days or no days specified
     */
    public boolean checkRealWorldDay() {
        return checkRealWorldDay(LocalDate.now(ZoneId.systemDefault()).getDayOfWeek());
    }

    /**
     * Checks if the given day of week passes the constraint.
     *
     * @param dayOfWeek The day to check
     * @return true if day is allowed or no days specified
     */
    public boolean checkRealWorldDay(@Nonnull DayOfWeek dayOfWeek) {
        if (realWorldDays == null || realWorldDays.isEmpty()) {
            return true;
        }
        return realWorldDays.contains(dayOfWeek);
    }

    /**
     * Checks if the current date is within the allowed date range.
     *
     * @return true if today is within range or no range specified
     */
    public boolean checkDateRange() {
        return checkDateRange(LocalDate.now(ZoneId.systemDefault()));
    }

    /**
     * Checks if the given date is within the allowed date range.
     *
     * @param date The date to check
     * @return true if within range or no range specified
     */
    public boolean checkDateRange(@Nonnull LocalDate date) {
        if (startDate != null && date.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && date.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    // ========================================================================
    // Utility Methods
    // ========================================================================

    /**
     * Returns true if this schedule has no restrictions.
     */
    public boolean isAlwaysAvailable() {
        return minGameTime == null
            && maxGameTime == null
            && (realWorldDays == null || realWorldDays.isEmpty())
            && startDate == null
            && endDate == null;
    }

    /**
     * Returns true if this schedule has game time restrictions.
     */
    public boolean hasGameTimeRestriction() {
        return minGameTime != null || maxGameTime != null;
    }

    /**
     * Returns true if this schedule has real-world day restrictions.
     */
    public boolean hasDayOfWeekRestriction() {
        return realWorldDays != null && !realWorldDays.isEmpty();
    }

    /**
     * Returns true if this schedule has date range restrictions.
     */
    public boolean hasDateRangeRestriction() {
        return startDate != null || endDate != null;
    }

    /**
     * Gets a human-readable description of the game time restriction.
     *
     * @return Description string, or null if no restriction
     */
    @Nullable
    public String getGameTimeDescription() {
        if (!hasGameTimeRestriction()) return null;

        String minStr = minGameTime != null ? ticksToTimeString(minGameTime) : "00:00";
        String maxStr = maxGameTime != null ? ticksToTimeString(maxGameTime) : "24:00";

        return minStr + " - " + maxStr;
    }

    /**
     * Gets a human-readable description of the day of week restriction.
     *
     * @return Description string, or null if no restriction
     */
    @Nullable
    public String getDayOfWeekDescription() {
        Set<DayOfWeek> days = realWorldDays;
        if (days == null || days.isEmpty()) return null;

        return days.stream()
            .map(d -> d.name().substring(0, 3))
            .collect(Collectors.joining(", "));
    }

    /**
     * Gets a human-readable description of the date range restriction.
     *
     * @return Description string, or null if no restriction
     */
    @Nullable
    public String getDateRangeDescription() {
        if (!hasDateRangeRestriction()) return null;

        if (startDate != null && endDate != null) {
            return startDate + " → " + endDate;
        } else if (startDate != null) {
            return "From " + startDate;
        } else {
            return "Until " + endDate;
        }
    }

    /**
     * Converts game ticks to a time string (HH:MM format).
     *
     * @param ticks Game time in ticks
     * @return Time string in HH:MM format
     */
    @Nonnull
    public static String ticksToTimeString(long ticks) {
        // Minecraft time: 0 = 6:00 AM, 6000 = noon, 12000 = 6:00 PM, 18000 = midnight
        long normalizedTicks = ticks % TICKS_PER_DAY;
        if (normalizedTicks < 0) normalizedTicks += TICKS_PER_DAY;

        // Convert to real-world hours (0 ticks = 6 AM)
        int totalMinutes = (int) ((normalizedTicks * 24 * 60) / TICKS_PER_DAY);
        int hours = (6 + totalMinutes / 60) % 24;
        int minutes = totalMinutes % 60;

        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Converts a time string (HH:MM format) to game ticks.
     *
     * @param timeStr Time string in HH:MM format
     * @return Game time in ticks
     */
    public static long timeStringToTicks(@Nonnull String timeStr) {
        int colonIndex = timeStr.indexOf(':');
        String hoursText = colonIndex >= 0 ? timeStr.substring(0, colonIndex) : timeStr;
        String minutesText = colonIndex >= 0 ? timeStr.substring(colonIndex + 1) : "0";
        int hours = Integer.parseInt(hoursText);
        int minutes = minutesText.isEmpty() ? 0 : Integer.parseInt(minutesText);

        // Convert to ticks (6:00 = 0 ticks)
        int adjustedHours = (hours - 6 + 24) % 24;
        int totalMinutes = adjustedHours * 60 + minutes;

        return (totalMinutes * TICKS_PER_DAY) / (24 * 60);
    }

    // ========================================================================
    // Builder
    // ========================================================================

    /**
     * Creates a builder for constructing a DialogSchedule.
     */
    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for DialogSchedule.
     */
    public static class Builder {
        @Nullable private Long minGameTime;
        @Nullable private Long maxGameTime;
        @Nullable private Set<DayOfWeek> realWorldDays;
        @Nullable private LocalDate startDate;
        @Nullable private LocalDate endDate;

        private Builder() {}

        /**
         * Sets the minimum game time (in ticks, 0-24000).
         */
        @Nonnull
        public Builder minGameTime(long ticks) {
            this.minGameTime = ticks;
            return this;
        }

        /**
         * Sets the maximum game time (in ticks, 0-24000).
         */
        @Nonnull
        public Builder maxGameTime(long ticks) {
            this.maxGameTime = ticks;
            return this;
        }

        /**
         * Sets the game time range (in ticks).
         */
        @Nonnull
        public Builder gameTimeRange(long min, long max) {
            this.minGameTime = min;
            this.maxGameTime = max;
            return this;
        }

        /**
         * Sets the allowed days of the week.
         */
        @Nonnull
        public Builder days(DayOfWeek... days) {
            this.realWorldDays = EnumSet.of(days[0], days);
            return this;
        }

        /**
         * Sets the allowed days of the week.
         */
        @Nonnull
        public Builder days(Set<DayOfWeek> days) {
            this.realWorldDays = days.isEmpty() ? null : EnumSet.copyOf(days);
            return this;
        }

        /**
         * Sets the start date for the date range.
         */
        @Nonnull
        public Builder startDate(LocalDate date) {
            this.startDate = date;
            return this;
        }

        /**
         * Sets the end date for the date range.
         */
        @Nonnull
        public Builder endDate(LocalDate date) {
            this.endDate = date;
            return this;
        }

        /**
         * Sets the date range.
         */
        @Nonnull
        public Builder dateRange(LocalDate start, LocalDate end) {
            this.startDate = start;
            this.endDate = end;
            return this;
        }

        /**
         * Builds the DialogSchedule.
         */
        @Nonnull
        public DialogSchedule build() {
            return new DialogSchedule(minGameTime, maxGameTime, realWorldDays, startDate, endDate);
        }
    }
}
