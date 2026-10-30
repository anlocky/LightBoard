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

/**
 * Код подтверждения из СМС -> чип в полосе подсказок. Работает только если включён переключатель в настройках и выдано разрешение на СМС.
 * Текст сообщения нигде не сохраняется: хранится один найденный код на 5 минут (удаляется при вставке, закрытии чипа и по истечении срока).
 */
public final class SmsReceiver extends BroadcastReceiver {
    interface Sink { void onCode(String code); }

    static volatile Sink sink;

    // код живёт только в памяти процесса (на диск не пишется) и не дольше срока, который задаёт клавиатура
    private static String pending;
    private static long pendingAt;

    static synchronized void put(String code) { pending = code; pendingAt = SystemClock.elapsedRealtime(); }
    static synchronized String fresh(long ttl) { if (pending != null && SystemClock.elapsedRealtime() - pendingAt >= ttl) pending = null; return pending; }
    static synchronized long age() { return pending == null ? 0L : SystemClock.elapsedRealtime() - pendingAt; }
    static synchronized void clear() { pending = null; }

    private static final Pattern PLAIN = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)"), SPLIT = Pattern.compile("(?<!\\d)(\\d{3})[ -](\\d{3})(?!\\d)");
    private static final String[] KEYS = {"код", "code", "парол", "password", "otp", "подтвержд", "confirm", "verif", "одноразов", "passcode", "pin-code", "pin code", "kod"};

    static String extract(String body) {
        String low = body.toLowerCase(Locale.ROOT);
        boolean ok = false;
        for (String k : KEYS) if (low.contains(k)) { ok = true; break; }
        if (!ok) return null; // без слов про код номера и суммы кодами не считаем
        Matcher m = SPLIT.matcher(body);
        if (m.find()) return m.group(1) + m.group(2);
        m = PLAIN.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    @SuppressWarnings("deprecation")
    @Override public void onReceive(Context c, Intent in) {
        if (in == null || !"android.provider.Telephony.SMS_RECEIVED".equals(in.getAction())) return;
        try {
            if (!c.getSharedPreferences("lb", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS).getBoolean("sms", false)) return;
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
