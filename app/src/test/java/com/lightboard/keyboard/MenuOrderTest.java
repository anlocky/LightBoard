package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class MenuOrderTest {
    @Test public void defaultIsIdentity() {
        int[] o = MenuOrder.parse(MenuOrder.DEFAULT);
        for (int i = 0; i < o.length; i++) assertEquals(i, o[i]);
    }

    @Test public void roundTrip() {
        int[] o = {3, 1, 0, 2};
        assertEquals("3102", MenuOrder.encode(o));
        int[] p = MenuOrder.parse("3102");
        for (int i = 0; i < 4; i++) assertEquals(o[i], p[i]);
    }

    @Test public void brokenValuesFallBackToDefault() {
        String[] bad = {null, "", "012", "01234", "0011", "01a3", "0124", "-123"};
        for (String s : bad) { int[] o = MenuOrder.parse(s); for (int i = 0; i < 4; i++) assertEquals(s, i, o[i]); }
    }
}
