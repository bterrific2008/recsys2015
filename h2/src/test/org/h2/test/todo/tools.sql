/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */

-- update all rows in all tables
select 'update ' || table_schema || '.' || table_name || ' set ' || column_name || '=' || column_name || ';'
from information_schema.columns where ORDINAL_POSITION = 1 and table_schema <> 'INFORMATION_SCHEMA';
