/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.h2.constant.ErrorCode;
import org.h2.store.fs.FileSystem;
import org.h2.test.TestBase;
import org.h2.test.utils.FileSystemDebug;

/**
 * Tests that use the debug file system to simulate power failure.
 */
public class TestPowerOffFs extends TestBase {

    private FileSystemDebug fs;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        FileSystemDebug.register();
        fs = (FileSystemDebug) FileSystem.getInstance("debug:/");
        test(Integer.MAX_VALUE);
        System.out.println(Integer.MAX_VALUE - fs.getPowerOffCount());
        System.out.println("done");
        for (int i = 0;; i++) {
            boolean end = test(i);
            if (end) {
                break;
            }
        }
    }

    private boolean test(int x) throws SQLException {
        deleteDb("memFS:", null);
        fs.setPowerOffCount(x);
        String url = "jdbc:h2:debug:memFS:powerOffFs;FILE_LOCK=NO;TRACE_LEVEL_FILE=0;WRITE_DELAY=0;CACHE_SIZE=4096";
        url += ";page_store=true";
        Connection conn = null;
        Statement stat = null;
        try {
            conn = DriverManager.getConnection(url);
            stat = conn.createStatement();
            stat.execute("create table test(id int primary key, name varchar)");
            stat.execute("insert into test values(1, 'Hello')");
            stat.execute("create index idx_name on test(name)");
            stat.execute("insert into test values(2, 'World')");
            stat.execute("update test set name='Hallo' where id=1");
            stat.execute("delete from test where name=2");
            stat.execute("insert into test values(3, space(10000))");
            stat.execute("update test set name='Hallo' where id=3");
            stat.execute("drop table test");
            conn.close();
            conn = null;
            return fs.getPowerOffCount() > 0;
        } catch (SQLException e) {
            if (e.getErrorCode() == ErrorCode.TABLE_OR_VIEW_ALREADY_EXISTS_1) {
                throw e;
            }
            // ignore
        } finally {
            if (conn != null) {
                try {
                    if (stat != null) {
                        stat.execute("shutdown immediately");
                    }
                } catch (Exception e2) {
                    // ignore
                }
                try {
                    conn.close();
                } catch (Exception e2) {
                    // ignore
                }
            }
        }
        fs.setPowerOffCount(0);
        conn = DriverManager.getConnection(url);
        stat = conn.createStatement();
        stat.execute("script to 'memFS:test.sql'");
        conn.close();
        return false;
    }

}
