package com.vgrid.ocpp;

import com.vgrid.dao.jdbc.JdbcOcppDao;
import com.vgrid.dao.jdbc.MySqlDataLayer;
import com.vgrid.data.AppConfig;
import com.vgrid.model.City;

import java.net.BindException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Starts the VGrid OCPP server in its own console window (run-ocpp-server.bat).
 *
 * It reads db.properties, connects to the same MySQL database as the VGrid app, creates the
 * "VGrid OCPP Test Station" with four OCPP chargers, then waits for chargers (or a charger simulator)
 * to connect. Everything they report is written to MySQL, and every open VGrid window shows it.
 */
public final class OcppServerMain {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private OcppServerMain() {
    }

    public static void main(String[] args) {
        System.out.println("==============================================================");
        System.out.println("  VGrid OCPP 1.6 server");
        System.out.println("==============================================================");

        AppConfig config = AppConfig.load();
        if (config.memoryMode()) {
            fail("The OCPP server needs MySQL. Set data.mode=mysql in " + AppConfig.FILE + ".");
        }
        MySqlDataLayer data;
        try {
            data = MySqlDataLayer.connect(config.dbUrl(), config.dbUser(), config.dbPassword());
        } catch (Exception e) {
            fail("Could not connect to MySQL: " + e.getMessage());
            return;
        }
        log("Connected to " + data.description());

        City city = City.byId(config.ocppTestCity());
        List<JdbcOcppDao.Link> links = data.ocpp().ensureTestStation(city);
        data.ocpp().markAllDisconnected();
        log("Test station: \"VGrid OCPP Test Station\" in " + city.name() + " with these chargers:");
        for (JdbcOcppDao.Link link : links) {
            System.out.println("      " + link.chargerCode() + "  ->  ws://localhost:" + config.ocppPort() + "/ocpp/" + link.ocppId());
        }
        System.out.println();

        OcppServer server = new OcppServer(config.ocppPort(), new OcppHandler(data, OcppServerMain::log),
                OcppServerMain::log, e -> {
                    if (e instanceof BindException) {
                        fail("Port " + config.ocppPort() + " is already in use. Is the OCPP server already running? "
                                + "(You can change ocpp.port in " + AppConfig.FILE + ".)");
                    }
                    fail("The OCPP server stopped: " + e.getMessage());
                });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop(1000);
                data.ocpp().markAllDisconnected();
            } catch (Exception ignored) {
                // shutting down anyway
            }
        }));
        server.start(); // listens on its own thread; OcppServer.onStart() confirms
    }

    static synchronized void log(String line) {
        System.out.println(LocalTime.now().format(TIME) + "  " + line);
    }

    private static void fail(String message) {
        System.out.println();
        System.out.println("ERROR: " + message);
        System.exit(1);
    }
}
