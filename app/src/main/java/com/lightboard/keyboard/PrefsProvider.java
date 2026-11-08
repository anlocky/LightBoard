package com.lightboard.keyboard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Map;

/**
 * The only writer of the settings file. It lives in the keyboard's own process (not exported), so the settings screen
 * in its separate process reads and changes preferences through it, and the keyboard always sees the same in-memory
 * SharedPreferences instance. This replaces the deprecated, unreliable MODE_MULTI_PROCESS.
 */
public final class PrefsProvider extends ContentProvider {
    static final String M_ALL = "all", M_APPLY = "apply";

    static String authority(Context c) { return c.getPackageName() + ".prefs"; }

    @Override public boolean onCreate() { return true; }

    @SuppressWarnings("deprecation")
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context c = getContext();
        if (c == null || method == null || arg == null) return null;
        if (!"lb".equals(arg) && !"lbs".equals(arg)) return null;
        SharedPreferences sp = c.getSharedPreferences(arg, Context.MODE_PRIVATE);
        if (M_ALL.equals(method)) {
            Bundle out = new Bundle();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                String k = e.getKey();
                if (v instanceof String) out.putString(k, (String) v);
                else if (v instanceof Boolean) out.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) out.putInt(k, (Integer) v);
                else if (v instanceof Long) out.putLong(k, (Long) v);
                else if (v instanceof Float) out.putFloat(k, (Float) v);
            }
            return out;
        }
        if (M_APPLY.equals(method) && "lb".equals(arg) && extras != null) {
            SharedPreferences.Editor ed = sp.edit();
            PrefsDelta.fromBundle(extras).applyTo(ed);
            if (extras.getBoolean("sync")) ed.commit(); else ed.apply();
            return new Bundle();
        }
        return null;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
