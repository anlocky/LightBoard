package com.lightboard.keyboard;

import java.util.Random;

/** Короткие щелчки, синтезируемые в коде (чистая Java): на выходе готовый WAV (моно, 16 бит, 44,1 кГц). Реальные записи iOS не используются. */
final class ClickSynth {
    private ClickSynth() { }

    static final int RATE = 44100;
    // буква — самый «звонкий» и короткий, стереть ниже, служебные клавиши (пробел, shift, 123, return) глуше всего
    private static final float[] FREQ = {2100f, 1500f, 1150f}, TAU = {0.0085f, 0.011f, 0.013f};

    static byte[] wav(int kind) {
        int k = Math.max(0, Math.min(2, kind));
        int n = (int) (RATE * 0.05f);
        float[] x = new float[n];
        Random r = new Random(7 + k);
        float f = FREQ[k], t0 = TAU[k], peak = 0f;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double body = Math.exp(-t / t0) * (Math.sin(2 * Math.PI * f * t) + 0.45 * Math.sin(2 * Math.PI * f * 2.7 * t) * Math.exp(-t / (t0 * 0.4)));
            double tick = Math.exp(-t / 0.0007) * (r.nextDouble() * 2 - 1) * 0.5;
            double fade = Math.min(1.0, t / 0.0003);
            x[i] = (float) ((body * 0.7 + tick) * fade);
            peak = Math.max(peak, Math.abs(x[i]));
        }
        float g = peak > 0f ? 0.85f / peak : 1f;
        byte[] out = new byte[44 + 2 * n];
        put(out, 0, "RIFF"); le32(out, 4, 36 + 2 * n); put(out, 8, "WAVE"); put(out, 12, "fmt ");
        le32(out, 16, 16); le16(out, 20, 1); le16(out, 22, 1); le32(out, 24, RATE); le32(out, 28, RATE * 2); le16(out, 32, 2); le16(out, 34, 16);
        put(out, 36, "data"); le32(out, 40, 2 * n);
        for (int i = 0; i < n; i++) le16(out, 44 + 2 * i, (int) (x[i] * g * 32767f));
        return out;
    }

    private static void put(byte[] b, int o, String s) { for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i); }
    private static void le16(byte[] b, int o, int v) { b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); }
    private static void le32(byte[] b, int o, int v) { le16(b, o, v); le16(b, o + 2, v >> 16); }
}
