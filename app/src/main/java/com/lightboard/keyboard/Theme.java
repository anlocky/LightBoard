package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

final class Theme {
    static final int ACCENT = Color.rgb(0, 122, 255), ACCENT_DOWN = Color.rgb(0, 98, 214);

    boolean dark, blur;
    int panelPct = 60;
    int key, keyDown, spec, specDown, space, text, muted, div, shadow, pop, panelTop, panelBot;
    int accent = ACCENT, onAccent = Color.WHITE;
    int panelHue, panelInt, keyHue, keyInt, keyText, keyMuted;

    private static final int DARK_TEXT = Color.rgb(28, 29, 34), DARK_MUTED = Color.rgb(104, 108, 120),
            LIGHT_TEXT = Color.rgb(244, 245, 250), LIGHT_MUTED = Color.rgb(160, 164, 178);

    Theme() { update(); }

    boolean set(boolean dark, boolean blur, int panelPct) { return set(dark, blur, panelPct, 0, 0, 0, 0); }

    boolean set(boolean dark, boolean blur, int panelPct, int pHue, int pInt, int kHue, int kInt) {
        if (this.dark == dark && this.blur == blur && this.panelPct == panelPct && panelHue == pHue && panelInt == pInt && keyHue == kHue && keyInt == kInt) return false;
        this.dark = dark; this.blur = blur; this.panelPct = panelPct;
        panelHue = pHue; panelInt = pInt; keyHue = kHue; keyInt = kInt;
        update();
        return true;
    }

    static final int HUE_MAX = 380;

    static int hueColor(int h) { return hue(h, 1f); }

    private static int hue(int h, float v) {
        if (h > 360) {
            int k = Math.max(0, Math.round(255f * v * (HUE_MAX - Math.min(h, HUE_MAX)) / (HUE_MAX - 360)));
            return Color.rgb(k, 0, 0);
        }
        return Color.HSVToColor(new float[]{h % 360, 1f, v});
    }

    private static int tintTo(int base, int target, float t) {
        float u = 1f - t;
        return Color.argb(Color.alpha(base), Math.round(Color.red(base) * u + Color.red(target) * t),
                Math.round(Color.green(base) * u + Color.green(target) * t), Math.round(Color.blue(base) * u + Color.blue(target) * t));
    }

    private static boolean light(int c) { return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c) > 148f; }

    private void tint() {
        if (panelInt > 0) {
            int tp = hue(panelHue, 1f);
            float t = panelInt / 100f;
            panelTop = tintTo(panelTop, tp, t);
            panelBot = tintTo(panelBot, tp, t);
            boolean lt = light(panelBot);
            text = lt ? DARK_TEXT : LIGHT_TEXT;
            muted = lt ? DARK_MUTED : LIGHT_MUTED;
            div = lt ? Color.argb(50, 70, 78, 110) : Color.argb(60, 255, 255, 255);
        }
        if (keyInt > 0) {
            int tk = hue(keyHue, 1f), ts = hue(keyHue, 0.86f);
            float t = keyInt / 100f;
            key = tintTo(key, tk, t);
            space = tintTo(space, tk, t);
            pop = tintTo(pop, tk, t);
            spec = tintTo(spec, ts, t);
            keyDown = tintTo(keyDown, dark ? mix(key, Color.WHITE, 0.18f) : mix(key, Color.BLACK, 0.12f), t);
            specDown = tintTo(specDown, dark ? mix(spec, Color.WHITE, 0.18f) : mix(spec, Color.BLACK, 0.12f), t);
            boolean lt = light(key);
            keyText = lt ? DARK_TEXT : LIGHT_TEXT;
            keyMuted = lt ? DARK_MUTED : LIGHT_MUTED;
        }
    }

    private void update() {
        int pa = blur ? Math.round(255f * panelPct / 100f) : 250;
        if (dark) {
            panelTop = Color.argb(pa, 46, 48, 60);
            panelBot = Color.argb(Math.min(255, pa + 10), 36, 38, 48);
            key = Color.argb(blur ? 236 : 250, 98, 100, 112);
            keyDown = Color.rgb(132, 134, 148);
            spec = Color.argb(blur ? 226 : 244, 68, 70, 82);
            specDown = Color.rgb(104, 106, 120);
            space = Color.argb(blur ? 236 : 250, 98, 100, 112);
            text = Color.rgb(244, 245, 250); muted = Color.rgb(160, 164, 178);
            div = Color.argb(60, 255, 255, 255); shadow = Color.argb(70, 0, 0, 0);
            pop = Color.rgb(124, 126, 140);
        } else {
            panelTop = Color.argb(pa, 238, 241, 252);
            panelBot = Color.argb(Math.min(255, pa + 10), 230, 234, 248);
            key = Color.argb(blur ? 240 : 250, 251, 252, 255);
            keyDown = Color.rgb(203, 208, 225);
            spec = Color.argb(blur ? 226 : 244, 237, 240, 250);
            specDown = Color.rgb(203, 208, 225);
            space = Color.argb(blur ? 236 : 248, 246, 248, 254);
            text = Color.rgb(28, 29, 34); muted = Color.rgb(104, 108, 120);
            div = Color.argb(50, 70, 78, 110); shadow = Color.argb(24, 40, 50, 90);
            pop = Color.rgb(255, 255, 255);
        }
        keyText = text; keyMuted = muted;
        if (panelInt > 0 || keyInt > 0) tint();
    }

    static int fade(int c, float f) {
        if (f >= 0.999f) return c;
        if (f <= 0f) return c & 0x00FFFFFF;
        return (Math.round((c >>> 24) * f) << 24) | (c & 0x00FFFFFF);
    }

    static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        float u = 1f - t;
        return Color.argb((int) (Color.alpha(a) * u + Color.alpha(b) * t), (int) (Color.red(a) * u + Color.red(b) * t),
                (int) (Color.green(a) * u + Color.green(b) * t), (int) (Color.blue(a) * u + Color.blue(b) * t));
    }

    static final String[] SCHEMES = {"auto", "blue", "green", "purple", "red", "orange", "teal", "yellow", "pink", "indigo", "cyan", "lime", "brown", "grey"};

    static int seedOf(String s) {
        switch (s == null ? "auto" : s) {
            case "blue": return Color.rgb(52, 87, 181);
            case "green": return Color.rgb(46, 125, 84);
            case "purple": return Color.rgb(103, 80, 164);
            case "red": return Color.rgb(179, 38, 30);
            case "orange": return Color.rgb(176, 96, 0);
            case "teal": return Color.rgb(0, 109, 119);
            case "yellow": return Color.rgb(140, 115, 0);
            case "pink": return Color.rgb(170, 45, 110);
            case "indigo": return Color.rgb(62, 70, 170);
            case "cyan": return Color.rgb(0, 100, 150);
            case "lime": return Color.rgb(95, 125, 20);
            case "brown": return Color.rgb(121, 85, 72);
            case "grey": return Color.rgb(96, 106, 115);
            default: return 0;
        }
    }

    void setMaterial(Context c, boolean dark, String scheme) {
        this.dark = dark; this.blur = false;
        int panel, keyC, specC, txt, mut, dv, acc, onAcc;
        if (dark) {
            panel = Color.rgb(27, 28, 32); keyC = Color.rgb(52, 54, 59); specC = Color.rgb(68, 71, 78);
            txt = Color.rgb(228, 230, 236); mut = Color.rgb(160, 164, 176); dv = Color.rgb(80, 83, 90);
            acc = Color.rgb(168, 199, 250); onAcc = Color.rgb(6, 46, 111);
        } else {
            panel = Color.rgb(241, 244, 249); keyC = Color.WHITE; specC = Color.rgb(221, 226, 222);
            txt = Color.rgb(30, 30, 30); mut = Color.rgb(96, 100, 112); dv = Color.rgb(198, 202, 205);
            acc = Color.rgb(52, 87, 181); onAcc = Color.WHITE;
        }
        int seed = seedOf(scheme);
        if (seed != 0) {
            int W = Color.WHITE;
            if (dark) {
                panel = mix(Color.rgb(24, 25, 28), seed, 0.12f); keyC = mix(Color.rgb(48, 50, 55), seed, 0.14f);
                specC = mix(Color.rgb(62, 65, 70), seed, 0.30f); dv = mix(Color.rgb(70, 72, 78), seed, 0.25f);
                acc = mix(seed, W, 0.55f); onAcc = mix(seed, Color.BLACK, 0.75f);
                txt = Color.rgb(230, 232, 238); mut = Color.rgb(160, 164, 176);
            } else {
                panel = mix(W, seed, 0.07f); keyC = W; specC = mix(W, seed, 0.20f); dv = mix(W, seed, 0.28f);
                acc = seed; onAcc = W; txt = Color.rgb(28, 28, 30); mut = Color.rgb(96, 100, 112);
            }
        } else if (Build.VERSION.SDK_INT >= 31) {
            try {
                panel = c.getColor(dark ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50);
                specC = c.getColor(dark ? android.R.color.system_accent2_700 : android.R.color.system_accent2_100);
                keyC = c.getColor(dark ? android.R.color.system_neutral1_700 : android.R.color.system_neutral1_0);
                txt = c.getColor(dark ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900);
                mut = c.getColor(dark ? android.R.color.system_neutral2_300 : android.R.color.system_neutral2_600);
                dv = c.getColor(dark ? android.R.color.system_neutral2_600 : android.R.color.system_neutral2_200);
                acc = c.getColor(dark ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600);
                onAcc = c.getColor(dark ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0);
            } catch (RuntimeException ignored) { }
        }
        panelTop = panelBot = panel; key = keyC; space = keyC; spec = specC;
        keyDown = mix(keyC, txt, 0.14f); specDown = mix(specC, txt, 0.14f);
        text = txt; muted = mut; div = dv; shadow = Color.argb(40, 0, 0, 0); pop = keyC;
        accent = acc; onAccent = onAcc;
    }
}
