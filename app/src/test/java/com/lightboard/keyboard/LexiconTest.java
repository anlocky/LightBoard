package com.lightboard.keyboard;

import static org.junit.Assert.*;
import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.File;
import org.junit.Test;

public class LexiconTest {
    private static Lexicon build(String lang, String[] top, int filler) throws Exception {
        final File dir = java.nio.file.Files.createTempDirectory("lb").toFile();
        StringBuilder sb = new StringBuilder();
        int f = 100000;
        for (String w : top) sb.append(w).append(' ').append(f--).append('\n');
        String cons = "bcdfghklmnprstvz", vow = "aeiou";
        for (int i = 0; i < filler; i++) {
            String w = "" + cons.charAt(i % 16) + vow.charAt((i / 16) % 5) + cons.charAt((i / 80) % 16) + vow.charAt((i / 1280) % 5) + cons.charAt((i / 6400 + i) % 16);
            sb.append(w).append(' ').append(f > 5 ? f-- : 5).append('\n');
        }
        LexBuilder.build(lang, new ByteArrayInputStream(sb.toString().getBytes("UTF-8")), 200000, new File(dir, "lex_" + lang + ".lex"));
        return Lexicon.load(new Context() { @Override public File getFilesDir() { return dir; } }, lang);
    }

    @Test public void correctsTransposedFirstLetters() throws Exception {
        Lexicon lx = build("en", new String[]{"the", "hello", "world", "and", "that", "with", "have"}, 3000);
        assertEquals("the", lx.correct("hte"));
        assertEquals("the", lx.correct("teh"));
    }

    @Test public void missingAccentIsNearlyFree() throws Exception {
        Lexicon lx = build("es", new String[]{"de", "que", "caf\u00E9", "ni\u00F1o", "a\u00F1o", "pap\u00E1"}, 3000);
        assertEquals("caf\u00E9", lx.correct("cafe"));
        assertEquals("a\u00F1o", lx.correct("ano"));
        assertEquals("pap\u00E1", lx.correct("papa"));
    }

    @Test public void splitsGluedWords() throws Exception {
        Lexicon lx = build("en", new String[]{"hello", "world", "the", "good", "morning", "and", "love", "you", "thank"}, 3000);
        assertEquals("hello world", lx.split("helloworld"));
        assertEquals("good morning", lx.split("goodmorning"));
        assertEquals("thank you", lx.split("thankyou"));
        assertNull(lx.split("qqqqqq"));
        assertNull(lx.split("hello"));
    }

    @Test public void wordShapeSeparatesWordsFromKeyMashing() throws Exception {
        Lexicon lx = build("en", new String[]{"the", "hello", "world", "banana", "family", "bakery"}, 6000);
        assertEquals(0, lx.unseenTriples("hello"));
        assertEquals(0, lx.unseenTriples("banana"));
        assertTrue(lx.unseenTriples("qxzvkjw") >= 2);
        assertTrue(lx.unseenTriples("hellq") >= 1);
        assertEquals(-1, build("en", new String[]{"the"}, 1500).unseenTriples("the"));
    }

    @Test public void autoCorrectNeedsMoreEvidenceThanASuggestion() throws Exception {
        Lexicon lx = build("en", new String[]{"the", "hello", "world", "banana", "family", "bakery", "morning"}, 6000);
        assertEquals("hello", lx.correct("hellp"));
        assertEquals("hello", lx.autoCorrect("hellp", 0L));
        assertNull(lx.autoCorrect("hello", 0L));
    }

    @Test public void existingWordIsKept() throws Exception {
        Lexicon lx = build("en", new String[]{"the", "hello", "world"}, 3000);
        assertTrue(lx.contains("hello"));
        assertFalse(lx.contains("helloworld"));
    }

    @Test public void suggestsCompletions() throws Exception {
        Lexicon lx = build("en", new String[]{"hello", "help", "helmet", "world"}, 3000);
        String[] out = new String[3];
        int n = lx.suggest("hel", out);
        assertTrue(n >= 2);
        assertEquals("hello", out[0]);
    }

    @Test public void arabicScriptDictionaryBuildsAndLoads() throws Exception {
        final File dir = java.nio.file.Files.createTempDirectory("lb").toFile();
        StringBuilder sb = new StringBuilder();
        String letters = "\u0627\u0628\u062A\u062B\u062C\u062D\u062E\u062F\u0631\u0632\u0633\u0634\u0635\u0639\u0641\u0642\u0643\u0644\u0645\u0646\u0647\u0648\u064A";
        for (int i = 0; i < 2500; i++) { sb.append(letters.charAt(i % 23)).append(letters.charAt((i / 23) % 23)).append(letters.charAt((i / 7 + 3) % 23)).append(letters.charAt((i * 5) % 23)).append(' ').append(10000 - i).append('\n'); }
        int n = LexBuilder.build("ar", new ByteArrayInputStream(sb.toString().getBytes("UTF-8")), 100000, new File(dir, "lex_ar.lex"));
        assertTrue("words " + n, n >= 1000);
        Lexicon lx = Lexicon.load(new Context() { @Override public File getFilesDir() { return dir; } }, "ar");
        assertTrue(lx.n >= 1000);
    }

    @Test public void devanagariWordsWithMarksAreKept() throws Exception {
        final File dir = java.nio.file.Files.createTempDirectory("lb").toFile();
        StringBuilder sb = new StringBuilder();
        String cons = "\u0915\u0916\u0917\u0918\u091A\u091B\u091C\u091D\u091F\u0920\u0921\u0922\u0924\u0925\u0926\u0927\u0928\u092A\u092B\u092C\u092D\u092E\u092F\u0930\u0932\u0935\u0936\u0937\u0938\u0939";
        String marks = "\u093E\u093F\u0940\u0941\u0942\u0947\u0948\u094B\u094C";
        for (int i = 0; i < 3000; i++) sb.append(cons.charAt(i % 30)).append(marks.charAt((i / 30) % 9)).append(cons.charAt((i / 270 + 7) % 30)).append(' ').append(9000 - i).append('\n');
        int n = LexBuilder.build("hi", new ByteArrayInputStream(sb.toString().getBytes("UTF-8")), 100000, new File(dir, "lex_hi.lex"));
        assertTrue("words " + n, n >= 1000);
    }
}
