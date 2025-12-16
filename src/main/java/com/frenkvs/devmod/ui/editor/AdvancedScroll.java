package com.frenkvs.devmod.ui.editor;

import org.lwjgl.glfw.GLFW;

public class AdvancedScroll {
    private float targetOffset = 0;
    private float currentOffset = 0;
    private float velocity = 0;
    private final float smoothing = 0.8f;
    private final float friction = 0.9f;
    
    public void update() {
        // Smooth interpolation
        float diff = targetOffset - currentOffset;
        velocity += diff * smoothing;
        velocity *= friction;
        currentOffset += velocity;
        
        // Stop micro-movements
        if (Math.abs(diff) < 0.1f && Math.abs(velocity) < 0.1f) {
            currentOffset = targetOffset;
            velocity = 0;
        }
    }
    
    public void scroll(double delta) {
        targetOffset = Math.max(0, targetOffset - (float)(delta * 20));
    }
    
    public void scrollTo(float offset) {
        targetOffset = Math.max(0, offset);
    }
    
    public void scrollToTop() {
        scrollTo(0);
    }
    
    public boolean handleKeyPress(int keyCode) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> { scroll(3); return true; }
            case GLFW.GLFW_KEY_DOWN -> { scroll(-3); return true; }
            case GLFW.GLFW_KEY_PAGE_UP -> { scroll(10); return true; }
            case GLFW.GLFW_KEY_PAGE_DOWN -> { scroll(-10); return true; }
            case GLFW.GLFW_KEY_HOME -> { scrollToTop(); return true; }
            case GLFW.GLFW_KEY_END -> { scrollTo(Float.MAX_VALUE); return true; }
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