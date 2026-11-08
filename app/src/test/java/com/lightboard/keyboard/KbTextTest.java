package com.lightboard.keyboard;

import static org.junit.Assert.*;
import java.lang.reflect.Field;
import org.junit.Test;

public class KbTextTest {
    @Test public void everyRowComplete() throws Exception {
        Field f = KbText.class.getDeclaredField("T");
        f.setAccessible(true);
        String[][] t = (String[][]) f.get(null);
        for (String[] r : t) {
            assertEquals(r[0], 12, r.length);
            for (String s : r) assertTrue(r[0], !s.trim().isEmpty());
        }
    }

    @Test public void followsAppLanguage() {
        KbText.set("ru");
        assertEquals("Буфер обмена", KbText.t("Буфер обмена"));
        KbText.set("de");
        assertEquals("Zwischenablage", KbText.t("Буфер обмена"));
        KbText.set("xx");
        assertEquals("Clipboard", KbText.t("Буфер обмена"));
        assertEquals("нет такой", KbText.t("нет такой"));
        KbText.set("vi");
        assertEquals("Hoàn tác", KbText.t("Отменить"));
        assertEquals("Recognition error: 7".replace("Recognition error", "Lỗi nhận dạng"), String.format(KbText.t("Ошибка распознавания: %d"), 7));
    }
}
