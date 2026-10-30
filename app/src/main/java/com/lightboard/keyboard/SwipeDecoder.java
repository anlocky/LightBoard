package com.lightboard.keyboard;

/**
 * Непрерывный ввод. Упрощённый SHARK2: путь пальца и «идеальный» путь слова (ломаная через центры букв) ресемплируются в N точек;
 * сравнивается среднее расстояние в долях клавиши (начало и конец жеста весомее) + штраф за редкость слова. Кроме словаря
 * кандидатами служат «свои» слова из самообучения, а частота набора лучших кандидатов слегка поднимает их выше.
 */
final class SwipeDecoder {
    private static final int N = 24, K = 8;
    private final float[] ux = new float[N], uy = new float[N], tx = new float[N], ty = new float[N];
    private final float[] wx = new float[32], wy = new float[32], poolS = new float[K];
    private final int[] poolI = new int[K];
    private final String[] poolW = new String[K], ownW = new String[300];
    private final int[] ownC = new int[300];
    private static final int MAPN = 0x700; // латиница, кириллица, греческий, иврит, арабский
    private final short[] map = new short[MAPN]; // один на всё время жизни: на каждый свайп массив не создаётся
    private int poolN;
    private float pitch;
    private Learner learner;

    void setLearner(Learner l) { learner = l; }

    private static char norm(char c) { return c == 'ё' ? 'е' : c; }

    int decode(boolean ru, Lexicon lex, String letters, float[] lcx, float[] lcy, float pitch,
               float[] px, float[] py, int n, String[] out) {
        int nl = letters.length();
        if (lex == null || lex.n == 0 || n < 2 || nl == 0 || pitch <= 0) return 0;
        this.pitch = pitch;
        float plen = 0;
        for (int i = 1; i < n; i++) plen += dist(px[i] - px[i - 1], py[i] - py[i - 1]);
        if (plen < pitch * 0.7f) return 0; // раньше было 1,5: короткие слова («не», «на») отбрасывались
        resample(px, py, n, ux, uy);

        java.util.Arrays.fill(map, (short) -1);
        for (int i = 0; i < nl; i++) { char lc0 = letters.charAt(i); if (lc0 < MAPN) map[lc0] = (short) i; }
        boolean[] okF = new boolean[nl], okL = new boolean[nl];
        float r = pitch * 1.1f;
        for (int i = 0; i < nl; i++) {
            okF[i] = dist(lcx[i] - px[0], lcy[i] - py[0]) <= r;
            okL[i] = dist(lcx[i] - px[n - 1], lcy[i] - py[n - 1]) <= r;
        }

        poolN = 0;
        int[] off = lex.off;
        for (int fi = 0; fi < nl; fi++) {
            if (!okF[fi]) continue;
            char fc = letters.charAt(fi);
            for (int pass = 0; pass < (fc == 'е' ? 2 : 1); pass++) {
                char c = pass == 0 ? fc : 'ё';
                int lo = lex.lowerBound(String.valueOf(c)), hi = lex.lowerBound(String.valueOf((char) (c + 1)));
                for (int wi = lo; wi < hi; wi++) {
                    int s = off[wi], e = off[wi + 1], L = e - s;
                    if (L < 2 || L > 24) continue;
                    char lc = norm(lex.at(e - 1));
                    int li = lc < MAPN ? map[lc] : -1;
                    if (li < 0 || !okL[li]) continue;
                    int m = 0, prev = -1;
                    boolean bad = false;
                    for (int i = s; i < e; i++) {
                        char x = norm(lex.at(i));
                        int k = x < MAPN ? map[x] : -1;
                        if (k < 0) { bad = true; break; }
                        if (k == prev) continue;
                        wx[m] = lcx[k]; wy[m] = lcy[k]; m++; prev = k;
                    }
                    if (bad || m < 2) continue;
                    float score = geo(m, plen, lex.freq[wi] & 0xFF);
                    if (score >= 0f) push(score, wi, null);
                }
            }
        }
        if (learner != null) { // свои слова из самообучения
            int xn = learner.own(ownW, ownC);
            for (int q = 0; q < xn; q++) {
                String w = ownW[q];
                int L = w.length();
                if (L < 2 || L > 24) continue;
                char f0 = norm(w.charAt(0)), l0 = norm(w.charAt(L - 1));
                int fi = f0 < MAPN ? map[f0] : -1, li = l0 < MAPN ? map[l0] : -1;
                if (fi < 0 || li < 0 || !okF[fi] || !okL[li]) continue;
                int m = 0, prev = -1;
                boolean bad = false;
                for (int i = 0; i < L; i++) {
                    char x = norm(w.charAt(i));
                    int k = x < MAPN ? map[x] : -1;
                    if (k < 0) { bad = true; break; }
                    if (k == prev) continue;
                    wx[m] = lcx[k]; wy[m] = lcy[k]; m++; prev = k;
                }
                if (bad || m < 2) continue;
                float score = geo(m, plen, 110 + Math.min(80, 8 * ownC[q]));
                if (score >= 0f) push(score, -1, w);
            }
        }
        // итог: у лучших кандидатов учитываем, как часто слово набирали
        int found = 0;
        for (int i = 0; i < poolN; i++) {
            if (poolW[i] == null) poolW[i] = lex.word(poolI[i]);
            if (learner != null) {
                int cnt = learner.count(poolW[i]);
                if (cnt > 0) poolS[i] -= Math.min(0.15f, 0.045f * (float) (Math.log(1 + cnt) / Math.log(2)));
            }
        }
        for (; found < out.length && found < poolN; found++) {
            int bi = found;
            for (int i = found + 1; i < poolN; i++) if (poolS[i] < poolS[bi]) bi = i;
            String w = poolW[bi]; float sc = poolS[bi];
            poolW[bi] = poolW[found]; poolS[bi] = poolS[found]; poolI[bi] = poolI[found];
            poolW[found] = w; poolS[found] = sc;
            out[found] = w;
        }
        return found;
    }

    /** Совпадение идеального пути слова (wx, wy, m точек) с путём пальца; -1 — не подходит. */
    private float geo(int m, float plen, int freqByte) {
        float wl = 0;
        for (int i = 1; i < m; i++) wl += dist(wx[i] - wx[i - 1], wy[i] - wy[i - 1]);
        if (wl < 0.45f * plen || wl > 2.2f * plen) return -1f;
        resample(wx, wy, m, tx, ty);
        float d = 0, ws = 0;
        for (int i = 0; i < N; i++) {
            float w = (i < 3 || i >= N - 3) ? 2f : 1f; // начало и конец жеста самые точные
            d += w * dist(ux[i] - tx[i], uy[i] - ty[i]); ws += w;
        }
        d = d / ws / pitch;
        if (d > 2f) return -1f;
        return d + (m <= 2 ? 0.55f : 0.35f) * (255 - freqByte) / 232f; // у коротких слов частота решает больше
    }

    private void push(float score, int wi, String w) {
        int pos = poolN < K ? poolN : K;
        while (pos > 0 && poolS[pos - 1] > score) pos--;
        if (pos >= K) return;
        int last = Math.min(poolN, K - 1);
        for (int j = last; j > pos; j--) { poolS[j] = poolS[j - 1]; poolI[j] = poolI[j - 1]; poolW[j] = poolW[j - 1]; }
        poolS[pos] = score; poolI[pos] = wi; poolW[pos] = w;
        if (poolN < K) poolN++;
    }

    private static float dist(float dx, float dy) { return (float) Math.sqrt(dx * dx + dy * dy); }

    private static void resample(float[] x, float[] y, int n, float[] ox, float[] oy) {
        float total = 0;
        for (int i = 1; i < n; i++) total += dist(x[i] - x[i - 1], y[i] - y[i - 1]);
        if (total == 0) { for (int k = 0; k < N; k++) { ox[k] = x[0]; oy[k] = y[0]; } return; }
        float step = total / (N - 1), c = 0;
        int seg = 1;
        ox[0] = x[0]; oy[0] = y[0];
        for (int k = 1; k < N - 1; k++) {
            float target = k * step, sl = dist(x[seg] - x[seg - 1], y[seg] - y[seg - 1]);
            while (seg < n - 1 && c + sl < target) {
                c += sl; seg++;
                sl = dist(x[seg] - x[seg - 1], y[seg] - y[seg - 1]);
            }
            float f = sl == 0 ? 0 : (target - c) / sl;
            if (f > 1) f = 1;
            ox[k] = x[seg - 1] + (x[seg] - x[seg - 1]) * f;
            oy[k] = y[seg - 1] + (y[seg] - y[seg - 1]) * f;
        }
        ox[N - 1] = x[n - 1]; oy[N - 1] = y[n - 1];
    }
}
