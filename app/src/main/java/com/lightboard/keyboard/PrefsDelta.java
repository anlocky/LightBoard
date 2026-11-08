package com.lightboard.keyboard;

import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A set of changes to one preferences file: what to remove and which typed values to store. It travels between the
 * settings process and the keyboard process as a Bundle, so only the changed keys are ever sent and never a whole
 * snapshot that could overwrite newer values.
 */
final class PrefsDelta {
    static final Object REMOVE = new Object();

    private boolean clear;
    private final LinkedHashMap<String, Object> ops = new LinkedHashMap<>();

    void put(String key, Object value) { ops.put(key, value == null ? REMOVE : value); }

    void remove(String key) { ops.put(key, REMOVE); }

    void clear() { clear = true; ops.clear(); }

    boolean isEmpty() { return !clear && ops.isEmpty(); }

    void merge(PrefsDelta later) {
        if (later.clear) { clear = true; ops.clear(); }
        ops.putAll(later.ops);
    }

    void applyTo(Map<String, Object> m) {
        if (clear) m.clear();
        for (Map.Entry<String, Object> e : ops.entrySet()) {
            if (e.getValue() == REMOVE) m.remove(e.getKey()); else m.put(e.getKey(), e.getValue());
        }
    }

    void applyTo(SharedPreferences.Editor ed) {
        if (clear) ed.clear();
        for (Map.Entry<String, Object> e : ops.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v == REMOVE) ed.remove(k);
            else if (v instanceof String) ed.putString(k, (String) v);
            else if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else if (v instanceof Long) ed.putLong(k, (Long) v);
            else if (v instanceof Float) ed.putFloat(k, (Float) v);
        }
    }

    Bundle toBundle() {
        Bundle b = new Bundle();
        if (clear) b.putBoolean("clear", true);
        Bundle put = new Bundle();
        ArrayList<String> rm = new ArrayList<>();
        for (Map.Entry<String, Object> e : ops.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v == REMOVE) rm.add(k);
            else if (v instanceof String) put.putString(k, (String) v);
            else if (v instanceof Boolean) put.putBoolean(k, (Boolean) v);
            else if (v instanceof Integer) put.putInt(k, (Integer) v);
            else if (v instanceof Long) put.putLong(k, (Long) v);
            else if (v instanceof Float) put.putFloat(k, (Float) v);
        }
        b.putBundle("put", put);
        b.putStringArray("rm", rm.toArray(new String[0]));
        return b;
    }

    @SuppressWarnings("deprecation")
    static PrefsDelta fromBundle(Bundle b) {
        PrefsDelta d = new PrefsDelta();
        if (b == null) return d;
        if (b.getBoolean("clear")) d.clear();
        String[] rm = b.getStringArray("rm");
        if (rm != null) for (String k : rm) d.remove(k);
        Bundle put = b.getBundle("put");
        if (put != null) for (String k : put.keySet()) {
            Object v = put.get(k);
            if (v instanceof String || v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Float) d.put(k, v);
        }
        return d;
    }
}
