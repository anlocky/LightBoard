package com.lightboard.keyboard;

import android.graphics.Path;

final class Trail {
    private Trail() { }

    static final long LIFE = 320;

    static boolean live(long[] t, int n, long now) { return n > 1 && now - t[n - 1] < LIFE; }

    static boolean ribbon(Path p, float[] x, float[] y, long[] t, int n, long now, float headW) {
        int i0 = n;
        while (i0 > 0 && now - t[i0 - 1] < LIFE) i0--;
        int cnt = n - i0;
        if (cnt < 2) return false;
        p.reset();
        for (int pass = 0; pass < 2; pass++) {
            float side = pass == 0 ? 1f : -1f;
            for (int k = 0; k < cnt; k++) {
                int i = pass == 0 ? i0 + k : n - 1 - k;
                int a = Math.max(i0, i - 1), b = Math.min(n - 1, i + 1);
                float dx = x[b] - x[a], dy = y[b] - y[a], len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len == 0f) { dx = 1f; dy = 0f; len = 1f; }
                float f = 1f - (now - t[i]) / (float) LIFE, w = headW * 0.5f * (f < 0f ? 0f : f) * side;
                float px = x[i] - dy / len * w, py = y[i] + dx / len * w;
                if (pass == 0 && k == 0) p.moveTo(px, py); else p.lineTo(px, py);
            }
        }
        p.close();
        return true;
    }
}
