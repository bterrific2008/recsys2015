/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;

import org.h2.jdbc.JdbcConnection;
import org.h2.message.TraceSystem;
import org.h2.store.FileLock;
import org.h2.store.fs.FileSystem;
import org.h2.tools.DeleteDbFiles;

/**
 * The base class for all tests.
 */
public abstract class TestBase {

    /**
     * The base directory to write test databases.
     */
    protected static String baseDir = getTestDir("");

    /**
     * The temporary directory.
     */
    protected static final String TEMP_DIR = "data/temp";

    /**
     * An id used to create unique file names.
     */
    protected static int uniqueId;

    private static final String BASE_TEST_DIR = "data";

    /**
     * The last time something was printed.
     */
    private static long lastPrint;

    /**
     * The test configuration.
     */
    public TestAll config;

    /**
     * The time when the test was started.
     */
    protected long start;

    private LinkedList<byte[]> memory = new LinkedList<byte[]>();

    /**
     * Get the test directory for this test.
     *
     * @param name the directory name suffix
     * @return the test directory
     */
    public static String getTestDir(String name) {
        return BASE_TEST_DIR + "/test" + name;
    }

    /**
     * Start the TCP server if enabled in the configuration.
     */
    protected void startServerIfRequired() throws SQLException {
        config.beforeTest();
    }

    /**
     * Initialize the test configuration using the default settings.
     *
     * @return itself
     */
    public TestBase init() throws Exception {
        return init(new TestAll());
    }

    /**
     * Initialize the test configuration.
     *
     * @param conf the configuration
     * @return itself
     */
    public TestBase init(TestAll conf) throws Exception {
        baseDir = getTestDir("");
        System.setProperty("java.io.tmpdir", TEMP_DIR);
        this.config = conf;
        return this;
    }

    /**
     * Run a test case using the given seed value.
     *
     * @param seed the random seed value
     */
    public void testCase(int seed) throws Exception {
        // do nothing
    }

    /**
     * This method is initializes the test, runs the test by calling the test()
     * method, and prints status information. It also catches exceptions so that
     * the tests can continue.
     *
     * @param conf the test configuration
     */
    public void runTest(TestAll conf) {
        try {
            init(conf);
            start = System.currentTimeMillis();
            test();
            println("");
        } catch (Throwable e) {
            println("FAIL " + e.toString());
            logError("FAIL " + e.toString(), e);
            if (config.stopOnError) {
                throw new AssertionError("ERROR");
            }
            if (e instanceof OutOfMemoryError) {
                throw (OutOfMemoryError) e;
            }
        } finally {
            try {
                FileSystem.getInstance("memFS:").deleteRecursive("memFS:", false);
                FileSystem.getInstance("memLZF:").deleteRecursive("memLZF:", false);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Open a database connection in admin mode. The default user name and
     * password is used.
     *
     * @param name the database name
     * @return the connection
     */
    public Connection getConnection(String name) throws SQLException {
        return getConnectionInternal(getURL(name, true), getUser(), getPassword());
    }

    /**
     * Open a database connection.
     *
     * @param name the database name
     * @param user the user name to use
     * @param password the password to use
     * @return the connection
     */
    protected Connection getConnection(String name, String user, String password) throws SQLException {
        return getConnectionInternal(getURL(name, false), user, password);
    }

    /**
     * Get the password to use to login for the given user password. The file
     * password is added if required.
     *
     * @param userPassword the password of this user
     * @return the login password
     */
    protected String getPassword(String userPassword) {
        return config == null || config.cipher == null ? userPassword : "filePassword " + userPassword;
    }

    /**
     * Get the login password. This is usually the user password. If file
     * encryption is used it is combined with the file password.
     *
     * @return the login password
     */
    protected String getPassword() {
        return getPassword("123");
    }

    private void deleteIndexFiles(String name) {
        if (name.indexOf(";") > 0) {
            name = name.substring(0, name.indexOf(';'));
        }
        name += ".index.db";
        if (new File(name).canWrite()) {
            new File(name).delete();
        }
    }

    /**
     * Get the database URL for the given database name using the current
     * configuration options.
     *
     * @param name the database name
     * @param admin true if the current user is an admin
     * @return the database URL
     */
    protected String getURL(String name, boolean admin) {
        String url;
        if (name.startsWith("jdbc:")) {
            return name;
        }
        if (config.memory) {
            name = "mem:" + name;
        } else {
            if (!name.startsWith("memFS:") && !name.startsWith(baseDir + "/")) {
                name = baseDir + "/" + name;
            }
            if (config.deleteIndex) {
                deleteIndexFiles(name);
            }
        }
        if (config.networked) {
            if (config.ssl) {
                url = "ssl://localhost:9192/" + name;
            } else {
                url = "tcp://localhost:9192/" + name;
            }
        } else if (config.googleAppEngine) {
            url = "gae://" + name + ";FILE_LOCK=NO;AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE";
        } else {
            url = name;
        }
        if (!config.memory) {
            if (admin) {
                url += ";LOG=" + config.logMode;
            }
            if (config.smallLog && admin) {
                url += ";MAX_LOG_SIZE=1";
            }
        }
        if (config.traceSystemOut) {
            url += ";TRACE_LEVEL_SYSTEM_OUT=2";
        }
        if (config.traceLevelFile > 0 && admin) {
            if (url.indexOf("TRACE_LEVEL_FILE=") < 0) {
                url += ";TRACE_LEVEL_FILE=" + config.traceLevelFile;
            }
        }
        if (config.throttle > 0) {
            url += ";THROTTLE=" + config.throttle;
        }
        if (url.indexOf("LOCK_TIMEOUT=") < 0) {
            url += ";LOCK_TIMEOUT=50";
        }
        if (config.diskUndo && admin) {
            url += ";MAX_MEMORY_UNDO=3";
        }
        if (config.big && admin) {
            // force operations to disk
            url += ";MAX_OPERATION_MEMORY=1";
        }
        if (config.mvcc && url.indexOf("MVCC=") < 0) {
            url += ";MVCC=TRUE";
        }
        if (config.diskResult && admin) {
            url += ";MAX_MEMORY_ROWS=100;CACHE_SIZE=0";
        }
        if (config.cipher != null) {
            url += ";CIPHER=" + config.cipher;
        }
        if (!config.pageStore && url.indexOf("PAGE_STORE=") < 0) {
            url += ";PAGE_STORE=FALSE";
        }
        return "jdbc:h2:" + url;
    }

    private Connection getConnectionInternal(String url, String user, String password) throws SQLException {
        org.h2.Driver.load();
        // url += ";DEFAULT_TABLE_TYPE=1";
        // Class.forName("org.hsqldb.jdbcDriver");
        // return DriverManager.getConnection("jdbc:hsqldb:" + name, "sa", "");
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Get the small or the big value depending on the configuration.
     *
     * @param small the value to return if the current test mode is 'small'
     * @param big the value to return if the current test mode is 'big'
     * @return small or big, depending on the configuration
     */
    protected int getSize(int small, int big) {
        return config.endless ? Integer.MAX_VALUE : config.big ? big : small;
    }

    protected String getUser() {
        return "sa";
    }

    /**
     * Write a message to system out if trace is enabled.
     *
     * @param x the value to write
     */
    protected void trace(int x) {
        trace("" + x);
    }

    /**
     * Write a message to system out if trace is enabled.
     *
     * @param s the message to write
     */
    public void trace(String s) {
        if (config.traceTest) {
            lastPrint = 0;
            println(s);
        }
    }

    /**
     * Print how much memory is currently used.
     */
    protected void traceMemory() {
        if (config.traceTest) {
            trace("mem=" + getMemoryUsed());
        }
    }

    /**
     * Print the currently used memory, the message and the given time in
     * milliseconds.
     *
     * @param s the message
     * @param time the time in millis
     */
    public void printTimeMemory(String s, long time) {
        if (config.big) {
            println(getMemoryUsed() + " MB: " + s + " ms: " + time);
        }
    }

    /**
     * Get the number of megabytes heap memory in use.
     *
     * @return the used megabytes
     */
    public static int getMemoryUsed() {
        Runtime rt = Runtime.getRuntime();
        long memory = Long.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            rt.gc();
            long memNow = rt.totalMemory() - rt.freeMemory();
            if (memNow >= memory) {
                break;
            }
            memory = memNow;
        }
        int mb = (int) (memory / 1024 / 1024);
        return mb;
    }

    /**
     * Called if the test reached a point that was not expected.
     *
     * @throws AssertionError always throws an AssertionError
     */
    protected void fail() {
        fail("Unexpected success");
    }

    /**
     * Called if the test reached a point that was not expected.
     *
     * @param string the error message
     * @throws AssertionError always throws an AssertionError
     */
    protected void fail(String string) {
        lastPrint = 0;
        println(string);
        throw new AssertionError(string);
    }

    /**
     * Log an error message.
     *
     * @param s the message
     * @param e the exception
     */
    public static void logError(String s, Throwable e) {
        if (e == null) {
            e = new Exception(s);
        }
        System.out.flush();
        System.err.println("ERROR: " + s + " " + e.toString() + " ------------------------------");
        e.printStackTrace();
        try {
            TraceSystem ts = new TraceSystem(null, false);
            FileLock lock = new FileLock(ts, "error.lock", 1000);
            lock.lock(FileLock.LOCK_FILE);
            FileWriter fw = new FileWriter("error.txt", true);
            PrintWriter pw = new PrintWriter(fw);
            e.printStackTrace(pw);
            pw.close();
            fw.close();
            lock.unlock();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.err.flush();
    }

    /**
     * Print a message to system out.
     *
     * @param s the message
     */
    public void println(String s) {
        long now = System.currentTimeMillis();
        if (now > lastPrint + 1000) {
            lastPrint = now;
            long time = now - start;
            printlnWithTime(time, getClass().getName() + " " + s);
        }
    }

    /**
     * Print a message, prepended with the specified time in milliseconds.
     *
     * @param millis the time in milliseconds
     * @param s the message
     */
    static void printlnWithTime(long millis, String s) {
        System.out.println(formatTime(millis) + " " + s);
    }

    /**
     * Print the current time and a message to system out.
     *
     * @param s the message
     */
    protected void printTime(String s) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        println(dateFormat.format(new java.util.Date()) + " " + s);
    }

    /**
     * Format the time in the format hh:mm:ss.1234 where 1234 is milliseconds.
     *
     * @param millis the time in milliseconds
     * @return the formatted time
     */
    static String formatTime(long millis) {
        String s = new java.sql.Time(java.sql.Time.valueOf("0:0:0").getTime() + millis).toString()
        + "." + ("" + (1000 + (millis % 1000))).substring(1);
        if (s.startsWith("00:")) {
            s = s.substring(3);
        }
        return s;
    }

    /**
     * Delete all database files for this database.
     *
     * @param name the database name
     */
    protected void deleteDb(String name) throws SQLException {
        deleteDb(baseDir, name);
    }

    /**
     * Delete all database files for a database.
     *
     * @param dir the directory where the database files are located
     * @param name the database name
     */
    protected void deleteDb(String dir, String name) throws SQLException {
        DeleteDbFiles.execute(dir, name, true);
        // ArrayList<String> list;
        // list = FileLister.getDatabaseFiles(baseDir, name, true);
        // if (list.size() >  0) {
        //    System.out.println("Not deleted: " + list);
        // }
    }

    /**
     * This method will be called by the test framework.
     *
     * @throws Exception if an exception in the test occurs
     */
    public abstract void test() throws Exception;

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param message the message to print in case of error
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    public void assertEquals(String message, int expected, int actual) {
        if (expected != actual) {
            fail("Expected: " + expected + " actual: " + actual + " message: " + message);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    public void assertEquals(int expected, int actual) {
        if (expected != actual) {
            fail("Expected: " + expected + " actual: " + actual);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    public void assertEquals(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                fail("[" + i + "]: expected: " + (int) expected[i] + " actual: " + (int) actual[i]);
            }
        }
    }

    /**
     * Check if two readers are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEqualReaders(Reader expected, Reader actual, int len) throws IOException {
        for (int i = 0; len < 0 || i < len; i++) {
            int ce = expected.read();
            int ca = actual.read();
            assertEquals(ce, ca);
            if (ce == -1) {
                break;
            }
        }
        expected.close();
        actual.close();
    }

    /**
     * Check if two streams are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEqualStreams(InputStream expected, InputStream actual, int len) throws IOException {
        // this doesn't actually read anything - just tests reading 0 bytes
        actual.read(new byte[0]);
        expected.read(new byte[0]);
        actual.read(new byte[10], 3, 0);
        expected.read(new byte[10], 0, 0);

        for (int i = 0; len < 0 || i < len; i++) {
            int ca = actual.read();
            actual.read(new byte[0]);
            int ce = expected.read();
            assertEquals(ce, ca);
            if (ca == -1) {
                break;
            }
        }
        actual.read(new byte[10], 3, 0);
        expected.read(new byte[10], 0, 0);
        actual.read(new byte[0]);
        expected.read(new byte[0]);
        actual.close();
        expected.close();
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEquals(String expected, String actual) {
        if (expected == null && actual == null) {
            return;
        } else if (expected == null || actual == null) {
            fail("Expected: " + expected + " Actual: " + actual);
        }
        if (!expected.equals(actual)) {
            for (int i = 0; i < expected.length(); i++) {
                String s = expected.substring(0, i);
                if (!actual.startsWith(s)) {
                    expected = expected.substring(0, i) + "<*>" + expected.substring(i);
                    break;
                }
            }
            int al = expected.length();
            int bl = actual.length();
            if (al > 4000) {
                expected = expected.substring(0, 4000);
            }
            if (bl > 4000) {
                actual = actual.substring(0, 4000);
            }
            fail("Expected: " + expected + " (" + al + ") actual: " + actual + " (" + bl + ")");
        }
    }

    /**
     * Check if the first value is larger or equal than the second value, and if
     * not throw an exception.
     *
     * @param a the first value
     * @param b the second value (must be smaller than the first value)
     * @throws AssertionError if the first value is smaller
     */
    protected void assertSmaller(long a, long b) {
        if (a >= b) {
            fail("a: " + a + " is not smaller than b: " + b);
        }
    }

    /**
     * Check that a result contains the given substring.
     *
     * @param result the result value
     * @param contains the term that should appear in the result
     * @throws AssertionError if the term was not found
     */
    protected void assertContains(String result, String contains) {
        if (result.indexOf(contains) < 0) {
            fail(result + " does not contain: " + contains);
        }
    }

    /**
     * Check that a text starts with the expected characters..
     *
     * @param text the text
     * @param  expectedStart the expected prefix
     * @throws AssertionError if the text does not start with the expected characters
     */
    protected void assertStartsWith(String text, String expectedStart) {
        if (!text.startsWith(expectedStart)) {
            fail(text + " does not start with: " + expectedStart);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEquals(long expected, long actual) {
        if (expected != actual) {
            fail("Expected: " + expected + " actual: " + actual);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEquals(double expected, double actual) {
        if (expected != actual) {
            if (Double.isNaN(expected) && Double.isNaN(actual)) {
                // if both a NaN, then there is no error
            } else {
                fail("Expected: " + expected + " actual: " + actual);
            }
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEquals(float expected, float actual) {
        if (expected != actual) {
            if (Float.isNaN(expected) && Float.isNaN(actual)) {
                // if both a NaN, then there is no error
            } else {
                fail("Expected: " + expected + " actual: " + actual);
            }
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEquals(boolean expected, boolean actual) {
        if (expected != actual) {
            fail("Boolean expected: " + expected + " actual: " + actual);
        }
    }

    /**
     * Check that the passed boolean is true.
     *
     * @param condition the condition
     * @throws AssertionError if the condition is false
     */
    public void assertTrue(boolean condition) {
        assertTrue("Expected: true got: false", condition);
    }

    /**
     * Check that the passed boolean is true.
     *
     * @param message the message to print if the condition is false
     * @param condition the condition
     * @throws AssertionError if the condition is false
     */
    protected void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }

    /**
     * Check that the passed boolean is false.
     *
     * @param value the condition
     * @throws AssertionError if the condition is true
     */
    protected void assertFalse(boolean value) {
        assertFalse("Expected: false got: true", value);
    }

    /**
     * Check that the passed boolean is false.
     *
     * @param message the message to print if the condition is false
     * @param value the condition
     * @throws AssertionError if the condition is true
     */
    protected void assertFalse(String message, boolean value) {
        if (value) {
            fail(message);
        }
    }

    /**
     * Check that the result set row count matches.
     *
     * @param rs the result set
     * @param expected the number of expected rows
     * @throws AssertionError if a different number of rows have been found
     */
    protected void assertResultRowCount(ResultSet rs, int expected) throws SQLException {
        int i = 0;
        while (rs.next()) {
            i++;
        }
        assertEquals(expected, i);
    }

    /**
     * Check that the result set of a query is exactly this value.
     *
     * @param stat the statement
     * @param sql the SQL statement to execute
     * @param expected the expected result value
     * @throws AssertionError if a different result value was returned
     */
    protected void assertSingleValue(Statement stat, String sql, int expected) throws SQLException {
        ResultSet rs = stat.executeQuery(sql);
        assertTrue(rs.next());
        assertEquals(expected, rs.getInt(1));
        assertFalse(rs.next());
    }

    /**
     * Check that the result set of a query is exactly this value.
     *
     * @param expected the expected result value
     * @param stat the statement
     * @param sql the SQL statement to execute
     * @throws AssertionError if a different result value was returned
     */
    protected void assertResult(String expected, Statement stat, String sql) throws SQLException {
        ResultSet rs = stat.executeQuery(sql);
        if (rs.next()) {
            String actual = rs.getString(1);
            assertEquals(expected, actual);
        } else {
            assertEquals(null, expected);
        }
    }

    /**
     * Check if the result set meta data is correct.
     *
     * @param rs the result set
     * @param columnCount the expected column count
     * @param labels the expected column labels
     * @param datatypes the expected data types
     * @param precision the expected precisions
     * @param scale the expected scales
     */
    protected void assertResultSetMeta(ResultSet rs, int columnCount, String[] labels, int[] datatypes, int[] precision,
            int[] scale) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cc = meta.getColumnCount();
        if (cc != columnCount) {
            fail("result set contains " + cc + " columns not " + columnCount);
        }
        for (int i = 0; i < columnCount; i++) {
            if (labels != null) {
                String l = meta.getColumnLabel(i + 1);
                if (!labels[i].equals(l)) {
                    fail("column label " + i + " is " + l + " not " + labels[i]);
                }
            }
            if (datatypes != null) {
                int t = meta.getColumnType(i + 1);
                if (datatypes[i] != t) {
                    fail("column datatype " + i + " is " + t + " not " + datatypes[i] + " (prec="
                            + meta.getPrecision(i + 1) + " scale=" + meta.getScale(i + 1) + ")");
                }
                String typeName = meta.getColumnTypeName(i + 1);
                String className = meta.getColumnClassName(i + 1);
                switch (t) {
                case Types.INTEGER:
                    assertEquals("INTEGER", typeName);
                    assertEquals("java.lang.Integer", className);
                    break;
                case Types.VARCHAR:
                    assertEquals("VARCHAR", typeName);
                    assertEquals("java.lang.String", className);
                    break;
                case Types.SMALLINT:
                    assertEquals("SMALLINT", typeName);
                    assertEquals("java.lang.Short", className);
                    break;
                case Types.TIMESTAMP:
                    assertEquals("TIMESTAMP", typeName);
                    assertEquals("java.sql.Timestamp", className);
                    break;
                case Types.DECIMAL:
                    assertEquals("DECIMAL", typeName);
                    assertEquals("java.math.BigDecimal", className);
                    break;
                default:
                }
            }
            if (precision != null) {
                int p = meta.getPrecision(i + 1);
                if (precision[i] != p) {
                    fail("column precision " + i + " is " + p + " not " + precision[i]);
                }
            }
            if (scale != null) {
                int s = meta.getScale(i + 1);
                if (scale[i] != s) {
                    fail("column scale " + i + " is " + s + " not " + scale[i]);
                }
            }

        }
    }

    /**
     * Check if a result set contains the expected data.
     * The sort order is significant
     *
     * @param rs the result set
     * @param data the expected data
     * @throws AssertionError if there is a mismatch
     */
    protected void assertResultSetOrdered(ResultSet rs, String[][] data) throws SQLException {
        assertResultSet(true, rs, data);
    }

    /**
     * Check if a result set contains the expected data.
     * The sort order is not significant
     *
     * @param rs the result set
     * @param data the expected data
     * @throws AssertionError if there is a mismatch
     */
//    void assertResultSetUnordered(ResultSet rs, String[][] data) {
//        assertResultSet(false, rs, data);
//    }

    /**
     * Check if a result set contains the expected data.
     *
     * @param ordered if the sort order is significant
     * @param rs the result set
     * @param data the expected data
     * @throws AssertionError if there is a mismatch
     */
    private void assertResultSet(boolean ordered, ResultSet rs, String[][] data) throws SQLException {
        int len = rs.getMetaData().getColumnCount();
        int rows = data.length;
        if (rows == 0) {
            // special case: no rows
            if (rs.next()) {
                fail("testResultSet expected rowCount:" + rows + " got:0");
            }
        }
        int len2 = data[0].length;
        if (len < len2) {
            fail("testResultSet expected columnCount:" + len2 + " got:" + len);
        }
        for (int i = 0; i < rows; i++) {
            if (!rs.next()) {
                fail("testResultSet expected rowCount:" + rows + " got:" + i);
            }
            String[] row = getData(rs, len);
            if (ordered) {
                String[] good = data[i];
                if (!testRow(good, row, good.length)) {
                    fail("testResultSet row not equal, got:\n" + formatRow(row) + "\n" + formatRow(good));
                }
            } else {
                boolean found = false;
                for (int j = 0; j < rows; j++) {
                    String[] good = data[i];
                    if (testRow(good, row, good.length)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    fail("testResultSet no match for row:" + formatRow(row));
                }
            }
        }
        if (rs.next()) {
            String[] row = getData(rs, len);
            fail("testResultSet expected rowcount:" + rows + " got:>=" + (rows + 1) + " data:" + formatRow(row));
        }
    }

    private boolean testRow(String[] a, String[] b, int len) {
        for (int i = 0; i < len; i++) {
            String sa = a[i];
            String sb = b[i];
            if (sa == null || sb == null) {
                if (sa != sb) {
                    return false;
                }
            } else {
                if (!sa.equals(sb)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String[] getData(ResultSet rs, int len) throws SQLException {
        String[] data = new String[len];
        for (int i = 0; i < len; i++) {
            data[i] = rs.getString(i + 1);
            // just check if it works
            rs.getObject(i + 1);
        }
        return data;
    }

    private String formatRow(String[] row) {
        String sb = "";
        for (String r : row) {
            sb += "{" + r + "}";
        }
        return "{" + sb + "}";
    }

    /**
     * Simulate a database crash. This method will also close the database
     * files, but the files are in a state as the power was switched off. It
     * doesn't throw an exception.
     *
     * @param conn the database connection
     */
    protected void crash(Connection conn) throws SQLException {
        ((JdbcConnection) conn).setPowerOffCount(1);
        try {
            conn.createStatement().execute("SET WRITE_DELAY 0");
            conn.createStatement().execute("CREATE TABLE TEST_A(ID INT)");
            fail("should be crashed already");
        } catch (SQLException e) {
            // expected
        }
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
    }

    /**
     * Read a string from the reader. This method reads until end of file.
     *
     * @param reader the reader
     * @return the string read
     */
    protected String readString(Reader reader) {
        if (reader == null) {
            return null;
        }
        StringBuilder buffer = new StringBuilder();
        try {
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    break;
                }
                buffer.append((char) c);
            }
            return buffer.toString();
        } catch (Exception e) {
            assertTrue(false);
            return null;
        }
    }

    /**
     * Check that a given exception is not an unexpected 'general error'
     * exception.
     *
     * @param e the error
     */
    protected void assertKnownException(SQLException e) {
        assertKnownException("", e);
    }

    /**
     * Check that a given exception is not an unexpected 'general error'
     * exception.
     *
     * @param message the message
     * @param e the exception
     */
    protected void assertKnownException(String message, SQLException e) {
        if (e != null && e.getSQLState().startsWith("HY000")) {
            TestBase.logError("Unexpected General error " + message, e);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     *
     * @param expected the expected value
     * @param actual the actual value
     * @throws AssertionError if the values are not equal
     */
    protected void assertEquals(Integer expected, Integer actual) {
        if (expected == null || actual == null) {
            assertTrue(expected == actual);
        } else {
            assertEquals(expected.intValue(), actual.intValue());
        }
    }

    /**
     * Check if two databases contain the same met data.
     *
     * @param stat1 the connection to the first database
     * @param stat2 the connection to the second database
     * @throws AssertionError if the databases don't match
     */
    protected void assertEqualDatabases(Statement stat1, Statement stat2) throws SQLException {
        ResultSet rs1 = stat1.executeQuery("SCRIPT NOPASSWORDS");
        ResultSet rs2 = stat2.executeQuery("SCRIPT NOPASSWORDS");
        ArrayList<String> list1 = new ArrayList<String>();
        ArrayList<String> list2 = new ArrayList<String>();
        while (rs1.next()) {
            String s1 = rs1.getString(1);
            list1.add(s1);
            if (!rs2.next()) {
                fail("expected: " + s1);
            }
            String s2 = rs2.getString(1);
            list2.add(s2);
        }
        for (String s : list1) {
            if (!list2.remove(s)) {
                fail("only found in first: " + s);
            }
        }
        assertEquals(0, list2.size());
        assertFalse(rs2.next());
    }

    /**
     * Create a new object of the calling class.
     *
     * @return the new test
     */
    public static TestBase createCaller() {
        return createCaller(new Exception().getStackTrace()[1].getClassName());
    }

    /**
     * Create a new object of the given class.
     *
     * @param className the class name
     * @return the new test
     */
    public static TestBase createCaller(String className) {
        org.h2.Driver.load();
        try {
            return (TestBase) Class.forName(className).newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Can not create object " + className, e);
        }
    }

    /**
     * Get the classpath list used to execute java -cp ...
     *
     * @return the classpath list
     */
    protected String getClassPath() {
        return "bin" + File.pathSeparator + "temp" + File.pathSeparator + ".";
    }

    /**
     * Get the page store property to use when starting new processes.
     *
     * @return the property
     */
    public String getPageStoreProperty() {
        return "-Dh2.pageStore=" + System.getProperty("h2.pageStore");
    }

    /**
     * Use up almost all memory.
     *
     * @param remainingKB the number of kilobytes that are not referenced
     */
    protected void eatMemory(int remainingKB) {
        byte[] reserve = new byte[remainingKB * 1024];
        int max = 128 * 1024 * 1024;
        int div = 2;
        while (true) {
            long free = Runtime.getRuntime().freeMemory();
            long freeTry = free / div;
            int eat = (int) Math.min(max, freeTry);
            try {
                byte[] block = new byte[eat];
                memory.add(block);
            } catch (OutOfMemoryError e) {
                if (eat < 32) {
                    break;
                }
                if (eat == max) {
                    max /= 2;
                    if (max < 128) {
                        break;
                    }
                }
                if (eat == freeTry) {
                    div += 1;
                } else {
                    div = 2;
                }
            }
        }
        // silly code - makes sure there are no warnings
        reserve[0] = reserve[1];
        // actually it is anyway garbage collected
        reserve = null;
    }

    /**
     * Remove the hard reference to the memory.
     */
    protected void freeMemory() {
        memory.clear();
    }

}
