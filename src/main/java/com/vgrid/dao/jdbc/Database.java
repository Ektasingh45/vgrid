package com.vgrid.dao.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens the JDBC connection to MySQL and runs transactions.
 *
 * A desktop app has one user doing one thing at a time, so it reuses a single connection
 * instead of opening a new one for every query (much faster). A server with many users
 * would use a connection pool such as HikariCP instead.
 */
public final class Database {

    /** A piece of JDBC work that may throw SQLException. */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** The open connection, reconnecting automatically if MySQL was restarted. */
    public synchronized Connection connection() throws SQLException {
        if (connection == null || !connection.isValid(2)) {
            connection = DriverManager.getConnection(url, user, password);
        }
        return connection;
    }

    /**
     * Runs the work as ONE transaction: if every statement succeeds the changes are committed,
     * if anything fails (SQL error or a ConflictException) everything is rolled back.
     */
    public synchronized <T> T inTransaction(SqlWork<T> work) throws SQLException {
        Connection c = connection();
        c.setAutoCommit(false);
        try {
            T result = work.run(c);
            c.commit();
            return result;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Runs every statement of an .sql file from the classpath (statements end with ';'). */
    public void runScript(String resource) throws SQLException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new SQLException(resource + " not found on the classpath");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Could not read " + resource, e);
        }
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            int comment = line.indexOf("--");
            withoutComments.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        try (Statement statement = connection().createStatement()) {
            for (String sql : withoutComments.toString().split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    /** "jdbc:mysql://localhost:3306/vgrid?..." -> "localhost:3306/vgrid" */
    public String displayName() {
        String text = url.replaceFirst("^jdbc:\\w+://", "");
        int query = text.indexOf('?');
        return query >= 0 ? text.substring(0, query) : text;
    }
}
