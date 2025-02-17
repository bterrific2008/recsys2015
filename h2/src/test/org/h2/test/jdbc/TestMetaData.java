/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.h2.constant.SysProperties;
import org.h2.engine.Constants;
import org.h2.test.TestBase;
import org.h2.value.DataType;

/**
 * Test for the DatabaseMetaData implementation.
 */
public class TestMetaData extends TestBase {

    private Connection conn;
    private DatabaseMetaData meta;
    private Statement stat;
    private String catalog = "METADATA";

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws SQLException {
        deleteDb("metaData");

        testTempTable();

        conn = getConnection("metaData");

        testColumnLobMeta();
        testColumnMetaData();
        testColumnPrecision();
        testColumnDefault();
        testCrossReferences();
        testProcedureColumns();

        stat = conn.createStatement();
        meta = conn.getMetaData();
        testStatic();
        // TODO test remaining meta data

        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("CREATE INDEX IDXNAME ON TEST(NAME)");

        ResultSet rs;

        rs = meta.getCatalogs();
        rs.next();
        assertEquals(catalog, rs.getString(1));
        assertFalse(rs.next());

        rs = meta.getSchemas();
        rs.next();
        assertEquals("INFORMATION_SCHEMA", rs.getString("TABLE_SCHEM"));
        rs.next();
        assertEquals("PUBLIC", rs.getString("TABLE_SCHEM"));
        assertFalse(rs.next());

        rs = meta.getTableTypes();
        rs.next();
        assertEquals("SYSTEM TABLE", rs.getString("TABLE_TYPE"));
        rs.next();
        assertEquals("TABLE", rs.getString("TABLE_TYPE"));
        rs.next();
        assertEquals("TABLE LINK", rs.getString("TABLE_TYPE"));
        rs.next();
        assertEquals("VIEW", rs.getString("TABLE_TYPE"));
        assertFalse(rs.next());

        rs = meta.getTables(null, Constants.SCHEMA_MAIN, null, new String[] { "TABLE" });
        assertTrue(rs.getStatement() == null);
        rs.next();
        assertEquals("TEST", rs.getString("TABLE_NAME"));
        assertFalse(rs.next());

        rs = meta.getTables(null, "INFORMATION_SCHEMA", null, new String[] { "TABLE", "SYSTEM TABLE" });
        rs.next();
        assertEquals("CATALOGS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("COLLATIONS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("COLUMNS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("COLUMN_PRIVILEGES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("CONSTANTS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("CONSTRAINTS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("CROSS_REFERENCES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("DOMAINS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("FUNCTION_ALIASES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("FUNCTION_COLUMNS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("HELP", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("INDEXES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("IN_DOUBT", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("LOCKS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("RIGHTS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("ROLES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("SCHEMATA", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("SEQUENCES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("SESSIONS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("SESSION_STATE", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("SETTINGS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("TABLES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("TABLE_PRIVILEGES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("TABLE_TYPES", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("TRIGGERS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("TYPE_INFO", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("USERS", rs.getString("TABLE_NAME"));
        rs.next();
        assertEquals("VIEWS", rs.getString("TABLE_NAME"));
        assertFalse(rs.next());

        rs = meta.getColumns(null, null, "TEST", null);
        rs.next();
        assertEquals("ID", rs.getString("COLUMN_NAME"));
        rs.next();
        assertEquals("NAME", rs.getString("COLUMN_NAME"));
        assertFalse(rs.next());

        rs = meta.getPrimaryKeys(null, null, "TEST");
        rs.next();
        assertEquals("ID", rs.getString("COLUMN_NAME"));
        assertFalse(rs.next());

        rs = meta.getBestRowIdentifier(null, null, "TEST", DatabaseMetaData.bestRowSession, false);
        rs.next();
        assertEquals("ID", rs.getString("COLUMN_NAME"));
        assertFalse(rs.next());

        rs = meta.getIndexInfo(null, null, "TEST", false, false);
        rs.next();
        String index = rs.getString("INDEX_NAME");
        assertTrue(index.startsWith("PRIMARY_KEY"));
        assertEquals("ID", rs.getString("COLUMN_NAME"));
        rs.next();
        assertEquals("IDXNAME", rs.getString("INDEX_NAME"));
        assertEquals("NAME", rs.getString("COLUMN_NAME"));
        assertFalse(rs.next());

        rs = meta.getIndexInfo(null, null, "TEST", true, false);
        rs.next();
        index = rs.getString("INDEX_NAME");
        assertTrue(index.startsWith("PRIMARY_KEY"));
        assertEquals("ID", rs.getString("COLUMN_NAME"));
        assertFalse(rs.next());

        rs = meta.getVersionColumns(null, null, "TEST");
        assertFalse(rs.next());

        stat.execute("DROP TABLE TEST");

        rs = stat.executeQuery("SELECT * FROM INFORMATION_SCHEMA.SETTINGS");
        while (rs.next()) {
            String name = rs.getString("NAME");
            String value = rs.getString("VALUE");
            trace(name + "=" + value);
        }

        test(conn);

        // meta.getTablePrivileges()

        // meta.getAttributes()
        // meta.getColumnPrivileges()
        // meta.getSuperTables()
        // meta.getSuperTypes()
        // meta.getTypeInfo()
        // meta.getUDTs()

        conn.close();

        deleteDb("metaData");

    }

    private void testColumnLobMeta() throws SQLException {
        Statement stat = conn.createStatement();
        stat.executeUpdate("CREATE TABLE t (blob BLOB, clob CLOB)");
        stat.execute("INSERT INTO t VALUES('', '')");
        ResultSet rs = stat.executeQuery("SELECT blob,clob FROM t");
        ResultSetMetaData meta = rs.getMetaData();
        if (SysProperties.RETURN_LOB_OBJECTS) {
            assertEquals("java.sql.Blob", meta.getColumnClassName(1));
            assertEquals("java.sql.Clob", meta.getColumnClassName(2));
        } else {
            assertEquals("java.io.InputStream", meta.getColumnClassName(1));
            assertEquals("java.io.Reader", meta.getColumnClassName(2));
        }
        rs.next();
        if (SysProperties.RETURN_LOB_OBJECTS) {
            assertTrue(rs.getObject(1) instanceof java.sql.Blob);
            assertTrue(rs.getObject(2) instanceof java.sql.Clob);
        } else {
            assertEquals("java.io.ByteArrayInputStream", rs.getObject(1).getClass().getName());
            assertEquals("java.io.BufferedReader", rs.getObject(2).getClass().getName());
        }
        stat.executeUpdate("DROP TABLE t");
    }

    private void testColumnMetaData() throws SQLException {
        String statement = "select substring('Hello',0,1)";
        ResultSet rs = conn.prepareStatement(statement).executeQuery();
        rs.next();
        int type = rs.getMetaData().getColumnType(1);
        assertEquals(Types.VARCHAR, type);
        rs = conn.createStatement().executeQuery("SELECT COUNT(*) C FROM DUAL");
        assertEquals("C", rs.getMetaData().getColumnName(1));

        Statement stat = conn.createStatement();
        stat.execute("create table a(x array)");
        stat.execute("insert into a values((1, 2))");
        rs = stat.executeQuery("SELECT x[1] FROM a");
        ResultSetMetaData meta = rs.getMetaData();
        assertEquals(Types.VARCHAR, meta.getColumnType(1));
        rs.next();
        // assertEquals(String.class.getName(), rs.getObject(1).getClass().getName());
        stat.execute("drop table a");
    }

    private void testColumnPrecision() throws SQLException {
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE ONE(X NUMBER(12,2), Y FLOAT)");
        stat.execute("CREATE TABLE TWO AS SELECT * FROM ONE");
        ResultSet rs;
        ResultSetMetaData meta;
        rs = stat.executeQuery("SELECT * FROM ONE");
        meta = rs.getMetaData();
        assertEquals(12, meta.getPrecision(1));
        assertEquals(17, meta.getPrecision(2));
        assertEquals(Types.DECIMAL, meta.getColumnType(1));
        assertEquals(Types.DOUBLE, meta.getColumnType(2));
        rs = stat.executeQuery("SELECT * FROM TWO");
        meta = rs.getMetaData();
        assertEquals(12, meta.getPrecision(1));
        assertEquals(17, meta.getPrecision(2));
        assertEquals(Types.DECIMAL, meta.getColumnType(1));
        assertEquals(Types.DOUBLE, meta.getColumnType(2));
        stat.execute("DROP TABLE ONE, TWO");
    }

    private void testColumnDefault() throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs;
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(A INT, B INT DEFAULT NULL)");
        rs = meta.getColumns(null, null, "TEST", null);
        rs.next();
        assertEquals("A", rs.getString("COLUMN_NAME"));
        assertEquals(null, rs.getString("COLUMN_DEF"));
        rs.next();
        assertEquals("B", rs.getString("COLUMN_NAME"));
        assertEquals("NULL", rs.getString("COLUMN_DEF"));
        assertFalse(rs.next());
        stat.execute("DROP TABLE TEST");
    }

    private void testProcedureColumns() throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs;
        Statement stat = conn.createStatement();
        stat.execute("CREATE ALIAS PROP FOR \"java.lang.System.getProperty(java.lang.String)\"");
        stat.execute("CREATE ALIAS EXIT FOR \"java.lang.System.exit\"");
        rs = meta.getProcedures(null, null, "EX%");
        assertResultSetMeta(rs, 9, new String[] { "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME",
                "NUM_INPUT_PARAMS", "NUM_OUTPUT_PARAMS", "NUM_RESULT_SETS", "REMARKS", "PROCEDURE_TYPE",
                "SPECIFIC_NAME"
                }, new int[] {
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER,
                Types.VARCHAR, Types.SMALLINT, Types.VARCHAR }, null, null);
        assertResultSetOrdered(rs, new String[][] { { catalog, Constants.SCHEMA_MAIN, "EXIT", "1", "0", "0", "",
                "" + DatabaseMetaData.procedureNoResult } });
        rs = meta.getProcedureColumns(null, null, null, null);
        assertResultSetMeta(rs, 20,
                new String[] { "PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME", "COLUMN_NAME", "COLUMN_TYPE",
                        "DATA_TYPE", "TYPE_NAME", "PRECISION", "LENGTH", "SCALE", "RADIX", "NULLABLE", "REMARKS",
                        "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION",
                        "IS_NULLABLE", "SPECIFIC_NAME" },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.INTEGER,
                        Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR,
                        Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.VARCHAR, Types.VARCHAR}, null, null);
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "EXIT", "P1", "" + DatabaseMetaData.procedureColumnIn,
                        "" + Types.INTEGER, "INTEGER", "10", "10", "0", "10", "" + DatabaseMetaData.procedureNoNulls },
                { catalog, Constants.SCHEMA_MAIN, "PROP", "P1", "" + DatabaseMetaData.procedureColumnIn,
                        "" + Types.VARCHAR, "VARCHAR", "" + Integer.MAX_VALUE, "" + Integer.MAX_VALUE, "0", "10",
                        "" + DatabaseMetaData.procedureNullable }, });
        stat.execute("DROP ALIAS EXIT");
        stat.execute("DROP ALIAS PROP");
    }

    private void testCrossReferences() throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rs;
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE PARENT(A INT, B INT, PRIMARY KEY(A, B))");
        stat
                .execute("CREATE TABLE CHILD(ID INT PRIMARY KEY, PA INT, PB INT, CONSTRAINT AB FOREIGN KEY(PA, PB) REFERENCES PARENT(A, B))");
        rs = meta.getCrossReference(null, "PUBLIC", "PARENT", null, "PUBLIC", "CHILD");
        checkCrossRef(rs);
        rs = meta.getImportedKeys(null, "PUBLIC", "CHILD");
        checkCrossRef(rs);
        rs = meta.getExportedKeys(null, "PUBLIC", "PARENT");
        checkCrossRef(rs);
        stat.execute("DROP TABLE PARENT");
        stat.execute("DROP TABLE CHILD");
    }

    private void checkCrossRef(ResultSet rs) throws SQLException {
        assertResultSetMeta(rs, 14, new String[] { "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
                "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE",
                "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY" }, new int[] { Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT }, null,
                null);
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "PARENT", "A", catalog, Constants.SCHEMA_MAIN, "CHILD", "PA", "1",
                        "" + DatabaseMetaData.importedKeyRestrict, "" + DatabaseMetaData.importedKeyRestrict, "AB",
                        null, "" + DatabaseMetaData.importedKeyNotDeferrable },
                { catalog, Constants.SCHEMA_MAIN, "PARENT", "B", catalog, Constants.SCHEMA_MAIN, "CHILD", "PB", "2",
                        "" + DatabaseMetaData.importedKeyRestrict, "" + DatabaseMetaData.importedKeyRestrict, "AB",
                        null, "" + DatabaseMetaData.importedKeyNotDeferrable } });
    }

    private void testTempTable() throws SQLException {
        Connection conn = getConnection("metaData");
        Statement stat = conn.createStatement();
        stat.execute("DROP TABLE IF EXISTS TEST_TEMP");
        stat.execute("CREATE TEMP TABLE TEST_TEMP(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        stat.execute("CREATE INDEX IDX_NAME ON TEST_TEMP(NAME)");
        stat.execute("ALTER TABLE TEST_TEMP ADD FOREIGN KEY(ID) REFERENCES(ID)");
        conn.close();

        conn = getConnection("metaData");
        stat = conn.createStatement();
        stat.execute("CREATE TEMP TABLE TEST_TEMP(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        ResultSet rs = stat.executeQuery("SELECT STORAGE_TYPE FROM "
                + "INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='TEST_TEMP'");
        rs.next();
        assertEquals("GLOBAL TEMPORARY", rs.getString("STORAGE_TYPE"));
        stat.execute("DROP TABLE IF EXISTS TEST_TEMP");
        conn.close();
    }

    private void testStatic() throws SQLException {
        Driver dr = org.h2.Driver.load();

        assertEquals(dr.getMajorVersion(), meta.getDriverMajorVersion());
        assertEquals(dr.getMinorVersion(), meta.getDriverMinorVersion());
        assertTrue(dr.jdbcCompliant());

        assertEquals(0, dr.getPropertyInfo(null, null).length);
        assertTrue(dr.connect("jdbc:test:false", null) == null);

        assertTrue(meta.getNumericFunctions().length() > 0);
        assertTrue(meta.getStringFunctions().length() > 0);
        assertTrue(meta.getSystemFunctions().length() > 0);
        assertTrue(meta.getTimeDateFunctions().length() > 0);

        assertTrue(meta.allProceduresAreCallable());
        assertTrue(meta.allTablesAreSelectable());
        assertTrue(meta.dataDefinitionCausesTransactionCommit());
        assertFalse(meta.dataDefinitionIgnoredInTransactions());
        assertFalse(meta.deletesAreDetected(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.deletesAreDetected(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.deletesAreDetected(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.doesMaxRowSizeIncludeBlobs());
        assertEquals(".", meta.getCatalogSeparator());
        assertEquals("catalog", meta.getCatalogTerm());
        assertTrue(meta.getConnection() == conn);
        if (config.jdk14) {
            String versionStart = meta.getDatabaseMajorVersion() + "." + meta.getDatabaseMinorVersion();
            assertTrue(meta.getDatabaseProductVersion().startsWith(versionStart));
            assertEquals(meta.getDatabaseMajorVersion(), meta.getDriverMajorVersion());
            assertEquals(meta.getDatabaseMinorVersion(), meta.getDriverMinorVersion());
            assertEquals(3, meta.getJDBCMajorVersion());
            assertEquals(0, meta.getJDBCMinorVersion());
        }
        assertEquals("H2", meta.getDatabaseProductName());
        assertEquals(Connection.TRANSACTION_READ_COMMITTED, meta.getDefaultTransactionIsolation());
        assertEquals("H2 JDBC Driver", meta.getDriverName());

        String versionStart = meta.getDriverMajorVersion() + "." + meta.getDriverMinorVersion();
        assertTrue(meta.getDriverVersion().startsWith(versionStart));
        assertEquals("", meta.getExtraNameCharacters());
        assertEquals("\"", meta.getIdentifierQuoteString());
        assertEquals(0, meta.getMaxBinaryLiteralLength());
        assertEquals(0, meta.getMaxCatalogNameLength());
        assertEquals(0, meta.getMaxCharLiteralLength());
        assertEquals(0, meta.getMaxColumnNameLength());
        assertEquals(0, meta.getMaxColumnsInGroupBy());
        assertEquals(0, meta.getMaxColumnsInIndex());
        assertEquals(0, meta.getMaxColumnsInOrderBy());
        assertEquals(0, meta.getMaxColumnsInSelect());
        assertEquals(0, meta.getMaxColumnsInTable());
        assertEquals(0, meta.getMaxConnections());
        assertEquals(0, meta.getMaxCursorNameLength());
        assertEquals(0, meta.getMaxIndexLength());
        assertEquals(0, meta.getMaxProcedureNameLength());
        assertEquals(0, meta.getMaxRowSize());
        assertEquals(0, meta.getMaxSchemaNameLength());
        assertEquals(0, meta.getMaxStatementLength());
        assertEquals(0, meta.getMaxStatements());
        assertEquals(0, meta.getMaxTableNameLength());
        assertEquals(0, meta.getMaxTablesInSelect());
        assertEquals(0, meta.getMaxUserNameLength());
        assertEquals("procedure", meta.getProcedureTerm());
        if (config.jdk14) {
            assertEquals(ResultSet.CLOSE_CURSORS_AT_COMMIT, meta.getResultSetHoldability());
            assertEquals(DatabaseMetaData.sqlStateSQL99, meta.getSQLStateType());
            assertFalse(meta.locatorsUpdateCopy());
        }
        assertEquals("schema", meta.getSchemaTerm());
        assertEquals("\\", meta.getSearchStringEscape());
        assertEquals("LIMIT,MINUS,ROWNUM,SYSDATE,SYSTIME,SYSTIMESTAMP,TODAY", meta.getSQLKeywords());

        assertTrue(meta.getURL().startsWith("jdbc:h2:"));
        assertTrue(meta.getUserName().length() > 1);
        assertFalse(meta.insertsAreDetected(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.insertsAreDetected(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.insertsAreDetected(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertTrue(meta.isCatalogAtStart());
        assertFalse(meta.isReadOnly());
        assertTrue(meta.nullPlusNonNullIsNull());
        assertFalse(meta.nullsAreSortedAtEnd());
        assertFalse(meta.nullsAreSortedAtStart());
        assertFalse(meta.nullsAreSortedHigh());
        assertTrue(meta.nullsAreSortedLow());
        assertFalse(meta.othersDeletesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.othersDeletesAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.othersDeletesAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.othersInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.othersInsertsAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.othersInsertsAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.othersUpdatesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.othersUpdatesAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.othersUpdatesAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.ownDeletesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.ownDeletesAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.ownDeletesAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.ownInsertsAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.ownInsertsAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.ownInsertsAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertTrue(meta.ownUpdatesAreVisible(ResultSet.TYPE_FORWARD_ONLY));
        assertTrue(meta.ownUpdatesAreVisible(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertTrue(meta.ownUpdatesAreVisible(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.storesLowerCaseIdentifiers());
        assertFalse(meta.storesLowerCaseQuotedIdentifiers());
        assertFalse(meta.storesMixedCaseIdentifiers());
        assertTrue(meta.storesMixedCaseQuotedIdentifiers());
        assertTrue(meta.storesUpperCaseIdentifiers());
        assertFalse(meta.storesUpperCaseQuotedIdentifiers());
        assertTrue(meta.supportsAlterTableWithAddColumn());
        assertTrue(meta.supportsAlterTableWithDropColumn());
        assertTrue(meta.supportsANSI92EntryLevelSQL());
        assertFalse(meta.supportsANSI92IntermediateSQL());
        assertFalse(meta.supportsANSI92FullSQL());
        assertTrue(meta.supportsBatchUpdates());
        assertTrue(meta.supportsCatalogsInDataManipulation());
        assertTrue(meta.supportsCatalogsInIndexDefinitions());
        assertTrue(meta.supportsCatalogsInPrivilegeDefinitions());
        assertFalse(meta.supportsCatalogsInProcedureCalls());
        assertTrue(meta.supportsCatalogsInTableDefinitions());
        assertTrue(meta.supportsColumnAliasing());
        assertTrue(meta.supportsConvert());
        assertTrue(meta.supportsConvert(Types.INTEGER, Types.VARCHAR));
        assertTrue(meta.supportsCoreSQLGrammar());
        assertTrue(meta.supportsCorrelatedSubqueries());
        assertFalse(meta.supportsDataDefinitionAndDataManipulationTransactions());
        assertTrue(meta.supportsDataManipulationTransactionsOnly());
        assertFalse(meta.supportsDifferentTableCorrelationNames());
        assertTrue(meta.supportsExpressionsInOrderBy());
        assertFalse(meta.supportsExtendedSQLGrammar());
        assertFalse(meta.supportsFullOuterJoins());
        if (config.jdk14) {
            assertTrue(meta.supportsGetGeneratedKeys());
            assertTrue(meta.supportsMultipleOpenResults());
            assertFalse(meta.supportsNamedParameters());
        }
        assertTrue(meta.supportsGroupBy());
        assertTrue(meta.supportsGroupByBeyondSelect());
        assertTrue(meta.supportsGroupByUnrelated());
        assertTrue(meta.supportsIntegrityEnhancementFacility());
        assertTrue(meta.supportsLikeEscapeClause());
        assertTrue(meta.supportsLimitedOuterJoins());
        assertTrue(meta.supportsMinimumSQLGrammar());
        assertFalse(meta.supportsMixedCaseIdentifiers());
        assertTrue(meta.supportsMixedCaseQuotedIdentifiers());
        assertFalse(meta.supportsMultipleResultSets());
        assertTrue(meta.supportsMultipleTransactions());
        assertTrue(meta.supportsNonNullableColumns());
        assertFalse(meta.supportsOpenCursorsAcrossCommit());
        assertFalse(meta.supportsOpenCursorsAcrossRollback());
        assertTrue(meta.supportsOpenStatementsAcrossCommit());
        assertTrue(meta.supportsOpenStatementsAcrossRollback());
        assertTrue(meta.supportsOrderByUnrelated());
        assertTrue(meta.supportsOuterJoins());
        assertTrue(meta.supportsPositionedDelete());
        assertTrue(meta.supportsPositionedUpdate());
        assertTrue(meta.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
        assertTrue(meta.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
        assertTrue(meta.supportsResultSetConcurrency(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY));
        assertTrue(meta.supportsResultSetConcurrency(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE));
        assertFalse(meta.supportsResultSetConcurrency(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_READ_ONLY));
        assertFalse(meta.supportsResultSetConcurrency(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE));
        if (config.jdk14) {
            assertFalse(meta.supportsResultSetHoldability(ResultSet.HOLD_CURSORS_OVER_COMMIT));
            assertTrue(meta.supportsResultSetHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT));
            assertTrue(meta.supportsSavepoints());
            assertFalse(meta.supportsStatementPooling());
        }
        assertTrue(meta.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY));
        assertTrue(meta.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.supportsResultSetType(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertTrue(meta.supportsSchemasInDataManipulation());
        assertTrue(meta.supportsSchemasInIndexDefinitions());
        assertTrue(meta.supportsSchemasInPrivilegeDefinitions());
        assertTrue(meta.supportsSchemasInProcedureCalls());
        assertTrue(meta.supportsSchemasInTableDefinitions());
        assertTrue(meta.supportsSelectForUpdate());
        assertFalse(meta.supportsStoredProcedures());
        assertTrue(meta.supportsSubqueriesInComparisons());
        assertTrue(meta.supportsSubqueriesInExists());
        assertTrue(meta.supportsSubqueriesInIns());
        assertTrue(meta.supportsSubqueriesInQuantifieds());
        assertTrue(meta.supportsTableCorrelationNames());
        assertTrue(meta.supportsTransactions());
        assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_NONE));
        assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED));
        assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED));
        assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ));
        assertTrue(meta.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE));
        assertTrue(meta.supportsUnion());
        assertTrue(meta.supportsUnionAll());
        assertFalse(meta.updatesAreDetected(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(meta.updatesAreDetected(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertFalse(meta.updatesAreDetected(ResultSet.TYPE_SCROLL_SENSITIVE));
        assertFalse(meta.usesLocalFilePerTable());
        assertTrue(meta.usesLocalFiles());
    }

    private void test(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        Statement stat = conn.createStatement();
        ResultSet rs;

        conn.setReadOnly(true);
        conn.setReadOnly(false);
        assertFalse(conn.isReadOnly());
        assertTrue(conn.isReadOnly() == meta.isReadOnly());

        assertTrue(conn == meta.getConnection());

        // currently, setCatalog is ignored
        conn.setCatalog("XYZ");
        trace(conn.getCatalog());

        String product = meta.getDatabaseProductName();
        trace("meta.getDatabaseProductName:" + product);

        String version = meta.getDatabaseProductVersion();
        trace("meta.getDatabaseProductVersion:" + version);

        int major = meta.getDriverMajorVersion();
        trace("meta.getDriverMajorVersion:" + major);

        int minor = meta.getDriverMinorVersion();
        trace("meta.getDriverMinorVersion:" + minor);

        String driverName = meta.getDriverName();
        trace("meta.getDriverName:" + driverName);

        String driverVersion = meta.getDriverVersion();
        trace("meta.getDriverVersion:" + driverVersion);

        meta.getSearchStringEscape();

        String url = meta.getURL();
        trace("meta.getURL:" + url);

        String user = meta.getUserName();
        trace("meta.getUserName:" + user);

        trace("meta.nullsAreSortedHigh:" + meta.nullsAreSortedHigh());
        trace("meta.nullsAreSortedLow:" + meta.nullsAreSortedLow());
        trace("meta.nullsAreSortedAtStart:" + meta.nullsAreSortedAtStart());
        trace("meta.nullsAreSortedAtEnd:" + meta.nullsAreSortedAtEnd());
        int count = (meta.nullsAreSortedHigh() ? 1 : 0) + (meta.nullsAreSortedLow() ? 1 : 0)
                + (meta.nullsAreSortedAtStart() ? 1 : 0) + (meta.nullsAreSortedAtEnd() ? 1 : 0);
        assertTrue(count == 1);

        trace("meta.allProceduresAreCallable:" + meta.allProceduresAreCallable());
        assertTrue(meta.allProceduresAreCallable());

        trace("meta.allTablesAreSelectable:" + meta.allTablesAreSelectable());
        assertTrue(meta.allTablesAreSelectable());

        trace("getTables");
        rs = meta.getTables(null, Constants.SCHEMA_MAIN, null, new String[] { "TABLE" });
        assertResultSetMeta(rs, 11, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS",
                "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION", "SQL" },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR }, null, null);
        if (rs.next()) {
            fail("Database is not empty after dropping all tables");
        }
        stat.executeUpdate("CREATE TABLE TEST(" + "ID INT PRIMARY KEY," + "TEXT_V VARCHAR(120),"
                + "DEC_V DECIMAL(12,3)," + "DATE_V DATETIME," + "BLOB_V BLOB," + "CLOB_V CLOB" + ")");
        rs = meta.getTables(null, Constants.SCHEMA_MAIN, null, new String[] { "TABLE" });
        assertResultSetOrdered(rs, new String[][] { { catalog, Constants.SCHEMA_MAIN, "TEST", "TABLE", "" } });
        trace("getColumns");
        rs = meta.getColumns(null, null, "TEST", null);
        assertResultSetMeta(rs, 23, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "DATA_TYPE",
                "TYPE_NAME", "COLUMN_SIZE", "BUFFER_LENGTH", "DECIMAL_DIGITS", "NUM_PREC_RADIX", "NULLABLE", "REMARKS",
                "COLUMN_DEF", "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "CHAR_OCTET_LENGTH", "ORDINAL_POSITION",
                "IS_NULLABLE", "SCOPE_CATALOG", "SCOPE_SCHEMA", "SCOPE_TABLE", "SOURCE_DATA_TYPE", "IS_AUTOINCREMENT" },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER,
                Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.VARCHAR }, null, null);
        assertResultSetOrdered(rs,
                new String[][] {
                        { catalog, Constants.SCHEMA_MAIN, "TEST", "ID", "" + Types.INTEGER, "INTEGER", "10", "10", "0",
                                "10", "" + DatabaseMetaData.columnNoNulls, "", null, "" + Types.INTEGER, "0", "10", "1",
                                "NO" },
                        { catalog, Constants.SCHEMA_MAIN, "TEST", "TEXT_V", "" + Types.VARCHAR, "VARCHAR", "120",
                                "120", "0", "10", "" + DatabaseMetaData.columnNullable, "", null, "" + Types.VARCHAR,
                                "0", "120", "2", "YES" },
                        { catalog, Constants.SCHEMA_MAIN, "TEST", "DEC_V", "" + Types.DECIMAL, "DECIMAL", "12", "12",
                                "3", "10", "" + DatabaseMetaData.columnNullable, "", null, "" + Types.DECIMAL, "0", "12",
                                "3", "YES" },
                        { catalog, Constants.SCHEMA_MAIN, "TEST", "DATE_V", "" + Types.TIMESTAMP, "TIMESTAMP", "23",
                                "23", "10", "10", "" + DatabaseMetaData.columnNullable, "", null, "" + Types.TIMESTAMP,
                                "0", "23", "4", "YES" },
                        { catalog, Constants.SCHEMA_MAIN, "TEST", "BLOB_V", "" + Types.BLOB, "BLOB",
                                "" + Integer.MAX_VALUE, "" + Integer.MAX_VALUE, "0", "10",
                                "" + DatabaseMetaData.columnNullable, "", null, "" + Types.BLOB, "0",
                                "" + Integer.MAX_VALUE, "5", "YES" },
                        { catalog, Constants.SCHEMA_MAIN, "TEST", "CLOB_V", "" + Types.CLOB, "CLOB",
                                "" + Integer.MAX_VALUE, "" + Integer.MAX_VALUE, "0", "10",
                                "" + DatabaseMetaData.columnNullable, "", null, "" + Types.CLOB, "0",
                                "" + Integer.MAX_VALUE, "6", "YES" } });
        /*
         * rs=meta.getColumns(null,null,"TEST",null); while(rs.next()) { int
         * datatype=rs.getInt(5); }
         */
        trace("getIndexInfo");
        stat.executeUpdate("CREATE INDEX IDX_TEXT_DEC ON TEST(TEXT_V,DEC_V)");
        stat.executeUpdate("CREATE UNIQUE INDEX IDX_DATE ON TEST(DATE_V)");
        rs = meta.getIndexInfo(null, null, "TEST", false, false);
        assertResultSetMeta(rs, 14, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE",
                "INDEX_QUALIFIER", "INDEX_NAME", "TYPE", "ORDINAL_POSITION", "COLUMN_NAME", "ASC_OR_DESC",
                "CARDINALITY", "PAGES", "FILTER_CONDITION", "SORT_TYPE"}, new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                DataType.TYPE_BOOLEAN, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR,
                Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.VARCHAR, Types.INTEGER}, null, null);
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "TEST", "FALSE", catalog, "IDX_DATE",
                        "" + DatabaseMetaData.tableIndexOther, "1", "DATE_V", "A", "0", "0", "" },
                { catalog, Constants.SCHEMA_MAIN, "TEST", "FALSE", catalog, "PRIMARY_KEY_2",
                        "" + DatabaseMetaData.tableIndexOther, "1", "ID", "A", "0", "0", "" },
                { catalog, Constants.SCHEMA_MAIN, "TEST", "TRUE", catalog, "IDX_TEXT_DEC",
                        "" + DatabaseMetaData.tableIndexOther, "1", "TEXT_V", "A", "0", "0", "" },
                { catalog, Constants.SCHEMA_MAIN, "TEST", "TRUE", catalog, "IDX_TEXT_DEC",
                        "" + DatabaseMetaData.tableIndexOther, "2", "DEC_V", "A", "0", "0", "" }, });
        stat.executeUpdate("DROP INDEX IDX_TEXT_DEC");
        stat.executeUpdate("DROP INDEX IDX_DATE");
        rs = meta.getIndexInfo(null, null, "TEST", false, false);
        assertResultSetMeta(rs, 14, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "NON_UNIQUE",
                "INDEX_QUALIFIER", "INDEX_NAME", "TYPE", "ORDINAL_POSITION", "COLUMN_NAME", "ASC_OR_DESC",
                "CARDINALITY", "PAGES", "FILTER_CONDITION", "SORT_TYPE" }, new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                DataType.TYPE_BOOLEAN, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR,
                Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.VARCHAR, Types.INTEGER }, null, null);
        assertResultSetOrdered(rs, new String[][] { { catalog, Constants.SCHEMA_MAIN, "TEST", "FALSE", catalog,
                "PRIMARY_KEY_2", "" + DatabaseMetaData.tableIndexOther, "1", "ID", "A", "0", "0", "" } });
        trace("getPrimaryKeys");
        rs = meta.getPrimaryKeys(null, null, "TEST");
        assertResultSetMeta(rs, 6, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ",
                "PK_NAME" }, new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT,
                Types.VARCHAR }, null, null);
        assertResultSetOrdered(rs,
                new String[][] { { catalog, Constants.SCHEMA_MAIN, "TEST", "ID", "1", "CONSTRAINT_2" }, });
        trace("getTables - using a wildcard");
        stat.executeUpdate("CREATE TABLE T_2(B INT,A VARCHAR(6),C INT,PRIMARY KEY(C,A,B))");
        stat.executeUpdate("CREATE TABLE TX2(B INT,A VARCHAR(6),C INT,PRIMARY KEY(C,A,B))");
        rs = meta.getTables(null, null, "T_2", null);
        assertResultSetOrdered(rs, new String[][] { { catalog, Constants.SCHEMA_MAIN, "TX2", "TABLE", "" },
                { catalog, Constants.SCHEMA_MAIN, "T_2", "TABLE", "" } });
        trace("getTables - using a quoted _ character");
        rs = meta.getTables(null, null, "T\\_2", null);
        assertResultSetOrdered(rs, new String[][] { { catalog, Constants.SCHEMA_MAIN, "T_2", "TABLE", "" } });
        trace("getTables - using the % wildcard");
        rs = meta.getTables(null, Constants.SCHEMA_MAIN, "%", new String[] { "TABLE" });
        assertResultSetOrdered(rs, new String[][] { { catalog, Constants.SCHEMA_MAIN, "TEST", "TABLE", "" },
                { catalog, Constants.SCHEMA_MAIN, "TX2", "TABLE", "" },
                { catalog, Constants.SCHEMA_MAIN, "T_2", "TABLE", "" } });
        stat.execute("DROP TABLE TEST");

        trace("getColumns - using wildcards");
        rs = meta.getColumns(null, null, "___", "B%");
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "TX2", "B", "" + Types.INTEGER, "INTEGER", "10"},
                { catalog, Constants.SCHEMA_MAIN, "T_2", "B", "" + Types.INTEGER, "INTEGER", "10"}, });
        trace("getColumns - using wildcards");
        rs = meta.getColumns(null, null, "_\\__", "%");
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "T_2", "B", "" + Types.INTEGER, "INTEGER", "10"},
                { catalog, Constants.SCHEMA_MAIN, "T_2", "A", "" + Types.VARCHAR, "VARCHAR", "6"},
                { catalog, Constants.SCHEMA_MAIN, "T_2", "C", "" + Types.INTEGER, "INTEGER", "10"}, });
        trace("getIndexInfo");
        stat.executeUpdate("CREATE UNIQUE INDEX A_INDEX ON TX2(B,C,A)");
        stat.executeUpdate("CREATE INDEX B_INDEX ON TX2(A,B,C)");
        rs = meta.getIndexInfo(null, null, "TX2", false, false);
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "TX2", "FALSE", catalog, "A_INDEX",
                        "" + DatabaseMetaData.tableIndexOther, "1", "B", "A" },
                { catalog, Constants.SCHEMA_MAIN, "TX2", "FALSE", catalog, "A_INDEX",
                        "" + DatabaseMetaData.tableIndexOther, "2", "C", "A"},
                { catalog, Constants.SCHEMA_MAIN, "TX2", "FALSE", catalog, "A_INDEX",
                        "" + DatabaseMetaData.tableIndexOther, "3", "A", "A"},
                { catalog, Constants.SCHEMA_MAIN, "TX2", "FALSE", catalog, "PRIMARY_KEY_14",
                        "" + DatabaseMetaData.tableIndexOther, "1", "C", "A"},
                { catalog, Constants.SCHEMA_MAIN, "TX2", "FALSE", catalog, "PRIMARY_KEY_14",
                        "" + DatabaseMetaData.tableIndexOther, "2", "A", "A" },
                { catalog, Constants.SCHEMA_MAIN, "TX2", "FALSE", catalog, "PRIMARY_KEY_14",
                        "" + DatabaseMetaData.tableIndexOther, "3", "B", "A"},
                { catalog, Constants.SCHEMA_MAIN, "TX2", "TRUE", catalog, "B_INDEX",
                        "" + DatabaseMetaData.tableIndexOther, "1", "A", "A" },
                { catalog, Constants.SCHEMA_MAIN, "TX2", "TRUE", catalog, "B_INDEX",
                        "" + DatabaseMetaData.tableIndexOther, "2", "B", "A" },
                { catalog, Constants.SCHEMA_MAIN, "TX2", "TRUE", catalog, "B_INDEX",
                        "" + DatabaseMetaData.tableIndexOther, "3", "C", "A" }, });
        trace("getPrimaryKeys");
        rs = meta.getPrimaryKeys(null, null, "T_2");
        assertResultSetOrdered(rs, new String[][] {
                { catalog, Constants.SCHEMA_MAIN, "T_2", "A", "2", "CONSTRAINT_1" },
                { catalog, Constants.SCHEMA_MAIN, "T_2", "B", "3", "CONSTRAINT_1" },
                { catalog, Constants.SCHEMA_MAIN, "T_2", "C", "1", "CONSTRAINT_1" }, });
        stat.executeUpdate("DROP TABLE TX2");
        stat.executeUpdate("DROP TABLE T_2");
        stat.executeUpdate("CREATE TABLE PARENT(ID INT PRIMARY KEY)");
        stat
                .executeUpdate("CREATE TABLE CHILD(P_ID INT,ID INT,PRIMARY KEY(P_ID,ID),FOREIGN KEY(P_ID) REFERENCES PARENT(ID))");

        trace("getImportedKeys");
        rs = meta.getImportedKeys(null, null, "CHILD");
        assertResultSetMeta(rs, 14, new String[] { "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
                "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE",
                "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY" }, new int[] { Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT }, null,
                null);
        // TODO test
        // testResultSetOrdered(rs, new String[][] { { null, null, "PARENT",
        // "ID",
        // null, null, "CHILD", "P_ID", "1",
        // "" + DatabaseMetaData.importedKeyNoAction,
        // "" + DatabaseMetaData.importedKeyNoAction, "FK_1", null,
        // "" + DatabaseMetaData.importedKeyNotDeferrable}});

        trace("getExportedKeys");
        rs = meta.getExportedKeys(null, null, "PARENT");
        assertResultSetMeta(rs, 14, new String[] { "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
                "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE",
                "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY" }, new int[] { Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT }, null,
                null);
        // TODO test
        /*
         * testResultSetOrdered(rs, new String[][]{ { null,null,"PARENT","ID",
         * null,null,"CHILD","P_ID",
         * "1",""+DatabaseMetaData.importedKeyNoAction,
         * ""+DatabaseMetaData.importedKeyNoAction,
         * null,null,""+DatabaseMetaData.importedKeyNotDeferrable } } );
         */
        trace("getCrossReference");
        rs = meta.getCrossReference(null, null, "PARENT", null, null, "CHILD");
        assertResultSetMeta(rs, 14, new String[] { "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
                "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME", "KEY_SEQ", "UPDATE_RULE",
                "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY" }, new int[] { Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.SMALLINT, Types.SMALLINT, Types.SMALLINT, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT }, null,
                null);
        // TODO test
        /*
         * testResultSetOrdered(rs, new String[][]{ { null,null,"PARENT","ID",
         * null,null,"CHILD","P_ID",
         * "1",""+DatabaseMetaData.importedKeyNoAction,
         * ""+DatabaseMetaData.importedKeyNoAction,
         * null,null,""+DatabaseMetaData.importedKeyNotDeferrable } } );
         */

        rs = meta.getSchemas();
        assertResultSetMeta(rs, 3, new String[] { "TABLE_SCHEM", "TABLE_CATALOG", "IS_DEFAULT" }, new int[] {
                Types.VARCHAR, Types.VARCHAR, DataType.TYPE_BOOLEAN }, null, null);
        assertTrue(rs.next());
        assertEquals("INFORMATION_SCHEMA", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("PUBLIC", rs.getString(1));
        assertFalse(rs.next());

        rs = meta.getCatalogs();
        assertResultSetMeta(rs, 1, new String[] { "TABLE_CAT" }, new int[] { Types.VARCHAR }, null, null);
        assertResultSetOrdered(rs, new String[][] { { catalog } });

        rs = meta.getTableTypes();
        assertResultSetMeta(rs, 1, new String[] { "TABLE_TYPE" }, new int[] { Types.VARCHAR }, null, null);
        assertResultSetOrdered(rs, new String[][] { { "SYSTEM TABLE" }, { "TABLE" }, { "TABLE LINK" }, { "VIEW" } });

        rs = meta.getTypeInfo();
        assertResultSetMeta(rs, 18, new String[] { "TYPE_NAME", "DATA_TYPE", "PRECISION", "LITERAL_PREFIX",
                "LITERAL_SUFFIX", "CREATE_PARAMS", "NULLABLE", "CASE_SENSITIVE", "SEARCHABLE", "UNSIGNED_ATTRIBUTE",
                "FIXED_PREC_SCALE", "AUTO_INCREMENT", "LOCAL_TYPE_NAME", "MINIMUM_SCALE", "MAXIMUM_SCALE",
                "SQL_DATA_TYPE", "SQL_DATETIME_SUB", "NUM_PREC_RADIX" }, new int[] { Types.VARCHAR, Types.INTEGER,
                Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.SMALLINT, DataType.TYPE_BOOLEAN,
                Types.SMALLINT, DataType.TYPE_BOOLEAN, DataType.TYPE_BOOLEAN, DataType.TYPE_BOOLEAN, Types.VARCHAR,
                Types.SMALLINT, Types.SMALLINT, Types.INTEGER, Types.INTEGER, Types.INTEGER }, null, null);

        rs = meta.getTablePrivileges(null, null, null);
        assertResultSetMeta(rs, 7, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "GRANTOR", "GRANTEE",
                "PRIVILEGE", "IS_GRANTABLE" }, new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR }, null, null);

        rs = meta.getColumnPrivileges(null, null, "TEST", null);
        assertResultSetMeta(rs, 8, new String[] { "TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "GRANTOR",
                "GRANTEE", "PRIVILEGE", "IS_GRANTABLE" }, new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR }, null, null);

        assertTrue(conn.getWarnings() == null);
        conn.clearWarnings();
        assertTrue(conn.getWarnings() == null);

    }

}
