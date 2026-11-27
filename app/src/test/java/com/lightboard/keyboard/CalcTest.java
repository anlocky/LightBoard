package com.lightboard.keyboard;

import static org.junit.Assert.*;
import org.junit.Test;

public class CalcTest {
    @Test public void basic() { assertEquals("7", Calc.solve("1+2*3")); }
    @Test public void unaryMinusIsWeakerThanPower() { assertEquals("-4", Calc.solve("-2^2")); }
    @Test public void powerWithNegativeExponent() { assertEquals("0.5", Calc.solve("2^-1")); }
    @Test public void rightAssociativePower() { assertEquals("512", Calc.solve("2^3^2")); }
    @Test public void parentheses() { assertEquals("4", Calc.solve("(-2)^2")); }
    @Test public void plainNumberIsNotAnExpression() { assertNull(Calc.solve("123")); }
}
