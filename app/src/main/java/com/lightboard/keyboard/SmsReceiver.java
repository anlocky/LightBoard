package com.lightboard.keyboard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SmsReceiver extends BroadcastReceiver {
    interface Sink { void onCode(String code); }

    static volatile Sink sink;

    private static String pending;
    private static long pendingAt;

    static synchronized void put(String code) { pending = code; pendingAt = SystemClock.elapsedRealtime(); }
    static synchronized String fresh(long ttl) { if (pending != null && SystemClock.elapsedRealtime() - pendingAt >= ttl) pending = null; return pending; }
    static synchronized long age() { return pending == null ? 0L : SystemClock.elapsedRealtime() - pendingAt; }
    static synchronized void clear() { pending = null; }

    private static final Pattern PLAIN = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)"), SPLIT = Pattern.compile("(?<!\\d)(\\d{3})[ -](\\d{3})(?!\\d)");
    private static final String[] KEYS = {"код", "code", "парол", "password", "otp", "подтвержд", "confirm", "verif", "одноразов", "passcode", "pin-code", "pin code", "kod"};

    private static final Pattern CURRENCY = Pattern.compile("^\\s?(руб|р(?!\\p{L})|₽|rub|usd|eur|byn|kzt|uah|грн|тг|₸|\\$|€|%|тенге)");
    private static final String[] MONEY = {"сумм", "amount", "баланс", "balance", "остат", "итого", "total"};

    private static boolean money(String low, int st, int en) {
        if (st > 0 && "$€₽".indexOf(low.charAt(st - 1)) >= 0) return true;
        if (CURRENCY.matcher(low.substring(en)).find()) return true;
        String before = low.substring(Math.max(0, st - 10), st);
        for (String w : MONEY) if (before.contains(w)) return true;
        return false;
    }

    /** The code is the number closest after a keyword such as "code"; amounts of money are never taken. */
    static String extract(String body) {
        String low = body.toLowerCase(Locale.ROOT);
        java.util.ArrayList<Integer> keyEnds = new java.util.ArrayList<>();
        for (String k : KEYS) {
            for (int i = low.indexOf(k); i >= 0; i = low.indexOf(k, i + 1)) keyEnds.add(i + k.length());
        }
        if (keyEnds.isEmpty()) return null;
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            Matcher m = (pass == 0 ? SPLIT : PLAIN).matcher(body);
            while (m.find()) {
                if (money(low, m.start(), m.end())) continue;
                int d = Integer.MAX_VALUE;
                for (int ke : keyEnds) {
                    int v = m.start() >= ke ? m.start() - ke : (ke - m.end()) + 1000;
                    if (v < d) d = v;
                }
                if (d < bestD) { bestD = d; best = pass == 0 ? m.group(1) + m.group(2) : m.group(1); }
            }
        }
        return best;
    }

    @SuppressWarnings("deprecation")
    @Override public void onReceive(Context c, Intent in) {
        if (in == null || !"android.provider.Telephony.SMS_RECEIVED".equals(in.getAction())) return;
        try {
            if (!c.getSharedPreferences("lb", Context.MODE_PRIVATE).getBoolean("sms", false)) return;
            SmsMessage[] ms = Telephony.Sms.Intents.getMessagesFromIntent(in);
            if (ms == null || ms.length == 0) return;
            StringBuilder sb = new StringBuilder();
            for (SmsMessage s : ms) if (s != null && s.getMessageBody() != null) sb.append(s.getMessageBody());
            String code = extract(sb.toString());
            if (code == null) return;
            put(code);
            Sink s = sink;
            if (s != null) s.onCode(code);
        } catch (RuntimeException ignored) { }
    }
}
