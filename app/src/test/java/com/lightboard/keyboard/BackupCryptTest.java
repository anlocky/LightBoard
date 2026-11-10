package com.lightboard.keyboard;

import static org.junit.Assert.*;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.junit.Test;

public class BackupCryptTest {
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }

    @Test public void pbkdf2MatchesPublishedVectors() throws Exception {
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", hex(BackupCrypt.pbkdf2("passwd".toCharArray(), "salt".getBytes(), 1)));
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a", hex(BackupCrypt.pbkdf2("password".toCharArray(), "salt".getBytes(), 4096)));
    }

    @Test public void roundTripAndRejections() throws Exception {
        byte[] plain = "{\"app\":\"LightBoard\"}".getBytes("UTF-8");
        byte[] enc = BackupCrypt.encrypt(plain, "pass123".toCharArray());
        assertTrue(BackupCrypt.isEncrypted(enc));
        assertFalse(BackupCrypt.isEncrypted(plain));
        assertTrue(Arrays.equals(plain, BackupCrypt.decrypt(enc, "pass123".toCharArray())));
        try { BackupCrypt.decrypt(enc, "other1".toCharArray()); fail(); } catch (GeneralSecurityException expected) { }
        byte[] head = enc.clone(); head[8] ^= 1;
        try { BackupCrypt.decrypt(head, "pass123".toCharArray()); fail(); } catch (GeneralSecurityException expected) { }
        byte[] body = enc.clone(); body[body.length - 1] ^= 1;
        try { BackupCrypt.decrypt(body, "pass123".toCharArray()); fail(); } catch (GeneralSecurityException expected) { }
    }

    private static void fail() { throw new AssertionError("expected an exception"); }
}
