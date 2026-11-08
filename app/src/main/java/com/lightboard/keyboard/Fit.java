package com.lightboard.keyboard;

import android.graphics.Paint;

final class Fit {
    private Fit() { }

    static final float MIN = 0.7f;

    static float k(Paint p, String s, float maxW) {
        float w = p.measureText(s);
        return w <= maxW ? 1f : Math.max(MIN, maxW / w);
    }

    static String cut(Paint p, String s, float maxW, float k) {
        float sz = p.getTextSize();
        p.setTextSize(sz * k);
        String r = s;
        if (p.measureText(s) > maxW) r = s.substring(0, Math.max(1, p.breakText(s, true, maxW * 0.92f, null) - 1)) + "…";
        p.setTextSize(sz);
        return r;
    }
}
