/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.fulltext;

import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.StringTokenizer;
import org.h2.api.CloseListener;
import org.h2.api.Trigger;
import org.h2.command.Parser;
import org.h2.engine.Session;
import org.h2.expression.Comparison;
import org.h2.expression.ConditionAndOr;
import org.h2.expression.Expression;
import org.h2.expression.ExpressionColumn;
import org.h2.expression.ValueExpression;
import org.h2.jdbc.JdbcConnection;
import org.h2.message.Message;
import org.h2.tools.SimpleResultSet;
import org.h2.util.ByteUtils;
import org.h2.util.IOUtils;
import org.h2.util.JdbcUtils;
import org.h2.util.New;
import org.h2.util.StatementBuilder;
import org.h2.util.StringUtils;
import org.h2.value.DataType;

/**
 * This class implements the native full text search.
 * Most methods can be called using SQL statements as well.
 */
public class FullText {

    /**
     * A column name of the result set returned by the searchData method.
     */
    protected static final String FIELD_SCHEMA = "SCHEMA";

    /**
     * A column name of the result set returned by the searchData method.
     */
    protected static final String FIELD_TABLE = "TABLE";

    /**
     * A column name of the result set returned by the searchData method.
     */
    protected static final String FIELD_COLUMNS = "COLUMNS";

    /**
     * A column name of the result set returned by the searchData method.
     */
    protected static final String FIELD_KEYS = "KEYS";

    /**
     * The hit score.
     */
    protected static final String FIELD_SCORE = "SCORE";

    private static final String TRIGGER_PREFIX = "FT_";
    private static final String SCHEMA = "FT";
    private static final String SELECT_MAP_BY_WORD_ID = "SELECT ROWID FROM " + SCHEMA + ".MAP WHERE WORDID=?";
    private static final String SELECT_ROW_BY_ID = "SELECT KEY, INDEXID FROM " + SCHEMA + ".ROWS WHERE ID=?";

    /**
     * The column name of the result set returned by the search method.
     */
    private static final String FIELD_QUERY = "QUERY";

    /**
     * Initializes full text search functionality for this database. This adds
     * the following Java functions to the database:
     * <ul>
     * <li>FT_CREATE_INDEX(schemaNameString, tableNameString,
     * columnListString)</li>
     * <li>FT_SEARCH(queryString, limitInt, offsetInt): result set</li>
     * <li>FT_REINDEX()</li>
     * <li>FT_DROP_ALL()</li>
     * </ul>
     * It also adds a schema FT to the database where bookkeeping information
     * is stored. This function may be called from a Java application, or by
     * using the SQL statements:
     *
     * <pre>
     * CREATE ALIAS IF NOT EXISTS FT_INIT FOR
     *      &quot;org.h2.fulltext.FullText.init&quot;;
     * CALL FT_INIT();
     * </pre>
     *
     * @param conn the connection
     */
    public static void init(Connection conn) throws SQLException {
        Statement stat = conn.createStatement();
        stat.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        stat.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA
                        + ".INDEXES(ID INT AUTO_INCREMENT PRIMARY KEY, SCHEMA VARCHAR, TABLE VARCHAR, COLUMNS VARCHAR, UNIQUE(SCHEMA, TABLE))");
        stat.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA
                + ".WORDS(ID INT AUTO_INCREMENT PRIMARY KEY, NAME VARCHAR, UNIQUE(NAME))");
        stat.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA
                + ".ROWS(ID IDENTITY, HASH INT, INDEXID INT, KEY VARCHAR, UNIQUE(HASH, INDEXID, KEY))");
        stat.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA
                        + ".MAP(ROWID INT, WORDID INT, PRIMARY KEY(WORDID, ROWID))");
        stat.execute("CREATE TABLE IF NOT EXISTS " + SCHEMA + ".IGNORELIST(LIST VARCHAR)");
        stat.execute("CREATE ALIAS IF NOT EXISTS FT_CREATE_INDEX FOR \"" + FullText.class.getName() + ".createIndex\"");
        stat.execute("CREATE ALIAS IF NOT EXISTS FT_DROP_INDEX FOR \"" + FullText.class.getName() + ".dropIndex\"");
        stat.execute("CREATE ALIAS IF NOT EXISTS FT_SEARCH FOR \"" + FullText.class.getName() + ".search\"");
        stat.execute("CREATE ALIAS IF NOT EXISTS FT_SEARCH_DATA FOR \"" + FullText.class.getName() + ".searchData\"");
        stat.execute("CREATE ALIAS IF NOT EXISTS FT_REINDEX FOR \"" + FullText.class.getName() + ".reindex\"");
        stat.execute("CREATE ALIAS IF NOT EXISTS FT_DROP_ALL FOR \"" + FullText.class.getName() + ".dropAll\"");
        FullTextSettings setting = FullTextSettings.getInstance(conn);
        ResultSet rs = stat.executeQuery("SELECT * FROM " + SCHEMA + ".IGNORELIST");
        while (rs.next()) {
            String commaSeparatedList = rs.getString(1);
            setIgnoreList(setting, commaSeparatedList);
        }
        rs = stat.executeQuery("SELECT * FROM " + SCHEMA + ".WORDS");
        HashMap<String, Integer> map = setting.getWordList();
        while (rs.next()) {
            String word = rs.getString("NAME");
            int id = rs.getInt("ID");
            word = setting.convertWord(word);
            if (word != null) {
                map.put(word, id);
            }
        }
        setting.setInitialized(true);
    }

    /**
     * Create a new full text index for a table and column list. Each table may
     * only have one index at any time.
     *
     * @param conn the connection
     * @param schema the schema name of the table (case sensitive)
     * @param table the table name (case sensitive)
     * @param columnList the column list (null for all columns)
     */
    public static void createIndex(Connection conn, String schema, String table, String columnList) throws SQLException {
        init(conn);
        PreparedStatement prep = conn.prepareStatement("INSERT INTO " + SCHEMA
                + ".INDEXES(SCHEMA, TABLE, COLUMNS) VALUES(?, ?, ?)");
        prep.setString(1, schema);
        prep.setString(2, table);
        prep.setString(3, columnList);
        prep.execute();
        createTrigger(conn, schema, table);
        indexExistingRows(conn, schema, table);
    }

    /**
     * Re-creates the full text index for this database
     *
     * @param conn the connection
     */
    public static void reindex(Connection conn) throws SQLException {
        init(conn);
        removeAllTriggers(conn, TRIGGER_PREFIX);
        FullTextSettings setting = FullTextSettings.getInstance(conn);
        setting.getWordList().clear();
        Statement stat = conn.createStatement();
        stat.execute("TRUNCATE TABLE " + SCHEMA + ".WORDS");
        stat.execute("TRUNCATE TABLE " + SCHEMA + ".ROWS");
        stat.execute("TRUNCATE TABLE " + SCHEMA + ".MAP");
        ResultSet rs = stat.executeQuery("SELECT * FROM " + SCHEMA + ".INDEXES");
        while (rs.next()) {
            String schema = rs.getString("SCHEMA");
            String table = rs.getString("TABLE");
            createTrigger(conn, schema, table);
            indexExistingRows(conn, schema, table);
        }
    }

    /**
     * Drop an existing full text index for a table. This method returns
     * silently if no index for this table exists.
     *
     * @param conn the connection
     * @param schema the schema name of the table (case sensitive)
     * @param table the table name (case sensitive)
     */
    public static void dropIndex(Connection conn, String schema, String table) throws SQLException {
        init(conn);
        PreparedStatement prep = conn.prepareStatement("SELECT ID FROM " + SCHEMA
                + ".INDEXES WHERE SCHEMA=? AND TABLE=?");
        prep.setString(1, schema);
        prep.setString(2, table);
        ResultSet rs = prep.executeQuery();
        if (!rs.next()) {
            return;
        }
        int indexId = rs.getInt(1);
        prep = conn.prepareStatement("DELETE FROM " + SCHEMA
                + ".INDEXES WHERE ID=?");
        prep.setInt(1, indexId);
        prep.execute();
        createOrDropTrigger(conn, schema, table, false);
        prep = conn.prepareStatement("DELETE FROM " + SCHEMA +
                ".ROWS WHERE INDEXID=? AND ROWNUM<10000");
        while (true) {
            prep.setInt(1, indexId);
            int deleted = prep.executeUpdate();
            if (deleted == 0) {
                break;
            }
        }
        prep = conn.prepareStatement("DELETE FROM " + SCHEMA + ".MAP M " +
                "WHERE NOT EXISTS (SELECT * FROM " + SCHEMA + ".ROWS R WHERE R.ID=M.ROWID) AND ROWID<10000");
        while (true) {
            int deleted = prep.executeUpdate();
            if (deleted == 0) {
                break;
            }
        }
    }

    /**
     * Drops all full text indexes from the database.
     *
     * @param conn the connection
     */
    public static void dropAll(Connection conn) throws SQLException {
        init(conn);
        Statement stat = conn.createStatement();
        stat.execute("DROP SCHEMA IF EXISTS " + SCHEMA);
        removeAllTriggers(conn, TRIGGER_PREFIX);
        FullTextSettings setting = FullTextSettings.getInstance(conn);
        setting.removeAllIndexes();
        setting.getIgnoreList().clear();
        setting.getWordList().clear();
    }

    /**
     * Searches from the full text index for this database.
     * The returned result set has the following column:
     * <ul><li>QUERY (varchar): the query to use to get the data.
     * The query does not include 'SELECT * FROM '. Example:
     * PUBLIC.TEST WHERE ID = 1
     * </li><li>SCORE (float) the relevance score. This value is always 1.0
     * for the native fulltext search.
     * </li></ul>
     *
     * @param conn the connection
     * @param text the search query
     * @param limit the maximum number of rows or 0 for no limit
     * @param offset the offset or 0 for no offset
     * @return the result set
     */
    public static ResultSet search(Connection conn, String text, int limit, int offset) throws SQLException {
        return search(conn, text, limit, offset, false);
    }

    /**
     * Searches from the full text index for this database. The result contains
     * the primary key data as an array. The returned result set has the
     * following columns:
     * <ul>
     * <li>SCHEMA (varchar): the schema name. Example: PUBLIC </li>
     * <li>TABLE (varchar): the table name. Example: TEST </li>
     * <li>COLUMNS (array of varchar): comma separated list of quoted column
     * names. The column names are quoted if necessary. Example: (ID) </li>
     * <li>KEYS (array of values): comma separated list of values. Example: (1)
     * </li>
     * <li>SCORE (float) the relevance score. This value is always 1.0
     * for the native fulltext search.
     * </li>
     * </ul>
     *
     * @param conn the connection
     * @param text the search query
     * @param limit the maximum number of rows or 0 for no limit
     * @param offset the offset or 0 for no offset
     * @return the result set
     */
    public static ResultSet searchData(Connection conn, String text, int limit, int offset) throws SQLException {
        return search(conn, text, limit, offset, true);
    }

    /**
     * Change the ignore list. The ignore list is a comma separated list of
     * common words that must not be indexed. The default ignore list is empty.
     * If indexes already exist at the time this list is changed, reindex must
     * be called.
     *
     * @param conn the connection
     * @param commaSeparatedList the list
     */
    public static void setIgnoreList(Connection conn, String commaSeparatedList) throws SQLException {
        init(conn);
        FullTextSettings setting = FullTextSettings.getInstance(conn);
        setIgnoreList(setting, commaSeparatedList);
        Statement stat = conn.createStatement();
        stat.execute("TRUNCATE TABLE " + SCHEMA + ".IGNORELIST");
        PreparedStatement prep = conn.prepareStatement("INSERT INTO " + SCHEMA + ".IGNORELIST VALUES(?)");
        prep.setString(1, commaSeparatedList);
        prep.execute();
    }

    /**
     * INTERNAL.
     * Convert the object to a string.
     *
     * @param data the object
     * @param type the SQL type
     * @return the string
     */
    protected static String asString(Object data, int type) throws SQLException {
        if (data == null) {
            return "NULL";
        }
        switch (type) {
        case Types.BIT:
        case DataType.TYPE_BOOLEAN:
        case Types.INTEGER:
        case Types.BIGINT:
        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.NUMERIC:
        case Types.REAL:
        case Types.SMALLINT:
        case Types.TINYINT:
        case Types.DATE:
        case Types.TIME:
        case Types.TIMESTAMP:
        case Types.LONGVARCHAR:
        case Types.CHAR:
        case Types.VARCHAR:
            return data.toString();
        case Types.CLOB:
            try {
                if (data instanceof Clob) {
                    data = ((Clob) data).getCharacterStream();
                }
                return IOUtils.readStringAndClose((Reader) data, -1);
            } catch (IOException e) {
                throw Message.convert(e);
            }
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
        case Types.BINARY:
        case Types.JAVA_OBJECT:
        case Types.OTHER:
        case Types.BLOB:
        case Types.STRUCT:
        case Types.REF:
        case Types.NULL:
        case Types.ARRAY:
        case DataType.TYPE_DATALINK:
        case Types.DISTINCT:
            throw new SQLException("FULLTEXT", "Unsupported column data type: " + type);
        default:
            return "";
        }
    }

    /**
     * Create an empty search result and initialize the columns.
     *
     * @param data true if the result set should contain the primary key data as
     *            an array.
     * @return the empty result set
     */
    protected static SimpleResultSet createResultSet(boolean data) throws SQLException {
        SimpleResultSet result = new SimpleResultSet();
        if (data) {
            result.addColumn(FullText.FIELD_SCHEMA, Types.VARCHAR, 0, 0);
            result.addColumn(FullText.FIELD_TABLE, Types.VARCHAR, 0, 0);
            result.addColumn(FullText.FIELD_COLUMNS, Types.ARRAY, 0, 0);
            result.addColumn(FullText.FIELD_KEYS, Types.ARRAY, 0, 0);
        } else {
            result.addColumn(FullText.FIELD_QUERY, Types.VARCHAR, 0, 0);
        }
        result.addColumn(FullText.FIELD_SCORE, Types.FLOAT, 0, 0);
        return result;
    }

    /**
     * Parse a primary key condition into the primary key columns.
     *
     * @param conn the database connection
     * @param key the primary key condition as a string
     * @return an array containing the column name list and the data list
     */
    protected static Object[][] parseKey(Connection conn, String key) throws SQLException {
        ArrayList<String> columns = New.arrayList();
        ArrayList<String> data = New.arrayList();
        JdbcConnection c = (JdbcConnection) conn;
        Session session = (Session) c.getSession();
        Parser p = new Parser(session);
        Expression expr = p.parseExpression(key);
        addColumnData(columns, data, expr);
        Object[] col = new Object[columns.size()];
        columns.toArray(col);
        Object[] dat = new Object[columns.size()];
        data.toArray(dat);
        Object[][] columnData = new Object[][] {
                col, dat
        };
        return columnData;
    }

    /**
     * INTERNAL.
     * Convert an object to a String as used in a SQL statement.
     *
     * @param data the object
     * @param type the SQL type
     * @return the SQL String
     */
    protected static String quoteSQL(Object data, int type) throws SQLException {
        if (data == null) {
            return "NULL";
        }
        switch (type) {
        case Types.BIT:
        case DataType.TYPE_BOOLEAN:
        case Types.INTEGER:
        case Types.BIGINT:
        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.NUMERIC:
        case Types.REAL:
        case Types.SMALLINT:
        case Types.TINYINT:
            return data.toString();
        case Types.DATE:
        case Types.TIME:
        case Types.TIMESTAMP:
        case Types.LONGVARCHAR:
        case Types.CHAR:
        case Types.VARCHAR:
            return quoteString(data.toString());
        case Types.VARBINARY:
        case Types.LONGVARBINARY:
        case Types.BINARY:
            return "'" + ByteUtils.convertBytesToString((byte[]) data) + "'";
        case Types.CLOB:
        case Types.JAVA_OBJECT:
        case Types.OTHER:
        case Types.BLOB:
        case Types.STRUCT:
        case Types.REF:
        case Types.NULL:
        case Types.ARRAY:
        case DataType.TYPE_DATALINK:
        case Types.DISTINCT:
            throw new SQLException("FULLTEXT", "Unsupported key data type: " + type);
        default:
            return "";
        }
    }

    /**
     * Remove all triggers that start with the given prefix.
     *
     * @param conn the database connection
     * @param prefix the prefix
     */
    protected static void removeAllTriggers(Connection conn, String prefix) throws SQLException {
        Statement stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT * FROM INFORMATION_SCHEMA.TRIGGERS");
        Statement stat2 = conn.createStatement();
        while (rs.next()) {
            String schema = rs.getString("TRIGGER_SCHEMA");
            String name = rs.getString("TRIGGER_NAME");
            if (name.startsWith(prefix)) {
                name = StringUtils.quoteIdentifier(schema) + "." + StringUtils.quoteIdentifier(name);
                stat2.execute("DROP TRIGGER " + name);
            }
        }
    }

    /**
     * Set the column indices of a set of keys.
     *
     * @param index the column indices (will be modified)
     * @param keys the key list
     * @param columns the column list
     */
    protected static void setColumns(int[] index, ArrayList<String> keys, ArrayList<String> columns) throws SQLException {
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int found = -1;
            for (int j = 0; found == -1 && j < columns.size(); j++) {
                String column = columns.get(j);
                if (column.equals(key)) {
                    found = j;
                }
            }
            if (found < 0) {
                throw new SQLException("FULLTEXT", "Column not found: " + key);
            }
            index[i] = found;
        }
    }

    /**
     * Do the search.
     *
     * @param conn the database connection
     * @param text the query
     * @param limit the limit
     * @param offset the offset
     * @param data whether the raw data should be returned
     * @return the result set
     */
    protected static ResultSet search(Connection conn, String text, int limit, int offset, boolean data) throws SQLException {
        SimpleResultSet result = createResultSet(data);
        if (conn.getMetaData().getURL().startsWith("jdbc:columnlist:")) {
            // this is just to query the result set columns
            return result;
        }
        if (text == null || text.trim().length() == 0) {
            return result;
        }
        FullTextSettings setting = FullTextSettings.getInstance(conn);
        if (!setting.isInitialized()) {
            init(conn);
        }
        HashSet<String> words = New.hashSet();
        addWords(setting, words, text);
        HashSet<Integer> rIds = null, lastRowIds = null;
        HashMap<String, Integer> allWords = setting.getWordList();

        PreparedStatement prepSelectMapByWordId = setting.prepare(conn, SELECT_MAP_BY_WORD_ID);
        for (String word : words) {
            lastRowIds = rIds;
            rIds = New.hashSet();
            Integer wId = allWords.get(word);
            if (wId == null) {
                continue;
            }
            prepSelectMapByWordId.setInt(1, wId.intValue());
            ResultSet rs = prepSelectMapByWordId.executeQuery();
            while (rs.next()) {
                Integer rId = rs.getInt(1);
                if (lastRowIds == null || lastRowIds.contains(rId)) {
                    rIds.add(rId);
                }
            }
        }
        if (rIds == null || rIds.size() == 0) {
            return result;
        }
        PreparedStatement prepSelectRowById = setting.prepare(conn, SELECT_ROW_BY_ID);
        int rowCount = 0;
        for (int rowId : rIds) {
            prepSelectRowById.setInt(1, rowId);
            ResultSet rs = prepSelectRowById.executeQuery();
            if (!rs.next()) {
                continue;
            }
            if (offset > 0) {
                offset--;
            } else {
                String key = rs.getString(1);
                int indexId = rs.getInt(2);
                IndexInfo index = setting.getIndexInfo(indexId);
                if (data) {
                    Object[][] columnData = parseKey(conn, key);
                    result.addRow(
                            index.schema,
                            index.table,
                            columnData[0],
                            columnData[1],
                            1.0);
                } else {
                    String query = StringUtils.quoteIdentifier(index.schema) +
                        "." + StringUtils.quoteIdentifier(index.table) +
                        " WHERE " + key;
                    result.addRow(query, 1.0);
                }
                rowCount++;
                if (limit > 0 && rowCount >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private static void addColumnData(ArrayList<String> columns, ArrayList<String> data, Expression expr) {
        if (expr instanceof ConditionAndOr) {
            ConditionAndOr and = (ConditionAndOr) expr;
            Expression left = and.getExpression(true);
            Expression right = and.getExpression(false);
            addColumnData(columns, data, left);
            addColumnData(columns, data, right);
        } else {
            Comparison comp = (Comparison) expr;
            ExpressionColumn ec = (ExpressionColumn) comp.getExpression(true);
            ValueExpression ev = (ValueExpression) comp.getExpression(false);
            String columnName = ec.getColumnName();
            columns.add(columnName);
            if (ev == null) {
                data.add(null);
            } else {
                data.add(ev.getValue(null).getString());
            }
        }
    }

    /**
     * Add all words in the given text to the hash set.
     *
     * @param setting the fulltext settings
     * @param set the hash set
     * @param reader the reader
     */
    protected static void addWords(FullTextSettings setting, HashSet<String> set, Reader reader) throws SQLException {
        StreamTokenizer tokenizer = new StreamTokenizer(reader);
        tokenizer.resetSyntax();
        tokenizer.wordChars(' ' + 1, 255);
        for (char ch : " \t\n\r\f+\"*%&/()=?'!,.;:-_#@|^~`{}[]".toCharArray()) {
            tokenizer.whitespaceChars(ch, ch);
        }
        try {
            while (true) {
                int token = tokenizer.nextToken();
                if (token == StreamTokenizer.TT_EOF) {
                    break;
                } else if (token == StreamTokenizer.TT_WORD) {
                    String word = tokenizer.sval;
                    word = setting.convertWord(word);
                    if (word != null) {
                        set.add(word);
                    }
                }
            }
        } catch (IOException e) {
            throw Message.convertIOException(e, "Tokenizer error");
        }
    }

    /**
     * Add all words in the given text to the hash set.
     *
     * @param setting the fulltext settings
     * @param set the hash set
     * @param text the text
     */
    protected static void addWords(FullTextSettings setting, HashSet<String> set, String text) {
        StringTokenizer tokenizer = new StringTokenizer(text, " \t\n\r\f+\"*%&/()=?'!,.;:-_#@|^~`{}[]");
        while (tokenizer.hasMoreTokens()) {
            String word = tokenizer.nextToken();
            word = setting.convertWord(word);
            if (word != null) {
                set.add(word);
            }
        }
    }

    /**
     * Create the trigger.
     *
     * @param conn the database connection
     * @param schema the schema name
     * @param table the table name
     */
    protected static void createTrigger(Connection conn, String schema, String table) throws SQLException {
        createOrDropTrigger(conn, schema, table, true);
    }

    private static void createOrDropTrigger(Connection conn, String schema, String table, boolean create) throws SQLException {
        Statement stat = conn.createStatement();
        String trigger = StringUtils.quoteIdentifier(schema) + "."
                + StringUtils.quoteIdentifier(TRIGGER_PREFIX + table);
        stat.execute("DROP TRIGGER IF EXISTS " + trigger);
        if (create) {
            StringBuilder buff = new StringBuilder("CREATE TRIGGER IF NOT EXISTS ");
            buff.append(trigger).
                append(" AFTER INSERT, UPDATE, DELETE ON ").
                append(StringUtils.quoteIdentifier(schema)).
                append('.').
                append(StringUtils.quoteIdentifier(table)).
                append(" FOR EACH ROW CALL \"").
                append(FullText.FullTextTrigger.class.getName()).
                append("\"");
            stat.execute(buff.toString());
        }
    }

    /**
     * Add the existing data to the index.
     *
     * @param conn the database connection
     * @param schema the schema name
     * @param table the table name
     */
    protected static void indexExistingRows(Connection conn, String schema, String table) throws SQLException {
        FullText.FullTextTrigger existing = new FullText.FullTextTrigger();
        existing.init(conn, schema, null, table, false, Trigger.INSERT);
        String sql = "SELECT * FROM " + StringUtils.quoteIdentifier(schema) + "." + StringUtils.quoteIdentifier(table);
        ResultSet rs = conn.createStatement().executeQuery(sql);
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getObject(i + 1);
            }
            existing.fire(conn, null, row);
        }
    }

    private static String quoteString(String data) {
        if (data.indexOf('\'') < 0) {
            return "'" + data + "'";
        }
        StringBuilder buff = new StringBuilder(data.length() + 2);
        buff.append('\'');
        for (int i = 0; i < data.length(); i++) {
            char ch = data.charAt(i);
            if (ch == '\'') {
                buff.append(ch);
            }
            buff.append(ch);
        }
        buff.append('\'');
        return buff.toString();
    }

    private static void setIgnoreList(FullTextSettings setting, String commaSeparatedList) {
        String[] list = StringUtils.arraySplit(commaSeparatedList, ',', true);
        HashSet<String> set = setting.getIgnoreList();
        for (String word : list) {
            String converted = setting.convertWord(word);
            if (converted != null) {
                set.add(word);
            }
        }
    }

    /**
     * Check if a the indexed columns of a row have changed.
     *
     * @param oldRow the old row
     * @param newRow the new row
     * @param indexColumns the indexed columns
     * @return true if the indexed columns don't match
     */
    protected static boolean hasChanged(Object[] oldRow, Object[] newRow, int[] indexColumns) {
        for (int c : indexColumns) {
            Object o = oldRow[c], n = newRow[c];
            if (o == null) {
                if (n != null) {
                    return true;
                }
            } else if (!o.equals(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Trigger updates the index when a inserting, updating, or deleting a row.
     */
    public static class FullTextTrigger implements Trigger, CloseListener {

        protected FullTextSettings setting;
        protected IndexInfo index;
        protected int[] columnTypes;
        protected PreparedStatement prepInsertWord, prepInsertRow, prepInsertMap;
        protected PreparedStatement prepDeleteRow, prepDeleteMap;
        protected PreparedStatement prepSelectRow;

        /**
         * INTERNAL
         */
        public void init(Connection conn, String schemaName, String triggerName,
                String tableName, boolean before, int type) throws SQLException {
            setting = FullTextSettings.getInstance(conn);
            ArrayList<String> keyList = New.arrayList();
            DatabaseMetaData meta = conn.getMetaData();
            ResultSet rs = meta.getColumns(null,
                    JdbcUtils.escapeMetaDataPattern(schemaName),
                    JdbcUtils.escapeMetaDataPattern(tableName),
                    null);
            ArrayList<String> columnList = New.arrayList();
            while (rs.next()) {
                columnList.add(rs.getString("COLUMN_NAME"));
            }
            columnTypes = new int[columnList.size()];
            index = new IndexInfo();
            index.schema = schemaName;
            index.table = tableName;
            index.columns = new String[columnList.size()];
            columnList.toArray(index.columns);
            rs = meta.getColumns(null,
                    JdbcUtils.escapeMetaDataPattern(schemaName),
                    JdbcUtils.escapeMetaDataPattern(tableName),
                    null);
            for (int i = 0; rs.next(); i++) {
                columnTypes[i] = rs.getInt("DATA_TYPE");
            }
            if (keyList.size() == 0) {
                rs = meta.getPrimaryKeys(null,
                        JdbcUtils.escapeMetaDataPattern(schemaName),
                        tableName);
                while (rs.next()) {
                    keyList.add(rs.getString("COLUMN_NAME"));
                }
            }
            if (keyList.size() == 0) {
                throw new SQLException("No primary key for table " + tableName);
            }
            ArrayList<String> indexList = New.arrayList();
            PreparedStatement prep = conn.prepareStatement(
                    "SELECT ID, COLUMNS FROM " + SCHEMA + ".INDEXES WHERE SCHEMA=? AND TABLE=?");
            prep.setString(1, schemaName);
            prep.setString(2, tableName);
            rs = prep.executeQuery();
            if (rs.next()) {
                index.id = rs.getInt(1);
                String columns = rs.getString(2);
                if (columns != null) {
                    for (String s : StringUtils.arraySplit(columns, ',', true)) {
                        indexList.add(s);
                    }
                }
            }
            if (indexList.size() == 0) {
                indexList.addAll(columnList);
            }
            index.keys = new int[keyList.size()];
            setColumns(index.keys, keyList, columnList);
            index.indexColumns = new int[indexList.size()];
            setColumns(index.indexColumns, indexList, columnList);
            setting.addIndexInfo(index);
            prepInsertWord = conn.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".WORDS(NAME) VALUES(?)");
            prepInsertRow = conn.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".ROWS(HASH, INDEXID, KEY) VALUES(?, ?, ?)");
            prepInsertMap = conn.prepareStatement(
                    "INSERT INTO " + SCHEMA + ".MAP(ROWID, WORDID) VALUES(?, ?)");
            prepDeleteRow = conn.prepareStatement(
                    "DELETE FROM " + SCHEMA + ".ROWS WHERE HASH=? AND INDEXID=? AND KEY=?");
            prepDeleteMap = conn.prepareStatement(
                    "DELETE FROM " + SCHEMA + ".MAP WHERE ROWID=? AND WORDID=?");
            prepSelectRow = conn.prepareStatement(
                    "SELECT ID FROM " + SCHEMA + ".ROWS WHERE HASH=? AND INDEXID=? AND KEY=?");
        }

        /**
         * INTERNAL
         */
        public void fire(Connection conn, Object[] oldRow, Object[] newRow)
                throws SQLException {
            if (oldRow != null) {
                if (newRow != null) {
                    // update
                    if (hasChanged(oldRow, newRow, index.indexColumns)) {
                        delete(setting, oldRow);
                        insert(setting, newRow);
                    }
                } else {
                    // delete
                    delete(setting, oldRow);
                }
            } else if (newRow != null) {
                // insert
                insert(setting, newRow);
            }
        }

        /**
         * INTERNAL
         */
        public void close() {
            setting.removeIndexInfo(index);
        }

        /**
         * INTERNAL
         */
        public void remove() {
            setting.removeIndexInfo(index);
        }

        /**
         * Add a row to the index.
         *
         * @param setting the setting
         * @param row the row
         */
        protected void insert(FullTextSettings setting, Object[] row) throws SQLException {
            String key = getKey(row);
            int hash = key.hashCode();
            prepInsertRow.setInt(1, hash);
            prepInsertRow.setInt(2, index.id);
            prepInsertRow.setString(3, key);
            prepInsertRow.execute();
            ResultSet rs = JdbcUtils.getGeneratedKeys(prepInsertRow);
            rs.next();
            int rowId = rs.getInt(1);
            prepInsertMap.setInt(1, rowId);
            int[] wordIds = getWordIds(setting, row);
            for (int id : wordIds) {
                prepInsertMap.setInt(2, id);
                prepInsertMap.execute();
            }
        }

        /**
         * Delete a row from the index.
         *
         * @param setting the setting
         * @param row the row
         */
        protected void delete(FullTextSettings setting, Object[] row) throws SQLException {
            String key = getKey(row);
            int hash = key.hashCode();
            prepSelectRow.setInt(1, hash);
            prepSelectRow.setInt(2, index.id);
            prepSelectRow.setString(3, key);
            ResultSet rs = prepSelectRow.executeQuery();
            if (rs.next()) {
                int rowId = rs.getInt(1);
                prepDeleteMap.setInt(1, rowId);
                int[] wordIds = getWordIds(setting, row);
                for (int id : wordIds) {
                    prepDeleteMap.setInt(2, id);
                    prepDeleteMap.executeUpdate();
                }
                prepDeleteRow.setInt(1, hash);
                prepDeleteRow.setInt(2, index.id);
                prepDeleteRow.setString(3, key);
                prepDeleteRow.executeUpdate();
            }
        }

        private int[] getWordIds(FullTextSettings setting, Object[] row) throws SQLException {
            HashSet<String> words = New.hashSet();
            for (int i = 0; i < index.indexColumns.length; i++) {
                int idx = index.indexColumns[i];
                int type = columnTypes[idx];
                Object data = row[idx];
                if (type == Types.CLOB && data != null) {
                    Reader reader;
                    if (data instanceof Reader) {
                        reader = (Reader) data;
                    } else {
                        reader = ((Clob) data).getCharacterStream();
                    }
                    addWords(setting, words, reader);
                } else {
                    String string = asString(data, type);
                    addWords(setting, words, string);
                }
            }
            HashMap<String, Integer> allWords = setting.getWordList();
            int[] wordIds = new int[words.size()];
            Iterator<String> it = words.iterator();
            for (int i = 0; it.hasNext(); i++) {
                String word = it.next();
                Integer wId = allWords.get(word);
                int wordId;
                if (wId == null) {
                    prepInsertWord.setString(1, word);
                    prepInsertWord.execute();
                    ResultSet rs = JdbcUtils.getGeneratedKeys(prepInsertWord);
                    rs.next();
                    wordId = rs.getInt(1);
                    allWords.put(word, wordId);
                } else {
                    wordId = wId.intValue();
                }
                wordIds[i] = wordId;
            }
            Arrays.sort(wordIds);
            return wordIds;
        }

        private String getKey(Object[] row) throws SQLException {
            StatementBuilder buff = new StatementBuilder();
            for (int columnIndex : index.keys) {
                buff.appendExceptFirst(" AND ");
                buff.append(StringUtils.quoteIdentifier(index.columns[columnIndex]));
                Object o = row[columnIndex];
                if (o == null) {
                    buff.append(" IS NULL");
                } else {
                    buff.append('=').append(quoteSQL(o, columnTypes[columnIndex]));
                }
            }
            return buff.toString();
        }

    }

}
