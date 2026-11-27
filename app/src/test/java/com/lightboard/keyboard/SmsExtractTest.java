package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class SmsExtractTest {
    @Test public void plainCode() { assertEquals("123456", SmsReceiver.extract("Ваш код 123456")); }
    @Test public void splitCode() { assertEquals("123456", SmsReceiver.extract("Your code is 123 456")); }
    @Test public void amountsAreNotCodes() { assertNull(SmsReceiver.extract("Сумма 5000 руб списана")); }
    @Test public void codeAfterAmount() { assertEquals("1234", SmsReceiver.extract("Сумма 5000 руб, код 1234")); }
    @Test public void codeBeforeAmount() { assertEquals("1234", SmsReceiver.extract("Код: 1234. Сумма 5000 руб")); }
    @Test public void currencyIsSkipped() { assertEquals("123456", SmsReceiver.extract("Confirm payment of 5000 RUB, code 123456")); }
    @Test public void dollarIsSkipped() { assertEquals("4821", SmsReceiver.extract("Your verification code: 4821 ($1200 pending)")); }
}
