package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Locale;

final class MaterialView extends BoardView {

    { numRow = true; }
    private static final float PITCH = 46, STRIP = 48, PAD = 4, GAP = 4, BOT = 8, BAR = 46, PANEL_R = 18;
    private static final int T_GLOBE = 6, T_PUNCT = 7, T_MIC = 8;



    private boolean logoOn;

    private String clipDisp;


    private final long[] pressT = new long[MAXP];

    private final Runnable clearRun = new Runnable() {
        @Override public void run() {
            int s = clearSlot;
            if (s < 0 || !used[s] || lis == null) return;
            fired[s] = true; clearSlot = -1;
            buzz(HapticFeedbackConstants.LONG_PRESS);
            lis.onClearAll();
            invalidate();
        }
    };
    private final Runnable rep = new Runnable() {
        @Override public void run() {
            if (repSlot < 0 || lis == null) return;
            lis.onBack();
            lis.onKeySound(KeySounds.DELETE);
            repCount++;
            h.postDelayed(this, repCount > 12 ? 35 : 55);
        }
    };



    MaterialView(Context c, Theme theme) {
        super(c);
        th = theme;
        den = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < MAXP; i++) {
            final int s = i; psug[i] = -1;
            lp[i] = new Runnable() { @Override public void run() { longPress(s); } };
        }
        pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeWidth(d(1.8f));
        pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        pTrail.setStyle(Paint.Style.STROKE); pTrail.setStrokeCap(Paint.Cap.ROUND); pTrail.setStrokeJoin(Paint.Join.ROUND);
        setup(pLetter, 22); setup(pMid, 15); setup(pSug, 17); setup(pPop, 32);
        offLetter = off(pLetter); offMid = off(pMid); offSug = off(pSug); offPop = off(pPop);
    }

    @Override public void setListener(KeyboardView.Listener l) { lis = l; }
    @Override public void setLogo(boolean on) { if (logoOn != on) { logoOn = on; invalidate(); } }

    private float hsEff() { return hs * (numpad != 0 && numRow ? 1.25f : 1f); }

    void restyle() {
        boolean np = numpad != 0;
        setup(pLetter, 22 * ts * fs * (np ? 1.12f : 1f)); setup(pMid, 15 * ts * fs * (np ? 1.08f : 1f)); setup(pPop, 32 * ts * fs);
        if (ts > 1f || np) { Typeface md = Typeface.create("sans-serif-medium", Typeface.NORMAL); pLetter.setTypeface(md); pMid.setTypeface(md); }
        setup(pHint, 10 * ts * fs);
        if (bold) { pLetter.setTypeface(Typeface.DEFAULT_BOLD); pMid.setTypeface(Typeface.DEFAULT_BOLD); pPop.setTypeface(Typeface.DEFAULT_BOLD); pHint.setTypeface(Typeface.DEFAULT_BOLD); }
        offLetter = off(pLetter); offMid = off(pMid); offPop = off(pPop); offHint = off(pHint);
    }
    @Override public void setRound(int v) { if (round != v) { round = v; build(); invalidate(); } }
    @Override public int[] strip() { return new int[]{(int) sx0(), (int) (W - sx1()), (int) stripBottom()}; }
    @Override public void playEntrance() { if (toolsOpen || toolsP != 0f) { toolsOpen = false; toolsP = 0f; if (lis != null) lis.onToolsToggle(false); invalidate(); } }
    @Override public void themeChanged() { invalidate(); }
    @Override public void setEnterLabel(String s) { if (!eq(enterLabel, s)) { enterLabel = s; invalidate(); } }
    @Override public void setPage(int p) {
        p = Math.max(0, Math.min(3, p));
        if (page == p) return;
        page = p;
        if (W > 0) pageP = 0f;
        build(); invalidate();
    }
    @Override public void setShift(int s) { if (shift != s) { shift = s; invalidate(); } }

    @Override public void setClip(String s) { if (!eq(clipText, s)) { clipText = s; clipDisp = null; sugP = 0f; invalidate(); } }

    void build() {
        if (W == 0) return;
        ArrayList<Key> L = new ArrayList<>(48);
        float pad = d(PAD), gx = d(GAP), full = W - 2 * pad, top = d(showWords ? STRIP : 6);
        float pitch = d(PITCH) * hsEff(), kh = pitch - d(9), vg = (pitch - kh) / 2f;
        float[] y = new float[5];
        for (int i = 0; i < 5; i++) y[i] = top + (i - (numRow ? 0 : 1)) * pitch + vg;

        float[] ys = new float[4];
        for (int i = 0; i < 4; i++) ys[i] = top + i * pitch + vg;
        int npm = numpad;
        if (npm != 0) {
            KeyPlan np = KeyPlan.numpad(npm);
            float gp = d(10), nw = tablet ? Math.min(full, np.total * 1.45f * kh + (np.total - 1f) * gp) : full;
            buildFromPlan(L, np, (W - nw) / 2f, nw, gp, ys, kh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }
        if (tablet) {
            buildFromPlan(L, KeyPlan.tablet(lay, numRow, page), pad, full, gx, y, kh, numRow ? 0 : 1);
            finishKeys(L, gx);
            return;
        }

        if (page != 0) {
            buildFromPlan(L, KeyPlan.symbols(lay, page, numRow), pad, full, gx, numRow ? y : ys, kh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }

        float kwn = (full - 9 * gx) / 10f;
        if (numRow) row(L, "1234567890", pad, y[0], kwn, kh, gx, false, 0);

        if (page == 0) {
            final String r1 = lay.r1, r2 = lay.r2, r3 = lay.r3;
            final int cols = lay.cols;
            float kw = (full - (cols - 1) * gx) / cols;
            float w1 = r1.length() * kw + (r1.length() - 1) * gx;
            row(L, r1, r1.length() == cols ? pad : (W - w1) / 2f, y[1], kw, kh, gx, true, 1);
            float w2 = r2.length() * kw + (r2.length() - 1) * gx;
            row(L, r2, (W - w2) / 2f, y[2], kw, kh, gx, true, 2);
            float w3 = r3.length() * kw + (r3.length() - 1) * gx, rem = full - w3;
            float sw = Math.min(1.5f * kw, (rem - 2 * gx) / 2f), side = (rem - 2 * sw) / 2f;
            Key sh = new Key(T_SHIFT, null, null, 0, 3);
            add(L, sh, pad, y[3], sw, kh); sh.rad = kh * 0.5f;
            row(L, r3, pad + sw + side, y[3], kw, kh, gx, true, 3);
            Key bk = new Key(T_BACK, null, null, 0, 3);
            add(L, bk, W - pad - sw, y[3], sw, kh); bk.rad = kh * 0.5f;
        }

        float[] w;
        int[] types;
        if (bb()) {
            w = new float[]{105f, 400f, 60f, 97f};
            types = new int[]{T_PAGE, T_SPACE, T_PUNCT, T_ENTER};
        } else if (showWords) {
            w = langKey ? new float[]{105f, 60f, 60f, 280f, 60f, 97f} : new float[]{105f, 60f, 340f, 60f, 97f};
            types = langKey ? new int[]{T_PAGE, T_GLOBE, T_LANG, T_SPACE, T_PUNCT, T_ENTER} : new int[]{T_PAGE, T_GLOBE, T_SPACE, T_PUNCT, T_ENTER};
        } else {
            w = langKey ? new float[]{105f, 60f, 60f, 60f, 220f, 60f, 97f} : new float[]{105f, 60f, 60f, 280f, 60f, 97f};
            types = langKey ? new int[]{T_PAGE, T_GLOBE, T_LANG, T_MIC, T_SPACE, T_PUNCT, T_ENTER} : new int[]{T_PAGE, T_GLOBE, T_MIC, T_SPACE, T_PUNCT, T_ENTER};
        }
        if (!micOn) for (int i = 0; i < types.length; i++) if (types[i] == T_MIC) {
            float[] w2 = new float[w.length - 1]; int[] t2 = new int[w.length - 1];
            for (int j = 0, q = 0; j < w.length; j++) { if (j == i) continue; w2[q] = w[j]; t2[q++] = types[j]; }
            for (int j = 0; j < t2.length; j++) if (t2[j] == T_SPACE) w2[j] += w[i];
            w = w2; types = t2; break;
        }
        float tot = 0, x = pad, c123 = 0, cEn = 0;
        for (float v : w) tot += v;
        float avail = full - (w.length - 1) * gx;
        for (int i = 0; i < w.length; i++) {
            float wi = avail * w[i] / tot;
            Key k = types[i] == T_PAGE ? new Key(T_PAGE, page == 0 ? "?123" : "ABC", null, page == 0 ? 1 : 0, 4)
                    : types[i] == T_PUNCT ? new Key(T_PUNCT, ".", PUNCT_MENU, 0, 4) : new Key(types[i], null, null, 0, 4);
            add(L, k, x, y[4], wi, kh);
            k.rad = types[i] == T_PUNCT ? kh * 0.34f : kh * 0.5f;
            if (types[i] == T_PAGE) c123 = x + wi / 2f;
            if (types[i] == T_ENTER) cEn = x + wi / 2f;
            x += wi + gx;
        }
        if (bb()) {
            float it = y[4] + kh + d(5), ib = H - d(3), hw = d(28);
            float cA = swapIcons ? cEn : c123, cB = swapIcons ? c123 : cEn;
            if (langKey) {
                float h2 = d(23), off = d(25);
                add(L, new Key(T_GLOBE, null, null, 0, 5), cA - off - h2, it, 2 * h2, ib - it);
                add(L, new Key(T_LANG, null, null, 0, 5), cA + off - h2, it, 2 * h2, ib - it);
            } else add(L, new Key(T_GLOBE, null, null, 0, 5), cA - hw, it, 2 * hw, ib - it);
            if (micOn && (micPos != 0 || !showWords)) add(L, new Key(T_MIC, null, null, 0, 5), cB - hw, it, 2 * hw, ib - it);
        }

        finishKeys(L, gx);
    }

    private int vt(int kind) {
        switch (kind) {
            case KeyPlan.SHIFT: return T_SHIFT;
            case KeyPlan.BACK: return T_BACK;
            case KeyPlan.PAGE: return T_PAGE;
            case KeyPlan.SPACE: return T_SPACE;
            case KeyPlan.ENTER: return T_ENTER;
            case KeyPlan.EMOJI: return T_GLOBE;
            case KeyPlan.MIC: return T_MIC;
            case KeyPlan.PUNCT: return T_PUNCT;
            case KeyPlan.LANG: return T_LANG;
            case KeyPlan.TAB: return T_TAB;
            case KeyPlan.CAPS: return T_CAPS;
            case KeyPlan.LEFT: return T_LEFT;
            case KeyPlan.RIGHT: return T_RIGHT;
            default: return T_CHAR;
        }
    }

    private void buildFromPlan(ArrayList<Key> L, KeyPlan plan, float x0, float full, float gx, float[] y, float rh, int off) {
        final float c = (full - (plan.total - 1f) * gx) / plan.total;
        for (int r = 0; r < plan.rows.size(); r++) {
            float x = x0;
            for (KeyPlan.Spec sp : plan.rows.get(r)) {
                float w = sp.w * c + (sp.w - 1f) * gx;
                Key k = new Key(vt(sp.kind), sp.label, ".".equals(sp.label) ? android.text.TextUtils.join("|", PUNCT_ITEMS) : (sp.letter ? lay.alt(sp.label.charAt(0)) : null), sp.target, r + off);
                if (sp.letter) k.upper = lay.upper(k.label);
                add(L, k, x, y[r + off], w, rh);
                k.rad = (plan.pill || sp.kind == KeyPlan.ENTER || (sp.kind == KeyPlan.PAGE && sp.target == 0)) ? rh * 0.5f : Math.min(rh * 0.36f, w * 0.5f);
                x += w + gx;
            }
        }
    }

    private void splitFrame(boolean on) {
        float m = d(PAD);
        for (Key k : keys) {
            if (!on) { k.l = k.sl; k.r = k.sr; continue; }
            k.sl = k.l; k.sr = k.r;
            if (k.type == T_SPACE) { k.l = Split.xp(k.ol, W, m, false, spP); k.r = Split.xp(k.orr, W, m, true, spP); }
            else { boolean rt = (k.ol + k.orr) / 2f >= W / 2f; k.l = Split.xp(k.ol, W, m, rt, spP); k.r = Split.xp(k.orr, W, m, rt, spP); }
        }
    }

    private void finishKeys(ArrayList<Key> L, float gx) {
        keys = L.toArray(new Key[0]);
        for (Key ok : keys) { ok.ol = ok.l; ok.orr = ok.r; }
        applyHints(1, numRow, numpad != 0);
        for (Key rk : keys) rk.rad = Shape.radius(rk.rad, Math.min(rk.r - rk.l, rk.b - rk.t) * 0.5f, round);
        if (split) for (Key sk : keys) {
            if (sk.type == T_SPACE) { sk.l = Split.x(sk.l, W, d(PAD), false); sk.r = Split.x(sk.r, W, d(PAD), true); }
            else { boolean rt = (sk.l + sk.r) / 2f >= W / 2f; sk.l = Split.x(sk.l, W, d(PAD), rt); sk.r = Split.x(sk.r, W, d(PAD), rt); }
        }
        StringBuilder sb = new StringBuilder(40);
        int n = 0;
        for (Key ck : keys) if (ck.type == T_CHAR && ck.row >= 1 && page == 0 && n < 40) {
            sb.append(ck.label); lcx[n] = (ck.l + ck.r) / 2f; lcy[n] = (ck.t + ck.b) / 2f; n++;
            kwPitch = (ck.r - ck.l) + gx;
        }
        letters = sb.toString();
    }

    private void row(ArrayList<Key> L, String chars, float x0, float y, float kw, float kh, float gx, boolean letters, int rowIdx) {
        for (int i = 0; i < chars.length(); i++) {
            char ch = chars.charAt(i);
            String alt = letters ? lay.alt(ch) : null;
            Key k = new Key(T_CHAR, String.valueOf(ch), alt, 0, rowIdx);
            if (letters) k.upper = lay.upper(k.label);
            add(L, k, x0 + i * (kw + gx), y, kw, kh);
            k.rad = Math.min(kh * 0.36f, kw * 0.5f);
        }
    }

    @Override protected void onMeasure(int ws, int hSpec) {
        int w = MeasureSpec.getSize(ws);
        if (w == 0) w = getResources().getDisplayMetrics().widthPixels;
        setMeasuredDimension(w, Math.round(d(showWords ? STRIP : 6) + d((numRow ? PITCH : 0) + 4 * PITCH) * hs + d((bb() ? BAR : BOT) + botPad)));
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        W = w; H = hh; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; clipDisp = null; build();
    }


    private boolean tick(long now) {
        float dt = lastFrame == 0 ? 0f : Math.min(48f, now - lastFrame);
        boolean a = false;
        for (Key k : keys) {
            float tg = k.down ? 1f : 0f;
            if (k.p != tg) { k.p = Anim.step(k.p, tg, dt, tg > k.p ? 40f : 140f); a = true; }
        }
        for (int i = 0; i < MAXP; i++) if (used[i] && pk[i] != null && pk[i].type == T_CHAR && now - pressT[i] < 80) a = true;
        if (pageP < 1f) { pageP = Anim.step(pageP, 1f, dt, 160f); a = true; }
        if (sugP < 1f) { sugP = Anim.step(sugP, 1f, dt, 130f); a = true; }
        float tgT = toolsOpen ? 1f : 0f, tgO = oneHand != 0 ? 1f : 0f, tgS = split ? 1f : 0f;
        if (toolsP != tgT) { toolsP = Anim.step(toolsP, tgT, dt, 230f); a = true; }
        if (onP != tgO) { onP = Anim.step(onP, tgO, dt, 220f); a = true; }
        if (spP != tgS) { spP = Anim.step(spP, tgS, dt, 220f); a = true; }
        if (menuOn) { menuP = Anim.step(menuP, 1f, dt, MenuFx.OPEN); a = true; } else if (menuP > 0f) { menuP = Anim.step(menuP, 0f, dt, MenuFx.CLOSE); a = true; }
        if (menuP > 0f && menuItems != null && menuSel >= 0 && menuSel < menuItems.length)
            a |= fx.step(dt, menuOn, menuSel, menuR - (menuSel % menuCols + 0.5f) * menuCell, menuB - (menuSel / menuCols + 0.5f) * menuCellH);
        else a |= fx.step(dt, menuOn, -1, 0f, 0f);
        if (clearSlot >= 0) a = true; else if (listening) slowTick = true;
        if (lineOn && Trail.live(trT, gn, SystemClock.uptimeMillis())) a = true;
        if (langT != 0) { if (now - langT < 1100) a = true; else langT = 0; }
        lastFrame = a ? now : 0;
        return a;
    }

    void draw0(Canvas c) {
        long now = SystemClock.uptimeMillis();
        slowTick = false;
        boolean anim = tick(now);
        float rad = d(PANEL_R);
        pFill.setColor(th.panelTop);
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pFill);
        drawStrip(c);
        float pe = Anim.ease(pageP);
        boolean sa = Math.abs(spP - (split ? 1f : 0f)) > 0.002f && numpad == 0;
        if (sa) splitFrame(true);
        for (Key k : keys) drawKey(c, k, pe, now);
        if (sa) splitFrame(false);
        for (int i = 0; i < MAXP; i++) {
            Key k = pk[i];
            if (popupOn && used[i] && k != null && k.type == T_CHAR && !fired[i]) drawPopup(c, k, (shift != 0 && page == 0) ? k.upper : k.label, Anim.ease(Math.min(1f, (now - pressT[i]) / 70f)));
        }
        if (menuP > 0f) drawMenu(c);
        if (clearSlot >= 0) drawClearArc(c, now);
        long trNow = SystemClock.uptimeMillis();
        if (lineOn && Trail.live(trT, gn, trNow)) drawTrail(c, trNow);
        if (anim) postInvalidateOnAnimation(); else if (slowTick) invalidateSlow();
    }

    private void drawStrip(Canvas c) {
        if (!showWords) return;
        float cy = d(STRIP) / 2f + d(1), sp = Anim.ease(sugP), u = den;
        float bx = menuSwap ? W - d(27) : d(27);
        if (toolsP < 1f) Icons.apps(c, pFill, rf, bx, cy, u, Theme.fade(th.text, 1f - toolsP));
        if (toolsP > 0f) Icons.chevron(c, path, pStroke, bx, cy, u, Theme.fade(th.text, toolsP));

        float mx = menuSwap ? d(34) : W - d(34);
        if (micTop()) {
        if (listening) {
            float ph = (SystemClock.uptimeMillis() % 1100) / 1100f;
            pFill.setColor(Theme.fade(th.accent, 0.35f * (1f - ph))); c.drawCircle(mx, cy, d(19) + d(9) * ph, pFill);
            setMicBox(mx, cy, d(30));
        }
        pFill.setColor(listening ? th.accent : th.key);
        c.drawCircle(mx, cy, d(19), pFill);
        int mc = listening ? th.onAccent : th.text;
        Icons.mic(c, pStroke, rf, mx - d(1.5f), cy + d(1), u * 0.9f, mc);
        Icons.sparkle(c, path, pFill, mx + d(9), cy - d(8), d(4.2f), mc);
        }

        float x0 = sx0(), x1 = sx1(), cell = (x1 - x0) / 3f;
        if (toolsP > 0f) MenuBar.draw(c, pStroke, pFill, rf, x0, x1, cy, u, th.text, th.accent, onP, spP, toolsP, menuPressed());
        if (toolsP >= 1f || stripHidden) return;
        sp *= 1f - toolsP;
        if (clipText != null) {
            float l = x0, r = x1;
            pFill.setColor(Theme.fade(th.key, sp));
            c.drawRoundRect(l, cy - d(17), r, cy + d(17), d(17), d(17), pFill);
            if (clipImg != null) drawThumb(c, l + d(20), cy, sp); else Icons.clip(c, pStroke, rf, l + d(20), cy, u, Theme.fade(th.muted, sp));
            Icons.cross(c, pStroke, r - d(20), cy, u, Theme.fade(th.muted, sp));
            if (clipDisp == null) {
                String s = clipText.replace('\n', ' ');
                float maxW = r - l - d(80);
                if (pSug.measureText(s) > maxW) { int n = pSug.breakText(s, true, maxW - d(10), null); s = s.substring(0, Math.max(1, n - 1)) + "…"; }
                clipDisp = s;
            }
            txt(c, clipDisp, (l + r) / 2f, cy, pSug, offSug, Theme.fade(th.text, sp));
            return;
        }
        if (undoLabel != null) {
            float tw = pSug.measureText(undoLabel), pw = Math.min(x1 - x0, tw + d(66)), m = (x0 + x1) / 2f, pl = m - pw / 2f, pr = m + pw / 2f;
            pFill.setColor(Theme.fade(th.key, sp));
            c.drawRoundRect(pl, cy - d(17), pr, cy + d(17), d(17), d(17), pFill);
            Icons.undo(c, path, pStroke, pl + d(25), cy, u * 0.95f, Theme.fade(th.accent, sp), undoRedo);
            txt(c, undoLabel, pl + d(41) + tw / 2f, cy, pSug, offSug, Theme.fade(th.text, sp));
            return;
        }
        for (int i = 0; i < 3; i++) {
            String s = sug[i];
            if (s == null || s.isEmpty()) continue;
            float cx = x0 + cell * (i + 0.5f), maxW = cell - d(14);
            if (sugDisp[i] == null) { sugK[i] = Fit.k(pSug, s, maxW); sugDisp[i] = Fit.cut(pSug, s, maxW, sugK[i]); }
            float sz = pSug.getTextSize(), k = sugK[i];
            if (k < 1f) pSug.setTextSize(sz * k);
            txt(c, sugDisp[i], cx, cy + (1f - sp) * d(3), pSug, offSug * k, Theme.fade(th.text, sp));
            pSug.setTextSize(sz);
        }
        if (toolsP >= 0.34f) return;
        pFill.setColor(Theme.fade(th.div, 1f - toolsP * 3f));
        for (int i = 1; i < 3; i++) { float x = x0 + cell * i; c.drawRect(x - d(0.5f), cy - d(10), x + d(0.5f), cy + d(10), pFill); }
    }

    private void drawKey(Canvas c, Key k, float pe, long now) {
        if (pe <= 0.01f) return;
        float u = uu(), cx = (k.l + k.r) / 2f, cy = (k.t + k.b) / 2f;
        int base, down;
        boolean accent = k.type == T_ENTER && numpad == 0 && !(tablet && enterLabel == null);
        if (accent) { base = th.accent; down = Theme.mix(th.accent, th.onAccent, 0.2f); }
        else if (k.type == T_CHAR || k.type == T_SPACE || (!tablet && (k.type == T_GLOBE || k.type == T_LANG)) || k.type == T_MIC || (!tablet && k.type == T_SHIFT && shift != 0)) { base = th.key; down = th.keyDown; }
        else { base = th.spec; down = th.specDown; }
        float dy = (1f - pe) * d(4), sq = Shape.squeeze(round), kw2 = k.r - k.l, kh2 = k.b - k.t;
        float ix = kw2 > kh2 && kw2 <= 1.6f * kh2 ? (kw2 - kh2) * sq / 2f : 0f, iy = kh2 > kw2 ? (kh2 - kw2) * sq / 2f : 0f;
        float fb = ((keyBg || accent) && k.row != 5) ? pe : pe * k.p;
        if (fb > 0.01f) {
            pFill.setColor(Theme.fade(Theme.mix(base, down, k.p), fb));
            c.drawRoundRect(k.l + ix, k.t + dy + iy, k.r - ix, k.b + dy - iy, k.rad, k.rad, pFill);
        }
        int tc = Theme.fade(th.keyText, pe);
        if (dy != 0f) { c.save(); c.translate(0, dy); }
        switch (k.type) {
            case T_CHAR:
                drawLetter(c, pLetter, k, cx, cy, tc, Theme.fade(th.keyMuted, 0.85f * pe));
                break;
            case T_PAGE: txt(c, k.label, cx, cy, pMid, offMid, tc); break;
            case T_PUNCT: txt(c, k.label != null ? k.label : ".", cx, cy, pLetter, offLetter, tc); break;
            case T_SHIFT: Icons.shift(c, path, pFill, pStroke, cx, cy, u, shift, tc); break;
            case T_BACK: Icons.back(c, path, pStroke, cx, cy, u, tc); break;
            case T_GLOBE:
                if (globeMode) Icons.globe(c, pStroke, cx, cy, u * 0.9f, tc); else Icons.smiley(c, pFill, pStroke, rf, cx, cy, u * 0.9f, tc);
                break;
            case T_MIC: Icons.mic(c, pStroke, rf, cx, cy, u, listening ? th.accent : tc); break;
            case T_ENTER:
                if (numpad != 0) Icons.check(c, path, pStroke, cx, cy, u, Theme.fade(th.keyText, pe));
                else Icons.enter(c, path, pStroke, cx, cy, u, Theme.fade(accent ? th.onAccent : th.keyText, pe));
                break;
            case T_LANG: Icons.globe(c, pStroke, cx, cy, u * 0.9f, tc); break;
            case T_TAB: Icons.tab(c, pStroke, cx, cy, u, tc); break;
            case T_CAPS: Icons.caps(c, pStroke, cx, cy, u, shift == 2 ? th.accent : tc); break;
            case T_LEFT: Icons.triangle(c, path, pFill, cx, cy, u, true, tc); break;
            case T_RIGHT: Icons.triangle(c, path, pFill, cx, cy, u, false, tc); break;
            case T_SPACE: {
                if (incoActive) Icons.incognito(c, pStroke, k.l + d(24), cy, u * 0.8f, Theme.fade(th.keyMuted, pe));
                if (!logoOn) { txt(c, lay.name, cx, cy, pMid, offMid, Theme.fade(th.keyMuted, pe)); break; }
                float la = spaceLabelAlpha(now);
                if (la < 1f) Icons.logo(c, path, pStroke, pFill, cx, cy, u, Theme.fade(th.keyMuted, pe * (1f - la)));
                drawSpaceLabel(c, cx, cy, pe, now);
                break;
            }
            default: break;
        }
        if (dy != 0f) c.restore();
    }

    private float spaceLabelAlpha(long now) {
        if (langT == 0) return 0f;
        float t = now - langT;
        return t < 150f ? t / 150f : (t > 850f ? Math.max(0f, 1f - (t - 850f) / 250f) : 1f);
    }

    private void drawSpaceLabel(Canvas c, float cx, float cy, float fa, long now) {
        float a = spaceLabelAlpha(now);
        if (a <= 0f) return;
        txt(c, lay.name, cx, cy, pMid, offMid, Theme.fade(th.keyMuted, fa * a));
    }

    private void drawPopup(Canvas c, Key k, String text, float p) {
        float a = Math.min(1f, p * 1.6f), sc = 0.88f + 0.12f * p, cx = (k.l + k.r) / 2f;
        float bw = Math.max(k.r - k.l + d(16), d(48)), bh = d(52), rr = d(14);
        float l = Math.max(d(2), Math.min(cx - bw / 2f, W - d(2) - bw)), b = k.t - d(6), t = b - bh;
        if (t < d(2)) { bh = Math.max(b - d(2), d(30)); t = Math.max(d(2), b - bh); b = t + bh; }
        c.save(); c.scale(sc, sc, cx, k.t);
        pFill.setColor(Theme.fade(Color.argb(60, 0, 0, 0), a)); c.drawRoundRect(l, t + d(2), l + bw, b + d(2), rr, rr, pFill);
        pFill.setColor(Theme.fade(th.pop, a)); c.drawRoundRect(l, t, l + bw, b, rr, rr, pFill);
        txt(c, text, l + bw / 2f, (t + b) / 2f, pPop, offPop, Theme.fade(th.keyText, a));
        c.restore();
    }

    private void drawMenu(Canvas c) {
        final boolean open = menuOn;
        float bp = MenuFx.body(menuP, open), sc = 0.5f + 0.5f * bp, a = Math.min(1f, menuP * 2.4f), rr = d(14);
        float px = Math.max(menuL, Math.min(menuR, menuAx));
        c.save(); c.scale(sc, sc, px, menuB + d(8));
        pFill.setColor(Theme.fade(Color.argb(60, 0, 0, 0), a)); c.drawRoundRect(menuL, menuT + d(1 + 2 * bp), menuR, menuB + d(1 + 2 * bp), rr, rr, pFill);
        pFill.setColor(Theme.fade(th.pop, a)); c.drawRoundRect(menuL, menuT, menuR, menuB, rr, rr, pFill);
        if (menuSel >= 0 && menuSel < menuItems.length) {
            pFill.setColor(Theme.fade(th.accent, a * Math.min(1f, MenuFx.item(menuP, open, 0) * 2f)));
            c.drawCircle(fx.hx, fx.hy, (Math.min(menuCell, menuCellH) / 2f - d(3)) * fx.hlScale(), pFill);
        }
        for (int j = 0; j < menuItems.length; j++) {
            float ip = MenuFx.item(menuP, open, j % menuCols + j / menuCols);
            if (ip <= 0f) continue;
            float cx = menuR - (j % menuCols + 0.5f) * menuCell, cy = menuB - (j / menuCols + 0.5f) * menuCellH;
            boolean on = j == menuSel;
            float gs = (0.35f + 0.65f * Anim.back(ip)) * (on ? 1.12f : 1f), ga = a * Math.min(1f, ip * 1.8f);
            c.save(); c.scale(gs, gs, cx, cy);
            txt(c, menuItems[j], cx, cy, pLetter, offLetter, Theme.fade(on ? th.onAccent : th.keyText, ga));
            c.restore();
        }
        c.restore();
    }

    private void drawTrail(Canvas c, long now) {
        if (!Trail.ribbon(trail, trX, trY, trT, gn, now, d(10))) return;
        pFill.setColor(Theme.fade(th.accent, 0.92f));
        c.drawPath(trail, pFill);
        c.drawCircle(trX[gn - 1], trY[gn - 1], d(5) * (1f - (now - trT[gn - 1]) / (float) Trail.LIFE), pFill);
    }

    private float sx0() { return menuSwap ? d(58) : d(56); }

    private float sx1() { return menuSwap ? W - d(56) : W - d(58); }

    private float stripBottom() { return showWords ? d(STRIP) : 0f; }

    private Key hit(float x, float y) {
        float hx = d(GAP) / 2f, hy = d(4), bd = Float.MAX_VALUE;
        Key best = null;
        for (Key k : keys) {
            if (x >= k.l - hx && x < k.r + hx && y >= k.t - hy && y < k.b + hy) return k;
            float dx = x < k.l ? k.l - x : (x > k.r ? x - k.r : 0), dy = y < k.t ? k.t - y : (y > k.b ? y - k.b : 0);
            float dd = dx * dx + dy * dy;
            if (dd < bd) { bd = dd; best = k; }
        }
        return (numpad != 0 && tablet) || (split && Math.abs(x - W / 2f) < Split.GAP * (W - 2 * d(PAD)) / 2f) ? null : best;
    }

    @SuppressWarnings("deprecation")
    private void invKey(Key k) { invalidate((int) (k.l - d(10)), (int) (k.t - d(66)), (int) (k.r + d(10)), (int) (k.b + d(6))); }

    void down(int id, float x, float y) {
        if (gesturing) return;
        int s = -1, active = 0;
        for (int i = 0; i < MAXP; i++) { if (used[i]) active++; else if (s < 0) s = i; }
        if (s < 0) return;
        used[s] = true; pid[s] = id; fired[s] = false; pk[s] = null; psug[s] = -1; downX[s] = x; downY[s] = y; cursorMode[s] = false;
        if (y < stripBottom()) { psug[s] = zone(x); invalidate(); return; }
        Key k = hit(x, y);
        press(s, k);
        if (swipeOn && swipe != null && page == 0 && active == 0 && k != null && k.type == T_CHAR && k.row >= 1) {
            gSlot = s; gKey = k; gsx = x; gsy = y; gn = 0; addPt(x, y);
        }
    }

    private int zone(float x) {
        if (menuSwap ? x > W - d(54) : x < d(54)) return 10;
        if (menuSwap ? x < d(62) : x > W - d(62)) return 11;
        if (!showWords) return -1;
        if (toolsOpen) { int h = MenuBar.hit(x, sx0(), sx1()); return h < 0 ? -1 : 20 + h; }
        float x0 = sx0(), cell = (sx1() - x0) / 3f;
        return Math.max(0, Math.min(2, (int) ((x - x0) / cell)));
    }

    private void press(int s, Key k) {
        pk[s] = k;
        if (k == null) return;
        k.down = true; pressT[s] = SystemClock.uptimeMillis();
        buzz(HapticFeedbackConstants.KEYBOARD_TAP);
        if (lis != null) lis.onKeySound(soundKind(k));
        if (k.type == T_BACK) {
            if (lis != null) lis.onBack();
            repSlot = s; repCount = 0; h.removeCallbacks(rep); h.postDelayed(rep, 400);
        } else if (k.alt != null || k.type == T_GLOBE || (k.type == T_SPACE && incoAllowed)) h.postDelayed(lp[s], k.type == T_SPACE ? 600 : 350);
        invKey(k);
    }

    private static int soundKind(Key k) { return k.type == T_BACK ? KeySounds.DELETE : (k.type == T_CHAR || k.type == T_PUNCT) ? KeySounds.KEY : KeySounds.MOD; }

    void release(int s) {
        cursorMode[s] = false;
        if (clearSlot == s) { clearSlot = -1; h.removeCallbacks(clearRun); }
        Key k = pk[s];
        if (k != null) {
            k.down = false; h.removeCallbacks(lp[s]);
            if (repSlot == s) { repSlot = -1; h.removeCallbacks(rep); }
            invKey(k);
        }
        pk[s] = null;
    }

    private void longPress(int s) {
        Key k = pk[s];
        if (k == null || !used[s] || lis == null) return;
        if ((k.type == T_PUNCT && (k.label == null || ".".equals(k.label))) || (k.type == T_CHAR && ".".equals(k.label))) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS);
            openMenu(s, k, PUNCT_ITEMS, true);
        } else if (k.type == T_SPACE) {
            if (incoAllowed && !fired[s] && !cursorMode[s]) { fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onIncognito(); }
        } else if (k.type == T_GLOBE) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onEmojiKey(true);
        } else if (k.alt != null) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); openMenu(s, k, k.alt.split("\\|"), false);
        }
    }

    void move(int id, float x, float y) {
        int s = slotOf(id);
        if (s < 0) return;
        if (menuOn && s == menuSlot) { int sel = menuSelAt(x, y); if (sel != menuSel) { menuSel = sel; invalidate(); } return; }
        if (cursorMode[s]) { cursorStep(s, x, y); return; }
        Key cur = pk[s];
        if (cur != null && cur.type == T_SPACE && spaceLang) { spaceLangMove(s, x, y); return; }
        if (cur != null && !fired[s] && cur.type == T_SPACE && spaceCursor && (Math.abs(x - downX[s]) > d(14) || Math.abs(y - downY[s]) > d(22))) {
            cursorMode[s] = true; fired[s] = true; lastCx[s] = downX[s]; lastCy[s] = downY[s]; buzz(HapticFeedbackConstants.KEYBOARD_TAP); cursorStep(s, x, y); return;
        }
        if (cur != null && cur.type == T_BACK && clearAll && !fired[s]) {
            if (y < cur.t) {
                if (y < cur.t - d(24)) {
                    if (clearSlot != s) {
                        clearSlot = s; clearStart = SystemClock.uptimeMillis();
                        h.removeCallbacks(rep); repSlot = -1;
                        h.postDelayed(clearRun, clearHold); invalidate();
                    }
                } else if (clearSlot == s) { clearSlot = -1; h.removeCallbacks(clearRun); invalidate(); }
                return;
            } else if (clearSlot == s) { clearSlot = -1; h.removeCallbacks(clearRun); invalidate(); }
        }
        if (s == gSlot) {
            addPt(x, y);
            if (!gesturing && Anim.sq(x - gsx) + Anim.sq(y - gsy) > Anim.sq(d(26)) && hit(x, y) != gKey) {
                gesturing = true; fired[s] = true; trailA = 1f; release(s); buzz(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            if (gesturing) { invalidate(); return; }
        }
        if (psug[s] >= 0 || fired[s]) return;
        Key nk = y < stripBottom() ? null : hit(x, y);
        if (nk != null && nk.type == T_BACK && (pk[s] == null || pk[s].type != T_BACK)) nk = null;
        if (nk != pk[s]) { release(s); press(s, nk); }
    }

    void up(int id, float x, float y) {
        int s = slotOf(id);
        if (s < 0) return;
        if (menuOn && s == menuSlot) {
            if (menuSel >= 0 && lis != null) lis.onChar(menuItems[menuSel]);
            menuOn = false; menuSlot = -1; release(s); used[s] = false; psug[s] = -1; invalidate(); return;
        }
        if (s == gSlot) {
            boolean was = gesturing;
            if (was) addPt(x, y);
            if (was && swipe != null && lis != null) {
                int n = swipe.decode(lay.ru, Lexicon.peek(lay.id), letters, lcx, lcy, kwPitch, trX, trY, gn, gOut);
                if (n > 0) lis.onSwipe(gOut, n);
            }
            gSlot = -1; gesturing = false; gKey = null;
            if (was) { release(s); used[s] = false; psug[s] = -1; invalidate(); return; }
        }
        if (psug[s] >= 0) {
            if (y < stripBottom() && lis != null && zone(x) == psug[s]) {
                int z = psug[s];
                if (z == 10) setTools(!toolsOpen);
                else if (z == 11) { if (micTop()) lis.onMic(); }
                else if (z >= 20) { setTools(false); lis.onMenu(MenuBar.id(z - 20)); }
                else if (clipText != null) lis.onClipTap(x > sx1() - d(46));
                else if (undoLabel != null) lis.onUndo();
                else lis.onSuggest(z);
            }
        } else {
            Key k = pk[s];
            if (k != null && !fired[s]) {
                if (k.type == T_CHAR) recordTouch(x, y, kwPitch);
                commit(k);
                tcN = 0;
            }
        }
        release(s); used[s] = false; psug[s] = -1;
        invalidate();
    }

    private void commit(Key k) {
        if (lis == null) return;
        switch (k.type) {
            case T_CHAR: lis.onChar(k.label); break;
            case T_SHIFT: lis.onShift(); break;
            case T_PAGE: setPage(k.target); break;
            case T_SPACE: lis.onSpace(); break;
            case T_ENTER: lis.onEnter(); break;
            case T_GLOBE: lis.onEmojiKey(false); break;
            case T_LANG: lis.onLang(); break;
            case T_TAB: lis.onSpace(); break;
            case T_CAPS: lis.onCaps(); break;
            case T_LEFT: lis.onCursor(-1); break;
            case T_RIGHT: lis.onCursor(1); break;
            case T_MIC: lis.onMic(); break;
            case T_PUNCT: lis.onChar(k.label != null ? k.label : "."); break;
            default: break;
        }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacksAndMessages(null);
        for (int i = 0; i < MAXP; i++) { pk[i] = null; used[i] = false; }
        repSlot = -1; lastFrame = 0;
    }
}
