package com.vgrid.ocpp;

import com.google.gson.*;
import com.vgrid.dao.jdbc.JdbcOcppDao;
import com.vgrid.dao.jdbc.MySqlDataLayer;
import com.vgrid.model.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The logic of the OCPP server: reads one OCPP 1.6 (JSON) message from a charger, updates MySQL
 * and builds the reply.
 *
 * OCPP-J messages are JSON arrays:
 *   CALL        [2, "uniqueId", "Action", {payload}]       charger -> server request
 *   CALLRESULT  [3, "uniqueId", {payload}]                 answer
 *   CALLERROR   [4, "uniqueId", "ErrorCode", "text", {}]   error answer
 *
 * Supported actions (the core of OCPP 1.6): BootNotification, Heartbeat, StatusNotification,
 * Authorize, StartTransaction, MeterValues, StopTransaction. Anything else gets "NotImplemented".
 */
public class OcppHandler {
    public static final int HEARTBEAT_INTERVAL_SECONDS = 60;
    private static final Set<String> FAULT = Set.of("Faulted", "Unavailable");

    private final MySqlDataLayer data;
    private final JdbcOcppDao ocpp;
    private final Consumer<String> log;

    public OcppHandler(MySqlDataLayer data, Consumer<String> log) {
        this.data = data;
        this.ocpp = data.ocpp();
        this.log = log;
    }

    public boolean isKnown(String chargePointId) {
        return ocpp.findLink(chargePointId).isPresent();
    }

    public synchronized void connected(String chargePointId) {
        ocpp.setConnected(chargePointId, true, LocalDateTime.now());
    }

    public synchronized void disconnected(String chargePointId) {
        ocpp.setConnected(chargePointId, false, LocalDateTime.now());
    }

    /**
     * Handles one message from a charger and returns the reply to send back (or null when the message
     * was itself a reply). synchronized: messages are handled one at a time, so database work never overlaps.
     */
    public synchronized String handle(String chargePointId, String text) {
        JsonArray message;
        try {
            message = JsonParser.parseString(text).getAsJsonArray();
        } catch (RuntimeException e) {
            log.accept("     ! not valid OCPP JSON, ignored");
            return null;
        }
        if (message.size() < 3 || !message.get(0).isJsonPrimitive()) {
            return callError("", "FormationViolation", "Expected [2, uniqueId, action, payload]");
        }
        int type = message.get(0).getAsInt();
        String uniqueId = message.get(1).getAsString();
        if (type == 3 || type == 4) {
            return null; // the charger answering something we sent; nothing to do
        }
        if (type != 2) {
            return callError(uniqueId, "ProtocolError", "Unknown message type " + type);
        }
        String action = message.get(2).getAsString();
        JsonObject payload = message.size() > 3 && message.get(3).isJsonObject()
                ? message.get(3).getAsJsonObject() : new JsonObject();
        try {
            JsonObject result = switch (action) {
                case "BootNotification" -> bootNotification(chargePointId, payload);
                case "Heartbeat" -> heartbeat(chargePointId);
                case "StatusNotification" -> statusNotification(chargePointId, payload);
                case "Authorize" -> authorize(chargePointId);
                case "StartTransaction" -> startTransaction(chargePointId, payload);
                case "MeterValues" -> meterValues(chargePointId, payload);
                case "StopTransaction" -> stopTransaction(chargePointId, payload);
                default -> null;
            };
            if (result == null) {
                return callError(uniqueId, "NotImplemented", action + " is not supported by the VGrid OCPP server");
            }
            return callResult(uniqueId, result);
        } catch (RuntimeException e) {
            log.accept("     ! " + action + " failed: " + e.getMessage());
            return callError(uniqueId, "InternalError", String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ actions

    private JsonObject bootNotification(String cp, JsonObject p) {
        ocpp.recordBoot(cp, text(p, "chargePointVendor"), text(p, "chargePointModel"), LocalDateTime.now());
        log.accept("     " + describe(cp) + " registered (" + text(p, "chargePointVendor") + " " + text(p, "chargePointModel") + ")");
        JsonObject r = new JsonObject();
        r.addProperty("status", "Accepted");
        r.addProperty("currentTime", now());
        r.addProperty("interval", HEARTBEAT_INTERVAL_SECONDS);
        return r;
    }

    private JsonObject heartbeat(String cp) {
        ocpp.touch(cp, LocalDateTime.now());
        JsonObject r = new JsonObject();
        r.addProperty("currentTime", now());
        return r;
    }

    /** The most important message: the connector's status. Faults alert every driver booked on the charger. */
    private JsonObject statusNotification(String cp, JsonObject p) {
        String status = text(p, "status");
        String errorCode = text(p, "errorCode");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        LocalDateTime now = LocalDateTime.now();
        ocpp.updateStatus(cp, status, errorCode, now);

        JdbcOcppDao.Link link = ocpp.findLink(cp).orElseThrow();
        Station station = data.stations().findStation(link.stationId()).orElseThrow();
        Charger charger = station.findCharger(link.chargerCode());
        boolean storedOutOfService = charger.getStatus() == ChargerStatus.OUT_OF_SERVICE;

        if (FAULT.contains(status)) {
            if (!storedOutOfService) {
                String reason = errorCode == null || errorCode.equals("NoError") ? status : status + ": " + errorCode;
                String note = "Charger " + charger.getId() + " reported a fault itself (OCPP " + reason + ").";
                List<BookingRef> affected = data.reservations().reportChargerProblem(station.getId(), charger.getId(), now, note);
                for (BookingRef booking : affected) {
                    data.notifications().addNotification(booking.userId(), NotificationType.CHARGER_UNAVAILABLE,
                            "Reserved charger unavailable", "Charger " + charger.getId() + " at " + station.getName()
                                    + " reported a fault. Your reservation " + booking.reservationId()
                                    + " needs a new charger.", now, booking.reservationId());
                    data.notifications().addNotification(booking.userId(), NotificationType.ALTERNATIVE_SUGGESTION,
                            "Find a compatible alternative", charger.getConnectorType() + " chargers nearby may be free for "
                                    + booking.reservationId() + ". Compare and switch in one step.", now, booking.reservationId());
                }
                log.accept("     " + describe(cp) + " is OUT OF SERVICE · " + affected.size() + " booking(s) alerted");
            }
        } else {
            if (storedOutOfService) {
                data.stations().updateChargerStatus(station.getId(), charger.getId(), ChargerStatus.AVAILABLE);
                log.accept("     " + describe(cp) + " is back in service");
            }
            log.accept("     " + describe(cp) + " -> " + switch (status) {
                case "Available" -> "Available";
                case "Reserved" -> "Reserved";
                default -> "In use (" + status + ")";
            });
        }
        return new JsonObject();
    }

    /** Every RFID card / app id is accepted in this demo server. */
    private JsonObject authorize(String cp) {
        ocpp.touch(cp, LocalDateTime.now());
        JsonObject r = new JsonObject();
        r.add("idTagInfo", accepted());
        return r;
    }

    private JsonObject startTransaction(String cp, JsonObject p) {
        int meterStart = integer(p, "meterStart", 0);
        int transactionId = ocpp.startTransaction(cp, text(p, "idTag"), meterStart, LocalDateTime.now());
        ocpp.touch(cp, LocalDateTime.now());
        log.accept("     " + describe(cp) + " started transaction " + transactionId + " (idTag " + text(p, "idTag") + ")");
        JsonObject r = new JsonObject();
        r.addProperty("transactionId", transactionId);
        r.add("idTagInfo", accepted());
        return r;
    }

    /** Reads the energy register (Wh or kWh) from the first sampled value set. */
    private JsonObject meterValues(String cp, JsonObject p) {
        ocpp.touch(cp, LocalDateTime.now());
        JsonArray meterValue = p.has("meterValue") && p.get("meterValue").isJsonArray()
                ? p.getAsJsonArray("meterValue") : new JsonArray();
        for (JsonElement mv : meterValue) {
            JsonArray samples = mv.isJsonObject() && mv.getAsJsonObject().has("sampledValue")
                    ? mv.getAsJsonObject().getAsJsonArray("sampledValue") : new JsonArray();
            for (JsonElement s : samples) {
                JsonObject sample = s.getAsJsonObject();
                String measurand = text(sample, "measurand");
                if (measurand != null && !measurand.equals("Energy.Active.Import.Register")) continue;
                double value = Double.parseDouble(text(sample, "value"));
                int wh = "kWh".equalsIgnoreCase(text(sample, "unit")) ? (int) Math.round(value * 1000) : (int) Math.round(value);
                ocpp.updateMeter(cp, wh);
                log.accept("     " + describe(cp) + " meter reading " + String.format("%.2f", wh / 1000.0) + " kWh");
                return new JsonObject();
            }
        }
        return new JsonObject();
    }

    private JsonObject stopTransaction(String cp, JsonObject p) {
        int transactionId = integer(p, "transactionId", -1);
        int energyWh = ocpp.stopTransaction(transactionId, integer(p, "meterStop", 0), LocalDateTime.now());
        ocpp.touch(cp, LocalDateTime.now());
        log.accept("     " + describe(cp) + " stopped transaction " + transactionId
                + (energyWh >= 0 ? " · " + String.format("%.2f", energyWh / 1000.0) + " kWh delivered" : " (unknown id)"));
        JsonObject r = new JsonObject();
        r.add("idTagInfo", accepted());
        return r;
    }

    // ------------------------------------------------------------------ helpers

    /** "C1 at VGrid OCPP Test Station" for log lines. */
    private String describe(String cp) {
        Optional<JdbcOcppDao.Link> link = ocpp.findLink(cp);
        return link.map(l -> l.chargerCode() + " (" + l.stationId() + ")").orElse(cp);
    }

    static String callResult(String uniqueId, JsonObject payload) {
        JsonArray a = new JsonArray();
        a.add(3);
        a.add(uniqueId);
        a.add(payload);
        return a.toString();
    }

    static String callError(String uniqueId, String code, String description) {
        JsonArray a = new JsonArray();
        a.add(4);
        a.add(uniqueId);
        a.add(code);
        a.add(description);
        a.add(new JsonObject());
        return a.toString();
    }

    private static JsonObject accepted() {
        JsonObject info = new JsonObject();
        info.addProperty("status", "Accepted");
        return info;
    }

    private static String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : fallback;
    }
}
