package com.lightboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * SharedPreferences for the settings process. Values are read once from {@link PrefsProvider} in the keyboard
 * process and cached; every change updates the cache at once and is sent to the provider in order on a background
 * thread (commit() waits for it). Only the changed keys are sent.
 */
@SuppressWarnings("deprecation")
final class RemotePrefs implements SharedPreferences {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "lb-prefs"); t.setDaemon(true); return t; }
    });

    private final Context app;
    private final String name;
    private final Uri uri;
    private final HashMap<String, Object> map = new HashMap<>();

    RemotePrefs(Context c, String name) {
        Context a = c.getApplicationContext();
        this.app = a != null ? a : c;
        this.name = name;
        this.uri = Uri.parse("content://" + PrefsProvider.authority(this.app));
        reload();
    }

    void reload() {
        Bundle b = call(PrefsProvider.M_ALL, null);
        synchronized (map) {
            map.clear();
            if (b != null) for (String k : b.keySet()) { Object v = b.get(k); if (v != null) map.put(k, v); }
        }
    }

    private Bundle call(String method, Bundle extras) {
        for (int i = 0; i < 2; i++) {
            try {
                Bundle r = app.getContentResolver().call(uri, method, name, extras);
                if (r != null) return r;
            } catch (RuntimeException ignored) { }
            if (i == 0) {
                try { Thread.sleep(120); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
            }
        }
        return null;
    }

    /** Waits until everything sent with apply() has reached the keyboard process. */
    void flush() {
        try { IO.submit(new Runnable() { @Override public void run() { } }).get(3, TimeUnit.SECONDS); }
        catch (Exception ignored) { }
    }

    private PrefsDelta pending;

    private boolean send(PrefsDelta d, boolean sync) {
        PrefsDelta all;
        synchronized (this) {
            if (pending != null) { pending.merge(d); all = pending; pending = null; } else all = d;
        }
        if (all.isEmpty()) return true;
        Bundle x = all.toBundle();
        if (sync) x.putBoolean("sync", true);
        boolean ok = call(PrefsProvider.M_APPLY, x) != null;
        if (!ok) synchronized (this) {
            if (pending != null) all.merge(pending);
            pending = all;
        }
        return ok;
    }

    @Override public Map<String, ?> getAll() { synchronized (map) { return new HashMap<>(map); } }

    @Override public String getString(String k, String d) { synchronized (map) { Object v = map.get(k); return v instanceof String ? (String) v : d; } }

    @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }

    @Override public int getInt(String k, int d) { synchronized (map) { Object v = map.get(k); return v instanceof Integer ? (Integer) v : d; } }

    @Override public long getLong(String k, long d) {
        synchronized (map) { Object v = map.get(k); return v instanceof Long ? (Long) v : v instanceof Integer ? (long) (Integer) v : d; }
    }

    @Override public float getFloat(String k, float d) { synchronized (map) { Object v = map.get(k); return v instanceof Float ? (Float) v : d; } }

    @Override public boolean getBoolean(String k, boolean d) { synchronized (map) { Object v = map.get(k); return v instanceof Boolean ? (Boolean) v : d; } }

    @Override public boolean contains(String k) { synchronized (map) { return map.containsKey(k); } }

    @Override public Editor edit() { return new Ed(); }

    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }

    private final class Ed implements Editor {
        private PrefsDelta d = new PrefsDelta();

        @Override public Editor putString(String k, String v) { d.put(k, v); return this; }
        @Override public Editor putStringSet(String k, Set<String> v) { throw new UnsupportedOperationException("string sets are not used"); }
        @Override public Editor putInt(String k, int v) { d.put(k, v); return this; }
        @Override public Editor putLong(String k, long v) { d.put(k, v); return this; }
        @Override public Editor putFloat(String k, float v) { d.put(k, v); return this; }
        @Override public Editor putBoolean(String k, boolean v) { d.put(k, v); return this; }
        @Override public Editor remove(String k) { d.remove(k); return this; }
        @Override public Editor clear() { d.clear(); return this; }

        private PrefsDelta take() {
            PrefsDelta x = d;
            d = new PrefsDelta();
            synchronized (map) { x.applyTo(map); }
            return x;
        }

        @Override public boolean commit() {
            final PrefsDelta x = take();
            flush();
            return send(x, true);
        }

        @Override public void apply() {
            final PrefsDelta x = take();
            if (x.isEmpty()) return;
            IO.execute(new Runnable() { @Override public void run() { send(x, false); } });
        }
    }
}
