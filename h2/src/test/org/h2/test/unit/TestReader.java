/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import org.h2.dev.util.ReaderInputStream;
import org.h2.test.TestBase;
import org.h2.util.IOUtils;

/**
 * Tests the stream to UTF-8 reader conversion.
 */
public class TestReader extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    public void test() throws Exception {
        String s = "\u00ef\u00f6\u00fc";
        StringReader r = new StringReader(s);
        InputStream in = new ReaderInputStream(r);
        byte[] buff = IOUtils.readBytesAndClose(in, 0);
        InputStream in2 = new ByteArrayInputStream(buff);
        Reader r2 = IOUtils.getReader(in2);
        String s2 = IOUtils.readStringAndClose(r2, Integer.MAX_VALUE);
        assertEquals(s, s2);
    }

}
