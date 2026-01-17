package com.devmod.npc.dialog.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import com.devmod.npc.NpcEmotion;
import com.devmod.npc.dialog.NpcContext;

/**
 * Sealed interface for dialog conditions that control visibility of nodes and options.
 * Each condition type has its own implementation with specific test logic.
 */
public sealed interface DialogCondition permits
    DialogCondition.Always,
    DialogCondition.FirstVisit,
    DialogCondition.HasPermission,
    DialogCondition.HasSelectedOption,
    DialogCondition.NpcHasEmotion,
    DialogCondition.QuestCompleted,
    DialogCondition.QuestCount,
    DialogCondition.TimeOfDay,
    DialogCondition.And,
    DialogCondition.Or,
    DialogCondition.Not,
    DialogCondition.Custom
{
    /**
     * Returns the type identifier for this condition.
     */
    @Nonnull
    String getType();

    /**
     * Tests if this condition is met for the given player.
     *
     * @param player  The server player to test
     * @param context The NPC dialog context
     * @return true if the condition is met
     */
    boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context);

    /**
     * Tests this condition and returns a detailed debug result.
     * Override in subclasses to provide specific debug reasons.
     *
     * @param player  The server player to test
     * @param context The NPC dialog context
     * @return A debug result with pass/fail status and reason
     */
    @Nonnull
    default ConditionDebugResult debugTest(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
        boolean result = test(player, context);
        return ConditionDebugResult.simple(this, result, getDebugReason(player, context, result));
    }

    /**
     * Gets a human-readable debug reason for the condition result.
     * Override in subclasses to provide specific reasons.
     *
     * @param player  The server player
     * @param context The NPC dialog context
     * @param result  The test result
     * @return A human-readable reason
     */
    @Nonnull
    default String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
        return result ? "Condition passed" : "Condition failed";
    }

    /**
     * Dispatch codec for serializing conditions by type.
     */
    Codec<DialogCondition> CODEC = Codec.STRING.fieldOf("type").codec().dispatch(
        DialogCondition::getType,
        type -> switch (type) {
            case "always" -> Always.CODEC;
            case "first_visit" -> FirstVisit.CODEC;
            case "permission" -> HasPermission.CODEC;
            case "has_selected_option" -> HasSelectedOption.CODEC;
            case "npc_emotion" -> NpcHasEmotion.CODEC;
            case "quest_completed" -> QuestCompleted.CODEC;
            case "quest_count" -> QuestCount.CODEC;
            case "time" -> TimeOfDay.CODEC;
            case "and" -> And.CODEC;
            case "or" -> Or.CODEC;
            case "not" -> Not.CODEC;
            case "custom" -> Custom.CODEC;
            default -> throw new IllegalArgumentException("Unknown condition type: " + type);
        }
    );

    // ========================================================================
    // Condition Implementations
    // ========================================================================

    /**
     * Always true condition.
     */
    record Always() implements DialogCondition {
        public static final MapCodec<Always> CODEC = MapCodec.unit(Always::new);

        @Override
        public String getType() {
            return "always";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return true;
        }

        @Override
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            return "Always returns true";
        }
    }

    /**
     * True if this is the player's first visit to the NPC.
     */
    record FirstVisit(@Nonnull UUID npcId) implements DialogCondition {
        public static final MapCodec<FirstVisit> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString)
                    .fieldOf("npcId").forGetter(f -> f.npcId)
            ).apply(instance, FirstVisit::new)
        );

        /**
         * Creates a FirstVisit condition for the current NPC context.
         */
        public static FirstVisit forCurrentNpc() {
            // Will be resolved at runtime
            return new FirstVisit(new UUID(0, 0));
        }

        @Override
        public String getType() {
            return "first_visit";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            // Use context NPC ID if placeholder UUID
            UUID targetNpcId = npcId.getMostSignificantBits() == 0 && npcId.getLeastSignificantBits() == 0
                ? context.npcId()
                : npcId;
            return !context.hasVisited(player.getUUID(), targetNpcId);
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            UUID targetNpcId = npcId.getMostSignificantBits() == 0 && npcId.getLeastSignificantBits() == 0
                ? context.npcId()
                : npcId;
            int visitCount = context.getRegistry().getVisitCount(player.getUUID(), targetNpcId);
            return result
                ? "First visit (no previous visits)"
                : "Player has visited NPC " + visitCount + " times";
        }
    }

    /**
     * True if the player has the specified permission level.
     */
    record HasPermission(int opLevel) implements DialogCondition {
        public static final MapCodec<HasPermission> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.INT.fieldOf("opLevel").forGetter(HasPermission::opLevel)
            ).apply(instance, HasPermission::new)
        );

        @Override
        public String getType() {
            return "permission";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return player.hasPermissions(opLevel);
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            var server = player.getServer();
            int playerOpLevel = server != null ? server.getProfilePermissions(player.getGameProfile()) : 0;
            return "Player OP level: " + playerOpLevel + " (required: " + opLevel + ")";
        }
    }

    /**
     * True if the player has previously selected a specific option with this NPC.
     * Used for branching dialogs based on past choices.
     */
    record HasSelectedOption(@Nonnull String optionId) implements DialogCondition {
        public static final MapCodec<HasSelectedOption> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("optionId").forGetter(HasSelectedOption::optionId)
            ).apply(instance, HasSelectedOption::new)
        );

        @Override
        public String getType() {
            return "has_selected_option";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return context.getRegistry().hasSelectedOption(player.getUUID(), context.npcId(), optionId);
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            return "Option '" + optionId + "': " + (result ? "previously selected" : "not selected yet");
        }
    }

    /**
     * True if the NPC is currently in the specified emotional state.
     */
    record NpcHasEmotion(@Nonnull NpcEmotion emotion) implements DialogCondition {
        public static final MapCodec<NpcHasEmotion> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                NpcEmotion.CODEC.fieldOf("emotion").forGetter(NpcHasEmotion::emotion)
            ).apply(instance, NpcHasEmotion::new)
        );

        @Override
        public String getType() {
            return "npc_emotion";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return context.getEmotion() == emotion;
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            return "NPC emotion: " + context.getEmotion().getSerializedName() +
                " (required: " + emotion.getSerializedName() + ")";
        }
    }

    /**
     * True if the player has completed the specified quest.
     */
    record QuestCompleted(@Nonnull String questId) implements DialogCondition {
        public static final MapCodec<QuestCompleted> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("questId").forGetter(QuestCompleted::questId)
            ).apply(instance, QuestCompleted::new)
        );

        @Override
        public String getType() {
            return "quest_completed";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return context.hasCompletedQuest(player.getUUID(), questId);
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            return "Quest '" + questId + "': " + (result ? "completed" : "not completed");
        }
    }

    /**
     * True if the player has completed at least the specified number of quests.
     */
    record QuestCount(int minQuests, @Nonnull Comparison comparison) implements DialogCondition {
        public static final MapCodec<QuestCount> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.INT.fieldOf("minQuests").forGetter(QuestCount::minQuests),
                Codec.STRING.xmap(Comparison::valueOf, Comparison::name)
                    .optionalFieldOf("comparison", Comparison.GREATER_OR_EQUAL)
                    .forGetter(QuestCount::comparison)
            ).apply(instance, QuestCount::new)
        );

        @Override
        public String getType() {
            return "quest_count";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            int count = context.getCompletedQuestCount(player.getUUID());
            return comparison.test(count, minQuests);
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            int count = context.getCompletedQuestCount(player.getUUID());
            String compSymbol = switch (comparison) {
                case EQUAL -> "==";
                case NOT_EQUAL -> "!=";
                case GREATER -> ">";
                case LESS -> "<";
                case GREATER_OR_EQUAL -> ">=";
                case LESS_OR_EQUAL -> "<=";
            };
            return "Quest count: " + count + " " + compSymbol + " " + minQuests;
        }
    }

    /**
     * Comparison operators for numeric conditions.
     */
    enum Comparison {
        EQUAL {
            @Override
            public boolean test(int a, int b) {
                return a == b;
            }
        },
        NOT_EQUAL {
            @Override
            public boolean test(int a, int b) {
                return a != b;
            }
        },
        GREATER {
            @Override
            public boolean test(int a, int b) {
                return a > b;
            }
        },
        LESS {
            @Override
            public boolean test(int a, int b) {
                return a < b;
            }
        },
        GREATER_OR_EQUAL {
            @Override
            public boolean test(int a, int b) {
                return a >= b;
            }
        },
        LESS_OR_EQUAL {
            @Override
            public boolean test(int a, int b) {
                return a <= b;
            }
        };

        public abstract boolean test(int a, int b);
    }

    /**
     * True if the current game time is within the specified range.
     */
    record TimeOfDay(long minTime, long maxTime) implements DialogCondition {
        public static final MapCodec<TimeOfDay> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.LONG.fieldOf("minTime").forGetter(TimeOfDay::minTime),
                Codec.LONG.fieldOf("maxTime").forGetter(TimeOfDay::maxTime)
            ).apply(instance, TimeOfDay::new)
        );

        /** Day time (6000-18000 ticks, roughly 6am-6pm) */
        public static TimeOfDay day() {
            return new TimeOfDay(0, 12000);
        }

        /** Night time (18000-6000 ticks, roughly 6pm-6am) */
        public static TimeOfDay night() {
            return new TimeOfDay(12000, 24000);
        }

        @Override
        public String getType() {
            return "time";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            long time = player.level().getDayTime() % 24000;
            if (minTime <= maxTime) {
                return time >= minTime && time <= maxTime;
            } else {
                // Handle wrap-around (e.g., night spans 18000-6000)
                return time >= minTime || time <= maxTime;
            }
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            long time = player.level().getDayTime() % 24000;
            return "Current time: " + time + " (range: " + minTime + "-" + maxTime + ")";
        }
    }

    /**
     * True if all contained conditions are true.
     */
    record And(@Nonnull List<DialogCondition> conditions) implements DialogCondition {
        public static final MapCodec<And> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                DialogCondition.CODEC.listOf().fieldOf("conditions").forGetter(And::conditions)
            ).apply(instance, And::new)
        );

        @Override
        public String getType() {
            return "and";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return conditions.stream().allMatch(c -> c.test(player, context));
        }

        @Override
        @Nonnull
        public ConditionDebugResult debugTest(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            List<ConditionDebugResult> childResults = new ArrayList<>();
            int passed = 0;
            int failed = 0;
            for (DialogCondition c : conditions) {
                ConditionDebugResult childResult = c.debugTest(player, context);
                childResults.add(childResult);
                if (childResult.passed()) {
                    passed++;
                } else {
                    failed++;
                }
            }
            boolean result = failed == 0;
            String reason = passed + "/" + conditions.size() + " conditions passed";
            return ConditionDebugResult.composite(this, result, reason, childResults);
        }
    }

    /**
     * True if any contained condition is true.
     */
    record Or(@Nonnull List<DialogCondition> conditions) implements DialogCondition {
        public static final MapCodec<Or> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                DialogCondition.CODEC.listOf().fieldOf("conditions").forGetter(Or::conditions)
            ).apply(instance, Or::new)
        );

        @Override
        public String getType() {
            return "or";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return conditions.stream().anyMatch(c -> c.test(player, context));
        }

        @Override
        @Nonnull
        public ConditionDebugResult debugTest(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            List<ConditionDebugResult> childResults = new ArrayList<>();
            int passed = 0;
            for (DialogCondition c : conditions) {
                ConditionDebugResult childResult = c.debugTest(player, context);
                childResults.add(childResult);
                if (childResult.passed()) {
                    passed++;
                }
            }
            boolean result = passed > 0;
            String reason = passed + "/" + conditions.size() + " conditions passed (need at least 1)";
            return ConditionDebugResult.composite(this, result, reason, childResults);
        }
    }

    /**
     * Negates the contained condition.
     */
    record Not(@Nonnull DialogCondition condition) implements DialogCondition {
        public static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                DialogCondition.CODEC.fieldOf("condition").forGetter(Not::condition)
            ).apply(instance, Not::new)
        );

        @Override
        public String getType() {
            return "not";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            return !condition.test(player, context);
        }

        @Override
        @Nonnull
        public ConditionDebugResult debugTest(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            ConditionDebugResult childResult = condition.debugTest(player, context);
            boolean result = !childResult.passed();
            String reason = "Negates inner condition (" + (childResult.passed() ? "was true" : "was false") + ")";
            return ConditionDebugResult.composite(this, result, reason, List.of(childResult));
        }
    }

    /**
     * Custom condition for extensibility.
     */
    record Custom(
        @Nonnull String handlerId,
        @Nonnull CompoundTag data
    ) implements DialogCondition {
        public static final MapCodec<Custom> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                Codec.STRING.fieldOf("handlerId").forGetter(Custom::handlerId),
                CompoundTag.CODEC.optionalFieldOf("data", new CompoundTag()).forGetter(Custom::data)
            ).apply(instance, Custom::new)
        );

        public Custom(@Nonnull String handlerId) {
            this(handlerId, new CompoundTag());
        }

        @Override
        public String getType() {
            return "custom";
        }

        @Override
        public boolean test(@Nonnull ServerPlayer player, @Nonnull NpcContext context) {
            // Custom handlers would be registered externally
            return true;
        }

        @Override
        @Nonnull
        public String getDebugReason(@Nonnull ServerPlayer player, @Nonnull NpcContext context, boolean result) {
            return "Custom handler: " + handlerId + " (always returns true - no handler registered)";
        }
    }
}
