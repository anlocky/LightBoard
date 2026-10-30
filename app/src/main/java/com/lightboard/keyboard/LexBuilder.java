package com.lightboard.keyboard;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

/**
 * Собирает словарь языка из списка «слово частота» (формат FrequencyWords, от частых к редким) в формат LEX4.
 * Берутся только слова из букв письменности раскладки этого языка; алфавит определяется по самим словам.
 */
final class LexBuilder {
    private LexBuilder() { }

    /** Письменность буквы: 1 латиница, 2 кириллица, 3 греческий, 4 иврит, 5 арабское письмо, 6 армянский, 7 грузинский, 8 тайский, 9 деванагари, 10 бенгальский, 11 тамильский, 12 телугу, 13 малаялам, 14 сингальский, 0 прочее. */
    static int sc(char c) {
        if ((c >= 'a' && c <= 'z') || (c >= 0xC0 && c <= 0x24F) || (c >= 0x1E00 && c <= 0x1EFF)) return 1;
        if (c >= 0x400 && c <= 0x52F) return 2;
        if ((c >= 0x370 && c <= 0x3FF) || (c >= 0x1F00 && c <= 0x1FFF)) return 3;
        if (c >= 0x5D0 && c <= 0x5EA) return 4;
        if ((c >= 0x600 && c <= 0x6FF) || (c >= 0x750 && c <= 0x77F)) return 5;
        if (c >= 0x531 && c <= 0x58F) return 6;
        if (c >= 0x10A0 && c <= 0x10FF) return 7;
        if (c >= 0xE01 && c <= 0xE5B) return 8;
        if (c >= 0x900 && c <= 0x97F) return 9;
        if (c >= 0x980 && c <= 0x9FF) return 10;
        if (c >= 0xB80 && c <= 0xBFF) return 11;
        if (c >= 0xC00 && c <= 0xC7F) return 12;
        if (c >= 0xD00 && c <= 0xD7F) return 13;
        if (c >= 0xD80 && c <= 0xDFF) return 14;
        return 0;
    }

    /** Знак гласной или другой знак слога: в тайском и индийских письменностях он входит в слово. */
    static boolean mark(char c) {
        if (c < 0x900) return false;
        int t = Character.getType(c);
        return t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK;
    }

    private static boolean ok(String w, int script, int rank) {
        int n = w.length();
        if (n < 1 || n > 24 || (n == 1 && rank > 300)) return false; // одиночные буквы — только самые частые («a», «я»)
        for (int i = 0; i < n; i++) { char c = w.charAt(i); if (!(Character.isLetter(c) || (script >= 8 && mark(c))) || sc(c) != script) return false; }
        return true;
    }

    /** Возвращает число слов в записанном словаре. */
    static int build(String lang, InputStream txt, int top, File out) throws IOException {
        final int script = Layouts.script(lang);
        BufferedReader br = new BufferedReader(new InputStreamReader(txt, "UTF-8"), 1 << 16);
        ArrayList<String> words = new ArrayList<>(top + 1024);
        HashSet<String> seen = new HashSet<>(top * 2);
        String line;
        int rank = 0;
        while (words.size() < top && (line = br.readLine()) != null) {
            int sp = line.indexOf(' ');
            String w = (sp < 0 ? line : line.substring(0, sp)).trim().toLowerCase(Locale.ROOT);
            rank++;
            if (ok(w, script, rank) && seen.add(w)) words.add(w);
        }
        int[] cnt = new int[0x2000];
        for (String w : words) for (int i = 0; i < w.length(); i++) cnt[w.charAt(i)]++;
        ArrayList<Integer> letters = new ArrayList<>();
        for (int c = 0; c < cnt.length; c++) if (cnt[c] > 0) letters.add(c);
        if (letters.size() > 254) { // слишком много букв: оставляем самые частые, слова с остальными выбрасываем
            final int[] cc = cnt;
            java.util.Collections.sort(letters, new Comparator<Integer>() { @Override public int compare(Integer a, Integer b) { return cc[b] - cc[a]; } });
            boolean[] drop = new boolean[cnt.length];
            for (int i = 254; i < letters.size(); i++) drop[letters.get(i)] = true;
            ArrayList<String> keep = new ArrayList<>(words.size());
            outer:
            for (String w : words) { for (int i = 0; i < w.length(); i++) if (drop[w.charAt(i)]) continue outer; keep.add(w); }
            words = keep;
            letters = new ArrayList<>(letters.subList(0, 254));
            java.util.Collections.sort(letters);
        }
        final int n = words.size();
        if (n < 1000) throw new IOException("слишком мало слов: " + n);
        final int m = letters.size();
        int[] idx = new int[cnt.length];
        char[] alpha = new char[m];
        for (int i = 0; i < m; i++) { alpha[i] = (char) (int) letters.get(i); idx[alpha[i]] = i + 1; }
        final String[] arr = words.toArray(new String[0]);
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, new Comparator<Integer>() { @Override public int compare(Integer a, Integer b) { return arr[a].compareTo(arr[b]); } });
        ByteArrayOutputStream pl = new ByteArrayOutputStream(n * 4);
        byte[] fr = new byte[n];
        int total = 0;
        String prev = "";
        for (int k = 0; k < n; k++) {
            int i = order[k];
            String w = arr[i];
            fr[k] = (byte) Math.max(1, 255 - (int) Math.round(14 * Math.log(i + 1) / Math.log(2)));
            int p = 0, mm = Math.min(w.length(), prev.length());
            while (p < mm && w.charAt(p) == prev.charAt(p)) p++;
            pl.write(p);
            for (int j = p; j < w.length(); j++) pl.write(idx[w.charAt(j)]);
            pl.write(0);
            total += w.length();
            prev = w;
        }
        File tmp = new File(out.getPath() + ".tmp");
        try (DataOutputStream o = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(tmp), 1 << 16))) {
            o.writeInt(Lexicon.MAGIC); o.writeInt(n); o.writeInt(total); o.writeInt(pl.size());
            o.writeByte(m);
            for (char c : alpha) o.writeChar(c);
            o.write(fr);
            pl.writeTo(o);
        }
        if (!tmp.renameTo(out)) throw new IOException("cannot rename"); // rename на Android заменяет файл атомарно
        return n;
    }
}
