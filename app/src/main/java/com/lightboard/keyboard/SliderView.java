package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Ползунок в стиле Material 3 Expressive: толстая закруглённая дорожка и вертикальная «ручка» с зазором. */
final class SliderView extends View {
    interface Listener {
        void onValue(int v);   // во время перетаскивания
        void onStop(int v);    // палец отпущен: значение можно сохранять
    }

    private final float den;
    private final int min, max, accent, inactive;
    private int value;
    private Listener lis;
    private boolean drag;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    SliderView(Context c, int min, int max, int value, int accent, int inactive) {
        super(c);
        den = c.getResources().getDisplayMetrics().density;
        this.min = min; this.max = max; this.value = value; this.accent = accent; this.inactive = inactive;
    }

    void setListener(Listener l) { lis = l; }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), (int) (44 * den)); }

    private float left() { return 6 * den; }
    private float right() { return getWidth() - 6 * den; }

    @Override protected void onDraw(Canvas c) {
        float l = left(), r = right(), cy = getHeight() / 2f, th = (drag ? 18f : 16f) * den, gap = 6 * den;
        float t = (value - min) / (float) Math.max(1, max - min), x = l + t * (r - l), rad = th / 2f, inner = 3 * den;
        paint.setStyle(Paint.Style.FILL);
        if (x - gap > l + 1) { // активная часть
            paint.setColor(accent);
            rf.set(l, cy - th / 2f, x - gap, cy + th / 2f);
            c.drawRoundRect(rf, rad, rad, paint);
            rf.set(x - gap - rad * 1.2f, cy - th / 2f, x - gap, cy + th / 2f); c.drawRoundRect(rf, inner, inner, paint); // торец у ручки менее круглый
        }
        if (x + gap < r - 1) { // оставшаяся часть
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
                getParent().requestDisallowInterceptTouchEvent(true); // прокрутка списка не перехватывает перетаскивание
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
