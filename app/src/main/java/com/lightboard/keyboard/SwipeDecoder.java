package com.lightboard.keyboard;

final class SwipeDecoder {
    private static final int N = 24, K = 32, BAND = 3;
    private static final float WEND = 2f, WF = 0.40f;
    private static final float CW = 0.5f, KW = 0.3f;
    private final float[] ux = new float[N], uy = new float[N], tx = new float[N], ty = new float[N];
    private final float[] wx = new float[32], wy = new float[32], poolS = new float[K], poolG = new float[K];
    private final int[] poolI = new int[K], poolF = new int[K];
    private final String[] poolW = new String[K], ownW = new String[300];
    private final int[] ownC = new int[300];
    private final boolean[] fOk = new boolean[64], lOk = new boolean[64];
    private static final int MAPN = 0x700;
    private final short[] map = new short[MAPN];
    private final float[] dtw0 = new float[N + 1], dtw1 = new float[N + 1];
    private int poolN;
    private float pitch;
    private Learner learner;

    void setLearner(Learner l) { learner = l; }

    private static char norm(char c) { return c == '\u0451' ? '\u0435' : c; }

    int decode(boolean ru, Lexicon lex, String letters, float[] lcx, float[] lcy, float pitch,
               float[] px, float[] py, int n, String[] out) {
        int nl = letters.length();
        if (lex == null || lex.n == 0 || n < 2 || nl == 0 || pitch <= 0) return 0;
        this.pitch = pitch;
        float plen = 0;
        for (int i = 1; i < n; i++) plen += dist(px[i] - px[i - 1], py[i] - py[i - 1]);
        if (plen < pitch * 0.7f) return 0;
        resample(px, py, n, ux, uy);

        java.util.Arrays.fill(map, (short) -1);
        for (int i = 0; i < nl; i++) { char lc0 = letters.charAt(i); if (lc0 < MAPN) map[lc0] = (short) i; }
        final boolean[] okF = fOk, okL = lOk;
        for (int attempt = 0; attempt < 2; attempt++) {
        float r = pitch * (attempt == 0 ? 1.1f : 1.6f);
        for (int i = 0; i < nl; i++) {
            okF[i] = dist(lcx[i] - px[0], lcy[i] - py[0]) <= r;
            okL[i] = dist(lcx[i] - px[n - 1], lcy[i] - py[n - 1]) <= r;
        }

        poolN = 0;
        int[] off = lex.off;
        for (int fi = 0; fi < nl; fi++) {
            if (!okF[fi]) continue;
            char fc = letters.charAt(fi);
            for (int pass = 0; pass < (fc == '\u0435' ? 2 : 1); pass++) {
                char c = pass == 0 ? fc : '\u0451';
                int lo = lex.lowerBound(String.valueOf(c)), hi = lex.lowerBound(String.valueOf((char) (c + 1)));
                for (int wi = lo; wi < hi; wi++) {
                    int s = off[wi], e = off[wi + 1], L = e - s;
                    if (L < 2 || L > 24) continue;
                    char lc = norm(lex.at(e - 1));
                    int li = lc < MAPN ? map[lc] : -1;
                    if (li < 0 || !okL[li]) continue;
                    int m = 0, pk = -1;
                    boolean bad = false;
                    for (int i = s; i < e; i++) {
                        char x = norm(lex.at(i));
                        int k = x < MAPN ? map[x] : -1;
                        if (k < 0) { bad = true; break; }
                        if (k == pk) continue;
                        wx[m] = lcx[k]; wy[m] = lcy[k]; m++; pk = k;
                    }
                    if (bad || m < 2) continue;
                    float g = geo(m, plen);
                    if (g >= 0f) push(g, wi, null, lex.freq[wi] & 0xFF);
                }
            }
        }
        if (learner != null) {
            int xn = learner.own(ownW, ownC);
            for (int q = 0; q < xn; q++) {
                String w = ownW[q];
                int L = w.length();
                if (L < 2 || L > 24) continue;
                char f0 = norm(w.charAt(0)), l0 = norm(w.charAt(L - 1));
                int fi = f0 < MAPN ? map[f0] : -1, li = l0 < MAPN ? map[l0] : -1;
                if (fi < 0 || li < 0 || !okF[fi] || !okL[li]) continue;
                int m = fill(w, lcx, lcy);
                if (m < 2) continue;
                float g = geo(m, plen);
                if (g >= 0f) push(g, -1, w, 110 + Math.min(80, 8 * ownC[q]));
            }
        }
        if (poolN >= 3) break;
        }

        for (int i = 0; i < poolN; i++) {
            String w = poolW[i] != null ? poolW[i] : lex.word(poolI[i]);
            poolW[i] = w;
            int m = fill(w, lcx, lcy);
            float s = poolG[i];
            if (m >= 2) {
                resample(wx, wy, m, tx, ty);
                s = elastic() / pitch;
            }
            if (m >= 2) s += CW * coverage(m) + KW * corners(m);
            int fq = poolF[i];
            s += (m <= 2 ? 1.57f : 1f) * WF * (255 - fq) / 232f;
            if (learner != null) {
                int cnt = learner.count(w);
                if (cnt > 0) s -= Math.min(0.15f, 0.045f * (float) (Math.log(1 + cnt) / Math.log(2)));
            }
            poolS[i] = s;
        }
        int found = 0;
        for (; found < out.length && found < poolN; found++) {
            int bi = found;
            for (int i = found + 1; i < poolN; i++) if (poolS[i] < poolS[bi]) bi = i;
            String w = poolW[bi]; float sc = poolS[bi];
            poolW[bi] = poolW[found]; poolS[bi] = poolS[found];
            poolW[found] = w; poolS[found] = sc;
            out[found] = w;
        }
        return found;
    }

    private float coverage(int m) {
        float tot = 0;
        for (int k = 0; k < m; k++) {
            float best = 1e9f;
            for (int j = 0; j < N; j++) { float d = Anim.sq(wx[k] - ux[j]) + Anim.sq(wy[k] - uy[j]); if (d < best) best = d; }
            float d = (float) Math.sqrt(best) / pitch - 0.45f;
            if (d > 0) tot += d;
        }
        return tot / m;
    }

    private float corners(int m) {
        float tot = 0; int c = 0;
        for (int j = 2; j < N - 2; j++) {
            float ax = ux[j] - ux[j - 2], ay = uy[j] - uy[j - 2], bx = ux[j + 2] - ux[j], by = uy[j + 2] - uy[j];
            float la = (float) Math.sqrt(ax * ax + ay * ay), lb = (float) Math.sqrt(bx * bx + by * by);
            if (la < 1e-3f || lb < 1e-3f) continue;
            float cos = (ax * bx + ay * by) / (la * lb);
            if (cos > 0.5f) continue;
            float best = 1e9f;
            for (int k = 0; k < m; k++) { float d = Anim.sq(wx[k] - ux[j]) + Anim.sq(wy[k] - uy[j]); if (d < best) best = d; }
            float d = (float) Math.sqrt(best) / pitch - 0.5f;
            if (d > 0) tot += d;
            c++;
        }
        return c == 0 ? 0f : tot / c;
    }

    private int fill(String w, float[] lcx, float[] lcy) {
        int m = 0, pk = -1;
        for (int i = 0; i < w.length(); i++) {
            char x = norm(w.charAt(i));
            int k = x < MAPN ? map[x] : -1;
            if (k < 0) return 0;
            if (k == pk) continue;
            wx[m] = lcx[k]; wy[m] = lcy[k]; m++; pk = k;
        }
        return m;
    }

    private float geo(int m, float plen) {
        float wl = 0;
        for (int i = 1; i < m; i++) wl += dist(wx[i] - wx[i - 1], wy[i] - wy[i - 1]);
        if (wl < 0.45f * plen || wl > 2.2f * plen) return -1f;
        resample(wx, wy, m, tx, ty);
        float d = 0, ws = 0;
        for (int i = 0; i < N; i++) {
            float w = (i < 3 || i >= N - 3) ? 2f : 1f;
            d += w * dist(ux[i] - tx[i], uy[i] - ty[i]); ws += w;
        }
        d = d / ws / pitch;
        return d > 2f ? -1f : d;
    }

    private float elastic() {
        float[] a = dtw0, b = dtw1;
        final float INF = 1e9f;
        for (int j = 0; j <= N; j++) a[j] = INF;
        a[0] = 0f;
        for (int i = 1; i <= N; i++) {
            for (int j = 0; j <= N; j++) b[j] = INF;
            int j0 = Math.max(1, i - BAND), j1 = Math.min(N, i + BAND);
            for (int j = j0; j <= j1; j++) {
                float best = a[j - 1];
                if (a[j] < best) best = a[j];
                if (b[j - 1] < best) best = b[j - 1];
                float we = (i <= 3 || i > N - 3 || j <= 3 || j > N - 3) ? WEND : 1f;
                b[j] = best + we * dist(ux[i - 1] - tx[j - 1], uy[i - 1] - ty[j - 1]);
            }
            float[] t = a; a = b; b = t;
        }
        return a[N] / (2f * N);
    }

    private void push(float g, int wi, String w, int freq) {
        float rough = g + 0.35f * (255 - freq) / 232f;
        int KK = K;
        int pos = poolN < KK ? poolN : KK;
        while (pos > 0 && poolS[pos - 1] > rough) pos--;
        if (pos >= KK) return;
        int last = Math.min(poolN, KK - 1);
        for (int j = last; j > pos; j--) { poolS[j] = poolS[j - 1]; poolI[j] = poolI[j - 1]; poolW[j] = poolW[j - 1]; poolG[j] = poolG[j - 1]; poolF[j] = poolF[j - 1]; }
        poolS[pos] = rough; poolI[pos] = wi; poolW[pos] = w; poolG[pos] = g; poolF[pos] = freq;
        if (poolN < KK) poolN++;
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
