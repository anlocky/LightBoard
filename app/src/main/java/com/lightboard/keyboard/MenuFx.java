package com.lightboard.keyboard;

final class MenuFx {

    static final float OPEN = 260f, CLOSE = 120f;

    float hx, hy;
    float pulse;
    private int last = -2;

    void reset() { last = -2; pulse = 0f; }

    boolean step(float dt, boolean open, int sel, float tx, float ty) {
        if (!open) { last = -2; pulse = 0f; return false; }
        boolean a = false;
        if (sel != last) {
            if (sel >= 0) { if (last < 0) { hx = tx; hy = ty; } pulse = 1f; }
            last = sel;
        }
        if (sel >= 0) {
            float k = Math.min(1f, dt / 55f);
            hx += (tx - hx) * k; hy += (ty - hy) * k;
            if (Math.abs(tx - hx) > 0.4f || Math.abs(ty - hy) > 0.4f) a = true; else { hx = tx; hy = ty; }
        }
        if (pulse > 0f) { pulse = Math.max(0f, pulse - dt / 190f); a = true; }
        return a;
    }

    static float body(float menuP, boolean open) { return open ? Anim.back(Math.min(1f, menuP * 1.6f)) : Anim.ease(menuP); }

    static float item(float menuP, boolean open, int order) {
        if (!open) return 1f;
        return Anim.clamp((menuP - 0.18f - 0.07f * order) / 0.42f, 0f, 1f);
    }

    float hlScale() { float p = pulse; return 1f + 0.18f * p * (2f - p); }
}
