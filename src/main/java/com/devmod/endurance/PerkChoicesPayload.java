package com.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * Network payload sent from server to client with perk choices after wave completion.
 * Contains serialized perk data plus an expiry timestamp for countdown UI.
 */
public record PerkChoicesPayload(
    int waveNumber,
    long expiresAt,
    List<PerkChoice> choices
) implements CustomPacketPayload {

    private static final int MAX_CHOICES = 5;
    private static final int MAX_STRING_LENGTH = 256;

    public static final Type<PerkChoicesPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath("devmod", "perk_choices"))
    );

    /**
     * Serialized perk data for client display.
     */
    public record PerkChoice(
        String id,
        String name,
        String description,
        String tierName,
        int tierColor,
        String categoryName,
        int categoryColor,
        boolean stackable,
        int currentStacks,
        int maxStacks,
        boolean suggested,
        boolean required,
        // Synergy preview data
        int synergyScore,        // Total synergy value (0-10+)
        String synergyHint,      // Short description of best synergy
        int newSynergyCount,     // Number of synergies this would complete
        int potentialCount,      // Number of synergies this advances
        boolean isRecommended    // True if completing a near-active synergy
    ) {
        public static PerkChoice from(PerkSystem.Perk perk, int currentStacks, boolean suggested, boolean required) {
            return new PerkChoice(
                perk.id,
                perk.name,
                perk.description,
                perk.tier.displayName,
                perk.tier.color,
                perk.category.displayName,
                perk.category.color,
                perk.stackable,
                currentStacks,
                perk.maxStacks,
                suggested,
                required,
                0, "", 0, 0, false  // Default synergy values
            );
        }

        /**
         * Create a PerkChoice with synergy preview data.
         */
        public static PerkChoice fromWithSynergy(PerkSystem.Perk perk, int currentStacks,
                boolean suggested, boolean required, PerkSynergySystem.SynergyPreview preview) {
            String hint = "";
            if (!preview.newSynergies().isEmpty()) {
                hint = "Completes: " + preview.newSynergies().get(0).synergy().name;
            } else if (!preview.potentialSynergies().isEmpty()) {
                var potential = preview.potentialSynergies().get(0);
                int missing = potential.missingPerks().size();
                hint = potential.synergy().name + " (" + missing + " away)";
            } else if (!preview.activeSynergies().isEmpty()) {
                hint = "Enhances: " + preview.activeSynergies().get(0).synergy().name;
            }

            return new PerkChoice(
                perk.id,
                perk.name,
                perk.description,
                perk.tier.displayName,
                perk.tier.color,
                perk.category.displayName,
                perk.category.color,
                perk.stackable,
                currentStacks,
                perk.maxStacks,
                suggested,
                required,
                preview.totalSynergyScore(),
                hint,
                preview.newSynergies().size(),
                preview.potentialSynergies().size(),
                !preview.newSynergies().isEmpty()
            );
        }

        /**
         * Check if this perk has synergy data.
         */
        public boolean hasSynergyInfo() {
            return synergyScore > 0 || !synergyHint.isEmpty() || newSynergyCount > 0 || potentialCount > 0;
        }
    }

    public static final StreamCodec<ByteBuf, PerkChoicesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PerkChoicesPayload decode(@Nonnull ByteBuf buf) {
            int waveNumber = buf.readInt();
            long expiresAt = buf.readLong();
            int choiceCount = Math.min(buf.readInt(), MAX_CHOICES);

            List<PerkChoice> choices = new ArrayList<>();
            for (int i = 0; i < choiceCount; i++) {
                choices.add(decodePerkChoice(buf));
            }

            return new PerkChoicesPayload(waveNumber, expiresAt, choices);
        }

        @Override
        public void encode(@Nonnull ByteBuf buf, @Nonnull PerkChoicesPayload payload) {
            buf.writeInt(payload.waveNumber);
            buf.writeLong(payload.expiresAt);
            buf.writeInt(Math.min(payload.choices.size(), MAX_CHOICES));

            for (int i = 0; i < Math.min(payload.choices.size(), MAX_CHOICES); i++) {
                encodePerkChoice(buf, payload.choices.get(i));
            }
        }

        private PerkChoice decodePerkChoice(ByteBuf buf) {
            String id = readString(buf);
            String name = readString(buf);
            String description = readString(buf);
            String tierName = readString(buf);
            int tierColor = buf.readInt();
            String categoryName = readString(buf);
            int categoryColor = buf.readInt();
            boolean stackable = buf.readBoolean();
            int currentStacks = buf.readInt();
            int maxStacks = buf.readInt();
            boolean suggested = buf.readBoolean();
            boolean required = buf.readBoolean();
            // Synergy data
            int synergyScore = buf.readInt();
            String synergyHint = readString(buf);
            int newSynergyCount = buf.readInt();
            int potentialCount = buf.readInt();
            boolean isRecommended = buf.readBoolean();

            return new PerkChoice(id, name, description, tierName, tierColor,
                categoryName, categoryColor, stackable, currentStacks, maxStacks, suggested, required,
                synergyScore, synergyHint, newSynergyCount, potentialCount, isRecommended);
        }

        private void encodePerkChoice(ByteBuf buf, PerkChoice choice) {
            writeString(buf, choice.id);
            writeString(buf, choice.name);
            writeString(buf, choice.description);
            writeString(buf, choice.tierName);
            buf.writeInt(choice.tierColor);
            writeString(buf, choice.categoryName);
            buf.writeInt(choice.categoryColor);
            buf.writeBoolean(choice.stackable);
            buf.writeInt(choice.currentStacks);
            buf.writeInt(choice.maxStacks);
            buf.writeBoolean(choice.suggested);
            buf.writeBoolean(choice.required);
            // Synergy data
            buf.writeInt(choice.synergyScore);
            writeString(buf, choice.synergyHint);
            buf.writeInt(choice.newSynergyCount);
            buf.writeInt(choice.potentialCount);
            buf.writeBoolean(choice.isRecommended);
        }

        private String readString(ByteBuf buf) {
            int length = buf.readInt();
            if (length < 0 || length > MAX_STRING_LENGTH) {
                length = 0;
            }
            if (length == 0) return "";
            return buf.readCharSequence(length, StandardCharsets.UTF_8).toString();
        }

        private void writeString(ByteBuf buf, String str) {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(bytes.length, MAX_STRING_LENGTH);
            buf.writeInt(length);
            buf.writeBytes(bytes, 0, length);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
