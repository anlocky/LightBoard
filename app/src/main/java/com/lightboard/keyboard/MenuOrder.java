package com.lightboard.keyboard;

final class MenuOrder {
    private MenuOrder() { }

    static final int N = 4;
    static final String DEFAULT = "0123";

    static int[] parse(String s) {
        int[] def = {0, 1, 2, 3};
        if (s == null || s.length() != N) return def;
        boolean[] seen = new boolean[N];
        int[] r = new int[N];
        for (int i = 0; i < N; i++) {
            int v = s.charAt(i) - '0';
            if (v < 0 || v >= N || seen[v]) return def;
            seen[v] = true;
            r[i] = v;
        }
        return r;
    }

    static String encode(int[] o) {
        StringBuilder sb = new StringBuilder(N);
        for (int v : o) sb.append((char) ('0' + v));
        return sb.toString();
    }
}
