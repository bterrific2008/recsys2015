/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.build.doc;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.h2.build.BuildBase;

/**
 * The spell checker makes sure that each word used in the source code
 * is spelled correctly, by comparing the words with a word list.
 * Camel case and uppercase words are checked as well.
 * HTTP links are not checked; however they may not end with a dot.
 */
public class SpellChecker {

    private static final String[] SUFFIX = new String[] { "html", "java", "sql", "txt", "xml", "jsp", "css", "bat",
            "csv", "xml", "js", "Driver", "properties", "task", "MF", "sh", "" };
    private static final String[] IGNORE = new String[] { "dev", "nsi", "gif", "png", "odg", "ico", "sxd", "zip",
            "bz2", "rc", "layout", "res", "dll", "jar", "svg" };
    private static final String DELIMITERS = " \n.();-\"=,*/{}_<>+\r:'@[]&\\!#|?$^%~`\t";
    private static final String PREFIX_IGNORE = "abc";
    private static final String[] IGNORE_FILES = {"mainWeb.html", "pg_catalog.sql"};

    private HashSet<String> dictionary = new HashSet<String>();
    private HashSet<String> used = new HashSet<String>();
    private HashMap<String, Integer> unknown = new HashMap<String, Integer>();
    private boolean debug;
    private boolean printDictionary;
    private boolean addToDictionary;
    private int errorCount;
    private int contextCount;

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws IOException {
        String dir = "src";
        new SpellChecker().run("tools/org/h2/build/doc/dictionary.txt", dir);
    }

    private void run(String dictionary, String dir) throws IOException {
        process(new File(dir + "/" + dictionary));
        process(new File(dir));
        if (printDictionary) {
            System.out.println("USED WORDS");
            String[] list = new String[used.size()];
            used.toArray(list);
            Arrays.sort(list);
            StringBuilder buff = new StringBuilder();
            for (String s : list) {
                if (buff.length() > 0) {
                    if (buff.length() + s.length() > 80) {
                        System.out.println(buff.toString());
                        buff.setLength(0);
                    } else {
                        buff.append(' ');
                    }
                }
                buff.append(s);
            }
            System.out.println(buff.toString());
        }
        if (unknown.size() > 0) {
            System.out.println();
            System.out.println("UNKNOWN WORDS");
            for (String s : unknown.keySet()) {
                // int count = unknown.get(s);
                System.out.print(s + " ");
                errorCount++;
            }
            System.out.println();
            System.out.println();
        }
        if (errorCount > 0) {
            throw new IOException(errorCount + " error found");
        }
    }

    private void process(File file) throws IOException {
        String name = file.getName();
        if (name.endsWith(".svn") || name.endsWith(".DS_Store")) {
            return;
        }
        if (name.startsWith("_") && name.indexOf("_en") < 0) {
            return;
        }
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                process(f);
            }
        } else {
            String fileName = file.getAbsolutePath();
            int idx = fileName.lastIndexOf('.');
            String suffix;
            if (idx < 0) {
                suffix = "";
            } else {
                suffix = fileName.substring(idx + 1);
            }
            for (String s : IGNORE) {
                if (s.equals(suffix)) {
                    return;
                }
            }
            for (String ignoreFile : IGNORE_FILES) {
                if (fileName.endsWith(ignoreFile)) {
                    return;
                }
            }
            boolean ok = false;
            for (String s : SUFFIX) {
                if (s.equals(suffix)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new IOException("Unsupported suffix: " + suffix + " for file: " + fileName);
            }
            String text = new String(BuildBase.readFile(file));
            if (fileName.endsWith("dictionary.txt")) {
                addToDictionary = true;
            } else {
                addToDictionary = false;
            }
            scan(fileName, text);
        }
    }

    private void scan(String fileName, String text) {
        HashSet<String> notFound = new HashSet<String>();
        text = removeLinks(fileName, text);
        StringTokenizer tokenizer = new StringTokenizer(text, DELIMITERS);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            char first = token.charAt(0);
            if (Character.isDigit(first)) {
                continue;
            }
            if (!addToDictionary && debug) {
                System.out.print(token + " ");
            }
            scanCombinedToken(notFound, token);
            if (!addToDictionary && debug) {
                System.out.println();
            }
        }
        if (notFound.isEmpty()) {
            return;
        }
        if (notFound.size() > 0) {
            System.out.println("file: " + fileName);
            for (String s : notFound) {
                System.out.print(s + " ");
            }
            System.out.println();
        }
    }

    private String removeLinks(String fileName, String text) {
        StringBuilder buff = new StringBuilder(text.length());
        int pos = 0, last = 0;
        while (true) {
            pos = text.indexOf("http://", pos);
            if (pos < 0) {
                break;
            }
            int start = pos;
            buff.append(text.substring(last, start));
            pos += "http://".length();
            while (true) {
                char c = text.charAt(pos);
                if (!Character.isJavaIdentifierPart(c) && ".#/?&=%+_-:".indexOf(c) < 0) {
                    break;
                }
                pos++;
            }
            String link = text.substring(start, pos);
            if (link.endsWith(".")) {
                System.out.println("Link ending with dot in " + fileName + ": " + link);
                errorCount++;
            }
            last = pos;
        }
        buff.append(text.substring(last));
        String changed = buff.toString();
        return changed;
    }

    private void scanCombinedToken(HashSet<String> notFound, String token) {
        for (int i = 1; i < token.length(); i++) {
            char charLeft = token.charAt(i - 1);
            char charRight = token.charAt(i);
            if (Character.isLowerCase(charLeft) && Character.isUpperCase(charRight)) {
                scanToken(notFound, token.substring(0, i));
                token = token.substring(i);
                i = 1;
            } else if (Character.isUpperCase(charLeft) && Character.isLowerCase(charRight)) {
                scanToken(notFound, token.substring(0, i - 1));
                token = token.substring(i - 1);
                i = 1;
            }
        }
        scanToken(notFound, token);
    }

    private void scanToken(HashSet<String> notFound, String token) {
        if (token.length() < 3) {
            return;
        }
        if (contextCount > 0) {
            // System.out.println(token);
            contextCount--;
        }
        while (true) {
            char last = token.charAt(token.length() - 1);
            if (!Character.isDigit(last)) {
                break;
            }
            token = token.substring(0, token.length() - 1);
        }
        if (token.length() < 3) {
            return;
        }
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                return;
            }
        }
        token = token.toLowerCase();
        if (!addToDictionary && debug) {
            System.out.print(token + " ");
        }
        if (token.startsWith(PREFIX_IGNORE)) {
            return;
        }
        if (addToDictionary) {
            dictionary.add(token);
        } else {
            if (!dictionary.contains(token)) {
                notFound.add(token);
                increment(unknown, token);
            } else {
                used.add(token);
            }
        }
    }

    private void increment(HashMap<String, Integer> map, String key) {
        Integer value = map.get(key);
        value = Integer.valueOf(value == null ? 0 : value + 1);
        map.put(key, value);
        contextCount = 10;
    }

}
