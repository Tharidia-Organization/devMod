package com.devmod.foundry.client.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;

/**
 * Simple fluid cuboid used by tank-like models.
 * Supports faces for flowing textures and incremental scaling.
 */
public class FoundryFluidCuboid {
    public static final int DEFAULT_INCREMENTS = 256;

    private static final Map<Direction, FluidFace> DEFAULT_FACES;
    static {
        DEFAULT_FACES = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.values()) {
            DEFAULT_FACES.put(direction, FluidFace.NORMAL);
        }
    }

    private final Vector3f from;
    private final Vector3f to;
    private final Map<Direction, FluidFace> faces;
    private final int increments;

    @Nullable
    private Vector3f fromScaled;
    @Nullable
    private Vector3f toScaled;

    public FoundryFluidCuboid(Vector3f from, Vector3f to, Map<Direction, FluidFace> faces, int increments) {
        this.from = from;
        this.to = to;
        this.faces = faces.isEmpty() ? DEFAULT_FACES : new EnumMap<>(faces);
        this.increments = Math.max(1, increments);
    }

    public Vector3f getFrom() {
        return from;
    }

    public Vector3f getTo() {
        return to;
    }

    public Map<Direction, FluidFace> getFaces() {
        return faces;
    }

    @Nullable
    public FluidFace getFace(Direction face) {
        return faces.get(face);
    }

    public int getIncrements() {
        return increments;
    }

    public Vector3f getFromScaled() {
        if (fromScaled == null) {
            fromScaled = new Vector3f(from);
            fromScaled.mul(1 / 16f);
        }
        return fromScaled;
    }

    public Vector3f getToScaled() {
        if (toScaled == null) {
            toScaled = new Vector3f(to);
            toScaled.mul(1 / 16f);
        }
        return toScaled;
    }

    /** Represents a single fluid face in the model. */
    public record FluidFace(boolean isFlowing, int rotation) {
        public static final FluidFace NORMAL = new FluidFace(false, 0);

        public FluidFace {
            if (!isValidRotation(rotation)) {
                throw new IllegalArgumentException("Rotation must be 0/90/180/270");
            }
        }
    }

    /**
     * Create a fluid cuboid with default increments (256 levels).
     */
    public static FoundryFluidCuboid create(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return create(minX, minY, minZ, maxX, maxY, maxZ, DEFAULT_INCREMENTS);
    }

    /**
     * Create a fluid cuboid with custom increments.
     */
    public static FoundryFluidCuboid create(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int increments) {
        return new FoundryFluidCuboid(
            new Vector3f(minX, minY, minZ),
            new Vector3f(maxX, maxY, maxZ),
            DEFAULT_FACES,
            increments
        );
    }

    /** Parses a fluid cuboid from JSON. */
    public static FoundryFluidCuboid fromJson(JsonObject json) {
        Vector3f from = readVec3(json, "from");
        Vector3f to = readVec3(json, "to");
        int increments = GsonHelper.getAsInt(json, "increments", DEFAULT_INCREMENTS);

        Map<Direction, FluidFace> faces = new EnumMap<>(Direction.class);
        if (json.has("faces")) {
            JsonObject facesJson = GsonHelper.getAsJsonObject(json, "faces");
            for (Entry<String, JsonElement> entry : facesJson.entrySet()) {
                Direction direction = Direction.byName(entry.getKey());
                if (direction == null) {
                    continue;
                }
                JsonObject faceJson = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey());
                boolean flowing = GsonHelper.getAsBoolean(faceJson, "flowing", false);
                int rotation = GsonHelper.getAsInt(faceJson, "rotation", 0);
                faces.put(direction, new FluidFace(flowing, rotation));
            }
        }
        if (faces.isEmpty()) {
            faces = DEFAULT_FACES;
        }
        return new FoundryFluidCuboid(from, to, faces, increments);
    }

    private static Vector3f readVec3(JsonObject json, String key) {
        var array = GsonHelper.getAsJsonArray(json, key);
        if (array.size() != 3) {
            throw new IllegalArgumentException("Expected " + key + " to have 3 elements");
        }
        float x = GsonHelper.convertToFloat(array.get(0), key + "[0]");
        float y = GsonHelper.convertToFloat(array.get(1), key + "[1]");
        float z = GsonHelper.convertToFloat(array.get(2), key + "[2]");
        return new Vector3f(x, y, z);
    }

    private static boolean isValidRotation(int rotation) {
        return rotation >= 0 && rotation < 360 && (rotation % 90 == 0);
    }

    /**
     * Get scaled fluid bounds for a given fill amount.
     * @param amount Current amount (0 to increments)
     * @param isGas If true, fluid fills from top (for gases lighter than air)
     * @return ScaledBounds with adjusted from/to vectors
     */
    @Nullable
    public ScaledBounds getScaledBounds(int amount, boolean isGas) {
        if (amount <= 0) {
            return null;
        }

        float minX = from.x() / 16f;
        float minY = from.y() / 16f;
        float minZ = from.z() / 16f;
        float maxX = to.x() / 16f;
        float maxY = to.y() / 16f;
        float maxZ = to.z() / 16f;

        int steps = increments;
        amount = Math.min(amount, steps);

        float scaledMinY;
        float scaledMaxY;
        if (isGas) {
            scaledMaxY = maxY;
            scaledMinY = maxY - (amount * (maxY - minY) / steps);
        } else {
            scaledMinY = minY;
            scaledMaxY = minY + (amount * (maxY - minY) / steps);
        }

        return new ScaledBounds(
            new Vector3f(minX, scaledMinY, minZ),
            new Vector3f(maxX, scaledMaxY, maxZ)
        );
    }

    /**
     * Get scaled bounds based on fill percentage (0.0 to 1.0).
     */
    @Nullable
    public ScaledBounds getScaledBounds(float fillPercentage, boolean isGas) {
        int amount = (int) (fillPercentage * increments);
        return getScaledBounds(amount, isGas);
    }

    /**
     * Scaled bounds result for fluid rendering.
     */
    public record ScaledBounds(Vector3f from, Vector3f to) {
        public float minX() { return from.x(); }
        public float minY() { return from.y(); }
        public float minZ() { return from.z(); }
        public float maxX() { return to.x(); }
        public float maxY() { return to.y(); }
        public float maxZ() { return to.z(); }

        public float[] getUVs(float u0, float v0, float u1, float v1) {
            return new float[] { u0, v0, u1, v1 };
        }
    }
}
