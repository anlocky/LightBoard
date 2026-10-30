package com.lightboard.keyboard;

/** Разделение клавиатуры: каждая половина сжимается к своему краю, посередине остаётся свободный промежуток. Работает с готовыми координатами клавиш. */
final class Split {
    private Split() { }

    static final float GAP = 0.14f; // промежуток — доля ширины области клавиш

    /** Новая координата x. right — клавиша относится к правой половине. w — ширина вида, m — боковое поле. */
    static float x(float x, float w, float m, boolean right) { return xp(x, w, m, right, 1f); }

    /** То же при доле разделения p: 0 — клавиатура цельная, 1 — разделена (так половины плавно разъезжаются). */
    static float xp(float x, float w, float m, boolean right, float p) {
        float mid = w / 2f, half = mid - m, s = 1f + ((half - GAP * (w - 2f * m) / 2f) / half - 1f) * p;
        return right ? (w - m) - (w - m - x) * s : m + (x - m) * s;
    }
}
