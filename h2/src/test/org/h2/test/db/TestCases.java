/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Random;
import org.h2.constant.ErrorCode;
import org.h2.test.TestBase;

/**
 * Various test cases.
 */
public class TestCases extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        testOrderByWithSubselect();
        testInsertDeleteRollback();
        testLargeRollback();
        testConstraintAlterTable();
        testJoinWithView();
        testLobDecrypt();
        testInvalidDatabaseName();
        testReuseSpace();
        testDeleteGroup();
        testDisconnect();
        testExecuteTrace();
        if (config.memory || config.logMode == 0) {
            return;
        }
        testDeleteAndDropTableWithLobs(true);
        testDeleteAndDropTableWithLobs(false);
        testEmptyBtreeIndex();
        testReservedKeywordReconnect();
        testSpecialSQL();
        testUpperCaseLowerCaseDatabase();
        testManualCommitSet();
        testSchemaIdentityReconnect();
        testAlterTableReconnect();
        testPersistentSettings();
        testInsertSelectUnion();
        testViewReconnect();
        testDefaultQueryReconnect();
        testBigString();
        testRenameReconnect();
        testAllSizes();
        testCreateDrop();
        testPolePos();
        testQuick();
        testMutableObjects();
        testSelectForUpdate();
        testDoubleRecovery();
        testConstraintReconnect();
        testCollation();
        deleteDb("cases");
    }

    private void testInsertDeleteRollback() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("set cache_size 1");
        stat.execute("SET MAX_MEMORY_ROWS " + Integer.MAX_VALUE);
        stat.execute("SET MAX_MEMORY_UNDO " + Integer.MAX_VALUE);
        stat.execute("SET MAX_OPERATION_MEMORY " + Integer.MAX_VALUE);
        stat.execute("create table test(id identity)");
        conn.setAutoCommit(false);
        stat.execute("insert into test select x from system_range(1, 11)");
        stat.execute("delete from test");
        conn.rollback();
        conn.close();
    }

    private void testLargeRollback() throws SQLException {
        Connection conn;
        Statement stat;

        deleteDb("cases");
        conn = getConnection("cases");
        stat = conn.createStatement();
        stat.execute("set max_operation_memory 1");
        stat.execute("create table test(id int)");
        stat.execute("insert into test values(1), (2)");
        stat.execute("create index idx on test(id)");
        conn.setAutoCommit(false);
        stat.execute("update test set id = id where id=2");
        stat.execute("update test set id = id");
        conn.rollback();
        conn.close();

        deleteDb("cases");
        conn = getConnection("cases");
        conn.createStatement().execute("set MAX_MEMORY_UNDO 1");
        conn.createStatement().execute("create table test(id number primary key)");
        conn.createStatement().execute("insert into test(id) select x from system_range(1, 2)");
        Connection conn2 = getConnection("cases");
        conn2.setAutoCommit(false);
        assertEquals(2, conn2.createStatement().executeUpdate("delete from test"));
        conn2.close();
        conn.close();

        deleteDb("cases");
        conn = getConnection("cases");
        conn.createStatement().execute("set MAX_MEMORY_UNDO 8");
        conn.createStatement().execute("create table test(id number primary key)");
        conn.setAutoCommit(false);
        conn.createStatement().execute("insert into test select x from system_range(1, 10)");
        conn.createStatement().execute("delete from test");
        conn.rollback();
        conn.close();
    }

    private void testConstraintAlterTable() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table parent (pid int)");
        stat.execute("create table child (cid int primary key, pid int)");
        stat.execute("alter table child add foreign key (pid) references parent(pid)");
        stat.execute("alter table child add column c2 int");
        stat.execute("alter table parent add column p2 varchar");
        conn.close();
    }

    private void testEmptyBtreeIndex() throws SQLException {
        deleteDb("cases");
        Connection conn;
        conn = getConnection("cases");
        conn.createStatement().execute("CREATE TABLE test(id int PRIMARY KEY);");
        conn.createStatement().execute("INSERT INTO test SELECT X FROM SYSTEM_RANGE(1, 77)");
        conn.createStatement().execute("DELETE from test");
        conn.close();
        conn = getConnection("cases");
        conn.createStatement().execute("INSERT INTO test (id) VALUES (1)");
        conn.close();
        conn = getConnection("cases");
        conn.createStatement().execute("DELETE from test");
        conn.createStatement().execute("drop table test");
        conn.close();
    }

    private void testJoinWithView() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        conn.createStatement().execute(
                "create table t(i identity, n varchar) as select 1, 'x'");
        PreparedStatement prep = conn.prepareStatement(
                "select 1 from dual " +
                "inner join(select n from t where i=?) a on a.n='x' " +
                "inner join(select n from t where i=?) b on b.n='x'");
        prep.setInt(1, 1);
        prep.setInt(2, 1);
        prep.execute();
        conn.close();
    }

    private void testLobDecrypt() throws SQLException {
        Connection conn = getConnection("cases");
        String key = "key";
        String value = "Hello World";
        PreparedStatement prep = conn.prepareStatement("CALL ENCRYPT('AES', RAWTOHEX(?), STRINGTOUTF8(?))");
        prep.setCharacterStream(1, new StringReader(key), -1);
        prep.setCharacterStream(2, new StringReader(value), -1);
        ResultSet rs = prep.executeQuery();
        rs.next();
        String encrypted = rs.getString(1);
        PreparedStatement prep2 = conn
                .prepareStatement("CALL TRIM(CHAR(0) FROM UTF8TOSTRING(DECRYPT('AES', RAWTOHEX(?), ?)))");
        prep2.setCharacterStream(1, new StringReader(key), -1);
        prep2.setCharacterStream(2, new StringReader(encrypted), -1);
        ResultSet rs2 = prep2.executeQuery();
        rs2.first();
        String decrypted = rs2.getString(1);
        prep2.close();
        assertEquals(value, decrypted);
        conn.close();
    }

    private void testReservedKeywordReconnect() throws SQLException {
        if (config.memory) {
            return;
        }
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table \"UNIQUE\"(\"UNIQUE\" int)");
        conn.close();
        conn = getConnection("cases");
        stat = conn.createStatement();
        stat.execute("select \"UNIQUE\" from \"UNIQUE\"");
        stat.execute("drop table \"UNIQUE\"");
        conn.close();
    }

    private void testInvalidDatabaseName() {
        if (config.memory) {
            return;
        }
        try {
            getConnection("cases/");
            fail();
        } catch (SQLException e) {
            assertEquals(ErrorCode.INVALID_DATABASE_NAME_1, e.getErrorCode());
        }
    }

    private void testReuseSpace() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        int tableCount = getSize(2, 5);
        for (int i = 0; i < tableCount; i++) {
            stat.execute("create table t" + i + "(data varchar)");
        }
        Random random = new Random(1);
        int len = getSize(50, 500);
        for (int i = 0; i < len; i++) {
            String table = "t" + random.nextInt(tableCount);
            String sql;
            if (random.nextBoolean()) {
                sql = "insert into " + table + " values(space(100000))";
            } else {
                sql = "delete from " + table;
            }
            stat.execute(sql);
            stat.execute("script to '" + baseDir + "/test.sql'");
        }
        conn.close();
    }

    private void testDeleteGroup() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("set max_memory_rows 2");
        stat.execute("create table test(id int primary key, x int)");
        stat.execute("insert into test values(0, 0), (1, 1), (2, 2)");
        stat.execute("delete from test where id not in (select min(x) from test group by id)");
        conn.close();
    }

    private void testSpecialSQL() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        try {
            stat.execute("create table address(id identity, name varchar check? instr(value, '@') > 1)");
        } catch (SQLException e) {
            assertKnownException(e);
        }
        stat.execute("SET AUTOCOMMIT OFF; \n//create sequence if not exists object_id;\n");
        stat.execute("SET AUTOCOMMIT OFF;\n//create sequence if not exists object_id;\n");
        stat.execute("SET AUTOCOMMIT OFF; //create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF;//create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF \n//create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF\n//create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF //create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF//create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF; \n///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF;\n///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF; ///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF;///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF \n///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF\n///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF ///create sequence if not exists object_id;");
        stat.execute("SET AUTOCOMMIT OFF///create sequence if not exists object_id;");
        conn.close();
    }

    private void testUpperCaseLowerCaseDatabase() throws SQLException {
        if (File.separatorChar != '\\' || config.googleAppEngine) {
            return;
        }
        deleteDb("cases");
        deleteDb("CaSeS");
        Connection conn, conn2;
        ResultSet rs;
        conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("CHECKPOINT");
        stat.execute("CREATE TABLE TEST(ID INT)");
        stat.execute("INSERT INTO TEST VALUES(1)");
        stat.execute("CHECKPOINT");

        conn2 = getConnection("CaSeS");
        rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        assertTrue(rs.next());
        conn2.close();

        conn.close();

        conn = getConnection("cases");
        rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        assertTrue(rs.next());
        conn.close();

        conn = getConnection("CaSeS");
        rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        assertTrue(rs.next());
        conn.close();

        deleteDb("cases");
        deleteDb("CaSeS");

    }

    private void testManualCommitSet() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Connection conn2 = getConnection("cases");
        conn.setAutoCommit(false);
        conn2.setAutoCommit(false);
        conn.createStatement().execute("SET MODE REGULAR");
        conn2.createStatement().execute("SET MODE REGULAR");
        conn.close();
        conn2.close();
    }

    private void testSchemaIdentityReconnect() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create schema s authorization sa");
        stat.execute("create table s.test(id identity)");
        conn.close();
        conn = getConnection("cases");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM S.TEST");
        while (rs.next()) {
            // ignore
        }
        conn.close();
    }

    private void testDisconnect() throws Exception {
        if (config.networked || config.codeCoverage) {
            return;
        }
        deleteDb("cases");
        Connection conn = getConnection("cases");
        final Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID IDENTITY)");
        for (int i = 0; i < 1000; i++) {
            stat.execute("INSERT INTO TEST() VALUES()");
        }
        final SQLException[] stopped = new SQLException[1];
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    long time = System.currentTimeMillis();
                    ResultSet rs = stat
                            .executeQuery("SELECT MAX(T.ID) FROM TEST T, TEST, TEST, TEST, TEST, TEST, TEST, TEST, TEST, TEST, TEST");
                    rs.next();
                    time = System.currentTimeMillis() - time;
                    TestBase.logError("query was too quick; result: " + rs.getInt(1) + " time:" + time, null);
                } catch (SQLException e) {
                    stopped[0] = e;
                    // ok
                }
            }
        });
        t.start();
        Thread.sleep(300);
        long time = System.currentTimeMillis();
        conn.close();
        t.join(5000);
        if (stopped[0] == null) {
            fail("query still running");
        } else {
            assertKnownException(stopped[0]);
        }
        time = System.currentTimeMillis() - time;
        if (time > 5000) {
            fail("closing took " + time);
        }
        deleteDb("cases");
    }

    private void testExecuteTrace() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT ? FROM DUAL {1: 'Hello'}");
        rs.next();
        assertEquals("Hello", rs.getString(1));
        assertFalse(rs.next());

        rs = stat.executeQuery("SELECT ? FROM DUAL UNION ALL SELECT ? FROM DUAL {1: 'Hello', 2:'World' }");
        rs.next();
        assertEquals("Hello", rs.getString(1));
        rs.next();
        assertEquals("World", rs.getString(1));
        assertFalse(rs.next());

        conn.close();
    }

    private void testAlterTableReconnect() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id identity);");
        stat.execute("insert into test values(1);");
        try {
            stat.execute("alter table test add column name varchar not null;");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
        conn = getConnection("cases");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        rs.next();
        assertEquals("1", rs.getString(1));
        assertFalse(rs.next());
        stat = conn.createStatement();
        stat.execute("drop table test");
        stat.execute("create table test(id identity)");
        stat.execute("insert into test values(1)");
        stat.execute("alter table test alter column id set default 'x'");
        conn.close();
        conn = getConnection("cases");
        stat = conn.createStatement();
        rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        rs.next();
        assertEquals("1", rs.getString(1));
        assertFalse(rs.next());
        stat.execute("drop table test");
        stat.execute("create table test(id identity)");
        stat.execute("insert into test values(1)");
        try {
            stat.execute("alter table test alter column id date");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
        conn = getConnection("cases");
        rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        rs.next();
        assertEquals("1", rs.getString(1));
        assertFalse(rs.next());
        conn.close();
    }

    private void testCollation() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("SET COLLATION ENGLISH STRENGTH PRIMARY");
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello'), (2, 'World'), (3, 'WORLD'), (4, 'HELLO')");
        stat.execute("create index idxname on test(name)");
        ResultSet rs;
        rs = stat.executeQuery("select name from test order by name");
        rs.next();
        assertEquals("Hello", rs.getString(1));
        rs.next();
        assertEquals("HELLO", rs.getString(1));
        rs.next();
        assertEquals("World", rs.getString(1));
        rs.next();
        assertEquals("WORLD", rs.getString(1));
        rs = stat.executeQuery("select name from test where name like 'He%'");
        rs.next();
        assertEquals("Hello", rs.getString(1));
        rs.next();
        assertEquals("HELLO", rs.getString(1));
        conn.close();
    }

    private void testPersistentSettings() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("SET COLLATION de_DE");
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        stat.execute("CREATE INDEX IDXNAME ON TEST(NAME)");
        // \u00f6 = oe
        stat.execute("INSERT INTO TEST VALUES(1, 'B\u00f6hlen'), (2, 'Bach'), (3, 'Bucher')");
        conn.close();
        conn = getConnection("cases");
        ResultSet rs = conn.createStatement().executeQuery("SELECT NAME FROM TEST ORDER BY NAME");
        rs.next();
        assertEquals("Bach", rs.getString(1));
        rs.next();
        assertEquals("B\u00f6hlen", rs.getString(1));
        rs.next();
        assertEquals("Bucher", rs.getString(1));
        conn.close();
    }

    private void testInsertSelectUnion() throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ORDER_ID INT PRIMARY KEY, ORDER_DATE DATETIME, USER_ID INT ,"
                + "DESCRIPTION VARCHAR, STATE VARCHAR, TRACKING_ID VARCHAR)");
        Timestamp orderDate = Timestamp.valueOf("2005-05-21 17:46:00");
        String sql = "insert into TEST (ORDER_ID,ORDER_DATE,USER_ID,DESCRIPTION,STATE,TRACKING_ID) "
                + "select cast(? as int),cast(? as date),cast(? as int),cast(? as varchar),cast(? as varchar),cast(? as varchar) union all select ?,?,?,?,?,?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, 5555);
        ps.setTimestamp(2, orderDate);
        ps.setInt(3, 2222);
        ps.setString(4, "test desc");
        ps.setString(5, "test_state");
        ps.setString(6, "testid");
        ps.setInt(7, 5556);
        ps.setTimestamp(8, orderDate);
        ps.setInt(9, 2222);
        ps.setString(10, "test desc");
        ps.setString(11, "test_state");
        ps.setString(12, "testid");
        assertEquals(2, ps.executeUpdate());
        ps.close();
        conn.close();
    }

    private void testViewReconnect() throws SQLException {
        trace("testViewReconnect");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int)");
        stat.execute("create view abc as select * from test");
        stat.execute("drop table test");
        conn.close();
        conn = getConnection("cases");
        stat = conn.createStatement();
        try {
            stat.execute("select * from abc");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn.close();
    }

    private void testDefaultQueryReconnect() throws SQLException {
        trace("testDefaultQueryReconnect");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table parent(id int)");
        stat.execute("insert into parent values(1)");
        stat.execute("create table test(id int default (select max(id) from parent), name varchar)");

        conn.close();
        conn = getConnection("cases");
        stat = conn.createStatement();
        conn.setAutoCommit(false);
        stat.execute("insert into parent values(2)");
        stat.execute("insert into test(name) values('test')");
        ResultSet rs = stat.executeQuery("select * from test");
        rs.next();
        assertEquals(2, rs.getInt(1));
        assertFalse(rs.next());
        conn.close();
    }

    private void testBigString() throws SQLException {
        trace("testBigString");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("DROP TABLE IF EXISTS TEST");
        stat.execute("CREATE TABLE TEST(ID INT, TEXT VARCHAR, TEXT_C CLOB)");
        PreparedStatement prep = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?, ?)");
        int len = getSize(1000, 66000);
        char[] buff = new char[len];

        // The UCS code values 0xd800-0xdfff (UTF-16 surrogates)
        // as well as 0xfffe and 0xffff (UCS non-characters)
        // should not appear in conforming UTF-8 streams.
        // (String.getBytes("UTF-8") only returns 1 byte for 0xd800-0xdfff)

        Random random = new Random();
        random.setSeed(1);
        for (int i = 0; i < len; i++) {
            char c;
            do {
                c = (char) random.nextInt();
            } while (c >= 0xd800 && c <= 0xdfff);
            buff[i] = c;
        }
        String big = new String(buff);
        prep.setInt(1, 1);
        prep.setString(2, big);
        prep.setString(3, big);
        prep.execute();
        prep.setInt(1, 2);
        prep.setCharacterStream(2, new StringReader(big), 0);
        prep.setCharacterStream(3, new StringReader(big), 0);
        prep.execute();
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST ORDER BY ID");
        rs.next();
        assertEquals(1, rs.getInt(1));
        assertEquals(big, rs.getString(2));
        assertEquals(big, readString(rs.getCharacterStream(2)));
        assertEquals(big, rs.getString(3));
        assertEquals(big, readString(rs.getCharacterStream(3)));
        rs.next();
        assertEquals(2, rs.getInt(1));
        assertEquals(big, rs.getString(2));
        assertEquals(big, readString(rs.getCharacterStream(2)));
        assertEquals(big, rs.getString(3));
        assertEquals(big, readString(rs.getCharacterStream(3)));
        rs.next();
        assertFalse(rs.next());
        conn.close();
    }

    private void testConstraintReconnect() throws SQLException {
        trace("testConstraintReconnect");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("drop table if exists parent");
        stat.execute("drop table if exists child");
        stat.execute("create table parent(id int)");
        stat.execute("create table child(c_id int, p_id int, foreign key(p_id) references parent(id))");
        stat.execute("insert into parent values(1), (2)");
        stat.execute("insert into child values(1, 1)");
        stat.execute("insert into child values(2, 2)");
        stat.execute("insert into child values(3, 2)");
        stat.execute("delete from child");
        conn.close();
        conn = getConnection("cases");
        conn.close();
    }

    private void testDoubleRecovery() throws SQLException {
        if (config.networked || config.googleAppEngine) {
            return;
        }
        trace("testDoubleRecovery");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("SET WRITE_DELAY 0");
        stat.execute("DROP TABLE IF EXISTS TEST");
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.setAutoCommit(false);
        stat.execute("INSERT INTO TEST VALUES(2, 'World')");
        crash(conn);

        conn = getConnection("cases");
        stat = conn.createStatement();
        stat.execute("SET WRITE_DELAY 0");
        stat.execute("INSERT INTO TEST VALUES(3, 'Break')");
        crash(conn);

        conn = getConnection("cases");
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST ORDER BY ID");
        rs.next();
        assertEquals(1, rs.getInt(1));
        assertEquals("Hello", rs.getString(2));
        rs.next();
        assertEquals(3, rs.getInt(1));
        assertEquals("Break", rs.getString(2));
        conn.close();
    }

    private void testRenameReconnect() throws SQLException {
        trace("testRenameReconnect");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        conn.createStatement().execute("CREATE TABLE TEST_SEQ(ID INT IDENTITY, NAME VARCHAR(255))");
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY)");
        conn.createStatement().execute("ALTER TABLE TEST RENAME TO TEST2");
        conn.createStatement().execute("CREATE TABLE TEST_B(ID INT PRIMARY KEY, NAME VARCHAR, UNIQUE(NAME))");
        conn.close();
        conn = getConnection("cases");
        conn.createStatement().execute("INSERT INTO TEST_SEQ(NAME) VALUES('Hi')");
        ResultSet rs = conn.createStatement().executeQuery("CALL IDENTITY()");
        rs.next();
        assertEquals(1, rs.getInt(1));
        conn.createStatement().execute("SELECT * FROM TEST2");
        conn.createStatement().execute("SELECT * FROM TEST_B");
        conn.createStatement().execute("ALTER TABLE TEST_B RENAME TO TEST_B2");
        conn.close();
        conn = getConnection("cases");
        conn.createStatement().execute("SELECT * FROM TEST_B2");
        conn.createStatement().execute("INSERT INTO TEST_SEQ(NAME) VALUES('World')");
        rs = conn.createStatement().executeQuery("CALL IDENTITY()");
        rs.next();
        assertEquals(2, rs.getInt(1));
        conn.close();
    }

    private void testAllSizes() throws SQLException {
        trace("testAllSizes");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(A INT, B INT, C INT, DATA VARCHAR)");
        int increment = getSize(100, 1);
        for (int i = 1; i < 500; i += increment) {
            StringBuilder buff = new StringBuilder();
            buff.append("CREATE TABLE TEST");
            for (int j = 0; j < i; j++) {
                buff.append('a');
            }
            buff.append("(ID INT)");
            String sql = buff.toString();
            stat.execute(sql);
            stat.execute("INSERT INTO TEST VALUES(" + i + ", 0, 0, '" + sql + "')");
        }
        conn.close();
        conn = getConnection("cases");
        stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST");
        while (rs.next()) {
            int id = rs.getInt(1);
            String s = rs.getString("DATA");
            if (!s.endsWith(")")) {
                fail("id=" + id);
            }
        }
        conn.close();
    }

    private void testSelectForUpdate() throws SQLException {
        trace("testSelectForUpdate");
        deleteDb("cases");
        Connection conn1 = getConnection("cases");
        Statement stat1 = conn1.createStatement();
        stat1.execute("CREATE TABLE TEST(ID INT)");
        stat1.execute("INSERT INTO TEST VALUES(1)");
        conn1.setAutoCommit(false);
        stat1.execute("SELECT * FROM TEST FOR UPDATE");
        Connection conn2 = getConnection("cases");
        Statement stat2 = conn2.createStatement();
        try {
            stat2.execute("UPDATE TEST SET ID=2");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
        conn1.commit();
        stat2.execute("UPDATE TEST SET ID=2");
        conn1.close();
        conn2.close();
    }

    private void testMutableObjects() throws SQLException {
        trace("testMutableObjects");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT, D DATE, T TIME, TS TIMESTAMP)");
        stat.execute("INSERT INTO TEST VALUES(1, '2001-01-01', '20:00:00', '2002-02-02 22:22:22.2')");
        stat.execute("INSERT INTO TEST VALUES(1, '2001-01-01', '20:00:00', '2002-02-02 22:22:22.2')");
        ResultSet rs = stat.executeQuery("SELECT * FROM TEST");
        rs.next();
        Date d1 = rs.getDate("D");
        Time t1 = rs.getTime("T");
        Timestamp ts1 = rs.getTimestamp("TS");
        rs.next();
        Date d2 = rs.getDate("D");
        Time t2 = rs.getTime("T");
        Timestamp ts2 = rs.getTimestamp("TS");
        assertTrue(ts1 != ts2);
        assertTrue(d1 != d2);
        assertTrue(t1 != t2);
        assertTrue(t2 != rs.getObject("T"));
        assertTrue(d2 != rs.getObject("D"));
        assertTrue(ts2 != rs.getObject("TS"));
        assertFalse(rs.next());
        conn.close();
    }

    private void testCreateDrop() throws SQLException {
        trace("testCreateDrop");
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table employee(id int, firstName VARCHAR(50), salary decimal(10, 2), "
                + "superior_id int, CONSTRAINT PK_employee PRIMARY KEY (id), "
                + "CONSTRAINT FK_superior FOREIGN KEY (superior_id) REFERENCES employee(ID))");
        stat.execute("DROP TABLE employee");
        conn.close();
        conn = getConnection("cases");
        conn.close();
    }

    private void testPolePos() throws SQLException {
        trace("testPolePos");
        // poleposition-0.20

        Connection c0 = getConnection("cases");
        c0.createStatement().executeUpdate("SET AUTOCOMMIT FALSE");
        c0.createStatement().executeUpdate(
                "create table australia (ID  INTEGER NOT NULL, " +
                "Name VARCHAR(100), firstName VARCHAR(100), " +
                "Points INTEGER, LicenseID INTEGER, PRIMARY KEY(ID))");
        c0.createStatement().executeUpdate("COMMIT");
        c0.close();

        c0 = getConnection("cases");
        c0.createStatement().executeUpdate("SET AUTOCOMMIT FALSE");
        PreparedStatement p15 = c0.prepareStatement("insert into australia"
                + "(id, Name, firstName, Points, LicenseID) values (?, ?, ?, ?, ?)");
        int len = getSize(1, 1000);
        for (int i = 0; i < len; i++) {
            p15.setInt(1, i);
            p15.setString(2, "Pilot_" + i);
            p15.setString(3, "Herkules");
            p15.setInt(4, i);
            p15.setInt(5, i);
            p15.executeUpdate();
        }
        c0.createStatement().executeUpdate("COMMIT");
        c0.close();

        // c0=getConnection("cases");
        // c0.createStatement().executeUpdate("SET AUTOCOMMIT FALSE");
        // c0.createStatement().executeQuery("select * from australia");
        // c0.createStatement().executeQuery("select * from australia");
        // c0.close();

        // c0=getConnection("cases");
        // c0.createStatement().executeUpdate("SET AUTOCOMMIT FALSE");
        // c0.createStatement().executeUpdate("COMMIT");
        // c0.createStatement().executeUpdate("delete from australia");
        // c0.createStatement().executeUpdate("COMMIT");
        // c0.close();

        c0 = getConnection("cases");
        c0.createStatement().executeUpdate("SET AUTOCOMMIT FALSE");
        c0.createStatement().executeUpdate("drop table australia");
        c0.createStatement().executeUpdate(
                "create table australia (ID  INTEGER NOT NULL, Name VARCHAR(100), "
                        + "firstName VARCHAR(100), Points INTEGER, LicenseID INTEGER, PRIMARY KEY(ID))");
        c0.createStatement().executeUpdate("COMMIT");
        c0.close();

        c0 = getConnection("cases");
        c0.createStatement().executeUpdate("SET AUTOCOMMIT FALSE");
        PreparedStatement p65 = c0.prepareStatement("insert into australia"
                + "(id, Name, FirstName, Points, LicenseID) values (?, ?, ?, ?, ?)");
        len = getSize(1, 1000);
        for (int i = 0; i < len; i++) {
            p65.setInt(1, i);
            p65.setString(2, "Pilot_" + i);
            p65.setString(3, "Herkules");
            p65.setInt(4, i);
            p65.setInt(5, i);
            p65.executeUpdate();
        }
        c0.createStatement().executeUpdate("COMMIT");
        c0.createStatement().executeUpdate("COMMIT");
        c0.createStatement().executeUpdate("COMMIT");
        c0.close();

        c0 = getConnection("cases");
        c0.close();
    }

    private void testQuick() throws SQLException {
        trace("testQuick");
        deleteDb("cases");

        Connection c0 = getConnection("cases");
        c0.createStatement().executeUpdate("create table test (ID  int PRIMARY KEY)");
        c0.createStatement().executeUpdate("insert into test values(1)");
        c0.createStatement().executeUpdate("drop table test");
        c0.createStatement().executeUpdate("create table test (ID  int PRIMARY KEY)");
        c0.close();

        c0 = getConnection("cases");
        c0.createStatement().executeUpdate("insert into test values(1)");
        c0.close();

        c0 = getConnection("cases");
        c0.close();
    }

    private void testOrderByWithSubselect() throws SQLException {
        deleteDb("cases");

        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("create table master(id number primary key, name varchar2(30));");
        stat.execute("create table detail(id number references master(id), location varchar2(30));");

        stat.execute("Insert into master values(1,'a'), (2,'b'), (3,'c');");
        stat.execute("Insert into detail values(1,'a'), (2,'b'), (3,'c');");

        ResultSet rs = stat.executeQuery(
                "select master.id, master.name " +
                "from master " +
                "where master.id in (select detail.id from detail) " +
                "order by master.id");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));

        conn.close();
    }

    private void testDeleteAndDropTableWithLobs(boolean useDrop) throws SQLException {
        deleteDb("cases");
        Connection conn = getConnection("cases");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(id int, content BLOB)");
        stat.execute("set MAX_LENGTH_INPLACE_LOB 1");

        PreparedStatement prepared = conn.prepareStatement("INSERT INTO TEST VALUES(?, ?)");
        byte[] blobContent = "BLOB_CONTENT".getBytes();
        prepared.setInt(1, 1);
        prepared.setBytes(2, blobContent);
        prepared.execute();

        if (useDrop) {
            stat.execute("DROP TABLE TEST");
        } else {
            stat.execute("DELETE FROM TEST");
        }

        conn.close();

        File[] files = new File(baseDir + "/cases.lobs.db").listFiles();
        if (files != null && files.length > 0) {
            fail("Lob file was not deleted");
        }
    }

}