/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.build.doc;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import org.h2.tools.Csv;
import org.h2.tools.SimpleResultSet;

/**
 * Generates the help.csv file that is included in the jar file.
 */
public class GenerateHelp {

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        new GenerateHelp().run();
    }

    private void run() throws Exception {
        String in = "src/docsrc/help/help.csv";
        String out = "src/main/org/h2/res/help.csv";
        ResultSet rs = Csv.getInstance().read(in, null, null);
        SimpleResultSet rs2 = new SimpleResultSet();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount() - 1;
        for (int i = 0; i < columnCount; i++) {
            rs2.addColumn(meta.getColumnLabel(1 + i), Types.VARCHAR, 0, 0);
        }
        while (rs.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                String s = rs.getString(1 + i);
                if (i == 3) {
                    int dot = s.indexOf('.');
                    if (dot >= 0) {
                        s = s.substring(0, dot + 1);
                    }
                }
                row[i] = s;
            }
            rs2.addRow(row);
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(out));
        writer.write("# Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,\n" +
                "# Version 1.0, and under the Eclipse Public License, Version 1.0\n" +
                "# (http://h2database.com/html/license.html).\n" +
                "# Initial Developer: H2 Group)\n");
        Csv csv = Csv.getInstance();
        csv.setLineSeparator("\n");
        csv.write(writer, rs2);
    }

}
