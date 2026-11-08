package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class TouchTest {
    private static Touch word(String w) {
        Touch t = new Touch();
        char[] c = {'x', 'y', 'z', 'w'};
        float[] d = {0.1f, 0.5f, 0.9f, 1.2f};
        for (int i = 0; i < w.length(); i++) t.push(w.charAt(i), c, d, 4);
        return t;
    }

    @Test public void matchesOnlyTheSameWord() {
        Touch t = word("hello");
        assertTrue(t.matches("hello"));
        assertFalse(t.matches("hellp"));
        assertFalse(t.matches("hell"));
    }

    @Test public void popAndClear() {
        Touch t = word("hello");
        t.pop();
        assertTrue(t.matches("hell"));
        t.clear();
        assertTrue(t.matches(""));
    }

    @Test public void tooLongWordNeverMatches() {
        Touch t = word("abcdefghijklmnopqrstuvwxyz");
        assertFalse(t.matches("abcdefghijklmnopqrstuvwxyz"));
        t.pop();
        assertFalse(t.matches("abcdefghijklmnopqrstuvwxy"));
        t.clear();
        assertTrue(t.matches(""));
    }

    @Test public void copyIsIndependent() {
        Touch t = word("cat");
        Touch c = t.copy();
        t.pop();
        assertTrue(c.matches("cat"));
        assertTrue(t.matches("ca"));
    }
}
