package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class SideBar extends View {

    interface Listener {
        void onExpand();
        void onFlip();
    }

    private Theme th;
    private final float den;
    private Listener lis;
    private int W, H;
    private final Paint pPanel = new Paint(Paint.ANTI_ALIAS_FLAG), pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int down = -1;
    private boolean leftSide;
    private View mate;

    SideBar(Context c, Theme theme) {
        super(c);
        th = theme;
        den = c.getResources().getDisplayMetrics().density;
        pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeWidth(1.8f * den);
        pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
    }

    void setListener(Listener l) { lis = l; }
    void setMate(View v) { mate = v; }

    void setLeftSide(boolean left) { if (leftSide != left) { leftSide = left; invalidate(); } }

    void setTheme(Theme t) { th = t; }

    void themeChanged() { makeShader(); invalidate(); }

    private void makeShader() {
        if (H == 0) return;
        pPanel.setShader(new LinearGradient(0, 0, 0, H, th.panelTop, th.panelBot, Shader.TileMode.CLAMP));
    }

    @Override protected void onMeasure(int w, int h) {
        int hh = mate != null ? mate.getMeasuredHeight() : 0;
        if (hh == 0 && MeasureSpec.getMode(h) == MeasureSpec.EXACTLY) hh = MeasureSpec.getSize(h);
        setMeasuredDimension(MeasureSpec.getSize(w), hh);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { W = w; H = h; makeShader(); }

    private float by(int i) { return H * (i == 0 ? 0.32f : 0.68f); }

    private int hit(float x, float y) {
        float r = 26 * den;
        for (int i = 0; i < 2; i++) if (Math.abs(y - by(i)) < r && Math.abs(x - W / 2f) < Math.max(r, W / 2f)) return i;
        return -1;
    }

    @Override protected void onDraw(Canvas c) {
        float rad = 16 * den;
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pPanel);
        float cx = W / 2f, u = den;
        for (int i = 0; i < 2; i++) {
            float cy = by(i);
            pFill.setColor(Theme.fade(th.key, down == i ? 0.7f : 1f));
            c.drawCircle(cx, cy, (down == i ? 18 : 20) * u, pFill);
            pStroke.setColor(th.text);
            if (i == 0) {
                c.drawLine(cx - 9 * u, cy, cx + 9 * u, cy, pStroke);
                c.drawLine(cx - 9 * u, cy, cx - 5.5f * u, cy - 3.5f * u, pStroke); c.drawLine(cx - 9 * u, cy, cx - 5.5f * u, cy + 3.5f * u, pStroke);
                c.drawLine(cx + 9 * u, cy, cx + 5.5f * u, cy - 3.5f * u, pStroke); c.drawLine(cx + 9 * u, cy, cx + 5.5f * u, cy + 3.5f * u, pStroke);
            } else {
                float dir = leftSide ? -1f : 1f, tip = cx + dir * 4 * u;
                c.drawLine(cx + dir * 9 * u, cy - 6 * u, cx + dir * 9 * u, cy + 6 * u, pStroke);
                c.drawLine(cx - dir * 8 * u, cy, tip, cy, pStroke);
                c.drawLine(tip, cy, tip - dir * 3.5f * u, cy - 3.5f * u, pStroke);
                c.drawLine(tip, cy, tip - dir * 3.5f * u, cy + 3.5f * u, pStroke);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: down = hit(e.getX(), e.getY()); invalidate(); return true;
            case MotionEvent.ACTION_UP: {
                int h = hit(e.getX(), e.getY());
                int was = down; down = -1; invalidate();
                if (lis != null && h >= 0 && h == was) { if (h == 0) lis.onExpand(); else lis.onFlip(); }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: down = -1; invalidate(); return true;
            default: return true;
        }
    }
}
