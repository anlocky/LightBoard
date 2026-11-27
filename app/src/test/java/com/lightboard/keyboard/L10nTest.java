package com.lightboard.keyboard;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import java.util.HashSet;
import org.junit.Test;

public class L10nTest {
    private static String[][] table() throws Exception {
        Field f = L10n.class.getDeclaredField("T");
        f.setAccessible(true);
        return (String[][]) f.get(null);
    }

    @Test public void everyRowHasAllLanguagesAndNoDuplicates() throws Exception {
        HashSet<String> seen = new HashSet<>();
        for (String[] r : table()) {
            assertEquals((long) L10n.LANGS.length, (long) r.length);
            assertTrue("duplicate key " + r[0], seen.add(r[0]));
            for (int i = 1; i < r.length; i++) assertTrue("empty translation " + r[0] + " / " + L10n.LANGS[i], !r[i].trim().isEmpty());
        }
    }

    @Test public void translatesNewStrings() {
        L10n.set("de");
        assertEquals("Wörterbücher", L10n.t("Словари"));
        L10n.set("vi");
        assertEquals("Từ điển", L10n.t("Словари"));
        L10n.set("ru");
        assertEquals("Словари", L10n.t("Словари"));
    }
}
