package com.lightboard.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;

/** Цвета для обеих панелей (буквы и эмодзи). Значения сняты с референсного скриншота iOS. */
final class Theme {
    static final int ACCENT = Color.rgb(0, 122, 255), ACCENT_DOWN = Color.rgb(0, 98, 214);

    boolean dark, blur;
    int panelPct = 60; // непрозрачность подложки при размытии, %
    int key, keyDown, spec, specDown, space, text, muted, div, shadow, pop, panelTop, panelBot;
    int accent = ACCENT, onAccent = Color.WHITE; // используются только дизайном Gboard

    Theme() { update(); }

    /** @return true, если что-то изменилось */
    boolean set(boolean dark, boolean blur, int panelPct) {
        if (this.dark == dark && this.blur == blur && this.panelPct == panelPct) return false;
        this.dark = dark; this.blur = blur; this.panelPct = panelPct; update();
        return true;
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
