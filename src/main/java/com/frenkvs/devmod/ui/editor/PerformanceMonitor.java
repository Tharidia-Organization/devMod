package com.frenkvs.devmod.ui.editor;

import net.minecraft.client.Minecraft;
import java.util.ArrayDeque;
import java.util.Queue;

public class PerformanceMonitor {
    private static final int SAMPLE_SIZE = 60; // 3 seconds at 20 TPS
    
    private final Queue<Long> frameTimes = new ArrayDeque<>();
    private final Queue<Long> renderTimes = new ArrayDeque<>();
    private final Queue<Integer> entityCounts = new ArrayDeque<>();
    
    private long lastFrameTime = System.nanoTime();
    private long renderStartTime = 0;
    
    public void startFrame() {
        long now = System.nanoTime();
        long frameTime = now - lastFrameTime;
        lastFrameTime = now;
        
        frameTimes.offer(frameTime);
        if (frameTimes.size() > SAMPLE_SIZE) {
            frameTimes.poll();
        }
        
        renderStartTime = now;
    }
    
    public void endRender() {
        long renderTime = System.nanoTime() - renderStartTime;
        renderTimes.offer(renderTime);
        if (renderTimes.size() > SAMPLE_SIZE) {
            renderTimes.poll();
        }
        
        var mc = Minecraft.getInstance();
        int entities = mc.level != null ? mc.level.getEntityCount() : 0;
        entityCounts.offer(entities);
        if (entityCounts.size() > SAMPLE_SIZE) {
            entityCounts.poll();
        }
    }
    
    public PerformanceData getMetrics() {
        double frameMs = calculateAverage(frameTimes) / 1_000_000.0; // ms
        double renderMs = calculateAverage(renderTimes) / 1_000_000.0; // ms
        double entityAvg = calculateAverage(entityCounts.stream().mapToLong(Integer::longValue).boxed().toList());
        String bottleneck = identifyBottleneck(frameMs, renderMs, entityAvg);
        return new PerformanceData(frameMs, renderMs, entityAvg, bottleneck);
    }
    
    private double calculateAverage(Queue<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    private double calculateAverage(java.util.List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    private String identifyBottleneck(double frameMs, double renderMs, double entities) {
        if (frameMs > 50) return "LOW_FPS";
        if (renderMs > 16) return "RENDER_BOUND";
        if (entities > 1000) return "ENTITY_HEAVY";
        return "OPTIMAL";
    }
    
    public record PerformanceData(double frameTime, double renderTime, double entityCount, String bottleneck) {}
}
