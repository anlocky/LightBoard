package com.lightboard.keyboard;

import android.content.Context;

final class Ui {
    private Ui() { }

    /** Bold text in the settings screen (the "app_bold" option). */
    static boolean appBold;

    static boolean tablet(Context c) { return c.getResources().getConfiguration().smallestScreenWidthDp >= 600; }

    static boolean cupertinoId(String v) { return v != null && (v.equals("cupertino") || v.length() == 3); }

    static int defRound(Context c, boolean cupertino) { return cupertino && tablet(c) ? 65 : Shape.DEFAULT; }

    static float heightMul(Context c) { return tablet(c) ? 1.3f : 1f; }
    static float textMul(Context c) { return tablet(c) ? 1.25f : 1f; }
}
