package com.lightboard.keyboard;

import static org.junit.Assert.*;

import android.os.Bundle;
import java.util.HashMap;
import org.junit.Test;

public class PrefsDeltaTest {
    @Test public void putAndRemoveApplyInOrder() {
        HashMap<String, Object> m = new HashMap<>();
        m.put("a", 1); m.put("b", "x");
        PrefsDelta d = new PrefsDelta();
        d.put("a", 5); d.remove("b"); d.put("c", true); d.put("a", 7);
        d.applyTo(m);
        assertEquals(7, m.get("a"));
        assertFalse(m.containsKey("b"));
        assertEquals(true, m.get("c"));
    }

    @Test public void nullValueMeansRemove() {
        HashMap<String, Object> m = new HashMap<>();
        m.put("k", "v");
        PrefsDelta d = new PrefsDelta();
        d.put("k", null);
        d.applyTo(m);
        assertFalse(m.containsKey("k"));
    }

    @Test public void clearComesFirstAndLaterPutsSurvive() {
        HashMap<String, Object> m = new HashMap<>();
        m.put("old", 1);
        PrefsDelta d = new PrefsDelta();
        d.put("before", 1);
        d.clear();
        d.put("after", 2L);
        d.applyTo(m);
        assertFalse(m.containsKey("old"));
        assertFalse(m.containsKey("before"));
        assertEquals(2L, m.get("after"));
    }

    @Test public void bundleRoundTripKeepsTypesAndOnlyChangedKeys() {
        PrefsDelta d = new PrefsDelta();
        d.put("s", "text"); d.put("i", 3); d.put("l", 9000000000L); d.put("f", 1.5f); d.put("b", false); d.remove("gone");
        Bundle b = d.toBundle();
        PrefsDelta r = PrefsDelta.fromBundle(b);
        HashMap<String, Object> m = new HashMap<>();
        m.put("gone", 1); m.put("untouched", "keep");
        r.applyTo(m);
        assertEquals("text", m.get("s"));
        assertEquals(3, m.get("i"));
        assertEquals(9000000000L, m.get("l"));
        assertEquals(1.5f, m.get("f"));
        assertEquals(false, m.get("b"));
        assertFalse(m.containsKey("gone"));
        assertEquals("keep", m.get("untouched"));
    }

    @Test public void emptyDeltaIsEmpty() {
        assertTrue(new PrefsDelta().isEmpty());
        PrefsDelta d = new PrefsDelta();
        d.clear();
        assertFalse(d.isEmpty());
    }
}
