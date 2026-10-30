package com.lightboard.keyboard;

/** Общие мелкие функции анимации и геометрии для обоих дизайнов клавиатуры. */
final class Anim {
    private Anim() { }

    /** Плавное замедление к концу (0..1 -> 0..1). */
    static float ease(float t) { float u = 1f - t; return 1f - u * u * u; }

    /** «Пружинка»: лёгкий перелёт за единицу и возврат. */
    static float back(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = t - 1f;
        return 1f + 2.70158f * u * u * u + 1.70158f * u * u;
    }

    static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static float sq(float v) { return v * v; }

    /** Шаг к цели за dt мс при полной длительности dur мс. */
    static float step(float cur, float target, float dt, float dur) {
        float s = dt / dur;
        return cur < target ? Math.min(target, cur + s) : Math.max(target, cur - s);
    }
}
