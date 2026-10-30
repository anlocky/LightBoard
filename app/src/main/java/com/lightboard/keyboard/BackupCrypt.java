package com.lightboard.keyboard;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Шифрование файла резервной копии паролем: ключ из пароля — PBKDF2-HMAC-SHA256 (собран на HmacSHA256, он есть на любой версии Android),
 * шифр — AES-256-GCM. Формат: «LBK1» + соль (16) + число итераций (4) + IV (12) + шифртекст с меткой; заголовок входит в проверяемые данные,
 * поэтому подмена соли или числа итераций обнаруживается. Неверный пароль и повреждённый файл неотличимы: метка не сойдётся.
 */
final class BackupCrypt {
    private BackupCrypt() { }

    private static final byte[] MAGIC = {'L', 'B', 'K', '1'};
    private static final int SALT = 16, IV = 12, HEAD = 4 + SALT + 4, ITER = 250_000, MIN_ITER = 10_000, MAX_ITER = 2_000_000, TAG = 128;
    private static final SecureRandom RND = new SecureRandom();

    static boolean isEncrypted(byte[] b) {
        if (b == null || b.length < HEAD + IV + 16) return false;
        for (int i = 0; i < MAGIC.length; i++) if (b[i] != MAGIC[i]) return false;
        return true;
    }

    static byte[] encrypt(byte[] plain, char[] password) throws GeneralSecurityException {
        byte[] head = new byte[HEAD], iv = new byte[IV];
        System.arraycopy(MAGIC, 0, head, 0, 4);
        RND.nextBytes(iv);
        byte[] salt = new byte[SALT];
        RND.nextBytes(salt);
        System.arraycopy(salt, 0, head, 4, SALT);
        putInt(head, 4 + SALT, ITER);
        byte[] key = pbkdf2(password, salt, ITER);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG, iv));
            c.updateAAD(head);
            byte[] ct = c.doFinal(plain);
            byte[] out = new byte[HEAD + IV + ct.length];
            System.arraycopy(head, 0, out, 0, HEAD);
            System.arraycopy(iv, 0, out, HEAD, IV);
            System.arraycopy(ct, 0, out, HEAD + IV, ct.length);
            return out;
        } finally { Arrays.fill(key, (byte) 0); }
    }

    /** Бросает GeneralSecurityException (AEADBadTagException), если пароль неверный или файл изменён. */
    static byte[] decrypt(byte[] data, char[] password) throws GeneralSecurityException {
        if (!isEncrypted(data)) throw new GeneralSecurityException("not encrypted");
        int iter = getInt(data, 4 + SALT);
        if (iter < MIN_ITER || iter > MAX_ITER) throw new GeneralSecurityException("bad header");
        byte[] salt = Arrays.copyOfRange(data, 4, 4 + SALT);
        byte[] key = pbkdf2(password, salt, iter);
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG, data, HEAD, IV));
            c.updateAAD(data, 0, HEAD);
            return c.doFinal(data, HEAD + IV, data.length - HEAD - IV);
        } finally { Arrays.fill(key, (byte) 0); }
    }

    /** PBKDF2 (RFC 8018) с HMAC-SHA256, один блок: ключ 32 байта. */
    static byte[] pbkdf2(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        byte[] pw = new String(password).getBytes(StandardCharsets.UTF_8);
        if (pw.length == 0) throw new GeneralSecurityException("empty password");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pw, "HmacSHA256"));
            mac.update(salt);
            mac.update(new byte[]{0, 0, 0, 1});
            byte[] u = mac.doFinal(), t = u.clone();
            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < t.length; j++) t[j] ^= u[j];
            }
            return t;
        } finally { Arrays.fill(pw, (byte) 0); }
    }

    private static void putInt(byte[] b, int o, int v) { b[o] = (byte) (v >>> 24); b[o + 1] = (byte) (v >>> 16); b[o + 2] = (byte) (v >>> 8); b[o + 3] = (byte) v; }

    private static int getInt(byte[] b, int o) { return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF); }
}
