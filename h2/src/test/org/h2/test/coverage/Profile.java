/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.coverage;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.Writer;

/**
 * The class used at runtime to measure the code usage and performance.
 */
public class Profile extends Thread {
    private static final boolean LIST_UNVISITED = false;
    private static final boolean TRACE = false;
    private static final Profile MAIN = new Profile();
    private static int top = 15;
    private int[] count;
    private int[] time;
    private boolean stop;
    private int maxIndex;
    private int lastIndex;
    private long lastTime;
    private BufferedWriter trace;

    private Profile() {
        FileReader reader = null;
        try {
            reader = new FileReader("profile.txt");
            LineNumberReader r = new LineNumberReader(reader);
            while (r.readLine() != null) {
                // nothing - just count lines
            }
            maxIndex = r.getLineNumber();
            count = new int[maxIndex];
            time = new int[maxIndex];
            lastTime = System.currentTimeMillis();
            Runtime.getRuntime().addShutdownHook(this);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        } finally {
            closeSilently(reader);
        }
    }

    static {
        try {
            String s = System.getProperty("profile.top");
            if (s != null) {
                top = Integer.parseInt(s);
            }
        } catch (Throwable e) {
            // ignore SecurityExceptions
        }
    }

    /**
     * This method is called by an instrumented application whenever a line of
     * code is executed.
     *
     * @param i the line number that is executed
     */
    public static void visit(int i) {
        MAIN.addVisit(i);
    }

    public void run() {
        list();
    }

    /**
     * Start collecting data.
     */
    public static void startCollecting() {
        MAIN.stop = false;
        MAIN.lastTime = System.currentTimeMillis();
    }

    /**
     * Stop collecting data.
     */
    public static void stopCollecting() {
        MAIN.stop = true;
    }

    /**
     * List all captured data.
     */
    public static void list() {
        if (MAIN.lastIndex == 0) {
            // don't list anything if no statistics collected
            return;
        }
        try {
            MAIN.listUnvisited();
            MAIN.listTop("MOST CALLED", MAIN.count, top);
            MAIN.listTop("MOST TIME USED", MAIN.time, top);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeSilently(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void closeSilently(Writer writer) {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void addVisit(int i) {
        if (stop) {
            return;
        }
        long now = System.currentTimeMillis();
        if (TRACE) {
            if (trace != null) {
                int duration = (int) (now - lastTime);
                try {
                    trace.write(i + "\t" + duration + "\r\n");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
        }
        count[i]++;
        time[lastIndex] += (int) (now - lastTime);
        lastTime = now;
        lastIndex = i;
    }

    private void listUnvisited() throws IOException {
        printLine('=');
        print("NOT COVERED");
        printLine('-');
        FileReader reader = null;
        FileWriter fileWriter = null;
        try {
            reader = new FileReader("profile.txt");
            LineNumberReader r = new LineNumberReader(reader);
            fileWriter = new FileWriter("notCovered.txt");
            BufferedWriter writer = new BufferedWriter(fileWriter);
            int unvisited = 0;
            int unvisitedThrow = 0;
            for (int i = 0; i < maxIndex; i++) {
                String line = r.readLine();
                if (count[i] == 0) {
                    if (!line.endsWith("throw")) {
                        writer.write(line + "\r\n");
                        if (LIST_UNVISITED) {
                            print(line + "\r\n");
                        }
                        unvisited++;
                    } else {
                        unvisitedThrow++;
                    }
                }
            }
            int percent = 100 * unvisited / maxIndex;
            print("Not covered: " + percent + " % " + " (" + unvisited + " of " + maxIndex + "; throw="
                    + unvisitedThrow + ")");
        } finally {
            closeSilently(fileWriter);
            closeSilently(reader);
        }
    }

    private void listTop(String title, int[] list, int max) throws IOException {
        printLine('-');
        int total = 0;
        int totalLines = 0;
        for (int j = 0; j < maxIndex; j++) {
            int l = list[j];
            if (l > 0) {
                total += list[j];
                totalLines++;
            }
        }
        if (max == 0) {
            max = totalLines;
        }
        print(title);
        print("Total: " + total);
        printLine('-');
        String[] text = new String[max];
        int[] index = new int[max];
        for (int i = 0; i < max; i++) {
            int big = list[0];
            int bigIndex = 0;
            for (int j = 1; j < maxIndex; j++) {
                int l = list[j];
                if (l > big) {
                    big = l;
                    bigIndex = j;
                }
            }
            list[bigIndex] = -(big + 1);
            index[i] = bigIndex;
        }
        FileReader reader = null;
        try {
            reader = new FileReader("profile.txt");
            LineNumberReader r = new LineNumberReader(reader);
            for (int i = 0; i < maxIndex; i++) {
                String line = r.readLine();
                int k = list[i];
                if (k < 0) {
                    k = -(k + 1);
                    list[i] = k;
                    for (int j = 0; j < max; j++) {
                        if (index[j] == i) {
                            int percent = 100 * k / total;
                            text[j] = k + " " + percent + "%: " + line;
                        }
                    }
                }
            }
            for (int i = 0; i < max; i++) {
                print(text[i]);
            }
        } finally {
            closeSilently(reader);
        }
    }

    private void print(String s) {
        System.out.println(s);
    }

    private void printLine(char c) {
        for (int i = 0; i < 60; i++) {
            System.out.print(c);
        }
        print("");
    }
}
