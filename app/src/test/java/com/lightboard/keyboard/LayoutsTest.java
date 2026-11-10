package com.lightboard.keyboard;

import static org.junit.Assert.*;
import java.util.HashSet;
import org.junit.Test;

public class LayoutsTest {
    @Test public void everySupportedLanguageHasUsableLayout() {
        String[] ids = Layouts.supportedIds();
        assertTrue(ids.length >= 80);
        for (String id : ids) {
            Layouts.L l = Layouts.byLang(id);
            assertTrue(id + " missing", l != null);
            assertTrue(id + " cols " + l.cols, l.cols >= 5 && l.cols <= 12);
            HashSet<Character> seen = new HashSet<>();
            String all = l.r1 + l.r2 + l.r3;
            for (int i = 0; i < all.length(); i++) assertTrue(id + " duplicate key " + all.charAt(i), seen.add(all.charAt(i)));
            assertTrue(id + " too few keys", all.length() >= 20);
        }
    }

    @Test public void newScriptsAreThere() {
        String[] must = {"ar", "fa", "ur", "hy", "ka", "th", "hi", "bn", "ta", "te", "ml", "si", "kk", "uz", "br", "cy"};
        for (String id : must) assertTrue(id, Layouts.byLang(id) != null);
        assertTrue(Layouts.byLang("pt_br") != null);
        assertTrue(Layouts.byLang("zh") == null && Layouts.byLang("ja") == null && Layouts.byLang("ko") == null);
    }

    @Test public void sameLanguageIsEqualById() {
        assertEquals(Layouts.byLang("kk"), Layouts.byLang("kk"));
        assertFalse(Layouts.byLang("kk").equals(Layouts.byLang("bg")));
    }

    @Test public void everyLetterIsReachable() {
        Layouts.L l = Layouts.byLang("hi");
        StringBuilder reach = new StringBuilder(l.r1 + l.r2 + l.r3);
        for (char c : (l.r1 + l.r2 + l.r3).toCharArray()) { String a = l.alt(c); if (a != null) reach.append(a.replace("|", "")); }
        String base = "अआइईउऊऋएऐओऔकखगघङचछजझञटठडढणतथदधनपफबभमयरलवशषसह";
        for (char c : base.toCharArray()) assertTrue("hi: " + c, reach.indexOf(String.valueOf(c)) >= 0);
        for (char c : "\u093E\u093F\u0940\u0941\u0942\u0947\u0948\u094B\u094C\u0902\u094D".toCharArray()) assertTrue("hi mark " + (int) c, reach.indexOf(String.valueOf(c)) >= 0);
    }
}
