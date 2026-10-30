package com.lightboard.keyboard;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Шифрование файла истории буфера: AES-256-GCM, ключ лежит в Android Keystore и не покидает его (из файла данных без устройства не прочитать).
 * Формат: «LBC1» + IV (12 байт) + шифртекст с меткой. Если ключ недоступен, возвращается null — открытым текстом на диск не пишем.
 */
final class Vault {
    private Vault() { }

    private static final String ALIAS = "lb_clips_v1";
    private static final byte[] MAGIC = {'L', 'B', 'C', '1'};
    private static final int IV = 12, TAG = 128;

    private static synchronized SecretKey key() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return (SecretKey) ks.getKey(ALIAS, null);
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return g.generateKey();
    }

    static boolean isSealed(byte[] b) {
        if (b == null || b.length < MAGIC.length + IV + 16) return false;
        for (int i = 0; i < MAGIC.length; i++) if (b[i] != MAGIC[i]) return false;
        return true;
    }

    /** null — зашифровать не удалось. */
    static byte[] seal(byte[] plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV(), ct = c.doFinal(plain);
            if (iv == null || iv.length != IV) return null;
            byte[] out = new byte[MAGIC.length + IV + ct.length];
            System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
            System.arraycopy(iv, 0, out, MAGIC.length, IV);
            System.arraycopy(ct, 0, out, MAGIC.length + IV, ct.length);
            return out;
        } catch (GeneralSecurityException | IOException | RuntimeException e) { return null; }
    }

    /** null — ключа нет (данные приложения восстановили на другом устройстве) или файл повреждён. */
    static byte[] open(byte[] sealed) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG, sealed, MAGIC.length, IV));
            return c.doFinal(sealed, MAGIC.length + IV, sealed.length - MAGIC.length - IV);
        } catch (GeneralSecurityException | IOException | RuntimeException e) { return null; }
    }
}
