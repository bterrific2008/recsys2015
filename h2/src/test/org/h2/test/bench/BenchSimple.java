/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.bench;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

/**
 * This is a very simple benchmark application. One table is created
 * where rows are inserted, updated, selected (in sequential and random order),
 * and then deleted.
 */
public class BenchSimple implements Bench {

    Database db;
    int records;

    public void init(Database db, int size) throws SQLException {
        this.db = db;
        this.records = size * 60;

        db.start(this, "Init");
        db.openConnection();
        db.dropTable("TEST");
        db.setAutoCommit(false);
        int commitEvery = 1000;
        db.update("CREATE TABLE TEST(ID INT NOT NULL PRIMARY KEY, NAME VARCHAR(255))");
        db.commit();
        PreparedStatement prep = db.prepare("INSERT INTO TEST VALUES(?, ?)");
        for (int i = 0; i < records; i++) {
            prep.setInt(1, i);
            prep.setString(2, "Hello World " + i);
            db.update(prep, "insertTest");
            if (i % commitEvery == 0) {
                db.commit();
            }
        }
        db.commit();
        db.closeConnection();
        db.end();

//        db.start(this, "Open/Close");
//        db.openConnection();
//        db.closeConnection();
//        db.end();

    }

    public void runTest() throws SQLException {
        PreparedStatement prep;
        Random random = db.getRandom();

        db.openConnection();

        db.start(this, "Query (random)");
        prep = db.prepare("SELECT * FROM TEST WHERE ID=?");
        for (int i = 0; i < records; i++) {
            prep.setInt(1, random.nextInt(records));
            db.queryReadResult(prep);
        }
        db.end();

        db.start(this, "Query (sequential)");
        prep = db.prepare("SELECT * FROM TEST WHERE ID=?");
        for (int i = 0; i < records; i++) {
            prep.setInt(1, i);
            db.queryReadResult(prep);
        }
        db.end();

        db.start(this, "Update (random)");
        prep = db.prepare("UPDATE TEST SET NAME=? WHERE ID=?");
        for (int i = 0; i < records; i++) {
            prep.setString(1, "Hallo Welt");
            prep.setInt(2, i);
            db.update(prep, "updateTest");
        }
        db.end();

        db.start(this, "Delete (sequential)");
        prep = db.prepare("DELETE FROM TEST WHERE ID=?");
        // delete only 50%
        for (int i = 0; i < records; i += 2) {
            prep.setInt(1, i);
            db.update(prep, "deleteTest");
        }
        db.end();

        db.closeConnection();

        db.openConnection();
        prep = db.prepare("SELECT * FROM TEST WHERE ID=?");
        for (int i = 0; i < records; i++) {
            prep.setInt(1, random.nextInt(records));
            db.queryReadResult(prep);
        }
        db.logMemory(this, "Memory Usage");
        db.closeConnection();

    }

    public String getName() {
        return "Simple";
    }

}
