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
        final int type, target, row; final String label; String alt, upper, hint;
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

    float toolsP, onP, spP;

    int failN;

    final float[] sugK = {1f, 1f, 1f};

    boolean stripHidden;

    static final String PUNCT_MENU = ",!?:;'\"-()";

    boolean menuOn;

    int menuSlot = -1, menuSel = -1;

    final String[] sug = new String[3], sugDisp = new String[3];

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

    int clearHold = 600, clearSlot = -1;

    long clearStart;

    int gSlot = -1, gn;

    boolean gesturing;

    Key gKey;

    float gsx, gsy;

    final float[] trX = new float[512], trY = new float[512];

    final long[] trT = new long[512];

    final String[] gOut = new String[3];

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
        trX[gn] = x; trY[gn] = y; trT[gn] = SystemClock.uptimeMillis(); gn++;
    }

    public void setSpaceCursor(boolean on) { spaceCursor = on; }

    @Override public void setSpaceLang(boolean on, boolean vertical) { spaceLang = on; langVert = vertical; }

    /** Swipe on the space bar changes the language: one step per 40 dp along the chosen axis; right/up is next, left/down is previous. */
    void spaceLangMove(int s, float x, float y) {
        if (lis == null) return;
        float dx = x - downX[s], dy = downY[s] - y;
        float along = langVert ? dy : dx, across = langVert ? dx : dy;
        if (Math.abs(along) < d(40) || Math.abs(along) < 1.5f * Math.abs(across)) return;
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

    abstract void build();
    abstract void down(int id, float x, float y);
    abstract void draw0(Canvas c);
    abstract void move(int id, float x, float y);
    abstract void release(int s);
    abstract void restyle();
    abstract void up(int id, float x, float y);

    boolean bb() { return bottomBar && numpad == 0 && !tablet; }

    void buzz(int c) { if (haptic) performHapticFeedback(c); }

    public void cancelTouches() {
        for (int i = 0; i < MAXP; i++) { release(i); used[i] = false; psug[i] = -1; }
        gSlot = -1; gesturing = false; gn = 0; menuOn = false; menuSlot = -1;
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
        try { draw0(c); failN = 0; }
        catch (RuntimeException e) { Diag.saveOnce(getContext(), e); heal(); }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        try { return touch0(e); } catch (RuntimeException ex) { Diag.saveOnce(getContext(), ex); return true; }
    }

    void openMenu(int s, Key k, String[] items, boolean punct) {
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
        if (gSlot == s) { gSlot = -1; gn = 0; gesturing = false; gKey = null; }
        menuSlot = s; menuSel = 0; menuOn = true;
        menuAx = (k.l + k.r) / 2f; fx.reset(); menuP = 0f;
        invalidate();
    }

    public void setBottomBar(boolean v) { if (bottomBar != v) { bottomBar = v; requestLayout(); build(); invalidate(); } }

    @Override public void setBottomPad(int dp) { if (botPad != dp) { botPad = dp; requestLayout(); build(); invalidate(); } }

    @Override public void setFontScale(float f) { if (fs != f) { fs = f; restyle(); invalidate(); } }

    boolean bold;

    @Override public void setBold(boolean on) { if (bold != on) { bold = on; restyle(); invalidate(); } }

    public void setGlobeMode(boolean g) { if (globeMode != g) { globeMode = g; invalidate(); } }

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

    public void setShowWords(boolean v) { if (showWords != v) { showWords = v; requestLayout(); build(); invalidate(); } }

    public void setSplit(boolean on) { if (split != on) { split = on; if (getWidth() == 0 || !isShown()) spP = on ? 1f : 0f; build(); invalidate(); } }

    public void setStripHidden(boolean h) { if (stripHidden != h) { stripHidden = h; invalidate(); } }

    public void setSuggestions(String a, String b, String c) {
        if (eq(sug[0], a) && eq(sug[1], b) && eq(sug[2], c)) return;
        sug[0] = a; sug[1] = b; sug[2] = c; sugDisp[0] = sugDisp[1] = sugDisp[2] = null;
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
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex(); down(e.getPointerId(i), e.getX(i), e.getY(i)); break;
            }
            case MotionEvent.ACTION_MOVE: {
                int hs = e.getHistorySize();
                for (int i = 0; i < e.getPointerCount(); i++) {
                    int id = e.getPointerId(i);
                    if (hs > 0 && gSlot >= 0 && slotOf(id) == gSlot) for (int q = 0; q < hs; q++) addPt(e.getHistoricalX(i, q), e.getHistoricalY(i, q));
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
