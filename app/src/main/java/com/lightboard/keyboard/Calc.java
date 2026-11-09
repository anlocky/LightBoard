package com.lightboard.keyboard;

import java.math.BigDecimal;
import java.math.MathContext;

final class Calc {
    private final String s;
    private int p, ops;

    private Calc(String s) { this.s = s; }

    private static boolean ok(char c) { return Character.isLetterOrDigit(c) || "+-*/^%().,× ÷xх".indexOf(c) >= 0; }

    static String solve(CharSequence before) {
        String t = before.toString();
        int end = t.length();
        while (end > 0 && t.charAt(end - 1) == ' ') end--;
        int start = end;
        while (start > 0 && end - start < 80 && ok(t.charAt(start - 1))) start--;
        for (int i = start; i < end; i++) {
            if (i > 0 && Character.isLetterOrDigit(t.charAt(i - 1)) && Character.isLetterOrDigit(t.charAt(i))) continue;
            String ex = t.substring(i, end).trim();
            if (ex.isEmpty()) continue;
            Calc c = new Calc(ex.replace('×', '*').replace('÷', '/'));
            double v;
            try { v = c.expr(); } catch (RuntimeException e) { continue; }
            if (c.p != c.s.length() || c.ops == 0 || Double.isNaN(v) || Double.isInfinite(v)) continue;
            return fmt(v, ex.indexOf(',') >= 0);
        }
        return null;
    }

    static String fmt(double v, boolean comma) {
        String r;
        if (v == Math.rint(v) && Math.abs(v) < 1e15) r = Long.toString((long) v);
        else r = new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();
        return comma ? r.replace('.', ',') : r;
    }

    private char peek() { while (p < s.length() && s.charAt(p) == ' ') p++; return p < s.length() ? s.charAt(p) : '\0'; }

    private double expr() {
        double v = term();
        for (char c = peek(); c == '+' || c == '-'; c = peek()) { p++; ops++; double r = term(); v = c == '+' ? v + r : v - r; }
        return v;
    }

    private double term() {
        double v = unary();
        for (;;) {
            char c = peek();
            if (c == 'x' || c == 'х') c = '*';
            if (c != '*' && c != '/' && c != '%') return v;
            p++; ops++;
            double r = unary();
            v = c == '*' ? v * r : c == '/' ? v / r : v % r;
        }
    }

    private double power() {
        double b = atom();
        if (peek() == '^') { p++; ops++; return Math.pow(b, unary()); }
        return b;
    }

    private double unary() {
        char c = peek();
        if (c == '-') { p++; return -unary(); }
        if (c == '+') { p++; return unary(); }
        return power();
    }

    private double atom() {
        char c = peek();
        if (c == '(') { p++; double v = expr(); if (peek() != ')') throw new IllegalStateException(); p++; return v; }
        if (Character.isDigit(c) || c == '.' || c == ',') {
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.' || s.charAt(p) == ',')) p++;
            return Double.parseDouble(s.substring(st, p).replace(',', '.'));
        }
        int st = p;
        while (p < s.length() && Character.isLetter(s.charAt(p))) p++;
        String f = s.substring(st, p).toLowerCase(java.util.Locale.ROOT);
        if (f.equals("pi") || f.equals("пи")) return Math.PI;
        if (f.equals("e")) return Math.E;
        if (f.isEmpty() || peek() != '(') throw new IllegalStateException();
        p++; double a = expr(); if (peek() != ')') throw new IllegalStateException(); p++; ops++;
        switch (f) {
            case "sqrt": return Math.sqrt(a);
            case "sin": return Math.sin(a);
            case "cos": return Math.cos(a);
            case "tan": return Math.tan(a);
            case "ln": return Math.log(a);
            case "log": return Math.log10(a);
            case "abs": return Math.abs(a);
            default: throw new IllegalStateException();
        }
    }
}
