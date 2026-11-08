package com.lightboard.keyboard;

final class Touch {
    static final int MAX = 24;
    final char[] typed = new char[MAX];
    final char[][] ch = new char[MAX][4];
    final float[][] d2 = new float[MAX][4];
    final int[] n = new int[MAX];
    int len;
    boolean bad;

    void clear() { len = 0; bad = false; }

    void push(char c, char[] cs, float[] ds, int cnt) {
        if (len >= MAX) { bad = true; return; }
        typed[len] = c;
        n[len] = cnt;
        for (int i = 0; i < cnt; i++) { ch[len][i] = cs[i]; d2[len][i] = ds[i]; }
        len++;
    }

    void pop() { if (!bad && len > 0) len--; }

    boolean matches(String w) {
        if (bad || len != w.length()) return false;
        for (int i = 0; i < len; i++) if (typed[i] != w.charAt(i)) return false;
        return true;
    }

    Touch copy() {
        Touch t = new Touch();
        t.len = len; t.bad = bad;
        System.arraycopy(typed, 0, t.typed, 0, MAX);
        System.arraycopy(n, 0, t.n, 0, MAX);
        for (int i = 0; i < len; i++) { System.arraycopy(ch[i], 0, t.ch[i], 0, 4); System.arraycopy(d2[i], 0, t.d2[i], 0, 4); }
        return t;
    }
}
