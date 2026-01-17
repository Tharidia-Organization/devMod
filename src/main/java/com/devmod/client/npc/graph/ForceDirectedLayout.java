package com.devmod.client.npc.graph;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import javax.annotation.Nonnull;

/**
 * Force-directed graph layout algorithm.
 * Positions nodes by simulating physical forces:
 * - Repulsion between all nodes (like charged particles)
 * - Attraction along edges (like springs)
 * - Gravity towards center
 */
public final class ForceDirectedLayout {

    /** Repulsion constant - higher values push nodes further apart */
    private static final float REPULSION = 8000f;

    /** Attraction constant - higher values pull connected nodes closer */
    private static final float ATTRACTION = 0.05f;

    /** Gravity constant - pulls nodes towards center */
    private static final float GRAVITY = 0.01f;

    /** Damping factor - reduces velocity over time (0-1) */
    private static final float DAMPING = 0.85f;

    /** Minimum distance for repulsion calculation */
    private static final float MIN_DISTANCE = 50f;

    /** Velocity threshold below which nodes are considered stable */
    private static final float VELOCITY_THRESHOLD = 0.1f;

    /** Maximum iterations per layout step */
    private static final int MAX_ITERATIONS = 100;

    private ForceDirectedLayout() {}

    /**
     * Performs a single layout iteration.
     *
     * @param nodes       Map of node ID to GraphNode
     * @param connections List of connections
     * @param centerX     Center X position
     * @param centerY     Center Y position
     * @return true if layout is stable (all velocities below threshold)
     */
    public static boolean iterate(
        @Nonnull Map<String, GraphNode> nodes,
        @Nonnull List<GraphConnection> connections,
        int centerX,
        int centerY
    ) {
        if (nodes.isEmpty()) return true;

        Collection<GraphNode> nodeList = nodes.values();

        // Calculate forces
        for (GraphNode node : nodeList) {
            if (node.isDragging()) continue;

            float fx = 0, fy = 0;

            // Repulsion from all other nodes
            for (GraphNode other : nodeList) {
                if (node == other) continue;

                float dx = node.getX() - other.getX();
                float dy = node.getY() - other.getY();
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                distance = Math.max(distance, MIN_DISTANCE);

                float force = REPULSION / (distance * distance);
                fx += (dx / distance) * force;
                fy += (dy / distance) * force;
            }

            // Attraction to connected nodes
            for (GraphConnection conn : connections) {
                GraphNode other = null;
                boolean isSource = conn.sourceNodeId().equals(node.getNodeId());
                boolean isTarget = conn.targetNodeId().equals(node.getNodeId());

                if (isSource) {
                    other = nodes.get(conn.targetNodeId());
                } else if (isTarget) {
                    other = nodes.get(conn.sourceNodeId());
                }

                if (other != null) {
                    float dx = other.getX() - node.getX();
                    float dy = other.getY() - node.getY();
                    fx += dx * ATTRACTION;
                    fy += dy * ATTRACTION;
                }
            }

            // Gravity towards center
            float dx = centerX - node.getCenterX();
            float dy = centerY - node.getCenterY();
            fx += dx * GRAVITY;
            fy += dy * GRAVITY;

            // Update velocity with damping
            node.velocityX = (node.velocityX + fx) * DAMPING;
            node.velocityY = (node.velocityY + fy) * DAMPING;
        }

        // Apply velocities
        boolean stable = true;
        for (GraphNode node : nodeList) {
            if (node.isDragging()) continue;

            // Check if this node is stable
            float speed = (float) Math.sqrt(
                node.velocityX * node.velocityX + node.velocityY * node.velocityY
            );
            if (speed > VELOCITY_THRESHOLD) {
                stable = false;
            }

            // Apply velocity
            node.setPosition(
                node.getX() + (int) node.velocityX,
                node.getY() + (int) node.velocityY
            );
        }

        return stable;
    }

    /**
     * Runs the layout algorithm until stable or max iterations reached.
     *
     * @param nodes       Map of node ID to GraphNode
     * @param connections List of connections
     * @param centerX     Center X position
     * @param centerY     Center Y position
     */
    public static void layout(
        @Nonnull Map<String, GraphNode> nodes,
        @Nonnull List<GraphConnection> connections,
        int centerX,
        int centerY
    ) {
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            if (iterate(nodes, connections, centerX, centerY)) {
                break;
            }
        }
    }

    /**
     * Applies an initial circular layout before running force-directed.
     * This provides a better starting point than random positions.
     *
     * @param nodes   Map of node ID to GraphNode
     * @param centerX Center X position
     * @param centerY Center Y position
     * @param radius  Initial circle radius
     */
    public static void circularLayout(
        @Nonnull Map<String, GraphNode> nodes,
        int centerX,
        int centerY,
        int radius
    ) {
        int count = nodes.size();
        if (count == 0) return;

        int i = 0;
        for (GraphNode node : nodes.values()) {
            double angle = 2 * Math.PI * i / count;
            int x = centerX + (int) (radius * Math.cos(angle)) - node.getWidth() / 2;
            int y = centerY + (int) (radius * Math.sin(angle)) - node.getHeight() / 2;
            node.setPosition(x, y);
            node.velocityX = 0;
            node.velocityY = 0;
            i++;
        }
    }

    /**
     * Applies a hierarchical layout based on node connections.
     * Entry node at left, subsequent nodes arranged in layers.
     *
     * @param nodes       Map of node ID to GraphNode
     * @param connections List of connections
     * @param entryNodeId The entry node ID
     * @param startX      Starting X position
     * @param startY      Starting Y position
     */
    public static void hierarchicalLayout(
        @Nonnull Map<String, GraphNode> nodes,
        @Nonnull List<GraphConnection> connections,
        @Nonnull String entryNodeId,
        int startX,
        int startY
    ) {
        if (nodes.isEmpty()) return;

        // Calculate layers using BFS from entry node
        Map<String, Integer> layers = new java.util.HashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        GraphNode entryNode = nodes.get(entryNodeId);
        if (entryNode != null) {
            queue.add(entryNodeId);
            layers.put(entryNodeId, 0);
        }

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            Integer layerObj = layers.get(nodeId);
            if (layerObj == null) {
                continue;
            }
            int layer = layerObj;

            for (GraphConnection conn : connections) {
                if (conn.sourceNodeId().equals(nodeId)) {
                    String targetId = conn.targetNodeId();
                    if (!layers.containsKey(targetId)) {
                        layers.put(targetId, layer + 1);
                        queue.add(targetId);
                    }
                }
            }
        }

        // Assign layers to unvisited nodes
        int maxLayer = layers.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        for (String nodeId : nodes.keySet()) {
            if (!layers.containsKey(nodeId)) {
                layers.put(nodeId, maxLayer);
            }
        }

        // Count nodes per layer
        Map<Integer, Integer> nodesPerLayer = new java.util.HashMap<>();
        Map<Integer, Integer> currentIndexInLayer = new java.util.HashMap<>();

        for (int layer : layers.values()) {
            nodesPerLayer.merge(layer, 1, Integer::sum);
            currentIndexInLayer.putIfAbsent(layer, 0);
        }

        // Position nodes
        int layerSpacing = 250;
        int nodeSpacing = 120;

        for (Map.Entry<String, GraphNode> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            GraphNode node = entry.getValue();
            Integer layerObj = layers.get(nodeId);
            if (layerObj == null) {
                continue;
            }
            int layer = layerObj;

            Integer layerCountObj = nodesPerLayer.get(layer);
            Integer indexInLayerObj = currentIndexInLayer.get(layer);
            if (layerCountObj == null || indexInLayerObj == null) {
                continue;
            }
            int layerCount = layerCountObj;
            int indexInLayer = indexInLayerObj;
            currentIndexInLayer.put(layer, indexInLayer + 1);

            int x = startX + layer * layerSpacing;
            int y = startY + indexInLayer * nodeSpacing - (layerCount * nodeSpacing / 2);

            node.setPosition(x, y);
            node.velocityX = 0;
            node.velocityY = 0;
        }
    }
}
