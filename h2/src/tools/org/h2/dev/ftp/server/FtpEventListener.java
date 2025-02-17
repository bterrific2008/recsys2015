/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.dev.ftp.server;

/**
 * Event listener for the FTP Server.
 */
public interface FtpEventListener {

    /**
     * Called before the given command is processed.
     *
     * @param event the event
     */
    void beforeCommand(FtpEvent event);

    /**
     * Called after the command has been processed.
     *
     * @param event the event
     */
    void afterCommand(FtpEvent event);

    /**
     * Called when an unsupported command is processed.
     * This method is called after beforeCommand.
     *
     * @param event the event
     */
    void onUnsupportedCommand(FtpEvent event);
}
