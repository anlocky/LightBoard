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

/**
 * Словарь одного языка одним блоком: слова по алфавиту в одном byte[] (1 байт на букву, номер буквы в алфавите словаря)
 * + частота (1 байт) на слово. Алфавит хранится в самом файле, поэтому подходит любой язык (до 254 букв).
 * Поиск по префиксу и по первой букве — бинарным поиском. В памяти держится только словарь активного языка.
 * Файл lex_&lt;язык&gt;.lex берётся из filesDir (импортированный), иначе из assets (встроенный).
 */
final class Lexicon {
    static final int MAGIC = 0x4C455834; // LEX4

    private static Lexicon cur;
    private static String curLang, loadingLang;
    private static final HashSet<String> have = new HashSet<>();      // языки, для которых словарь есть
    private static final HashMap<String, Long> seen = new HashMap<>(); // файл -> время изменения: заголовок читаем только при изменении

    final int n;
    final byte[] freq;
    final int[] off;
    final byte[] ch;
    final char[] alpha; // alpha[0] не используется, байт буквы = номер в алфавите (по возрастанию кода)
    long stamp; // у запасного словаря совпадает со временем файла, который не удалось прочитать: иначе он перечитывался бы при каждом входе в поле
    private final boolean[] adj; // соседние клавиши (по раскладке языка): замена на соседнюю букву дешевле
    private final String[] rows;
    private final int[] fold; // буква с диакритикой -> номер той же буквы без неё (e вместо é): такая замена почти ничего не стоит

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
    }

    /** Номер буквы без диакритики в алфавите словаря или 0. Только латиница, греческий и «ё»: в остальных письменностях такие пары — разные буквы. */
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

    /** Номер буквы в алфавите словаря или -1. */
    int indexOf(char c) { int k = Arrays.binarySearch(alpha, 1, alpha.length, c); return k > 0 ? k : -1; }

    // ---------- какие словари есть и какой загружен ----------
    /** Уже загруженный словарь языка или null (не блокирует поток интерфейса). */
    static synchronized Lexicon peek(String lang) { return lang.equals(curLang) ? cur : null; }

    private static int gen; // поколение: загрузка, начатая до invalidate(), результат не публикует (словарь не остаётся в памяти зря)

    static synchronized void invalidate() { cur = null; curLang = null; loadingLang = null; gen++; }

    static synchronized boolean has(String lang) { return have.contains(lang); }

    static File file(Context c, String lang) { return new File(c.getFilesDir(), "lex_" + lang + ".lex"); }

    /** Настройки работают в другом процессе: смотрим, какие словари появились или изменились. */
    static synchronized void refreshIfChanged(Context c) {
        have.clear();
        String[] fl = c.getFilesDir().list();
        if (fl != null) for (String f : fl) note(c, f, false);
        try { String[] as = c.getAssets().list(""); if (as != null) for (String f : as) note(c, f, true); } catch (IOException ignored) { }
        if (cur != null && cur.stamp != stampOf(c, curLang)) cur = null;
    }

    private static void note(Context c, String f, boolean asset) {
        if (!f.startsWith("lex_") || !f.endsWith(".lex")) return;
        String lang = f.substring(4, f.length() - 4);
        if (!asset || !have.contains(lang)) have.add(lang);
        long m = asset ? -1L : new File(c.getFilesDir(), f).lastModified();
        Long old = seen.get(f);
        if (old != null && old == m) return;
        seen.put(f, m);
        try (InputStream in = asset ? c.getAssets().open(f) : new FileInputStream(new File(c.getFilesDir(), f))) {
            DataInputStream di = new DataInputStream(new BufferedInputStream(in, 1024));
            di.skipBytes(16);
            int k = di.readUnsignedByte();
            char[] a = new char[k];
            for (int i = 0; i < k; i++) a[i] = di.readChar();
            Layouts.alphabetChanged(lang, new String(a)); // из него у языков без своей раскладки берутся буквы с диакритикой
        } catch (IOException | RuntimeException ignored) { }
    }

    private static long stampOf(Context c, String lang) {
        File f = file(c, lang);
        return f.exists() ? f.lastModified() : 0L;
    }

    /** Грузит в фоне; onDone выполняется в главном потоке (можно null). */
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
        File f = file(c, lang);
        if (f.exists()) {
            try (InputStream in = new BufferedInputStream(new FileInputStream(f), 1 << 16)) { return read(in, f.lastModified(), lang); }
            catch (IOException | RuntimeException ignored) { }
            Lexicon b = loadBuiltin(c, lang); // файл битый: берём встроенный, но помним время файла (иначе перечитывали бы каждый раз)
            b.stamp = f.lastModified();
            return b;
        }
        return loadBuiltin(c, lang);
    }

    /** Встроенный словарь из assets (игнорирует импортированный). */
    static Lexicon loadBuiltin(Context c, String lang) {
        try (InputStream in = c.getAssets().open("lex_" + lang + ".lex")) { return read(in, 0L, lang); }
        catch (IOException | RuntimeException e) { return new Lexicon(0, new byte[0], new int[]{0}, new byte[0], new char[1], 0L, rowsOf(lang)); }
    }

    /** Число слов в словаре языка (читает только заголовок файла), 0 — словаря нет. */
    static int count(Context c, String lang) {
        File f = file(c, lang);
        try (DataInputStream di = new DataInputStream(f.exists() ? new FileInputStream(f) : c.getAssets().open("lex_" + lang + ".lex"))) {
            di.readInt();
            return di.readInt();
        } catch (IOException e) { return 0; }
    }

    private static String[] rowsOf(String lang) {
        Layouts.L l = Layouts.byLang(lang);
        return l != null ? new String[]{l.r1, l.r2, l.r3} : new String[]{"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    }

    /**
     * LEX4: int магия, int число слов, int число букв всего, int длина блока, byte число букв алфавита, char[] алфавит,
     * byte[n] частоты, затем для каждого слова: длина общего начала с прошлым словом, хвост (номера букв), 0.
     * Весь блок читается одним readFully: без тысяч мелких чтений ассета.
     */
    private static Lexicon read(InputStream in, long stamp, String lang) throws IOException {
        DataInputStream di = new DataInputStream(in);
        if (di.readInt() != MAGIC) throw new IOException("bad lexicon");
        int n = di.readInt(), total = di.readInt(), plen = di.readInt(), m = di.readUnsignedByte();
        if (n < 0 || n > 1_000_000 || total < 0 || total > 16_000_000 || plen < 0 || plen > 16_000_000 || m < 1 || m > 254) throw new IOException("bad lexicon header"); // битый файл: не выделяем гигантские массивы
        char[] alpha = new char[m + 1];
        for (int i = 1; i <= m; i++) alpha[i] = di.readChar();
        byte[] freq = new byte[n];
        di.readFully(freq);
        InputStream pl = new BufferedInputStream(di, 1 << 15); // блок префиксов читается потоком: второй такой же массив в памяти не нужен
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
                if (b == 0) break; // завершающий 0
                if (pos >= total || b > m) throw new IOException("bad lexicon size");
                ch[pos++] = (byte) b;
            }
            off[i + 1] = pos; prevS = s; prevL = pos - s;
        }
        if (pos != total) throw new IOException("bad lexicon total");
        return new Lexicon(n, freq, off, ch, alpha, stamp, rowsOf(lang));
    }

    // ---------- доступ ----------
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

    /** Первый индекс слова, большего любого слова с префиксом p. */
    int prefixEnd(String p) {
        return lowerBound(p.substring(0, p.length() - 1) + (char) (p.charAt(p.length() - 1) + 1));
    }

    boolean contains(String w) { int i = lowerBound(w); return i < n && compare(i, w) == 0; }

    /** До out.length самых частотных продолжений префикса (сам префикс не включается). */
    int suggest(String p, String[] out) {
        if (p.isEmpty() || n == 0) return 0;
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

    // ---------- исправление опечаток ----------
    private static final float INF = 99f;

    /**
     * Взвешенное расстояние Дамерау–Левенштейна: соседние клавиши дешевле, перестановка 0.7.
     * Считается только в полосе |i-j| <= band, где band = (int) limit: любая клетка дальше от диагонали требует
     * не меньше band+1 вставок/удалений (по 1.0 каждая), то есть заведомо дороже limit, поэтому результат
     * (расстояние, если оно <= limit, иначе «много») точно такой же, как у полного расчёта, но клеток в 3 раза меньше.
     * q — номера букв слова в алфавите словаря (-1: такой буквы в словаре нет).
     */
    private float wed(int[] q, int bi, float limit, float[] r0, float[] r1, float[] r2) {
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
                if (ac != bb) v += (ac > 0 && (fd[bb] == ac || fd[ac] == bb)) ? 0.12f : ((ac > 0 && tab[ac * a + bb]) ? 0.5f : 1f);
                float t;
                if (j <= i - 1 + band) { t = p1[j] + 1f; if (t < v) v = t; }      // клетка (i-1, j) в полосе
                if (j - 1 >= i - band) { t = cur[j - 1] + 1f; if (t < v) v = t; } // клетка (i, j-1) в полосе
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

    /** Лучшая правка слова (в нижнем регистре) или null, если слово уже известно / уверенной правки нет. */
    String correct(String w) {
        int wl = w.length();
        if (n == 0 || wl < 3 || wl > 20 || contains(w)) return null;
        float limit = wl <= 4 ? 1.0f : 1.55f, bestScore = 1e9f;
        final int band = (int) limit, a = alpha.length;
        int best = -1;
        final float[] r0 = new float[48], r1 = new float[48], r2 = new float[48]; // свои на вызов: correct() работает и в фоновом потоке
        final int[] q = new int[wl];
        for (int i = 0; i < wl; i++) q[i] = indexOf(w.charAt(i));
        char c0 = w.charAt(0), c1 = w.charAt(1); // c1: «hte» -> «the» (первые две буквы переставлены)
        final int q0 = q[0], q1 = q[1];
        for (int fi = 1; fi < a; fi++) { // первая буква кандидата: набранная, соседняя клавиша, вторая (перестановка) или та же буква с диакритикой
            char fc = alpha[fi];
            int bf = fold[fi] > 0 ? fold[fi] : fi;
            if (!(fc == c0 || fc == c1 || bf == q0 || bf == q1 || (q0 > 0 && (adj[q0 * a + fi] || adj[q0 * a + bf])))) continue;
            boolean same = fc == c0 || (q0 > 0 && (fold[fi] == q0 || fold[q0] == fi));
            int lo = lowerBound(String.valueOf(fc)), hi = lowerBound(String.valueOf((char) (fc + 1)));
            for (int i = lo; i < hi; i++) {
                int l = off[i + 1] - off[i];
                if (l - wl > band || wl - l > band) continue; // разница длин дороже порога
                float cost = wed(q, i, limit, r0, r1, r2);
                if (cost > limit) continue;
                if (!same) cost += 0.3f; // ошибка в первой букве встречается реже
                float score = cost - 0.35f * (freq[i] & 0xFF) / 255f;
                if (score < bestScore && cost <= limit) { bestScore = score; best = i; }
            }
        }
        return best < 0 ? null : word(best);
    }

    private int find(String w) { int i = lowerBound(w); return i < n && compare(i, w) == 0 ? i : -1; }

    /** Слитное слово -> «слово слово» (пропущен пробел), если обе части (от двух букв) из частых слов словаря; иначе null. */
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
        return best < 0 || bestF < 70 ? null : w.substring(0, best) + " " + w.substring(best); // 70 — примерно первые 10 000 слов
    }
}
