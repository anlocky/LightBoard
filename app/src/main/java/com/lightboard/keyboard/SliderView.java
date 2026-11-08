package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class SliderView extends View {
    interface Listener {
        void onValue(int v);
        void onStop(int v);
    }

    private final float den;
    private final int min, max, accent, inactive;
    private final boolean hue;
    private Shader rainbow;
    private int value;
    private Listener lis;
    private boolean drag;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    SliderView(Context c, int min, int max, int value, int accent, int inactive) { this(c, min, max, value, accent, inactive, false); }

    SliderView(Context c, int min, int max, int value, int accent, int inactive, boolean hue) {
        super(c);
        den = c.getResources().getDisplayMetrics().density;
        this.min = min; this.max = max; this.value = value; this.accent = accent; this.inactive = inactive; this.hue = hue;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (hue) {
            float k = 360f / Theme.HUE_MAX;
            rainbow = new LinearGradient(left(), 0, right(), 0,
                    new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000, 0xFF000000},
                    new float[]{0f, k / 6f, k / 3f, k / 2f, 2f * k / 3f, 5f * k / 6f, k, 1f}, Shader.TileMode.CLAMP);
        }
    }

    void setListener(Listener l) { lis = l; }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (44 * den)); }

    private float left() { return (hue ? 16 : 6) * den; }
    private float right() { return getWidth() - (hue ? 16 : 6) * den; }

    @Override protected void onDraw(Canvas c) {
        float l = left(), r = right(), cy = getHeight() / 2f, th = (drag ? 18f : 16f) * den, gap = 6 * den;
        float t = (value - min) / (float) Math.max(1, max - min), x = l + t * (r - l), rad = th / 2f, inner = 3 * den;
        paint.setStyle(Paint.Style.FILL);
        if (hue) {
            paint.setShader(rainbow);
            rf.set(l, cy - th / 2f, r, cy + th / 2f);
            c.drawRoundRect(rf, rad, rad, paint);
            paint.setShader(null);
            float cr = (drag ? 14f : 12f) * den;
            paint.setColor(Color.WHITE);
            c.drawCircle(x, cy, cr, paint);
            paint.setColor(Theme.hueColor(value));
            c.drawCircle(x, cy, cr - 3 * den, paint);
            return;
        }
        if (x - gap > l + 1) {
            paint.setColor(accent);
            rf.set(l, cy - th / 2f, x - gap, cy + th / 2f);
            c.drawRoundRect(rf, rad, rad, paint);
            rf.set(x - gap - rad * 1.2f, cy - th / 2f, x - gap, cy + th / 2f); c.drawRoundRect(rf, inner, inner, paint);
        }
        if (x + gap < r - 1) {
            paint.setColor(inactive);
            rf.set(x + gap, cy - th / 2f, r, cy + th / 2f);
            c.drawRoundRect(rf, rad, rad, paint);
            rf.set(x + gap, cy - th / 2f, x + gap + rad * 1.2f, cy + th / 2f); c.drawRoundRect(rf, inner, inner, paint);
        }
        paint.setColor(accent);
        float hw = (drag ? 3f : 4f) * den, hh = 40 * den / 2f;
        rf.set(x - hw / 2f - (drag ? 0.5f * den : 0f), cy - hh, x + hw / 2f + (drag ? 0.5f * den : 0f), cy + hh);
        c.drawRoundRect(rf, hw, hw, paint);
    }

    private void update(float x, boolean fromUser) {
        float t = Anim.clamp((x - left()) / Math.max(1f, right() - left()), 0f, 1f);
        int v = min + Math.round(t * (max - min));
        if (v != value) { value = v; invalidate(); if (lis != null) lis.onValue(v); }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                drag = true; update(e.getX(), true); invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                update(e.getX(), true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drag = false; invalidate();
                if (lis != null) lis.onStop(value);
                return true;
            default:
                return true;
        }
    }
}
