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

/**
 * Звуки нажатий. Если в assets лежат click_key.wav, click_delete.wav, click_mod.wav, берутся они (так можно подложить свои записи);
 * иначе используются щелчки из ClickSynth. Не звучит в беззвучном режиме и режиме вибрации, как клавиатура iOS.
 */
final class KeySounds {
    static final int KEY = 0, DELETE = 1, MOD = 2;
    static final float MAX_GAIN = 0.1f; // 100 % на ползунке — это 10 % полной громкости: нижняя часть шкалы стала «тоньше»
    private static final String[] NAMES = {"click_key.wav", "click_delete.wav", "click_mod.wav"};

    private final AudioManager am;
    private SoundPool pool;
    private final int[] ids = new int[3];
    private long ringAt; private boolean ringOk = true; // режим звонка — это обращение к системе: на каждое нажатие не спрашиваем

    KeySounds(Context c) {
        am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        try {
            pool = new SoundPool.Builder().setMaxStreams(4).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (int i = 0; i < 3; i++) ids[i] = pool.load(file(c, i).getPath(), 1);
        } catch (RuntimeException | IOException e) {
            release(); // без звука клавиатура работает как обычно
        }
    }

    private static File file(Context c, int kind) throws IOException {
        long rev = 0L; // версия сборки в имени файла: после обновления (в т.ч. со своими записями в assets) старые файлы не используются
        try { rev = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime; } catch (Exception ignored) { }
        File f = new File(c.getCacheDir(), rev + "_" + NAMES[kind]);
        if (f.exists() && f.length() >= 64) return f; // уже готов: не читаем assets и не синтезируем заново
        byte[] data = null;
        try (InputStream in = c.getAssets().open(NAMES[kind])) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            data = bo.toByteArray();
        } catch (IOException ignored) { } // файла нет — синтез
        if (data == null || data.length < 64) data = ClickSynth.wav(kind);
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
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
