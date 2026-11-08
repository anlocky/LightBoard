package com.lightboard.keyboard;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Encrypted storage for what the keyboard learned while typing (words, word pairs, own words). */
final class LearnStore {
    private LearnStore() { }

    static final String FILE = "learn.dat";
    /** Order: learned, pairs, user_words, own. */
    static final int N = 4;
    /** Returned by {@link #read} when the file exists but cannot be decrypted right now. */
    static final String[] LOCKED = new String[0];

    private static final int MAGIC = 0x4C424C31;
    private static final int MAX_PART = 4_000_000;

    /** @return null if there is no (valid) file, {@link #LOCKED} if it cannot be opened now, else the four strings. */
    static String[] read(File f) {
        byte[] p = Vault.readFile(f);
        if (p == null) return null;
        if (p == Vault.LOCKED) return LOCKED;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(p))) {
            if (in.readInt() != MAGIC) return null;
            String[] v = new String[N];
            for (int i = 0; i < N; i++) {
                int len = in.readInt();
                if (len < 0 || len > MAX_PART) return null;
                byte[] b = new byte[len];
                in.readFully(b);
                v[i] = new String(b, StandardCharsets.UTF_8);
            }
            return v;
        } catch (IOException e) { return null; }
    }

    static byte[] pack(String[] v) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(4096);
        try (DataOutputStream o = new DataOutputStream(bo)) {
            o.writeInt(MAGIC);
            for (int i = 0; i < N; i++) {
                byte[] b = (v[i] == null ? "" : v[i]).getBytes(StandardCharsets.UTF_8);
                o.writeInt(b.length);
                o.write(b);
            }
        }
        return bo.toByteArray();
    }

    static void write(File f, String[] v, long seq) throws IOException { Vault.writeFile(f, pack(v), seq); }
}
