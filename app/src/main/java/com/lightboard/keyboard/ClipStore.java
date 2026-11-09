package com.lightboard.keyboard;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;

final class ClipStore {
    static final int MAX = 30, MAX_LEN = 10000, MAX_TOTAL = 40000, DEFAULT_DAYS = 1;

    static final class Item {
        final String text;
        boolean pinned;
        long minute;
        Item(String text, boolean pinned, long minute) { this.text = text; this.pinned = pinned; this.minute = minute; }
    }

    private final ArrayList<Item> items = new ArrayList<>();

    int size() { return items.size(); }
    Item get(int i) { return items.get(i); }

    boolean add(String text, long nowMin) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty() || t.length() > MAX_LEN) return false;
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (!it.text.equals(t)) continue;
            it.minute = nowMin;
            if (!it.pinned && i > pinnedCount()) { items.remove(i); items.add(pinnedCount(), it); }
            return true;
        }
        Item fresh = new Item(t, false, nowMin);
        items.add(pinnedCount(), fresh);
        shrink();
        return items.contains(fresh);
    }

    private int pinnedCount() {
        int n = 0;
        while (n < items.size() && items.get(n).pinned) n++;
        return n;
    }

    private void shrink() {
        while (items.size() > MAX || total() > MAX_TOTAL) {
            int victim = -1;
            for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).pinned) { victim = i; break; }
            if (victim < 0) break;
            items.remove(victim);
        }
    }

    private int total() {
        int n = 0;
        for (Item it : items) n += it.text.length();
        return n;
    }

    boolean expire(long nowMin, int ttlMin) {
        boolean ch = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            Item it = items.get(i);
            if (!it.pinned && nowMin - it.minute >= ttlMin) { items.remove(i); ch = true; }
        }
        return ch;
    }

    void remove(int i) { if (i >= 0 && i < items.size()) items.remove(i); }

    void togglePin(int i) {
        if (i < 0 || i >= items.size()) return;
        Item it = items.remove(i);
        it.pinned = !it.pinned;
        items.add(pinnedCount(), it);
    }

    void clear() {
        for (int i = items.size() - 1; i >= 0; i--) if (!items.get(i).pinned) items.remove(i);
    }

    void clearAll() { items.clear(); }

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

    private void normalize() {
        ArrayList<Item> pins = new ArrayList<>(), rest = new ArrayList<>();
        for (Item it : items) (it.pinned ? pins : rest).add(it);
        items.clear(); items.addAll(pins); items.addAll(rest);
        shrink();
    }

    byte[] snapshot() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(4096);
        try (DataOutputStream o = new DataOutputStream(bo)) {
            o.writeInt(items.size());
            for (Item it : items) { o.writeBoolean(it.pinned); o.writeLong(it.minute); o.writeUTF(it.text); }
        }
        return bo.toByteArray();
    }

    static void write(File f, byte[] plain) throws IOException { Vault.writeFile(f, plain, 0L); }

    static void write(File f, byte[] plain, long seq) throws IOException { Vault.writeFile(f, plain, seq); }

    /** True when the stored file exists but could not be decrypted (key store busy or unavailable). Nothing may be written then. */
    boolean locked;

    void touchAll(long nowMin) { for (Item it : items) it.minute = nowMin; }

    /** Reads f and puts what it holds below the items already in memory. @return false if it still cannot be opened. */
    boolean mergeFrom(File f) {
        ClipStore o = new ClipStore();
        o.loadFrom(f);
        if (o.locked) { locked = true; return false; }
        locked = false;
        for (Item it : o.items) {
            boolean dup = false;
            for (Item m : items) if (m.text.equals(it.text)) { dup = true; if (it.pinned) m.pinned = true; break; }
            if (!dup) items.add(it);
        }
        normalize();
        return true;
    }

    boolean loadFrom(File f) {
        items.clear();
        locked = false;
        boolean[] lg = new boolean[1];
        byte[] plain = Vault.readFile(f, lg);
        if (plain == null) return false;
        if (plain == Vault.LOCKED) { locked = true; return false; }
        boolean legacy = lg[0];
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
