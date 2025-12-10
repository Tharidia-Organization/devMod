package com.frenkvs.devmod.endurance;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Network payload sent from server to client with perk choices after wave completion.
 * Contains serialized perk data for display in PerkSelectionScreen.
 */
@SuppressWarnings({"null", "unused"})
public record PerkChoicesPayload(
    int waveNumber,
    List<PerkChoice> choices
) implements CustomPacketPayload {

    private static final int MAX_CHOICES = 5;
    private static final int MAX_STRING_LENGTH = 256;

    public static final Type<PerkChoicesPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("devmod", "perk_choices")
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
        int maxStacks
    ) {
        public static PerkChoice from(PerkSystem.Perk perk, int currentStacks) {
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
                perk.maxStacks
            );
        }
    }

    public static final StreamCodec<ByteBuf, PerkChoicesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PerkChoicesPayload decode(ByteBuf buf) {
            int waveNumber = buf.readInt();
            int choiceCount = Math.min(buf.readInt(), MAX_CHOICES);

            List<PerkChoice> choices = new ArrayList<>();
            for (int i = 0; i < choiceCount; i++) {
                choices.add(decodePerkChoice(buf));
            }

            return new PerkChoicesPayload(waveNumber, choices);
        }

        @Override
        public void encode(ByteBuf buf, PerkChoicesPayload payload) {
            buf.writeInt(payload.waveNumber);
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

            return new PerkChoice(id, name, description, tierName, tierColor,
                categoryName, categoryColor, stackable, currentStacks, maxStacks);
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
