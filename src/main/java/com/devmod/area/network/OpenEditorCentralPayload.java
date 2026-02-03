package com.devmod.area.network;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.devmod.DevMod;
import com.devmod.network.PayloadValidation;
import com.devmod.network.PayloadSizeUtil;

/**
 * Server -> Client payload to open the Nexus Editor Central screen.
 * Contains summary data about existing areas for the central management view.
 */
public record OpenEditorCentralPayload(
    List<AreaSummary> areas,
    @Nullable UUID mainHubId,
    boolean canCreateAreas,
    /** H-07 fix: Indicates if more areas exist beyond MAX_AREAS limit */
    boolean hasMoreAreas,
    /** H-07 fix: Total area count on server (may exceed areas.size()) */
    int totalAreaCount
) implements CustomPacketPayload, PayloadValidation.SizedPayload {

    /** Compatibility constructor for existing code */
    public OpenEditorCentralPayload(List<AreaSummary> areas, @Nullable UUID mainHubId, boolean canCreateAreas) {
        this(areas, mainHubId, canCreateAreas, false, areas.size());
    }

    /** Maximum number of areas that can be sent in a single payload to prevent OOM */
    public static final int MAX_AREAS = 1000;

    public static final ResourceLocation ID = Objects.requireNonNull(
        ResourceLocation.fromNamespaceAndPath(DevMod.MODID, "open_editor_central")
    );
    public static final Type<OpenEditorCentralPayload> TYPE = new Type<>(Objects.requireNonNull(ID));

    public static final StreamCodec<FriendlyByteBuf, OpenEditorCentralPayload> STREAM_CODEC =
        StreamCodec.of(OpenEditorCentralPayload::encode, OpenEditorCentralPayload::decode);

    /**
     * Summary data for an area (lightweight for list display).
     */
    public record AreaSummary(
        UUID id,
        String name,
        String shapeType,
        int width,
        int length,
        int height,
        boolean isMainHub,
        long createdAt
    ) {
        public static final StreamCodec<FriendlyByteBuf, AreaSummary> STREAM_CODEC =
            StreamCodec.of(AreaSummary::encode, AreaSummary::decode);

        private static void encode(FriendlyByteBuf buf, AreaSummary summary) {
            buf.writeUUID(Objects.requireNonNull(summary.id));
            buf.writeUtf(Objects.requireNonNull(summary.name), 128);
            buf.writeUtf(Objects.requireNonNull(summary.shapeType), 32);
            buf.writeVarInt(summary.width);
            buf.writeVarInt(summary.length);
            buf.writeVarInt(summary.height);
            buf.writeBoolean(summary.isMainHub);
            buf.writeLong(summary.createdAt);
        }

        private static AreaSummary decode(FriendlyByteBuf buf) {
            return new AreaSummary(
                buf.readUUID(),
                buf.readUtf(128),
                buf.readUtf(32),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readLong()
            );
        }
    }

    private static void encode(FriendlyByteBuf buf, OpenEditorCentralPayload payload) {
        // Areas list
        buf.writeVarInt(payload.areas.size());
        for (AreaSummary area : payload.areas) {
            AreaSummary.STREAM_CODEC.encode(buf, Objects.requireNonNull(area));
        }

        // Main hub ID (optional)
        buf.writeBoolean(payload.mainHubId != null);
        if (payload.mainHubId != null) {
            buf.writeUUID(payload.mainHubId);
        }

        // Can create areas
        buf.writeBoolean(payload.canCreateAreas);

        // H-07 fix: Write truncation info
        buf.writeBoolean(payload.hasMoreAreas);
        buf.writeVarInt(payload.totalAreaCount);
    }

    private static OpenEditorCentralPayload decode(FriendlyByteBuf buf) {
        int sentAreas = Math.max(0, buf.readVarInt());
        int areaCount = Math.min(sentAreas, MAX_AREAS);
        List<AreaSummary> areas = new java.util.ArrayList<>(areaCount);
        for (int i = 0; i < areaCount; i++) {
            areas.add(AreaSummary.STREAM_CODEC.decode(buf));
        }
        // Skip any extra areas beyond MAX_AREAS (shouldn't happen with proper encoding)
        for (int i = areaCount; i < sentAreas; i++) {
            AreaSummary.STREAM_CODEC.decode(buf);
        }

        UUID mainHubId = buf.readBoolean() ? buf.readUUID() : null;
        boolean canCreateAreas = buf.readBoolean();

        // H-07 fix: Read truncation info
        boolean hasMoreAreas = buf.readBoolean();
        int totalAreaCount = buf.readVarInt();

        return new OpenEditorCentralPayload(List.copyOf(areas), mainHubId, canCreateAreas, hasMoreAreas, totalAreaCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @Override
    public int estimatedSize() {
        int size = PayloadSizeUtil.varIntSize(areas.size());
        size += 1; // mainHubId present
        size += 1; // canCreateAreas
        size += 1; // hasMoreAreas
        size += PayloadSizeUtil.varIntSize(totalAreaCount);
        if (mainHubId != null) {
            size += 16;
        }
        for (AreaSummary summary : areas) {
            size += 16; // UUID
            size += PayloadSizeUtil.estimatedUtfSize(summary.name());
            size += PayloadSizeUtil.estimatedUtfSize(summary.shapeType());
            size += PayloadSizeUtil.varIntSize(summary.width());
            size += PayloadSizeUtil.varIntSize(summary.length());
            size += PayloadSizeUtil.varIntSize(summary.height());
            size += 1; // isMainHub
            size += 8; // createdAt
        }
        return size;
    }

    /**
     * Returns true if there are any areas.
     */
    public boolean hasAreas() {
        return !areas.isEmpty();
    }

    /**
     * Returns true if there's a main hub defined.
     */
    public boolean hasMainHub() {
        return mainHubId != null;
    }
}
