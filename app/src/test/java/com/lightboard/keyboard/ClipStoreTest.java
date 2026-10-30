package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class ClipStoreTest {
    @Test public void newestOnTopAndDuplicatesMoveUp() {
        ClipStore c = new ClipStore();
        c.add("a", 0); c.add("b", 0); c.add("a", 1);
        assertEquals(2, c.size());
        assertEquals("a", c.get(0).text);
    }

    @Test public void pinnedStayFirst() {
        ClipStore c = new ClipStore();
        c.add("a", 0); c.add("b", 0);          // b, a
        c.togglePin(1);                        // a закреплена
        assertEquals("a", c.get(0).text);
        c.add("c", 0);
        assertEquals("a", c.get(0).text);
        assertEquals("c", c.get(1).text);
    }

    @Test public void expireKeepsPinned() {
        ClipStore c = new ClipStore();
        c.add("old", 0); c.add("pin", 0); c.togglePin(0);
        c.add("new", 100);
        assertTrue(c.expire(100, 50));
        assertEquals(2, c.size());
        assertEquals("pin", c.get(0).text);
        assertEquals("new", c.get(1).text);
    }

    @Test public void tooLongIsRejected() {
        ClipStore c = new ClipStore();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= ClipStore.MAX_LEN; i++) sb.append('x');
        assertFalse(c.add(sb.toString(), 0));
        assertEquals(0, c.size());
    }

    @Test public void stringRoundTrip() {
        ClipStore c = new ClipStore();
        c.add("текст с пробелами\nи строкой", 5); c.add("emoji 😀", 7);
        ClipStore d = new ClipStore();
        d.load(c.save());
        assertEquals(2, d.size());
        assertEquals("emoji 😀", d.get(0).text);
        assertEquals("текст с пробелами\nи строкой", d.get(1).text);
    }
}
