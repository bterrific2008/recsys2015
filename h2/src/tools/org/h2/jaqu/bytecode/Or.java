/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.jaqu.bytecode;

import org.h2.jaqu.Query;
import org.h2.jaqu.SQLStatement;
import org.h2.jaqu.Token;

/**
 * An OR expression.
 */
public class Or implements Token {

    private final Token left, right;

    private Or(Token left, Token right) {
        this.left = left;
        this.right = right;
    }

    static Or get(Token left, Token right) {
        return new Or(left, right);
    }

    public <T> void appendSQL(SQLStatement stat, Query<T> query) {
        // untested
        left.appendSQL(stat, query);
        stat.appendSQL(" OR ");
        right.appendSQL(stat, query);
    }

}
