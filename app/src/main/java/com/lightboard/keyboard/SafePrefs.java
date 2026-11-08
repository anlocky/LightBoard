package com.lightboard.keyboard;

import android.content.SharedPreferences;

import java.util.Map;
import java.util.Set;

/** SharedPreferences whose getters return a usable value (or the default) even if a key holds a different type. */
final class SafePrefs implements SharedPreferences {
    private final SharedPreferences p;

    SafePrefs(SharedPreferences p) { this.p = p; }

    private Object raw(String k) { return p.getAll().get(k); }

    @Override public Map<String, ?> getAll() { return p.getAll(); }

    @Override public String getString(String k, String d) {
        try { return p.getString(k, d); } catch (ClassCastException e) { Object v = raw(k); return v == null ? d : String.valueOf(v); }
    }

    @Override public Set<String> getStringSet(String k, Set<String> d) {
        try { return p.getStringSet(k, d); } catch (ClassCastException e) { return d; }
    }

    @Override public int getInt(String k, int d) {
        try { return p.getInt(k, d); } catch (ClassCastException e) {
            Object v = raw(k);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v instanceof String) { try { return Integer.parseInt(((String) v).trim()); } catch (NumberFormatException ignored) { } }
            return d;
        }
    }

    @Override public long getLong(String k, long d) {
        try { return p.getLong(k, d); } catch (ClassCastException e) {
            Object v = raw(k);
            if (v instanceof Number) return ((Number) v).longValue();
            if (v instanceof String) { try { return Long.parseLong(((String) v).trim()); } catch (NumberFormatException ignored) { } }
            return d;
        }
    }

    @Override public float getFloat(String k, float d) {
        try { return p.getFloat(k, d); } catch (ClassCastException e) {
            Object v = raw(k);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) { try { return Float.parseFloat(((String) v).trim()); } catch (NumberFormatException ignored) { } }
            return d;
        }
    }

    @Override public boolean getBoolean(String k, boolean d) {
        try { return p.getBoolean(k, d); } catch (ClassCastException e) {
            Object v = raw(k);
            if (v instanceof String) return "true".equalsIgnoreCase(((String) v).trim()) || (!"false".equalsIgnoreCase(((String) v).trim()) && d);
            if (v instanceof Number) return ((Number) v).intValue() != 0;
            return d;
        }
    }

    @Override public boolean contains(String k) { return p.contains(k); }

    @Override public Editor edit() { return p.edit(); }

    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { p.registerOnSharedPreferenceChangeListener(l); }

    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { p.unregisterOnSharedPreferenceChangeListener(l); }
}
