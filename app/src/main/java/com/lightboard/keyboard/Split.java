package com.lightboard.keyboard;

final class Split {
    private Split() { }

    static final float GAP = 0.14f;

    static float x(float x, float w, float m, boolean right) { return xp(x, w, m, right, 1f); }

    static float xp(float x, float w, float m, boolean right, float p) {
        float mid = w / 2f, half = mid - m, s = 1f + ((half - GAP * (w - 2f * m) / 2f) / half - 1f) * p;
        return right ? (w - m) - (w - m - x) * s : m + (x - m) * s;
    }
}
