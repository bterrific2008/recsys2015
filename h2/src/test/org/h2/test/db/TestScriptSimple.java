/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.h2.test.TestBase;
import org.h2.util.ScriptReader;

/**
 * This test runs a simple SQL script file and compares the output with the
 * expected output.
 */
public class TestScriptSimple extends TestBase {

    private Connection conn;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        if (config.memory || config.big || config.networked) {
            return;
        }
        deleteDb("scriptSimple");
        reconnect();
        String inFile = "org/h2/test/testSimple.in.txt";
        InputStream is = getClass().getClassLoader().getResourceAsStream(inFile);
        LineNumberReader lineReader = new LineNumberReader(new InputStreamReader(is, "Cp1252"));
        ScriptReader reader = new ScriptReader(lineReader);
        while (true) {
            String sql = reader.readStatement();
            if (sql == null) {
                break;
            }
            sql = sql.trim();
            try {
                if ("@reconnect".equals(sql.toLowerCase())) {
                    reconnect();
                } else if (sql.length() == 0) {
                    // ignore
                } else if (sql.toLowerCase().startsWith("select")) {
                    ResultSet rs = conn.createStatement().executeQuery(sql);
                    while (rs.next()) {
                        String expected = reader.readStatement().trim();
                        String got = "> " + rs.getString(1);
                        assertEquals(expected, got);
                    }
                } else {
                    conn.createStatement().execute(sql);
                }
            } catch (SQLException e) {
                System.out.println(sql);
                throw e;
            }
        }
        is.close();
        conn.close();
        deleteDb("scriptSimple");
    }

    private void reconnect() throws SQLException {
        if (conn != null) {
            conn.close();
        }
        conn = getConnection("scriptSimple");
    }

}
