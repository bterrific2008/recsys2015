/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.build.doc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

import org.h2.engine.Constants;
import org.h2.util.StringUtils;

/**
 * This application merges the html documentation to one file
 * (onePage.html), so that the PDF document can be created.
 */
public class MergeDocs {

    private String baseDir = "docs/html";

    /**
     * This method is called when executing this application from the command
     * line.
     *
     * @param args the command line parameters
     */
    public static void main(String... args) throws Exception {
        new MergeDocs().run();
    }

    private void run() throws Exception {
        // the order of pages is important here
        String[] pages = { "quickstart.html", "installation.html", "tutorial.html", "features.html",
                "performance.html", "advanced.html", "grammar.html", "functions.html", "datatypes.html", "build.html",
                "history.html", "faq.html" };
        StringBuilder buff = new StringBuilder();
        for (String fileName : pages) {
            String text = getContent(fileName);
            for (String page : pages) {
                text = StringUtils.replaceAll(text, page + "#", "#");
            }
            text = disableRailroads(text);
            text = removeHeaderFooter(fileName, text);
            buff.append(text);
        }
        String finalText = buff.toString();
        File output = new File(baseDir, "onePage.html");
        PrintWriter writer = new PrintWriter(new FileWriter(output));
        writer.println("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" /><title>");
        writer.println("H2 Documentation");
        writer.println("</title><link rel=\"stylesheet\" type=\"text/css\" href=\"stylesheetPdf.css\" /></head><body>");
        writer.println("<h1>H2 Database Engine</h1>");
        writer.println("<p>Version " + Constants.getFullVersion() + "</p>");
        writer.println(finalText);
        writer.println("</body></html>");
        writer.close();
    }

    private String disableRailroads(String text) {
        text = StringUtils.replaceAll(text, "<!-- railroad-start -->", "<!-- railroad-start ");
        text = StringUtils.replaceAll(text, "<!-- railroad-end -->", " railroad-end -->");
        text = StringUtils.replaceAll(text, "<!-- syntax-start", "<!-- syntax-start -->");
        text = StringUtils.replaceAll(text, "syntax-end -->", "<!-- syntax-end -->");
        return text;
    }

    private String removeHeaderFooter(String fileName, String text) {
        // String start = "<body";
        // String end = "</body>";

        String start = "<!-- } -->";
        String end = "<!-- [close] { --></div></td></tr></table><!-- } --><!-- analytics --></body></html>";

        int idx = text.indexOf(end);
        if (idx < 0) {
            throw new RuntimeException("Footer not found in file " + fileName);
        }
        text = text.substring(0, idx);
        idx = text.indexOf(start) + start.length();
        text = text.substring(idx + 1);
        return text;
    }

    private String getContent(String fileName) throws Exception {
        File file = new File(baseDir, fileName);
        int length = (int) file.length();
        char[] data = new char[length];
        FileReader reader = new FileReader(file);
        int off = 0;
        while (length > 0) {
            int len = reader.read(data, off, length);
            off += len;
            length -= len;
        }
        reader.close();
        String s = new String(data);
        return s;
    }
}
