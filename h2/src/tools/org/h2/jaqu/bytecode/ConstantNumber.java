/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jaqu.bytecode;

import org.h2.jaqu.Query;
import org.h2.jaqu.SQLStatement;

/**
 * A literal number.
 */
public class ConstantNumber implements Constant {

    private final String value;
    private final Type type;
    private final long longValue;

    private ConstantNumber(String value, long longValue, Type type) {
        this.value = value;
        this.longValue = longValue;
        this.type = type;
    }

    static ConstantNumber get(String v) {
        return new ConstantNumber(v, 0, Type.STRING);
    }

    static ConstantNumber get(int v) {
        return new ConstantNumber("" + v, v, Type.INT);
    }

    static ConstantNumber get(long v) {
        return new ConstantNumber("" + v, v, Type.LONG);
    }

    static ConstantNumber get(String s, long x, Type type) {
        return new ConstantNumber(s, x, type);
    }

    public int intValue() {
        return (int) longValue;
    }

    public String toString() {
        return value;
    }

    public <T> void appendSQL(SQLStatement stat, Query<T> query) {
        stat.appendSQL(toString());
    }

    public Constant.Type getType() {
        return type;
    }

}
