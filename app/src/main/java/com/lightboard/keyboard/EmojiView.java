package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

final class EmojiView extends View {

    interface Listener {
        void onEmoji(String e);
        void onBack();
        void onAbc();
    }

    private static final float TITLE_H = 34, BAR_H = 52, SIDE = 4;

    private final Theme th;
    private final int fixedH;
    private final float den;
    private Listener lis;
    private final Handler h = new Handler(Looper.getMainLooper());

    private String[] names = new String[0], icons = new String[0], titles = new String[0];
    private String[][] cells = new String[0][];
    private int[] startCol = new int[0];
    private int totalCols, rows = 5;
    private boolean loaded;
    private String recent = "";

    private int W, H;
    private float cell, gridTop, barTop, scrollX, maxScroll, hlX = -1f;
    private float vel, target = -1f;
    private long lastFrame, tapT;
    private int tapCol = -1, tapRow = -1;

    private float downX, downY, lastX;
    private boolean dragging, barDown;
    private int barItem = -2;
    private VelocityTracker vt;
    private final int slop;
    private boolean backHeld;
    private final Runnable rep = new Runnable() {
        @Override public void run() {
            if (!backHeld || lis == null) return;
            lis.onBack(); h.postDelayed(this, 45);
        }
    };

    private final Paint pEmoji = new Paint(Paint.ANTI_ALIAS_FLAG), pSmall = new Paint(Paint.ANTI_ALIAS_FLAG),
            pTitle = new Paint(Paint.ANTI_ALIAS_FLAG), pFill = new Paint(Paint.ANTI_ALIAS_FLAG),
            pStroke = new Paint(Paint.ANTI_ALIAS_FLAG), pPanel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private float offEmoji, offSmall;

    EmojiView(Context c, Theme theme, int heightPx) {
        super(c);
        th = theme; fixedH = heightPx;
        den = c.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        pEmoji.setTextAlign(Paint.Align.CENTER);
        pSmall.setTextAlign(Paint.Align.CENTER);
        pSmall.setTextSize(20 * den);
        pTitle.setTextSize(12 * den); pTitle.setTypeface(Typeface.DEFAULT_BOLD); pTitle.setLetterSpacing(0.04f);
        pStroke.setStyle(Paint.Style.STROKE); pStroke.setStrokeWidth(1.6f * den);
        pStroke.setStrokeCap(Paint.Cap.ROUND); pStroke.setStrokeJoin(Paint.Join.ROUND);
        Paint.FontMetrics f = pSmall.getFontMetrics(); offSmall = -(f.ascent + f.descent) / 2f;
    }

    void setListener(Listener l) { lis = l; }
    void themeChanged() { makeShader(); invalidate(); }

    private void makeShader() {
        if (H == 0) return;
        pPanel.setShader(new android.graphics.LinearGradient(0, 0, 0, H, th.panelTop, th.panelBot, android.graphics.Shader.TileMode.CLAMP));
    }

    void open(String recentList) {
        recent = recentList == null ? "" : recentList.trim();
        if (!loaded) load(); else rebuildRecent();
        layout();
        scrollX = 0; vel = 0; target = -1f; hlX = -1f;
        invalidate();
    }

    private final ArrayList<String> bName = new ArrayList<>(), bIcon = new ArrayList<>();
    private final ArrayList<String[]> bCells = new ArrayList<>();

    private void load() {
        loaded = true;
        bName.clear(); bIcon.clear(); bCells.clear();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getContext().getAssets().open("emoji.txt"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|", 3);
                if (p.length < 3) continue;
                String[] toks = p[2].trim().split("\\s+");
                for (int i = 0; i < toks.length; i++) if (toks[i].length() == 1) toks[i] = toks[i] + "\uFE0F";
                bName.add(p[0]); bIcon.add(fix(p[1])); bCells.add(toks);
            }
        } catch (IOException ignored) { }
        rebuildRecent();
    }

    private static String fix(String s) { return s.length() == 1 ? s + "\uFE0F" : s; }

    private void rebuildRecent() {
        int n = bName.size();
        ArrayList<String> nm = new ArrayList<>(n + 1), ic = new ArrayList<>(n + 1);
        ArrayList<String[]> cl = new ArrayList<>(n + 1);
        int off = 0;
        if (!recent.isEmpty()) {
            if (n > 0 && bName.get(0).equals("Недавние")) off = 1;
            nm.add("Недавние"); ic.add("\uD83D\uDD52"); cl.add(recent.split("\\s+"));
        } else if (n > 0 && bName.get(0).equals("Недавние")) off = 1;
        for (int i = off; i < n; i++) { nm.add(bName.get(i)); ic.add(bIcon.get(i)); cl.add(bCells.get(i)); }
        bName.clear(); bName.addAll(nm); bIcon.clear(); bIcon.addAll(ic); bCells.clear(); bCells.addAll(cl);
        names = nm.toArray(new String[0]); icons = ic.toArray(new String[0]); cells = cl.toArray(new String[0][]);
        titles = new String[names.length];
        for (int i = 0; i < names.length; i++) titles[i] = KbText.t(names[i]).toUpperCase(Locale.getDefault());
    }

    private void layout() {
        if (W == 0) return;
        rows = H < 260 * den ? 4 : 5;
        gridTop = TITLE_H * den; barTop = H - BAR_H * den;
        cell = (barTop - gridTop) / rows;
        pEmoji.setTextSize(cell * 0.62f);
        Paint.FontMetrics f = pEmoji.getFontMetrics(); offEmoji = -(f.ascent + f.descent) / 2f;
        startCol = new int[cells.length];
        int c = 0;
        for (int i = 0; i < cells.length; i++) { startCol[i] = c; c += (cells[i].length + rows - 1) / rows; }
        totalCols = c;
        maxScroll = Math.max(0f, totalCols * cell + 2 * SIDE * den - W);
    }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        if (w == 0) w = getResources().getDisplayMetrics().widthPixels;
        setMeasuredDimension(w, fixedH);
    }

    @Override protected void onSizeChanged(int w, int hh, int ow, int oh) {
        W = w; H = hh;
        makeShader();
        layout();
    }

    private int sectionAt(float sx) {
        int col = (int) ((sx + W * 0.2f) / cell);
        int s = 0;
        for (int i = 0; i < startCol.length; i++) if (col >= startCol[i]) s = i;
        return s;
    }

    private boolean tick(long now) {
        float dt = lastFrame == 0 ? 0f : Math.min(48f, now - lastFrame);
        boolean a = false;
        if (target >= 0f) {
            float diff = target - scrollX;
            if (Math.abs(diff) < 0.6f) { scrollX = target; target = -1f; }
            else { scrollX += diff * (1f - (float) Math.exp(-dt / 85f)); a = true; }
        } else if (Math.abs(vel) > 0.02f && !dragging) {
            scrollX -= vel * dt;
            vel *= (float) Math.exp(-dt / 320f);
            a = true;
        }
        if (scrollX < 0f) { scrollX = 0f; vel = 0f; }
        if (scrollX > maxScroll) { scrollX = maxScroll; vel = 0f; }
        if (tapT != 0) { if (now - tapT < 260) a = true; else { tapT = 0; tapCol = -1; } }
        if (names.length > 0) {
            float tx = iconX(sectionAt(scrollX));
            if (hlX < 0f) hlX = tx;
            else if (Math.abs(tx - hlX) > 0.5f) { hlX += (tx - hlX) * (1f - (float) Math.exp(-dt / 70f)); a = true; }
        }
        lastFrame = a ? now : 0;
        return a;
    }

    private float iconX(int i) {
        float l = 66 * den, r = W - 66 * den, n = Math.max(1, names.length);
        return l + (r - l) * (i + 0.5f) / n;
    }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean anim = tick(now);
        float rad = 16 * den;
        c.drawRoundRect(0, 0, W, H + rad, rad, rad, pPanel);
        if (names.length == 0) return;

        int sec = sectionAt(scrollX);
        pTitle.setColor(th.muted);
        c.drawText(titles[sec], 16 * den, 22 * den, pTitle);

        int first = Math.max(0, (int) ((scrollX - SIDE * den) / cell)), last = (int) ((scrollX + W) / cell) + 1;
        int si = 0;
        for (int i = 0; i < startCol.length; i++) if (startCol[i] <= first) si = i;
        for (int col = first; col <= last && col < totalCols; col++) {
            while (si + 1 < startCol.length && col >= startCol[si + 1]) si++;
            String[] cl = cells[si];
            float cx = SIDE * den + col * cell + cell / 2f - scrollX;
            for (int r = 0; r < rows; r++) {
                int idx = (col - startCol[si]) * rows + r;
                if (idx >= cl.length) break;
                float cy = gridTop + r * cell + cell / 2f;
                float sc = 1f;
                if (tapT != 0 && col == tapCol && r == tapRow) {
                    float p = Math.min(1f, (now - tapT) / 260f);
                    sc = 1f + 0.4f * (float) Math.sin(Math.PI * p);
                    pFill.setColor(Theme.fade(th.text, 0.10f * (1f - p)));
                    c.drawCircle(cx, cy, cell * 0.5f, pFill);
                }
                if (sc != 1f) { c.save(); c.scale(sc, sc, cx, cy); }
                c.drawText(cl[idx], cx, cy + offEmoji, pEmoji);
                if (sc != 1f) c.restore();
            }
        }

        float by = barTop + (H - barTop) * 0.46f;
        float w = 50 * den, hh = 34 * den;
        pFill.setColor(barItem == -1 && barDown ? th.specDown : th.spec);
        c.drawRoundRect(8 * den, by - hh / 2f, 8 * den + w, by + hh / 2f, 7 * den, 7 * den, pFill);
        pSmall.setColor(th.text); pSmall.setTextSize(15 * den);
        float o15 = offSmall * 15f / 20f;
        c.drawText("ABC", 8 * den + w / 2f, by + o15, pSmall);
        pSmall.setTextSize(20 * den);

        float rx = W - 8 * den - w;
        pFill.setColor(barItem == 1000 && barDown ? th.specDown : th.spec);
        c.drawRoundRect(rx, by - hh / 2f, rx + w, by + hh / 2f, 7 * den, 7 * den, pFill);
        Icons.back(c, path, pStroke, rx + w / 2f, by, den, th.text);

        if (hlX >= 0f) { pFill.setColor(Theme.fade(th.text, 0.12f)); c.drawCircle(hlX, by, 17 * den, pFill); }
        for (int i = 0; i < icons.length; i++) {
            pSmall.setAlpha(i == sec ? 255 : 140);
            pSmall.setTextSize(i == sec ? 21 * den : 19 * den);
            c.drawText(icons[i], iconX(i), by + offSmall, pSmall);
        }
        pSmall.setAlpha(255); pSmall.setTextSize(20 * den);
        if (anim) postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = lastX = e.getX(); downY = e.getY(); dragging = false; vel = 0f; target = -1f;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain(); vt.addMovement(e);
                barDown = false; barItem = -2;
                if (downY >= barTop) {
                    barDown = true; barItem = barHit(downX);
                    if (barItem == 1000 && lis != null) { lis.onBack(); backHeld = true; h.postDelayed(rep, 400); }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (vt != null) vt.addMovement(e);
                if (barDown) return true;
                float x = e.getX();
                if (!dragging && Math.abs(x - downX) > slop) dragging = true;
                if (dragging) {
                    scrollX -= x - lastX;
                    if (scrollX < 0f) scrollX = 0f; else if (scrollX > maxScroll) scrollX = maxScroll;
                    lastX = x; invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                backHeld = false; h.removeCallbacks(rep);
                boolean up = e.getActionMasked() == MotionEvent.ACTION_UP;
                if (barDown) {
                    if (up && barItem != -2 && barItem == barHit(e.getX())) {
                        if (barItem == -1 && lis != null) lis.onAbc();
                        else if (barItem >= 0 && barItem < 1000 && barItem < startCol.length) {
                            target = Math.min(maxScroll, startCol[barItem] * cell);
                        }
                    }
                    barDown = false; barItem = -2;
                } else if (dragging) {
                    if (vt != null) { vt.addMovement(e); vt.computeCurrentVelocity(1); vel = vt.getXVelocity(); }
                } else if (up) {
                    tapAt(e.getX(), e.getY());
                }
                if (vt != null) { vt.recycle(); vt = null; }
                dragging = false;
                invalidate();
                return true;
            }
        }
        return true;
    }

    private int barHit(float x) {
        if (x < 66 * den) return x < 62 * den ? -1 : -2;
        if (x > W - 66 * den) return 1000;
        float l = 66 * den, r = W - 66 * den, n = Math.max(1, names.length);
        int i = (int) ((x - l) / ((r - l) / n));
        return Math.max(0, Math.min(names.length - 1, i));
    }

    private void tapAt(float x, float y) {
        if (y < gridTop || y >= barTop || lis == null) return;
        int col = (int) ((x + scrollX - SIDE * den) / cell), r = (int) ((y - gridTop) / cell);
        if (col < 0 || col >= totalCols || r < 0 || r >= rows) return;
        int si = 0;
        for (int i = 0; i < startCol.length; i++) if (col >= startCol[i]) si = i;
        int idx = (col - startCol[si]) * rows + r;
        if (idx >= cells[si].length) return;
        tapCol = col; tapRow = r; tapT = SystemClock.uptimeMillis();
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        lis.onEmoji(cells[si][idx]);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        h.removeCallbacksAndMessages(null);
        if (vt != null) { vt.recycle(); vt = null; }
    }
}
