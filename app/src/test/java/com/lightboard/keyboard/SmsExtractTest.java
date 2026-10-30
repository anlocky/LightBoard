package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class SmsExtractTest {
    @Test public void plainCode() { assertEquals("123456", SmsReceiver.extract("Ваш код 123456")); }
    @Test public void splitCode() { assertEquals("123456", SmsReceiver.extract("Your code is 123 456")); }
    @Test public void amountsAreNotCodes() { assertNull(SmsReceiver.extract("Сумма 5000 руб списана")); }
}
