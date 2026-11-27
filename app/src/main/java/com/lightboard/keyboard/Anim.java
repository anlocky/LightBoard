package com.lightboard.keyboard;

final class Anim {
    private Anim() { }

    static float sv;

    static float ease(float t) { float u = 1f - t; return 1f - u * u * u; }

    static float back(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = t - 1f;
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
    }

    static float spr(float x, float v, float target, float dtMs, float k, float c) {
        float dt = Math.min(dtMs, 48f) / 1000f;
        int n = Math.max(1, (int) Math.ceil(dt / 0.008f));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            v += ((target - x) * k - c * v) * h;
            x += v * h;
        }
        sv = v;
        return x;
    }

    static float spring(float x, float v, float target, float dtMs, float k, float z) {
        x = spr(x, v, target, dtMs, k, 2f * z * (float) Math.sqrt(k));
        v = sv;
        if (Math.abs(target - x) < 0.0015f && Math.abs(v) < 0.02f) { x = target; v = 0f; }
        if (x < 0f) { x = 0f; if (v < 0f) v = 0f; }
        else if (x > 1f) { x = 1f; if (v > 0f) v = 0f; }
        sv = v;
        return x;
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static float sq(float v) { return v * v; }

    static float step(float cur, float target, float dt, float dur) {
        float s = dt / dur;
        return cur < target ? Math.min(target, cur + s) : Math.max(target, cur - s);
    }
}
