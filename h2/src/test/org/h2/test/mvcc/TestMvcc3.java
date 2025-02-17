/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.mvcc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import org.h2.test.TestBase;

/**
 * Additional MVCC (multi version concurrency) test cases.
 */
public class TestMvcc3 extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws SQLException {
        testInsertUpdateRollback();
        testCreateTableAsSelect();
        testSequence();
        testDisableAutoCommit();
        testRollback();
        deleteDb("mvcc3");
    }

    private void testInsertUpdateRollback() throws SQLException {
        if (!config.mvcc) {
            return;
        }

        deleteDb("mvcc3");
        Connection c1 = getConnection("mvcc3");
        Statement s1 = c1.createStatement();
        Connection c2 = getConnection("mvcc3");
        Statement s2 = c2.createStatement();

        s1.execute("create table test(id int primary key, name varchar) as select 0, 'Hello'");
        c1.setAutoCommit(false);
        s1.executeUpdate("update test set name = 'World'");
        printRows("after update", s1, s2);
        Savepoint sp1 = c1.setSavepoint();
        s1.executeUpdate("delete from test");
        printRows("after delete", s1, s2);
        c1.rollback(sp1);
        printRows("after rollback delete", s1, s2);
        c1.rollback();
        printRows("after rollback all", s1, s2);

        ResultSet rs = s2.executeQuery("select * from test");
        assertTrue(rs.next());
        assertFalse(rs.next());
        c1.close();
        c2.close();
    }

    private void printRows(String s, Statement s1, Statement s2) throws SQLException {
        trace(s);
        ResultSet rs;
        rs = s1.executeQuery("select * from test");
        while (rs.next()) {
            trace("s1: " + rs.getString(2));
        }
        rs = s2.executeQuery("select * from test");
        while (rs.next()) {
            trace("s2: " + rs.getString(2));
        }
    }

    private void testCreateTableAsSelect() throws SQLException {
        if (!config.mvcc) {
            return;
        }
        deleteDb("mvcc3");
        Connection c1 = getConnection("mvcc3");
        Statement s1 = c1.createStatement();
        s1.execute("CREATE TABLE TEST AS SELECT X ID, 'Hello' NAME FROM SYSTEM_RANGE(1, 3)");
        Connection c2 = getConnection("mvcc3");
        Statement s2 = c2.createStatement();
        ResultSet rs = s2.executeQuery("SELECT NAME FROM TEST WHERE ID=1");
        rs.next();
        assertEquals("Hello", rs.getString(1));
        c1.close();
        c2.close();
    }

    private void testRollback() throws SQLException {
        if (!config.mvcc) {
            return;
        }

        deleteDb("mvcc3");
        Connection conn = getConnection("mvcc3");
        Statement stat = conn.createStatement();
        stat.executeUpdate("DROP TABLE IF EXISTS TEST");
        stat.executeUpdate("CREATE TABLE TEST (ID NUMBER(2) PRIMARY KEY, VAL VARCHAR(10))");
        stat.executeUpdate("INSERT INTO TEST (ID, VAL) VALUES (1, 'Value')");
        stat.executeUpdate("INSERT INTO TEST (ID, VAL) VALUES (2, 'Value')");
        if (!config.memory) {
            conn.close();
            conn = getConnection("mvcc3");
        }
        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        Connection conn2 = getConnection("mvcc3");
        conn2.setAutoCommit(false);
        conn2.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        conn.createStatement().executeUpdate("UPDATE TEST SET VAL='Updated' WHERE ID = 1");
        conn.rollback();

        ResultSet rs = conn2.createStatement().executeQuery("SELECT * FROM TEST");
        assertTrue(rs.next());
        assertEquals("Value", rs.getString(2));
        assertTrue(rs.next());
        assertEquals("Value", rs.getString(2));
        assertFalse(rs.next());

        conn.createStatement().executeUpdate("UPDATE TEST SET VAL='Updated' WHERE ID = 1");
        conn.commit();
        rs = conn2.createStatement().executeQuery("SELECT * FROM TEST ORDER BY ID");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertEquals("Updated", rs.getString(2));
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        assertEquals("Value", rs.getString(2));
        assertFalse(rs.next());

        conn.close();
        conn2.close();
    }

    private void testDisableAutoCommit() throws SQLException {
        if (!config.mvcc) {
            return;
        }
        deleteDb("mvcc3");
        Connection conn = getConnection("mvcc3");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY)");
        stat.execute("INSERT INTO TEST VALUES(0)");
        conn.setAutoCommit(false);
        stat.execute("INSERT INTO TEST VALUES(1)");
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST ORDER BY ID");
        rs.next();
        assertEquals(0, rs.getInt(1));
        rs.next();
        assertEquals(1, rs.getInt(1));
        conn.close();
    }

    private void testSequence() throws SQLException {
        if (config.memory) {
            return;
        }

        deleteDb("mvcc3");
        Connection conn;
        ResultSet rs;

        conn = getConnection("mvcc3");
        conn.createStatement().execute("create sequence abc");
        conn.close();

        conn = getConnection("mvcc3");
        rs = conn.createStatement().executeQuery("call abc.nextval");
        rs.next();
        assertEquals(1, rs.getInt(1));
        conn.close();

        conn = getConnection("mvcc3");
        rs = conn.createStatement().executeQuery("call abc.currval");
        rs.next();
        assertEquals(1, rs.getInt(1));
        conn.close();
    }
}




