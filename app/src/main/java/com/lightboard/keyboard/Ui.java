package com.lightboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;

final class Ui {
    private Ui() { }

    static boolean appBold;

    static float panelR = 16f;

    static final String[] GLOBAL_KEYS = {"swipe", "trail", "autocorrect", "autocaps", "autoperiod", "autospace", "clip", "spacecursor", "langswipe", "langswipedir", "langswipedist", "clearall", "clearhold", "haptic", "sound", "soundvol2"};

    static boolean isGlobalKey(String b) {
        for (String g : GLOBAL_KEYS) if (g.equals(b)) return true;
        return false;
    }

    static void migrateGlobal(SharedPreferences sp) {
        if (sp.getBoolean("glob1", false)) return;
        java.util.Map<String, ?> all = sp.getAll();
        boolean cup = cupertinoId(sp.getString("design", "material"));
        String pre = cup ? "i_" : "g_", other = cup ? "g_" : "i_";
        SharedPreferences.Editor ed = sp.edit();
        for (String k : GLOBAL_KEYS) {
            Object v = all.get(pre + k);
            if (v == null) v = all.get(other + k);
            if (v != null && !all.containsKey(k)) {
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Long) ed.putLong(k, (Long) v);
                else if (v instanceof Float) ed.putFloat(k, (Float) v);
                else if (v instanceof String) ed.putString(k, (String) v);
            }
            ed.remove("g_" + k).remove("i_" + k);
        }
        ed.putBoolean("glob1", true).apply();
    }

    static boolean tablet(Context c) { return c.getResources().getConfiguration().smallestScreenWidthDp >= 600; }

    static boolean cupertinoId(String v) { return v != null && (v.equals("cupertino") || v.length() == 3); }

    static int defRound(Context c, boolean cupertino) { return cupertino && tablet(c) ? 65 : Shape.DEFAULT; }

    static float heightMul(Context c) { return tablet(c) ? 1.3f : 1f; }
    static float textMul(Context c) { return tablet(c) ? 1.25f : 1f; }
}
