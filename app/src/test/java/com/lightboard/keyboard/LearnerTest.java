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

    @Test public void ideographsAreNeverWords() {
        assertFalse(Learner.wordOk("\u4F60\u597D"));
        assertFalse(Learner.wordOk("\u3053\u3093\u306B\u3061\u306F"));
        assertFalse(Learner.wordOk("\uC548\uB155"));
        assertTrue(Learner.wordOk("\u0E2A\u0E27\u0E31\u0E2A\u0E14\u0E35"));
    }

    @Test public void plausible() {
        assertTrue(Learner.plausible("привет"));
        assertFalse(Learner.plausible("ааа"));
        assertFalse(Learner.plausible("фывап"));
    }

    @Test public void ownWordNeedsThreeTypings() {
        Learner l = new Learner();
        l.bump("кекс", 1);
        l.bump("кекс", 1);
        assertFalse(l.known("кекс"));
        l.bump("кекс", 1);
        assertTrue(l.known("кекс"));
    }

    @Test public void typedOnceIsAlreadyOfferedToSwipe() {
        Learner l = new Learner();
        l.bump("оптимизация", 1);
        String[] w = new String[300];
        int[] c = new int[300];
        assertEquals(1, l.own(w, c));
        assertEquals("оптимизация", w[0]);
        assertFalse(l.known("оптимизация"));
    }

    @Test public void wordNextToADictionaryWordNeedsMoreTypings() {
        Learner l = new Learner();
        for (int i = 0; i < Learner.OWN_AT_TYPO - 1; i++) l.bump("приветт", 2);
        assertFalse(l.known("приветт"));
        l.bump("приветт", 2);
        assertTrue(l.known("приветт"));
    }

    @Test public void noWordShapeIsNeverCounted() {
        Learner l = new Learner();
        for (int i = 0; i < 10; i++) l.bump("квджх", 5);
        assertEquals(0, l.count("квджх"));
        assertFalse(l.known("квджх"));
    }

    @Test public void deletedRightAwayCountsLess() {
        Learner l = new Learner();
        for (int i = 0; i < 4; i++) l.bump("кекс", 1);
        assertTrue(l.known("кекс"));
        l.demote("кекс");
        assertFalse(l.known("кекс"));
        l.demote("кекс");
        assertEquals(0, l.count("кекс"));
    }

    @Test public void wordsTheyAddedThemselvesSurviveDemotion() {
        Learner l = new Learner();
        assertTrue(l.learnUser("мемас"));
        l.demote("мемас");
        assertTrue(l.known("мемас"));
    }

    @Test public void keyMashingIsNotLearnedEvenOnPurpose() {
        Learner l = new Learner();
        assertFalse(l.learnUser("йцукенгш"));
    }

    @Test public void pairsAndNext() {
        Learner l = new Learner();
        l.bump("world", 0); l.bump("world", 0);
        l.pair("hello", "world");
        String[] out = new String[3];
        assertEquals(0, l.next("hello", out, 0));
        l.pair("hello", "world");
        assertEquals(1, l.next("hello", out, 0));
        assertEquals("world", out[0]);
    }

    @Test public void nextWordMustBeAKnownWord() {
        Learner l = new Learner();
        l.pair("hello", "asdqw"); l.pair("hello", "asdqw"); l.pair("hello", "asdqw");
        assertEquals(0, l.next("hello", new String[3], 0));
    }

    @Test public void loadRejectsBadKeys() {
        Learner l = new Learner();
        l.loadWords("good:3 b:2 ba d:2 x>y:4");
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
