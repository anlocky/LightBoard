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

/** Общая часть обоих видов клавиатуры (Cupertino и Material): всё, что в них совпадало дословно. */
abstract class BoardView extends View implements Board {
    // ---- общие для обоих видов клавиатуры ----
    static final int T_CHAR = 0, T_SHIFT = 1, T_BACK = 2, T_PAGE = 3, T_SPACE = 4, T_ENTER = 5, T_LANG = 9, T_TAB = 10, T_CAPS = 11, T_LEFT = 12, T_RIGHT = 13;

    BoardView(Context c) { super(c); }

    static final class Key {
        final int type, target, row; final String label; String alt, upper, hint;
        float l, t, r, b, rad, p, ol, orr, sl, sr; boolean down; // ol/orr — координаты цельной клавиатуры (для анимации разделения), sl/sr — запас на время кадра
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

    /** Подсказки на буквах: у каждой буквы свой знак (позиция в ряду) и он же добавляется в меню по удержанию. */
    void applyHints(int firstRow, boolean numRow, boolean pad) {
        if ((!hintNum && !hintSym) || pad || page != 0) return;
        int[] n = new int[3];
        for (Key k : keys) {
            if (k.type != T_CHAR || k.upper == null) continue;
            int rr = k.row - firstRow;
            if (rr < 0 || rr > 2) continue;
            String h = KeyPlan.hint(rr, n[rr]++, numRow, hintNum, hintSym);
            if (h == null) continue;
            k.hint = h;
            k.alt = k.alt == null ? h : h + "|" + k.alt;
        }
    }

    /** Клавиша «1234»: четыре цифры 2×2, как в Gboard. */
    void numIcon(Canvas c, Key k, float cx, Paint p, float off, int color) {
        float h = k.b - k.t;
        txt(c, "1 2", cx, k.t + h * 0.34f, p, off, color);
        txt(c, "3 4", cx, k.t + h * 0.7f, p, off, color);
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

    final Handler h = new Handler(Looper.getMainLooper());

    // ---- жесты: курсор по пробелу, «стереть всё», микрофон слушает ----
    float hs = 1f;

    boolean spaceCursor = true, clearAll = true, listening;

    int clearHold = 600, clearSlot = -1;

    long clearStart;

    // ---- непрерывный ввод ----
    int gSlot = -1, gn;

    boolean gesturing;

    Key gKey;

    float gsx, gsy;

    final float[] trX = new float[320], trY = new float[320];

    final long[] trT = new long[320];

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
        if (b != null) { int s = Math.min(b.getWidth(), b.getHeight()); clipSrc.set((b.getWidth() - s) / 2, (b.getHeight() - s) / 2, (b.getWidth() + s) / 2, (b.getHeight() + s) / 2); } // по центру, квадратом
        invalidate();
    }

    float ts = 1f;

    static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }

    void add(ArrayList<Key> L, Key k, float x, float y, float w, float hh) {
        k.l = x; k.t = y; k.r = x + w; k.b = y + hh; L.add(k);
    }

    // ---------- рисование ----------
    void txt(Canvas c, String s, float cx, float cy, Paint p, float off, int color) {
        p.setColor(color); c.drawText(s, cx, cy + off, p);
    }

    static final String[] PUNCT_ITEMS = {",", "!", "?", ":", ";", "'", "\"", "-", "(", ")"};

    String[] menuItems = PUNCT_ITEMS;

    int menuCols = 5;

    float menuCellH, menuAx; // menuAx: где над клавишей начинается меню (из этой точки оно «вырастает»)
    final MenuFx fx = new MenuFx();

    @Override public View view() { return this; }


    void addPt(float x, float y) {
        if (gn > 0) {
            float dx = x - trX[gn - 1], dy = y - trY[gn - 1], m = d(2.5f);
            if (dx * dx + dy * dy < m * m) return;
        }
        if (gn == trX.length) gn--;
        trX[gn] = x; trY[gn] = y; trT[gn] = SystemClock.uptimeMillis(); gn++;
    }

    public void setSpaceCursor(boolean on) { spaceCursor = on; }

    public void setClearAll(boolean on, int holdMs) { clearAll = on; clearHold = holdMs; }

    public void setListening(boolean on) { if (listening != on) { listening = on; invalidate(); } }
}
