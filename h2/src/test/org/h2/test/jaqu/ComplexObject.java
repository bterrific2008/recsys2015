/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.jaqu;

//## Java 1.5 begin ##
import static org.h2.jaqu.Define.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.sql.Time;
import java.sql.Timestamp;
import org.h2.jaqu.Table;
//## Java 1.5 end ##

/**
 * A table containing all possible data types.
 */
//## Java 1.5 begin ##
public class ComplexObject implements Table {
    public Integer id;
    public Long amount;
    public String name;
    public BigDecimal value;
    public Date birthday;
    public Time time;
    public Timestamp created;

    static ComplexObject build(Integer id, boolean isNull) {
        ComplexObject obj = new ComplexObject();
        obj.id = id;
        obj.amount = isNull ? null : Long.valueOf(1);
        obj.name = isNull ? null : "hello";
        obj.value = isNull ? null : new BigDecimal("1");
        obj.birthday = isNull ? null : java.sql.Date.valueOf("2001-01-01");
        obj.time = isNull ? null : Time.valueOf("10:20:30");
        obj.created = isNull ? null : Timestamp.valueOf("2002-02-02 02:02:02");
        return obj;
    }

    public void define() {
        primaryKey(id);
    }

    public static List<ComplexObject> getList() {
        return Arrays.asList(new ComplexObject[] { build(0, true), build(1, false) });
    }

}
//## Java 1.5 end ##
