# 02 - Mesh Generation (Geodesic Sphere)

> Last updated: 2025-12-26
> Status: NEEDS_VERIFICATION

## Obiettivo

Creare una mesh sferica esagonale/pentagonale usando la tecnica dell'icosahedron subdivision per lo scudo energetico.

## Teoria

### Icosahedron
- Solido platonico con 20 facce triangolari
- 12 vertici, 30 edges
- Base perfetta per subdivision

### Subdivision
- Ogni triangolo viene diviso in 4 triangoli più piccoli
- I nuovi vertici vengono proiettati sulla sfera
- Più iterazioni = mesh più liscia

```
Subdivision 0: 20 triangoli (icosahedron base)
Subdivision 1: 80 triangoli
Subdivision 2: 320 triangoli
Subdivision 3: 1280 triangoli (raccomandato)
```

## Implementazione

### Nuovo File: `HexagonalShieldMesh.java`

```java
package com.devmod.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/**
 * Generates a geodesic sphere mesh from icosahedron subdivision.
 * Used for energy shield rendering.
 */
public class HexagonalShieldMesh {

    // Golden ratio for icosahedron vertices
    private static final float PHI = (1.0f + (float) Math.sqrt(5.0)) / 2.0f;

    // Cached mesh data
    private final List<Vec3> vertices;
    private final List<int[]> triangles;
    private final List<Vec3> normals;
    private final int subdivisions;

    /**
     * Creates a geodesic sphere mesh.
     *
     * @param subdivisions Number of subdivision iterations (0-4 recommended)
     * @param radius Sphere radius
     */
    public HexagonalShieldMesh(int subdivisions, float radius) {
        this.subdivisions = subdivisions;
        this.vertices = new ArrayList<>();
        this.triangles = new ArrayList<>();
        this.normals = new ArrayList<>();

        generateIcosahedron(radius);
        for (int i = 0; i < subdivisions; i++) {
            subdivide(radius);
        }
        calculateNormals();
    }

    /**
     * Generates the base icosahedron (20 faces).
     */
    private void generateIcosahedron(float radius) {
        // Normalize factor
        float norm = (float) Math.sqrt(1.0 + PHI * PHI);

        // 12 vertices of icosahedron
        float[][] baseVertices = {
            {-1,  PHI, 0}, { 1,  PHI, 0}, {-1, -PHI, 0}, { 1, -PHI, 0},
            { 0, -1,  PHI}, { 0,  1,  PHI}, { 0, -1, -PHI}, { 0,  1, -PHI},
            { PHI, 0, -1}, { PHI, 0,  1}, {-PHI, 0, -1}, {-PHI, 0,  1}
        };

        // Scale to radius
        for (float[] v : baseVertices) {
            float scale = radius / norm;
            vertices.add(new Vec3(v[0] * scale, v[1] * scale, v[2] * scale));
        }

        // 20 triangular faces
        int[][] faces = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
            {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
            {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };

        for (int[] face : faces) {
            triangles.add(face);
        }
    }

    /**
     * Subdivides all triangles (each becomes 4 triangles).
     */
    private void subdivide(float radius) {
        Map<Long, Integer> midpointCache = new HashMap<>();
        List<int[]> newTriangles = new ArrayList<>();

        for (int[] tri : triangles) {
            // Get midpoints of each edge
            int a = getMidpoint(tri[0], tri[1], radius, midpointCache);
            int b = getMidpoint(tri[1], tri[2], radius, midpointCache);
            int c = getMidpoint(tri[2], tri[0], radius, midpointCache);

            // Create 4 new triangles
            newTriangles.add(new int[]{tri[0], a, c});
            newTriangles.add(new int[]{tri[1], b, a});
            newTriangles.add(new int[]{tri[2], c, b});
            newTriangles.add(new int[]{a, b, c});
        }

        triangles.clear();
        triangles.addAll(newTriangles);
    }

    /**
     * Gets or creates midpoint vertex between two vertices.
     * Projects the midpoint onto the sphere.
     */
    private int getMidpoint(int i1, int i2, float radius, Map<Long, Integer> cache) {
        // Create unique key for edge (order-independent)
        long key = (Math.min(i1, i2) << 32) | Math.max(i1, i2);

        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        Vec3 v1 = vertices.get(i1);
        Vec3 v2 = vertices.get(i2);

        // Calculate midpoint
        Vec3 mid = v1.add(v2).scale(0.5);

        // Project onto sphere
        Vec3 normalized = mid.normalize().scale(radius);

        int index = vertices.size();
        vertices.add(normalized);
        cache.put(key, index);

        return index;
    }

    /**
     * Calculates normals for each vertex (points outward from center).
     */
    private void calculateNormals() {
        normals.clear();
        for (Vec3 v : vertices) {
            normals.add(v.normalize());
        }
    }

    /**
     * Renders the mesh to a VertexConsumer.
     *
     * @param consumer Vertex consumer (usually from BufferBuilder)
     * @param centerX Center X position
     * @param centerY Center Y position
     * @param centerZ Center Z position
     * @param color ARGB color
     */
    public void render(VertexConsumer consumer, double centerX, double centerY,
                       double centerZ, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int[] tri : triangles) {
            for (int idx : tri) {
                Vec3 v = vertices.get(idx);
                Vec3 n = normals.get(idx);

                consumer.addVertex(
                    (float)(centerX + v.x),
                    (float)(centerY + v.y),
                    (float)(centerZ + v.z)
                );
                consumer.setColor(r, g, b, a);
                consumer.setNormal((float)n.x, (float)n.y, (float)n.z);
            }
        }
    }

    /**
     * Gets vertex count for statistics.
     */
    public int getVertexCount() {
        return vertices.size();
    }

    /**
     * Gets triangle count for statistics.
     */
    public int getTriangleCount() {
        return triangles.size();
    }

    // === Static Factory Methods ===

    /** Low detail mesh (for distant shields) */
    public static HexagonalShieldMesh lowDetail(float radius) {
        return new HexagonalShieldMesh(1, radius);
    }

    /** Medium detail mesh (default) */
    public static HexagonalShieldMesh mediumDetail(float radius) {
        return new HexagonalShieldMesh(2, radius);
    }

    /** High detail mesh (for close-up) */
    public static HexagonalShieldMesh highDetail(float radius) {
        return new HexagonalShieldMesh(3, radius);
    }
}
```

## LOD (Level of Detail) System

Per performance, usare mesh diverse in base alla distanza dalla camera:

```java
package com.devmod.client.render;

/**
 * Manages LOD meshes for shield rendering.
 */
public class ShieldMeshLOD {

    private final HexagonalShieldMesh highDetail;   // < 8 blocks
    private final HexagonalShieldMesh mediumDetail; // 8-24 blocks
    private final HexagonalShieldMesh lowDetail;    // > 24 blocks

    public ShieldMeshLOD(float radius) {
        this.highDetail = HexagonalShieldMesh.highDetail(radius);
        this.mediumDetail = HexagonalShieldMesh.mediumDetail(radius);
        this.lowDetail = HexagonalShieldMesh.lowDetail(radius);
    }

    public HexagonalShieldMesh getMeshForDistance(double distanceSq) {
        if (distanceSq < 64) {        // < 8 blocks
            return highDetail;
        } else if (distanceSq < 576) { // < 24 blocks
            return mediumDetail;
        } else {
            return lowDetail;
        }
    }
}
```

## Integrazione con Shield Renderer

```java
// In EnergyShieldRenderer.java

public class EnergyShieldRenderer {

    // Cached meshes per radius (most common radii)
    private static final Map<Float, ShieldMeshLOD> MESH_CACHE = new HashMap<>();

    public static void renderShield(PoseStack poseStack, MultiBufferSource bufferSource,
                                    LivingEntity entity, float partialTick) {
        ArmorStats stats = getShieldStats(entity);
        if (stats == null || stats.shieldBlockStrength <= 0) return;

        // Calculate shield radius based on entity size
        float radius = entity.getBbWidth() * 0.8f + 0.5f;

        // Get or create LOD meshes
        ShieldMeshLOD lod = MESH_CACHE.computeIfAbsent(radius, ShieldMeshLOD::new);

        // Select mesh based on camera distance
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSq = entity.position().distanceToSqr(cameraPos);
        HexagonalShieldMesh mesh = lod.getMeshForDistance(distSq);

        // Bind shader and render
        EnergyShieldShader.bind(/* params */);

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        mesh.render(consumer, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    stats.shieldColor | ((int)(stats.shieldOpacity * 255) << 24));

        EnergyShieldShader.unbind();
    }
}
```

## Performance Notes

| Subdivision | Triangoli | Uso Consigliato |
|-------------|-----------|-----------------|
| 0 | 20 | Debug only |
| 1 | 80 | Lontano (>24 blocks) |
| 2 | 320 | Medio (8-24 blocks) |
| 3 | 1280 | Vicino (<8 blocks) |
| 4 | 5120 | Non raccomandato (troppo pesante) |

Con LOD attivo, il costo GPU rimane ragionevole anche con molti scudi attivi.
