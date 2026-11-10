package com.lightboard.keyboard;

import android.content.Context;
import android.util.Log;

final class Diag {
    private Diag() { }

    private static boolean saved;

    static void saveOnce(Context c, Throwable t) {
        if (saved) return;
        saved = true;
        save(c, t);
    }

    static void save(Context c, Throwable t) {
        StackTraceElement[] st = t.getStackTrace();
        Log.e("LightBoard", t.getClass().getName() + (st.length > 0 ? " at " + st[0] : ""));
    }
}
