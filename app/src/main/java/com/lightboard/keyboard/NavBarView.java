package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

final class NavBarView extends View {
    interface Listener { void onTab(int i); }

    private final float den;
    private final String[] labels;
    private final int bg, text, muted, indicator;
    private int sel;
    private float pos;
    private long selT;
    private Listener lis;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG), pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    NavBarView(Context c, String[] labels, int bg, int text, int muted, int indicator) {
        super(c);
        den = c.getResources().getDisplayMetrics().density;
        this.labels = labels; this.bg = bg; this.text = text; this.muted = muted; this.indicator = indicator;
        pText.setTextAlign(Paint.Align.CENTER); pText.setTextSize(12.5f * den);
        paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setListener(Listener l) { lis = l; }

    void select(int i) { sel = i; pos = i; invalidate(); }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (80 * den)); }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), cell = w / labels.length;
        paint.setStyle(Paint.Style.FILL); paint.setColor(bg);
        c.drawRect(0, 0, w, getHeight(), paint);
        boolean moving = Math.abs(pos - sel) > 0.002f;
        if (moving) pos += (sel - pos) * 0.3f; else pos = sel;
        long now = SystemClock.uptimeMillis();
        float bounce = selT == 0 ? 1f : Anim.clamp((now - selT) / 320f, 0f, 1f);
        float iy = 30 * den;

        float stretch = 1f + Math.min(0.5f, Math.abs(sel - pos) * 0.6f);
        float icx = (pos + 0.5f) * cell, iw = 32 * den * stretch;
        paint.setColor(indicator);
        rf.set(icx - iw, iy - 16 * den, icx + iw, iy + 16 * den);
        c.drawRoundRect(rf, 16 * den, 16 * den, paint);
        for (int i = 0; i < labels.length; i++) {
            float cx = (i + 0.5f) * cell, on = i == sel ? 1f : 0f;
            float s = i == sel ? 0.85f + 0.15f * Anim.back(bounce) : 1f;
            int col = i == sel ? text : muted;
            c.save(); c.scale(s * 1.18f, s * 1.18f, cx, iy);
            icon(c, i, cx, iy, col);
            c.restore();
            pText.setColor(col);
            pText.setTypeface(i == sel || Ui.appBold ? Typeface.DEFAULT_BOLD : Typeface.create("sans-serif-medium", Typeface.NORMAL));
            c.drawText(labels[i], cx, 61 * den, pText);
        }
        if (moving || bounce < 1f) postInvalidateOnAnimation();
        else selT = 0;
    }

    private void icon(Canvas c, int i, float cx, float cy, int col) {
        float u = den;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2f * u); paint.setColor(col);
        switch (i) {
            case 0:
                path.reset();
                path.moveTo(cx - 9 * u, cy); path.lineTo(cx, cy - 8 * u); path.lineTo(cx + 9 * u, cy);
                path.moveTo(cx - 6.5f * u, cy - 2 * u); path.lineTo(cx - 6.5f * u, cy + 8 * u); path.lineTo(cx + 6.5f * u, cy + 8 * u); path.lineTo(cx + 6.5f * u, cy - 2 * u);
                c.drawPath(path, paint);
                break;
            case 1:
                c.drawCircle(cx, cy, 9 * u, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - 3.5f * u, cy - 2.5f * u, 1.7f * u, paint);
                c.drawCircle(cx + 1.5f * u, cy - 4 * u, 1.7f * u, paint);
                c.drawCircle(cx + 4.5f * u, cy + 0.5f * u, 1.7f * u, paint);
                break;
            case 2:
                rf.set(cx - 10 * u, cy - 6.5f * u, cx + 10 * u, cy + 6.5f * u);
                c.drawRoundRect(rf, 3 * u, 3 * u, paint);
                paint.setStyle(Paint.Style.FILL);
                for (int k = 0; k < 3; k++) { c.drawCircle(cx - 5 * u + k * 5 * u, cy - 2 * u, 1.1f * u, paint); }
                c.drawRect(cx - 5 * u, cy + 1.5f * u, cx + 5 * u, cy + 3 * u, paint);
                break;
            case 3:
                rf.set(cx - 8 * u, cy - 9 * u, cx + 8 * u, cy + 9 * u);
                c.drawRoundRect(rf, 3 * u, 3 * u, paint);
                c.drawLine(cx - 4 * u, cy - 9 * u, cx - 4 * u, cy + 9 * u, paint);
                c.drawLine(cx - 1 * u, cy - 3 * u, cx + 4.5f * u, cy - 3 * u, paint);
                c.drawLine(cx - 1 * u, cy + 1 * u, cx + 4.5f * u, cy + 1 * u, paint);
                break;
            default:
                c.drawCircle(cx, cy, 9.5f * u, paint);
                c.drawLine(cx, cy - 1 * u, cx, cy + 5 * u, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy - 4.5f * u, 1.3f * u, paint);
                break;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int i = Math.max(0, Math.min(labels.length - 1, (int) (e.getX() / (getWidth() / (float) labels.length))));
            if (i != sel) { sel = i; selT = SystemClock.uptimeMillis(); invalidate(); if (lis != null) lis.onTab(i); }
        }
        return true;
    }
}
