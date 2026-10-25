package com.vgrid.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** The settings in db.properties (read again every time the app starts or Retry is pressed). */
public record AppConfig(String dataMode, String dbUrl, String dbUser, String dbPassword,
                        String ocmApiKey, int ocmMaxResults, int ocppPort, String ocppTestCity) {

    public static final String FILE = "db.properties";
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/vgrid?createDatabaseIfNotExist=true&connectTimeout=4000&allowPublicKeyRetrieval=true";

    public static AppConfig load() {
        Properties p = new Properties();
        Path file = Path.of(FILE);
        try (InputStream in = Files.exists(file) ? Files.newInputStream(file)
                : AppConfig.class.getResourceAsStream("/" + FILE)) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            // Unreadable file: the defaults below are used.
        }
        return new AppConfig(
                p.getProperty("data.mode", "mysql").trim(),
                p.getProperty("db.url", DEFAULT_URL).trim(),
                p.getProperty("db.user", "root").trim(),
                p.getProperty("db.password", ""),
                p.getProperty("ocm.api.key", "").trim(),
                Math.max(10, Math.min(number(p, "ocm.max.results", 100), 500)),
                number(p, "ocpp.port", 8887),
                p.getProperty("ocpp.test.city", "pune").trim().toLowerCase());
    }

    private static int number(Properties p, String key, int fallback) {
        try {
            return Integer.parseInt(p.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean memoryMode() {
        return dataMode.equalsIgnoreCase("memory");
    }

    public boolean hasOcmKey() {
        return !ocmApiKey.isEmpty();
    }
}
