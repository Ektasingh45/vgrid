package com.vgrid.simulator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * One pretend charger: plays the part of the small computer inside a real charging station.
 *
 * It connects to the VGrid OCPP server like a real charger (WebSocket, sub-protocol "ocpp1.6") and sends
 * the same OCPP 1.6 messages: BootNotification, Heartbeat, StatusNotification, StartTransaction,
 * MeterValues and StopTransaction. The server cannot tell it apart from real hardware.
 *
 * Charging: energy needed = (target% - battery%) x battery kWh, delivered at the charger's power.
 * One tick per real second; at demo speed one tick is one simulated minute. When the target is reached
 * the charger stops the transaction and reports the plug as free by itself (no "Done" button needed).
 *
 * Not a JavaFX class: the window only calls the public methods and listens to {@link Listener}.
 */
public class SimulatedCharger {

    public enum State {
        NOT_CONNECTED("Not connected"),
        CONNECTING("Connecting…"),
        AVAILABLE("Available"),
        PREPARING("Car plugged in"),
        CHARGING("Charging"),
        FINISHING("Finishing"),
        FAULTED("Faulted");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Called from background threads; the window moves the work to the JavaFX thread. */
    public interface Listener {
        void changed(SimulatedCharger charger);

        void log(SimulatedCharger charger, String line);
    }

    private static final String ID_TAG = "VGRID-DEMO";
    private static final int METER_EVERY_TICKS_DEMO = 3;
    private static final int METER_EVERY_TICKS_REAL = 30;
    private static final int FINISHING_SECONDS = 3;

    private final String code;
    private final String connector;
    private final double powerKw;
    private final URI serverUri;
    private final ScheduledExecutorService timer;
    private final BooleanSupplier demoSpeed;
    private final Listener listener;

    private WebSocketClient socket;
    private State state = State.NOT_CONNECTED;
    private String problem;
    private int nextMessageId = 1;
    private final Map<String, String> waitingFor = new HashMap<>();
    private ScheduledFuture<?> heartbeat;
    private ScheduledFuture<?> chargingTicks;

    // The charger's energy meter only goes up, like a real one (Wh).
    private double meterWh;
    private Integer transactionId;
    private double meterStartWh;
    private double batteryKwh;
    private double batteryPercent;
    private double targetPercent;
    private int ticks;

    public SimulatedCharger(String code, String connector, double powerKw, URI serverUri,
                            ScheduledExecutorService timer, BooleanSupplier demoSpeed, Listener listener) {
        this.code = code;
        this.connector = connector;
        this.powerKw = powerKw;
        this.serverUri = serverUri;
        this.timer = timer;
        this.demoSpeed = demoSpeed;
        this.listener = listener;
    }

    // ------------------------------------------------------------------ buttons

    /** Opens the WebSocket; BootNotification follows as soon as it is open. */
    public synchronized void connect() {
        if (state != State.NOT_CONNECTED) return;
        problem = null;
        state = State.CONNECTING;
        socket = new WebSocketClient(serverUri, new Draft_6455(Collections.emptyList(),
                List.of(new Protocol("ocpp1.6")))) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                opened(this);
            }

            @Override
            public void onMessage(String message) {
                received(this, message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                closed(this, code, reason);
            }

            @Override
            public void onError(Exception ex) {
                // onClose follows and explains what happened
            }
        };
        socket.setConnectionLostTimeout(0);
        log("connecting to " + serverUri);
        changed();
        socket.connect();
    }

    /** Only allowed while no car is charging, so the server never keeps a transaction open. */
    public synchronized void disconnect() {
        if (socket == null || isCarPluggedIn()) return;
        socket.close(1000, "Disconnected in the simulator");
    }

    /** A driver plugs in a car: Preparing -> StartTransaction -> Charging. */
    public synchronized void plugIn(double batteryKwh, double batteryPercent, double targetPercent) {
        if (state != State.AVAILABLE) return;
        this.batteryKwh = batteryKwh;
        this.batteryPercent = batteryPercent;
        this.targetPercent = targetPercent;
        this.meterStartWh = meterWh;
        this.ticks = 0;
        state = State.PREPARING;
        status("Preparing", "NoError");
        JsonObject p = new JsonObject();
        p.addProperty("connectorId", 1);
        p.addProperty("idTag", ID_TAG);
        p.addProperty("meterStart", (int) Math.round(meterWh));
        p.addProperty("timestamp", now());
        call("StartTransaction", p); // charging starts when the server answers with a transactionId
        changed();
    }

    /** The driver leaves before the battery reaches the target. */
    public synchronized void unplugNow() {
        if (!isCarPluggedIn()) return;
        stopCharging("EVDisconnected");
        state = State.AVAILABLE;
        status("Available", "NoError");
        changed();
    }

    /** The charger breaks: any charging stops and it reports Faulted, so VGrid alerts booked drivers. */
    public synchronized void fault() {
        if (!isConnected() || state == State.FAULTED) return;
        if (isCarPluggedIn()) {
            stopCharging("Other");
        }
        state = State.FAULTED;
        status("Faulted", "GroundFailure");
        changed();
    }

    /** A technician fixed it. */
    public synchronized void fixed() {
        if (state != State.FAULTED) return;
        state = State.AVAILABLE;
        status("Available", "NoError");
        changed();
    }

    /** Window closing: end any charging cleanly, then hang up. */
    public synchronized void shutdown() {
        if (isCarPluggedIn()) {
            stopCharging("PowerLoss");
            status("Available", "NoError");
        }
        if (socket != null) {
            socket.close(1000, "Simulator closed");
        }
    }

    // ------------------------------------------------------------------ WebSocket events

    private synchronized void opened(WebSocketClient from) {
        if (from != socket) return;
        log("connected");
        JsonObject p = new JsonObject();
        p.addProperty("chargePointVendor", "VGrid Simulator");
        p.addProperty("chargePointModel", connector + " " + Math.round(powerKw) + " kW");
        call("BootNotification", p);
    }

    private synchronized void received(WebSocketClient from, String text) {
        if (from != socket) return;
        log("<-  " + text);
        JsonArray message;
        try {
            message = JsonParser.parseString(text).getAsJsonArray();
        } catch (RuntimeException e) {
            return;
        }
        int type = message.get(0).getAsInt();
        String id = message.get(1).getAsString();
        if (type == 2) {
            // The server asked us something (not used by the VGrid server): say we don't support it.
            JsonArray error = new JsonArray();
            error.add(4);
            error.add(id);
            error.add("NotImplemented");
            error.add("The simulator only sends messages");
            error.add(new JsonObject());
            send(error.toString());
            return;
        }
        String action = waitingFor.remove(id);
        if (action == null) return;
        if (type == 4) {
            answerRefused(action, message.size() > 3 ? message.get(3).getAsString() : "error");
            return;
        }
        JsonObject result = message.get(2).getAsJsonObject();
        switch (action) {
            case "BootNotification" -> bootAccepted(result);
            case "StartTransaction" -> transactionStarted(result);
            default -> {
                // Heartbeat, StatusNotification, MeterValues, StopTransaction: nothing to do with the answer
            }
        }
    }

    private synchronized void closed(WebSocketClient from, int closeCode, String reason) {
        if (from != socket) return;
        cancelTimers();
        boolean wasConnecting = state == State.CONNECTING;
        state = State.NOT_CONNECTED;
        transactionId = null;
        socket = null;
        waitingFor.clear();
        if (closeCode == 1008) {
            problem = "Server refused " + ocppId() + " (unknown charger id)";
        } else if (wasConnecting) {
            problem = "OCPP server not reachable. Start run-ocpp-server.bat first.";
        } else if (closeCode != 1000) {
            problem = "Connection lost" + (reason == null || reason.isBlank() ? "" : ": " + reason);
        }
        log("disconnected" + (problem == null ? "" : " · " + problem));
        changed();
    }

    // ------------------------------------------------------------------ answers from the server

    private void bootAccepted(JsonObject result) {
        String status = result.has("status") ? result.get("status").getAsString() : "";
        if (!status.equals("Accepted")) {
            problem = "Server answered BootNotification with " + status;
            socket.close(1000, "Not accepted");
            return;
        }
        int interval = result.has("interval") ? Math.max(10, result.get("interval").getAsInt()) : 60;
        state = State.AVAILABLE;
        status("Available", "NoError");
        heartbeat = timer.scheduleAtFixedRate(this::sendHeartbeat, interval, interval, TimeUnit.SECONDS);
        changed();
    }

    private void transactionStarted(JsonObject result) {
        if (state != State.PREPARING) return; // unplugged or faulted while waiting
        transactionId = result.get("transactionId").getAsInt();
        state = State.CHARGING;
        status("Charging", "NoError");
        chargingTicks = timer.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
        changed();
    }

    private void answerRefused(String action, String errorCode) {
        log(action + " refused by the server (" + errorCode + ")");
        if (action.equals("StartTransaction") && state == State.PREPARING) {
            state = State.AVAILABLE;
            status("Available", "NoError");
            changed();
        }
    }

    // ------------------------------------------------------------------ charging

    /** One second of real time: adds energy, sends meter readings, finishes at the target. */
    private synchronized void tick() {
        if (state != State.CHARGING) return;
        int simulatedSeconds = demoSpeed.getAsBoolean() ? 60 : 1;
        double neededWh = (targetPercent - batteryPercent) / 100.0 * batteryKwh * 1000;
        double addWh = Math.min(powerKw * 1000 * simulatedSeconds / 3600.0, neededWh);
        meterWh += addWh;
        batteryPercent += addWh / (batteryKwh * 1000) * 100;
        ticks++;
        boolean full = batteryPercent >= targetPercent - 0.001;
        int meterEvery = demoSpeed.getAsBoolean() ? METER_EVERY_TICKS_DEMO : METER_EVERY_TICKS_REAL;
        if (full || ticks % meterEvery == 0) {
            meterValues();
        }
        if (full) {
            batteryPercent = targetPercent;
            log("battery reached " + Math.round(targetPercent) + "%, the car is unplugged");
            stopCharging("EVDisconnected");
            state = State.FINISHING;
            status("Finishing", "NoError");
            timer.schedule(this::finishingDone, FINISHING_SECONDS, TimeUnit.SECONDS);
        }
        changed();
    }

    private synchronized void finishingDone() {
        if (state != State.FINISHING) return;
        state = State.AVAILABLE;
        status("Available", "NoError");
        changed();
    }

    private void stopCharging(String reason) {
        if (chargingTicks != null) {
            chargingTicks.cancel(false);
            chargingTicks = null;
        }
        if (transactionId != null) {
            JsonObject p = new JsonObject();
            p.addProperty("transactionId", transactionId);
            p.addProperty("idTag", ID_TAG);
            p.addProperty("meterStop", (int) Math.round(meterWh));
            p.addProperty("timestamp", now());
            p.addProperty("reason", reason);
            call("StopTransaction", p);
            transactionId = null;
        }
    }

    private void meterValues() {
        JsonObject sample = new JsonObject();
        sample.addProperty("value", String.valueOf(Math.round(meterWh)));
        sample.addProperty("measurand", "Energy.Active.Import.Register");
        sample.addProperty("unit", "Wh");
        JsonArray samples = new JsonArray();
        samples.add(sample);
        JsonObject reading = new JsonObject();
        reading.addProperty("timestamp", now());
        reading.add("sampledValue", samples);
        JsonArray readings = new JsonArray();
        readings.add(reading);
        JsonObject p = new JsonObject();
        p.addProperty("connectorId", 1);
        if (transactionId != null) p.addProperty("transactionId", transactionId);
        p.add("meterValue", readings);
        call("MeterValues", p);
    }

    private synchronized void sendHeartbeat() {
        if (isConnected()) {
            call("Heartbeat", new JsonObject());
        }
    }

    // ------------------------------------------------------------------ sending

    private void status(String status, String errorCode) {
        JsonObject p = new JsonObject();
        p.addProperty("connectorId", 1);
        p.addProperty("status", status);
        p.addProperty("errorCode", errorCode);
        p.addProperty("timestamp", now());
        call("StatusNotification", p);
    }

    /** Sends a CALL [2, "id", "Action", {payload}] and remembers which answer we are waiting for. */
    private void call(String action, JsonObject payload) {
        String id = String.valueOf(nextMessageId++);
        waitingFor.put(id, action);
        JsonArray message = new JsonArray();
        message.add(2);
        message.add(id);
        message.add(action);
        message.add(payload);
        send(message.toString());
    }

    private void send(String text) {
        if (socket == null || !socket.isOpen()) return;
        socket.send(text);
        log("->  " + text);
    }

    private void cancelTimers() {
        if (heartbeat != null) heartbeat.cancel(false);
        if (chargingTicks != null) chargingTicks.cancel(false);
        heartbeat = null;
        chargingTicks = null;
    }

    private void changed() {
        listener.changed(this);
    }

    private void log(String line) {
        listener.log(this, line);
    }

    private static String now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // ------------------------------------------------------------------ what the window shows

    public String code() {
        return code;
    }

    public String connector() {
        return connector;
    }

    public double powerKw() {
        return powerKw;
    }

    public String ocppId() {
        String path = serverUri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    public synchronized State state() {
        return state;
    }

    public synchronized String problem() {
        return problem;
    }

    public synchronized boolean isConnected() {
        return state != State.NOT_CONNECTED && state != State.CONNECTING;
    }

    public synchronized boolean isCarPluggedIn() {
        return state == State.PREPARING || state == State.CHARGING;
    }

    public synchronized double batteryPercent() {
        return batteryPercent;
    }

    public synchronized double targetPercent() {
        return targetPercent;
    }

    /** Energy given to the car in the current (or last) charge, in kWh. */
    public synchronized double sessionKwh() {
        return (meterWh - meterStartWh) / 1000.0;
    }

    /** Simulated minutes until the target is reached. */
    public synchronized double minutesLeft() {
        double neededKwh = Math.max(0, targetPercent - batteryPercent) / 100.0 * batteryKwh;
        return neededKwh / powerKw * 60;
    }

    /** How long a charge from battery% to target% takes at this charger, in minutes. */
    public double estimateMinutes(double batteryKwh, double fromPercent, double toPercent) {
        return Math.max(0, toPercent - fromPercent) / 100.0 * batteryKwh / powerKw * 60;
    }
}
