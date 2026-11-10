package com.lightboard.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

final class Lexicon {
    static final int MAGIC = LexBuilder.MAGIC;

    private static Lexicon cur;
    private static String curLang, loadingLang;
    private static final HashSet<String> have = new HashSet<>();
    private static final HashMap<String, Long> seen = new HashMap<>();

    final int n;
    final byte[] freq;
    final int[] off;
    final byte[] ch;
    final char[] alpha;
    long stamp;
    private final boolean[] adj;
    private final String[] rows;
    private final int[] fold;
    private final long[] tri = new long[TRI_BITS >>> 6];

    private static final int TRI_BITS = 1 << 18;

    private static int triHash(int a, int b, int c) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA6B + c * 0xC2B2AE35;
        h ^= h >>> 15; h *= 0x2C1B3C6D; h ^= h >>> 12;
        return h & (TRI_BITS - 1);
    }

    private void addTri(int a, int b, int c) { int h = triHash(a, b, c); tri[h >>> 6] |= 1L << (h & 63); }

    private boolean hasTri(int a, int b, int c) { int h = triHash(a, b, c); return (tri[h >>> 6] & (1L << (h & 63))) != 0L; }

    private void buildTri() {
        for (int i = 0; i < n; i++) {
            int s = off[i], e = off[i + 1];
            int a = 0, b = 0;
            for (int k = s; k < e; k++) { int c = ch[k] & 0xFF; addTri(a, b, c); a = b; b = c; }
            addTri(a, b, 0);
        }
    }

    int unseenTriples(String w) {
        if (n < 5000) return -1;
        int a = 0, b = 0, bad = 0, wl = w.length();
        for (int i = 0; i < wl; i++) {
            int c = indexOf(w.charAt(i));
            if (c < 0) return 9;
            if (!hasTri(a, b, c)) bad++;
            a = b; b = c;
        }
        if (!hasTri(a, b, 0)) bad++;
        return bad;
    }

    private Lexicon(int n, byte[] freq, int[] off, byte[] ch, char[] alpha, long stamp, String[] rows) {
        this.n = n; this.freq = freq; this.off = off; this.ch = ch; this.alpha = alpha; this.stamp = stamp; this.rows = rows;
        int a = alpha.length;
        adj = new boolean[a * a];
        final float[] shift = {0f, 0.4f, 1.0f};
        for (int ra = 0; ra < rows.length; ra++) for (int ia = 0; ia < rows[ra].length(); ia++) {
            int x = indexOf(rows[ra].charAt(ia));
            if (x < 0) continue;
            for (int rb = 0; rb < rows.length; rb++) for (int ib = 0; ib < rows[rb].length(); ib++) {
                int y = indexOf(rows[rb].charAt(ib));
                if (y >= 0 && Math.abs(ra - rb) <= 1 && Math.abs((ia + shift[ra]) - (ib + shift[rb])) <= 1.2f && !(ra == rb && ia == ib)) adj[x * a + y] = true;
            }
        }
        fold = new int[a];
        for (int i = 1; i < a; i++) fold[i] = baseIndex(alpha[i]);
        buildTri();
    }

    private int baseIndex(char x) {
        if (!((x >= 0xC0 && x <= 0x24F) || (x >= 0x1E00 && x <= 0x1EFF) || (x >= 0x370 && x <= 0x3FF) || (x >= 0x1F00 && x <= 0x1FFF) || x == 0x451)) return 0;
        char b;
        switch (x) {
            case '\u00DF': b = 's'; break;
            case '\u0142': b = 'l'; break;
            case '\u0111': b = 'd'; break;
            case '\u00F8': b = 'o'; break;
            case '\u00E6': b = 'a'; break;
            case '\u0131': b = 'i'; break;
            case '\u00F0': b = 'd'; break;
            case '\u00FE': b = 't'; break;
            case '\u0153': b = 'o'; break;
            default: {
                String d = java.text.Normalizer.normalize(String.valueOf(x), java.text.Normalizer.Form.NFD);
                if (d.length() < 2) return 0;
                b = d.charAt(0);
            }
        }
        int k = indexOf(b);
        return k > 0 && alpha[k] != x ? k : 0;
    }

    int indexOf(char c) { int k = Arrays.binarySearch(alpha, 1, alpha.length, c); return k > 0 ? k : -1; }

    static synchronized Lexicon peek(String lang) { return lang.equals(curLang) ? cur : null; }

    private static int gen;

    static synchronized void invalidate() { cur = null; curLang = null; loadingLang = null; gen++; }

    static synchronized boolean has(String lang) { return have.contains(lang); }

    static File file(Context c, String lang) { return new File(c.getFilesDir(), "lex_" + lang + ".lex"); }

    private static String[] assetNames;
    private static long pkgRev = -1L;

    private static synchronized String[] assets(Context c) {
        if (assetNames == null) {
            String[] l = null;
            try { l = c.getAssets().list("lex"); } catch (IOException ignored) { }
            assetNames = l == null ? new String[0] : l;
        }
        return assetNames;
    }

    private static synchronized long rev(Context c) {
        if (pkgRev < 0) {
            long r = 0L;
            try { r = new File(c.getApplicationInfo().sourceDir).lastModified(); } catch (RuntimeException ignored) { }
            pkgRev = Math.max(1L, r);
        }
        return pkgRev;
    }

    private static File offFile(Context c, String lang) { return new File(c.getFilesDir(), "lex_" + lang + ".lex.off"); }

    static boolean builtin(Context c, String lang) {
        String n = "lex_" + lang + ".lex";
        for (String x : assets(c)) if (x.equals(n)) return !offFile(c, lang).exists();
        return false;
    }

    static boolean exists(Context c, String lang) { return file(c, lang).exists() || builtin(c, lang); }

    static InputStream open(Context c, String lang) throws IOException {
        File f = file(c, lang);
        if (f.exists()) return new FileInputStream(f);
        if (builtin(c, lang)) return c.getAssets().open("lex/lex_" + lang + ".lex");
        throw new java.io.FileNotFoundException(lang);
    }

    static java.util.List<String> ids(Context c) {
        java.util.TreeSet<String> r = new java.util.TreeSet<>();
        String[] fl = c.getFilesDir().list();
        if (fl != null) for (String f : fl) if (f.startsWith("lex_") && f.endsWith(".lex")) r.add(f.substring(4, f.length() - 4));
        for (String x : assets(c)) {
            if (!x.startsWith("lex_") || !x.endsWith(".lex")) continue;
            String lang = x.substring(4, x.length() - 4);
            if (!offFile(c, lang).exists()) r.add(lang);
        }
        return new java.util.ArrayList<>(r);
    }

    static void remove(Context c, String lang) {
        File f = file(c, lang);
        f.delete();
        new File(f.getPath() + ".tmp").delete();
        String n = "lex_" + lang + ".lex";
        for (String x : assets(c)) if (x.equals(n)) {
            try { new java.io.FileOutputStream(offFile(c, lang)).close(); } catch (IOException ignored) { }
            break;
        }
    }

    static void restore(Context c, String lang) { offFile(c, lang).delete(); }

    static synchronized void refreshIfChanged(Context c) {
        have.clear();
        for (String lang : ids(c)) note(c, lang);
        if (cur != null && cur.stamp != stampOf(c, curLang)) cur = null;
    }

    private static void note(Context c, String lang) {
        have.add(lang);
        String key = "lex_" + lang + ".lex";
        long m = stampOf(c, lang);
        Long old = seen.get(key);
        if (old != null && old == m) return;
        seen.put(key, m);
        try (InputStream in = open(c, lang)) {
            DataInputStream di = new DataInputStream(new BufferedInputStream(in, 1024));
            di.skipBytes(16);
            int k = di.readUnsignedByte();
            char[] a = new char[k];
            for (int i = 0; i < k; i++) a[i] = di.readChar();
            Layouts.alphabetChanged(lang, new String(a));
        } catch (IOException | RuntimeException ignored) { }
    }

    private static long stampOf(Context c, String lang) {
        File f = file(c, lang);
        if (f.exists()) return f.lastModified();
        return builtin(c, lang) ? rev(c) : 0L;
    }

    static void preload(final Context c, final String lang, final Runnable onDone) {
        final int g;
        synchronized (Lexicon.class) {
            if (lang.equals(curLang) && cur != null) { if (onDone != null) new Handler(Looper.getMainLooper()).post(onDone); return; }
            if (lang.equals(loadingLang)) return;
            loadingLang = lang; g = gen;
        }
        final Context app = c.getApplicationContext();
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                Lexicon l = null;
                try { l = load(app, lang); } catch (RuntimeException | OutOfMemoryError ignored) { }
                synchronized (Lexicon.class) {
                    if (g == gen) { if (l != null) { cur = l; curLang = lang; } loadingLang = null; }
                }
                if (onDone != null) new Handler(Looper.getMainLooper()).post(onDone);
            }
        }, "lex-load");
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    static Lexicon load(Context c, String lang) {
        if (exists(c, lang)) {
            long stamp = stampOf(c, lang);
            try (InputStream in = new BufferedInputStream(open(c, lang), 1 << 16)) { return read(in, stamp, lang); }
            catch (IOException | RuntimeException ignored) { }
            Lexicon b = empty(lang);
            b.stamp = stamp;
            return b;
        }
        return empty(lang);
    }

    private static Lexicon empty(String lang) {
        return new Lexicon(0, new byte[0], new int[]{0}, new byte[0], new char[1], 0L, rowsOf(lang));
    }

    static int count(Context c, String lang) {
        if (!exists(c, lang)) return 0;
        try (DataInputStream di = new DataInputStream(open(c, lang))) {
            di.readInt();
            return di.readInt();
        } catch (IOException e) { return 0; }
    }

    private static String[] rowsOf(String lang) {
        Layouts.L l = Layouts.byLang(lang);
        return l != null ? new String[]{l.r1, l.r2, l.r3} : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    }

    private static Lexicon read(InputStream in, long stamp, String lang) throws IOException {
        DataInputStream di = new DataInputStream(in);
        if (di.readInt() != MAGIC) throw new IOException("bad lexicon");
        int n = di.readInt(), total = di.readInt(), plen = di.readInt(), m = di.readUnsignedByte();
        if (n < 0 || n > 1_000_000 || total < 0 || total > 16_000_000 || plen < 0 || plen > 16_000_000 || m < 1 || m > 254) throw new IOException("bad lexicon header");
        char[] alpha = new char[m + 1];
        for (int i = 1; i <= m; i++) alpha[i] = di.readChar();
        byte[] freq = new byte[n];
        di.readFully(freq);
        InputStream pl = new BufferedInputStream(di, 1 << 15);
        int[] off = new int[n + 1];
        byte[] ch = new byte[total];
        int q = 0, pos = 0, prevS = 0, prevL = 0;
        for (int i = 0; i < n; i++) {
            if (q >= plen) throw new IOException("bad lexicon data");
            int pre = pl.read();
            q++;
            if (pre < 0) throw new IOException("bad lexicon eof");
            if (pre > prevL || pos + pre > total) throw new IOException("bad lexicon prefix");
            System.arraycopy(ch, prevS, ch, pos, pre);
            int s = pos;
            pos += pre;
            while (true) {
                if (q >= plen) throw new IOException("bad lexicon end");
                int b = pl.read();
                q++;
                if (b < 0) throw new IOException("bad lexicon eof");
                if (b == 0) break;
                if (pos >= total || b > m) throw new IOException("bad lexicon size");
                ch[pos++] = (byte) b;
            }
            off[i + 1] = pos; prevS = s; prevL = pos - s;
        }
        if (pos != total) throw new IOException("bad lexicon total");
        return new Lexicon(n, freq, off, ch, alpha, stamp, rowsOf(lang));
    }

    char at(int i) { return alpha[ch[i] & 0xFF]; }

    String word(int i) {
        int s = off[i], l = off[i + 1] - s;
        char[] t = new char[l];
        for (int k = 0; k < l; k++) t[k] = at(s + k);
        return new String(t);
    }

    private int compare(int i, String p) {
        int s = off[i], l = off[i + 1] - s, m = Math.min(l, p.length());
        for (int k = 0; k < m; k++) { int d = at(s + k) - p.charAt(k); if (d != 0) return d; }
        return l - p.length();
    }

    int lowerBound(String p) {
        int lo = 0, hi = n;
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (compare(mid, p) < 0) lo = mid + 1; else hi = mid; }
        return lo;
    }

    int prefixEnd(String p) {
        return lowerBound(p.substring(0, p.length() - 1) + (char) (p.charAt(p.length() - 1) + 1));
    }

    boolean contains(String w) { int i = lowerBound(w); return i < n && compare(i, w) == 0; }

    private final java.util.concurrent.ConcurrentHashMap<String, String[]> topCache = new java.util.concurrent.ConcurrentHashMap<>();

    int suggest(String p, String[] out) {
        if (p.isEmpty() || n == 0) return 0;
        if (p.length() > 2 || out.length > 3) return scan(p, out);
        String[] c = topCache.get(p);
        if (c == null) {
            String[] tmp = new String[3];
            c = Arrays.copyOf(tmp, scan(p, tmp));
            topCache.put(p, c);
        }
        int m = Math.min(c.length, out.length);
        System.arraycopy(c, 0, out, 0, m);
        return m;
    }

    private int scan(String p, String[] out) {
        int lo = lowerBound(p), hi = prefixEnd(p), plen = p.length();
        int k = out.length;
        int[] bi = new int[k], bf = new int[k];
        int found = 0;
        for (int i = lo; i < hi; i++) {
            int l = off[i + 1] - off[i];
            if (l <= plen) continue;
            int f = freq[i] & 0xFF;
            int pos = found < k ? found : k;
            while (pos > 0 && (bf[pos - 1] < f || (bf[pos - 1] == f && (off[bi[pos - 1] + 1] - off[bi[pos - 1]]) > l))) pos--;
            if (pos >= k) continue;
            for (int j = Math.min(found, k - 1); j > pos; j--) { bi[j] = bi[j - 1]; bf[j] = bf[j - 1]; }
            bi[pos] = i; bf[pos] = f;
            if (found < k) found++;
        }
        for (int j = 0; j < found; j++) out[j] = word(bi[j]);
        return found;
    }

    private static final float INF = 99f;

    private float wed(int[] q, int bi, float limit, float[] r0, float[] r1, float[] r2, float[] tc) {
        final int la = q.length, bs = off[bi], m = off[bi + 1] - bs, band = (int) limit, a = alpha.length;
        if (la - m > band || m - la > band) return INF;
        final byte[] ch = this.ch;
        final boolean[] tab = adj;
        final int[] fd = fold;
        float[] p2 = r0, p1 = r1, cur = r2;
        for (int j = 0; j <= m; j++) p1[j] = j;
        for (int i = 1; i <= la; i++) {
            final int lo = i - band > 0 ? i - band : 0, hi = i + band < m ? i + band : m;
            final int ac = q[i - 1];
            float rowMin = INF;
            int j = lo;
            if (j == 0) { cur[0] = i; rowMin = i; j = 1; }
            for (; j <= hi; j++) {
                final int bb = ch[bs + j - 1] & 0xFF;
                float v = p1[j - 1];
                if (ac != bb) {
                    if (ac > 0 && (fd[bb] == ac || fd[ac] == bb)) v += 0.12f;
                    else {
                        float tv = tc != null ? tc[(i - 1) * a + bb] : -1f;
                        v += tv >= 0f ? tv : ((ac > 0 && tab[ac * a + bb]) ? 0.5f : 1f);
                    }
                }
                float t;
                if (j <= i - 1 + band) { t = p1[j] + 1f; if (t < v) v = t; }
                if (j - 1 >= i - band) { t = cur[j - 1] + 1f; if (t < v) v = t; }
                if (i > 1 && j > 1 && ac == (ch[bs + j - 2] & 0xFF) && q[i - 2] == bb) {
                    t = p2[j - 2] + 0.7f; if (t < v) v = t;
                }
                cur[j] = v;
                if (v < rowMin) rowMin = v;
            }
            if (rowMin > limit) return INF;
            float[] tmp = p2; p2 = p1; p1 = cur; cur = tmp;
        }
        return p1[m];
    }

    String correct(String w) { return correct(w, 0L); }

    /** @param budgetNs 0 = unlimited; otherwise gives up (returns null) when the search takes longer than this. */
    String correct(String w, long budgetNs) { return search(w, budgetNs, false, null); }

    String correct(String w, long budgetNs, Touch t) { return search(w, budgetNs, false, t); }

    /** Same as above; timedOut[0] is set when the search was cut short by the budget (a null result is then not a verdict). */
    String correct(String w, long budgetNs, boolean[] timedOut) { return search(w, budgetNs, false, null, timedOut); }

    String autoCorrect(String w, long budgetNs) { return search(w, budgetNs, true, null); }

    String autoCorrect(String w, long budgetNs, Touch t) { return search(w, budgetNs, true, t); }

    private float[] touchCosts(String w, Touch t) {
        final int a = alpha.length, wl = w.length();
        float[] tc = new float[wl * a];
        for (int i = 0; i < wl; i++) {
            int cnt = t.n[i], base = i * a;
            if (cnt == 0) { for (int k = 0; k < a; k++) tc[base + k] = -1f; continue; }
            for (int k = 0; k < a; k++) tc[base + k] = 1f;
            float d0 = t.d2[i][0];
            for (int k = 0; k < cnt; k++) if (t.ch[i][k] == t.typed[i]) { d0 = t.d2[i][k]; break; }
            for (int k = 0; k < cnt; k++) {
                int x = indexOf(t.ch[i][k]);
                if (x < 0 || t.ch[i][k] == t.typed[i]) continue;
                float c = 0.1f + 0.25f * Math.max(0f, t.d2[i][k] - d0) / 0.3f;
                tc[base + x] = c > 1f ? 1f : c;
            }
        }
        return tc;
    }

    private String search(String w, long budgetNs, boolean auto, Touch t) { return search(w, budgetNs, auto, t, null); }

    private String search(String w, long budgetNs, boolean auto, Touch t, boolean[] timedOut) {
        final long deadline = budgetNs > 0 ? System.nanoTime() + budgetNs : 0L;
        int wl = w.length();
        if (n == 0 || wl < 3 || wl > 20 || contains(w)) return null;
        float limit = wl <= 4 ? 1.0f : 1.55f, bestScore = 1e9f, bestCost = 0f;
        final int band = (int) limit, a = alpha.length;
        int best = -1;
        final float[] r0 = new float[48], r1 = new float[48], r2 = new float[48];
        final int[] q = new int[wl];
        for (int i = 0; i < wl; i++) q[i] = indexOf(w.charAt(i));
        final float[] tc = t != null && t.matches(w) ? touchCosts(w, t) : null;
        char c0 = w.charAt(0), c1 = w.charAt(1);
        final int q0 = q[0], q1 = q[1];
        for (int fi = 1; fi < a; fi++) {
            char fc = alpha[fi];
            int bf = fold[fi] > 0 ? fold[fi] : fi;
            if (!(fc == c0 || fc == c1 || bf == q0 || bf == q1 || (q0 > 0 && (adj[q0 * a + fi] || adj[q0 * a + bf])))) continue;
            boolean same = fc == c0 || (q0 > 0 && (fold[fi] == q0 || fold[q0] == fi));
            int lo = lowerBound(String.valueOf(fc)), hi = lowerBound(String.valueOf((char) (fc + 1)));
            for (int i = lo; i < hi; i++) {
                if (deadline != 0L && (i & 127) == 0 && System.nanoTime() > deadline) { if (timedOut != null) timedOut[0] = true; return null; }
                int l = off[i + 1] - off[i];
                if (l - wl > band || wl - l > band) continue;
                float cost = wed(q, i, limit, r0, r1, r2, tc);
                if (cost > limit) continue;
                if (!same) cost += 0.3f;
                float score = cost - 0.35f * (freq[i] & 0xFF) / 255f;
                if (score < bestScore && cost <= limit) { bestScore = score; best = i; bestCost = cost; }
            }
        }
        if (best < 0) return null;
        if (auto) {
            int un = unseenTriples(w);
            if (un >= 0 && -0.0495f * (255 - (freq[best] & 0xFF)) - 4f * bestCost + 0.8f * wl + 5f * un < -6f) return null;
        }
        return word(best);
    }

    private int find(String w) { int i = lowerBound(w); return i < n && compare(i, w) == 0 ? i : -1; }

    String split(String w) {
        int wl = w.length();
        if (n == 0 || wl < 5 || wl > 24) return null;
        int best = -1, bestF = 0;
        for (int k = 2; k <= wl - 2; k++) {
            int i = find(w.substring(0, k));
            if (i < 0) continue;
            int j = find(w.substring(k));
            if (j < 0) continue;
            int f = Math.min(freq[i] & 0xFF, freq[j] & 0xFF);
            if (f > bestF) { bestF = f; best = k; }
        }
        return best < 0 || bestF < 70 ? null : w.substring(0, best) + " " + w.substring(best);
    }
}
