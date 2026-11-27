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

abstract class BoardView extends View implements Board {

    static final int T_CHAR = 0, T_SHIFT = 1, T_BACK = 2, T_PAGE = 3, T_SPACE = 4, T_ENTER = 5, T_LANG = 9, T_TAB = 10, T_CAPS = 11, T_LEFT = 12, T_RIGHT = 13;

    static final int MAXP = 6;
    KeyboardView.Listener lis;
    boolean showWords = true;
    boolean split;
    boolean bottomBar;
    boolean langKey;
    boolean toolsOpen;
    boolean globeMode;
    boolean tablet;
    int numpad;
    int round = Shape.DEFAULT;
    int oneHand;
    boolean popupOn = true;
    boolean lineOn = true;
    boolean swapIcons;
    boolean keyBg = true;
    String clipText;
    String enterLabel;
    float menuL;
    float menuCell;
    float menuP;
    float menuB;
    float menuR;
    float menuT;
    boolean swipeOn;
    boolean haptic = true;
    float kwPitch;
    long lastFrame;
    long langT;
    float pageP = 1f;
    float trailA = 0f;
    float sugP = 1f;
    final boolean[] used = new boolean[MAXP];
    final boolean[] fired = new boolean[MAXP];
    final int[] psug = new int[MAXP];
    final int[] pid = new int[MAXP];
    final Key[] pk = new Key[MAXP];
    final Runnable[] lp = new Runnable[MAXP];
    int repSlot = -1;
    int repCount = 0;
    final float[] downX = new float[MAXP];
    final float[] lastCx = new float[MAXP];
    final float[] downY = new float[MAXP];
    final float[] lastCy = new float[MAXP];
    final boolean[] cursorMode = new boolean[MAXP];
    final Path trail = new Path();
    final Path path = new Path();
    boolean incoAllowed;
    boolean incoActive;
    boolean micOn = true;
    int botPad;
    float fs = 1f;
    int micPos;
    boolean slowTick;
    boolean numRow;

    final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG), pStroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            pLetter = new Paint(Paint.ANTI_ALIAS_FLAG), pMid = new Paint(Paint.ANTI_ALIAS_FLAG),
            pSug = new Paint(Paint.ANTI_ALIAS_FLAG), pPop = new Paint(Paint.ANTI_ALIAS_FLAG), pTrail = new Paint(Paint.ANTI_ALIAS_FLAG);

    BoardView(Context c) { super(c); }

    static final class Key {
        final int type, target, row; String label, alt, upper, hint;
        float l, t, r, b, rad, p, ol, orr, sl, sr; boolean down;
        Key(int type, String label, String alt, int target, int row) {
            this.type = type; this.label = label; this.alt = alt; this.target = target; this.row = row;
            this.upper = (type == T_CHAR && label != null) ? label.toUpperCase(Locale.ROOT) : label;
        }
    }

    Theme th;

    float den;

    int W, H;

    Key[] keys = new Key[0];

    final Paint pHint = new Paint(Paint.ANTI_ALIAS_FLAG);
    float offHint;
    boolean hintNum, hintSym;

    void applyHints(int firstRow, boolean numRow, boolean pad) {
        if ((!hintNum && !hintSym) || pad || page != 0) return;
        int[] n = new int[3];
        for (Key k : keys) {
            if (k.type != T_CHAR || k.upper == null) continue;
            int rr = k.row - firstRow;
            if (rr < 0 || rr > 2) continue;
            int pos = n[rr]++;
            String h = KeyPlan.hint(rr, pos, numRow, hintNum, hintSym);
            if (h == null) continue;
            String extra = rr == 0 && !numRow && hintNum && hintSym ? KeyPlan.topSym(pos) : null;
            k.hint = h;
            k.alt = (extra != null ? h + "|" + extra : h) + (k.alt == null ? "" : "|" + k.alt);
        }
    }

    int page = 0, shift = 0;

    Layouts.L lay = Layouts.EN, prevLay = Layouts.EN;

    float toolsP, onP, spP, spV;

    int failN;

    final float[] sugK = {1f, 1f, 1f};

    boolean stripHidden;

    static final String PUNCT_MENU = ",!?:;'\"-()";

    boolean menuOn;

    int menuSlot = -1, menuSel = -1;

    final String[] sug = new String[3], sugDisp = new String[3];
    final float[] sugE = new float[4];
    boolean sugFit;
    float sugX0, sugX1;

    void fitSug(Paint p, float x0, float x1, float pad) {
        if (sugFit && sugX0 == x0 && sugX1 == x1) return;
        float e = (x1 - x0) / 3f, floor = e * 0.5f, deficit = 0f, spare = 0f;
        float[] need = new float[3], w = {e, e, e};
        for (int i = 0; i < 3; i++) {
            String s = sug[i];
            need[i] = s == null || s.isEmpty() ? 0f : p.measureText(s) + pad;
            if (need[i] > e) deficit += need[i] - e; else spare += e - Math.max(need[i], floor);
        }
        if (deficit > 0f && spare > 0f) {
            float give = Math.min(deficit, spare);
            for (int i = 0; i < 3; i++) {
                if (need[i] > e) w[i] = e + (need[i] - e) * give / deficit;
                else w[i] = e - (e - Math.max(need[i], floor)) * give / spare;
            }
        }
        sugE[0] = x0; sugE[1] = x0 + w[0]; sugE[2] = sugE[1] + w[1]; sugE[3] = x1;
        for (int i = 0; i < 3; i++) {
            String s = sug[i];
            if (s == null || s.isEmpty()) { sugDisp[i] = null; sugK[i] = 1f; continue; }
            float maxW = w[i] - pad;
            sugK[i] = Fit.k(p, s, maxW);
            sugDisp[i] = Fit.cut(p, s, maxW, sugK[i]);
        }
        sugFit = true; sugX0 = x0; sugX1 = x1;
    }

    int sugAt(float x, float x0, float x1) {
        if (!sugFit) return Math.max(0, Math.min(2, (int) ((x - x0) / ((x1 - x0) / 3f))));
        return x < sugE[1] ? 0 : x < sugE[2] ? 1 : 2;
    }

    SwipeDecoder swipe;

    String letters = "";

    final float[] lcx = new float[40], lcy = new float[40];

    private final char[] tcCh = new char[4];
    private final float[] tcD2 = new float[4];
    int tcN;

    void recordTouch(float x, float y, float px) {
        tcN = 0;
        int n = letters.length();
        if (page != 0 || n < 2 || px <= 0f) return;
        float py = 0f;
        for (int i = 1; i < n; i++) if (Math.abs(lcy[i] - lcy[0]) > 1f) { py = Math.abs(lcy[i] - lcy[0]); break; }
        if (py <= 0f) return;
        for (int i = 0; i < n; i++) {
            float dx = (x - lcx[i]) / px, dy = (y - lcy[i]) / py, d = dx * dx + dy * dy;
            if (tcN == 4 && d >= tcD2[3]) continue;
            int p = tcN < 4 ? tcN : 3;
            while (p > 0 && tcD2[p - 1] > d) { tcD2[p] = tcD2[p - 1]; tcCh[p] = tcCh[p - 1]; p--; }
            tcD2[p] = d; tcCh[p] = letters.charAt(i);
            if (tcN < 4) tcN++;
        }
    }

    @Override public int lastTouch(char[] ch, float[] d2) {
        int n = tcN;
        for (int i = 0; i < n; i++) { ch[i] = tcCh[i]; d2[i] = tcD2[i]; }
        return n;
    }

    final Handler h = new Handler(Looper.getMainLooper());

    float hs = 1f;

    boolean spaceCursor = true, clearAll = true, listening, spaceLang, langVert;
    int langDist = 40;

    int clearHold = 600, clearSlot = -1;

    long clearStart;

    int gSlot = -1, gn;

    boolean gesturing;

    Key gKey;

    float gsx, gsy;

    final float[] trX = new float[512], trY = new float[512];

    final long[] trT = new long[512];

float offLetter, offMid, offSug, offPop;

    final RectF rf = new RectF();

    void setup(Paint p, float sp) {
        p.setTextSize(d(sp)); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.DEFAULT);
    }

    static float off(Paint p) { Paint.FontMetrics f = p.getFontMetrics(); return -(f.ascent + f.descent) / 2f; }

    float d(float v) { return v * den; }

    android.graphics.Bitmap clipImg;

    final android.graphics.Rect clipSrc = new android.graphics.Rect();

    @Override public void setClipImage(android.graphics.Bitmap b) {
        if (clipImg == b) return;
        clipImg = b;
        if (b != null) { int s = Math.min(b.getWidth(), b.getHeight()); clipSrc.set((b.getWidth() - s) / 2, (b.getHeight() - s) / 2, (b.getWidth() + s) / 2, (b.getHeight() + s) / 2); }
        invalidate();
    }

    float ts = 1f;

    static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    void add(ArrayList<Key> L, Key k, float x, float y, float w, float hh) {
        k.l = x; k.t = y; k.r = x + w; k.b = y + hh; L.add(k);
    }

    void drawLetter(Canvas c, Paint pLetter, Key k, float cx, float cy, int color, int hintColor) {
        String s = (shift != 0 && page == 0) ? k.upper : k.label;
        if (k.hint == null) { txt(c, s, cx, cy, pLetter, offLetter, color); return; }
        float w = k.r - k.l, h = k.b - k.t, lx = cx - w * 0.035f, ly = cy + h * 0.08f;
        c.save();
        c.scale(0.9f, 0.9f, lx, ly);
        txt(c, s, lx, ly, pLetter, offLetter, color);
        c.restore();
        txt(c, k.hint, k.r - w * 0.2f, k.t + h * 0.2f, pHint, offHint, hintColor);
    }

    void txt(Canvas c, String s, float cx, float cy, Paint p, float off, int color) {
        p.setColor(color); c.drawText(s, cx, cy + off, p);
    }

    static final String[] PUNCT_ITEMS = {",", "!", "?", ":", ";", "'", "\"", "-", "(", ")"};

    String[] menuItems = PUNCT_ITEMS;

    int menuCols = 5;

    String undoLabel;
    boolean undoRedo, menuSwap;
    float menuCellH, menuAx;
    final MenuFx fx = new MenuFx();

    @Override public View view() { return this; }

    @Override public void setUndo(String label, boolean redo) {
        if (java.util.Objects.equals(undoLabel, label) && undoRedo == redo) return;
        undoLabel = label; undoRedo = redo;
        invalidate();
    }

    @Override public void setMenuSwap(boolean on) { if (menuSwap != on) { menuSwap = on; invalidate(); } }

    long evT;

    int swGen;

    private static java.util.concurrent.ThreadPoolExecutor swEx;

    private static synchronized java.util.concurrent.Executor swExec() {
        if (swEx == null) {
            swEx = new java.util.concurrent.ThreadPoolExecutor(1, 1, 5L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(4), new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "lb-swipe"); t.setPriority(Thread.NORM_PRIORITY - 1); t.setDaemon(true); return t; }
            });
            swEx.allowCoreThreadTimeOut(true);
        }
        return swEx;
    }

    void startSwipe() {
        final SwipeDecoder sd = swipe;
        if (sd == null || lis == null || gn < 2) return;
        final boolean r = lay.ru;
        final Lexicon lex = Lexicon.peek(lay.id);
        sd.setBias(ox(), oy());
        sd.setContractions("en".equals(lay.id));
        final String lt = letters;
        final int nl = lt.length(), n = gn, gen = swGen;
        final float pitch = kwPitch;
        final float[] cx = java.util.Arrays.copyOf(lcx, nl), cy = java.util.Arrays.copyOf(lcy, nl);
        final float[] px = java.util.Arrays.copyOf(trX, n), py = java.util.Arrays.copyOf(trY, n);
        final long[] pt = java.util.Arrays.copyOf(trT, n);
        try { swExec().execute(new Runnable() { @Override public void run() {
            final String[] out = new String[3];
            int c = 0;
            try { c = sd.decode(r, lex, lt, cx, cy, pitch, px, py, pt, n, out); }
            catch (RuntimeException ex) { Diag.saveOnce(getContext(), ex); }
            final int cc = c;
            if (cc > 0) h.post(new Runnable() { @Override public void run() { if (gen == swGen && lis != null) lis.onSwipe(out, cc); } });
        } }); } catch (java.util.concurrent.RejectedExecutionException ignored) { }
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        swGen++;
        h.removeCallbacksAndMessages(null);
        for (int i = 0; i < MAXP; i++) { pk[i] = null; used[i] = false; }
        clearPops();
        repSlot = -1; lastFrame = 0;
    }

    void onSized() { }

    boolean tickExtra(long now) { return false; }

    void keyAdded(Key k, float w, float hh) { }

    void planAdded(Key k, KeyPlan plan, KeyPlan.Spec sp, float w, float hh) { }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        W = w; H = hh; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; sugFit = false; clipDisp = null;
        onSized();
        build();
    }

    void invKey(Key k) {
        invalidate((int) (k.l - d(10)), (int) (k.t - popRise() - d(10)), (int) (k.r + d(10)), (int) (k.b + d(6)));
    }

    void row(ArrayList<Key> L, String chars, float x0, float y, float kw, float rh, float gx, boolean letters, int rowIdx) {
        for (int i = 0; i < chars.length(); i++) {
            char ch = chars.charAt(i);
            Key k = new Key(T_CHAR, String.valueOf(ch), altFor(ch, letters), 0, rowIdx);
            if (letters) k.upper = lay.upper(k.label);
            add(L, k, x0 + i * (kw + gx), y, kw, rh);
            keyAdded(k, kw, rh);
        }
    }

    void buildFromPlan(ArrayList<Key> L, KeyPlan plan, float x0, float full, float gx, float[] ys, int yOff, float rh, int rowOff) {
        final float c = (full - (plan.total - 1f) * gx) / plan.total;
        for (int r = 0; r < plan.rows.size(); r++) {
            float x = x0;
            for (KeyPlan.Spec sp : plan.rows.get(r)) {
                float w = sp.w * c + (sp.w - 1f) * gx;
                Key k = planKey(sp, r + rowOff);
                if (sp.letter) k.upper = lay.upper(k.label);
                add(L, k, x, ys[r + yOff], w, rh);
                planAdded(k, plan, sp, w, rh);
                x += w + gx;
            }
        }
    }

    boolean tick(long now) {
        float dt = lastFrame == 0 ? 0f : Math.min(48f, now - lastFrame);
        boolean a = false;
        for (Key k : keys) {
            float tg = k.down ? 1f : 0f;
            if (k.p != tg) { k.p = Anim.step(k.p, tg, dt, tg > k.p ? 40f : pressOutMs()); a = true; }
        }
        if (pageP < 1f) { pageP = Anim.step(pageP, 1f, dt, pageMs()); a = true; }
        if (sugP < 1f) { sugP = Anim.step(sugP, 1f, dt, sugMs()); a = true; }
        float tgT = toolsOpen ? 1f : 0f, tgO = oneHand != 0 ? 1f : 0f, tgS = split ? 1f : 0f;
        if (toolsP != tgT) { toolsP = Anim.step(toolsP, tgT, dt, 230f); a = true; }
        if (onP != tgO) { onP = Anim.step(onP, tgO, dt, 220f); a = true; }
        if (spP != tgS || spV != 0f) { spP = Anim.spring(spP, spV, tgS, dt, 240f, 0.84f); spV = Anim.sv; a = true; }
        if (tickPops(dt)) a = true;
        if (menuOn) { menuP = Anim.step(menuP, 1f, dt, MenuFx.OPEN); a = true; }
        else if (menuP > 0f) { menuP = Anim.step(menuP, 0f, dt, MenuFx.CLOSE); a = true; }
        if (menuP > 0f && menuItems != null && menuSel >= 0 && menuSel < menuItems.length)
            a |= fx.step(dt, menuOn, menuSel, menuR - (menuSel % menuCols + 0.5f) * menuCell, menuB - (menuSel / menuCols + 0.5f) * menuCellH);
        else a |= fx.step(dt, menuOn, -1, 0f, 0f);
        if (clearSlot >= 0) a = true; else if (listening) slowTick = true;
        if (lineOn && Trail.live(trT, gn, SystemClock.uptimeMillis())) a = true;
        if (langT != 0) { if (now - langT < langMs()) a = true; else langT = 0; }
        if (tickExtra(now)) a = true;
        lastFrame = a ? now : 0;
        return a;
    }

    void addPt(float x, float y) {
        if (gn > 0) {
            float dx = x - trX[gn - 1], dy = y - trY[gn - 1], m = d(2.5f);
            if (dx * dx + dy * dy < m * m) return;
        }
        if (gn == trX.length) {
            int half = gn / 2;
            for (int i = 0; i < half; i++) { trX[i] = trX[2 * i]; trY[i] = trY[2 * i]; trT[i] = trT[2 * i]; }
            gn = half;
        }
        trX[gn] = x; trY[gn] = y; trT[gn] = evT != 0L ? evT : SystemClock.uptimeMillis(); gn++;
    }

    public void setSpaceCursor(boolean on) { spaceCursor = on; }

    @Override public void setSpaceLang(boolean on, boolean vertical, int distDp) { spaceLang = on; langVert = vertical; langDist = distDp; }

    void spaceLangMove(int s, float x, float y) {
        if (lis == null) return;
        float dx = x - downX[s], dy = downY[s] - y;
        float along = langVert ? dy : dx, across = langVert ? dx : dy;
        if (Math.abs(along) < d(langDist) || Math.abs(along) < 1.5f * Math.abs(across)) return;
        fired[s] = true;
        downX[s] = x; downY[s] = y;
        h.removeCallbacks(lp[s]);
        buzz(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        lis.onLangStep(along > 0 ? 1 : -1);
        invalidate();
    }

    public void setClearAll(boolean on, int holdMs) { clearAll = on; clearHold = holdMs; }

    public void setListening(boolean on) { if (listening != on) { listening = on; micBoxOn = false; invalidate(); } }

    private final int[] micBox = new int[4];
    private boolean micBoxOn;

    void setMicBox(float cx, float cy, float r) {
        micBox[0] = (int) (cx - r); micBox[1] = (int) (cy - r); micBox[2] = (int) (cx + r) + 1; micBox[3] = (int) (cy + r) + 1;
        micBoxOn = true;
    }

    void invalidateSlow() {
        if (micBoxOn) postInvalidateDelayed(45, micBox[0], micBox[1], micBox[2], micBox[3]); else postInvalidateDelayed(45);
    }

    static final int T_EMOJI = 6, T_MIC = 7, T_PUNCT = 8;

    final Runnable rep = new Runnable() {
        @Override public void run() {
            if (repSlot < 0 || lis == null) return;
            lis.onBack();
            lis.onKeySound(KeySounds.DELETE);
            repCount++;
            h.postDelayed(this, repCount > 12 ? 35 : 55);
        }
    };

    final Runnable clearRun = new Runnable() {
        @Override public void run() {
            int s = clearSlot;
            if (s < 0 || !used[s] || lis == null) return;
            fired[s] = true; clearSlot = -1;
            buzz(HapticFeedbackConstants.LONG_PRESS);
            lis.onClearAll();
            invalidate();
        }
    };

    abstract float stripBottomY();
    abstract float popRise();
    abstract float pressOutMs();
    abstract float pageMs();
    abstract float sugMs();
    abstract float langMs();
    abstract boolean popKeyOk(Key k);
    abstract void drawPopup(Canvas c, Key k, String text, float p);
    abstract int stripZoneAt(float x);
    abstract Key hitAt(float x, float y);
    abstract Key hitKey(float x, float y);
    abstract boolean letterRow(Key k);
    abstract int hintRow();
    abstract float chipTapX();
    abstract float sx1();

    static final int MAXPOP = 8;

    final int[] popIdx = new int[MAXP];

    { java.util.Arrays.fill(popIdx, -1); }
    final Key[] popKey = new Key[MAXPOP];
    final String[] popTxt = new String[MAXPOP];
    final float[] popP = new float[MAXPOP];
    final boolean[] popOn = new boolean[MAXPOP];

    float popFrom() { return 0.3f; }

    float popOutMs() { return 110f; }

    private int newPop(Key k, String t) {
        int f = -1;
        for (int i = 0; i < MAXPOP; i++) if (popKey[i] == null) { f = i; break; }
        if (f < 0) f = 0;
        popKey[f] = k; popTxt[f] = t; popP[f] = popFrom(); popOn[f] = true;
        return f;
    }

    private void endPop(int s) {
        if (popIdx[s] >= 0) { popOn[popIdx[s]] = false; popIdx[s] = -1; }
    }

    void onKeyPressed(int s, Key k) {
        if (k.type == T_CHAR && popKeyOk(k)) popIdx[s] = newPop(k, (shift != 0 && page == 0) ? k.upper : k.label);
    }

    void onKeyReleased(int s) { endPop(s); }

    void onKeyLong(int s, Key k) {
        if (k.alt != null || k.type == T_PUNCT) endPop(s);
    }

    boolean tickPops(float dt) {
        boolean a = false;
        for (int i = 0; i < MAXPOP; i++) {
            if (popKey[i] == null) continue;
            a = true;
            if (popOn[i]) popP[i] = Anim.step(popP[i], 1f, dt, 70f);
            else { popP[i] = Anim.step(popP[i], 0f, dt, popOutMs()); if (popP[i] <= 0f) popKey[i] = null; }
        }
        return a;
    }

    void clearPops() {
        for (int i = 0; i < MAXP; i++) popIdx[i] = -1;
        for (int i = 0; i < MAXPOP; i++) popKey[i] = null;
    }

    @Override public void drawPops(Canvas c) {
        if (popupOn) for (int i = 0; i < MAXPOP; i++) if (popKey[i] != null) drawPopup(c, popKey[i], popTxt[i], popP[i]);
    }

    View overlay;

    @Override public void setOverlay(View v) { overlay = v; }

    boolean blockSlide(Key nk, Key cur) { return nk.type == T_BACK && (cur == null || cur.type != T_BACK); }

    void down(int id, float x, float y) {
        if (gesturing) return;
        int s = -1, active = 0;
        for (int i = 0; i < MAXP; i++) { if (used[i]) active++; else if (s < 0) s = i; }
        if (s < 0) return;
        used[s] = true; pid[s] = id; fired[s] = false; pk[s] = null; psug[s] = -1; downX[s] = x; downY[s] = y; cursorMode[s] = false;
        if (y < stripBottomY()) { psug[s] = stripZoneAt(x); invalidate(); return; }
        Key k = hitAt(x - ox(), y - oy());
        press(s, k);
        if (swipeOn && swipe != null && page == 0 && active == 0 && k != null && k.type == T_CHAR && letterRow(k)) {
            gSlot = s; gKey = k; gsx = x; gsy = y; gn = 0; addPt(x, y);
        }
    }

    void press(int s, Key k) {
        pk[s] = k;
        if (k == null) return;
        k.down = true;
        onKeyPressed(s, k);
        buzz(HapticFeedbackConstants.KEYBOARD_TAP);
        if (lis != null) lis.onKeySound(soundKind(k));
        if (k.type == T_BACK) {
            if (lis != null) lis.onBack();
            repSlot = s; repCount = 0; h.removeCallbacks(rep); h.postDelayed(rep, 400);
        } else if (k.alt != null || k.type == T_EMOJI || k.type == T_ENTER || (k.type == T_SPACE && incoAllowed)) h.postDelayed(lp[s], k.type == T_SPACE || k.type == T_ENTER ? 600 : 350);
        invKey(k);
    }

    void release(int s) {
        cursorMode[s] = false;
        if (clearSlot == s) { clearSlot = -1; h.removeCallbacks(clearRun); }
        Key k = pk[s];
        if (k != null) {
            k.down = false; h.removeCallbacks(lp[s]);
            if (repSlot == s) { repSlot = -1; h.removeCallbacks(rep); }
            invKey(k);
        }
        onKeyReleased(s);
        pk[s] = null;
    }

    static int soundKind(Key k) {
        return k.type == T_BACK ? KeySounds.DELETE : (k.type == T_CHAR || k.type == T_PUNCT || (k.type == T_EMOJI && k.label != null)) ? KeySounds.KEY : KeySounds.MOD;
    }

    void longPress(int s) {
        Key k = pk[s];
        if (k == null || !used[s] || lis == null) return;
        onKeyLong(s, k);
        if ((k.type == T_PUNCT && (k.label == null || ".".equals(k.label))) || (k.type == T_CHAR && ".".equals(k.label))) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS);
            openMenu(s, k, PUNCT_ITEMS, true);
        } else if (k.type == T_SPACE) {
            if (incoAllowed && !fired[s] && !cursorMode[s]) { fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onIncognito(); }
        } else if (k.type == T_ENTER) {
            fired[s] = true; buzz(HapticFeedbackConstants.LONG_PRESS); lis.onOpenSettings();
        } else if (k.type == T_EMOJI) {
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
        if (cur != null && cur.type == T_BACK && delSlideOn && lis != null && (delSlot == s || (!fired[s] && clearSlot != s && downX[s] - x > d(30) && Math.abs(y - downY[s]) < d(30)))) {
            if (delSlot != s) { delSlot = s; fired[s] = true; h.removeCallbacks(rep); repSlot = -1; buzz(HapticFeedbackConstants.KEYBOARD_TAP); }
            int wds = downX[s] - x > d(30) ? (int) ((downX[s] - x - d(30)) / d(34)) + 1 : 0;
            if (wds != delWords) { delWords = wds; lis.onDelSel(wds); }
            return;
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
            if (!gesturing && Anim.sq(x - gsx) + Anim.sq(y - gsy) > Anim.sq(d(26)) && hitKey(x, y) != gKey) {
                gesturing = true; fired[s] = true; trailA = 1f; release(s); buzz(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            if (gesturing) { invalidate(); return; }
        }
        if (psug[s] >= 0 || fired[s] || (cur != null && cur.type == T_BACK && delSlideOn)) return;
        Key nk = hitAt(x - ox(), y - oy());
        if (nk != null && blockSlide(nk, pk[s])) nk = null;
        if (nk != pk[s]) { release(s); press(s, nk); }
    }

    void up(int id, float x, float y) {
        int s = slotOf(id);
        if (s < 0) return;
        if (delSlot == s) {
            int w = delWords;
            delSlot = -1; delWords = 0;
            if (lis != null) { if (w > 0) lis.onDelCommit(); else lis.onDelSel(0); }
            release(s); used[s] = false; psug[s] = -1; invalidate();
            return;
        }
        if (menuOn && s == menuSlot) {
            if (menuSel >= 0 && lis != null) lis.onChar(menuItems[menuSel]);
            menuOn = false; menuSlot = -1; release(s); used[s] = false; psug[s] = -1; invalidate(); return;
        }
        if (s == gSlot) {
            boolean was = gesturing;
            if (was) addPt(x, y);
            if (was) startSwipe();
            gSlot = -1; gesturing = false; gKey = null;
            if (was) { release(s); used[s] = false; psug[s] = -1; invalidate(); return; }
        }
        if (psug[s] >= 0) {
            if (y < stripBottomY() && lis != null && stripZoneAt(x) == psug[s]) {
                int z = psug[s];
                if (z == 10) setTools(!toolsOpen);
                else if (z == 11) { if (micTop()) lis.onMic(); }
                else if (z >= 20) { setTools(false); lis.onMenu(MenuBar.id(z - 20)); }
                else if (clipText != null) lis.onClipTap(x > chipTapX());
                else if (undoLabel != null) lis.onUndo();
                else lis.onSuggest(z);
            }
        } else {
            Key k = pk[s];
            if (k != null && !fired[s]) {
                if (k.type == T_CHAR) { recordTouch(x - ox(), y - oy(), kwPitch); learnTouch(k, x, y); }
                commit(k);
                tcN = 0;
            }
        }
        release(s); used[s] = false; psug[s] = -1;
        invalidate();
    }

    void commit(Key k) {
        if (lis == null) return;
        switch (k.type) {
            case T_CHAR: lis.onChar(k.label); break;
            case T_SHIFT: lis.onShift(); break;
            case T_PAGE: setPage(k.target); break;
            case T_SPACE: lis.onSpace(); break;
            case T_ENTER: lis.onEnter(); break;
            case T_EMOJI: if (commaKey) lis.onChar(","); else lis.onEmojiKey(false); break;
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

    int vt(int kind) {
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

String clipDisp;
    long shiftT;

    abstract float edge();

    float letterPt() { return 21f; }

    @Override public void setListener(KeyboardView.Listener l) { lis = l; }

    @Override public void setClip(String s) { if (!eq(clipText, s)) { clipText = s; clipDisp = null; sugP = 0f; invalidate(); } }

    @Override public void setEnterLabel(String s) { if (!eq(enterLabel, s)) { enterLabel = s; invalidate(); } }

    @Override public void setShift(int s) {
        if (shift == s) return;
        shift = s; shiftT = SystemClock.uptimeMillis(); invalidate();
    }

    @Override public void setPage(int p) {
        p = Math.max(0, Math.min(3, p));
        if (page == p) return;
        page = p;
        if (W > 0) pageP = 0f;
        build(); invalidate();
    }

    void restyle() {
        boolean np = numpad != 0;
        setup(pLetter, letterPt() * ts * fs * (np ? 1.12f : 1f)); setup(pMid, 15 * ts * fs * (np ? 1.08f : 1f)); setup(pPop, 32 * ts * fs);
        if (ts > 1f || np) { Typeface md = Typeface.create("sans-serif-medium", Typeface.NORMAL); pLetter.setTypeface(md); pMid.setTypeface(md); }
        setup(pHint, 10 * ts * fs);
        if (bold) { pLetter.setTypeface(Typeface.DEFAULT_BOLD); pMid.setTypeface(Typeface.DEFAULT_BOLD); pPop.setTypeface(Typeface.DEFAULT_BOLD); pHint.setTypeface(Typeface.DEFAULT_BOLD); }
        offLetter = off(pLetter); offMid = off(pMid); offPop = off(pPop); offHint = off(pHint);
    }

    void splitFrame(boolean on) {
        float m = edge();
        for (Key k : keys) {
            if (!on) { k.l = k.sl; k.r = k.sr; continue; }
            k.sl = k.l; k.sr = k.r;
            if (k.type == T_SPACE) { k.l = Split.xp(k.ol, W, m, false, spP); k.r = Split.xp(k.orr, W, m, true, spP); }
            else { boolean rt = (k.ol + k.orr) / 2f >= W / 2f; k.l = Split.xp(k.ol, W, m, rt, spP); k.r = Split.xp(k.orr, W, m, rt, spP); }
        }
    }

    Key planKey(KeyPlan.Spec sp, int row) {
        String alt = ".".equals(sp.label) ? android.text.TextUtils.join("|", PUNCT_ITEMS) : sp.label == null || sp.label.length() != 1 ? null : sp.letter ? lay.alt(sp.label.charAt(0)) : KeyPlan.symAlt(sp.label.charAt(0));
        return new Key(vt(sp.kind), sp.label, alt, sp.target, row);
    }

    String altFor(char ch, boolean letters) { return letters ? lay.alt(ch) : KeyPlan.symAlt(ch); }

    void onKeysReady() { }

    void onEntrance() { }

    @Override public void playEntrance() {
        onEntrance();
        boolean was = toolsOpen || toolsP != 0f;
        toolsOpen = false; toolsP = 0f;
        if (lis != null) lis.onToolsToggle(false);
        if (was) invalidate();
    }

    void finishKeys(ArrayList<Key> L, float gx) {
        keys = L.toArray(new Key[0]);
        for (Key ok : keys) { ok.ol = ok.l; ok.orr = ok.r; if (ok.type == T_EMOJI) ok.label = commaKey ? "," : null; }
        applyHints(hintRow(), numRow, numpad != 0);
        onKeysReady();
        if (split) {
            float e = edge();
            for (Key sk : keys) {
                if (sk.type == T_SPACE) { sk.l = Split.x(sk.l, W, e, false); sk.r = Split.x(sk.r, W, e, true); }
                else { boolean rt = (sk.l + sk.r) / 2f >= W / 2f; sk.l = Split.x(sk.l, W, e, rt); sk.r = Split.x(sk.r, W, e, rt); }
            }
        }
        StringBuilder sb = new StringBuilder(40);
        int n = 0;
        for (Key ck : keys) if (ck.type == T_CHAR && letterRow(ck) && page == 0 && n < 40) {
            sb.append(ck.label); lcx[n] = (ck.l + ck.r) / 2f; lcy[n] = (ck.t + ck.b) / 2f; n++;
            keyW = ck.r - ck.l; keyH = ck.b - ck.t;
            kwPitch = (ck.r - ck.l) + gx;
        }
        letters = sb.toString();
    }

    abstract void build();
    abstract void draw0(Canvas c);

    boolean bb() { return bottomBar && numpad == 0 && !tablet; }

    void buzz(int c) { if (haptic) performHapticFeedback(c); }

    public void cancelTouches() {
        for (int i = 0; i < MAXP; i++) { release(i); used[i] = false; psug[i] = -1; }
        gSlot = -1; gesturing = false; gn = 0; menuOn = false; menuSlot = -1;
        if (delSlot >= 0) { delSlot = -1; delWords = 0; if (lis != null) lis.onDelSel(0); }
        invalidate();
    }

    void cursorStep(int s, float x, float y) {
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

    void drawClearArc(Canvas c, long now) {
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

    void drawThumb(Canvas c, float cx, float cy, float a) {
        pFill.setColor(Theme.fade(Color.WHITE, a));
        rf.set(cx - d(14), cy - d(14), cx + d(14), cy + d(14));
        c.drawBitmap(clipImg, clipSrc, rf, pFill);
    }

    public int getPage() { return page; }

    void heal() {
        cancelTouches();
        gn = 0;
        if (++failN >= 2) { failN = 0; if (lis != null) lis.onBroken(); return; }
        build();
        postInvalidate();
    }

    public boolean healthy() {
        return W > 0 && H > 0 && keys != null && keys.length > 0 && !Float.isNaN(keys[0].l) && !Float.isNaN(keys[keys.length - 1].r);
    }

    int menuPressed() {
        for (int q = 0; q < MAXP; q++) if (used[q] && psug[q] >= 20) return psug[q] - 20;
        return -1;
    }

    int menuSelAt(float x, float y) {
        int c = (int) Math.floor((menuR - x) / menuCell);
        if (c < 0) c = 0;
        if (c >= menuCols) return -1;
        int r = (int) Math.floor((menuB - y) / menuCellH);
        if (r < 0) r = 0;
        int j = r * menuCols + c;
        return j < menuItems.length ? j : -1;
    }

    boolean micTop() { return micOn && showWords && (!bb() || micPos != 1); }

    @Override protected void onDraw(Canvas c) {
        try { draw0(c); failN = 0; if (overlay != null) overlay.postInvalidateOnAnimation(); }
        catch (RuntimeException e) { Diag.saveOnce(getContext(), e); heal(); }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        try { return touch0(e); } catch (RuntimeException ex) { Diag.saveOnce(getContext(), ex); return true; }
    }

    void openMenu(int s, Key k, String[] items, boolean punct) {
        String[] it = items;
        if (!punct && shift != 0 && page == 0) { it = items.clone(); for (int i = 0; i < it.length; i++) it[i] = lay.upperKeep(it[i]); }
        menuItems = it;
        menuCols = punct ? Math.min(5, it.length) : it.length > 8 ? (it.length + 1) / 2 : it.length;
        int rows = (it.length + menuCols - 1) / menuCols;
        menuCell = Math.min(d(44), (W - d(8)) / menuCols);
        menuCellH = d(punct ? 44 : 46);
        float anchor = punct ? Math.min(W - d(4), k.r + d(4)) : (k.l + k.r) / 2f + menuCell / 2f;
        menuR = Math.min(W - d(4), Math.max(anchor, d(4) + menuCols * menuCell));
        menuL = menuR - menuCols * menuCell;
        menuB = k.t - d(8); menuT = menuB - rows * menuCellH;
        if (menuT < 0f) { menuB -= menuT; menuT = 0f; }
        if (gSlot == s) { gSlot = -1; gn = 0; gesturing = false; gKey = null; }
        menuSlot = s; menuSel = 0; menuOn = true;
        menuAx = (k.l + k.r) / 2f; fx.reset(); menuP = 0f;
        invalidate();
    }

    public void setBottomBar(boolean v) { if (bottomBar != v) { bottomBar = v; requestLayout(); build(); invalidate(); } }

    @Override public void setBottomPad(int dp) { if (botPad != dp) { botPad = dp; requestLayout(); build(); invalidate(); } }

    @Override public void setFontScale(float f) { if (fs != f) { fs = f; sugFit = false; restyle(); invalidate(); } }

    boolean bold;

    @Override public void setBold(boolean on) { if (bold != on) { bold = on; restyle(); invalidate(); } }

    public void setGlobeMode(boolean g) { if (globeMode != g) { globeMode = g; invalidate(); } }

    boolean delSlideOn = true;
    float tbx, tby, keyW, keyH;
    int delSlot = -1, delWords;

    private float ox() { return Math.abs(tbx) > 0.03f ? tbx * keyW : 0f; }

    private float oy() { return Math.abs(tby) > 0.03f ? tby * keyH : 0f; }

    @Override public void setTouchBias(float x, float y) { tbx = x; tby = y; }

    @Override public float touchBiasX() { return tbx; }

    @Override public float touchBiasY() { return tby; }

    @Override public void setDelSlide(boolean on) { delSlideOn = on; }

    private void learnTouch(Key k, float x, float y) {
        float w = k.r - k.l, hh = k.b - k.t;
        if (w <= 0f || hh <= 0f) return;
        float fx = (x - (k.l + k.r) / 2f) / w, fy = (y - (k.t + k.b) / 2f) / hh;
        if (Math.abs(fx) > 0.6f || Math.abs(fy) > 0.6f) return;
        tbx = Math.max(-0.25f, Math.min(0.25f, tbx + 0.005f * (fx - tbx)));
        tby = Math.max(-0.25f, Math.min(0.25f, tby + 0.005f * (fy - tby)));
    }

    boolean commaKey;

    @Override public void setCommaKey(boolean on) { if (commaKey != on) { commaKey = on; build(); invalidate(); } }

    public void setHaptic(boolean on) { haptic = on; }

    public void setHeightScale(float v) { if (hs != v) { hs = v; requestLayout(); build(); invalidate(); } }

    @Override public void setHints(boolean num, boolean sym) { if (hintNum != num || hintSym != sym) { hintNum = num; hintSym = sym; restyle(); build(); invalidate(); } }

    @Override public void setIncognito(boolean allowed, boolean active) { if (incoAllowed != allowed || incoActive != active) { incoAllowed = allowed; incoActive = active; invalidate(); } }

    public void setKeyBg(boolean on) { if (keyBg != on) { keyBg = on; invalidate(); } }

    public void setLangKey(boolean on) { if (langKey != on) { langKey = on; build(); invalidate(); } }

    public void setLayout(Layouts.L l) {
        if (lay == l) return;
        prevLay = lay; lay = l;
        if (W > 0) { langT = SystemClock.uptimeMillis(); pageP = 0f; }
        build(); invalidate();
    }

    @Override public void setLine(boolean on) { lineOn = on; }

    @Override public void setMic(boolean on) { if (micOn != on) { micOn = on; build(); invalidate(); } }

    @Override public void setMicPos(int p) { if (micPos != p) { micPos = p; build(); invalidate(); } }

    public void setNumRow(boolean v) { if (numRow != v) { numRow = v; requestLayout(); build(); invalidate(); } }

    public void setNumpad(int m) { if (numpad != m) { numpad = m; restyle(); requestLayout(); build(); invalidate(); } }

    public void setOneHand(int m) { if (oneHand != m) { oneHand = m; invalidate(); } }

    @Override public void setPopup(boolean on) { popupOn = on; }

    @Override public int headNeeded() {
        if (!popupOn || keys == null) return 0;
        float top = Float.MAX_VALUE;
        for (Key k : keys) if (k.type == T_CHAR && popKeyOk(k) && k.t < top) top = k.t;
        return top == Float.MAX_VALUE ? 0 : (int) Math.max(0f, (float) Math.ceil(popRise() - top));
    }

    public void setShowWords(boolean v) { if (showWords != v) { showWords = v; requestLayout(); build(); invalidate(); } }

    public void setSplit(boolean on) { if (split != on) { split = on; if (getWidth() == 0 || !isShown()) spP = on ? 1f : 0f; spV = 0f; build(); invalidate(); } }

    public void setStripHidden(boolean h) { if (stripHidden != h) { stripHidden = h; invalidate(); } }

    public void setSuggestions(String a, String b, String c) {
        if (eq(sug[0], a) && eq(sug[1], b) && eq(sug[2], c)) return;
        sug[0] = a; sug[1] = b; sug[2] = c; sugDisp[0] = sugDisp[1] = sugDisp[2] = null; sugFit = false;
        if (clipText == null && undoLabel == null) sugP = 0f;
        invalidate();
    }

    public void setSwapIcons(boolean on) { if (swapIcons != on) { swapIcons = on; build(); invalidate(); } }

    public void setSwipe(SwipeDecoder s) { swipe = s; }

    public void setSwipeEnabled(boolean on) { swipeOn = on; }

    public void setTablet(boolean on) { if (tablet != on) { tablet = on; requestLayout(); build(); invalidate(); } }

    @Override public void setTextScale(float s) { if (ts != s) { ts = s; restyle(); invalidate(); } }

    void setTools(boolean on) {
        if (toolsOpen == on) return;
        toolsOpen = on; invalidate();
        if (lis != null) lis.onToolsToggle(on);
    }

    @Override public void setToolsOpen(boolean on) { setTools(on && showWords); }

    int slotOf(int id) {
        for (int i = 0; i < MAXP; i++) if (used[i] && pid[i] == id) return i;
        return -1;
    }

    boolean touch0(MotionEvent e) {
        evT = e.getEventTime();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex(); down(e.getPointerId(i), e.getX(i), e.getY(i)); break;
            }
            case MotionEvent.ACTION_MOVE: {
                int hs = e.getHistorySize();
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (hs > 0 && gSlot >= 0 && slotOf(id) == gSlot) {
                        for (int q = 0; q < hs; q++) { evT = e.getHistoricalEventTime(q); addPt(e.getHistoricalX(i, q), e.getHistoricalY(i, q)); }
                        evT = e.getEventTime();
                    }
                    move(id, e.getX(i), e.getY(i));
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex(); up(e.getPointerId(i), e.getX(i), e.getY(i)); break;
            }
            case MotionEvent.ACTION_CANCEL: cancelTouches(); break;
        }
        return true;
    }

    float uu() { return den * ts; }
}
