package com.lightboard.keyboard;

import java.util.ArrayList;

final class KeyPlan {
    static final int CHAR = 0, SHIFT = 1, BACK = 2, PAGE = 3, SPACE = 4, ENTER = 5, EMOJI = 6, MIC = 7, PUNCT = 8, LANG = 9,
            TAB = 10, CAPS = 11, LEFT = 12, RIGHT = 13;

    static final class Spec {
        final int kind, target;
        final String label;
        final float w;
        final boolean letter;
        Spec(int kind, String label, float w, int target, boolean letter) { this.kind = kind; this.label = label; this.w = w; this.target = target; this.letter = letter; }
    }

    final float total;
    final boolean pill;
    final ArrayList<ArrayList<Spec>> rows = new ArrayList<>();

    private KeyPlan(float total, boolean pill) { this.total = total; this.pill = pill; }

    private ArrayList<Spec> row() { ArrayList<Spec> r = new ArrayList<>(); rows.add(r); return r; }
    private static void ch(ArrayList<Spec> r, String s, float w, boolean letter) { r.add(new Spec(s.equals(" ") ? SPACE : CHAR, s, w, 0, letter)); }
    private static void kind(ArrayList<Spec> r, int k, float w) { r.add(new Spec(k, null, w, 0, false)); }
    private static void chars(ArrayList<Spec> r, String s, boolean letter) { for (int i = 0; i < s.length(); i++) ch(r, String.valueOf(s.charAt(i)), 1f, letter); }

    static KeyPlan numpad(int mode) {
        boolean pin = mode == 1;
        KeyPlan p = new KeyPlan(pin ? 3f : 3.85f, true);
        String[] sym = Layouts.pad(mode);
        for (int r = 0; r < 3; r++) {
            ArrayList<Spec> row = p.row();
            for (int c = 0; c < 3; c++) ch(row, String.valueOf(r * 3 + c + 1), 1f, false);
            if (!pin) { if (r == 0) kind(row, BACK, 0.85f); else ch(row, sym[r == 1 ? 2 : 3], 0.85f, false); }
        }
        ArrayList<Spec> last = p.row();
        if (pin) { kind(last, BACK, 1f); ch(last, "0", 1f, false); kind(last, ENTER, 1f); }
        else { ch(last, sym[0], 1f, false); ch(last, "0", 1f, false); ch(last, sym[1], 1f, false); kind(last, ENTER, 0.85f); }
        return p;
    }

    static String abc(Layouts.L lay) {
        char c = lay.r1.isEmpty() ? 'q' : lay.r1.charAt(0);
        if (c >= 0x400 && c <= 0x4FF) return "АБВ";
        if (c >= 0x370 && c <= 0x3FF) return "ΑΒΓ";
        if (c >= 0x5D0 && c <= 0x5EA) return "אבג";
        return "ABC";
    }

    private static void bottomSym(KeyPlan p, Layouts.L lay, String l, String r) {
        ArrayList<Spec> b = p.row();
        b.add(new Spec(PAGE, abc(lay), 1.5f, 0, false));
        b.add(new Spec(PUNCT, l, 1f, 0, false));
        kind(b, EMOJI, 1f);
        kind(b, SPACE, 4f);
        b.add(new Spec(PUNCT, r, 1f, 0, false));
        kind(b, ENTER, 1.5f);
    }

    static KeyPlan symbols(Layouts.L lay, int page) { return symbols(lay, page, false); }

    static KeyPlan symbols(Layouts.L lay, int page, boolean extra) {
        KeyPlan p = new KeyPlan(10f, false);
        ArrayList<Spec> r = p.row();
        if (page == 1) {
            chars(r, "1234567890", false);
            if (extra) { r = p.row(); chars(r, "~`|\u2022\u221A\u03C0\u00F7\u00D7\u00A7\u0394", false); }
            r = p.row(); chars(r, "@#$_&-+()/", false);
        } else {
            if (extra) { chars(r, "1234567890", false); r = p.row(); }
            chars(r, "~`|\u2022\u221A\u03C0\u00F7\u00D7\u00A7\u0394", false);
            String c = lay.cur.equals("$") || lay.cur.equals("\u00A3") || lay.cur.equals("\u20AC") || lay.cur.equals("\u00A2") ? "\u00A5" : lay.cur;
            r = p.row(); chars(r, "\u00A3\u20AC" + c + "\u00A2^\u00B0={}\\", false);
        }
        r = p.row();
        r.add(new Spec(PAGE, page == 1 ? "=\\<" : "?123", 1.5f, page == 1 ? 2 : 1, false));
        chars(r, page == 1 ? "*\"':;!?" : "%\u00A9\u00AE\u2122\u2713[]", false);
        kind(r, BACK, 1.5f);
        bottomSym(p, lay, page == 1 ? "," : "<", page == 1 ? "." : ">");
        return p;
    }

    private static final String TOP_SYM = "%\\|=[]<>{}\u00B0~", ROW2_SYM = "@#$_&-+()/", ROW3_SYM = "*\"':;!?";

    static String topSym(int pos) { return pos < TOP_SYM.length() ? String.valueOf(TOP_SYM.charAt(pos)) : null; }

    static String hint(int rr, int pos, boolean numRow, boolean num, boolean sym) {
        if (rr == 0) {
            if (!numRow && num) return pos < 10 ? String.valueOf((pos + 1) % 10) : null;
            return sym && pos < TOP_SYM.length() ? String.valueOf(TOP_SYM.charAt(pos)) : null;
        }
        String t = rr == 1 ? ROW2_SYM : ROW3_SYM;
        return sym && pos < t.length() ? String.valueOf(t.charAt(pos)) : null;
    }

    static KeyPlan tablet(Layouts.L lay, boolean numRow, int page) {
        KeyPlan p = new KeyPlan(15f, false);
        if (page != 0) {
            KeyPlan q = new KeyPlan(12.3f, false);
            ArrayList<Spec> r;
            if (numRow) { r = q.row(); chars(r, (page == 1 ? "\u00A1" : "\u00BF") + "1234567890", false); kind(r, BACK, 1.3f); }
            r = q.row(); kind(r, TAB, 1.3f); chars(r, page == 1 ? "%\\|=[]<>{}\u00B0" : "~`|\u2022\u221A\u03C0\u00F7\u00D7\u00A7\u0394\u2026", false);
            r = q.row(); kind(r, CAPS, 1.5f); chars(r, page == 1 ? "@#$_&-+()" : "\u00A3\u00A2\u20AC\u00A5^\u00B0={}", false); kind(r, ENTER, 1.8f);
            r = q.row(); r.add(new Spec(PAGE, page == 1 ? "=\\<" : "?123", 2.3f, page == 1 ? 2 : 1, false));
            chars(r, page == 1 ? "*\"':;!?,./" : "%\u00A9\u00AE\u2122\u2713[]<>\\", false);
            ArrayList<Spec> b = q.row();
            b.add(new Spec(PAGE, abc(lay), 1.5f, 0, false));
            kind(b, EMOJI, 1.5f);
            kind(b, SPACE, 6.0f); kind(b, LEFT, 1f); kind(b, RIGHT, 1f);
            b.add(new Spec(PAGE, abc(lay), 1.3f, 0, false));
            return q;
        } else {
            String[] t = lay.tabletRows();
            ArrayList<Spec> r;
            if (numRow) { r = p.row(); chars(r, "`1234567890-=", false); kind(r, BACK, 2f); }
            r = p.row(); kind(r, TAB, 1.5f); chars(r, t[0], true);
            float rest = 15f - 1.5f - t[0].length();
            if (numRow) ch(r, "\\", rest, false); else kind(r, BACK, rest);
            r = p.row(); kind(r, CAPS, 1.75f); chars(r, t[1], true); kind(r, ENTER, 15f - 1.75f - t[1].length());
            r = p.row(); kind(r, SHIFT, 15f - 1.75f - t[2].length()); chars(r, t[2], true); kind(r, SHIFT, 1.75f);
        }
        ArrayList<Spec> b = p.row();
        b.add(new Spec(PAGE, page == 0 ? "?123" : "ABC", 1.4f, page == 0 ? 1 : 0, false));
        kind(b, EMOJI, 1.4f); kind(b, LANG, 1.4f); kind(b, SPACE, 6.5f); kind(b, LEFT, 1.4f); kind(b, RIGHT, 1.4f);
        b.add(new Spec(PAGE, page == 0 ? "?123" : "ABC", 1.5f, page == 0 ? 1 : 0, false));
        return p;
    }
}
