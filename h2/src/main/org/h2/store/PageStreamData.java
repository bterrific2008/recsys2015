/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import java.sql.SQLException;
import org.h2.engine.Session;

/**
 * A data page of a stream. The format is:
 * <ul>
 * <li>page type: byte (0)</li>
 * <li>checksum: short (1-2)</li>
 * <li>the trunk page id: int (3-6)</li>
 * <li>log key: int (7-10)</li>
 * <li>data (11-)</li>
 * </ul>
 */
public class PageStreamData extends Page {

    private static final int DATA_START = 11;

    private final PageStore store;
    private int trunk;
    private int logKey;
    private Data data;
    private int remaining;

    private PageStreamData(PageStore store, int pageId, int trunk, int logKey) {
        setPos(pageId);
        this.store = store;
        this.trunk = trunk;
        this.logKey = logKey;
    }

    /**
     * Read a stream data page.
     *
     * @param store the page store
     * @param data the data
     * @param pageId the page id
     * @return the page
     */
    static PageStreamData read(PageStore store, Data data, int pageId) {
        PageStreamData p = new PageStreamData(store, pageId, 0, 0);
        p.data = data;
        p.read();
        return p;
    }

    /**
     * Create a new stream trunk page.
     *
     * @param store the page store
     * @param pageId the page id
     * @param trunk the trunk page
     * @param logKey the log key
     * @return the page
     */
    static PageStreamData create(PageStore store, int pageId, int trunk, int logKey) {
        return new PageStreamData(store, pageId, trunk, logKey);
    }

    /**
     * Read the page from the disk.
     */
    private void read() {
        data.reset();
        data.readByte();
        data.readShortInt();
        trunk = data.readInt();
        logKey = data.readInt();
    }

    public int getByteCount(DataPage dummy) {
        return store.getPageSize();
    }

    /**
     * Write the header data.
     */
    void initWrite() {
        data = store.createData();
        data.writeByte((byte) Page.TYPE_STREAM_DATA);
        data.writeShortInt(0);
        data.writeInt(trunk);
        data.writeInt(logKey);
        remaining = store.getPageSize() - data.length();
    }

    /**
     * Write the data to the buffer.
     *
     * @param buff the source data
     * @param off the offset in the source buffer
     * @param len the number of bytes to write
     * @return the number of bytes written
     */
    int write(byte[] buff, int offset, int len) {
        int max = Math.min(remaining, len);
        data.write(buff, offset, max);
        remaining -= max;
        return max;
    }

    public void write(DataPage buff) throws SQLException {
        store.writePage(getPos(), data);
    }

    /**
     * Get the number of bytes that fit in a page.
     *
     * @param pageSize the page size
     * @return the number of bytes
     */
    static int getCapacity(int pageSize) {
        return pageSize - DATA_START;
    }

    /**
     * Read the next bytes from the buffer.
     *
     * @param buff the target buffer
     * @param off the offset in the target buffer
     * @param len the number of bytes to read
     */
    void read(byte[] buff, int off, int len) {
        data.read(buff, off, len);
    }

    /**
     * Get the number of remaining data bytes of this page.
     *
     * @return the remaining byte count
     */
    int getRemaining() {
        return remaining;
    }

    /**
     * Get the estimated memory size.
     *
     * @return number of double words (4 bytes)
     */
    public int getMemorySize() {
        return store.getPageSize() >> 2;
    }

    /**
     * Reset the index.
     */
    void initRead() {
        data.setPos(DATA_START);
        remaining = store.getPageSize() - DATA_START;
    }

    public void moveTo(Session session, int newPos) {
        // not required
    }

    int getLogKey() {
        return logKey;
    }

    public String toString() {
        return "[" + getPos() + "] stream data pos:" + data.length() + " remaining:" + remaining;
    }

    public boolean canRemove() {
        return true;
    }

}