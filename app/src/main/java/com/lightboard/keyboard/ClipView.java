package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

final class ClipView extends View {

    interface Listener {
        void onPaste(String text);
        void onAbc();
        void onChanged();
    }

    private static final float HEAD_H = 46, PAD = 10, GAP = 8, CARD_PAD = 11, LINE = 19, MAX_LINES = 3;

    private final Theme th;
    private final int fixedH, slop;
    private final float den;
    private Listener lis;
    private ClipStore store;
    private boolean enabled = true;

    private int W, H;
    private float scrollY, maxScroll;
    private String[][] lines = new String[0][];
    private float[] top = new float[0], hgt = new float[0];
    private boolean dirty = true;

    private float downY, lastY;
    private boolean dragging;

    private final Paint pPanel = new Paint(Paint.ANTI_ALIAS_FLAG), pFill = new Paint(Paint.ANTI_ALIAS_FLAG),
            pStroke = new Paint(Paint.ANTI_ALIAS_FLAG), pText = new Paint(Paint.ANTI_ALIAS_FLAG), pHead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private float offText, offHead;

    ClipView(Context c, Theme theme, int heightPx) {
        super(c);
        th = theme; fixedH = heightPx;
        den = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeWidth(1.6f * den);
        pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        pText.setTextSize(14.5f * den);
        pHead.setTextSize(15f * den); pHead.setTypeface(Typeface.DEFAULT_BOLD); pHead.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics f = pText.getFontMetrics(); offText = -(f.ascent + f.descent) / 2f;
        f = pHead.getFontMetrics(); offHead = -(f.ascent + f.descent) / 2f;
    }

    void setListener(Listener l) { lis = l; }
    void themeChanged() { makeShader(); invalidate(); }

    void open(ClipStore s, boolean historyOn) {
        store = s; enabled = historyOn;
        dirty = true; scrollY = 0f;
        layout();
        invalidate();
    }

    private void makeShader() {
        if (H == 0) return;
        pPanel.setShader(new LinearGradient(0, 0, 0, H, th.panelTop, th.panelBot, Shader.TileMode.CLAMP));
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), fixedH); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w; H = h; makeShader(); dirty = true; layout();
    }

    private void layout() {
        if (!dirty || W == 0 || store == null) return;
        dirty = false;
        int n = store.size();
        lines = new String[n][]; top = new float[n]; hgt = new float[n];
        float y = (HEAD_H + PAD) * den, maxW = W - 2 * PAD * den - 2 * CARD_PAD * den - 78 * den;
        for (int i = 0; i < n; i++) {
            lines[i] = wrap(store.get(i).text, maxW);
            top[i] = y;
            hgt[i] = (2 * CARD_PAD + lines[i].length * LINE) * den;
            y += hgt[i] + GAP * den;
        }
        float content = y - GAP * den + PAD * den;
        maxScroll = Math.max(0f, content - H);
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    private String[] wrap(String raw, float maxW) {
        String s = raw.replace('\n', ' ').replace('\t', ' ');
        ArrayList<String> out = new ArrayList<>(3);
        int pos = 0, n = s.length();
        while (pos < n && out.size() < MAX_LINES) {
            int cnt = Math.max(1, pText.breakText(s, pos, n, true, maxW, null)), end = pos + cnt;
            boolean lastLine = out.size() == MAX_LINES - 1;
            if (end < n) {
                if (lastLine) {
                    cnt = Math.max(1, pText.breakText(s, pos, n, true, maxW - pText.measureText("…"), null));
                    out.add(s.substring(pos, pos + cnt).trim() + "…");
                    return out.toArray(new String[0]);
                }
                int sp = s.lastIndexOf(' ', end - 1);
                if (sp > pos) end = sp + 1;
            }
            out.add(s.substring(pos, end).trim());
            pos = end;
            while (pos < n && s.charAt(pos) == ' ') pos++;
        }
        if (out.isEmpty()) out.add("");
        return out.toArray(new String[0]);
    }

    @Override protected void onDraw(Canvas c) {
        float rad = 16 * den;
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pPanel);
        float hy = HEAD_H * den / 2f;
        pHead.setColor(th.text); pHead.setTextAlign(Paint.Align.CENTER);
        c.drawText(KbText.t("Буфер обмена"), W / 2f, hy + offHead, pHead);
        pHead.setTextAlign(Paint.Align.LEFT); pHead.setColor(th.muted);
        c.drawText("ABC", 18 * den, hy + offHead, pHead);
        boolean has = store != null && store.size() > 0;
        if (has) {
            pHead.setTextAlign(Paint.Align.RIGHT);
            c.drawText(KbText.t("Очистить"), W - 16 * den, hy + offHead, pHead);
        }
        if (!has || !enabled) {
            String msg = KbText.t("Скопированное появится здесь");
            String sub = KbText.t("Закреплённые записи хранятся всегда");
            float maxW = W - 48 * den;
            String[] l1 = wrap(msg, maxW), l2 = sub.isEmpty() ? new String[0] : wrap(sub, maxW);
            float lh = LINE * den, gap = l2.length > 0 ? 8 * den : 0f;
            float y = (HEAD_H * den + H) / 2f - ((l1.length + l2.length) * lh + gap) / 2f + lh / 2f;
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(th.text);
            for (String ln : l1) { c.drawText(ln, W / 2f, y + offText, pText); y += lh; }
            pText.setColor(th.muted); y += gap;
            for (String ln : l2) { c.drawText(ln, W / 2f, y + offText, pText); y += lh; }
            pText.setTextAlign(Paint.Align.LEFT);
            return;
        }
        c.save();
        c.clipRect(0, HEAD_H * den, W, H);
        c.translate(0, -scrollY);
        float x0 = PAD * den, x1 = W - PAD * den, cr = 12 * den;
        for (int i = 0; i < lines.length; i++) {
            float t = top[i], b = t + hgt[i];
            if (b - scrollY < HEAD_H * den || t - scrollY > H) continue;
            ClipStore.Item it = store.get(i);
            pFill.setColor(th.key);
            rf.set(x0, t, x1, b); c.drawRoundRect(rf, cr, cr, pFill);
            pText.setColor(th.text); pText.setTextAlign(Paint.Align.LEFT);
            for (int k = 0; k < lines[i].length; k++) c.drawText(lines[i][k], x0 + CARD_PAD * den, t + (CARD_PAD + LINE * (k + 0.5f)) * den + offText, pText);
            float cy = t + hgt[i] / 2f;
            Icons.pin(c, pFill, pStroke, x1 - 52 * den, cy, den, it.pinned, it.pinned ? th.accent : th.muted);
            Icons.cross(c, pStroke, x1 - 20 * den, cy, den * 1.1f, th.muted);
        }
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = lastY = e.getY(); dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getY() - downY) > slop) dragging = true;
                if (dragging) {
                    scrollY = Math.max(0f, Math.min(maxScroll, scrollY - (e.getY() - lastY)));
                    lastY = e.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) tap(e.getX(), e.getY());
                return true;
            default:
                return true;
        }
    }

    private void tap(float x, float y) {
        if (lis == null) return;
        if (y < HEAD_H * den) {
            if (x < 84 * den) lis.onAbc();
            else if (x > W - 110 * den && store != null && store.size() > 0) { store.clear(); changed(); }
            return;
        }
        if (store == null || !enabled) return;
        float yy = y + scrollY;
        for (int i = 0; i < lines.length; i++) {
            if (yy < top[i] || yy > top[i] + hgt[i]) continue;
            float x1 = W - PAD * den;
            if (x > x1 - 36 * den) { store.remove(i); changed(); }
            else if (x > x1 - 74 * den) { store.togglePin(i); changed(); }
            else lis.onPaste(store.get(i).text);
            return;
        }
    }

    private void changed() {
        dirty = true; layout(); invalidate();
        if (lis != null) lis.onChanged();
    }
}
