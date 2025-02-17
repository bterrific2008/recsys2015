/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.dev.ftp.server;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;
import org.h2.constant.SysProperties;
import org.h2.server.Service;
import org.h2.store.fs.FileSystem;
import org.h2.tools.Server;
import org.h2.util.FileUtils;
import org.h2.util.IOUtils;
import org.h2.util.MathUtils;
import org.h2.util.NetUtils;
import org.h2.util.SortedProperties;
import org.h2.util.Tool;

/**
 * Small FTP Server. Intended for ad-hoc networks in a secure environment.
 * Remote connections are possible.
 * See also http://cr.yp.to/ftp.html http://www.ftpguide.com/
 */
public class FtpServer extends Tool implements Service {

    /**
     * The default port to use for the FTP server.
     * This value is also in the documentation and in the Server javadoc.
     */
    public static final int DEFAULT_PORT = 8021;

    /**
     * The default root directory name used by the FTP server.
     * This value is also in the documentation and in the Server javadoc.
     */
    public static final String DEFAULT_ROOT = "ftp";

    /**
     * The default user name that is allowed to read data.
     * This value is also in the documentation and in the Server javadoc.
     */
    public static final String DEFAULT_READ = "guest";

    /**
     * The default user name that is allowed to read and write data.
     * This value is also in the documentation and in the Server javadoc.
     */
    public static final String DEFAULT_WRITE = "sa";

    /**
     * The default password of the user that is allowed to read and write data.
     * This value is also in the documentation and in the Server javadoc.
     */
    public static final String DEFAULT_WRITE_PASSWORD = "sa";

    static final String TASK_SUFFIX = ".task";

    private ServerSocket serverSocket;
    private int port = DEFAULT_PORT;
    private int openConnectionCount;
    private int maxConnectionCount = 100;

    private SimpleDateFormat dateFormatNew = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
    private SimpleDateFormat dateFormatOld = new SimpleDateFormat("MMM dd  yyyy", Locale.ENGLISH);
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");

    private String root = DEFAULT_ROOT;
    private String writeUserName = DEFAULT_WRITE, writePassword = DEFAULT_WRITE_PASSWORD;
    private String readUserName = DEFAULT_READ;
    private HashMap<String, Process> tasks = new HashMap<String, Process>();

    private FileSystem fs;
    private boolean trace;
    private boolean allowTask;

    private FtpEventListener eventListener;


    /**
     * When running without options, -tcp, -web, -browser and -pg are started.<br />
     * Options are case sensitive. Supported options are:
     * <table>
     * <tr><td>[-help] or [-?]</td>
     * <td>Print the list of options</td></tr>
     * <tr><td>[-web]</td>
     * <td>Start the web server with the H2 Console</td></tr>
     * <tr><td>[-webAllowOthers]</td>
     * <td>Allow other computers to connect</td></tr>
     * <tr><td>[-webPort &lt;port&gt;]</td>
     * <td>The port (default: 8082)</td></tr>
     * <tr><td>[-webSSL]</td>
     * <td>Use encrypted (HTTPS) connections</td></tr>
     * <tr><td>[-browser]</td>
     * <td>Start a browser and open a page to connect to the web server</td></tr>
     * <tr><td>[-tcp]</td>
     * <td>Start the TCP server</td></tr>
     * <tr><td>[-tcpAllowOthers]</td>
     * <td>Allow other computers to connect</td></tr>
     * <tr><td>[-tcpPort &lt;port&gt;]</td>
     * <td>The port (default: 9092)</td></tr>
     * <tr><td>[-tcpSSL]</td>
     * <td>Use encrypted (SSL) connections</td></tr>
     * <tr><td>[-tcpPassword &lt;pwd&gt;]</td>
     * <td>The password for shutting down a TCP server</td></tr>
     * <tr><td>[-tcpShutdown "&lt;url&gt;"]</td>
     * <td>Stop the TCP server; example: tcp://localhost:9094</td></tr>
     * <tr><td>[-tcpShutdownForce]</td>
     * <td>Do not wait until all connections are closed</td></tr>
     * <tr><td>[-pg]</td>
     * <td>Start the PG server</td></tr>
     * <tr><td>[-pgAllowOthers]</td>
     * <td>Allow other computers to connect</td></tr>
     * <tr><td>[-pgPort &lt;port&gt;]</td>
     * <td>The port (default: 5435)</td></tr>
     * <tr><td>[-ftp]</td>
     * <td>Start the FTP server</td></tr>
     * <tr><td>[-ftpPort &lt;port&gt;]</td>
     * <td>The port (default: 8021)</td></tr>
     * <tr><td>[-ftpDir &lt;dir&gt;]</td>
     * <td>The base directory (default: ftp)</td></tr>
     * <tr><td>[-ftpRead &lt;user&gt;]</td>
     * <td>The user name for reading (default: guest)</td></tr>
     * <tr><td>[-ftpWrite &lt;user&gt;]</td>
     * <td>The user name for writing (default: sa)</td></tr>
     * <tr><td>[-ftpWritePassword &lt;p&gt;]</td>
     * <td>The write password (default: sa)</td></tr>
     * <tr><td>[-baseDir &lt;dir&gt;]</td>
     * <td>The base directory for H2 databases; for all servers</td></tr>
     * <tr><td>[-ifExists]</td>
     * <td>Only existing databases may be opened; for all servers</td></tr>
     * <tr><td>[-trace]</td>
     * <td>Print additional trace information; for all servers</td></tr>
     * </table>
     * @h2.resource
     *
     * @param args the command line arguments
     */
    public static void main(String... args) throws SQLException {
        new FtpServer().run(args);
    }

    public void run(String... args) throws SQLException {
        for (int i = 0; args != null && i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            } else if ("-?".equals(arg) || "-help".equals(arg)) {
                showUsage();
                return;
            } else if (arg.startsWith("-ftp")) {
                if ("-ftpPort".equals(arg)) {
                    i++;
                } else if ("-ftpDir".equals(arg)) {
                    i++;
                } else if ("-ftpRead".equals(arg)) {
                    i++;
                } else if ("-ftpWrite".equals(arg)) {
                    i++;
                } else if ("-ftpWritePassword".equals(arg)) {
                    i++;
                } else if ("-ftpTask".equals(arg)) {
                    // no parameters
                } else {
                    throwUnsupportedOption(arg);
                }
            } else if ("-trace".equals(arg)) {
                // no parameters
            } else {
                throwUnsupportedOption(arg);
            }
        }
        Server server = new Server(this, args);
        server.start();
        out.println(server.getStatus());
    }

    public void listen() {
        try {
            while (serverSocket != null) {
                Socket s = serverSocket.accept();
                boolean stop;
                synchronized (this) {
                    openConnectionCount++;
                    stop = openConnectionCount > maxConnectionCount;
                }
                FtpControl c = new FtpControl(s, this, stop);
                c.start();
            }
        } catch (Exception e) {
            traceError(e);
        }
    }

    /**
     * Close a connection. The open connection count will be decremented.
     */
    void closeConnection() {
        synchronized (this) {
            openConnectionCount--;
        }
    }

    /**
     * Create a socket to listen for incoming data connections.
     *
     * @return the server socket
     */
    ServerSocket createDataSocket() throws SQLException {
        return NetUtils.createServerSocket(0, false);
    }

    private void appendFile(StringBuilder buff, String fileName) throws SQLException {
        buff.append(fs.isDirectory(fileName) ? 'd' : '-');
        buff.append('r');
        buff.append(fs.canWrite(fileName) ? 'w' : '-');
        buff.append("------- 1 owner group ");
        String size = String.valueOf(fs.length(fileName));
        for (int i = size.length(); i < 15; i++) {
            buff.append(' ');
        }
        buff.append(size);
        buff.append(' ');
        Date now = new Date(), mod = new Date(fs.getLastModified(fileName));
        String date;
        if (mod.after(now) || Math.abs((now.getTime() - mod.getTime()) / 1000 / 60 / 60 / 24) > 180) {
            synchronized (dateFormatOld) {
                date = dateFormatOld.format(mod);
            }
        } else {
            synchronized (dateFormatNew) {
                date = dateFormatNew.format(mod);
            }
        }
        buff.append(date);
        buff.append(' ');
        buff.append(FileUtils.getFileName(fileName));
        buff.append("\r\n");
    }

    /**
     * Get the last modified date of a date and format it as required by the FTP
     * protocol.
     *
     * @param fileName the file name
     * @return the last modified date of this file
     */
    String formatLastModified(String fileName) {
        synchronized (dateFormat) {
            return dateFormat.format(new Date(fs.getLastModified(fileName)));
        }
    }

    /**
     * Get the full file name of this relative path.
     *
     * @param path the relative path
     * @return the file name
     */
    String getFileName(String path) {
        return root + getPath(path);
    }

    private String getPath(String path) {
        if (path.indexOf("..") > 0) {
            path = "/";
        }
        while (path.startsWith("/") && root.endsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        trace("path: " + path);
        return path;
    }

    /**
     * Get the directory listing for this directory.
     *
     * @param directory the directory to list
     * @param listDirectories if sub-directories should be listed
     * @return the list
     */
    String getDirectoryListing(String directory, boolean listDirectories) throws SQLException {
        StringBuilder buff = new StringBuilder();
        for (String fileName : fs.listFiles(directory)) {
            if (!fs.isDirectory(fileName) || (fs.isDirectory(fileName) && listDirectories)) {
                appendFile(buff, fileName);
            }
        }
        return buff.toString();
    }

    /**
     * Check if this user name is allowed to write.
     *
     * @param userName the user name
     * @param password the password
     * @return true if this user may write
     */
    boolean checkUserPasswordWrite(String userName, String password) {
        return userName.equals(this.writeUserName) && password.equals(this.writePassword);
    }

    /**
     * Check if this user name is allowed to read.
     *
     * @param userName the user name
     * @return true if this user may read
     */
    boolean checkUserPasswordReadOnly(String userName) {
        return userName.equals(this.readUserName);
    }

    public void init(String... args) throws SQLException {
        for (int i = 0; args != null && i < args.length; i++) {
            String a = args[i];
            if ("-ftpPort".equals(a)) {
                port = MathUtils.decodeInt(args[++i]);
            } else if ("-ftpDir".equals(a)) {
                root = FileUtils.normalize(args[++i]);
            } else if ("-ftpRead".equals(a)) {
                readUserName = args[++i];
            } else if ("-ftpWrite".equals(a)) {
                writeUserName = args[++i];
            } else if ("-ftpWritePassword".equals(a)) {
                writePassword = args[++i];
            } else if ("-trace".equals(a)) {
                trace = true;
            } else if ("-log".equals(a) && SysProperties.OLD_COMMAND_LINE_OPTIONS) {
                trace = Tool.readArgBoolean(args, i) == 1;
                i++;
            } else if ("-ftpTask".equals(a)) {
                allowTask = true;
            }
        }
    }

    public String getURL() {
        return "ftp://" + NetUtils.getLocalAddress() + ":" + port;
    }

    public int getPort() {
        return port;
    }

    public void start() throws SQLException {
        fs = FileSystem.getInstance(root);
        root = fs.normalize(root);
        fs.mkdirs(root);
        serverSocket = NetUtils.createServerSocket(port, false);
        port = serverSocket.getLocalPort();
    }

    public void stop() {
        if (serverSocket == null) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            traceError(e);
        }
        serverSocket = null;
    }

    public boolean isRunning(boolean traceError) {
        if (serverSocket == null) {
            return false;
        }
        try {
            Socket s = NetUtils.createLoopbackSocket(port, false);
            s.close();
            return true;
        } catch (IOException e) {
            if (traceError) {
                traceError(e);
            }
            return false;
        }
    }

    public boolean getAllowOthers() {
        return true;
    }

    public String getType() {
        return "FTP";
    }

    public String getName() {
        return "H2 FTP Server";
    }

    /**
     * Write trace information if trace is enabled.
     *
     * @param s the message to write
     */
    void trace(String s) {
        if (trace) {
            System.out.println(s);
        }
    }

    /**
     * Write the stack trace if trace is enabled.
     *
     * @param e the exception
     */
    void traceError(Throwable e) {
        if (trace) {
            e.printStackTrace();
        }
    }

    boolean getAllowTask() {
        return allowTask;
    }

    /**
     * Start a task.
     *
     * @param path the name of the task file
     */
    void startTask(String path) throws IOException {
        stopTask(path);
        if (path.endsWith(".zip.task")) {
            trace("expand: " + path);
            Process p = Runtime.getRuntime().exec("jar -xf " + path, null, new File(root));
            new StreamRedirect(path, p.getInputStream(), null).start();
            return;
        }
        Properties prop = SortedProperties.loadProperties(path);
        String command = prop.getProperty("command");
        String outFile = path.substring(0, path.length() - TASK_SUFFIX.length());
        String errorFile = root + "/" + prop.getProperty("error", outFile + ".err.txt");
        String outputFile = root + "/" + prop.getProperty("output", outFile + ".out.txt");
        trace("start process: " + path + " / " + command);
        Process p = Runtime.getRuntime().exec(command, null, new File(root));
        new StreamRedirect(path, p.getErrorStream(), errorFile).start();
        new StreamRedirect(path, p.getInputStream(), outputFile).start();
        tasks.put(path, p);
    }

    /**
     * This class re-directs an input stream to a file.
     */
    private static class StreamRedirect extends Thread {
        private InputStream in;
        private OutputStream out;
        private String outFile;
        private String processFile;

        StreamRedirect(String processFile, InputStream in, String outFile) {
            this.processFile = processFile;
            this.in = in;
            this.outFile = outFile;
        }

        private void openOutput() {
            if (outFile != null) {
                try {
                    this.out = FileUtils.openFileOutputStream(outFile, false);
                } catch (SQLException e) {
                    // ignore
                }
                outFile = null;
            }
        }

        public void run() {
            while (true) {
                try {
                    int x = in.read();
                    if (x < 0) {
                        break;
                    }
                    openOutput();
                    if (out != null) {
                        out.write(x);
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
            IOUtils.closeSilently(out);
            IOUtils.closeSilently(in);
            new File(processFile).delete();
        }
    }

    /**
     * Stop a running task.
     *
     * @param processName the task name
     */
    void stopTask(String processName) {
        trace("kill process: " + processName);
        Process p = tasks.remove(processName);
        if (p == null) {
            return;
        }
        p.destroy();
    }

    /**
     * Get the file system used by this FTP server.
     *
     * @return the file system
     */
    FileSystem getFileSystem() {
        return fs;
    }

    /**
     * Set the event listener. Only one listener can be registered.
     *
     * @param eventListener the new listener, or null to de-register
     */
    public void setEventListener(FtpEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /**
     * Get the registered event listener.
     *
     * @return the event listener, or null if non is registered
     */
    FtpEventListener getEventListener() {
        return eventListener;
    }

    /**
     * Create a new FTP server, but does not start it yet. Example:
     *
     * <pre>
     * Server server = FtpServer.createFtpServer(null).start();
     * </pre>
     *
     * @param args the argument list
     * @return the server
     */
    public static Server createFtpServer(String... args) throws SQLException {
        return new Server(new FtpServer(), args);
    }

}
