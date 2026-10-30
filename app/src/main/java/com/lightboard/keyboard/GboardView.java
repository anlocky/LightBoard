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

/**
 * Второй дизайн: как Gboard (ряд цифр, строка подсказок со значком приложений и микрофоном, без нижней панели и без размытия).
 * Вся логика ввода в сервисе общая; здесь только геометрия, отрисовка и касания. Анимации лёгкие: нажатие, пузырь, след.
 */
final class GboardView extends BoardView {
    private static final float PITCH = 46, STRIP = 48, PAD = 4, GAP = 4, BOT = 8, BAR = 46, PANEL_R = 18;
    private static final int T_GLOBE = 6, T_PUNCT = 7, T_MIC = 8;
    private static final int MAXP = 6;


    private KeyboardView.Listener lis;
    private boolean toolsOpen, split, langKey; // меню над клавишами открыто / клавиатура разделена / отдельная клавиша языка
    private int oneHand;               // 0 выкл, 1 слева, 2 справа (для подсветки пункта меню)
    private int numpad;                // 0 обычная клавиатура, 1 PIN/целое число, 2 телефон, 3 дата/время, 4 число со знаком или точкой
    private boolean tablet;            // планшетная раскладка (отдельно от телефонной)
    private int round = Shape.DEFAULT; // закругление клавиш 0..100
    // анимация меню: раскрытие и подсветка включённых режимов
    private boolean keyBg = true;
 // масштаб шрифта слов подсказок (длинные слова уменьшаются, а не обрезаются)
    private boolean bottomBar, swapIcons, popupOn = true, lineOn = true, logoOn; // нижняя панель, значки местами, миниатюра, линия, значок L
    private int micPos;
    private boolean micOn = true, incoAllowed, incoActive;
    private int botPad;
    private float fs = 1f;
    @Override public void setBottomPad(int dp) { if (botPad != dp) { botPad = dp; requestLayout(); build(); invalidate(); } }
    @Override public void setFontScale(float f) { if (fs != f) { fs = f; restyle(); invalidate(); } }
    @Override public void setMic(boolean on) { if (micOn != on) { micOn = on; build(); invalidate(); } }
    @Override public void setIncognito(boolean allowed, boolean active) { if (incoAllowed != allowed || incoActive != active) { incoAllowed = allowed; incoActive = active; invalidate(); } }
               // с нижней панелью: 0 микрофон сверху, 1 снизу, 2 везде
       // поверх полосы показаны чипы автозаполнения
    private boolean haptic = true, swipeOn, numRow = true, showWords = true, globeMode;
    private String enterLabel, clipText, clipDisp;
    private float kwPitch;

    private long lastFrame, langT;
    private float pageP = 1f, sugP = 1f, trailA, menuP;

    private final boolean[] used = new boolean[MAXP], fired = new boolean[MAXP];
    private final int[] pid = new int[MAXP], psug = new int[MAXP];
    private final Key[] pk = new Key[MAXP];
    private final long[] pressT = new long[MAXP];
    private final Runnable[] lp = new Runnable[MAXP];
    private int repSlot = -1, repCount;

    private final float[] downX = new float[MAXP], lastCx = new float[MAXP];
    private final float[] downY = new float[MAXP], lastCy = new float[MAXP];
    private final boolean[] cursorMode = new boolean[MAXP];
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

    private final Path trail = new Path(), path = new Path();

    private float menuL, menuT, menuR, menuB, menuCell;

    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pStroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            pLetter = new Paint(Paint.ANTI_ALIAS_FLAG), pMid = new Paint(Paint.ANTI_ALIAS_FLAG),
            pSug = new Paint(Paint.ANTI_ALIAS_FLAG), pPop = new Paint(Paint.ANTI_ALIAS_FLAG), pTrail = new Paint(Paint.ANTI_ALIAS_FLAG);

    GboardView(Context c, Theme theme) {
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
    @Override public void setSwipe(SwipeDecoder s) { swipe = s; }
    @Override public void setSwipeEnabled(boolean on) { swipeOn = on; }
    @Override public void setHaptic(boolean on) { haptic = on; }
    @Override public int getPage() { return page; }
    @Override public void setNumRow(boolean v) { if (numRow != v) { numRow = v; requestLayout(); build(); invalidate(); } }
    @Override public void setShowWords(boolean v) { if (showWords != v) { showWords = v; requestLayout(); build(); invalidate(); } }
    @Override public void setGlobeMode(boolean g) { if (globeMode != g) { globeMode = g; invalidate(); } }
    @Override public void setBottomBar(boolean v) { if (bottomBar != v) { bottomBar = v; requestLayout(); build(); invalidate(); } }
    @Override public void setPopup(boolean on) { popupOn = on; }
    @Override public void setLine(boolean on) { lineOn = on; }
    @Override public void setLogo(boolean on) { if (logoOn != on) { logoOn = on; invalidate(); } }
    @Override public void setMicPos(int p) { if (micPos != p) { micPos = p; build(); invalidate(); } }
    private boolean bb() { return bottomBar && numpad == 0 && !tablet; }
    private boolean micTop() { return micOn && showWords && (!bb() || micPos != 1); }
    @Override public void setNumpad(int m) { if (numpad != m) { numpad = m; restyle(); requestLayout(); build(); invalidate(); } }

 // масштаб знаков на планшете
    /** Размер значков на клавишах: на планшете крупнее, на цифровой клавиатуре (PIN, телефон) тоже. */
    private float uu() { return den * ts; }
    /** Высота с учётом цифровой клавиатуры: у неё клавиши крупнее (но блок всё равно не на всю ширину). */
    private float hsEff() { return hs * ((numpad != 0 || (page != 0 && !tablet)) && numRow ? 1.25f : 1f); } // цифровая клавиатура той же высоты, что обычная (4 ряда вместо 5)

    @Override public void setTextScale(float s) { if (ts != s) { ts = s; restyle(); invalidate(); } }

    private void restyle() {
        boolean np = numpad != 0;
        setup(pLetter, 22 * ts * fs * (np ? 1.12f : 1f)); setup(pMid, 15 * ts * fs * (np ? 1.08f : 1f)); setup(pPop, 32 * ts * fs);
        if (ts > 1f || np) { Typeface md = Typeface.create("sans-serif-medium", Typeface.NORMAL); pLetter.setTypeface(md); pMid.setTypeface(md); }
        setup(pHint, 10 * ts * fs); offLetter = off(pLetter); offMid = off(pMid); offPop = off(pPop); offHint = off(pHint);
    }
    @Override public void setHints(boolean num, boolean sym) { if (hintNum != num || hintSym != sym) { hintNum = num; hintSym = sym; restyle(); build(); invalidate(); } }
    @Override public void setStripHidden(boolean h) { if (stripHidden != h) { stripHidden = h; invalidate(); } }
    @Override public void setSwapIcons(boolean on) { if (swapIcons != on) { swapIcons = on; build(); invalidate(); } }
    @Override public void setTablet(boolean on) { if (tablet != on) { tablet = on; requestLayout(); build(); invalidate(); } }
    @Override public void setRound(int v) { if (round != v) { round = v; build(); invalidate(); } }
    @Override public int[] strip() { return new int[]{(int) d(56), (int) d(58), (int) stripBottom()}; }
    @Override public void setLangKey(boolean on) { if (langKey != on) { langKey = on; build(); invalidate(); } }
    @Override public void setSplit(boolean on) { if (split != on) { split = on; if (getWidth() == 0 || !isShown()) spP = on ? 1f : 0f; build(); invalidate(); } } // анимация — только когда клавиатура на экране
    @Override public void setOneHand(int m) { if (oneHand != m) { oneHand = m; invalidate(); } }
    @Override public void playEntrance() { if (toolsOpen || toolsP != 0f) { toolsOpen = false; toolsP = 0f; if (lis != null) lis.onToolsToggle(false); invalidate(); } }
    @Override public void themeChanged() { invalidate(); }
    @Override public void setEnterLabel(String s) { if (!eq(enterLabel, s)) { enterLabel = s; invalidate(); } }
    @Override public void setLayout(Layouts.L l) {
        if (lay == l) return;
        prevLay = lay; lay = l;
        if (W > 0) { langT = SystemClock.uptimeMillis(); pageP = 0f; }
        build(); invalidate();
    }
    @Override public void setPage(int p) {
        p = Math.max(0, Math.min(3, p));
        if (page == p) return;
        page = p;
        if (W > 0) pageP = 0f;
        build(); invalidate();
    }
    @Override public void setShift(int s) { if (shift != s) { shift = s; invalidate(); } }
    @Override public void setSuggestions(String a, String b, String c) {
        if (eq(sug[0], a) && eq(sug[1], b) && eq(sug[2], c)) return;
        sug[0] = a; sug[1] = b; sug[2] = c; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; sugP = 0f; invalidate();
    }


    /** Миниатюра слева в чипе: квадрат 28 dp по центру картинки. */
    private void drawThumb(Canvas c, float cx, float cy, float a) {
        pFill.setColor(Theme.fade(Color.WHITE, a));
        rf.set(cx - d(14), cy - d(14), cx + d(14), cy + d(14));
        c.drawBitmap(clipImg, clipSrc, rf, pFill);
    }

    @Override public void setClip(String s) { if (!eq(clipText, s)) { clipText = s; clipDisp = null; sugP = 0f; invalidate(); } }
    @Override public void cancelTouches() {
        for (int i = 0; i < MAXP; i++) { release(i); used[i] = false; psug[i] = -1; }
        gSlot = -1; gesturing = false; gn = 0; menuOn = false; menuSlot = -1;
        invalidate();
    }

    // ---------- раскладка ----------
    private void build() {
        if (W == 0) return;
        ArrayList<Key> L = new ArrayList<>(48);
        float pad = d(PAD), gx = d(GAP), full = W - 2 * pad, top = d(showWords ? STRIP : 6);
        float pitch = d(PITCH) * hsEff(), kh = pitch - d(9), vg = (pitch - kh) / 2f;
        float[] y = new float[5];
        for (int i = 0; i < 5; i++) y[i] = top + (i - (numRow ? 0 : 1)) * pitch + vg; // логические ряды: 0 цифры, 1–3 буквы, 4 нижний

        float[] ys = new float[4]; // ряды цифровой клавиатуры и страниц ?123: четыре ряда подряд сверху
        for (int i = 0; i < 4; i++) ys[i] = top + i * pitch + vg;
        int npm = numpad != 0 ? numpad : (page == 3 ? 5 : 0); // 5 — цифровая клавиатура, вызванная клавишей «1234»
        if (npm != 0) { // поле числа, телефона или даты: общий план цифровой клавиатуры
            KeyPlan np = KeyPlan.numpad(npm);
            float gp = d(10), nw = tablet ? Math.min(full, np.total * 1.45f * kh + (np.total - 1f) * gp) : full; // телефон: на всю ширину; планшет: блок по центру, клавиши ~1,45:1
            buildFromPlan(L, np, (W - nw) / 2f, nw, gp, ys, kh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }
        if (tablet) { // планшетная раскладка: отдельный план (как на ПК), ряд цифр по желанию
            buildFromPlan(L, KeyPlan.tablet(lay, numRow, page), pad, full, gx, y, kh, numRow ? 0 : 1);
            finishKeys(L, gx);
            return;
        }

        if (page != 0) { // страницы ?123: общий план, одинаковый в обоих дизайнах
            buildFromPlan(L, KeyPlan.symbols(lay, page), pad, full, gx, ys, kh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }

        float kwn = (full - 9 * gx) / 10f; // ряд цифр
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

        // нижний ряд: ?123 | смайл или глобус | [микрофон, если нет верхней панели] | пробел | . | enter
        float[] w;
        int[] types;
        if (bb()) { // нижняя панель: глобус и микрофон уходят под клавиши, в ряду остаются ?123, пробел, точка и enter
            w = new float[]{105f, 400f, 60f, 97f};
            types = new int[]{T_PAGE, T_SPACE, T_PUNCT, T_ENTER};
        } else if (showWords) { // отдельная клавиша языка забирает часть пробела
            w = langKey ? new float[]{105f, 60f, 60f, 280f, 60f, 97f} : new float[]{105f, 60f, 340f, 60f, 97f};
            types = langKey ? new int[]{T_PAGE, T_GLOBE, T_LANG, T_SPACE, T_PUNCT, T_ENTER} : new int[]{T_PAGE, T_GLOBE, T_SPACE, T_PUNCT, T_ENTER};
        } else {
            w = langKey ? new float[]{105f, 60f, 60f, 60f, 220f, 60f, 97f} : new float[]{105f, 60f, 60f, 280f, 60f, 97f};
            types = langKey ? new int[]{T_PAGE, T_GLOBE, T_LANG, T_MIC, T_SPACE, T_PUNCT, T_ENTER} : new int[]{T_PAGE, T_GLOBE, T_MIC, T_SPACE, T_PUNCT, T_ENTER};
        }
        if (!micOn) for (int i = 0; i < types.length; i++) if (types[i] == T_MIC) { // микрофон выключен: его место достаётся пробелу
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
        if (bb()) { // значки нижней панели (строка 5: без подложки), как в Cupertino
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

    @Override public void setToolsOpen(boolean on) { setTools(on && showWords); }

    private void setTools(boolean on) {
        if (toolsOpen == on) return;
        toolsOpen = on; invalidate();
        if (lis != null) lis.onToolsToggle(on);
    }

    private int menuPressed() {
        for (int q = 0; q < MAXP; q++) if (used[q] && psug[q] >= 20) return psug[q] - 20;
        return -1;
    }

    private void buildFromPlan(ArrayList<Key> L, KeyPlan plan, float x0, float full, float gx, float[] y, float rh, int off) {
        final float c = (full - (plan.total - 1f) * gx) / plan.total;
        for (int r = 0; r < plan.rows.size(); r++) {
            float x = x0;
            for (KeyPlan.Spec sp : plan.rows.get(r)) {
                float w = sp.w * c + (sp.w - 1f) * gx;
                Key k = new Key(vt(sp.kind), sp.label, sp.letter ? lay.alt(sp.label.charAt(0)) : (".".equals(sp.label) ? android.text.TextUtils.join("|", PUNCT_ITEMS) : null), sp.target, r + off);
                if (sp.letter) k.upper = lay.upper(k.label);
                add(L, k, x, y[r + off], w, rh);
                k.rad = (plan.pill || sp.kind == KeyPlan.ENTER || (sp.kind == KeyPlan.PAGE && sp.target == 0)) ? rh * 0.5f : Math.min(rh * 0.36f, w * 0.5f);
                x += w + gx;
            }
        }
    }

    /** Ключи -> массив; закругление по ползунку; разделение; буквы для непрерывного ввода. */
    /** Пока половины разъезжаются или съезжаются (spP между 0 и 1), клавиши на кадр ставятся по доле разделения; потом координаты возвращаются. */
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
        for (Key rk : keys) rk.rad = Shape.radius(rk.rad, Math.min(rk.r - rk.l, rk.b - rk.t) * 0.5f, round); // 50 — как раньше
        if (split) for (Key sk : keys) { // каждая половина сжимается к своему краю; пробел перекрывает промежуток
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

    // ---------- анимации ----------
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
        float tgT = toolsOpen ? 1f : 0f, tgO = oneHand != 0 ? 1f : 0f, tgS = split ? 1f : 0f; // анимация меню и подсветки режимов
        if (toolsP != tgT) { toolsP = Anim.step(toolsP, tgT, dt, 230f); a = true; }
        if (onP != tgO) { onP = Anim.step(onP, tgO, dt, 220f); a = true; }
        if (spP != tgS) { spP = Anim.step(spP, tgS, dt, 220f); a = true; }
        if (menuOn) { menuP = Anim.step(menuP, 1f, dt, MenuFx.OPEN); a = true; } else if (menuP > 0f) { menuP = Anim.step(menuP, 0f, dt, MenuFx.CLOSE); a = true; }
        if (menuP > 0f && menuItems != null && menuSel >= 0 && menuSel < menuItems.length)
            a |= fx.step(dt, menuOn, menuSel, menuR - (menuSel % menuCols + 0.5f) * menuCell, menuB - (menuSel / menuCols + 0.5f) * menuCellH);
        else a |= fx.step(dt, menuOn, -1, 0f, 0f);
        if (clearSlot >= 0 || listening) a = true;
        if (lineOn && Trail.live(trT, gn, SystemClock.uptimeMillis())) a = true; // хвост следа тает сам, пока не исчезнет
        if (langT != 0) { if (now - langT < 1100) a = true; else langT = 0; }
        lastFrame = a ? now : 0;
        return a;
    }


    @Override protected void onDraw(Canvas c) {
        try { draw0(c); failN = 0; }
        catch (RuntimeException e) { Diag.saveOnce(getContext(), e); heal(); }
    }

    /** Сбой при рисовании не должен оставлять пустую панель: сбрасываем нажатия и след, пересобираем клавиши, а при повторе просим сервис пересоздать вид. */
    private void heal() {
        cancelTouches();
        gn = 0;
        if (++failN >= 2) { failN = 0; if (lis != null) lis.onBroken(); return; }
        build();
        postInvalidate();
    }

    @Override public boolean healthy() {
        return W > 0 && H > 0 && keys != null && keys.length > 0 && !Float.isNaN(keys[0].l) && !Float.isNaN(keys[keys.length - 1].r);
    }

    @Override public void setKeyBg(boolean on) { if (keyBg != on) { keyBg = on; invalidate(); } }

    private void draw0(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean anim = tick(now);
        float rad = d(PANEL_R);
        pFill.setColor(th.panelTop);
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pFill);
        drawStrip(c);
        float pe = Anim.ease(pageP);
        boolean sa = Math.abs(spP - (split ? 1f : 0f)) > 0.002f && numpad == 0; // с первого кадра рисуем «между» состояниями: без скачка в конечное положение
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
        if (anim) postInvalidateOnAnimation();
    }

    private void drawStrip(Canvas c) {
        if (!showWords) return; // верхняя панель отключена: значка настроек нет, микрофон переехал в нижний ряд
        float cy = d(STRIP) / 2f + d(1), sp = Anim.ease(sugP), u = den;
        if (toolsP < 1f) Icons.apps(c, pFill, rf, d(27), cy, u, Theme.fade(th.text, 1f - toolsP));
        if (toolsP > 0f) Icons.chevron(c, path, pStroke, d(27), cy, u, Theme.fade(th.text, toolsP));
        // кнопка микрофона (при нижней панели положение задаётся настройкой)
        float mx = W - d(34);
        if (micTop()) {
        if (listening) { // кольцо «слушаю»
            float ph = (SystemClock.uptimeMillis() % 1100) / 1100f;
            pFill.setColor(Theme.fade(th.accent, 0.35f * (1f - ph))); c.drawCircle(mx, cy, d(19) + d(9) * ph, pFill);
        }
        pFill.setColor(listening ? th.accent : th.key);
        c.drawCircle(mx, cy, d(19), pFill);
        int mc = listening ? th.onAccent : th.text;
        Icons.mic(c, pStroke, rf, mx - d(1.5f), cy + d(1), u * 0.9f, mc);
        Icons.sparkle(c, path, pFill, mx + d(9), cy - d(8), d(4.2f), mc);
        }

        float x0 = d(56), x1 = W - d(58), cell = (x1 - x0) / 3f; // между значком слева и микрофоном справа
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
        if (toolsP >= 0.34f) return; // разделители гаснут первыми при открытии меню
        pFill.setColor(Theme.fade(th.div, 1f - toolsP * 3f));
        for (int i = 1; i < 3; i++) { float x = x0 + cell * i; c.drawRect(x - d(0.5f), cy - d(10), x + d(0.5f), cy + d(10), pFill); }
    }

    private void drawKey(Canvas c, Key k, float pe, long now) {
        if (pe <= 0.01f) return;
        float u = uu(), cx = (k.l + k.r) / 2f, cy = (k.t + k.b) / 2f;
        int base, down;
        boolean accent = k.type == T_ENTER && numpad == 0 && !(tablet && enterLabel == null); // у числовой и планшетной клавиатуры обычный ввод — тонированная клавиша
        if (accent) { base = th.accent; down = Theme.mix(th.accent, th.onAccent, 0.2f); }
        else if (k.type == T_CHAR || k.type == T_SPACE || (!tablet && (k.type == T_GLOBE || k.type == T_LANG)) || k.type == T_MIC || (k.type == T_PAGE && k.target == 3) || (!tablet && k.type == T_SHIFT && shift != 0)) { base = th.key; down = th.keyDown; }
        else { base = th.spec; down = th.specDown; }
        float dy = (1f - pe) * d(4), sq = Shape.squeeze(round), kw2 = k.r - k.l, kh2 = k.b - k.t; // выше 50 % клавиша ещё и сжимается к кругу
        float ix = kw2 > kh2 && kw2 <= 1.6f * kh2 ? (kw2 - kh2) * sq / 2f : 0f, iy = kh2 > kw2 ? (kh2 - kw2) * sq / 2f : 0f;
        float fb = ((keyBg || accent) && k.row != 5) ? pe : pe * k.p; // значки нижней панели (строка 5) видны без подложки // без подложек клавиша видна только при нажатии
        if (fb > 0.01f) {
            pFill.setColor(Theme.fade(Theme.mix(base, down, k.p), fb));
            c.drawRoundRect(k.l + ix, k.t + dy + iy, k.r - ix, k.b + dy - iy, k.rad, k.rad, pFill);
        }
        int tc = Theme.fade(th.text, pe);
        if (dy != 0f) { c.save(); c.translate(0, dy); }
        switch (k.type) {
            case T_CHAR:
                txt(c, (shift != 0 && page == 0) ? k.upper : k.label, cx, cy, pLetter, offLetter, tc);
                if (k.hint != null) txt(c, k.hint, cx + (k.r - k.l) * 0.28f, cy - (k.b - k.t) * 0.3f, pHint, offHint, Theme.fade(th.muted, pe));
                break;
            case T_PAGE: if (k.target == 3) numIcon(c, k, cx, pMid, offMid, tc); else txt(c, k.label, cx, cy, pMid, offMid, tc); break;
            case T_PUNCT: txt(c, k.label != null ? k.label : ".", cx, cy, pLetter, offLetter, tc); break;
            case T_SHIFT: Icons.shift(c, path, pFill, pStroke, cx, cy, u, shift, tc); break;
            case T_BACK: Icons.back(c, path, pStroke, cx, cy, u, tc); break;
            case T_GLOBE:
                if (globeMode) Icons.globe(c, pStroke, cx, cy, u * 0.9f, tc); else Icons.smiley(c, pFill, pStroke, rf, cx, cy, u * 0.9f, tc);
                break;
            case T_MIC: Icons.mic(c, pStroke, rf, cx, cy, u, listening ? th.accent : tc); break;
            case T_ENTER:
                if (numpad != 0) Icons.check(c, path, pStroke, cx, cy, u, Theme.fade(th.text, pe));
                else Icons.enter(c, path, pStroke, cx, cy, u, Theme.fade(accent ? th.onAccent : th.text, pe));
                break;
            case T_LANG: Icons.globe(c, pStroke, cx, cy, u * 0.9f, tc); break;
            case T_TAB: Icons.tab(c, pStroke, cx, cy, u, tc); break;
            case T_CAPS: Icons.caps(c, pStroke, cx, cy, u, shift == 2 ? th.accent : tc); break;
            case T_LEFT: Icons.triangle(c, path, pFill, cx, cy, u, true, tc); break;
            case T_RIGHT: Icons.triangle(c, path, pFill, cx, cy, u, false, tc); break;
            case T_SPACE: {
                if (incoActive) Icons.incognito(c, pStroke, k.l + d(24), cy, u * 0.8f, Theme.fade(th.muted, pe));
                if (!logoOn) { txt(c, lay.name, cx, cy, pMid, offMid, Theme.fade(th.muted, pe)); break; } // по умолчанию на пробеле название языка
                float la = spaceLabelAlpha(now); // логотип приложения посередине пробела, на время показа названия языка гаснет
                if (la < 1f) Icons.logo(c, path, pStroke, pFill, cx, cy, u, Theme.fade(th.muted, pe * (1f - la)));
                drawSpaceLabel(c, cx, cy, pe, now);
                break;
            }
            default: break;
        }
        if (dy != 0f) c.restore();
    }

    /** При смене языка на пробеле на секунду появляется его название. */
    private float spaceLabelAlpha(long now) {
        if (langT == 0) return 0f;
        float t = now - langT;
        return t < 150f ? t / 150f : (t > 850f ? Math.max(0f, 1f - (t - 850f) / 250f) : 1f);
    }

    private void drawSpaceLabel(Canvas c, float cx, float cy, float fa, long now) {
        float a = spaceLabelAlpha(now);
        if (a <= 0f) return;
        txt(c, lay.name, cx, cy, pMid, offMid, Theme.fade(th.muted, fa * a));
    }

    private void drawPopup(Canvas c, Key k, String text, float p) {
        float a = Math.min(1f, p * 1.6f), sc = 0.88f + 0.12f * p, cx = (k.l + k.r) / 2f;
        float bw = Math.max(k.r - k.l + d(16), d(48)), bh = d(52), rr = d(14);
        float l = Math.max(d(2), Math.min(cx - bw / 2f, W - d(2) - bw)), b = k.t - d(6), t = b - bh;
        if (t < d(2)) { bh = Math.max(b - d(2), d(30)); t = Math.max(d(2), b - bh); b = t + bh; } // выше вида не рисуем: сначала ниже, потом сдвигаем
        c.save(); c.scale(sc, sc, cx, k.t);
        pFill.setColor(Theme.fade(Color.argb(60, 0, 0, 0), a)); c.drawRoundRect(l, t + d(2), l + bw, b + d(2), rr, rr, pFill);
        pFill.setColor(Theme.fade(th.pop, a)); c.drawRoundRect(l, t, l + bw, b, rr, rr, pFill);
        txt(c, text, l + bw / 2f, (t + b) / 2f, pPop, offPop, Theme.fade(th.text, a));
        c.restore();
    }

    private void drawMenu(Canvas c) {
        final boolean open = menuOn;
        float bp = MenuFx.body(menuP, open), sc = 0.5f + 0.5f * bp, a = Math.min(1f, menuP * 2.4f), rr = d(14);
        float px = Math.max(menuL, Math.min(menuR, menuAx)); // меню растёт из точки над нажатой клавишей
        c.save(); c.scale(sc, sc, px, menuB + d(8));
        pFill.setColor(Theme.fade(Color.argb(60, 0, 0, 0), a)); c.drawRoundRect(menuL, menuT + d(1 + 2 * bp), menuR, menuB + d(1 + 2 * bp), rr, rr, pFill); // тень «подрастает» вместе с меню
        pFill.setColor(Theme.fade(th.pop, a)); c.drawRoundRect(menuL, menuT, menuR, menuB, rr, rr, pFill);
        if (menuSel >= 0 && menuSel < menuItems.length) { // подсветка одна: она переезжает к выбранному пункту
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
            txt(c, menuItems[j], cx, cy, pLetter, offLetter, Theme.fade(on ? th.onAccent : th.text, ga));
            c.restore();
        }
        c.restore();
    }

    private void drawTrail(Canvas c, long now) {
        if (!Trail.ribbon(trail, trX, trY, trT, gn, now, d(10))) return;
        pFill.setColor(Theme.fade(th.accent, 0.92f));
        c.drawPath(trail, pFill);
        c.drawCircle(trX[gn - 1], trY[gn - 1], d(5) * (1f - (now - trT[gn - 1]) / (float) Trail.LIFE), pFill); // округлая «головка» у пальца
    }

    // ---------- касания ----------
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
        return split && Math.abs(x - W / 2f) < Split.GAP * (W - 2 * d(PAD)) / 2f ? null : best; // пустая середина разделённой клавиатуры не нажимает боковые клавиши
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        try { return touch0(e); } catch (RuntimeException ex) { Diag.saveOnce(getContext(), ex); return true; }
    }

    private boolean touch0(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: { int i = e.getActionIndex(); down(e.getPointerId(i), e.getX(i), e.getY(i)); break; }
            case MotionEvent.ACTION_MOVE: for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i)); break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: { int i = e.getActionIndex(); up(e.getPointerId(i), e.getX(i), e.getY(i)); break; }
            case MotionEvent.ACTION_CANCEL: cancelTouches(); break;
        }
        return true;
    }

    private int slotOf(int id) { for (int i = 0; i < MAXP; i++) if (used[i] && pid[i] == id) return i; return -1; }
    private void buzz(int c) { if (haptic) performHapticFeedback(c); }

    @SuppressWarnings("deprecation")
    private void invKey(Key k) { invalidate((int) (k.l - d(10)), (int) (k.t - d(66)), (int) (k.r + d(10)), (int) (k.b + d(6))); }

    private void down(int id, float x, float y) {
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

    /** 0..2 — подсказки, 10 — значок приложений, 11 — микрофон. */
    private int zone(float x) {
        if (x < d(54)) return 10;
        if (x > W - d(62)) return 11;
        if (!showWords) return -1;
        if (toolsOpen) { int h = MenuBar.hit(x, d(56), W - d(58)); return h < 0 ? -1 : 20 + h; }
        float x0 = d(56), cell = (W - d(58) - x0) / 3f;
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

    private void release(int s) {
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
        if (k.type == T_PUNCT && (k.label == null || ".".equals(k.label))) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS);
            openMenu(s, k, PUNCT_ITEMS, true);
        } else if (k.type == T_SPACE) { // удержание пробела (без сдвига) — режим инкогнито
            if (incoAllowed && !fired[s] && !cursorMode[s]) { fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onIncognito(); }
        } else if (k.type == T_GLOBE) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onEmojiKey(true);
        } else if (k.alt != null) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); openMenu(s, k, k.alt.split("\\|"), false);
        }
    }



    /** Меню над клавишей: знаки препинания (сетка 5 в ряд) или варианты буквы (ударение, ё, ъ). Выбор свайпом, ввод при отпускании. */
    private void openMenu(int s, Key k, String[] items, boolean punct) {
        String[] it = items;
        if (!punct && shift != 0 && page == 0) { it = items.clone(); for (int i = 0; i < it.length; i++) it[i] = lay.upperKeep(it[i]); }
        menuItems = it;
        menuCols = punct ? Math.min(5, it.length) : it.length;
        int rows = (it.length + menuCols - 1) / menuCols;
        menuCell = Math.min(d(44), (W - d(8)) / menuCols);
        menuCellH = d(punct ? 44 : 46);
        float anchor = punct ? Math.min(W - d(4), k.r + d(4)) : (k.l + k.r) / 2f + menuCell / 2f;
        menuR = Math.min(W - d(4), Math.max(anchor, d(4) + menuCols * menuCell));
        menuL = menuR - menuCols * menuCell;
        menuB = k.t - d(8); menuT = menuB - rows * menuCellH;
        menuSlot = s; menuSel = 0; menuOn = true;
        menuAx = (k.l + k.r) / 2f; fx.reset(); menuP = 0f;
        invalidate();
    }

    private int menuSelAt(float x, float y) {
        int c = (int) Math.floor((menuR - x) / menuCell);
        if (c < 0) c = 0;
        if (c >= menuCols) return -1;
        int r = (int) Math.floor((menuB - y) / menuCellH);
        if (r < 0) r = 0;
        int j = r * menuCols + c;
        return j < menuItems.length ? j : -1;
    }

    private void move(int id, float x, float y) {
        int s = slotOf(id);
        if (s < 0) return;
        if (menuOn && s == menuSlot) { int sel = menuSelAt(x, y); if (sel != menuSel) { menuSel = sel; invalidate(); } return; }
        if (cursorMode[s]) { cursorStep(s, x, y); return; }
        Key cur = pk[s];
        if (cur != null && !fired[s] && cur.type == T_SPACE && spaceCursor && (Math.abs(x - downX[s]) > d(14) || Math.abs(y - downY[s]) > d(22))) {
            cursorMode[s] = true; fired[s] = true; lastCx[s] = downX[s]; lastCy[s] = downY[s]; buzz(HapticFeedbackConstants.KEYBOARD_TAP); cursorStep(s, x, y); return;
        }
        if (cur != null && cur.type == T_BACK && clearAll && !fired[s]) { // свайп вверх по «стереть» + удержание = стереть всё
            if (y < cur.t) { // палец ушёл вверх за край «стереть»: ждём жест и на букву выше не переключаемся (раньше как раз это ломало жест)
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

    private void up(int id, float x, float y) {
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
                else if (z >= 20) { setTools(false); lis.onMenu(z - 20); }
                else if (clipText != null) lis.onClipTap(x > W - d(58) - d(46));
                else lis.onSuggest(z);
            }
        } else {
            Key k = pk[s];
            if (k != null && !fired[s]) commit(k);
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

    public void setHeightScale(float v) { if (hs != v) { hs = v; requestLayout(); build(); invalidate(); } }

    /** Свайп по пробелу: вправо-влево двигает курсор по строке, вверх-вниз — по строкам текста. */
    private void cursorStep(int s, float x, float y) {
        float step = d(11), stepV = d(26);
        while (Math.abs(x - lastCx[s]) >= step) {
            int dir = x > lastCx[s] ? 1 : -1;
            lastCx[s] += dir * step;
            if (lis != null) lis.onCursor(dir);
            buzz(HapticFeedbackConstants.CLOCK_TICK);
        }
        while (Math.abs(y - lastCy[s]) >= stepV) {
            int dir = y > lastCy[s] ? 1 : -1;
            lastCy[s] += dir * stepV;
            if (lis != null) lis.onCursorV(dir);
            buzz(HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /** Кольцо над «стереть»: сколько ещё удерживать до удаления всего текста. */
    private void drawClearArc(Canvas c, long now) {
        Key k = pk[clearSlot];
        if (k == null) return;
        float p = Math.min(1f, (now - clearStart) / (float) clearHold), cx = (k.l + k.r) / 2f, cy = k.t - d(34), r = d(15);
        pFill.setColor(Theme.fade(th.pop, 0.95f));
        c.drawCircle(cx, cy, r + d(5), pFill);
        float sw = pStroke.getStrokeWidth();
        pStroke.setStrokeWidth(d(3));
        pStroke.setColor(Theme.fade(th.muted, 0.5f)); c.drawCircle(cx, cy, r, pStroke);
        pStroke.setColor(th.accent); rf.set(cx - r, cy - r, cx + r, cy + r); c.drawArc(rf, -90, 360 * p, false, pStroke);
        pStroke.setStrokeWidth(sw);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacksAndMessages(null);
        for (int i = 0; i < MAXP; i++) { pk[i] = null; used[i] = false; }
        repSlot = -1; lastFrame = 0;
    }
}
