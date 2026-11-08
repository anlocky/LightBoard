package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class KeyPlanHintTest {
    @Test public void digitsOnlyOnTopRow() {
        assertEquals("1", KeyPlan.hint(0, 0, false, true, false));
        assertEquals("0", KeyPlan.hint(0, 9, false, true, false));
        assertNull(KeyPlan.hint(0, 10, false, true, false));
        assertNull(KeyPlan.hint(1, 0, false, true, false));
        assertNull(KeyPlan.hint(2, 3, false, true, false));
    }

    @Test public void symbolsEverywhere() {
        assertEquals("%", KeyPlan.hint(0, 0, false, false, true));
        assertEquals("@", KeyPlan.hint(1, 0, false, false, true));
        assertEquals("*", KeyPlan.hint(2, 0, false, false, true));
    }

    @Test public void bothTogetherDoNotConflict() {
        assertEquals("1", KeyPlan.hint(0, 0, false, true, true));
        assertEquals("%", KeyPlan.topSym(0));
        assertEquals("@", KeyPlan.hint(1, 0, false, true, true));
        assertNull(KeyPlan.hint(1, 0, false, true, false));
    }

    @Test public void noDigitHintsWhenNumberRowExists() {
        assertNull(KeyPlan.hint(0, 0, true, true, false));
        assertEquals("%", KeyPlan.hint(0, 0, true, true, true));
    }
}
