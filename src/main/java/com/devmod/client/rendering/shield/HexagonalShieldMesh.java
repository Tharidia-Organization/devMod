package com.devmod.client.rendering.shield;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HexagonalShieldMesh {

    // Golden ratio for icosahedron vertices
    private static final float PHI = (1.0f + (float) Math.sqrt(5.0)) / 2.0f;

    /** Width of hex grid lines as fraction of radius */
    public static final float LINE_WIDTH = 0.015f;

    // Cached mesh data
    private final List<Vec3> vertices;
    private final List<int[]> triangles;
    private final List<int[]> edges;
    private final List<Vec3> normals;
    private final int subdivisions;
    private final float radius;

    // Pre-computed float arrays for faster rendering
    private float[] vertexArray;
    private float[] normalArray;
    private int[] indexArray;

    /**
     * Creates a geodesic sphere mesh.
     *
     * @param subdivisions Number of subdivision iterations (0-4 recommended)
     * @param radius Sphere radius
     */
    public HexagonalShieldMesh(int subdivisions, float radius) {
        this.subdivisions = subdivisions;
        this.radius = radius;
        this.vertices = new ArrayList<>();
        this.triangles = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.normals = new ArrayList<>();

        generateIcosahedron();
        for (int i = 0; i < subdivisions; i++) {
            subdivide();
        }
        extractEdges();
        calculateNormals();
        buildArrays();
    }

    /**
     * Generates the base icosahedron (20 faces, 12 vertices).
     */
    private void generateIcosahedron() {
        // Normalize factor for unit icosahedron
        float norm = (float) Math.sqrt(1.0 + PHI * PHI);

        // 12 vertices of icosahedron (normalized to unit sphere, then scaled)
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

        // 20 triangular faces (winding order for correct normals)
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
    private void subdivide() {
        Map<Long, Integer> midpointCache = new HashMap<>();
        List<int[]> newTriangles = new ArrayList<>();

        for (int[] tri : triangles) {
            // Get midpoints of each edge
            int a = getMidpoint(tri[0], tri[1], midpointCache);
            int b = getMidpoint(tri[1], tri[2], midpointCache);
            int c = getMidpoint(tri[2], tri[0], midpointCache);

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
    private int getMidpoint(int i1, int i2, Map<Long, Integer> cache) {
        // Create unique key for edge (order-independent)
        long smaller = Math.min(i1, i2);
        long larger = Math.max(i1, i2);
        long key = (smaller << 32) | larger;

        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Vec3 v1 = Objects.requireNonNull(vertices.get(i1), "midpoint v1");
        Vec3 v2 = Objects.requireNonNull(vertices.get(i2), "midpoint v2");

        // Calculate midpoint
        Vec3 mid = Objects.requireNonNull(v1.add(v2), "midpoint").scale(0.5);

        // Project onto sphere (normalize then scale to radius)
        Vec3 normalized = Objects.requireNonNull(mid.normalize(), "midpoint normalized").scale(radius);

        int index = vertices.size();
        vertices.add(normalized);
        cache.put(key, index);

        return index;
    }

    /**
     * Extracts unique edges from all triangles for line rendering.
     */
    private void extractEdges() {
        edges.clear();
        Map<Long, Boolean> edgeSet = new HashMap<>();

        for (int[] tri : triangles) {
            addEdge(tri[0], tri[1], edgeSet);
            addEdge(tri[1], tri[2], edgeSet);
            addEdge(tri[2], tri[0], edgeSet);
        }
    }

    /**
     * Adds an edge if not already present.
     */
    private void addEdge(int i1, int i2, Map<Long, Boolean> edgeSet) {
        long smaller = Math.min(i1, i2);
        long larger = Math.max(i1, i2);
        long key = (smaller << 32) | larger;

        if (!edgeSet.containsKey(key)) {
            edgeSet.put(key, true);
            edges.add(new int[]{i1, i2});
        }
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
     * Builds flat arrays for faster rendering.
     */
    private void buildArrays() {
        // Vertex array (x, y, z per vertex)
        vertexArray = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            Vec3 v = vertices.get(i);
            vertexArray[i * 3] = (float) v.x;
            vertexArray[i * 3 + 1] = (float) v.y;
            vertexArray[i * 3 + 2] = (float) v.z;
        }

        // Normal array
        normalArray = new float[normals.size() * 3];
        for (int i = 0; i < normals.size(); i++) {
            Vec3 n = normals.get(i);
            normalArray[i * 3] = (float) n.x;
            normalArray[i * 3 + 1] = (float) n.y;
            normalArray[i * 3 + 2] = (float) n.z;
        }

        // Index array (flattened triangles)
        indexArray = new int[triangles.size() * 3];
        for (int i = 0; i < triangles.size(); i++) {
            int[] tri = triangles.get(i);
            indexArray[i * 3] = tri[0];
            indexArray[i * 3 + 1] = tri[1];
            indexArray[i * 3 + 2] = tri[2];
        }
    }

    /**
     * Renders the mesh to a VertexConsumer.
     *
     * @param consumer Vertex consumer
     * @param matrix Transformation matrix
     * @param centerX Center X position
     * @param centerY Center Y position
     * @param centerZ Center Z position
     * @param color ARGB color
     */
    public void render(VertexConsumer consumer, Matrix4f matrix,
                       float centerX, float centerY, float centerZ, int color) {
        Objects.requireNonNull(consumer, "vertex consumer");
        Objects.requireNonNull(matrix, "matrix");
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int[] tri : triangles) {
            for (int idx : tri) {
                Vec3 v = Objects.requireNonNull(vertices.get(idx), "vertex");
                Vec3 n = Objects.requireNonNull(normals.get(idx), "normal");

                consumer.addVertex(matrix,
                    centerX + (float) v.x,
                    centerY + (float) v.y,
                    centerZ + (float) v.z
                );
                consumer.setColor(r, g, b, a);
                consumer.setNormal((float) n.x, (float) n.y, (float) n.z);
            }
        }
    }

    /**
     * Renders the mesh with per-vertex alpha based on fresnel.
     */
    public void renderWithFresnel(VertexConsumer consumer, Matrix4f matrix,
                                   float centerX, float centerY, float centerZ,
                                   int baseColor, Vec3 cameraPos) {
        Objects.requireNonNull(consumer, "vertex consumer");
        Objects.requireNonNull(matrix, "matrix");
        Vec3 safeCamera = Objects.requireNonNull(cameraPos, "cameraPos");
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int baseAlpha = (baseColor >> 24) & 0xFF;

        Vec3 center = new Vec3(centerX, centerY, centerZ);

        for (int[] tri : triangles) {
            for (int idx : tri) {
                final Vec3 v = Objects.requireNonNull(vertices.get(idx), "vertex");
                final Vec3 n = Objects.requireNonNull(normals.get(idx), "normal");

                // World position of this vertex
                final Vec3 worldPos = Objects.requireNonNull(center.add(v), "worldPos");

                // View direction (from vertex to camera)
                final Vec3 viewDir = Objects.requireNonNull(safeCamera.subtract(worldPos).normalize(), "viewDir");

                // Fresnel calculation
                double dot = Math.max(viewDir.dot(n), 0.0);
                float fresnel = (float) Math.pow(1.0 - dot, 3.0);

                // Alpha based on fresnel (edges more visible)
                int alpha = (int) (baseAlpha * (0.3f + fresnel * 0.7f));
                alpha = Math.min(255, Math.max(0, alpha));

                consumer.addVertex(matrix,
                    centerX + (float) v.x,
                    centerY + (float) v.y,
                    centerZ + (float) v.z
                );
                consumer.setColor(r, g, b, alpha);
                consumer.setNormal((float) n.x, (float) n.y, (float) n.z);
            }
        }
    }

    // === Getters ===

    public int getVertexCount() {
        return vertices.size();
    }

    public int getTriangleCount() {
        return triangles.size();
    }

    public int getSubdivisions() {
        return subdivisions;
    }

    public float getRadius() {
        return radius;
    }

    public float[] getVertexArray() {
        return vertexArray;
    }

    public float[] getNormalArray() {
        return normalArray;
    }

    public int[] getIndexArray() {
        return indexArray;
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public List<int[]> getEdges() {
        return edges;
    }

    public Vec3 getVertex(int index) {
        return Objects.requireNonNull(vertices.get(index), "vertex");
    }

    public Vec3 getNormal(int index) {
        return Objects.requireNonNull(normals.get(index), "normal");
    }

    /**
     * Renders the hexagonal grid lines to a VertexConsumer.
     * Uses LINES mode for thin grid effect.
     *
     * @param consumer Vertex consumer (must be LINES mode)
     * @param matrix Transformation matrix
     * @param centerX Center X position
     * @param centerY Center Y position
     * @param centerZ Center Z position
     * @param color ARGB color for the lines
     */
    public void renderLines(VertexConsumer consumer, Matrix4f matrix,
                            float centerX, float centerY, float centerZ, int color) {
        Objects.requireNonNull(consumer, "vertex consumer");
        Objects.requireNonNull(matrix, "matrix");
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int[] edge : edges) {
            Vec3 v1 = Objects.requireNonNull(vertices.get(edge[0]), "edge vertex1");
            Vec3 v2 = Objects.requireNonNull(vertices.get(edge[1]), "edge vertex2");
            Vec3 n1 = Objects.requireNonNull(normals.get(edge[0]), "edge normal1");
            Vec3 n2 = Objects.requireNonNull(normals.get(edge[1]), "edge normal2");

            consumer.addVertex(matrix,
                centerX + (float) v1.x,
                centerY + (float) v1.y,
                centerZ + (float) v1.z
            );
            consumer.setColor(r, g, b, a);
            consumer.setNormal((float) n1.x, (float) n1.y, (float) n1.z);

            consumer.addVertex(matrix,
                centerX + (float) v2.x,
                centerY + (float) v2.y,
                centerZ + (float) v2.z
            );
            consumer.setColor(r, g, b, a);
            consumer.setNormal((float) n2.x, (float) n2.y, (float) n2.z);
        }
    }

    /**
     * Renders hexagonal grid lines with fresnel-based alpha.
     * Lines are more visible at grazing angles.
     */
    public void renderLinesWithFresnel(VertexConsumer consumer, Matrix4f matrix,
                                        float centerX, float centerY, float centerZ,
                                        int baseColor, Vec3 cameraPos) {
        Objects.requireNonNull(consumer, "vertex consumer");
        Objects.requireNonNull(matrix, "matrix");
        Vec3 safeCamera = Objects.requireNonNull(cameraPos, "cameraPos");
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int baseAlpha = (baseColor >> 24) & 0xFF;

        Vec3 center = new Vec3(centerX, centerY, centerZ);

        for (int[] edge : edges) {
            Vec3 v1 = Objects.requireNonNull(vertices.get(edge[0]), "edge vertex1");
            Vec3 v2 = Objects.requireNonNull(vertices.get(edge[1]), "edge vertex2");
            Vec3 n1 = Objects.requireNonNull(normals.get(edge[0]), "edge normal1");
            Vec3 n2 = Objects.requireNonNull(normals.get(edge[1]), "edge normal2");

            // Calculate fresnel for each vertex
            final Vec3 worldPos1 = Objects.requireNonNull(center.add(v1), "worldPos1");
            final Vec3 viewDir1 = Objects.requireNonNull(safeCamera.subtract(worldPos1).normalize(), "viewDir1");
            double dot1 = Math.max(viewDir1.dot(n1), 0.0);
            float fresnel1 = (float) Math.pow(1.0 - dot1, 2.0);
            int alpha1 = (int) (baseAlpha * (0.2f + fresnel1 * 0.8f));
            alpha1 = Math.min(255, Math.max(0, alpha1));

            final Vec3 worldPos2 = Objects.requireNonNull(center.add(v2), "worldPos2");
            final Vec3 viewDir2 = Objects.requireNonNull(safeCamera.subtract(worldPos2).normalize(), "viewDir2");
            double dot2 = Math.max(viewDir2.dot(n2), 0.0);
            float fresnel2 = (float) Math.pow(1.0 - dot2, 2.0);
            int alpha2 = (int) (baseAlpha * (0.2f + fresnel2 * 0.8f));
            alpha2 = Math.min(255, Math.max(0, alpha2));

            consumer.addVertex(matrix,
                centerX + (float) v1.x,
                centerY + (float) v1.y,
                centerZ + (float) v1.z
            );
            consumer.setColor(r, g, b, alpha1);
            consumer.setNormal((float) n1.x, (float) n1.y, (float) n1.z);

            consumer.addVertex(matrix,
                centerX + (float) v2.x,
                centerY + (float) v2.y,
                centerZ + (float) v2.z
            );
            consumer.setColor(r, g, b, alpha2);
            consumer.setNormal((float) n2.x, (float) n2.y, (float) n2.z);
        }
    }

    // === Static Factory Methods with Caching ===

    private static final Map<String, HexagonalShieldMesh> MESH_CACHE = new HashMap<>();

    /**
     * Gets or creates a mesh with the specified parameters.
     * Cached for reuse across multiple shields.
     */
    public static HexagonalShieldMesh getOrCreate(int subdivisions, float radius) {
        String key = subdivisions + "_" + radius;
        return MESH_CACHE.computeIfAbsent(key, k -> new HexagonalShieldMesh(subdivisions, radius));
    }

    /** Low detail mesh (for distant shields, ~80 triangles) */
    public static HexagonalShieldMesh lowDetail(float radius) {
        return getOrCreate(1, radius);
    }

    /** Medium detail mesh (default, ~320 triangles) */
    public static HexagonalShieldMesh mediumDetail(float radius) {
        return getOrCreate(2, radius);
    }

    /** High detail mesh (for close-up, ~1280 triangles) */
    public static HexagonalShieldMesh highDetail(float radius) {
        return getOrCreate(3, radius);
    }

    /**
     * Selects appropriate LOD mesh based on camera distance.
     */
    public static HexagonalShieldMesh forDistance(float radius, double distanceSquared) {
        if (distanceSquared < 64) {        // < 8 blocks
            return highDetail(radius);
        } else if (distanceSquared < 576) { // < 24 blocks
            return mediumDetail(radius);
        } else {
            return lowDetail(radius);
        }
    }

    /**
     * Clears the mesh cache.
     */
    public static void clearCache() {
        MESH_CACHE.clear();
    }

    @Override
    public String toString() {
        return String.format("HexagonalShieldMesh[subdiv=%d, radius=%.2f, verts=%d, tris=%d]",
            subdivisions, radius, vertices.size(), triangles.size());
    }
}
