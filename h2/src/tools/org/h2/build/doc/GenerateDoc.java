/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.build.doc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.h2.bnf.Bnf;
import org.h2.engine.Constants;
import org.h2.server.web.PageParser;
import org.h2.util.IOUtils;
import org.h2.util.JdbcUtils;
import org.h2.util.StringUtils;

/**
 * This application generates sections of the documentation
 * by converting the built-in help section (INFORMATION_SCHEMA.HELP)
 * to cross linked html.
 */
public class GenerateDoc {

    private String inDir = "src/docsrc/html";
    private String inHelp = "src/docsrc/help/help.csv";
    private String outDir = "docs/html";
    private Connection conn;
    private HashMap<String, Object> session = new HashMap<String, Object>();
    private Bnf bnf;

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        new GenerateDoc().run(args);
    }

    private void run(String... args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-in")) {
                inDir = args[++i];
            } else if (args[i].equals("-out")) {
                outDir = args[++i];
            }
        }
        Class.forName("org.h2.Driver");
        conn = DriverManager.getConnection("jdbc:h2:mem:");
        new File(outDir).mkdirs();
        new RailroadImages().run(outDir + "/images");
        bnf = Bnf.getInstance(null);
        bnf.linkStatements();
        session.put("version", Constants.getVersion());
        session.put("versionDate", Constants.BUILD_DATE);
        session.put("stableVersion", Constants.getVersionStable());
        session.put("stableVersionDate", Constants.BUILD_DATE_STABLE);
        // String help = "SELECT * FROM INFORMATION_SCHEMA.HELP WHERE SECTION";
        String help = "SELECT ROWNUM ID, * FROM CSVREAD('" + inHelp + "') WHERE SECTION ";
        map("commands", help + "LIKE 'Commands%' ORDER BY ID", true);
        map("commandsDML", help + "= 'Commands (DML)' ORDER BY ID", false);
        map("commandsDDL", help + "= 'Commands (DDL)' ORDER BY ID", false);
        map("commandsOther", help + "= 'Commands (Other)' ORDER BY ID", false);
        map("otherGrammar", help + "= 'Other Grammar' ORDER BY ID", true);
        map("functionsAggregate", help + "= 'Functions (Aggregate)' ORDER BY ID", false);
        map("functionsNumeric", help + "= 'Functions (Numeric)' ORDER BY ID", false);
        map("functionsString", help + "= 'Functions (String)' ORDER BY ID", false);
        map("functionsTimeDate", help + "= 'Functions (Time and Date)' ORDER BY ID", false);
        map("functionsSystem", help + "= 'Functions (System)' ORDER BY ID", false);
        map("functionsAll", help + "LIKE 'Functions%' ORDER BY SECTION, ID", true);
        map("dataTypes", help + "LIKE 'Data Types%' ORDER BY SECTION, ID", true);
        map("informationSchema", "SELECT TABLE_NAME TOPIC, GROUP_CONCAT(COLUMN_NAME "
                + "ORDER BY ORDINAL_POSITION SEPARATOR ', ') SYNTAX FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA='INFORMATION_SCHEMA' GROUP BY TABLE_NAME ORDER BY TABLE_NAME", false);
        processAll("");
        conn.close();
    }

    private void processAll(String dir) throws Exception {
        if (dir.endsWith(".svn")) {
            return;
        }
        File[] list = new File(inDir + "/" + dir).listFiles();
        for (File file : list) {
            if (file.isDirectory()) {
                processAll(dir + file.getName());
            } else {
                process(dir, file.getName());
            }
        }
    }

    private void process(String dir, String fileName) throws Exception {
        String inFile = inDir + "/" + dir + "/" + fileName;
        String outFile = outDir + "/" + dir + "/" + fileName;
        new File(outFile).getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(outFile);
        FileInputStream in = new FileInputStream(inFile);
        byte[] bytes = IOUtils.readBytesAndClose(in, 0);
        if (fileName.endsWith(".html")) {
            String page = new String(bytes);
            page = PageParser.parse(page, session);
            bytes = page.getBytes();
        }
        out.write(bytes);
        out.close();
    }

    private void map(String key, String sql, boolean railroads) throws Exception {
        ResultSet rs = null;
        Statement stat = null;
        try {
            stat = conn.createStatement();
            rs = stat.executeQuery(sql);
            ArrayList<HashMap<String, String>> list = new ArrayList<HashMap<String, String>>();
            while (rs.next()) {
                HashMap<String, String> map = new HashMap<String, String>();
                ResultSetMetaData meta = rs.getMetaData();
                for (int i = 0; i < meta.getColumnCount(); i++) {
                    String k = StringUtils.toLowerEnglish(meta.getColumnLabel(i + 1));
                    String value = rs.getString(i + 1);
                    value = value.trim();
                    map.put(k, PageParser.escapeHtml(value));
                }
                String topic = rs.getString("TOPIC");
                String syntax = rs.getString("SYNTAX").trim();
                if (railroads) {
                    String railroad = bnf.getRailroadHtml(syntax);
                    map.put("railroad", railroad);
                }
                syntax = bnf.getSyntaxHtml(syntax);
                map.put("syntax", syntax);

                // remove newlines in the regular text
                String text = map.get("text");
                if (text != null) {
                    // text is enclosed in <p> .. </p> so this works.
                    text = StringUtils.replaceAll(text, "<br /><br />", "</p><p>");
                    text = StringUtils.replaceAll(text, "<br />", " ");
                    map.put("text", text);
                }

                String link = topic.toLowerCase();
                link = StringUtils.replaceAll(link, " ", "_");
                // link = StringUtils.replaceAll(link, "_", "");
                link = StringUtils.replaceAll(link, "@", "_");
                map.put("link", StringUtils.urlEncode(link));

                list.add(map);
            }
            session.put(key, list);
            int div = 3;
            int part = (list.size() + div - 1) / div;
            for (int i = 0, start = 0; i < div; i++, start += part) {
                List<HashMap<String, String>> listThird = list.subList(start, Math.min(start + part, list.size()));
                session.put(key + "-" + i, listThird);
            }
        } finally {
            JdbcUtils.closeSilently(rs);
            JdbcUtils.closeSilently(stat);
        }
    }
}
