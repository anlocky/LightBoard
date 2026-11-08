package com.lightboard.keyboard;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

final class Icons {
    private Icons() { }

    static void shift(Canvas c, Path p, Paint fill, Paint stroke, float cx, float cy, float u, int state, int color) {
        p.reset();
        p.moveTo(cx, cy - 9 * u); p.lineTo(cx + 9.5f * u, cy + 1 * u); p.lineTo(cx + 4.8f * u, cy + 1 * u);
        p.lineTo(cx + 4.8f * u, cy + 8 * u); p.lineTo(cx - 4.8f * u, cy + 8 * u); p.lineTo(cx - 4.8f * u, cy + 1 * u);
        p.lineTo(cx - 9.5f * u, cy + 1 * u); p.close();
        if (state != 0) { fill.setColor(color); c.drawPath(p, fill); }
        else { stroke.setColor(color); c.drawPath(p, stroke); }
        if (state == 2) { fill.setColor(color); c.drawRect(cx - 4.8f * u, cy + 10 * u, cx + 4.8f * u, cy + 11.6f * u, fill); }
    }

    static void back(Canvas c, Path p, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        p.reset();
        p.moveTo(cx - 10.5f * u, cy); p.lineTo(cx - 5 * u, cy - 7 * u); p.lineTo(cx + 10 * u, cy - 7 * u);
        p.lineTo(cx + 10 * u, cy + 7 * u); p.lineTo(cx - 5 * u, cy + 7 * u); p.close();
        c.drawPath(p, stroke);
        c.drawLine(cx - 1 * u, cy - 3.5f * u, cx + 6 * u, cy + 3.5f * u, stroke);
        c.drawLine(cx + 6 * u, cy - 3.5f * u, cx - 1 * u, cy + 3.5f * u, stroke);
    }

    static void enter(Canvas c, Path p, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        p.reset();
        p.moveTo(cx + 8 * u, cy - 6 * u); p.lineTo(cx + 8 * u, cy + 2 * u); p.lineTo(cx - 8 * u, cy + 2 * u);
        c.drawPath(p, stroke);
        c.drawLine(cx - 8 * u, cy + 2 * u, cx - 3 * u, cy - 3 * u, stroke);
        c.drawLine(cx - 8 * u, cy + 2 * u, cx - 3 * u, cy + 7 * u, stroke);
    }

    static void incognito(Canvas c, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        c.drawLine(cx - 9 * u, cy - 3 * u, cx + 9 * u, cy - 3 * u, stroke);
        c.drawLine(cx - 4.5f * u, cy - 3 * u, cx - 3.5f * u, cy - 8 * u, stroke);
        c.drawLine(cx + 4.5f * u, cy - 3 * u, cx + 3.5f * u, cy - 8 * u, stroke);
        c.drawLine(cx - 3.5f * u, cy - 8 * u, cx + 3.5f * u, cy - 8 * u, stroke);
        c.drawCircle(cx - 4.5f * u, cy + 3.5f * u, 3 * u, stroke);
        c.drawCircle(cx + 4.5f * u, cy + 3.5f * u, 3 * u, stroke);
        c.drawLine(cx - 1.5f * u, cy + 3 * u, cx + 1.5f * u, cy + 3 * u, stroke);
    }

    static void globe(Canvas c, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        c.drawCircle(cx, cy, 10 * u, stroke);
        c.drawOval(cx - 4.5f * u, cy - 10 * u, cx + 4.5f * u, cy + 10 * u, stroke);
        c.drawLine(cx - 10 * u, cy, cx + 10 * u, cy, stroke);
        c.drawLine(cx - 8.6f * u, cy - 5 * u, cx + 8.6f * u, cy - 5 * u, stroke);
        c.drawLine(cx - 8.6f * u, cy + 5 * u, cx + 8.6f * u, cy + 5 * u, stroke);
    }

    static void smiley(Canvas c, Paint fill, Paint stroke, RectF rf, float cx, float cy, float u, int color) {
        stroke.setColor(color); fill.setColor(color);
        c.drawCircle(cx, cy, 10.5f * u, stroke);
        c.drawCircle(cx - 3.8f * u, cy - 3f * u, 1.4f * u, fill);
        c.drawCircle(cx + 3.8f * u, cy - 3f * u, 1.4f * u, fill);
        rf.set(cx - 6.2f * u, cy - 1.8f * u, cx + 6.2f * u, cy + 7.2f * u);
        c.drawArc(rf, 0, 180, true, fill);
    }

    static void mic(Canvas c, Paint stroke, RectF rf, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        rf.set(cx - 4.5f * u, cy - 11 * u, cx + 4.5f * u, cy + 2.5f * u);
        c.drawRoundRect(rf, 4.5f * u, 4.5f * u, stroke);
        rf.set(cx - 8 * u, cy - 6 * u, cx + 8 * u, cy + 8 * u);
        c.drawArc(rf, 0, 180, false, stroke);
        c.drawLine(cx, cy + 8 * u, cx, cy + 11.5f * u, stroke);
        c.drawLine(cx - 4 * u, cy + 11.5f * u, cx + 4 * u, cy + 11.5f * u, stroke);
    }

    static void clip(Canvas c, Paint stroke, RectF rf, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        rf.set(cx - 6 * u, cy - 6 * u, cx + 6 * u, cy + 8 * u);
        c.drawRoundRect(rf, 2.5f * u, 2.5f * u, stroke);
        c.drawLine(cx - 3 * u, cy - 7 * u, cx + 3 * u, cy - 7 * u, stroke);
        c.drawLine(cx - 3 * u, cy, cx + 3 * u, cy, stroke);
        c.drawLine(cx - 3 * u, cy + 3.5f * u, cx + 1.5f * u, cy + 3.5f * u, stroke);
    }

    static void undo(Canvas c, Path p, Paint stroke, float cx, float cy, float u, int color, boolean redo) {
        stroke.setColor(color);
        float k = redo ? -1f : 1f;
        p.reset();
        p.moveTo(cx + 5f * u * k, cy + 6f * u);
        p.lineTo(cx + 5f * u * k, cy + 1f * u);
        p.quadTo(cx + 5f * u * k, cy - 3f * u, cx + 1f * u * k, cy - 3f * u);
        p.lineTo(cx - 5.5f * u * k, cy - 3f * u);
        c.drawPath(p, stroke);
        p.reset();
        p.moveTo(cx - 2.5f * u * k, cy - 6.5f * u);
        p.lineTo(cx - 6f * u * k, cy - 3f * u);
        p.lineTo(cx - 2.5f * u * k, cy + 0.5f * u);
        c.drawPath(p, stroke);
    }

    static void cross(Canvas c, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        c.drawLine(cx - 4 * u, cy - 4 * u, cx + 4 * u, cy + 4 * u, stroke);
        c.drawLine(cx + 4 * u, cy - 4 * u, cx - 4 * u, cy + 4 * u, stroke);
    }

    static void apps(Canvas c, Paint fill, RectF rf, float cx, float cy, float u, int color) {
        fill.setColor(color);
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            float x = cx + (a == 0 ? -8.5f : 0.5f) * u, y = cy + (b == 0 ? -8.5f : 0.5f) * u;
            rf.set(x, y, x + 8 * u, y + 8 * u);
            c.drawRoundRect(rf, 3 * u, 3 * u, fill);
        }
    }

    static void sparkle(Canvas c, Path p, Paint fill, float cx, float cy, float r, int color) {
        fill.setColor(color);
        p.reset();
        p.moveTo(cx, cy - r); p.quadTo(cx, cy, cx + r, cy); p.quadTo(cx, cy, cx, cy + r);
        p.quadTo(cx, cy, cx - r, cy); p.quadTo(cx, cy, cx, cy - r); p.close();
        c.drawPath(p, fill);
    }

    static void gear(Canvas c, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        c.drawCircle(cx, cy, 6.2f * u, stroke);
        c.drawCircle(cx, cy, 2.4f * u, stroke);
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 4 * k;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            c.drawLine(cx + cs * 6.2f * u, cy + sn * 6.2f * u, cx + cs * 9.6f * u, cy + sn * 9.6f * u, stroke);
        }
    }

    static void split(Canvas c, Paint stroke, RectF rf, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        rf.set(cx - 11.5f * u, cy - 7 * u, cx - 2.5f * u, cy + 7 * u); c.drawRoundRect(rf, 2.5f * u, 2.5f * u, stroke);
        rf.set(cx + 2.5f * u, cy - 7 * u, cx + 11.5f * u, cy + 7 * u); c.drawRoundRect(rf, 2.5f * u, 2.5f * u, stroke);
    }

    static void oneHand(Canvas c, Paint stroke, RectF rf, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        rf.set(cx - 10 * u, cy - 9.5f * u, cx - 1.5f * u, cy + 9.5f * u); c.drawRoundRect(rf, 2.5f * u, 2.5f * u, stroke);
        c.drawLine(cx + 10 * u, cy, cx + 3 * u, cy, stroke);
        c.drawLine(cx + 3 * u, cy, cx + 6.5f * u, cy - 3.5f * u, stroke);
        c.drawLine(cx + 3 * u, cy, cx + 6.5f * u, cy + 3.5f * u, stroke);
    }

    static void chevron(Canvas c, Path p, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        p.reset();
        p.moveTo(cx + 3.5f * u, cy - 7.5f * u); p.lineTo(cx - 4 * u, cy); p.lineTo(cx + 3.5f * u, cy + 7.5f * u);
        c.drawPath(p, stroke);
    }

    static void pin(Canvas c, Paint fill, Paint stroke, float cx, float cy, float u, boolean on, int color) {
        stroke.setColor(color); fill.setColor(color);
        if (on) c.drawCircle(cx, cy - 3.5f * u, 4.2f * u, fill); else c.drawCircle(cx, cy - 3.5f * u, 4.2f * u, stroke);
        c.drawLine(cx, cy + 0.7f * u, cx, cy + 8 * u, stroke);
    }

    static void logo(Canvas c, Path p, Paint stroke, Paint fill, float cx, float cy, float u, int color) {
        float w = stroke.getStrokeWidth();
        stroke.setStrokeWidth(3.6f * u); stroke.setColor(color);
        p.reset();
        p.moveTo(cx - 4.5f * u, cy - 7 * u); p.lineTo(cx - 4.5f * u, cy + 5.5f * u); p.lineTo(cx + 5.5f * u, cy + 5.5f * u);
        c.drawPath(p, stroke);
        stroke.setStrokeWidth(w);
    }

    static void check(Canvas c, Path p, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        p.reset();
        p.moveTo(cx - 6 * u, cy + 0.5f * u); p.lineTo(cx - 2 * u, cy + 4.5f * u); p.lineTo(cx + 6.5f * u, cy - 4.5f * u);
        c.drawPath(p, stroke);
    }

    static void tab(Canvas c, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        c.drawLine(cx - 7 * u, cy, cx + 5 * u, cy, stroke);
        c.drawLine(cx + 5 * u, cy, cx + 1.5f * u, cy - 3.5f * u, stroke);
        c.drawLine(cx + 5 * u, cy, cx + 1.5f * u, cy + 3.5f * u, stroke);
        c.drawLine(cx + 8 * u, cy - 5 * u, cx + 8 * u, cy + 5 * u, stroke);
    }

    static void caps(Canvas c, Paint stroke, float cx, float cy, float u, int color) {
        stroke.setColor(color);
        c.drawLine(cx - 5.5f * u, cy, cx, cy - 5.5f * u, stroke);
        c.drawLine(cx, cy - 5.5f * u, cx + 5.5f * u, cy, stroke);
        c.drawLine(cx - 5.5f * u, cy + 4.5f * u, cx + 5.5f * u, cy + 4.5f * u, stroke);
    }

    static void triangle(Canvas c, Path p, Paint fill, float cx, float cy, float u, boolean left, int color) {
        fill.setColor(color);
        float d = left ? -1f : 1f;
        p.reset();
        p.moveTo(cx - d * 3 * u, cy - 4.5f * u); p.lineTo(cx + d * 3.5f * u, cy); p.lineTo(cx - d * 3 * u, cy + 4.5f * u); p.close();
        c.drawPath(p, fill);
    }
}
