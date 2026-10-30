package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Переключатель в стиле Material 3 Expressive: дорожка-таблетка, ползунок растёт и «пружинит», внутри появляется галочка. */
final class SwitchView extends View {
    interface Listener { void onChanged(boolean on); }

    private final float den;
    private final int accent, onAccent, trackOff, outline;
    private boolean on;
    private float p;        // 0 выкл .. 1 вкл
    private long last;
    private Listener lis;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check = new Path();

    SwitchView(Context c, boolean checked, int accent, int onAccent, int trackOff, int outline) {
        super(c);
        den = c.getResources().getDisplayMetrics().density;
        this.accent = accent; this.onAccent = onAccent; this.trackOff = trackOff; this.outline = outline;
        on = checked; p = checked ? 1f : 0f;
        setClickable(true);
    }

    void setListener(Listener l) { lis = l; }

    void toggle() {
        on = !on;
        last = 0;
        invalidate();
        if (lis != null) lis.onChanged(on);
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension((int) (52 * den), (int) (32 * den)); }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0f : now - last;
        last = now;
        float tg = on ? 1f : 0f;
        if (p != tg) p = Anim.step(p, tg, dt, 190f);
        float w = 52 * den, h = 32 * den;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Theme.mix(trackOff, accent, p));
        c.drawRoundRect(0, 0, w, h, h / 2f, h / 2f, paint);
        if (p < 1f) { // контур выключенной дорожки гаснет по мере включения
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * den);
            paint.setColor(Theme.fade(outline, 1f - p));
            c.drawRoundRect(den, den, w - den, h - den, h / 2f - den, h / 2f - den, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float k = Anim.back(p), r = (8f + 4f * Math.min(1f, p)) * den, cx = (16f + 20f * k) * den, cy = h / 2f;
        paint.setColor(Theme.mix(outline, onAccent, p));
        c.drawCircle(cx, cy, r, paint);
        if (p > 0.45f) { // галочка внутри ползунка
            float a = Math.min(1f, (p - 0.45f) / 0.55f), u = den * 0.9f;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f * den); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(Theme.fade(accent, a));
            check.reset();
            check.moveTo(cx - 3.6f * u, cy + 0.2f * u); check.lineTo(cx - 1f * u, cy + 2.8f * u); check.lineTo(cx + 3.8f * u, cy - 2.6f * u);
            c.drawPath(check, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (p != tg) postInvalidateOnAnimation();
        else last = 0;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= getHeight()) toggle();
        return true;
    }
}
