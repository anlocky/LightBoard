package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Группа кнопок-таблеток (Material 3 Expressive): выбранная «таблетка» плавно переезжает к новому пункту, цвет подписей перетекает.
 * Ширина ячейки зависит от длины подписи, а если всё вместе не помещается, шрифт слегка уменьшается: подписи не налезают друг на друга.
 */
final class SegmentView extends View {
    interface Listener { void onSelect(int i); }

    private final float den;
    private final String[] labels;
    private final int accent, onAccent, tonal, text;
    private int sel;
    private float pos;
    private Listener lis;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float[] edges;   // границы ячеек в пикселях (n + 1 значение)
    private float textScale = 1f;
    private int laidW = -1;

    SegmentView(Context c, String[] labels, int sel, int accent, int onAccent, int tonal, int text) {
        super(c);
        den = c.getResources().getDisplayMetrics().density;
        this.labels = labels; this.sel = sel; pos = sel;
        this.accent = accent; this.onAccent = onAccent; this.tonal = tonal; this.text = text;
        edges = new float[labels.length + 1];
        pText.setTextAlign(Paint.Align.CENTER); pText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    void setListener(Listener l) { lis = l; }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (44 * den)); }

    /** Ширины ячеек по длине подписей; если не помещается — сначала сжимаем шрифт (до 70 %), остаток делим пропорционально. */
    private void layoutCells(float w) {
        if ((int) w == laidW) return;
        laidW = (int) w;
        int n = labels.length;
        float pad = 4 * den, gap = 12 * den, avail = w - 2 * pad;
        float[] tw = new float[n];
        textScale = 1f;
        float sum = 0, textSum = 0;
        pText.setTextSize(14 * den);
        for (int i = 0; i < n; i++) { tw[i] = pText.measureText(labels[i]); textSum += tw[i]; }
        sum = textSum + 2 * gap * n;
        if (sum > avail) {
            textScale = Math.max(0.7f, Math.min(1f, (avail - 2 * gap * n) / textSum));
            sum = textSum * textScale + 2 * gap * n;
        }
        float x = pad;
        edges[0] = x;
        for (int i = 0; i < n; i++) { x += (tw[i] * textScale + 2 * gap) * avail / sum; edges[i + 1] = x; }
    }

    private float edge(float p) {
        int n = labels.length, i = (int) Math.floor(p);
        if (i < 0) return edges[0];
        if (i >= n) return edges[n];
        return edges[i] + (edges[i + 1] - edges[i]) * (p - i);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), pad = 4 * den;
        layoutCells(w);
        paint.setColor(tonal);
        c.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, paint);
        boolean moving = Math.abs(pos - sel) > 0.003f;
        pos = moving ? pos + (sel - pos) * 0.32f : sel;
        float stretch = Math.min(0.12f, Math.abs(sel - pos) * 0.1f) * (edges[1] - edges[0]); // «жидкая» таблетка растягивается на ходу
        paint.setColor(accent);
        rf.set(edge(pos) - stretch, pad, edge(pos + 1) + stretch, h - pad);
        c.drawRoundRect(rf, (h - 2 * pad) / 2f, (h - 2 * pad) / 2f, paint);
        pText.setTextSize(14 * den * textScale);
        float ty = h / 2f - (pText.ascent() + pText.descent()) / 2f;
        for (int i = 0; i < labels.length; i++) {
            pText.setColor(Theme.mix(text, onAccent, Anim.clamp(1f - Math.abs(pos - i), 0f, 1f)));
            c.drawText(labels[i], (edges[i] + edges[i + 1]) / 2f, ty, pText);
        }
        if (moving) postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            layoutCells(getWidth());
            int i = labels.length - 1;
            for (int k = 0; k < labels.length; k++) if (e.getX() < edges[k + 1]) { i = k; break; }
            if (i != sel) { sel = i; invalidate(); if (lis != null) lis.onSelect(i); }
        }
        return true;
    }
}
