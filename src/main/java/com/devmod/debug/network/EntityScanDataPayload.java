package com.devmod.debug.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadSizeUtil;
import com.devmod.network.PayloadValidation;

/**
 * Server → Client: Entity scan data from an EntityScanner block.
 */
public record EntityScanDataPayload(
    BlockPos scannerPos,
    int scanRadius,
    List<ScannedEntity> entities
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    private static final int MAX_ENTITIES = 100;
    private static final int MAX_STRING_LENGTH = 256;

    public static final Type<EntityScanDataPayload> TYPE = new Type<>(
        Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "entity_scan_data"))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityScanDataPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EntityScanDataPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int radius = buf.readVarInt();

            int entityCount = Math.min(buf.readVarInt(), MAX_ENTITIES);
            List<ScannedEntity> entities = new ArrayList<>(entityCount);
            for (int i = 0; i < entityCount; i++) {
                entities.add(decodeEntity(buf));
            }

            return new EntityScanDataPayload(pos, radius, entities);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull EntityScanDataPayload payload) {
            buf.writeBlockPos(payload.scannerPos);
            buf.writeVarInt(payload.scanRadius);

            buf.writeVarInt(payload.entities.size());
            for (ScannedEntity entity : payload.entities) {
                encodeEntity(buf, entity);
            }
        }

        private ScannedEntity decodeEntity(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            String name = buf.readUtf(MAX_STRING_LENGTH);
            String type = buf.readUtf(MAX_STRING_LENGTH);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float health = buf.readFloat();
            float maxHealth = buf.readFloat();

            // Attributes
            int attrCount = buf.readVarInt();
            List<AttributeData> attributes = new ArrayList<>(attrCount);
            for (int i = 0; i < attrCount; i++) {
                attributes.add(new AttributeData(
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readDouble(),
                    buf.readDouble()
                ));
            }

            // Equipment
            int equipCount = buf.readVarInt();
            List<EquipmentData> equipment = new ArrayList<>(equipCount);
            for (int i = 0; i < equipCount; i++) {
                equipment.add(new EquipmentData(
                    buf.readUtf(32),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readVarInt()
                ));
            }

            // Goals
            int goalCount = buf.readVarInt();
            List<GoalData> goals = new ArrayList<>(goalCount);
            for (int i = 0; i < goalCount; i++) {
                goals.add(new GoalData(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(MAX_STRING_LENGTH)
                ));
            }

            // Target goals
            int targetGoalCount = buf.readVarInt();
            List<GoalData> targetGoals = new ArrayList<>(targetGoalCount);
            for (int i = 0; i < targetGoalCount; i++) {
                targetGoals.add(new GoalData(
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(MAX_STRING_LENGTH)
                ));
            }

            return new ScannedEntity(
                entityId, name, type, x, y, z, health, maxHealth,
                attributes, equipment, goals, targetGoals
            );
        }

        private void encodeEntity(RegistryFriendlyByteBuf buf, ScannedEntity entity) {
            buf.writeVarInt(entity.entityId);
            buf.writeUtf(entity.name);
            buf.writeUtf(entity.type);
            buf.writeDouble(entity.x);
            buf.writeDouble(entity.y);
            buf.writeDouble(entity.z);
            buf.writeFloat(entity.health);
            buf.writeFloat(entity.maxHealth);

            // Attributes
            buf.writeVarInt(entity.attributes.size());
            for (AttributeData attr : entity.attributes) {
                buf.writeUtf(attr.name);
                buf.writeDouble(attr.baseValue);
                buf.writeDouble(attr.currentValue);
            }

            // Equipment
            buf.writeVarInt(entity.equipment.size());
            for (EquipmentData equip : entity.equipment) {
                buf.writeUtf(equip.slot);
                buf.writeUtf(equip.itemName);
                buf.writeVarInt(equip.count);
            }

            // Goals
            buf.writeVarInt(entity.goals.size());
            for (GoalData goal : entity.goals) {
                buf.writeVarInt(goal.priority);
                buf.writeBoolean(goal.isRunning);
                buf.writeUtf(goal.name);
            }

            // Target goals
            buf.writeVarInt(entity.targetGoals.size());
            for (GoalData goal : entity.targetGoals) {
                buf.writeVarInt(goal.priority);
                buf.writeBoolean(goal.isRunning);
                buf.writeUtf(goal.name);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        long size = 8; // BlockPos (long)
        size += PayloadSizeUtil.varIntSize(scanRadius);
        size += PayloadSizeUtil.varIntSize(entities.size());
        for (ScannedEntity entity : entities) {
            size += estimateEntitySize(entity);
        }
        return PayloadSizeUtil.clampToInt(size);
    }

    private static long estimateEntitySize(ScannedEntity entity) {
        long size = PayloadSizeUtil.varIntSize(entity.entityId);
        size += PayloadSizeUtil.estimatedUtfSize(entity.name);
        size += PayloadSizeUtil.estimatedUtfSize(entity.type);
        size += 8L * 3; // x/y/z
        size += 4; // health
        size += 4; // maxHealth

        size += PayloadSizeUtil.varIntSize(entity.attributes.size());
        for (AttributeData attr : entity.attributes) {
            size += PayloadSizeUtil.estimatedUtfSize(attr.name);
            size += 8L * 2; // baseValue/currentValue
        }

        size += PayloadSizeUtil.varIntSize(entity.equipment.size());
        for (EquipmentData equip : entity.equipment) {
            size += PayloadSizeUtil.estimatedUtfSize(equip.slot);
            size += PayloadSizeUtil.estimatedUtfSize(equip.itemName);
            size += PayloadSizeUtil.varIntSize(equip.count);
        }

        size += estimateGoalListSize(entity.goals);
        size += estimateGoalListSize(entity.targetGoals);
        return size;
    }

    private static long estimateGoalListSize(List<GoalData> goals) {
        long size = PayloadSizeUtil.varIntSize(goals.size());
        for (GoalData goal : goals) {
            size += PayloadSizeUtil.varIntSize(goal.priority);
            size += 1; // isRunning
            size += PayloadSizeUtil.estimatedUtfSize(goal.name);
        }
        return size;
    }

    /**
     * Scanned entity data.
     */
    public record ScannedEntity(
        int entityId,
        String name,
        String type,
        double x,
        double y,
        double z,
        float health,
        float maxHealth,
        List<AttributeData> attributes,
        List<EquipmentData> equipment,
        List<GoalData> goals,
        List<GoalData> targetGoals
    ) {}

    /**
     * Attribute data.
     */
    public record AttributeData(
        String name,
        double baseValue,
        double currentValue
    ) {}

    /**
     * Equipment slot data.
     */
    public record EquipmentData(
        String slot,
        String itemName,
        int count
    ) {}

    /**
     * AI goal data.
     */
    public record GoalData(
        int priority,
        boolean isRunning,
        String name
    ) {}

    /**
     * Server → Client: Open the scanner screen.
     */
    public record OpenScreenPayload(BlockPos scannerPos) implements CustomPacketPayload, PayloadValidation.SizedPayload {
        public static final Type<OpenScreenPayload> TYPE = new Type<>(
            Objects.requireNonNull(ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "entity_scanner_open"))
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, OpenScreenPayload> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public OpenScreenPayload decode(@Nonnull RegistryFriendlyByteBuf buf) {
                return new OpenScreenPayload(buf.readBlockPos());
            }

            @Override
            public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull OpenScreenPayload payload) {
                buf.writeBlockPos(payload.scannerPos);
            }
        };

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        @Override
        public int estimatedSize() {
            return 8;
        }
    }
}
