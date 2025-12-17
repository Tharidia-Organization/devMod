package com.frenkvs.devmod.ui.editor;

import org.lwjgl.glfw.GLFW;

public class AdvancedScroll {
    private static final float MIN_OFFSET = 0.0f;
    private static final float DEFAULT_SMOOTHING = 0.8f;
    private static final float DEFAULT_FRICTION = 0.9f;
    private static final float STOP_THRESHOLD = 0.1f;
    private static final float SCROLL_MULTIPLIER = 20.0f;
    private static final double KEY_SCROLL_STEP = 3.0;
    private static final double KEY_PAGE_STEP = 10.0;
    private static final float MAX_SCROLL_TARGET = Float.MAX_VALUE;

    private float targetOffset = MIN_OFFSET;
    private float currentOffset = MIN_OFFSET;
    private float velocity = 0.0f;
    
    public void update() {
        // Smooth interpolation
        float diff = targetOffset - currentOffset;
        velocity += diff * DEFAULT_SMOOTHING;
        velocity *= DEFAULT_FRICTION;
        currentOffset += velocity;
        
        // Stop micro-movements
        if (Math.abs(diff) < STOP_THRESHOLD && Math.abs(velocity) < STOP_THRESHOLD) {
            currentOffset = targetOffset;
            velocity = 0.0f;
        }
    }
    
    public void scroll(double delta) {
        targetOffset = Math.max(MIN_OFFSET, targetOffset - (float)(delta * SCROLL_MULTIPLIER));
    }
    
    public void scrollTo(float offset) {
        targetOffset = Math.max(MIN_OFFSET, offset);
    }
    
    public void scrollToTop() {
        scrollTo(MIN_OFFSET);
    }
    
    public boolean handleKeyPress(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> { scroll(KEY_SCROLL_STEP); return true; }
            case GLFW.GLFW_KEY_DOWN -> { scroll(-KEY_SCROLL_STEP); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { scroll(KEY_PAGE_STEP); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { scroll(-KEY_PAGE_STEP); return true; }
            case GLFW.GLFW_KEY_HOME -> { scrollToTop(); return true; }
            case GLFW.GLFW_KEY_END -> { scrollTo(MAX_SCROLL_TARGET); return true; }
        }
        return false;
    }
    
    public float getOffset() {
        return currentOffset;
    }
    
    public void setMaxScroll(float max) {
        targetOffset = Math.min(targetOffset, max);
    }
}
