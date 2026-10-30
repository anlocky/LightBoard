package com.lightboard.keyboard;

/** Закругление клавиш: ползунок 0..100. 0 — квадрат, 50 — прежний вид, 100 — круг (выше 50 клавиша ещё и сжимается до квадрата, из которого получается круг). */
final class Shape {
    private Shape() { }

    static final int DEFAULT = 50;

    /** base — прежний радиус клавиши, max — предел (половина меньшей стороны). */
    static float radius(float base, float max, int v) {
        int p = Math.max(0, Math.min(100, v));
        float r = p <= DEFAULT ? base * p / (float) DEFAULT : base + (max - base) * ((p - DEFAULT) / 50f);
        return Math.max(0f, Math.min(r, max));
    }

    /** Доля, на которую длинная сторона стягивается к короткой (0 до 50 %, 1 — на 100 %). */
    static float squeeze(int v) { return v <= DEFAULT ? 0f : (Math.min(100, v) - DEFAULT) / 50f; }
}
