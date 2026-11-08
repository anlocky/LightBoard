package com.lightboard.keyboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class KeySounds {
    static final int KEY = 0, DELETE = 1, MOD = 2;
    static final float MAX_GAIN = 0.1f;
    private static final String[] NAMES = {"click_key.wav", "click_delete.wav", "click_mod.wav"};

    private final AudioManager am;
    private SoundPool pool;
    private final int[] ids = new int[3];
    private long ringAt; private boolean ringOk = true;

    KeySounds(Context c) {
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (int i = 0; i < 3; i++) ids[i] = pool.load(file(c, i).getPath(), 1);
        } catch (RuntimeException | IOException e) {
            release();
        }
    }

    private static File file(Context c, int kind) throws IOException {
        long rev = 0L;
        try { rev = new File(c.getApplicationInfo().sourceDir).lastModified(); } catch (RuntimeException ignored) { }
        File f = new File(c.getCacheDir(), rev + "_" + NAMES[kind]);
        if (f.exists() && f.length() >= 64) return f;
        byte[] data = null;
        try (InputStream in = c.getAssets().open(NAMES[kind])) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            data = bo.toByteArray();
        } catch (IOException ignored) { }
        if (data == null || data.length < 64) data = ClickSynth.wav(kind);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(data); }
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw new IOException("rename"); }
        File[] old = c.getCacheDir().listFiles();
        if (old != null) for (File x : old) {
            String nm = x.getName();
            if (nm.matches("\\d+_click_[a-z]+\\.wav(\\.tmp)?") && !nm.startsWith(rev + "_")) x.delete();
        }
        return f;
    }

    void play(int kind, float vol) {
        if (pool == null || am == null || vol <= 0f) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - ringAt > 2000L) { ringAt = now; ringOk = am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL; }
        if (!ringOk) return;
        pool.play(ids[kind], vol, vol, 1, 0, 1f);
    }

    void release() {
        if (pool != null) { pool.release(); pool = null; }
    }
}
