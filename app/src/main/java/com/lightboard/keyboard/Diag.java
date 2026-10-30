package com.lightboard.keyboard;

import android.content.Context;
import android.util.Log;

/** Ошибки пишем только в logcat (тег LightBoard) и только тип с местом сбоя, без текста сообщения (в нём может оказаться введённый текст). Клавиатура продолжает работать. */
final class Diag {
    private Diag() { }

    private static boolean saved;

    /** Только первый сбой за время жизни процесса (рисование может падать на каждом кадре). */
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
