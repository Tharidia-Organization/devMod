package com.devmod.foundry.client.model.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import org.joml.Vector3f;

import com.google.gson.JsonObject;

import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.core.Direction;

import com.devmod.foundry.client.model.FoundryFluidCuboid;

/** Fluid cuboid that scales by a number of increments. */
public class FoundryIncrementalFluidCuboid extends FoundryFluidCuboid {
    public FoundryIncrementalFluidCuboid(Vector3f from, Vector3f to, Map<Direction, FluidFace> faces, int increments) {
        super(from, to, faces, increments);
    }

    @Override
    public int getIncrements() {
        return super.getIncrements();
    }

    public BlockElement getPart(int amount, boolean gas) {
        Vector3f from = getFrom();
        Vector3f to = getTo();
        float minY = from.y();
        float maxY = to.y();
        int increments = getIncrements();
        if (gas) {
            from = new Vector3f(from);
            from.y = maxY + (amount * (minY - maxY) / increments);
        } else {
            to = new Vector3f(to);
            to.y = minY + (amount * (maxY - minY) / increments);
        }

        Map<Direction, BlockElementFace> faces = new EnumMap<>(Direction.class);
        for (Entry<Direction, FluidFace> entry : getFaces().entrySet()) {
            Direction dir = entry.getKey();
            FluidFace face = entry.getValue();
            boolean isFlowing = face.isFlowing();
            faces.put(dir, new BlockElementFace(
                null,
                0,
                isFlowing ? "flowing_fluid" : "fluid",
                getFaceUvs(from, to, dir, face.rotation(), isFlowing ? 0.5f : 1f)
            ));
        }

        return new BlockElement(from, to, faces, null, false);
    }

    private static BlockFaceUV getFaceUvs(Vector3f from, Vector3f to, Direction side, int rotation, float scale) {
        float u1, u2, v1, v2;
        switch (side) {
            case DOWN -> {
                u1 = from.x(); v1 = 16f - to.z();
                u2 = to.x();   v2 = 16f - from.z();
            }
            case UP -> {
                u1 = from.x(); v1 = from.z();
                u2 = to.x();   v2 = to.z();
            }
            default -> {
                u1 = 16f - to.x();   v1 = 16f - to.y();
                u2 = 16f - from.x(); v2 = 16f - from.y();
            }
            case SOUTH -> {
                u1 = from.x(); v1 = 16f - to.y();
                u2 = to.x();   v2 = 16f - from.y();
            }
            case WEST -> {
                u1 = from.z(); v1 = 16f - to.y();
                u2 = to.z();   v2 = 16f - from.y();
            }
            case EAST -> {
                u1 = 16f - to.z();   v1 = 16f - to.y();
                u2 = 16f - from.z(); v2 = 16f - from.y();
            }
        }
        if (rotation >= 180) {
            float temp = v1;
            v1 = 16f - v2;
            v2 = 16f - temp;
        }
        if (rotation == 90 || rotation == 180) {
            float temp = u1;
            u1 = 16f - u2;
            u2 = 16f - temp;
        }
        float[] uv;
        if ((rotation % 180) == 90) {
            uv = new float[] {v1 * scale, u1 * scale, v2 * scale, u2 * scale};
        } else {
            uv = new float[] {u1 * scale, v1 * scale, u2 * scale, v2 * scale};
        }
        return new BlockFaceUV(uv, rotation);
    }

    public static FoundryIncrementalFluidCuboid fromJson(JsonObject json) {
        FoundryFluidCuboid base = FoundryFluidCuboid.fromJson(json);
        return new FoundryIncrementalFluidCuboid(base.getFrom(), base.getTo(), base.getFaces(), base.getIncrements());
    }
}
