package com.lightboard.keyboard;

import android.content.Context;

/** Общие правила размеров: планшет определяется одним способом и для клавиатуры, и для настроек. */
final class Ui {
    private Ui() { }

    static boolean tablet(Context c) { return c.getResources().getConfiguration().smallestScreenWidthDp >= 600; }

    /** Закругление клавиш по умолчанию: у планшетов в Cupertino 65 %, остальное — как у телефонов. */
    static int defRound(Context c, boolean cupertino) { return cupertino && tablet(c) ? 65 : Shape.DEFAULT; }

    /** На планшете ряды выше (ползунок «Высота» работает поверх), буквы крупнее и плотнее. */
    static float heightMul(Context c) { return tablet(c) ? 1.3f : 1f; }
    static float textMul(Context c) { return tablet(c) ? 1.25f : 1f; }
}
