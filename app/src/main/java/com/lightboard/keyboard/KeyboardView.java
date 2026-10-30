package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
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
 * Вся клавиатура — один View на Canvas: без битмапов, без аллокаций в onDraw.
 * Анимации считаются по времени прямо в onDraw; кадры запрашиваются только пока что-то движется,
 * в покое клавиатура не потребляет CPU.
 */
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
        void onCursor(int dir);      // сдвиг курсора свайпом по пробелу (-1 влево, +1 вправо)
        void onCursorV(int dir);     // то же вверх (-1) и вниз (+1) по строкам текста
        void onClearAll();           // стереть весь текст (свайп вверх по «стереть» и удержание)
        void onOpenSettings();       // открыть настройки (пункт меню)
        void onMenu(int id);         // пункт меню: Board.MENU_CLIP / MENU_ONEHAND / MENU_SPLIT
        void onIncognito();          // долгое нажатие на пробел
        void onKeySound(int kind);   // нажата клавиша: KeySounds.KEY / DELETE / MOD
        void onLang();               // отдельная клавиша языка
        void onSpecial(int keyCode); // служебная клавиша планшетной раскладки (Tab)
        void onCaps();               // клавиша Caps Lock планшетной раскладки
        void onToolsToggle(boolean open); // меню над клавишами открылось или закрылось
        void onBroken();             // сбой при рисовании повторился: сервису пора пересоздать вид
    }

    // ---- геометрия (dp), снята с референса iOS ----
    private static final float MARGIN = 3, GAP_X = 6, GAP_Y = 10, SUG_ZONE = 46, BOT_ZONE = 54, ROW_H = 38;
    private static final float KEY_R = 5.5f, PANEL_R = 16;
    private static final float BOT_ZONE_OFF = 10;

    private static final int T_EMOJI = 6, T_MIC = 7, T_PUNCT = 8;
    private static final int MAXP = 6, MAXPOP = 8;


    private Listener lis;
    private boolean globeMode, bottomBar, showWords = true, toolsOpen, split, langKey, tablet;
    private int oneHand, numpad, round = Shape.DEFAULT;
 // анимация меню: раскрытие и подсветка включённых режимов
    private boolean keyBg = true, swapIcons, popupOn = true, lineOn = true;
 // масштаб шрифта слов подсказок (длинные слова уменьшаются, а не обрезаются)
    // поверх полосы показаны чипы автозаполнения
    private String enterLabel, clipText;
    private boolean numRow;
    private float menuL, menuT, menuR, menuB, menuCell, menuP;
    private String chipDisp;
    private boolean haptic = true, swipeOn;
    private float kwPitch, zoneTop;

    // ---- анимации ----
    private long lastFrame, enterStart, langT, shiftT;
    private float pageP = 1f, sugP = 1f, trailA = 0f;

    // ---- мультитач ----
    private final boolean[] used = new boolean[MAXP], fired = new boolean[MAXP];
    private final int[] pid = new int[MAXP], psug = new int[MAXP], popIdx = new int[MAXP];
    private final Key[] pk = new Key[MAXP];
    private final Runnable[] lp = new Runnable[MAXP];
    private final Key[] popKey = new Key[MAXPOP];
    private final String[] popTxt = new String[MAXPOP];
    private final float[] popP = new float[MAXPOP];
    private final boolean[] popOn = new boolean[MAXPOP];
    private int repSlot = -1, repCount = 0;

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

    private final Path trail = new Path();

    // ---- краски ----
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pStroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            pPanel = new Paint(Paint.ANTI_ALIAS_FLAG), pLetter = new Paint(Paint.ANTI_ALIAS_FLAG),
            pMid = new Paint(Paint.ANTI_ALIAS_FLAG), pSug = new Paint(Paint.ANTI_ALIAS_FLAG),
            pPop = new Paint(Paint.ANTI_ALIAS_FLAG), pTrail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

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


    // ---------- публичный API ----------
    public void setListener(Listener l) { lis = l; }
    public int getPage() { return page; }
    public void setSwipe(SwipeDecoder s) { swipe = s; }
    public void setNumRow(boolean v) { if (numRow != v) { numRow = v; requestLayout(); build(); invalidate(); } }


    /** Миниатюра слева в чипе: квадрат 28 dp по центру картинки. */
    private void drawThumb(Canvas c, float cx, float cy, float a) {
        pFill.setColor(Theme.fade(Color.WHITE, a));
        rf.set(cx - d(14), cy - d(14), cx + d(14), cy + d(14));
        c.drawBitmap(clipImg, clipSrc, rf, pFill);
    }

    public void setClip(String s) { if (!eq(clipText, s)) { clipText = s; chipDisp = null; sugP = 0f; invalidate(); } }
    public void setSwipeEnabled(boolean on) { swipeOn = on; }
    public void setHaptic(boolean on) { haptic = on; }
    public void setGlobeMode(boolean g) { if (globeMode != g) { globeMode = g; invalidate(); } }
    public void setShowWords(boolean v) { if (showWords != v) { showWords = v; requestLayout(); build(); invalidate(); } }
    public void setNumpad(int m) { if (numpad != m) { numpad = m; restyle(); requestLayout(); build(); invalidate(); } }

 // масштаб знаков на планшете
    /** Размер значков на клавишах: на планшете крупнее, на цифровой клавиатуре (PIN, телефон) тоже. */
    private float uu() { return den * ts; }
    /** Высота с учётом цифровой клавиатуры: у неё клавиши крупнее (но блок всё равно не на всю ширину). */
    private float hsEff() { return hs; }

    @Override public void setTextScale(float s) { if (ts != s) { ts = s; restyle(); invalidate(); } }

    private void restyle() {
        boolean np = numpad != 0;
        setup(pLetter, 21 * ts * fs * (np ? 1.12f : 1f)); setup(pMid, 15 * ts * fs * (np ? 1.08f : 1f)); setup(pPop, 32 * ts * fs);
        if (ts > 1f || np) { Typeface md = Typeface.create("sans-serif-medium", Typeface.NORMAL); pLetter.setTypeface(md); pMid.setTypeface(md); }
        setup(pHint, 10 * ts * fs); offLetter = off(pLetter); offMid = off(pMid); offPop = off(pPop); offHint = off(pHint);
    }
    @Override public void setHints(boolean num, boolean sym) { if (hintNum != num || hintSym != sym) { hintNum = num; hintSym = sym; restyle(); build(); invalidate(); } }
    private boolean bb() { return bottomBar && numpad == 0 && !tablet; } // нижняя панель при цифровой клавиатуре не нужна
    public void setStripHidden(boolean h) { if (stripHidden != h) { stripHidden = h; invalidate(); } }
    private boolean micOn = true, incoAllowed, incoActive;
    private int botPad;
    private float fs = 1f;
    @Override public void setBottomPad(int dp) { if (botPad != dp) { botPad = dp; requestLayout(); build(); invalidate(); } }
    @Override public void setFontScale(float f) { if (fs != f) { fs = f; restyle(); invalidate(); } }
    @Override public void setMic(boolean on) { if (micOn != on) { micOn = on; build(); invalidate(); } }
    @Override public void setIncognito(boolean allowed, boolean active) { if (incoAllowed != allowed || incoActive != active) { incoAllowed = allowed; incoActive = active; invalidate(); } }
    @Override public void setPopup(boolean on) { popupOn = on; }
    @Override public void setLine(boolean on) { lineOn = on; }
    @Override public void setLogo(boolean on) { }
    private int micPos;
    @Override public void setMicPos(int p) { if (micPos != p) { micPos = p; build(); invalidate(); } }
    /** Микрофон справа на панели подсказок: всегда, если нет нижней панели, иначе по настройке «Кнопка микрофона». */
    private boolean micTop() { return micOn && showWords && (!bb() || micPos != 1); }
    private float sx1() { return W - d(MARGIN) - (micTop() ? d(34) : 0f); } // справа от подсказок — микрофон
    public void setSwapIcons(boolean on) { if (swapIcons != on) { swapIcons = on; build(); invalidate(); } }
    public void setTablet(boolean on) { if (tablet != on) { tablet = on; requestLayout(); build(); invalidate(); } }
    public void setRound(int v) { if (round != v) { round = v; invalidate(); } }
    public int[] strip() { return new int[]{(int) sx0(), (int) (d(MARGIN) + (micTop() ? d(34) : 0f)), (int) (d(SUG_ZONE) - d(GAP_Y) / 2f)}; }
    private float keyR(Key k) { // закругление: цифровая клавиатура — «таблетки», у остальных прежний радиус; ползунок масштабирует
        float base = numpad != 0 ? (k.b - k.t) * 0.5f : d(KEY_R);
        return Shape.radius(base, Math.min(k.r - k.l, k.b - k.t) * 0.5f, round);
    }
    public void setLangKey(boolean on) { if (langKey != on) { langKey = on; build(); invalidate(); } } // отдельная клавиша языка рядом с эмодзи
    public void setSplit(boolean on) { if (split != on) { split = on; if (getWidth() == 0 || !isShown()) spP = on ? 1f : 0f; build(); invalidate(); } } // анимация — только когда клавиатура на экране
    public void setOneHand(int m) { if (oneHand != m) { oneHand = m; invalidate(); } }
    public void setBottomBar(boolean v) { if (bottomBar != v) { bottomBar = v; requestLayout(); build(); invalidate(); } }
    public void setEnterLabel(String s) {
        if (s == null ? enterLabel != null : !s.equals(enterLabel)) { enterLabel = s; invalidate(); }
    }
    public void setLayout(Layouts.L l) {
        if (lay == l) return;
        prevLay = lay; lay = l;
        if (W > 0) { langT = SystemClock.uptimeMillis(); pageP = 0f; }
        build(); invalidate();
    }
    public void setPage(int p) {
        if (page == p) return;
        page = p;
        if (W > 0) pageP = 0f;
        build(); invalidate();
    }
    public void setShift(int s) {
        if (shift == s) return;
        shift = s; shiftT = SystemClock.uptimeMillis(); invalidate();
    }
    public void setSuggestions(String a, String b, String c) {
        if (eq(sug[0], a) && eq(sug[1], b) && eq(sug[2], c)) return;
        sug[0] = a; sug[1] = b; sug[2] = c; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; sugP = 0f; invalidate();
    }
    public void playEntrance() { enterStart = SystemClock.uptimeMillis(); toolsOpen = false; toolsP = 0f; if (lis != null) lis.onToolsToggle(false); invalidate(); }

    /** Тема (цвета) изменилась. */
    public void themeChanged() { makeShader(); invalidate(); }

    public void cancelTouches() {
        for (int i = 0; i < MAXP; i++) { release(i); used[i] = false; psug[i] = -1; }
        gSlot = -1; gesturing = false; gn = 0; menuOn = false; menuSlot = -1;
        invalidate();
    }

    private void makeShader() {
        if (H == 0) return;
        pPanel.setShader(new LinearGradient(0, 0, 0, H, th.panelTop, th.panelBot, Shader.TileMode.CLAMP));
    }

    // ---------- раскладка ----------
    private void build() {
        if (W == 0) return;
        ArrayList<Key> L = new ArrayList<>(40);
        float m = d(MARGIN), gx = d(GAP_X), gy = d(GAP_Y), full = W - 2 * m;
        float top = topH(), bz = d((bb() ? BOT_ZONE : BOT_ZONE_OFF) + botPad);
        float rh = (H - top - bz - 3 * gy) / 4f;
        float y0 = top, y1 = top + (rh + gy), y2 = top + 2 * (rh + gy), y3 = top + 3 * (rh + gy);
        zoneTop = bb() ? y3 + rh + gy / 2f : Float.MAX_VALUE;
        int npm = numpad != 0 ? numpad : (page == 3 ? 5 : 0); // 5 — цифровая клавиатура, вызванная клавишей «1234»
        if (npm != 0) { // поле числа, телефона или даты: общий план цифровой клавиатуры
            KeyPlan np = KeyPlan.numpad(npm);
            float gp = d(10), nw = tablet ? Math.min(full, np.total * 1.45f * rh + (np.total - 1f) * gp) : full; // телефон: на всю ширину; планшет: блок по центру, клавиши ~1,45:1
            buildFromPlan(L, np, (W - nw) / 2f, nw, gp, new float[]{y0, y1, y2, y3}, rh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }
        if (page != 0 && !tablet) { // страницы ?123: общий план, одинаковый в обоих дизайнах
            buildFromPlan(L, KeyPlan.symbols(lay, page), m, full, gx, new float[]{y0, y1, y2, y3}, rh, 0);
            finishKeys(L, gx);
            letters = "";
            return;
        }
        if (tablet) { // планшетная раскладка: отдельный план, ряды делят высоту поровну
            KeyPlan plan = KeyPlan.tablet(lay, numRow, page);
            int n = plan.rows.size();
            float areaTop = d(showWords ? SUG_ZONE : 8), rhh = (H - bz - areaTop - (n - 1) * gy) / n;
            float[] ys = new float[n];
            for (int q = 0; q < n; q++) ys[q] = areaTop + q * (rhh + gy);
            buildFromPlan(L, plan, m, full, gx, ys, rhh, numRow ? -1 : 0);
            finishKeys(L, gx);
            return;
        }

        if (numRow && page == 0) {
            float g5 = d(5), kwn = (full - 9 * g5) / 10f;
            for (int i = 0; i < 10; i++) add(L, new Key(T_CHAR, String.valueOf((i + 1) % 10), null, 0, -1), m + i * (kwn + g5), (showWords ? d(SUG_ZONE) : 0f) + d(5), kwn, d(36));
        }

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

        // нижний ряд
        float c123 = 0, cEn = 0;
        if (bottomBar) { // 123 | пробел | знаки препинания | return, под ними — смайл/глобус и микрофон
            float avail = full - 3 * gx, tot = 118f + 232f + 62f + 94f, x = m;
            float w123 = avail * 118f / tot, wsp = avail * 232f / tot, wpu = avail * 62f / tot, wen = avail * 94f / tot;
            add(L, new Key(T_PAGE, page == 0 ? "123" : "ABC", null, page == 0 ? 1 : 0, 3), x, y3, w123, rh);
            c123 = x + w123 / 2f; x += w123 + gx;
            add(L, new Key(T_SPACE, null, null, 0, 3), x, y3, wsp, rh); x += wsp + gx;
            add(L, new Key(T_PUNCT, ".", PUNCT_MENU, 0, 3), x, y3, wpu, rh); x += wpu + gx;
            add(L, new Key(T_ENTER, null, null, 0, 3), x, y3, wen, rh);
            cEn = x + wen / 2f;
            float it = y3 + rh + d(4), ib = H - d(2), hw = d(28);
            float cA = swapIcons ? cEn : c123, cB = swapIcons ? c123 : cEn; // можно поменять местами язык/эмодзи и микрофон
            if (langKey) { // две кнопки рядом: эмодзи и язык
                float h2 = d(23), off = d(25);
                add(L, new Key(T_EMOJI, null, null, 0, 4), cA - off - h2, it, 2 * h2, ib - it);
                add(L, new Key(T_LANG, null, null, 0, 4), cA + off - h2, it, 2 * h2, ib - it);
            } else add(L, new Key(T_EMOJI, null, null, 0, 4), cA - hw, it, 2 * hw, ib - it);
            if (micOn && (micPos != 0 || !showWords)) add(L, new Key(T_MIC, null, null, 0, 4), cB - hw, it, 2 * hw, ib - it);
        } else { // нижней панели нет: смайл/глобус переезжает в ряд клавиш
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

    private int vt(int kind) {
        switch (kind) {
            case KeyPlan.SHIFT: return T_SHIFT;
            case KeyPlan.BACK: return T_BACK;
            case KeyPlan.PAGE: return T_PAGE;
            case KeyPlan.SPACE: return T_SPACE;
            case KeyPlan.ENTER: return T_ENTER;
            case KeyPlan.EMOJI: return T_EMOJI;
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

    /** Ключи -> массив; разделение; буквы для непрерывного ввода. */
    /** Пока половины разъезжаются или съезжаются (spP между 0 и 1), клавиши на кадр ставятся по доле разделения; потом координаты возвращаются. */
    private void splitFrame(boolean on) {
        float m = d(MARGIN);
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
        applyHints(0, numRow, numpad != 0);
        if (split) for (Key sk : keys) { // каждая половина сжимается к своему краю; пробел перекрывает промежуток
            if (sk.type == T_SPACE) { sk.l = Split.x(sk.l, W, d(MARGIN), false); sk.r = Split.x(sk.r, W, d(MARGIN), true); }
            else { boolean rt = (sk.l + sk.r) / 2f >= W / 2f; sk.l = Split.x(sk.l, W, d(MARGIN), rt); sk.r = Split.x(sk.r, W, d(MARGIN), rt); }
        }
        StringBuilder sb = new StringBuilder(40);
        int n = 0;
        for (Key ck : keys) if (ck.type == T_CHAR && page == 0 && n < 40 && (!tablet || (ck.row >= 0 && ck.row <= 2))) {
            sb.append(ck.label); lcx[n] = (ck.l + ck.r) / 2f; lcy[n] = (ck.t + ck.b) / 2f; n++;
            kwPitch = (ck.r - ck.l) + gx;
        }
        letters = sb.toString();
    }

    private void buildFromPlan(ArrayList<Key> L, KeyPlan plan, float x0, float full, float gx, float[] ys, float rh, int rowOff) {
        final float c = (full - (plan.total - 1f) * gx) / plan.total;
        for (int r = 0; r < plan.rows.size(); r++) {
            float x = x0;
            for (KeyPlan.Spec sp : plan.rows.get(r)) {
                float w = sp.w * c + (sp.w - 1f) * gx;
                Key k = new Key(vt(sp.kind), sp.label, sp.letter ? lay.alt(sp.label.charAt(0)) : (".".equals(sp.label) ? android.text.TextUtils.join("|", PUNCT_ITEMS) : null), sp.target, r + rowOff);
                if (sp.letter) k.upper = lay.upper(k.label);
                add(L, k, x, ys[r], w, rh);
                x += w + gx;
            }
        }
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


    /** Верхняя зона: подсказки и/или ряд цифр (каждый — своя полоса), если ничего не включено — небольшой отступ. */
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
        W = w; H = hh; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; chipDisp = null; makeShader(); build();
    }

    // ---------- анимационный такт ----------
    /** Двигает все анимируемые величины; возвращает true, если нужен ещё кадр. */
    private boolean tick(long now) {
        float dt = lastFrame == 0 ? 0f : Math.min(48f, now - lastFrame);
        boolean a = false;
        for (Key k : keys) {
            float tg = k.down ? 1f : 0f;
            if (k.p != tg) { k.p = Anim.step(k.p, tg, dt, tg > k.p ? 40f : 160f); a = true; }
        }
        if (pageP < 1f) { pageP = Anim.step(pageP, 1f, dt, 190f); a = true; }
        if (sugP < 1f) { sugP = Anim.step(sugP, 1f, dt, 150f); a = true; }
        float tgT = toolsOpen ? 1f : 0f, tgO = oneHand != 0 ? 1f : 0f, tgS = split ? 1f : 0f; // анимация меню и подсветки режимов
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
        if (clearSlot >= 0 || listening) a = true;
        if (lineOn && Trail.live(trT, gn, SystemClock.uptimeMillis())) a = true; // хвост следа тает сам, пока не исчезнет
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

    public boolean healthy() {
        return W > 0 && H > 0 && keys != null && keys.length > 0 && !Float.isNaN(keys[0].l) && !Float.isNaN(keys[keys.length - 1].r);
    }

    public void setKeyBg(boolean on) { if (keyBg != on) { keyBg = on; invalidate(); } }

    private void draw0(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean anim = tick(now);
        float rad = d(PANEL_R);
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pPanel);

        if (showWords) drawSuggestions(c);

        float pe = Anim.ease(pageP);
        boolean sa = Math.abs(spP - (split ? 1f : 0f)) > 0.002f && numpad == 0; // с первого кадра рисуем «между» состояниями: без скачка в конечное положение
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
        if (anim) postInvalidateOnAnimation();
    }

    private void drawTrail(Canvas c, long now) {
        if (!Trail.ribbon(trail, trX, trY, trT, gn, now, d(10))) return;
        pFill.setColor(Theme.fade(Color.argb(235, 0, 122, 255), 0.92f));
        c.drawPath(trail, pFill);
        c.drawCircle(trX[gn - 1], trY[gn - 1], d(5) * (1f - (now - trT[gn - 1]) / (float) Trail.LIFE), pFill); // округлая «головка» у пальца
    }

    private void drawChip(Canvas c) {
        float l = sx0(), r = sx1() - d(4), t = d(7), b = d(39), sp = Anim.ease(sugP) * (1f - toolsP), cy = (t + b) / 2f;
        pFill.setColor(Theme.fade(th.key, 0.95f * sp));
        c.drawRoundRect(l, t + (1f - sp) * d(4), r, b + (1f - sp) * d(4), d(16), d(16), pFill);
        if (clipImg != null) drawThumb(c, l + d(20), cy, sp); else Icons.clip(c, pStroke, rf, l + d(20), cy, den, Theme.fade(th.muted, sp));
        Icons.cross(c, pStroke, r - d(22), cy, den, Theme.fade(th.muted, sp));
        if (chipDisp == null) {
            String s = clipText.replace('\n', ' ');
            float maxW = r - l - d(86);
            if (pSug.measureText(s) > maxW) {
                int n = pSug.breakText(s, true, maxW - d(10), null);
                s = s.substring(0, Math.max(1, n - 1)) + "…";
            }
            chipDisp = s;
        }
        txt(c, chipDisp, (l + r) / 2f, cy + (1f - sp) * d(4), pSug, offSug, Theme.fade(th.text, sp));
    }

    private void drawMenu(Canvas c) {
        final boolean open = menuOn;
        float bp = MenuFx.body(menuP, open), sc = 0.5f + 0.5f * bp, a = Math.min(1f, menuP * 2.4f), rr = d(12);
        float px = Math.max(menuL, Math.min(menuR, menuAx)); // меню растёт из точки над нажатой клавишей
        c.save(); c.scale(sc, sc, px, menuB + d(8));
        pFill.setColor(Theme.fade(Color.argb(60, 20, 25, 50), a));
        c.drawRoundRect(menuL, menuT + d(1 + 2 * bp), menuR, menuB + d(1 + 2 * bp), rr, rr, pFill); // тень «подрастает» вместе с меню
        pFill.setColor(Theme.fade(th.pop, a));
        c.drawRoundRect(menuL, menuT, menuR, menuB, rr, rr, pFill);
        if (menuSel >= 0 && menuSel < menuItems.length) { // подсветка одна: она переезжает к выбранному пункту
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
            txt(c, menuItems[j], cx, cy, pLetter, offLetter, Theme.fade(on ? Color.WHITE : th.text, ga));
            c.restore();
        }
        c.restore();
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

    private float sx0() { return d(MARGIN) + d(34); } // слева от подсказок — кнопка меню

    private void drawSuggestions(Canvas c) {
        float bx = d(MARGIN) + d(17), by = d(22);
        if (toolsP < 1f) Icons.apps(c, pFill, rf, bx, by, den * 0.85f, Theme.fade(th.muted, 1f - toolsP));
        if (toolsP > 0f) {
            Icons.chevron(c, path, pStroke, bx, by, den, Theme.fade(th.muted, toolsP));
            MenuBar.draw(c, pStroke, pFill, rf, sx0(), sx1() - d(6), by, den, th.text, th.accent, onP, spP, toolsP, menuPressed());
        }
        if (micTop()) { // микрофон справа на панели подсказок
            float mx = W - d(MARGIN) - d(17);
            if (listening) {
                float ph = (SystemClock.uptimeMillis() % 1100) / 1100f;
                pFill.setColor(Theme.fade(th.accent, 0.30f * (1f - ph))); c.drawCircle(mx, by, d(15) + d(8) * ph, pFill);
            }
            Icons.mic(c, pStroke, rf, mx, by, den * 0.9f, listening ? th.accent : th.muted);
        }
        if (toolsP >= 1f || stripHidden) return;
        if (clipText != null) { drawChip(c); return; }
        float m = sx0(), cell = (sx1() - m) / 3f, cy = d(22), sp = Anim.ease(sugP) * (1f - toolsP);
        for (int i = 0; i < 3; i++) {
            String s = sug[i];
            if (s == null || s.isEmpty()) continue;
            float cx = m + cell * (i + 0.5f), maxW = cell - d(14);
            if (sugDisp[i] == null) { sugK[i] = Fit.k(pSug, s, maxW); sugDisp[i] = Fit.cut(pSug, s, maxW, sugK[i]); }
            float sz = pSug.getTextSize(), k = sugK[i];
            if (k < 1f) pSug.setTextSize(sz * k);
            txt(c, sugDisp[i], cx, cy + (1f - sp) * d(4), pSug, offSug * k, Theme.fade(th.text, sp));
            pSug.setTextSize(sz);
        }
        if (toolsP < 0.34f) { // разделители есть всегда, как в iOS; при открытии меню гаснут первыми
            pFill.setColor(Theme.fade(th.div, 1f - toolsP * 3f));
            for (int i = 1; i < 3; i++) {
                float x = m + cell * i;
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
        else if (k.type == T_CHAR || (k.type == T_PAGE && k.target == 3) || (!tablet && k.type == T_SHIFT && shift != 0)) { base = th.key; downC = th.keyDown; }
        else if (k.type == T_SPACE) { base = th.space; downC = th.keyDown; }
        else { base = th.spec; downC = th.specDown; }

        float sq = Shape.squeeze(round), kw2 = k.r - k.l, kh2 = k.b - k.t; // выше 50 % клавиша ещё и сжимается к кругу
        float ix = kw2 > kh2 && kw2 <= 1.6f * kh2 ? (kw2 - kh2) * sq / 2f : 0f, iy = kh2 > kw2 ? (kh2 - kw2) * sq / 2f : 0f;
        float fb = (keyBg || (k.type == T_ENTER && enterLabel != null && numpad == 0)) ? fa : fa * k.p; // без подложек клавиша видна только при нажатии
        if (fb > 0.01f) {
            pFill.setColor(Theme.fade(th.shadow, fb));
            c.drawRoundRect(k.l + ix, k.t + d(1) + iy, k.r - ix, k.b + d(1) - iy, rk, rk, pFill);
            pFill.setColor(Theme.fade(Theme.mix(base, downC, k.p), fb));
            c.drawRoundRect(k.l + ix, k.t + iy, k.r - ix, k.b - iy, rk, rk, pFill);
        }

        int tc = Theme.fade(th.text, fa);
        switch (k.type) {
            case T_CHAR:
                txt(c, (shift != 0 && page == 0) ? k.upper : k.label, cx, cy, pLetter, offLetter, tc);
                if (k.hint != null) txt(c, k.hint, cx + (k.r - k.l) * 0.28f, cy - (k.b - k.t) * 0.3f, pHint, offHint, Theme.fade(th.muted, fa));
                break;
            case T_PAGE:
                if (k.target == 3) numIcon(c, k, cx, pMid, offMid, tc); else txt(c, k.label, cx, cy, pMid, offMid, tc); break;
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
                if (incoActive) Icons.incognito(c, pStroke, k.l + d(24), cy, u * 0.8f, Theme.fade(th.muted, fa));
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

    /** Подпись на пробеле: при смене языка плавно показывает название языка, затем возвращает «пробел». */
    private void drawSpaceLabel(Canvas c, float cx, float cy, float fa, long now) {
        String cur = lay.space, prev = prevLay.space, name = lay.name;
        float t = langT == 0 ? 99999f : now - langT;
        int mc = th.muted;
        if (t >= 1250f) { txt(c, cur, cx, cy, pMid, offMid, Theme.fade(mc, fa)); return; }
        if (t < 160f) {
            float f = t / 160f;
            txt(c, prev, cx, cy, pMid, offMid, Theme.fade(mc, fa * (1f - f)));
            txt(c, name, cx, cy, pMid, offMid, Theme.fade(th.text, fa * f));
        } else if (t < 1000f) {
            txt(c, name, cx, cy, pMid, offMid, Theme.fade(th.text, fa));
        } else {
            float f = (t - 1000f) / 250f;
            txt(c, name, cx, cy, pMid, offMid, Theme.fade(th.text, fa * (1f - f)));
            txt(c, cur, cx, cy, pMid, offMid, Theme.fade(mc, fa * f));
        }
    }

    /** Пузырь в стиле iOS: широкое «тело» над клавишей, сужающееся до её ширины. Появляется и гаснет плавно. */
    private void drawPopup(Canvas c, Key k, String text, float p) {
        if (text == null || p <= 0f) return;
        float rk = d(KEY_R), a = Math.min(1f, p * 1.6f), sc = 0.9f + 0.1f * Anim.ease(p);
        float bw = Math.max(k.r - k.l + d(16), d(46)), cx = (k.l + k.r) / 2f;
        float l = Math.max(d(2), Math.min(cx - bw / 2f, W - d(2) - bw)), r = l + bw;
        float t = Math.max(d(2), k.t - d(42)), ym = Math.max(t + d(24), k.t - d(4)), rd = d(10), kl = k.l, kr = k.r, kb = k.b; // не выше вида: иначе верх миниатюры обрезается
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
        txt(c, text, (l + r) / 2f, (t + ym) / 2f, pPop, offPop, Theme.fade(th.text, a));
        c.restore();
    }

    // ---------- касания ----------
    private float sugBoundary() { return showWords ? d(SUG_ZONE) - d(GAP_Y) / 2f : 0f; }

    /** 0..2 — подсказки, 10 — кнопка меню, 20.. — пункты меню, -1 — мимо. */
    private int sugIndex(float x) {
        float x0 = sx0();
        if (x < x0) return 10;
        if (micTop() && x > sx1()) return 11;
        if (toolsOpen) { int h = MenuBar.hit(x, x0, sx1() - d(6)); return h < 0 ? -1 : 20 + h; }
        int i = (int) ((x - x0) / ((sx1() - x0) / 3f));
        return Math.max(0, Math.min(2, i));
    }

    /** Клавиша под пальцем; ниже ряда клавиш — только иконки (смайл/микрофон), посередине ничего. */
    private Key hit(float x, float y) {
        float hx = d(GAP_X) / 2f, hy = d(GAP_Y) / 2f, bd = Float.MAX_VALUE;
        Key best = null;
        boolean zone = y >= zoneTop;
        for (Key k : keys) {
            if (k.row < 0 && !tablet) continue; // у телефонной раскладки ряд цифр ищется отдельно (hitNum); у планшетной это обычный ряд
            boolean icon = k.row == 4;
            if (zone != icon) continue;
            if (zone) { if (x >= k.l && x < k.r) return k; continue; }
            if (x >= k.l - hx && x < k.r + hx && y >= k.t - hy && y < k.b + hy) return k;
            float dx = x < k.l ? k.l - x : (x > k.r ? x - k.r : 0);
            float dy = y < k.t ? k.t - y : (y > k.b ? y - k.b : 0);
            float dd = dx * dx + dy * dy;
            if (dd < bd) { bd = dd; best = k; }
        }
        return zone || (split && Math.abs(x - W / 2f) < Split.GAP * (W - 2 * d(MARGIN)) / 2f) ? null : best; // пустая середина разделённой клавиатуры не нажимает боковые клавиши
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
        if (numRow && !tablet && page == 0 && y >= digTop && y < digBot) return hitNum(x);
        if (showWords && y < digTop) return null;
        return hit(x, y);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        try { return touch0(e); } catch (RuntimeException ex) { Diag.saveOnce(getContext(), ex); return true; }
    }

    private boolean touch0(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex(); down(e.getPointerId(i), e.getX(i), e.getY(i)); break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex(); up(e.getPointerId(i), e.getX(i), e.getY(i)); break;
            }
            case MotionEvent.ACTION_CANCEL: cancelTouches(); break;
        }
        return true;
    }

    private int slotOf(int id) {
        for (int i = 0; i < MAXP; i++) if (used[i] && pid[i] == id) return i;
        return -1;
    }


    private void buzz(int c) { if (haptic) performHapticFeedback(c); }

    /** Перерисовываем только область клавиши и пузыря над ней (важно при программной отрисовке). */
    @SuppressWarnings("deprecation")
    private void invKey(Key k) {
        invalidate((int) (k.l - d(10)), (int) (k.t - d(56)), (int) (k.r + d(10)), (int) (k.b + d(6)));
    }

    private void down(int id, float x, float y) {
        if (gesturing) return;
        int s = -1, active = 0;
        for (int i = 0; i < MAXP; i++) { if (used[i]) active++; else if (s < 0) s = i; }
        if (s < 0) return;
        used[s] = true; pid[s] = id; fired[s] = false; pk[s] = null; psug[s] = -1; downX[s] = x; downY[s] = y; cursorMode[s] = false;
        if (showWords && y < sugBoundary()) { psug[s] = sugIndex(x); invalidate(); return; }
        Key k = hitAny(x, y);
        press(s, k);
        if (swipeOn && swipe != null && page == 0 && active == 0 && k != null && k.type == T_CHAR && (k.row >= 0 || tablet)) {
            gSlot = s; gKey = k; gsx = x; gsy = y; gn = 0; addPt(x, y);
        }
    }


    private int newPop(Key k, String t) {
        int f = -1;
        for (int i = 0; i < MAXPOP; i++) if (popKey[i] == null) { f = i; break; }
        if (f < 0) f = 0;
        popKey[f] = k; popTxt[f] = t; popP[f] = 0.3f; popOn[f] = true;
        return f;
    }

    private void press(int s, Key k) {
        pk[s] = k;
        if (k == null) return;
        k.down = true;
        if (k.type == T_CHAR && (k.row >= 0 || tablet)) popIdx[s] = newPop(k, (shift != 0 && page == 0) ? k.upper : k.label);
        buzz(HapticFeedbackConstants.KEYBOARD_TAP);
        if (lis != null) lis.onKeySound(soundKind(k));
        if (k.type == T_BACK) {
            if (lis != null) lis.onBack();
            repSlot = s; repCount = 0;
            h.removeCallbacks(rep); h.postDelayed(rep, 400);
        } else if (k.alt != null || k.type == T_EMOJI || (k.type == T_SPACE && incoAllowed)) {
            h.postDelayed(lp[s], k.type == T_SPACE ? 600 : 350);
        }
        invKey(k);
    }

    private static int soundKind(Key k) { return k.type == T_BACK ? KeySounds.DELETE : (k.type == T_CHAR || k.type == T_PUNCT) ? KeySounds.KEY : KeySounds.MOD; }

    private void release(int s) {
        cursorMode[s] = false;
        if (clearSlot == s) { clearSlot = -1; h.removeCallbacks(clearRun); }
        Key k = pk[s];
        if (k != null) {
            k.down = false;
            h.removeCallbacks(lp[s]);
            if (repSlot == s) { repSlot = -1; h.removeCallbacks(rep); }
            invKey(k);
        }
        if (popIdx[s] >= 0) { popOn[popIdx[s]] = false; popIdx[s] = -1; }
        pk[s] = null;
    }

    private void longPress(int s) {
        Key k = pk[s];
        if (k == null || !used[s] || lis == null) return;
        if ((k.alt != null || k.type == T_PUNCT) && popIdx[s] >= 0) { popOn[popIdx[s]] = false; popIdx[s] = -1; } // меню знаков не должно рисоваться поверх увеличенной буквы
        if (k.type == T_PUNCT && (k.label == null || ".".equals(k.label))) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); openMenu(s, k, PUNCT_ITEMS, true);
        } else if (k.type == T_SPACE) {
            if (incoAllowed && !fired[s] && !cursorMode[s]) { fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onIncognito(); }
        } else if (k.type == T_EMOJI) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onEmojiKey(true);
        } else if (k.alt != null) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS);
            openMenu(s, k, k.alt.split("\\|"), false);
        }
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
                gesturing = true; fired[s] = true; trailA = 1f; release(s);
                buzz(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            if (gesturing) { invalidate(); return; }
        }
        if (psug[s] >= 0 || fired[s]) return;
        Key nk = hitAny(x, y);
        if (nk != null && (nk.type == T_BACK || nk.type == T_EMOJI || nk.type == T_MIC)
                && (pk[s] == null || pk[s].type != nk.type)) nk = null; // соскользнуть на служебные клавиши нельзя
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
            if (y < sugBoundary() && lis != null && sugIndex(x) == psug[s]) {
                int z = psug[s];
                if (z == 10) setTools(!toolsOpen);
                else if (z == 11) lis.onMic();
                else if (z >= 20) { setTools(false); lis.onMenu(z - 20); }
                else if (clipText != null) lis.onClipTap(x > sx1() - d(4) - d(46));
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
            case T_CHAR:  lis.onChar(k.label); break;
            case T_SHIFT: lis.onShift(); break;
            case T_PAGE:  setPage(k.target); break;
            case T_SPACE: lis.onSpace(); break;
            case T_ENTER: lis.onEnter(); break;
            case T_EMOJI: lis.onEmojiKey(false); break;
            case T_LANG:  lis.onLang(); break;
            case T_TAB:   lis.onSpace(); break;
            case T_CAPS:  lis.onCaps(); break;
            case T_LEFT:  lis.onCursor(-1); break;
            case T_RIGHT: lis.onCursor(1); break;
            case T_MIC:   lis.onMic(); break;
            case T_PUNCT: lis.onChar(k.label != null ? k.label : "."); break;
            default: break; // T_BACK уже отработал при нажатии
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
        for (int i = 0; i < MAXP; i++) { pk[i] = null; used[i] = false; popIdx[i] = -1; }
        for (int i = 0; i < MAXPOP; i++) popKey[i] = null;
        repSlot = -1; lastFrame = 0;
    }
}
