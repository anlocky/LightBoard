package com.lightboard.keyboard;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * История буфера обмена (файл шифруется через Vault; строковый формат save()/load() от Android не зависит). Закреплённые записи лежат сверху и не удаляются сами,
 * остальные живут столько, сколько задано в настройках (по умолчанию 3 дня), чтобы случайно скопированное не хранилось вечно. Повтор поднимает запись наверх.
 * Хранится одной строкой: текст кодируется URL-кодированием, поэтому в SharedPreferences не попадают управляющие символы.
 */
final class ClipStore {
    static final int MAX = 30, MAX_LEN = 10000, MAX_TOTAL = 40000, DEFAULT_DAYS = 3; // записи длиннее MAX_LEN в историю не идут (на панели подсказок они всё равно доступны)

    static final class Item {
        final String text;
        boolean pinned;
        long minute; // когда скопировано (минуты от начала эпохи)
        Item(String text, boolean pinned, long minute) { this.text = text; this.pinned = pinned; this.minute = minute; }
    }

    private final ArrayList<Item> items = new ArrayList<>(); // закреплённые сверху, дальше от новых к старым

    int size() { return items.size(); }
    Item get(int i) { return items.get(i); }

    /** true — список изменился. */
    boolean add(String text, long nowMin) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.length() > MAX_LEN) return false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (!it.text.equals(t)) continue;
            it.minute = nowMin;
            if (!it.pinned && i > pinnedCount()) { items.remove(i); items.add(pinnedCount(), it); } // подняли наверх обычных
            return true;
        }
        Item fresh = new Item(t, false, nowMin);
        items.add(pinnedCount(), fresh);
        shrink();
        return items.contains(fresh); // false: всё занято закреплёнными, новая запись в историю не поместилась
    }

    private int pinnedCount() {
        int n = 0;
        while (n < items.size() && items.get(n).pinned) n++;
        return n;
    }

    /** Убирает лишнее: по числу и по общему объёму текста, начиная с самых старых обычных записей. */
    private void shrink() {
        while (items.size() > MAX || total() > MAX_TOTAL) {
            int victim = -1;
            for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).pinned) { victim = i; break; }
            if (victim < 0) break; // остались только закреплённые
            items.remove(victim);
        }
    }

    private int total() {
        int n = 0;
        for (Item it : items) n += it.text.length();
        return n;
    }

    /** Удаляет обычные записи старше ttlMin минут. true — что-то удалили. */
    boolean expire(long nowMin, int ttlMin) {
        boolean ch = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (!it.pinned && nowMin - it.minute >= ttlMin) { items.remove(i); ch = true; }
        }
        return ch;
    }

    void remove(int i) { if (i >= 0 && i < items.size()) items.remove(i); }

    /** Закрепить/открепить; закреплённые идут первыми в порядке закрепления. */
    void togglePin(int i) {
        if (i < 0 || i >= items.size()) return;
        Item it = items.remove(i);
        it.pinned = !it.pinned;
        items.add(pinnedCount(), it); // закрепили — в конец закреплённых, открепили — первой среди обычных
    }

    /** Очистить всё, кроме закреплённых. */
    void clear() {
        for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).pinned) items.remove(i);
    }

    void clearAll() { items.clear(); }

    // ---------- хранение ----------
    String save() {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            try { sb.append(it.pinned ? 'P' : 'U').append(Long.toString(it.minute, 36)).append('.').append(URLEncoder.encode(it.text, "UTF-8")).append(' '); }
            catch (UnsupportedEncodingException ignored) { }
        }
        return sb.toString().trim();
    }

    void load(String s) {
        items.clear();
        if (s == null || s.isEmpty()) return;
        for (String e : s.split(" ")) {
            int dot = e.indexOf('.');
            if (e.length() < 3 || dot < 2 || (e.charAt(0) != 'P' && e.charAt(0) != 'U')) continue;
            try {
                long min = Long.parseLong(e.substring(1, dot), 36);
                String t = URLDecoder.decode(e.substring(dot + 1), "UTF-8");
                if (!t.isEmpty() && t.length() <= MAX_LEN) items.add(new Item(t, e.charAt(0) == 'P', min));
            } catch (UnsupportedEncodingException | IllegalArgumentException ignored) { }
        }
        normalize();
    }

    /** Порядок на всякий случай: закреплённые первыми, внутри групп порядок из файла. */
    private void normalize() {
        ArrayList<Item> pins = new ArrayList<>(), rest = new ArrayList<>();
        for (Item it : items) (it.pinned ? pins : rest).add(it);
        items.clear(); items.addAll(pins); items.addAll(rest);
        shrink();
    }

    // ---------- хранение файлом (UTF-8, шифруется Vault: AES-GCM, ключ в Android Keystore) ----------
    /** Пишет во временный файл и переименовывает: читатель из другого процесса не увидит половину файла. Без ключа на диск ничего не пишется. */
    void saveTo(File f) throws IOException { write(f, snapshot()); }

    /** Содержимое списка в виде байтов (быстро, на главном потоке); шифрование и запись можно делать в фоне через write(). */
    byte[] snapshot() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(4096);
        try (DataOutputStream o = new DataOutputStream(bo)) {
            o.writeInt(items.size());
            for (Item it : items) { o.writeBoolean(it.pinned); o.writeLong(it.minute); o.writeUTF(it.text); }
        }
        return bo.toByteArray();
    }

    /** Шифрует и атомарно записывает; вызовы выстраиваются в очередь (один временный файл). */
    static synchronized void write(File f, byte[] plain) throws IOException {
        byte[] enc = Vault.seal(plain);
        if (enc == null) { f.delete(); throw new IOException("vault"); } // ключ недоступен: открытым текстом историю не храним
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(enc); o.getFD().sync(); }
        if (!tmp.renameTo(f)) throw new IOException("rename");
    }

    /** Нет файла, он битый или не расшифровался — список пуст. true — прочитан старый файл без шифрования (его стоит сразу пересохранить). */
    boolean loadFrom(File f) {
        items.clear();
        if (!f.exists() || f.length() > 4_000_000L) return false;
        byte[] raw = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int p = 0, n;
            while (p < raw.length && (n = in.read(raw, p, raw.length - p)) > 0) p += n;
            if (p != raw.length) return false;
        } catch (IOException e) { return false; }
        boolean legacy = !Vault.isSealed(raw);
        byte[] plain = legacy ? raw : Vault.open(raw);
        if (plain == null) return false;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain))) {
            int n = in.readInt();
            if (n < 0 || n > 4 * MAX) return false;
            for (int i = 0; i < n; i++) {
                boolean pin = in.readBoolean();
                long min = in.readLong();
                String t = in.readUTF();
                if (!t.isEmpty() && t.length() <= MAX_LEN) items.add(new Item(t, pin, min));
            }
        } catch (IOException ignored) { }
        normalize();
        return legacy;
    }
}
