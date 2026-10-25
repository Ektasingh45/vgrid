package com.vgrid.data;

import com.vgrid.dao.DataLayer;
import com.vgrid.dao.jdbc.MySqlDataLayer;

/**
 * Chooses where the app's data is stored, based on db.properties:
 *  - data.mode=mysql  (default): connect to MySQL; if that fails, fall back to offline mode,
 *  - data.mode=memory: always use offline mode (in memory, nothing saved).
 */
public final class DataLayerFactory {

    public enum Status { MYSQL, OFFLINE_CHOSEN, OFFLINE_FALLBACK }

    /** The data layer plus what happened, so the login screen can explain it. */
    public record Result(DataLayer layer, Status status, String problem) {
    }

    private DataLayerFactory() {
    }

    /** Slow when MySQL is unreachable (connection timeout), so call it from a background thread. */
    public static Result open(AppConfig config) {
        if (config.memoryMode()) {
            return new Result(new DemoDataStore(), Status.OFFLINE_CHOSEN, null);
        }
        try {
            return new Result(MySqlDataLayer.connect(config.dbUrl(), config.dbUser(), config.dbPassword()),
                    Status.MYSQL, null);
        } catch (Exception e) {
            return new Result(new DemoDataStore(), Status.OFFLINE_FALLBACK, explain(e, config.dbUrl()));
        }
    }

    /** Turns the JDBC error into a sentence a user can act on. */
    private static String explain(Exception e, String url) {
        StringBuilder all = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append(' ');
        }
        String text = all.toString();
        if (text.contains("Communications link failure") || text.contains("Connection refused")) {
            return "MySQL is not running at " + url.replaceFirst("^jdbc:\\w+://", "").replaceFirst("[/?].*$", "") + ".";
        }
        if (text.contains("Access denied")) {
            return "MySQL refused the username or password in " + AppConfig.FILE + ".";
        }
        if (text.contains("No suitable driver")) {
            return "The MySQL JDBC driver is missing (lib/mysql-connector-j-8.4.0.jar).";
        }
        String first = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().lines().findFirst().orElse("");
        return first.length() > 200 ? first.substring(0, 200) + "…" : first;
    }
}
