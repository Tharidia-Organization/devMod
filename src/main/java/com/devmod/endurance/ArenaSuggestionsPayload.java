package com.devmod.endurance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.arena.policy.TemplateSuggestion;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client: Arena template suggestions for a mob.
 */
public record ArenaSuggestionsPayload(
    String mobId,
    List<TemplateSuggestion> suggestions,
    @Nullable String selectedTemplateId
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_STRING_LENGTH = 256;
    private static final int MAX_SUGGESTIONS = 20;
    private static final int MAX_BREAKDOWN_ENTRIES = 10;

    public static final Type<ArenaSuggestionsPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "arena_suggestions"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaSuggestionsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ArenaSuggestionsPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            String mobId = Objects.requireNonNull(buf.readUtf(MAX_STRING_LENGTH));

            int count = Math.min(buf.readVarInt(), MAX_SUGGESTIONS);
            List<TemplateSuggestion> suggestions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                suggestions.add(decodeSuggestion(buf));
            }

            String selectedTemplateId = null;
            if (buf.readBoolean()) {
                selectedTemplateId = buf.readUtf(MAX_STRING_LENGTH);
            }

            return new ArenaSuggestionsPayload(mobId, suggestions, selectedTemplateId);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull ArenaSuggestionsPayload payload) {
            buf.writeUtf(Objects.requireNonNull(payload.mobId));

            buf.writeVarInt(payload.suggestions.size());
            for (TemplateSuggestion suggestion : payload.suggestions) {
                encodeSuggestion(buf, suggestion);
            }

            buf.writeBoolean(payload.selectedTemplateId != null);
            if (payload.selectedTemplateId != null) {
                buf.writeUtf(payload.selectedTemplateId);
            }
        }

        private TemplateSuggestion decodeSuggestion(RegistryFriendlyByteBuf buf) {
            String templateId = buf.readUtf(MAX_STRING_LENGTH);
            String templateName = buf.readUtf(MAX_STRING_LENGTH);
            int size = buf.readVarInt();
            String biome = buf.readUtf(MAX_STRING_LENGTH);

            // Preview data fields
            String arenaShape = buf.readUtf(32);
            boolean hasWalls = buf.readBoolean();
            boolean hasHazards = buf.readBoolean();
            int spawnSlotCount = buf.readVarInt();

            double score = buf.readDouble();

            // Decode score breakdown
            int breakdownCount = Math.min(buf.readVarInt(), MAX_BREAKDOWN_ENTRIES);
            Map<String, Double> breakdown = new HashMap<>(breakdownCount);
            for (int i = 0; i < breakdownCount; i++) {
                String key = buf.readUtf(64);
                double value = buf.readDouble();
                breakdown.put(key, value);
            }

            boolean isSelected = buf.readBoolean();

            String incompatibilityReason = null;
            if (buf.readBoolean()) {
                incompatibilityReason = buf.readUtf(MAX_STRING_LENGTH);
            }

            return new TemplateSuggestion(
                templateId, templateName, size, biome, arenaShape, hasWalls, hasHazards, spawnSlotCount,
                score, breakdown, isSelected, incompatibilityReason
            );
        }

        private void encodeSuggestion(RegistryFriendlyByteBuf buf, TemplateSuggestion suggestion) {
            buf.writeUtf(Objects.requireNonNull(suggestion.templateId()));
            buf.writeUtf(Objects.requireNonNull(suggestion.templateName()));
            buf.writeVarInt(suggestion.size());
            String biome = suggestion.biome();
            buf.writeUtf(biome != null ? biome : "");

            // Preview data fields
            String arenaShape = suggestion.arenaShape();
            buf.writeUtf(arenaShape != null ? arenaShape : "RECTANGULAR");
            buf.writeBoolean(suggestion.hasWalls());
            buf.writeBoolean(suggestion.hasHazards());
            buf.writeVarInt(suggestion.spawnSlotCount());

            buf.writeDouble(suggestion.compatibilityScore());

            // Encode score breakdown
            Map<String, Double> breakdown = suggestion.scoreBreakdown();
            int validEntries = (int) breakdown.entrySet().stream()
                .filter(e -> e.getKey() != null)
                .count();
            buf.writeVarInt(validEntries);
            for (Map.Entry<String, Double> entry : breakdown.entrySet()) {
                String key = entry.getKey();
                if (key != null) {
                    buf.writeUtf(key);
                    buf.writeDouble(entry.getValue());
                }
            }

            buf.writeBoolean(suggestion.isSelected());

            String incompatReason = suggestion.incompatibilityReason();
            buf.writeBoolean(incompatReason != null);
            if (incompatReason != null) {
                buf.writeUtf(incompatReason);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = PayloadSizeUtil.estimatedUtfSize(mobId);
        size += PayloadSizeUtil.varIntSize(suggestions.size());
        for (TemplateSuggestion suggestion : suggestions) {
            size += estimateSuggestionSize(suggestion);
        }
        size += 1; // selectedTemplateId present flag
        if (selectedTemplateId != null) {
            size += PayloadSizeUtil.estimatedUtfSize(selectedTemplateId);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    private static long estimateSuggestionSize(TemplateSuggestion suggestion) {
        long size = PayloadSizeUtil.estimatedUtfSize(suggestion.templateId());
        size += PayloadSizeUtil.estimatedUtfSize(suggestion.templateName());
        size += PayloadSizeUtil.varIntSize(suggestion.size());
        size += PayloadSizeUtil.estimatedUtfSize(suggestion.biome());
        size += PayloadSizeUtil.estimatedUtfSize(suggestion.arenaShape());
        size += 1; // hasWalls
        size += 1; // hasHazards
        size += PayloadSizeUtil.varIntSize(suggestion.spawnSlotCount());
        size += 8; // compatibilityScore

        size += PayloadSizeUtil.varIntSize(suggestion.scoreBreakdown().size());
        for (Map.Entry<String, Double> entry : suggestion.scoreBreakdown().entrySet()) {
            size += PayloadSizeUtil.estimatedUtfSize(entry.getKey());
            size += 8; // value
        }

        size += 1; // isSelected
        size += 1; // incompatibilityReason present flag
        if (suggestion.incompatibilityReason() != null) {
            size += PayloadSizeUtil.estimatedUtfSize(suggestion.incompatibilityReason());
        }
        return size;
    }

    /**
     * Get the selected/recommended suggestion.
     */
    @Nullable
    public TemplateSuggestion getSelectedSuggestion() {
        if (selectedTemplateId == null) {
            return suggestions.isEmpty() ? null : suggestions.get(0);
        }
        return suggestions.stream()
            .filter(s -> s.templateId().equals(selectedTemplateId))
            .findFirst()
            .orElse(suggestions.isEmpty() ? null : suggestions.get(0));
    }
}
