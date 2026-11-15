package com.lightboard.keyboard;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import java.security.GeneralSecurityException;
import javax.crypto.AEADBadTagException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Vault {
    private Vault() { }

    private static final String ALIAS = "lb_clips_v1";
    private static final byte[] MAGIC = {'L', 'B', 'C', '1'};
    private static final int IV = 12, TAG = 128;

    private static synchronized SecretKey key(File dir) throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        if (dir == null) return generate();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(new File(dir, "key.lock"), "rw");
             java.nio.channels.FileLock fl = raf.getChannel().lock()) {
            ks.load(null);
            if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
            return generate();
        }
    }

    private static SecretKey generate() throws GeneralSecurityException {
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return g.generateKey();
    }

    private static synchronized SecretKey existingKey() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ks.containsAlias(ALIAS) ? (SecretKey) ks.getKey(ALIAS, null) : null;
    }

    private static long seq;

    static synchronized long nextSeq() { return ++seq; }

    static boolean isSealed(byte[] b) {
        if (b == null || b.length < MAGIC.length + IV + 16) return false;
        for (int i = 0; i < MAGIC.length; i++) if (b[i] != MAGIC[i]) return false;
        return true;
    }

    static byte[] seal(byte[] plain, File dir) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(dir));
            byte[] iv = c.getIV(), ct = c.doFinal(plain);
            if (iv == null || iv.length != IV) return null;
            byte[] out = new byte[MAGIC.length + IV + ct.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(iv, 0, out, MAGIC.length, IV);
            System.arraycopy(ct, 0, out, MAGIC.length + IV, ct.length);
            return out;
        } catch (GeneralSecurityException | IOException | RuntimeException e) { return null; }
    }

    static final byte[] LOCKED = new byte[0];

    private static final byte[] CORRUPT = new byte[0];

    private static final HashMap<String, Long> LAST = new HashMap<>();

    static synchronized void writeFile(File f, byte[] plain, long seq) throws IOException {
        String key = f.getPath();
        Long last = LAST.get(key);
        if (seq != 0L && last != null && seq < last) return;
        byte[] enc = seal(plain, f.getParentFile());
        if (enc == null) throw new IOException("vault");
        File tmp = new File(key + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) { o.write(enc); o.getFD().sync(); }
        if (!tmp.renameTo(f)) throw new IOException("rename");
        if (seq != 0L) LAST.put(key, seq);
    }

    static synchronized void barrier(File f, long seq) { LAST.put(f.getPath(), seq); }

    static byte[] readFile(File f) { return readFile(f, null); }

    static byte[] readFile(File f, boolean[] legacy) {
        if (!f.exists() || f.length() > 8_000_000L) return null;
        byte[] raw = new byte[(int) f.length()];
        try (InputStream in = new FileInputStream(f)) {
            int p = 0, n;
            while (p < raw.length && (n = in.read(raw, p, raw.length - p)) > 0) p += n;
            if (p != raw.length) return null;
        } catch (IOException e) { return null; }
        if (legacy != null) legacy[0] = !isSealed(raw);
        if (!isSealed(raw)) return raw;
        byte[] plain = tryOpen(raw);
        if (plain == CORRUPT) {
            File b1 = new File(f.getPath() + ".bad"), b2 = new File(f.getPath() + ".bad2");
            if (b1.exists()) { b2.delete(); b1.renameTo(b2); }
            f.renameTo(b1);
            return null;
        }
        return plain;
    }

    static byte[] open(byte[] sealed) {
        byte[] r = tryOpen(sealed);
        return r == LOCKED || r == CORRUPT ? null : r;
    }

    private static byte[] tryOpen(byte[] sealed) {
        try {
            SecretKey k = existingKey();
            if (k == null) return CORRUPT;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(TAG, sealed, MAGIC.length, IV));
            return c.doFinal(sealed, MAGIC.length + IV, sealed.length - MAGIC.length - IV);
        } catch (AEADBadTagException | KeyPermanentlyInvalidatedException e) {
            return CORRUPT;
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            return LOCKED;
        }
    }
}
