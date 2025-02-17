/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Random;

import org.h2.constant.SysProperties;
import org.h2.test.TestBase;
import org.h2.util.New;
import org.h2.value.Value;
import org.h2.value.ValueString;

/**
 * Tests numeric overflow on various data types.
 * Other than in Java, overflow is detected and an exception is thrown.
 */
public class TestOverflow extends TestBase {

    private ArrayList<Value> values;
    private int type;
    private BigInteger min, max;
    private boolean successExpected;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws SQLException {
        test(Value.BYTE, Byte.MIN_VALUE, Byte.MAX_VALUE);
        test(Value.INT, Integer.MIN_VALUE, Integer.MAX_VALUE);
        test(Value.LONG, Long.MIN_VALUE, Long.MAX_VALUE);
        test(Value.SHORT, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    private void test(int type, long min, long max) throws SQLException {
        values = New.arrayList();
        this.type = type;
        this.min = new BigInteger("" + min);
        this.max = new BigInteger("" + max);
        add(0);
        add(min);
        add(max);
        add(max - 1);
        add(min + 1);
        add(1);
        add(-1);
        Random random = new Random(1);
        for (int i = 0; i < 40; i++) {
            if (max > Integer.MAX_VALUE) {
                add(random.nextLong());
            } else {
                add((random.nextBoolean() ? 1 : -1) * random.nextInt((int) max));
            }
        }
        for (Value va : values) {
            for (Value vb : values) {
                testValues(va, vb);
            }
        }
    }

    private void checkIfExpected(String a, String b) {
        if (successExpected) {
            assertEquals(a, b);
        }
    }

    private void onSuccess() {
        if (!successExpected && SysProperties.OVERFLOW_EXCEPTIONS) {
            fail();
        }
    }

    private void onError() {
        if (successExpected) {
            fail();
        }
    }

    private void testValues(Value va, Value vb) {
        BigInteger a = new BigInteger(va.getString());
        BigInteger b = new BigInteger(vb.getString());
        successExpected = inRange(a.negate());
        try {
            checkIfExpected(va.negate().getString(), a.negate().toString());
            onSuccess();
        } catch (SQLException e) {
            onError();
        }
        successExpected = inRange(a.add(b));
        try {
            checkIfExpected(va.add(vb).getString(), a.add(b).toString());
            onSuccess();
        } catch (SQLException e) {
            onError();
        }
        successExpected = inRange(a.subtract(b));
        try {
            checkIfExpected(va.subtract(vb).getString(), a.subtract(b).toString());
            onSuccess();
        } catch (SQLException e) {
            onError();
        }
        successExpected = inRange(a.multiply(b));
        try {
            checkIfExpected(va.multiply(vb).getString(), a.multiply(b).toString());
            onSuccess();
        } catch (SQLException e) {
            onError();
        }
    }

    private boolean inRange(BigInteger v) {
        return v.compareTo(min) >= 0 && v.compareTo(max) <= 0;
    }

    private void add(long l) throws SQLException {
        values.add(ValueString.get("" + l).convertTo(type));
    }

}
