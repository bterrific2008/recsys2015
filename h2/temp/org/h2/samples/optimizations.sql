/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */

-------------------------------------------------------------------------------
-- Optimize Count Star
-------------------------------------------------------------------------------
-- This code snippet shows how to quickly get the the number of rows in a table.

-- Initialize the data
CREATE TABLE TEST(ID INT PRIMARY KEY);
INSERT INTO TEST SELECT X FROM SYSTEM_RANGE(1, 1000);

-- Query the count
SELECT COUNT(*) FROM TEST;
--> 1000
;

-- Display the query plan - 'direct lookup' means the index is used
EXPLAIN SELECT COUNT(*) FROM TEST;
--> SELECT COUNT(*)
-->    FROM PUBLIC.TEST /* PUBLIC.TEST_DATA */
-->    /* direct lookup */
;

DROP TABLE TEST;

-------------------------------------------------------------------------------
-- Optimize Distinct
-------------------------------------------------------------------------------
-- This code snippet shows how to quickly get all distinct values
-- of a column for the whole table.

-- Initialize the data
CREATE TABLE TEST(ID INT PRIMARY KEY, TYPE INT);
CALL RAND(0);
--> 0.730967787376657
;
INSERT INTO TEST SELECT X, MOD(X, 10) FROM SYSTEM_RANGE(1, 1000);

-- Create an index on the column TYPE
CREATE INDEX IDX_TEST_TYPE ON TEST(TYPE);

-- Calculate the selectivity - otherwise it will not be optimized
ANALYZE;

-- Query the distinct values
SELECT DISTINCT TYPE FROM TEST ORDER BY TYPE LIMIT 3;
--> 0
--> 1
--> 2
;

-- Display the query plan - 'index sorted' means the index is used to order
EXPLAIN SELECT DISTINCT TYPE FROM TEST ORDER BY TYPE LIMIT 3;
--> SELECT DISTINCT TYPE
-->    FROM PUBLIC.TEST /* PUBLIC.IDX_TEST_TYPE */
-->    ORDER BY 1
-->    LIMIT 3
-->    /* distinct */
-->    /* index sorted */
;

DROP TABLE TEST;

-------------------------------------------------------------------------------
-- Optimize Min Max
-------------------------------------------------------------------------------
-- This code snippet shows how to quickly get the smallest and largest value
-- of a column for each group.

-- Initialize the data
CREATE TABLE TEST(ID INT PRIMARY KEY, VALUE DECIMAL(100, 2));
CALL RAND(0);
--> 0.730967787376657
;
INSERT INTO TEST SELECT X, RAND()*100 FROM SYSTEM_RANGE(1, 1000);

-- Create an index on the column VALUE
CREATE INDEX IDX_TEST_VALUE ON TEST(VALUE);

-- Query the largest and smallest value - this is optimized
SELECT MIN(VALUE), MAX(VALUE) FROM TEST;
--> 0.01 99.89
;

-- Display the query plan - 'direct lookup' means it's optimized
EXPLAIN SELECT MIN(VALUE), MAX(VALUE) FROM TEST;
--> SELECT MIN(VALUE), MAX(VALUE)
-->    FROM PUBLIC.TEST /* PUBLIC.TEST_DATA */
-->    /* direct lookup */
;

DROP TABLE TEST;

-------------------------------------------------------------------------------
-- Optimize Grouped Min Max
-------------------------------------------------------------------------------
-- This code snippet shows how to quickly get the smallest and largest value
-- of a column for each group.

-- Initialize the data
CREATE TABLE TEST(ID INT PRIMARY KEY, TYPE INT, VALUE DECIMAL(100, 2));
CALL RAND(0);
--> 0.730967787376657
;
INSERT INTO TEST SELECT X, MOD(X, 5), RAND()*100 FROM SYSTEM_RANGE(1, 1000);

-- Create an index on the columns TYPE and VALUE
CREATE INDEX IDX_TEST_TYPE_VALUE ON TEST(TYPE, VALUE);

-- Analyze to optimize the DISTINCT part of the query query
ANALYZE;

-- Query the largest and smallest value - this is optimized
SELECT TYPE, (SELECT VALUE FROM TEST T2 WHERE T.TYPE = T2.TYPE
ORDER BY TYPE, VALUE LIMIT 1) MIN
FROM (SELECT DISTINCT TYPE FROM TEST) T ORDER BY TYPE;
--> 0 0.42
--> 1 0.14
--> 2 0.01
--> 3 0.40
--> 4 0.44
;

-- Display the query plan
EXPLAIN SELECT TYPE, (SELECT VALUE FROM TEST T2 WHERE T.TYPE = T2.TYPE
ORDER BY TYPE, VALUE LIMIT 1) MIN
FROM (SELECT DISTINCT TYPE FROM TEST) T ORDER BY TYPE;
--> SELECT TYPE, (SELECT VALUE
-->    FROM PUBLIC.TEST T2 /* PUBLIC.IDX_TEST_TYPE_VALUE: TYPE = T.TYPE */
-->    WHERE T.TYPE = T2.TYPE
-->    ORDER BY =TYPE, 1
-->    LIMIT 1
-->    /* index sorted */) AS MIN
-->    FROM (SELECT DISTINCT TYPE
-->    FROM PUBLIC.TEST /* PUBLIC.IDX_TEST_TYPE_VALUE */
-->    /* distinct */) T /* SELECT DISTINCT TYPE
-->    FROM PUBLIC.TEST /++ PUBLIC.IDX_TEST_TYPE_VALUE ++/
-->    /++ distinct ++/ */
-->    ORDER BY 1
;

DROP TABLE TEST;

-------------------------------------------------------------------------------
-- Optimize Top N --
-------------------------------------------------------------------------------
-- This code snippet shows how to quickly get the smallest and largest N
-- values of a column for the whole table.

-- Initialize the data
CREATE TABLE TEST(ID INT PRIMARY KEY, TYPE INT, VALUE DECIMAL(100, 2));
CALL RAND(0);
--> 0.730967787376657
;
INSERT INTO TEST SELECT X, MOD(X, 100), RAND()*100 FROM SYSTEM_RANGE(1, 1000);

-- Create an index on the column VALUE
CREATE INDEX IDX_TEST_VALUE ON TEST(VALUE);

-- Query the smallest 10 values
SELECT VALUE FROM TEST ORDER BY VALUE LIMIT 3;
--> 0.01
--> 0.14
--> 0.16
;

-- Display the query plan - 'index sorted' means the index is used
EXPLAIN SELECT VALUE FROM TEST ORDER BY VALUE LIMIT 10;
--> SELECT VALUE
-->    FROM PUBLIC.TEST /* PUBLIC.IDX_TEST_VALUE */
-->    ORDER BY 1
-->    LIMIT 10
-->    /* index sorted */
;

-- To optimize getting the largest values, a new descending index is required
CREATE INDEX IDX_TEST_VALUE_D ON TEST(VALUE DESC);

-- Query the largest 10 values
SELECT VALUE FROM TEST ORDER BY VALUE DESC LIMIT 3;
--> 99.89
--> 99.73
--> 99.68
;

-- Display the query plan - 'index sorted' means the index is used
EXPLAIN SELECT VALUE FROM TEST ORDER BY VALUE DESC LIMIT 10;
--> SELECT VALUE
-->    FROM PUBLIC.TEST /* PUBLIC.IDX_TEST_VALUE_D */
-->    ORDER BY 1 DESC
-->    LIMIT 10
-->    /* index sorted */
;

DROP TABLE TEST;
