package com.lightboard.keyboard;

import static org.junit.Assert.*;
import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import org.junit.Test;

public class SwipeDecoderTest {
    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};
    private static final float[] SHIFT = {0f, 0.5f, 1.5f};
    private static final float PITCH = 40f, RH = 56f;

    private static Lexicon lexicon(String[] top) throws Exception {
        final File dir = java.nio.file.Files.createTempDirectory("lb").toFile();
        StringBuilder sb = new StringBuilder();
        int f = 100000;
        for (String w : top) sb.append(w).append(' ').append(f--).append('\n');
        String cons = "bcdfghklmnprstvz", vow = "aeiou";
        for (int i = 0; i < 3000; i++) {
            String w = "" + cons.charAt(i % 16) + vow.charAt((i / 16) % 5) + cons.charAt((i / 80) % 16) + vow.charAt((i / 1280) % 5) + cons.charAt((i / 6400 + i) % 16);
            sb.append(w).append(' ').append(f > 5 ? f-- : 5).append('\n');
        }
        LexBuilder.build("en", new ByteArrayInputStream(sb.toString().getBytes("UTF-8")), 200000, new File(dir, "lex_en.lex"));
        return Lexicon.load(new Context() { @Override public File getFilesDir() { return dir; } }, "en");
    }

    private static String swipe(Lexicon lx, String word, float dx, float dy) {
        StringBuilder letters = new StringBuilder();
        ArrayList<float[]> keys = new ArrayList<>();
        for (int r = 0; r < 3; r++) for (int i = 0; i < ROWS[r].length(); i++) { letters.append(ROWS[r].charAt(i)); keys.add(new float[]{(i + SHIFT[r] + 0.5f) * PITCH, (r + 0.5f) * RH}); }
        float[] lcx = new float[keys.size()], lcy = new float[keys.size()];
        for (int i = 0; i < keys.size(); i++) { lcx[i] = keys.get(i)[0]; lcy[i] = keys.get(i)[1]; }
        float[] px = new float[512], py = new float[512];
        int n = 0;
        char prev = 0;
        float[] last = null;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (c == prev) continue;
            prev = c;
            int k = letters.indexOf(String.valueOf(c));
            float[] to = {lcx[k] + dx, lcy[k] + dy};
            if (last == null) { px[n] = to[0]; py[n] = to[1]; n++; }
            else {
                float len = (float) Math.hypot(to[0] - last[0], to[1] - last[1]);
                int steps = Math.max(1, (int) (len / 6f));
                for (int s = 1; s <= steps; s++) { px[n] = last[0] + (to[0] - last[0]) * s / steps; py[n] = last[1] + (to[1] - last[1]) * s / steps; n++; }
            }
            last = to;
        }
        String[] out = new String[3];
        int found = new SwipeDecoder().decode(false, lx, letters.toString(), lcx, lcy, PITCH, px, py, n, out);
        return found > 0 ? out[0] : null;
    }

    @Test public void picksTheSwipedWord() throws Exception {
        Lexicon lx = lexicon(new String[]{"the", "hello", "world", "help", "held", "hold", "wood"});
        assertEquals("hello", swipe(lx, "hello", 0f, 0f));
        assertEquals("world", swipe(lx, "world", 0f, 0f));
    }

    @Test public void toleratesASidewaysError() throws Exception {
        Lexicon lx = lexicon(new String[]{"the", "hello", "world", "help", "held", "hold", "wood"});
        assertEquals("hello", swipe(lx, "hello", 9f, -7f));
        assertEquals("world", swipe(lx, "world", -8f, 8f));
    }
}
