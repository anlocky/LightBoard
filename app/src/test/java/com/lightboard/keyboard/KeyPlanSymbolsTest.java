package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class KeyPlanSymbolsTest {
    private static String text(KeyPlan p, int row) {
        StringBuilder sb = new StringBuilder();
        for (KeyPlan.Spec s : p.rows.get(row)) if (s.kind == KeyPlan.CHAR) sb.append(s.label);
        return sb.toString();
    }

    @Test public void symbolPagesKeepFourRowsWithoutNumberRow() {
        assertEquals(4, KeyPlan.symbols(Layouts.EN, 1).rows.size());
        assertEquals(4, KeyPlan.symbols(Layouts.EN, 2).rows.size());
        assertEquals(4, KeyPlan.symbols(Layouts.EN, 1, false).rows.size());
    }

    @Test public void numberRowAddsOneMoreRowInsteadOfStretching() {
        KeyPlan one = KeyPlan.symbols(Layouts.EN, 1, true), two = KeyPlan.symbols(Layouts.EN, 2, true);
        assertEquals(5, one.rows.size());
        assertEquals(5, two.rows.size());
        assertEquals("1234567890", text(one, 0));
        assertEquals(text(KeyPlan.symbols(Layouts.EN, 2), 0), text(one, 1));
        assertEquals("@#$_&-+()/", text(one, 2));
        assertEquals("1234567890", text(two, 0));
        assertEquals(text(KeyPlan.symbols(Layouts.EN, 2), 0), text(two, 1));
    }
}
