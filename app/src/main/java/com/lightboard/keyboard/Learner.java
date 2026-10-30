package com.lightboard.keyboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Самообучение без Android-зависимостей: частота своих слов, пары «слово -> следующее слово» и слова, которые
 * пользователь велел не исправлять. Всё хранится короткими строками; форматы слов совместимы с прежними версиями.
 */
final class Learner {
    private static final int WORDS_MAX = 1500, WORDS_KEEP = 1200, PAIRS_MAX = 800, PAIRS_KEEP = 600, USER_MAX = 800, COUNT_MAX = 60;
    static final String END = ".!?";

    private final HashMap<String, Integer> words = new HashMap<>();  // слово -> сколько раз набрано
    private final HashMap<String, Integer> pairs = new HashMap<>();  // «пред>след» -> сколько раз; пред пустое в начале предложения
    private final HashSet<String> user = new HashSet<>();            // «не исправлять»
    private final HashSet<String> own = new HashSet<>();            // слова, которых нет в словаре языка: настоящие «свои»
    private boolean wordsDirty, pairsDirty, userDirty, ownDirty;

    // ---------- слова ----------
    interface Has { boolean has(String w); }

    private static final String VOWELS = "aeiouyаеёиоуыэюя";
    private static final String[] ROWS = {"йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final String[] ROWS_REV = new String[ROWS.length];
    static { for (int i = 0; i < ROWS.length; i++) ROWS_REV[i] = new StringBuilder(ROWS[i]).reverse().toString(); }

    /** Буква слова: обычная буква или знак гласной в тайском и индийских письменностях. */
    static boolean wordChar(char c) { return Character.isLetter(c) || LexBuilder.mark(c); }

    /** Слово годится для хранения: только буквы, апостроф и дефис (в сохранённой строке пробел, «:» и «>» — разделители). */
    static boolean wordOk(String w) {
        int n = w.length();
        if (n < 1 || n > 24) return false;
        for (int i = 0; i < n; i++) { char c = w.charAt(i); if (!wordChar(c) && c != '\'' && c != '-') return false; }
        return true;
    }

    /** Пять соседних букв одного ряда подряд (в любую сторону) — это «рука проехалась по клавиатуре»: фывап, asdfg. */
    private static boolean rowRun(String w) {
        for (int i = 0; i + 5 <= w.length(); i++) {
            String s = w.substring(i, i + 5);
            for (int k = 0; k < ROWS.length; k++) if (ROWS[k].contains(s) || ROWS_REV[k].contains(s)) return true;
        }
        return false;
    }

    /** Правдоподобное слово: нет «ааа», нет слитка согласных (фывпр), нет смеси алфавитов. */
    static boolean plausible(String w) {
        int n = w.length(), run = 1, cons = 0;
        boolean vowel = false, lat = false, cyr = false;
        for (int i = 0; i < n; i++) {
            char c = w.charAt(i);
            if (!wordChar(c) && c != '\'' && c != '-') return false;
            if (i > 0 && c == w.charAt(i - 1)) { if (++run >= 3) return false; } else run = 1;
            if (c >= 'a' && c <= 'z') lat = true; else if (c >= 0x400 && c <= 0x4FF) cyr = true;
            if (VOWELS.indexOf(c) >= 0) { vowel = true; cons = 0; } else if (((c >= 'a' && c <= 'z') || (c >= 0xC0 && c <= 0x24F) || (c >= 0x400 && c <= 0x4FF)) && ++cons > 4) return false; // слитки согласных проверяем только в латинице и кириллице: в других письменностях гласные не буквы из таблицы
        }
        return !(lat && cyr) && (n < 3 || vowel || !(lat || cyr)) && !rowRun(w);
    }

    /**
     * kind: 0 — слово из словаря (учитываем частоту для подсказок), 1 — нет в словаре и не похоже ни на одно (имя, сленг: своё сразу),
     * 2 — нет в словаре, но похоже на опечатку (своим становится только после 4 наборов), 3 — словарь недоступен (только частота).
     * Неправдоподобное не из словаря не запоминаем вообще.
     */
    void bump(String low, int kind) {
        if (low.length() < 2 || !wordOk(low)) return;
        if ((kind == 1 || kind == 2) && !plausible(low)) return;
        Integer c = words.get(low);
        int n = c == null ? 1 : Math.min(c + 1, COUNT_MAX);
        words.put(low, n);
        wordsDirty = true;
        if ((kind == 1 || (kind == 2 && n >= 4)) && own.add(low)) ownDirty = true;
        trim(words, WORDS_MAX, WORDS_KEEP);
    }

    void bump(String low) { bump(low, 1); }

    /** Слово своё: велели не исправлять, или оно «своё» и набрано не меньше двух раз. */
    boolean known(String low) {
        if (user.contains(low)) return true;
        Integer c = words.get(low);
        return c != null && c >= 2 && own.contains(low);
    }

    int count(String low) { Integer c = words.get(low); return c == null ? 0 : c; }

    /** Свои слова (набранные не меньше двух раз) для непрерывного ввода. */
    int own(String[] w, int[] cnt) {
        int n = 0;
        for (String s : own) {
            Integer c = words.get(s);
            if (c == null || c < 2) continue;
            if (n == w.length) break;
            w[n] = s; cnt[n] = c; n++;
        }
        return n;
    }

    /** Одноразово после обновления: убрать из «своих» слова, которые на самом деле есть в словаре. */
    void pruneOwn(Has dict) {
        java.util.Iterator<String> it = own.iterator();
        while (it.hasNext()) if (dict.has(it.next())) { it.remove(); ownDirty = true; }
    }

    boolean isUser(String low) { return user.contains(low); }

    boolean learnUser(String low) {
        if (low.length() < 2 || !wordOk(low) || user.size() >= USER_MAX || !user.add(low)) return false;
        userDirty = true;
        return true;
    }

    /** Самое частое своё слово, которое продолжает набранное начало. */
    String best(String prefixLow) {
        String best = null;
        int bc = 1;
        for (Map.Entry<String, Integer> e : words.entrySet()) {
            String k = e.getKey();
            if (e.getValue() > bc && k.length() > prefixLow.length() && k.startsWith(prefixLow)) { best = k; bc = e.getValue(); }
        }
        return best;
    }

    // ---------- пары ----------
    /** prev — предыдущее слово или пустая строка в начале предложения. */
    void pair(String prev, String next) {
        if (prev == null || !wordOk(next) || (!prev.isEmpty() && !wordOk(prev))) return;
        String k = prev + '>' + next;
        Integer c = pairs.get(k);
        pairs.put(k, c == null ? 1 : Math.min(c + 1, COUNT_MAX));
        pairsDirty = true;
        trim(pairs, PAIRS_MAX, PAIRS_KEEP);
    }

    /** Что чаще всего шло после prev: кладёт до out.length слов начиная с индекса from, возвращает новое число слов. */
    int next(String prev, String[] out, int from) {
        int n = from;
        int[] cnt = new int[out.length];
        String pre = prev + '>';
        for (Map.Entry<String, Integer> e : pairs.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith(pre)) continue;
            n = insert(out, cnt, n, from, k.substring(pre.length()), e.getValue());
        }
        return n;
    }

    /** Добирает самые частые свои слова (набранные не меньше minCount раз), без повторов. */
    int frequent(String[] out, int from, int minCount) {
        int n = from;
        if (n >= out.length) return n;
        int[] cnt = new int[out.length];
        for (int i = 0; i < from; i++) cnt[i] = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> e : words.entrySet()) {
            if (e.getValue() < minCount) continue;
            n = insert(out, cnt, n, from, e.getKey(), e.getValue());
        }
        return n;
    }

    /** Вставка в упорядоченный по убыванию счётчика топ out[from..], без повторов того, что уже есть в out. */
    private static int insert(String[] out, int[] cnt, int n, int from, String w, int c) {
        for (int i = 0; i < n; i++) if (out[i].equals(w)) return n;
        int pos = n < out.length ? n : out.length;
        while (pos > from && cnt[pos - 1] < c) pos--;
        if (pos >= out.length) return n;
        int last = n < out.length ? n : out.length - 1;
        for (int i = last; i > pos; i--) { out[i] = out[i - 1]; cnt[i] = cnt[i - 1]; }
        out[pos] = w; cnt[pos] = c;
        return n < out.length ? n + 1 : n;
    }

    /** Слово перед позицией s (пробелы пропускаются); "" — начало предложения или поля; null — знак, цифра, обрезанный текст. */
    static String prevWord(CharSequence b, int s, int window) {
        int k = s - 1;
        while (k >= 0 && b.charAt(k) == ' ') k--;
        if (k < 0) return b.length() < window ? "" : null;
        char c = b.charAt(k);
        if (END.indexOf(c) >= 0 || c == '\n') return "";
        int e = k + 1, st = e;
        while (st > 0 && Learner.wordChar(b.charAt(st - 1))) st--;
        if (st == e) return null;
        if (st > 0) { char pc = b.charAt(st - 1); if (pc == '\'' || pc == '@' || pc == '-' || pc == '_' || pc == '/' || pc == '.' || pc == '#' || Character.isDigit(pc)) return null; }
        return b.subSequence(st, e).toString().toLowerCase(java.util.Locale.ROOT);
    }

    /** Не даёт карте расти: сначала выбрасывает редкое, при необходимости «старит» остальное. */
    private static void trim(HashMap<String, Integer> m, int max, int keep) {
        if (m.size() <= max) return;
        for (int lim = 1; lim <= 3 && m.size() > keep; lim++) {
            Iterator<Map.Entry<String, Integer>> it = m.entrySet().iterator();
            while (it.hasNext() && m.size() > keep) if (it.next().getValue() <= lim) it.remove();
        }
        Iterator<Map.Entry<String, Integer>> it = m.entrySet().iterator();
        while (it.hasNext() && m.size() > keep) { it.next(); it.remove(); }
    }

    // ---------- сохранение ----------
    void loadWords(String t) { load(words, t, WORDS_MAX, false); }
    void loadPairs(String t) { load(pairs, t, PAIRS_MAX, true); }
    /** null — первый запуск после обновления: своими считаем слова, набранные не меньше двух раз (лишнее потом уберёт pruneOwn). */
    void loadOwn(String t) {
        if (t == null) { for (java.util.Map.Entry<String, Integer> e : words.entrySet()) if (e.getValue() >= 2 && plausible(e.getKey())) own.add(e.getKey()); ownDirty = true; return; }
        if (!t.isEmpty()) for (String w : t.split(" ")) if (wordOk(w) && own.size() < WORDS_MAX) own.add(w);
    }

    String saveOwn() {
        if (!ownDirty) return null;
        ownDirty = false;
        StringBuilder sb = new StringBuilder();
        for (String w : own) if (words.containsKey(w)) sb.append(w).append(' ');
        return sb.toString().trim();
    }

    void loadUser(String t) { if (!t.isEmpty()) for (String w : t.split(" ")) if (wordOk(w) && user.size() < USER_MAX) user.add(w); }

    /** Чужие данные (файл копии, старая версия): не больше max записей, длина и счётчик в допустимых границах. */
    private static void load(HashMap<String, Integer> m, String t, int max, boolean pair) {
        if (t.isEmpty()) return;
        for (String e : t.split(" ")) {
            if (m.size() >= max) break;
            int i = e.lastIndexOf(':');
            if (i < 1 || i > 49) continue;
            String key = e.substring(0, i);
            if (pair) { int gt = key.indexOf('>'); if (gt < 0 || !wordOk(key.substring(gt + 1)) || (gt > 0 && !wordOk(key.substring(0, gt)))) continue; }
            else if (!wordOk(key)) continue;
            try { m.put(key, Math.max(1, Math.min(COUNT_MAX, Integer.parseInt(e.substring(i + 1))))); } catch (NumberFormatException ignored) { }
        }
    }

    private static String dump(HashMap<String, Integer> m) {
        StringBuilder sb = new StringBuilder(m.size() * 12);
        for (Map.Entry<String, Integer> e : m.entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append(' ');
        return sb.toString().trim();
    }

    /** null — менять нечего (с прошлого сохранения ничего не изменилось). */
    String saveWords() { if (!wordsDirty) return null; wordsDirty = false; return dump(words); }
    String savePairs() { if (!pairsDirty) return null; pairsDirty = false; return dump(pairs); }
    String saveUser() {
        if (!userDirty) return null;
        userDirty = false;
        StringBuilder sb = new StringBuilder();
        for (String w : user) sb.append(w).append(' ');
        return sb.toString().trim();
    }

    /** Забыть слово: убрать из счётчиков, из «не исправлять» и из всех пар, где оно встречается. */
    void forget(String low) {
        if (words.remove(low) != null) wordsDirty = true;
        if (own.remove(low)) ownDirty = true;
        if (user.remove(low)) userDirty = true;
        Iterator<String> it = pairs.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            int i = k.indexOf('>');
            if (i >= 0 && (k.substring(0, i).equals(low) || k.substring(i + 1).equals(low))) { it.remove(); pairsDirty = true; }
        }
    }

    void markAllDirty() { wordsDirty = pairsDirty = userDirty = ownDirty = true; }

    void clear() { words.clear(); pairs.clear(); user.clear(); own.clear(); wordsDirty = pairsDirty = userDirty = ownDirty = false; }
}
