package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import java.util.ArrayList;

final class KeyboardView extends BoardView {

    interface Listener {
        void onChar(String s);
        void onBack();
        void onShift();
        void onSpace();
        void onEnter();
        void onEmojiKey(boolean longPress);
        void onMic();
        void onSuggest(int i);
        void onSwipe(String[] words, int n);
        void onClipTap(boolean dismiss);
        void onUndo();
        void onCursor(int dir);
        void onCursorV(int dir);
        void onClearAll();
        void onOpenSettings();
        void onMenu(int id);
        void onIncognito();
        void onKeySound(int kind);
        void onLang();
        void onLangStep(int dir);
        void onSpecial(int keyCode);
        void onCaps();
        void onToolsToggle(boolean open);
        void onBroken();
    }

    private static final float MARGIN = 3, GAP_X = 6, GAP_Y = 10, SUG_ZONE = 46, BOT_ZONE = 54, ROW_H = 38;
    private static final float KEY_R = 5.5f, PANEL_R = 16;
    private static final float BOT_ZONE_OFF = 10;

    private static final int MAXPOP = 8;



    private float zoneTop;

    private long enterStart;

    private final int[] popIdx = new int[MAXP];
    private final Key[] popKey = new Key[MAXPOP];
    private final String[] popTxt = new String[MAXPOP];
    private final float[] popP = new float[MAXPOP];
    private final boolean[] popOn = new boolean[MAXPOP];



    private final Paint pPanel = new Paint(Paint.ANTI_ALIAS_FLAG);

    KeyboardView(Context c, Theme theme) {
        super(c);
        th = theme;
        den = c.getResources().getDisplayMetrics().density;
        for (int i = 0; i < MAXP; i++) {
            final int s = i; psug[i] = -1; popIdx[i] = -1;
            lp[i] = new Runnable() { @Override public void run() { longPress(s); } };
        }
        pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeWidth(d(1.6f));
        pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        pTrail.setStyle(Paint.Style.STROKE); pTrail.setStrokeWidth(d(5));
        pTrail.setStrokeCap(Paint.Cap.ROUND); pTrail.setStrokeJoin(Paint.Join.ROUND);
        setup(pLetter, 21); setup(pMid, 15); setup(pSug, 16); setup(pPop, 32);
        offLetter = off(pLetter); offMid = off(pMid); offSug = off(pSug); offPop = off(pPop);
    }



    private float hsEff() { return hs; }

    @Override public void setLogo(boolean on) { }

    @Override float letterPt() { return 21f; }

    @Override float edge() { return d(MARGIN); }

    @Override float stripBottomY() { return sugBoundary(); }

    @Override int stripZoneAt(float x) { return sugIndex(x); }

    @Override Key hitAt(float x, float y) { return hitAny(x, y); }

    @Override Key hitKey(float x, float y) { return hit(x, y); }

    @Override boolean letterRow(Key k) { return k.row >= 0 && k.row <= 2; }

    @Override int hintRow() { return 0; }

    @Override void onEntrance() { enterStart = SystemClock.uptimeMillis(); invalidate(); }

    @Override float chipTapX() { return sx1() - d(4) - d(46); }

    @Override boolean blockSlide(Key nk, Key cur) { return (nk.type == T_BACK || nk.type == T_EMOJI || nk.type == T_MIC) && (cur == null || cur.type != nk.type); }

    @Override void onKeyPressed(int s, Key k) {
        if (k.type == T_CHAR && (k.row >= 0 || tablet)) popIdx[s] = newPop(k, (shift != 0 && page == 0) ? k.upper : k.label);
    }

    @Override void onKeyReleased(int s) {
        if (popIdx[s] >= 0) { popOn[popIdx[s]] = false; popIdx[s] = -1; }
    }

    @Override void onKeyLong(int s, Key k) {
        if ((k.alt != null || k.type == T_PUNCT) && popIdx[s] >= 0) { popOn[popIdx[s]] = false; popIdx[s] = -1; }
    }

    float sx1() { return W - d(MARGIN) - (menuSwap ? d(34) : (micTop() ? d(34) : 0f)); }
    public void setRound(int v) { if (round != v) { round = v; invalidate(); } }
    public int[] strip() { return new int[]{(int) sx0(), (int) (W - sx1()), (int) (d(SUG_ZONE) - d(GAP_Y) / 2f)}; }
    private float keyR(Key k) {
        float base = numpad != 0 ? (k.b - k.t) * 0.5f : d(KEY_R);
        return Shape.radius(base, Math.min(k.r - k.l, k.b - k.t) * 0.5f, round);
    }

    public void themeChanged() { makeShader(); invalidate(); }

    private void makeShader() {
        if (H == 0) return;
        pPanel.setShader(new LinearGradient(0, 0, 0, H, th.panelTop, th.panelBot, Shader.TileMode.CLAMP));
    }

    void build() {
        if (W == 0) return;
        ArrayList<Key> L = new ArrayList<>(40);
        float m = d(MARGIN), gx = d(GAP_X), gy = d(GAP_Y), full = W - 2 * m;
        float top = topH(), bz = d((bb() ? BOT_ZONE : BOT_ZONE_OFF) + botPad);
        float rh = (H - top - bz - 3 * gy) / 4f;
        float y0 = top, y1 = top + (rh + gy), y2 = top + 2 * (rh + gy), y3 = top + 3 * (rh + gy);
        zoneTop = bb() ? y3 + rh + gy / 2f : Float.MAX_VALUE;
        int npm = numpad;
        if (npm != 0) {
            KeyPlan np = KeyPlan.numpad(npm);
            float gp = d(10), nw = tablet ? Math.min(full, np.total * 1.45f * rh + (np.total - 1f) * gp) : full;
            buildFromPlan(L, np, (W - nw) / 2f, nw, gp, new float[]{y0, y1, y2, y3}, rh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }
        if (page != 0 && !tablet) {
            KeyPlan sp = KeyPlan.symbols(lay, page, numRow);
            if (numRow) { sp.rows.remove(0); addDigits(L, m, full); }
            buildFromPlan(L, sp, m, full, gx, new float[]{y0, y1, y2, y3}, rh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }
        if (tablet) {
            KeyPlan plan = KeyPlan.tablet(lay, numRow, page);
            int n = plan.rows.size();
            float areaTop = d(showWords ? SUG_ZONE : 8), rhh = (H - bz - areaTop - (n - 1) * gy) / n;
            float[] ys = new float[n];
            for (int q = 0; q < n; q++) ys[q] = areaTop + q * (rhh + gy);
            buildFromPlan(L, plan, m, full, gx, ys, rhh, numRow ? -1 : 0);
            finishKeys(L, gx);
            return;
        }

        if (numRow && page == 0) addDigits(L, m, full);

        if (page == 0) {
            final String r1 = lay.r1, r2 = lay.r2, r3 = lay.r3;
            final int cols = lay.cols;
            float kw = (full - (cols - 1) * gx) / cols;
            float w1 = r1.length() * kw + (r1.length() - 1) * gx;
            row(L, r1, r1.length() == cols ? m : (W - w1) / 2f, y0, kw, rh, gx, true, 0);
            float w2 = r2.length() * kw + (r2.length() - 1) * gx;
            row(L, r2, (W - w2) / 2f, y1, kw, rh, gx, true, 1);
            float w3 = r3.length() * kw + (r3.length() - 1) * gx, rem = full - w3;
            float sw = Math.min(1.3f * kw, (rem - 2 * gx) / 2f), side = (rem - 2 * sw) / 2f;
            add(L, new Key(T_SHIFT, null, null, 0, 2), m, y2, sw, rh);
            row(L, r3, m + sw + side, y2, kw, rh, gx, true, 2);
            add(L, new Key(T_BACK, null, null, 0, 2), W - m - sw, y2, sw, rh);
        }

        float c123 = 0, cEn = 0;
        if (bottomBar) {
            float avail = full - 3 * gx, tot = 118f + 232f + 62f + 94f, x = m;
            float w123 = avail * 118f / tot, wsp = avail * 232f / tot, wpu = avail * 62f / tot, wen = avail * 94f / tot;
            add(L, new Key(T_PAGE, page == 0 ? "123" : "ABC", null, page == 0 ? 1 : 0, 3), x, y3, w123, rh);
            c123 = x + w123 / 2f; x += w123 + gx;
            add(L, new Key(T_SPACE, null, null, 0, 3), x, y3, wsp, rh); x += wsp + gx;
            add(L, new Key(T_PUNCT, ".", PUNCT_MENU, 0, 3), x, y3, wpu, rh); x += wpu + gx;
            add(L, new Key(T_ENTER, null, null, 0, 3), x, y3, wen, rh);
            cEn = x + wen / 2f;
            float it = y3 + rh + d(4), ib = H - d(2), hw = d(28);
            float cA = swapIcons ? cEn : c123, cB = swapIcons ? c123 : cEn;
            if (langKey) {
                float h2 = d(23), off = d(25);
                add(L, new Key(T_EMOJI, null, null, 0, 4), cA - off - h2, it, 2 * h2, ib - it);
                add(L, new Key(T_LANG, null, null, 0, 4), cA + off - h2, it, 2 * h2, ib - it);
            } else add(L, new Key(T_EMOJI, null, null, 0, 4), cA - hw, it, 2 * hw, ib - it);
            if (micOn && (micPos != 0 || !showWords)) add(L, new Key(T_MIC, null, null, 0, 4), cB - hw, it, 2 * hw, ib - it);
        } else {
            float avail = full - (langKey ? 5 : 4) * gx, tot = 100f + 62f + 190f + 56f + 86f, x = m;
            float w123 = avail * 100f / tot, wem = avail * 62f / tot, wsp = avail * (190f - (langKey ? 62f : 0f)) / tot, wpu = avail * 56f / tot, wen = avail * 86f / tot;
            add(L, new Key(T_PAGE, page == 0 ? "123" : "ABC", null, page == 0 ? 1 : 0, 3), x, y3, w123, rh); x += w123 + gx;
            add(L, new Key(T_EMOJI, null, null, 0, 3), x, y3, wem, rh); x += wem + gx;
            if (langKey) { add(L, new Key(T_LANG, null, null, 0, 3), x, y3, wem, rh); x += wem + gx; }
            add(L, new Key(T_SPACE, null, null, 0, 3), x, y3, wsp, rh); x += wsp + gx;
            add(L, new Key(T_PUNCT, ".", PUNCT_MENU, 0, 3), x, y3, wpu, rh); x += wpu + gx;
            add(L, new Key(T_ENTER, null, null, 0, 3), x, y3, wen, rh);
        }

        finishKeys(L, gx);
    }




    private void buildFromPlan(ArrayList<Key> L, KeyPlan plan, float x0, float full, float gx, float[] ys, float rh, int rowOff) {
        final float c = (full - (plan.total - 1f) * gx) / plan.total;
        for (int r = 0; r < plan.rows.size(); r++) {
            float x = x0;
            for (KeyPlan.Spec sp : plan.rows.get(r)) {
                float w = sp.w * c + (sp.w - 1f) * gx;
                Key k = new Key(vt(sp.kind), sp.label, ".".equals(sp.label) ? android.text.TextUtils.join("|", PUNCT_ITEMS) : (sp.letter ? lay.alt(sp.label.charAt(0)) : null), sp.target, r + rowOff);
                if (sp.letter) k.upper = lay.upper(k.label);
                add(L, k, x, ys[r], w, rh);
                x += w + gx;
            }
        }
    }

    private void addDigits(ArrayList<Key> L, float m, float full) {
        float g5 = d(5), kwn = (full - 9 * g5) / 10f;
        for (int i = 0; i < 10; i++) add(L, new Key(T_CHAR, String.valueOf((i + 1) % 10), null, 0, -1), m + i * (kwn + g5), (showWords ? d(SUG_ZONE) : 0f) + d(5), kwn, d(36));
    }

    private void row(ArrayList<Key> L, String chars, float x0, float y, float kw, float rh, float gx, boolean letters, int rowIdx) {
        for (int i = 0; i < chars.length(); i++) {
            char ch = chars.charAt(i);
            String alt = letters ? lay.alt(ch) : null;
            Key k = new Key(T_CHAR, String.valueOf(ch), alt, 0, rowIdx);
            if (letters) k.upper = lay.upper(k.label);
            add(L, k, x0 + i * (kw + gx), y, kw, rh);
        }
    }

    private float topH() {
        float z = d(SUG_ZONE);
        if (showWords && numRow) return 2 * z;
        return (showWords || numRow) ? z : d(8);
    }

    @Override protected void onMeasure(int ws, int hSpec) {
        int w = MeasureSpec.getSize(ws);
        if (w == 0) w = getResources().getDisplayMetrics().widthPixels;
        setMeasuredDimension(w, Math.round(topH() + d(4 * ROW_H * hsEff() + 3 * GAP_Y) + d((bb() ? BOT_ZONE : BOT_ZONE_OFF) + botPad)));
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        W = w; H = hh; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; sugFit = false; clipDisp = null; makeShader(); build();
    }


    private boolean tick(long now) {
        float dt = lastFrame == 0 ? 0f : Math.min(48f, now - lastFrame);
        boolean a = false;
        for (Key k : keys) {
            float tg = k.down ? 1f : 0f;
            if (k.p != tg) { k.p = Anim.step(k.p, tg, dt, tg > k.p ? 40f : 160f); a = true; }
        }
        if (pageP < 1f) { pageP = Anim.step(pageP, 1f, dt, 190f); a = true; }
        if (sugP < 1f) { sugP = Anim.step(sugP, 1f, dt, 150f); a = true; }
        float tgT = toolsOpen ? 1f : 0f, tgO = oneHand != 0 ? 1f : 0f, tgS = split ? 1f : 0f;
        if (toolsP != tgT) { toolsP = Anim.step(toolsP, tgT, dt, 230f); a = true; }
        if (onP != tgO) { onP = Anim.step(onP, tgO, dt, 220f); a = true; }
        if (spP != tgS) { spP = Anim.step(spP, tgS, dt, 220f); a = true; }
        for (int i = 0; i < MAXPOP; i++) {
            if (popKey[i] == null) continue;
            a = true;
            if (popOn[i]) popP[i] = Anim.step(popP[i], 1f, dt, 70f);
            else { popP[i] = Anim.step(popP[i], 0f, dt, 110f); if (popP[i] <= 0f) popKey[i] = null; }
        }
        if (menuOn) { menuP = Anim.step(menuP, 1f, dt, MenuFx.OPEN); a = true; }
        else if (menuP > 0f) { menuP = Anim.step(menuP, 0f, dt, MenuFx.CLOSE); a = true; }
        if (menuP > 0f && menuItems != null && menuSel >= 0 && menuSel < menuItems.length)
            a |= fx.step(dt, menuOn, menuSel, menuR - (menuSel % menuCols + 0.5f) * menuCell, menuB - (menuSel / menuCols + 0.5f) * menuCellH);
        else a |= fx.step(dt, menuOn, -1, 0f, 0f);
        if (clearSlot >= 0) a = true; else if (listening) slowTick = true;
        if (lineOn && Trail.live(trT, gn, SystemClock.uptimeMillis())) a = true;
        if (enterStart != 0) { if (now - enterStart < 220 + 5 * 55) a = true; else enterStart = 0; }
        if (langT != 0) { if (now - langT < 1250) a = true; else langT = 0; }
        if (shiftT != 0) { if (now - shiftT < 260) a = true; else shiftT = 0; }
        lastFrame = a ? now : 0;
        return a;
    }

    private float rowFactor(int row, long now) {
        if (enterStart == 0) return 1f;
        float e = (now - enterStart) - row * 55f;
        if (e <= 0f) return 0f;
        if (e >= 220f) return 1f;
        return Anim.ease(e / 220f);
    }

    void draw0(Canvas c) {
        long now = SystemClock.uptimeMillis();
        slowTick = false;
        boolean anim = tick(now);
        float rad = d(PANEL_R);
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pPanel);

        if (showWords) drawSuggestions(c);

        float pe = Anim.ease(pageP);
        boolean sa = Math.abs(spP - (split ? 1f : 0f)) > 0.002f && numpad == 0;
        if (sa) splitFrame(true);
        for (Key k : keys) {
            float rf2 = rowFactor(k.row, now);
            float fa = pe * rf2;
            if (fa <= 0.01f) continue;
            float dy = (1f - pe) * d(5) + (1f - rf2) * d(16);
            drawKey(c, k, fa, dy, now);
        }
        if (sa) splitFrame(false);

        if (popupOn) for (int i = 0; i < MAXPOP; i++) if (popKey[i] != null) drawPopup(c, popKey[i], popTxt[i], popP[i]);

        if (menuP > 0f) drawMenu(c);
        if (clearSlot >= 0) drawClearArc(c, now);

        long trNow = SystemClock.uptimeMillis();
        if (lineOn && Trail.live(trT, gn, trNow)) drawTrail(c, trNow);
        if (anim) postInvalidateOnAnimation(); else if (slowTick) invalidateSlow();
    }

    private void drawTrail(Canvas c, long now) {
        if (!Trail.ribbon(trail, trX, trY, trT, gn, now, d(10))) return;
        pFill.setColor(Theme.fade(Color.argb(235, 0, 122, 255), 0.92f));
        c.drawPath(trail, pFill);
        c.drawCircle(trX[gn - 1], trY[gn - 1], d(5) * (1f - (now - trT[gn - 1]) / (float) Trail.LIFE), pFill);
    }

    private void drawUndo(Canvas c) {
        float l = sx0(), r = sx1() - d(4), t = d(7), b = d(39), sp = Anim.ease(sugP) * (1f - toolsP), cy = (t + b) / 2f + (1f - sp) * d(4);
        float tw = pSug.measureText(undoLabel), pw = Math.min(r - l, tw + d(64)), m = (l + r) / 2f, pl = m - pw / 2f, pr = m + pw / 2f;
        pFill.setColor(Theme.fade(th.key, 0.95f * sp));
        c.drawRoundRect(pl, t + (1f - sp) * d(4), pr, b + (1f - sp) * d(4), d(16), d(16), pFill);
        Icons.undo(c, path, pStroke, pl + d(24), cy, den * 0.9f, Theme.fade(th.accent, sp), undoRedo);
        txt(c, undoLabel, pl + d(40) + tw / 2f, cy, pSug, offSug, Theme.fade(th.text, sp));
    }

    private void drawChip(Canvas c) {
        float l = sx0(), r = sx1() - d(4), t = d(7), b = d(39), sp = Anim.ease(sugP) * (1f - toolsP), cy = (t + b) / 2f;
        pFill.setColor(Theme.fade(th.key, 0.95f * sp));
        c.drawRoundRect(l, t + (1f - sp) * d(4), r, b + (1f - sp) * d(4), d(16), d(16), pFill);
        if (clipImg != null) drawThumb(c, l + d(20), cy, sp); else Icons.clip(c, pStroke, rf, l + d(20), cy, den, Theme.fade(th.muted, sp));
        Icons.cross(c, pStroke, r - d(22), cy, den, Theme.fade(th.muted, sp));
        if (clipDisp == null) {
            String s = clipText.replace('\n', ' ');
            float maxW = r - l - d(86);
            if (pSug.measureText(s) > maxW) {
                int n = pSug.breakText(s, true, maxW - d(10), null);
                s = s.substring(0, Math.max(1, n - 1)) + "…";
            }
            clipDisp = s;
        }
        txt(c, clipDisp, (l + r) / 2f, cy + (1f - sp) * d(4), pSug, offSug, Theme.fade(th.text, sp));
    }

    private void drawMenu(Canvas c) {
        final boolean open = menuOn;
        float bp = MenuFx.body(menuP, open), sc = 0.5f + 0.5f * bp, a = Math.min(1f, menuP * 2.4f), rr = d(12);
        float px = Math.max(menuL, Math.min(menuR, menuAx));
        c.save(); c.scale(sc, sc, px, menuB + d(8));
        pFill.setColor(Theme.fade(Color.argb(60, 20, 25, 50), a));
        c.drawRoundRect(menuL, menuT + d(1 + 2 * bp), menuR, menuB + d(1 + 2 * bp), rr, rr, pFill);
        pFill.setColor(Theme.fade(th.pop, a));
        c.drawRoundRect(menuL, menuT, menuR, menuB, rr, rr, pFill);
        if (menuSel >= 0 && menuSel < menuItems.length) {
            pFill.setColor(Theme.fade(Theme.ACCENT, a * Math.min(1f, MenuFx.item(menuP, open, 0) * 2f)));
            c.drawCircle(fx.hx, fx.hy, (Math.min(menuCell, menuCellH) / 2f - d(3)) * fx.hlScale(), pFill);
        }
        for (int j = 0; j < menuItems.length; j++) {
            float ip = MenuFx.item(menuP, open, j % menuCols + j / menuCols);
            if (ip <= 0f) continue;
            float cx = menuR - (j % menuCols + 0.5f) * menuCell, cy = menuB - (j / menuCols + 0.5f) * menuCellH;
            boolean on = j == menuSel;
            float gs = (0.35f + 0.65f * Anim.back(ip)) * (on ? 1.12f : 1f), ga = a * Math.min(1f, ip * 1.8f);
            c.save(); c.scale(gs, gs, cx, cy);
            txt(c, menuItems[j], cx, cy, pLetter, offLetter, Theme.fade(on ? Color.WHITE : th.keyText, ga));
            c.restore();
        }
        c.restore();
    }

    private float sx0() { return d(MARGIN) + (menuSwap ? (micTop() ? d(34) : 0f) : d(34)); }

    private void drawSuggestions(Canvas c) {
        float bx = menuSwap ? W - d(MARGIN) - d(17) : d(MARGIN) + d(17), by = d(22);
        if (toolsP < 1f) Icons.apps(c, pFill, rf, bx, by, den * 0.85f, Theme.fade(th.muted, 1f - toolsP));
        if (toolsP > 0f) {
            Icons.chevron(c, path, pStroke, bx, by, den, Theme.fade(th.muted, toolsP));
            MenuBar.draw(c, pStroke, pFill, rf, sx0(), sx1() - d(6), by, den, th.text, th.accent, onP, spP, toolsP, menuPressed());
        }
        if (micTop()) {
            float mx = menuSwap ? d(MARGIN) + d(17) : W - d(MARGIN) - d(17);
            if (listening) {
                float ph = (SystemClock.uptimeMillis() % 1100) / 1100f;
                pFill.setColor(Theme.fade(th.accent, 0.30f * (1f - ph))); c.drawCircle(mx, by, d(15) + d(8) * ph, pFill);
                setMicBox(mx, by, d(24));
            }
            Icons.mic(c, pStroke, rf, mx, by, den * 0.9f, listening ? th.accent : th.muted);
        }
        if (toolsP >= 1f || stripHidden) return;
        if (clipText != null) { drawChip(c); return; }
        if (undoLabel != null) { drawUndo(c); return; }
        float m = sx0(), cy = d(22), sp = Anim.ease(sugP) * (1f - toolsP);
        fitSug(pSug, m, sx1(), d(14));
        for (int i = 0; i < 3; i++) {
            String s = sug[i];
            if (s == null || s.isEmpty()) continue;
            float cx = (sugE[i] + sugE[i + 1]) / 2f;
            float sz = pSug.getTextSize(), k = sugK[i];
            if (k < 1f) pSug.setTextSize(sz * k);
            txt(c, sugDisp[i], cx, cy + (1f - sp) * d(4), pSug, offSug * k, Theme.fade(th.text, sp));
            pSug.setTextSize(sz);
        }
        if (toolsP < 0.34f) {
            pFill.setColor(Theme.fade(th.div, 1f - toolsP * 3f));
            for (int i = 1; i < 3; i++) {
                float x = sugE[i];
                c.drawRect(x - d(0.5f), cy - d(9), x + d(0.5f), cy + d(9), pFill);
            }
        }
    }

    private void drawKey(Canvas c, Key k, float fa, float dy, long now) {
        float rk = keyR(k), u = uu();
        boolean saved = dy > 0.5f;
        if (saved) { c.save(); c.translate(0, dy); }
        float cx = (k.l + k.r) / 2f, cy = (k.t + k.b) / 2f;

        if (k.row == 4) {
            if (k.p > 0f) {
                pFill.setColor(Theme.fade(th.text, 0.10f * k.p * fa));
                c.drawCircle(cx, cy, d(21) * (0.85f + 0.15f * k.p), pFill);
            }
            int col = Theme.fade(th.text, fa);
            float s = 1f - 0.08f * k.p;
            c.save(); c.scale(s, s, cx, cy);
            if (k.type == T_MIC) {
                if (listening) {
                    float ph = (now % 1100) / 1100f;
                    pStroke.setColor(Theme.fade(th.accent, 1f - ph)); c.drawCircle(cx, cy, d(13) + d(12) * ph, pStroke);
                    setMicBox(cx, cy, d(27));
                }
                Icons.mic(c, pStroke, rf, cx, cy, u, listening ? th.accent : col);
            }
            else if (k.type == T_LANG || globeMode) Icons.globe(c, pStroke, cx, cy, u, col);
            else Icons.smiley(c, pFill, pStroke, rf, cx, cy, u, col);
            c.restore();
            if (saved) c.restore();
            return;
        }

        int base, downC;
        if (k.type == T_ENTER && enterLabel != null && numpad == 0) { base = Theme.ACCENT; downC = Theme.ACCENT_DOWN; }
        else if (k.type == T_CHAR || (!tablet && k.type == T_SHIFT && shift != 0)) { base = th.key; downC = th.keyDown; }
        else if (k.type == T_SPACE) { base = th.space; downC = th.keyDown; }
        else { base = th.spec; downC = th.specDown; }

        float sq = Shape.squeeze(round), kw2 = k.r - k.l, kh2 = k.b - k.t;
        float ix = kw2 > kh2 && kw2 <= 1.6f * kh2 ? (kw2 - kh2) * sq / 2f : 0f, iy = kh2 > kw2 ? (kh2 - kw2) * sq / 2f : 0f;
        float fb = (keyBg || (k.type == T_ENTER && enterLabel != null && numpad == 0)) ? fa : fa * k.p;
        if (fb > 0.01f) {
            pFill.setColor(Theme.fade(th.shadow, fb));
            c.drawRoundRect(k.l + ix, k.t + d(1) + iy, k.r - ix, k.b + d(1) - iy, rk, rk, pFill);
            pFill.setColor(Theme.fade(Theme.mix(base, downC, k.p), fb));
            c.drawRoundRect(k.l + ix, k.t + iy, k.r - ix, k.b - iy, rk, rk, pFill);
        }

        int tc = Theme.fade(th.keyText, fa);
        switch (k.type) {
            case T_CHAR:
                drawLetter(c, pLetter, k, cx, cy, tc, Theme.fade(th.keyMuted, 0.85f * fa));
                break;
            case T_PAGE:
                txt(c, k.label, cx, cy, pMid, offMid, tc); break;
            case T_PUNCT:
                txt(c, k.label != null ? k.label : ".", cx, cy, pLetter, offLetter, tc); break;
            case T_EMOJI:
                if (globeMode) Icons.globe(c, pStroke, cx, cy, u, tc); else Icons.smiley(c, pFill, pStroke, rf, cx, cy, u, tc);
                break;
            case T_LANG: Icons.globe(c, pStroke, cx, cy, u, tc); break;
            case T_TAB: Icons.tab(c, pStroke, cx, cy, u, tc); break;
            case T_CAPS: Icons.caps(c, pStroke, cx, cy, u, shift == 2 ? th.accent : tc); break;
            case T_LEFT: Icons.triangle(c, path, pFill, cx, cy, u, true, tc); break;
            case T_RIGHT: Icons.triangle(c, path, pFill, cx, cy, u, false, tc); break;
            case T_SPACE:
                if (incoActive) Icons.incognito(c, pStroke, k.l + d(24), cy, u * 0.8f, Theme.fade(th.keyMuted, fa));
                drawSpaceLabel(c, cx, cy, fa, now); break;
            case T_SHIFT: {
                float sc = 1f;
                if (shiftT != 0) { float p = Math.min(1f, (now - shiftT) / 260f); sc = 1f + 0.22f * (float) Math.sin(Math.PI * p); }
                c.save(); c.scale(sc, sc, cx, cy);
                Icons.shift(c, path, pFill, pStroke, cx, cy, u, shift, tc);
                c.restore();
                break;
            }
            case T_BACK: Icons.back(c, path, pStroke, cx, cy, u, tc); break;
            case T_ENTER:
                if (numpad != 0) Icons.check(c, path, pStroke, cx, cy, u, tc);
                else if (enterLabel != null) txt(c, enterLabel, cx, cy, pMid, offMid, Theme.fade(Color.WHITE, fa));
                else Icons.enter(c, path, pStroke, cx, cy, u, tc);
                break;
            default: break;
        }
        if (saved) c.restore();
    }

    private void drawSpaceLabel(Canvas c, float cx, float cy, float fa, long now) {
        String cur = lay.space, prev = prevLay.space, name = lay.name;
        float t = langT == 0 ? 99999f : now - langT;
        int mc = th.keyMuted;
        if (t >= 1250f) { txt(c, cur, cx, cy, pMid, offMid, Theme.fade(mc, fa)); return; }
        if (t < 160f) {
            float f = t / 160f;
            txt(c, prev, cx, cy, pMid, offMid, Theme.fade(mc, fa * (1f - f)));
            txt(c, name, cx, cy, pMid, offMid, Theme.fade(th.keyText, fa * f));
        } else if (t < 1000f) {
            txt(c, name, cx, cy, pMid, offMid, Theme.fade(th.keyText, fa));
        } else {
            float f = (t - 1000f) / 250f;
            txt(c, name, cx, cy, pMid, offMid, Theme.fade(th.keyText, fa * (1f - f)));
            txt(c, cur, cx, cy, pMid, offMid, Theme.fade(mc, fa * f));
        }
    }

    private void drawPopup(Canvas c, Key k, String text, float p) {
        if (text == null || p <= 0f) return;
        float rk = d(KEY_R), a = Math.min(1f, p * 1.6f), sc = 0.9f + 0.1f * Anim.ease(p);
        float bw = Math.max(k.r - k.l + d(16), d(46)), cx = (k.l + k.r) / 2f;
        float l = Math.max(d(2), Math.min(cx - bw / 2f, W - d(2) - bw)), r = l + bw;
        float t = Math.max(d(2), k.t - d(42)), ym = Math.max(t + d(24), k.t - d(4)), rd = d(10), kl = k.l, kr = k.r, kb = k.b;
        path.reset();
        path.moveTo(l + rd, t);
        path.lineTo(r - rd, t); path.quadTo(r, t, r, t + rd);
        path.lineTo(r, ym);     path.quadTo(r, ym + d(8), kr, ym + d(13));
        path.lineTo(kr, kb - rk); path.quadTo(kr, kb, kr - rk, kb);
        path.lineTo(kl + rk, kb); path.quadTo(kl, kb, kl, kb - rk);
        path.lineTo(kl, ym + d(13)); path.quadTo(l, ym + d(8), l, ym);
        path.lineTo(l, t + rd); path.quadTo(l, t, l + rd, t);
        path.close();
        c.save();
        c.scale(sc, sc, cx, kb);
        c.save(); c.translate(0, d(2));
        pFill.setColor(Theme.fade(Color.argb(60, 20, 25, 50), a)); c.drawPath(path, pFill);
        c.restore();
        pFill.setColor(Theme.fade(th.pop, a)); c.drawPath(path, pFill);
        txt(c, text, (l + r) / 2f, (t + ym) / 2f, pPop, offPop, Theme.fade(th.keyText, a));
        c.restore();
    }

    private float sugBoundary() { return showWords ? d(SUG_ZONE) - d(GAP_Y) / 2f : 0f; }

    private int sugIndex(float x) {
        float x0 = sx0();
        if (x < x0) { if (!menuSwap) return 10; if (micTop()) return 11; }
        if (x > sx1()) { if (menuSwap) return 10; if (micTop()) return 11; }
        if (toolsOpen) { int h = MenuBar.hit(x, x0, sx1() - d(6)); return h < 0 ? -1 : 20 + h; }
        return sugAt(x, x0, sx1());
    }

    private Key hit(float x, float y) {
        float hx = d(GAP_X) / 2f, hy = d(GAP_Y) / 2f, bd = Float.MAX_VALUE;
        Key best = null;
        boolean zone = y >= zoneTop;
        for (Key k : keys) {
            if (k.row < 0 && !tablet) continue;
            boolean icon = k.row == 4;
            if (zone != icon) continue;
            if (zone) { if (x >= k.l && x < k.r) return k; continue; }
            if (x >= k.l - hx && x < k.r + hx && y >= k.t - hy && y < k.b + hy) return k;
            float dx = x < k.l ? k.l - x : (x > k.r ? x - k.r : 0);
            float dy = y < k.t ? k.t - y : (y > k.b ? y - k.b : 0);
            float dd = dx * dx + dy * dy;
            if (dd < bd) { bd = dd; best = k; }
        }
        return zone || (numpad != 0 && tablet) || (split && Math.abs(x - W / 2f) < Split.GAP * (W - 2 * d(MARGIN)) / 2f) ? null : best;
    }

    private Key hitNum(float x) {
        Key best = null;
        float bd = Float.MAX_VALUE;
        for (Key k : keys) {
            if (k.row >= 0) continue;
            float dd = Math.abs(x - (k.l + k.r) / 2f);
            if (dd < bd) { bd = dd; best = k; }
        }
        return best;
    }

    private Key hitAny(float x, float y) {
        float digTop = sugBoundary(), digBot = topH() - d(GAP_Y) / 2f;
        if (numRow && !tablet && y >= digTop && y < digBot) return hitNum(x);
        if (showWords && y < digTop) return null;
        return hit(x, y);
    }

    @SuppressWarnings("deprecation")
    void invKey(Key k) {
        invalidate((int) (k.l - d(10)), (int) (k.t - d(56)), (int) (k.r + d(10)), (int) (k.b + d(6)));
    }


    private int newPop(Key k, String t) {
        int f = -1;
        for (int i = 0; i < MAXPOP; i++) if (popKey[i] == null) { f = i; break; }
        if (f < 0) f = 0;
        popKey[f] = k; popTxt[f] = t; popP[f] = 0.3f; popOn[f] = true;
        return f;
    }








    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacksAndMessages(null);
        for (int i = 0; i < MAXP; i++) { pk[i] = null; used[i] = false; popIdx[i] = -1; }
        for (int i = 0; i < MAXPOP; i++) popKey[i] = null;
        repSlot = -1; lastFrame = 0;
    }
}
