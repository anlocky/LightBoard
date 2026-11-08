package com.lightboard.keyboard;

import android.content.Context;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import java.util.ArrayList;

final class SysLangs {
    private SysLangs() { }

    static ArrayList<Layouts.L> enabled(Context c, ArrayList<InputMethodSubtype> subtypes) {
        ArrayList<Layouts.L> out = new ArrayList<>();
        try {
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) for (InputMethodInfo imi : imm.getEnabledInputMethodList()) {
                if (!imi.getPackageName().equals(c.getPackageName())) continue;
                for (InputMethodSubtype st : imm.getEnabledInputMethodSubtypeList(imi, true)) {
                    Layouts.L l = Layouts.byLang(st.getLocale());
                    if (l != null && !out.contains(l)) { out.add(l); if (subtypes != null) subtypes.add(st); }
                }
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    static String names(Context c) {
        StringBuilder sb = new StringBuilder();
        for (Layouts.L l : enabled(c, null)) { if (sb.length() > 0) sb.append(", "); sb.append(l.name); }
        return sb.length() == 0 ? "\u2014" : sb.toString();
    }
}
