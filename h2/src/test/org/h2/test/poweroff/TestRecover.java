/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.poweroff;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Date;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.h2.util.IOUtils;
import org.h2.util.New;

/**
 * This standalone test checks if recovery of a database works after power
 * failure.
 */
public class TestRecover {

    private static final String NODE = System.getProperty("test.node", "");
    private static final String DIR = System.getProperty("test.dir", "/temp/db");

    private static final String TEST_DIRECTORY = DIR + "/data" + NODE;
    private static final String BACKUP_DIRECTORY = DIR + "/last";
    private static final String URL = System.getProperty("test.url", "jdbc:h2:" + TEST_DIRECTORY + "/test;PAGE_STORE=TRUE");
    private static final String DRIVER = System.getProperty("test.driver", "org.h2.Driver");

    private Random random;

    // private static final String DIR =
    //     System.getProperty("test.dir", "/temp/derby");
    // private static final String URL =
    //     System.getProperty("test.url",
    //         "jdbc:derby:/temp/derby/data/test;create=true");
    // private static final String DRIVER =
    //     System.getProperty("test.driver",
    //         "org.apache.derby.jdbc.EmbeddedDriver");

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        new TestRecover().runTest();
    }

    private void runTest() throws Exception {
        System.out.println("URL=" + URL);
        System.out.println("backup...");
        new File(TEST_DIRECTORY).mkdirs();
        File backup = backup(TEST_DIRECTORY, BACKUP_DIRECTORY, "data", 10, NODE);
        System.out.println("check consistency...");
        if (!testConsistency()) {
            System.out.println("error! renaming file");
            backup.renameTo(new File(backup.getParentFile(), "error-" + backup.getName()));
        }
        System.out.println("deleting old run...");
        deleteRecursive(new File(TEST_DIRECTORY));
        System.out.println("testing...");
        testLoop();
    }

    private static File backup(String sourcePath, String targetPath, String basePath, int max, String node) throws IOException {
        File root = new File(targetPath);
        if (!root.exists()) {
            root.mkdirs();
        }
        while (true) {
            File oldest = null;
            int count = 0;
            for (File f : root.listFiles()) {
                String name = f.getName();
                if (f.isFile() && name.startsWith("backup") && name.endsWith(".zip")) {
                    count++;
                    if (oldest == null || f.lastModified() < oldest.lastModified()) {
                        oldest = f;
                    }
                }
            }
            if (count < max) {
                break;
            }
            oldest.delete();
        }
        SimpleDateFormat sd = new SimpleDateFormat("yyMMdd-HHmmss");
        String date = sd.format(new Date());
        File zipFile = new File(root, "backup-" + date + "-" + node + ".zip");
        ArrayList<File> list = New.arrayList();
        File base = new File(sourcePath);
        listRecursive(list, base);
        if (list.size() == 0) {
            FileOutputStream out = new FileOutputStream(zipFile);
            out.close();
        } else {
            OutputStream out = null;
            try {
                out = new FileOutputStream(zipFile);
                ZipOutputStream zipOut = new ZipOutputStream(out);
                String baseName = base.getAbsolutePath();
                for (File f : list) {
                    String fileName = f.getAbsolutePath();
                    String entryName = fileName;
                    if (fileName.startsWith(baseName)) {
                        entryName = entryName.substring(baseName.length());
                    }
                    if (entryName.startsWith("\\")) {
                        entryName = entryName.substring(1);
                    }
                    if (!entryName.startsWith("/")) {
                        entryName = "/" + entryName;
                    }
                    ZipEntry entry = new ZipEntry(basePath + entryName);
                    zipOut.putNextEntry(entry);
                    InputStream in = null;
                    try {
                        in = new FileInputStream(fileName);
                        IOUtils.copyAndCloseInput(in, zipOut);
                    } finally {
                        IOUtils.closeSilently(in);
                    }
                    zipOut.closeEntry();
                }
                zipOut.closeEntry();
                zipOut.close();
            } finally {
                IOUtils.closeSilently(out);
            }
        }
        return zipFile;
    }

    private static void listRecursive(List<File> list, File file) throws IOException {
        File[] l = file.listFiles();
        for (int i = 0; l != null && i < l.length; i++) {
            File f = l[i];
            if (f.isDirectory()) {
                listRecursive(list, f);
            } else {
                list.add(f);
            }
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                deleteRecursive(f);
            }
        }
        if (file.exists() && !file.delete()) {
            throw new IOException("Could not delete " + file.getAbsolutePath());
        }
    }

    private void testLoop() throws Exception {
        random = new SecureRandom();
        while (true) {
            runOneTest(random.nextInt());
        }
    }

    private Connection openConnection() throws Exception {
        Class.forName(DRIVER);
        Connection conn = DriverManager.getConnection(URL, "sa", "sa");
        Statement stat = conn.createStatement();
        try {
            stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        } catch (SQLException e) {
            // ignore
        }
        return conn;
    }

    private void closeConnection(Connection conn) {
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
        if (DRIVER.startsWith("org.apache.derby")) {
            try {
                DriverManager.getConnection("jdbc:derby:;shutdown=true");
            } catch (SQLException e) {
                // ignore
            }
            try {
                Driver driver = (Driver) Class.forName(DRIVER).newInstance();
                DriverManager.registerDriver(driver);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void runOneTest(int i) throws Exception {
        Random random = new Random(i);
        Connection conn = openConnection();
        PreparedStatement prepInsert = null;
        PreparedStatement prepDelete = null;
        conn.setAutoCommit(false);
        for (int id = 0;; id++) {
            boolean rollback = random.nextInt(10) == 1;
            int len;
            if (random.nextInt(10) == 1) {
                len = random.nextInt(100) * 2;
            } else {
                len = random.nextInt(2) * 2;
            }
            if (rollback && random.nextBoolean()) {
                // make the length odd
                len++;
            }
//            byte[] data = new byte[len];
//            random.nextBytes(data);
            int op = random.nextInt();
            if (op % 1000000 == 0) {
                closeConnection(conn);
                conn = openConnection();
                conn.setAutoCommit(false);
                prepInsert = null;
                prepDelete = null;
            }
            if (random.nextBoolean()) {
                if (prepInsert == null) {
                    prepInsert = conn.prepareStatement("INSERT INTO TEST(ID, NAME) VALUES(?, ?)");
                }
                prepInsert.setInt(1, id);
                prepInsert.setString(2, "" + len);
                prepInsert.execute();
            } else {
                ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM TEST");
                rs.next();
                int count = rs.getInt(1);
                rs.close();
                if (count > 1000) {
                    if (prepDelete == null) {
                        prepDelete = conn.prepareStatement("DELETE FROM TEST WHERE ROWNUM <= 4");
                    }
                    prepDelete.execute();
                }
            }
            if (rollback) {
                conn.rollback();
            } else {
                conn.commit();
            }
        }
    }

    private boolean testConsistency() {
        FileOutputStream out = null;
        PrintWriter p = null;
        try {
            out = new FileOutputStream(TEST_DIRECTORY + "/result.txt");
            p = new PrintWriter(out);
            p.println("Results");
            p.flush();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(0);
        }
        Connection conn = null;
        try {
            conn = openConnection();
            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
            int max = 0;
            int count = 0;
            while (rs.next()) {
                count++;
                int id = rs.getInt("ID");
                String name = rs.getString("NAME");
                int value = Integer.parseInt(name);
                if (value % 2 != 0) {
                    throw new Exception("unexpected odd entry " + id + " value " + value);
                }
                max = Math.max(max, id);
            }
            rs.close();
            closeConnection(conn);
            System.out.println("max row id: " + max + " rows: " + count);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            t.printStackTrace(p);
            return false;
        } finally {
            if (conn != null) {
                try {
                    closeConnection(conn);
                } catch (Throwable t2) {
                    t2.printStackTrace();
                    t2.printStackTrace(p);
                }
            }
            p.flush();
            p.close();
            IOUtils.closeSilently(out);
        }
    }

}
