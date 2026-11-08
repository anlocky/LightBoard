package com.lightboard.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

final class MenuBar {
    private MenuBar() { }

    static final int N = 4;
    private static int[] order = {0, 1, 2, 3};

    static void setOrder(String s) { order = MenuOrder.parse(s); }

    static int id(int pos) { return order[pos]; }

    static int hit(float x, float x0, float x1) {
        if (x < x0 || x > x1) return -1;
        return Math.min(N - 1, (int) ((x - x0) / ((x1 - x0) / N)));
    }

    static void draw(Canvas c, Paint stroke, Paint fill, RectF rf, float x0, float x1, float cy, float u, int color, int active, float onP, float spP, float prog, int pressed) {
        float cell = (x1 - x0) / N;
        for (int i = 0; i < N; i++) {
            float q = Anim.clamp((prog - i * 0.1f) / (1f - (N - 1) * 0.1f), 0f, 1f);
            if (q <= 0f) continue;
            float cx = x0 + cell * (i + 0.5f), yy = cy + (1f - Anim.ease(q)) * 7 * u;
            int id = order[i];
            float a = id == Board.MENU_ONEHAND ? onP : (id == Board.MENU_SPLIT ? spP : 0f);
            float sc = (0.55f + 0.45f * Anim.back(q)) * (pressed == i ? 0.86f : 1f);
            if (a > 0f) {
                fill.setColor(Theme.fade(active, 0.18f * a * q));
                rf.set(cx - 20 * u, yy - 15 * u, cx + 20 * u, yy + 15 * u);
                c.drawRoundRect(rf, 15 * u, 15 * u, fill);
            }
            int col = Theme.fade(Theme.mix(color, active, a), q);
            c.save();
            c.scale(sc, sc, cx, yy);
            switch (id) {
                case Board.MENU_CLIP: Icons.clip(c, stroke, rf, cx, yy, u * 1.1f, col); break;
                case Board.MENU_ONEHAND: Icons.oneHand(c, stroke, rf, cx, yy, u, col); break;
                case Board.MENU_SPLIT: Icons.split(c, stroke, rf, cx, yy, u, col); break;
                default: Icons.gear(c, stroke, cx, yy, u, col); break;
            }
            c.restore();
        }
    }
}
