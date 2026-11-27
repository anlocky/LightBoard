package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;

import java.util.ArrayList;

final class ClipView extends View {

    interface Listener {
        void onPaste(String text);
        void onAbc();
        void onChanged();
        void onEnable();
    }

    private static final float HEAD_H = 46, PAD = 10, GAP = 8, CARD_PAD = 11, LINE = 19, MAX_LINES = 3;
    private static final int MAXG = 8, B_NONE = 0, B_ABC = 1, B_CLEAR = 2, B_ENABLE = 3, B_CANCEL = 4, B_OK = 5, M_NONE = 0, M_BTN = 1, M_CARD = 2, M_SCROLL = 3, RED = 0xFFE5484D;

    private final Theme th;
    private final int fixedH, slop;
    private final float den;
    private Listener lis;
    private ClipStore store;
    private boolean enabled = true, swipeOn = true, delLeft = true;

    private int W, H, lineW;
    private float scrollY, maxScroll;
    private ClipStore.Item[] items = new ClipStore.Item[0];
    private String[][] lines = new String[0][];
    private float[] top = new float[0], hgt = new float[0], cy = new float[0], ax = new float[0], al = new float[0], cvy = new float[0], avx = new float[0];

    private final String[][] gLines = new String[MAXG][];
    private final float[] gY = new float[MAXG], gH = new float[MAXG], gX = new float[MAXG], gA = new float[MAXG], gV = new float[MAXG], gT = new float[MAXG], gX0 = new float[MAXG];
    private int gN;

    private int mode, dragIdx = -1, pressCard = -1, pressBtn;
    private boolean dragging, swiping, crossed, pressOn, confirm;
    private float downX, downY, lastX, lastY, vx, pressP, confP;
    private long lastMoveT, lastT;
    private boolean running;

    private final Paint pPanel = new Paint(Paint.ANTI_ALIAS_FLAG), pFill = new Paint(Paint.ANTI_ALIAS_FLAG),
            pStroke = new Paint(Paint.ANTI_ALIAS_FLAG), pText = new Paint(Paint.ANTI_ALIAS_FLAG), pHead = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF(), btn = new RectF(), abcR = new RectF(), clrR = new RectF(), cardR = new RectF(), cancelR = new RectF(), okR = new RectF();
    private float offText, offHead;

    private final Runnable frame = new Runnable() { @Override public void run() { step(); } };

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

    void setSwipe(boolean on, boolean deleteLeft) { swipeOn = on; delLeft = deleteLeft; }

    void themeChanged() { makeShader(); invalidate(); }

    void open(ClipStore s, boolean historyOn) {
        store = s; enabled = historyOn;
        confirm = false; confP = 0f; pressOn = false; pressP = 0f; pressBtn = B_NONE; gN = 0;
        scrollY = 0f;
        items = new ClipStore.Item[0];
        sync(false, true);
        invalidate();
    }

    private void makeShader() {
        if (H == 0) return;
        pPanel.setShader(new LinearGradient(0, 0, 0, H, th.panelTop, th.panelBot, Shader.TileMode.CLAMP));
    }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(MeasureSpec.getSize(w), fixedH); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w; H = h; makeShader();
        float hh = HEAD_H * den, m = 6 * den;
        abcR.set(m, m, 84 * den, hh - m);
        clrR.set(W - 112 * den, m, W - m, hh - m);
        sync(false, true);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(frame);
        running = false;
    }

    private static int fade(int c, float a) {
        int al = (int) (((c >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
        return (c & 0x00FFFFFF) | (al << 24);
    }

    private void sync(boolean ghosts, boolean snap) {
        if (W == 0 || store == null) return;
        int n = store.size();
        ClipStore.Item[] oi = items;
        String[][] ol = lines;
        float[] ocy = cy, oax = ax, oal = al, ocv = cvy, oav = avx;
        int olw = lineW;
        ClipStore.Item[] ni = new ClipStore.Item[n];
        String[][] nl = new String[n][];
        float[] nt = new float[n], nh = new float[n], nc = new float[n], nx = new float[n], na = new float[n], nv = new float[n], nw = new float[n];
        boolean[] kept = new boolean[oi.length];
        float y = (HEAD_H + PAD) * den, maxW = W - 2 * PAD * den - 2 * CARD_PAD * den - 78 * den;
        for (int i = 0; i < n; i++) {
            ClipStore.Item it = store.get(i);
            ni[i] = it;
            int j = -1;
            for (int k = 0; k < oi.length; k++) if (oi[k] == it) { j = k; break; }
            if (j >= 0) kept[j] = true;
            nl[i] = j >= 0 && olw == W ? ol[j] : wrap(it.text, maxW);
            nt[i] = y;
            nh[i] = (2 * CARD_PAD + nl[i].length * LINE) * den;
            if (snap) { nc[i] = y; nx[i] = 0f; na[i] = 1f; }
            else if (j >= 0) { nc[i] = ocy[j]; nx[i] = oax[j]; na[i] = oal[j]; nv[i] = ocv[j]; nw[i] = oav[j]; }
            else { nc[i] = y - 16 * den; nx[i] = 0f; na[i] = 0f; }
            y += nh[i] + GAP * den;
        }
        if (ghosts && !snap) {
            for (int j = 0; j < oi.length && gN < MAXG; j++) {
                if (kept[j]) continue;
                float yy = ocy[j] - scrollY;
                if (yy + (2 * CARD_PAD + ol[j].length * LINE) * den < HEAD_H * den || yy > H) continue;
                addGhost(ol[j], ocy[j], (2 * CARD_PAD + ol[j].length * LINE) * den, oax[j], 0f);
            }
        }
        items = ni; lines = nl; top = nt; hgt = nh; cy = nc; ax = nx; al = na; cvy = nv; avx = nw; lineW = W;
        float content = y - GAP * den + PAD * den;
        maxScroll = Math.max(0f, content - H);
        if (scrollY > maxScroll) scrollY = maxScroll;
        dragIdx = -1; pressCard = -1;
        kick();
    }

    private void addGhost(String[] ln, float y, float h, float x, float dir) {
        if (gN >= MAXG) return;
        gLines[gN] = ln; gY[gN] = y; gH[gN] = h; gX[gN] = x; gX0[gN] = x; gA[gN] = 1f; gV[gN] = dir; gT[gN] = 0f;
        gN++;
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

    private void kick() {
        if (running) return;
        running = true;
        lastT = AnimationUtils.currentAnimationTimeMillis();
        postOnAnimation(frame);
    }

    private void step() {
        long now = AnimationUtils.currentAnimationTimeMillis();
        float dt = Math.min(0.032f, Math.max(0.001f, (now - lastT) / 1000f));
        lastT = now;
        float ka = 1f - (float) Math.exp(-dt * 12f), kb = 1f - (float) Math.exp(-dt * 24f);
        boolean more = false;
        for (int i = 0; i < cy.length; i++) {
            float d = top[i] - cy[i];
            if (Math.abs(d) > 0.3f || Math.abs(cvy[i]) > 6f) { cy[i] = Anim.spr(cy[i], cvy[i], top[i], dt * 1000f, 380f, 36f); cvy[i] = Anim.sv; more = true; }
            else { cy[i] = top[i]; cvy[i] = 0f; }
            if (al[i] < 0.995f) { al[i] += (1f - al[i]) * ka; more = true; } else al[i] = 1f;
            if (i != dragIdx || !swiping) {
                if (Math.abs(ax[i]) > 0.5f || Math.abs(avx[i]) > 6f) { ax[i] = Anim.spr(ax[i], avx[i], 0f, dt * 1000f, 520f, 34f); avx[i] = Anim.sv; more = true; }
                else { ax[i] = 0f; avx[i] = 0f; }
            }
        }
        for (int g = 0; g < gN; ) {
            gT[g] += dt / 0.3f;
            float t = Math.min(1f, gT[g]);
            gX[g] = gX0[g] + gV[g] * W * 0.9f * t * t;
            gA[g] = 1f - t * t;
            if (gT[g] >= 1f) {
                gN--;
                gLines[g] = gLines[gN]; gY[g] = gY[gN]; gH[g] = gH[gN]; gX[g] = gX[gN]; gX0[g] = gX0[gN]; gA[g] = gA[gN]; gV[g] = gV[gN]; gT[g] = gT[gN];
                gLines[gN] = null;
            } else { g++; more = true; }
        }
        float pt = pressOn ? 1f : 0f;
        if (Math.abs(pt - pressP) > 0.01f) { pressP += (pt - pressP) * kb; more = true; } else { pressP = pt; if (!pressOn) pressBtn = B_NONE; }
        float ct = confirm ? 1f : 0f;
        if (Math.abs(ct - confP) > 0.01f) { confP += (ct - confP) * kb; more = true; } else confP = ct;
        invalidate();
        if (more) postOnAnimation(frame); else running = false;
    }

    private void confirmRects() {
        float cw = Math.min(W - 40 * den, 320 * den), ch = 142 * den;
        float x0 = (W - cw) / 2f, y0 = (HEAD_H * den + H) / 2f - ch / 2f;
        cardR.set(x0, y0, x0 + cw, y0 + ch);
        float by = y0 + ch - 14 * den - 44 * den, mid = x0 + cw / 2f;
        cancelR.set(x0 + 14 * den, by, mid - 7 * den, by + 44 * den);
        okR.set(mid + 7 * den, by, x0 + cw - 14 * den, by + 44 * den);
    }

    private void pill(Canvas c, RectF r, int id, int normalText, String label) {
        float p = pressBtn == id ? pressP : 0f;
        if (p > 0.01f) {
            pFill.setColor(fade(th.key, p));
            float rr = r.height() / 2f;
            c.drawRoundRect(r, rr, rr, pFill);
        }
        pHead.setColor(p > 0.5f ? th.text : normalText);
        pHead.setTextAlign(Paint.Align.CENTER);
        c.drawText(label, r.centerX(), r.centerY() + offHead, pHead);
    }

    @Override protected void onDraw(Canvas c) {
        float rad = Ui.panelR * den;
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pPanel);
        boolean has = store != null && store.size() > 0;
        pill(c, abcR, B_ABC, th.muted, "ABC");
        pHead.setColor(th.text); pHead.setTextAlign(Paint.Align.CENTER);
        c.drawText(KbText.t("Буфер обмена"), W / 2f, HEAD_H * den / 2f + offHead, pHead);
        if (has && enabled) pill(c, clrR, B_CLEAR, th.muted, KbText.t("Очистить"));
        if (!enabled) {
            String[] l1 = wrap(KbText.t("История буфера выключена"), W - 48 * den);
            float lh = LINE * den, bh = 42 * den, gap = 16 * den;
            pHead.setTextAlign(Paint.Align.CENTER);
            String label = KbText.t("Включить");
            float bw = Math.max(130 * den, pHead.measureText(label) + 48 * den);
            float y = (HEAD_H * den + H) / 2f - (l1.length * lh + gap + bh) / 2f + lh / 2f;
            pText.setTextAlign(Paint.Align.CENTER);
            pText.setColor(th.text);
            for (String ln : l1) { c.drawText(ln, W / 2f, y + offText, pText); y += lh; }
            pText.setTextAlign(Paint.Align.LEFT);
            float top0 = y - lh / 2f + gap;
            btn.set((W - bw) / 2f, top0, (W + bw) / 2f, top0 + bh);
            float sc = pressBtn == B_ENABLE ? 1f - 0.05f * pressP : 1f;
            c.save();
            c.scale(sc, sc, btn.centerX(), btn.centerY());
            pFill.setColor(th.accent);
            c.drawRoundRect(btn, bh / 2f, bh / 2f, pFill);
            if (pressBtn == B_ENABLE && pressP > 0.01f) { pFill.setColor(fade(0x33000000, pressP)); c.drawRoundRect(btn, bh / 2f, bh / 2f, pFill); }
            pHead.setColor(th.onAccent);
            c.drawText(label, W / 2f, top0 + bh / 2f + offHead, pHead);
            c.restore();
            return;
        }
        if (!has && gN == 0) {
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
            float t = cy[i], b = t + hgt[i];
            if (b - scrollY < HEAD_H * den || t - scrollY > H) continue;
            ClipStore.Item it = items[i];
            float a = al[i], dx = ax[i];
            if (Math.abs(dx) > 2 * den) {
                boolean del = (dx < 0) == delLeft;
                float prog = Math.min(1f, Math.abs(dx) / (W * 0.3f));
                pFill.setColor(fade(del ? RED : th.accent, a));
                rf.set(x0, t, x1, b); c.drawRoundRect(rf, cr, cr, pFill);
                float ic = dx < 0 ? x1 - 30 * den : x0 + 30 * den, icy = t + hgt[i] / 2f, s = 0.7f + 0.4f * prog;
                int wc = fade(0xFFFFFFFF, Math.min(1f, prog * 1.6f) * a);
                if (del) Icons.cross(c, pStroke, ic, icy, den * 1.3f * s, wc);
                else Icons.pin(c, pFill, pStroke, ic, icy, den * s, it.pinned, wc);
            }
            pFill.setColor(fade(pressCard == i ? th.keyDown : th.key, a));
            rf.set(x0 + dx, t, x1 + dx, b); c.drawRoundRect(rf, cr, cr, pFill);
            pText.setColor(fade(th.keyText, a)); pText.setTextAlign(Paint.Align.LEFT);
            for (int k = 0; k < lines[i].length; k++) c.drawText(lines[i][k], x0 + dx + CARD_PAD * den, t + (CARD_PAD + LINE * (k + 0.5f)) * den + offText, pText);
            float icy = t + hgt[i] / 2f;
            Icons.pin(c, pFill, pStroke, x1 + dx - 52 * den, icy, den, it.pinned, fade(it.pinned ? th.accent : th.keyMuted, a));
            Icons.cross(c, pStroke, x1 + dx - 20 * den, icy, den * 1.1f, fade(th.keyMuted, a));
        }
        for (int g = 0; g < gN; g++) {
            float a = Math.max(0f, gA[g]), t = gY[g], dx = gX[g];
            pFill.setColor(fade(th.key, a));
            rf.set(x0 + dx, t, x1 + dx, t + gH[g]); c.drawRoundRect(rf, cr, cr, pFill);
            pText.setColor(fade(th.keyText, a)); pText.setTextAlign(Paint.Align.LEFT);
            for (int k = 0; k < gLines[g].length; k++) c.drawText(gLines[g][k], x0 + dx + CARD_PAD * den, t + (CARD_PAD + LINE * (k + 0.5f)) * den + offText, pText);
        }
        c.restore();
        if (confP > 0.01f) drawConfirm(c);
    }

    private void drawConfirm(Canvas c) {
        confirmRects();
        float rad = Ui.panelR * den;
        pFill.setColor(fade(0x66000000, confP));
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pFill);
        float s = 0.94f + 0.06f * confP;
        c.save();
        c.scale(s, s, cardR.centerX(), cardR.centerY());
        pFill.setColor(fade(th.panelBot, confP));
        c.drawRoundRect(cardR, 18 * den, 18 * den, pFill);
        pHead.setTextAlign(Paint.Align.CENTER); pHead.setColor(fade(th.text, confP));
        c.drawText(KbText.t("Очистить историю?"), cardR.centerX(), cardR.top + 30 * den + offHead, pHead);
        pText.setTextAlign(Paint.Align.CENTER); pText.setColor(fade(th.muted, confP));
        c.drawText(KbText.t("Закреплённые записи останутся"), cardR.centerX(), cardR.top + 58 * den + offText, pText);
        pText.setTextAlign(Paint.Align.LEFT);
        float rr = 22 * den;
        float pc = pressBtn == B_CANCEL ? pressP : 0f, po = pressBtn == B_OK ? pressP : 0f;
        pFill.setColor(fade(pc > 0.01f ? th.keyDown : th.key, confP));
        c.drawRoundRect(cancelR, rr, rr, pFill);
        pHead.setColor(fade(th.text, confP));
        c.drawText(KbText.t("Отмена"), cancelR.centerX(), cancelR.centerY() + offHead, pHead);
        pFill.setColor(fade(RED, confP));
        c.drawRoundRect(okR, rr, rr, pFill);
        if (po > 0.01f) { pFill.setColor(fade(0x33000000, confP * po)); c.drawRoundRect(okR, rr, rr, pFill); }
        pHead.setColor(fade(0xFFFFFFFF, confP));
        c.drawText(KbText.t("Очистить"), okR.centerX(), okR.centerY() + offHead, pHead);
        c.restore();
    }

    private int hitButton(float x, float y) {
        if (confirm) {
            confirmRects();
            if (cancelR.contains(x, y)) return B_CANCEL;
            if (okR.contains(x, y)) return B_OK;
            return B_NONE;
        }
        if (y < HEAD_H * den) {
            if (abcR.contains(x, y)) return B_ABC;
            if (clrR.contains(x, y) && store != null && store.size() > 0 && enabled) return B_CLEAR;
            return B_NONE;
        }
        if (!enabled && btn.contains(x, y)) return B_ENABLE;
        return B_NONE;
    }

    private int hitCard(float y) {
        float yy = y + scrollY;
        for (int i = 0; i < cy.length; i++) if (yy >= cy[i] && yy <= cy[i] + hgt[i]) return i;
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = x; downY = lastY = y; dragging = false; swiping = false; crossed = false; vx = 0f;
                lastMoveT = e.getEventTime();
                int b = hitButton(x, y);
                if (b != B_NONE) { mode = M_BTN; pressBtn = b; pressOn = true; kick(); return true; }
                if (confirm) { mode = M_NONE; return true; }
                if (enabled && y > HEAD_H * den) {
                    int i = hitCard(y);
                    if (i >= 0) { mode = M_CARD; dragIdx = i; pressCard = i; invalidate(); return true; }
                    mode = M_SCROLL;
                    return true;
                }
                mode = M_NONE;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mode == M_BTN) {
                    if (pressOn && hitButton(x, y) != pressBtn) { pressOn = false; kick(); }
                    return true;
                }
                if (mode != M_CARD && mode != M_SCROLL) return true;
                float dx = x - downX, dyy = y - downY;
                if (!dragging && !swiping) {
                    if (mode == M_CARD && swipeOn && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dyy)) { swiping = true; pressCard = -1; }
                    else if (Math.abs(dyy) > slop) { dragging = true; pressCard = -1; }
                }
                if (swiping && dragIdx >= 0 && dragIdx < ax.length) {
                    long dtm = Math.max(1L, e.getEventTime() - lastMoveT);
                    vx = 0.6f * vx + 0.4f * ((x - lastX) / dtm * 1000f);
                    lastMoveT = e.getEventTime();
                    float lim = W * 0.8f, v = dx;
                    if (Math.abs(v) > lim) v = Math.signum(v) * lim;
                    ax[dragIdx] = v;
                    boolean over = Math.abs(v) > W * 0.3f;
                    if (over && !crossed) { crossed = true; performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }
                    else if (!over) crossed = false;
                    lastX = x;
                    invalidate();
                } else if (dragging) {
                    scrollY = Math.max(0f, Math.min(maxScroll, scrollY - (y - lastY)));
                    lastY = y;
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (confirm && mode == M_NONE) {
                    confirmRects();
                    if (!cardR.contains(x, y)) { confirm = false; kick(); }
                    return true;
                }
                if (mode == M_BTN) {
                    boolean fire = pressOn && hitButton(x, y) == pressBtn;
                    int b = pressBtn;
                    pressOn = false; kick();
                    if (fire) action(b);
                    return true;
                }
                if (swiping && dragIdx >= 0 && dragIdx < ax.length) {
                    float dx = ax[dragIdx];
                    boolean fast = Math.abs(vx) > 1100f * den && Math.signum(vx) == Math.signum(dx) && Math.abs(dx) > 24 * den;
                    if (Math.abs(dx) > W * 0.3f || fast) {
                        boolean del = (dx < 0) == delLeft;
                        if (del) deleteItem(dragIdx, dx < 0 ? -1f : 1f); else pinItem(dragIdx);
                    } else { avx[dragIdx] = vx; kick(); }
                    swiping = false; pressCard = -1; dragIdx = -1;
                    invalidate();
                    return true;
                }
                if (mode == M_CARD && !dragging) tapCard(x, y);
                pressCard = -1; dragIdx = -1; swiping = false; dragging = false;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                pressOn = false; pressCard = -1; swiping = false; dragging = false; dragIdx = -1;
                kick(); invalidate();
                return true;
            }
            default:
                return true;
        }
    }

    private void action(int b) {
        if (b == B_ABC) { if (lis != null) lis.onAbc(); }
        else if (b == B_CLEAR) { confirm = true; kick(); }
        else if (b == B_CANCEL) { confirm = false; kick(); }
        else if (b == B_OK) {
            confirm = false;
            if (store != null) { store.clear(); sync(true, false); changed(); }
            kick();
        } else if (b == B_ENABLE) { if (lis != null) lis.onEnable(); }
    }

    private void tapCard(float x, float y) {
        if (lis == null || store == null) return;
        int i = hitCard(y);
        if (i < 0) return;
        float x1 = W - PAD * den;
        if (x > x1 - 36 * den) deleteItem(i, 1f);
        else if (x > x1 - 74 * den) pinItem(i);
        else lis.onPaste(items[i].text);
    }

    private void deleteItem(int i, float dir) {
        if (store == null || i < 0 || i >= items.length) return;
        addGhost(lines[i], cy[i], hgt[i], ax[i], dir);
        store.remove(i);
        sync(false, false);
        changed();
    }

    private void pinItem(int i) {
        if (store == null || i < 0 || i >= items.length) return;
        store.togglePin(i);
        sync(false, false);
        changed();
    }

    private void changed() {
        if (lis != null) lis.onChanged();
    }
}
