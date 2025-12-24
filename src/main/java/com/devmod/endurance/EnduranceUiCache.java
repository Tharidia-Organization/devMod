package com.devmod.endurance;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

@OnlyIn(Dist.CLIENT)
public final class EnduranceUiCache {
    @Nullable
    private static volatile PerkChoicesPayload lastPerkChoices;
    @Nullable
    private static volatile QuestCompletionPayload lastQuestCompletion;
    @Nullable
    private static volatile WaveDirectiveChoicesPayload lastDirectiveChoices;

    private EnduranceUiCache() {}

    public static void setLastPerkChoices(@Nullable PerkChoicesPayload payload) {
        lastPerkChoices = payload;
    }

    @Nullable
    public static PerkChoicesPayload getLastPerkChoices() {
        return lastPerkChoices;
    }

    public static void setLastQuestCompletion(@Nullable QuestCompletionPayload payload) {
        lastQuestCompletion = payload;
    }

    @Nullable
    public static QuestCompletionPayload getLastQuestCompletion() {
        return lastQuestCompletion;
    }

    public static void setLastDirectiveChoices(@Nullable WaveDirectiveChoicesPayload payload) {
        lastDirectiveChoices = payload;
    }

    @Nullable
    public static WaveDirectiveChoicesPayload getLastDirectiveChoices() {
        return lastDirectiveChoices;
    }

    public static void clear() {
        lastPerkChoices = null;
        lastQuestCompletion = null;
        lastDirectiveChoices = null;
    }
}
