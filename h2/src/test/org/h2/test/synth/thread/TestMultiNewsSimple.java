/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth.thread;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The operation part of {@link TestMulti}.
 * Executes simple queries and updates in a table.
 */
public class TestMultiNewsSimple extends TestMultiThread {

    private static int newsCount = 10000;

    private Connection conn;

    TestMultiNewsSimple(TestMulti base) throws SQLException {
        super(base);
        conn = base.getConnection();
    }

    private static int getNewsCount() {
        return newsCount;
    }

    void first() throws SQLException {
        Connection conn = base.getConnection();
        conn.createStatement().execute("create table news(id identity, state int default 0, text varchar default '')");
        PreparedStatement prep = conn.prepareStatement("insert into news() values()");
        for (int i = 0; i < newsCount; i++) {
            prep.executeUpdate();
        }
        conn.createStatement().execute("update news set text = 'Text' || id");
        conn.close();
    }

    void begin() {
        // nothing to do
    }

    void end() throws SQLException {
        conn.close();
    }

    void operation() throws SQLException {
        if (random.nextInt(10) == 0) {
            conn.setAutoCommit(random.nextBoolean());
        } else if (random.nextInt(10) == 0) {
            if (random.nextBoolean()) {
                conn.commit();
            } else {
                // conn.rollback();
            }
        } else {
            if (random.nextBoolean()) {
                PreparedStatement prep = conn.prepareStatement("update news set state = ? where id = ?");
                prep.setInt(1, random.nextInt(getNewsCount()));
                prep.setInt(2, random.nextInt(10));
                prep.execute();
            } else {
                PreparedStatement prep = conn.prepareStatement("select * from news where id = ?");
                prep.setInt(1, random.nextInt(getNewsCount()));
                ResultSet rs = prep.executeQuery();
                if (!rs.next()) {
                    System.out.println("No row found");
                    // throw new AssertionError("No row found");
                }
                if (rs.next()) {
                    System.out.println("Multiple rows found");
                    // throw new AssertionError("Multiple rows found");
                }
            }
        }
    }

    void finalTest() {
        // nothing to do
    }

}
