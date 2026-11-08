package com.lightboard.keyboard;

final class Shape {
    private Shape() { }

    static final int DEFAULT = 50;

    static float radius(float base, float max, int v) {
        int p = Math.max(0, Math.min(100, v));
        float r = p <= DEFAULT ? base * p / (float) DEFAULT : base + (max - base) * ((p - DEFAULT) / 50f);
        return Math.max(0f, Math.min(r, max));
    }

    static float squeeze(int v) { return v <= DEFAULT ? 0f : (Math.min(100, v) - DEFAULT) / 50f; }
}
