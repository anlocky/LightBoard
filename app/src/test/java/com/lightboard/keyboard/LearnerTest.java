package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class LearnerTest {
    @Test public void wordOk() {
        assertTrue(Learner.wordOk("привет"));
        assertTrue(Learner.wordOk("it's"));
        assertFalse(Learner.wordOk("a b"));
        assertFalse(Learner.wordOk("x>y"));
        assertFalse(Learner.wordOk(""));
    }

    @Test public void plausible() {
        assertTrue(Learner.plausible("привет"));
        assertFalse(Learner.plausible("ааа"));
        assertFalse(Learner.plausible("фывап"));
    }

    @Test public void ownWordNeedsTwoTypings() {
        Learner l = new Learner();
        l.bump("кекс", 1);
        assertFalse(l.known("кекс"));
        l.bump("кекс", 1);
        assertTrue(l.known("кекс"));
    }

    @Test public void pairsAndNext() {
        Learner l = new Learner();
        l.pair("hello", "world");
        String[] out = new String[3];
        assertEquals(1, l.next("hello", out, 0));
        assertEquals("world", out[0]);
    }

    @Test public void loadRejectsBadKeys() {
        Learner l = new Learner();
        l.loadWords("good:3 ba d:2 x>y:4");
        l.loadPairs("a>b:2 nogt:2");
        assertEquals(3, l.count("good"));
        assertEquals(0, l.count("x>y"));
        String[] out = new String[3];
        assertEquals(1, l.next("a", out, 0));
        assertEquals(0, l.next("nogt", out, 0));
    }

    @Test public void forgetRemovesPairs() {
        Learner l = new Learner();
        l.bump("alpha", 0); l.pair("alpha", "beta");
        l.forget("alpha");
        assertEquals(0, l.count("alpha"));
        assertEquals(0, l.next("alpha", new String[3], 0));
    }
}
