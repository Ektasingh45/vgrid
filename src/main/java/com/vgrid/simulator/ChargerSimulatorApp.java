package com.vgrid.simulator;

import com.vgrid.data.AppConfig;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.net.URI;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The charger simulator window (run-charger-simulator.bat): four pretend chargers, C1 to C4 of the
 * "VGrid OCPP Test Station", each with its own WebSocket connection to the VGrid OCPP server.
 *
 * Simulator --OCPP--> OCPP server --> MySQL --> VGrid app (refreshes every 15 s)
 */
public class ChargerSimulatorApp extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_CHARS = 60_000;

    /** The same chargers JdbcOcppDao.ensureTestStation creates: code, connector, kW, typical battery kWh. */
    private static final Object[][] CHARGERS = {
            {"C1", "CCS2", 60.0, 40}, {"C2", "CCS2", 60.0, 40}, {"C3", "Type 2", 22.0, 30}, {"C4", "CHAdeMO", 50.0, 40}};

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "charger-simulator");
        t.setDaemon(true);
        return t;
    });
    private final List<ChargerCard> cards = new ArrayList<>();
    private final TextArea log = new TextArea();
    private final CheckBox demoSpeed = new CheckBox("Demo speed: 1 second = 1 minute");
    private volatile boolean demo = true;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        String city = config.ocppTestCity().toUpperCase(Locale.ROOT);
        String base = "ws://localhost:" + config.ocppPort() + "/ocpp/";

        SimulatedCharger.Listener listener = new SimulatedCharger.Listener() {
            @Override
            public void changed(SimulatedCharger charger) {
                Platform.runLater(() -> cards.forEach(ChargerCard::refresh));
            }

            @Override
            public void log(SimulatedCharger charger, String line) {
                String text = LocalTime.now().format(TIME) + "  " + charger.code() + "  " + line + "\n";
                Platform.runLater(() -> appendLog(text));
            }
        };
        for (Object[] c : CHARGERS) {
            String code = (String) c[0];
            SimulatedCharger charger = new SimulatedCharger(code, (String) c[1], (Double) c[2],
                    URI.create(base + "VGRID-" + city + "-" + code), timer, () -> demo, listener);
            cards.add(new ChargerCard(charger, (Integer) c[3]));
        }

        // ---------- header
        Label title = new Label("VGrid Charger Simulator");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Pretends to be the four chargers of the \"VGrid OCPP Test Station\". "
                + "Each one talks OCPP 1.6 to " + base + "…");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        VBox heading = new VBox(4, title, subtitle);
        HBox.setHgrow(heading, Priority.ALWAYS);

        demoSpeed.setSelected(true);
        demoSpeed.selectedProperty().addListener((obs, was, now) -> {
            demo = now;
            cards.forEach(ChargerCard::refresh);
        });
        Button connectAll = new Button("Connect all");
        connectAll.getStyleClass().addAll("btn", "btn-primary");
        connectAll.setOnAction(e -> cards.forEach(card -> card.charger.connect()));
        HBox actions = new HBox(14, demoSpeed, connectAll);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        HBox header = new HBox(16, heading, actions);
        header.setAlignment(Pos.CENTER_LEFT);

        // ---------- 2 x 2 charger cards
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);
        for (int i = 0; i < cards.size(); i++) {
            grid.add(cards.get(i).root, i % 2, i / 2);
        }
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        grid.getColumnConstraints().addAll(half, half);

        // ---------- message log
        Label logTitle = new Label("OCPP messages (->  sent to the server,  <-  server's answer)");
        logTitle.getStyleClass().add("subsection-title");
        Button clear = new Button("Clear");
        clear.getStyleClass().addAll("btn", "btn-ghost");
        clear.setOnAction(e -> log.clear());
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox logHeader = new HBox(8, logTitle, gap, clear);
        logHeader.setAlignment(Pos.CENTER_LEFT);
        log.setEditable(false);
        log.setWrapText(true);
        log.setStyle("-fx-font-family: Consolas, monospace; -fx-font-size: 11.5px;");
        log.setPrefRowCount(9);
        VBox logBox = new VBox(8, logHeader, log);
        logBox.getStyleClass().add("card");
        VBox.setVgrow(log, Priority.ALWAYS);
        VBox.setVgrow(logBox, Priority.ALWAYS);

        VBox page = new VBox(18, header, grid, logBox);
        page.setPadding(new Insets(22, 24, 22, 24));

        Scene scene = new Scene(page, 1080, 860);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/style.css"), "style.css not found").toExternalForm());
        stage.setTitle("VGrid Charger Simulator (OCPP 1.6)");
        stage.setMinWidth(860);
        stage.setMinHeight(640);
        stage.setScene(scene);
        stage.show();
        cards.forEach(ChargerCard::refresh);
        appendLog("Start run-ocpp-server.bat first, then press \"Connect all\".\n");
    }

    @Override
    public void stop() {
        cards.forEach(card -> card.charger.shutdown());
        timer.shutdown();
    }

    private void appendLog(String text) {
        log.appendText(text);
        if (log.getLength() > MAX_LOG_CHARS) {
            log.deleteText(0, log.getLength() - MAX_LOG_CHARS / 2);
        }
    }

    // ------------------------------------------------------------------ one card per charger

    private final class ChargerCard {
        final SimulatedCharger charger;
        final VBox root = new VBox(12);
        final Label badge = new Label();
        final Label problem = new Label();
        final ProgressBar progress = new ProgressBar(0);
        final Label progressText = new Label();
        final TextField batteryKwh = numberField("40");
        final TextField fromPercent = numberField("25");
        final TextField toPercent = numberField("80");
        final Label estimate = new Label();
        final Button connect = button("Connect", "btn-secondary");
        final Button plugIn = button("Plug in & charge", "btn-primary");
        final Button unplug = button("Unplug now", "btn-secondary");
        final Button fault = button("Fault", "btn-danger");
        final GridPane form = new GridPane();

        ChargerCard(SimulatedCharger charger, int typicalBatteryKwh) {
            this.charger = charger;
            batteryKwh.setText(String.valueOf(typicalBatteryKwh));

            Label name = new Label(charger.code() + " · " + charger.connector() + " · " + Math.round(charger.powerKw()) + " kW");
            name.getStyleClass().add("card-title");
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            badge.getStyleClass().add("badge");
            HBox top = new HBox(8, name, gap, badge);
            top.setAlignment(Pos.CENTER_LEFT);
            Label id = new Label("Charge point id " + charger.ocppId());
            id.getStyleClass().add("small-muted");

            problem.getStyleClass().add("form-error");
            problem.setWrapText(true);

            form.setHgap(10);
            form.setVgap(4);
            form.addRow(0, fieldLabel("Battery (kWh)"), fieldLabel("Battery now (%)"), fieldLabel("Charge to (%)"));
            form.addRow(1, batteryKwh, fromPercent, toPercent);
            for (TextField f : List.of(batteryKwh, fromPercent, toPercent)) {
                f.textProperty().addListener((obs, was, now) -> refresh());
            }
            estimate.getStyleClass().add("small-muted");

            progress.getStyleClass().add("bar-green");
            progress.setMaxWidth(Double.MAX_VALUE);
            progress.setMinHeight(10);
            progressText.getStyleClass().add("small-muted");

            connect.setOnAction(e -> {
                if (charger.isConnected() || charger.state() == SimulatedCharger.State.CONNECTING) {
                    charger.disconnect();
                } else {
                    charger.connect();
                }
            });
            plugIn.setOnAction(e -> {
                double[] input = input();
                if (input != null) charger.plugIn(input[0], input[1], input[2]);
            });
            unplug.setOnAction(e -> charger.unplugNow());
            fault.setOnAction(e -> {
                if (charger.state() == SimulatedCharger.State.FAULTED) {
                    charger.fixed();
                } else {
                    charger.fault();
                }
            });
            FlowPane buttons = new FlowPane(8, 8, connect, plugIn, unplug, fault);

            root.getChildren().addAll(new VBox(2, top, id), problem, form, estimate,
                    new VBox(6, progress, progressText), buttons);
            root.getStyleClass().add("card");
        }

        void refresh() {
            SimulatedCharger.State state = charger.state();
            badge.setText(state.label());
            badge.getStyleClass().removeAll("badge-success", "badge-warning", "badge-danger", "badge-neutral");
            badge.getStyleClass().add(switch (state) {
                case AVAILABLE -> "badge-success";
                case PREPARING, CHARGING, FINISHING -> "badge-warning";
                case FAULTED -> "badge-danger";
                default -> "badge-neutral";
            });

            String issue = charger.problem();
            problem.setText(issue == null ? "" : issue);
            problem.setVisible(issue != null);
            problem.setManaged(issue != null);

            boolean connected = charger.isConnected();
            boolean pluggedIn = charger.isCarPluggedIn();
            connect.setText(connected || state == SimulatedCharger.State.CONNECTING ? "Disconnect" : "Connect");
            connect.setDisable(pluggedIn);
            plugIn.setDisable(state != SimulatedCharger.State.AVAILABLE || input() == null);
            unplug.setDisable(!pluggedIn);
            boolean faulted = state == SimulatedCharger.State.FAULTED;
            fault.setText(faulted ? "Fixed" : "Fault");
            fault.getStyleClass().removeAll("btn-danger", "btn-secondary");
            fault.getStyleClass().add(faulted ? "btn-secondary" : "btn-danger");
            fault.setDisable(!connected);
            form.setDisable(pluggedIn);

            double[] input = input();
            estimate.setText(input == null ? "Enter a battery size and a target above the current %."
                    : "Needs " + kwh((input[2] - input[1]) / 100 * input[0]) + " · about "
                    + minutes(charger.estimateMinutes(input[0], input[1], input[2])) + " at "
                    + Math.round(charger.powerKw()) + " kW" + (demo ? " (that many seconds in demo speed)" : ""));

            switch (state) {
                case CHARGING, FINISHING -> {
                    double from = charger.batteryPercent();
                    progress.setProgress(from / 100);
                    progressText.setText("Battery " + Math.round(from) + "% → " + Math.round(charger.targetPercent())
                            + "% · " + kwh(charger.sessionKwh()) + " delivered"
                            + (state == SimulatedCharger.State.CHARGING
                            ? " · " + minutes(charger.minutesLeft()) + " left" : " · car unplugging"));
                }
                case PREPARING -> {
                    progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    progressText.setText("Car plugged in, waiting for the server to start the transaction…");
                }
                default -> {
                    progress.setProgress(0);
                    progressText.setText(switch (state) {
                        case NOT_CONNECTED -> "Not talking to the server. VGrid shows this charger as Offline.";
                        case CONNECTING -> "Opening the WebSocket…";
                        case FAULTED -> "Reported Faulted. VGrid shows Out of service and alerts booked drivers.";
                        default -> charger.sessionKwh() > 0
                                ? "Free. Last charge delivered " + kwh(charger.sessionKwh()) + "."
                                : "Free. Waiting for a car.";
                    });
                }
            }
        }

        /** battery kWh, now %, target % — or null if not valid. */
        double[] input() {
            try {
                double kwh = Double.parseDouble(batteryKwh.getText().trim());
                double from = Double.parseDouble(fromPercent.getText().trim());
                double to = Double.parseDouble(toPercent.getText().trim());
                if (kwh <= 0 || kwh > 200 || from < 0 || to > 100 || to <= from) return null;
                return new double[]{kwh, from, to};
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static TextField numberField(String value) {
        TextField f = new TextField(value);
        f.setPrefWidth(110);
        f.setPrefHeight(34);
        return f;
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Button button(String text, String style) {
        Button b = new Button(text);
        b.getStyleClass().addAll("btn", style);
        return b;
    }

    private static String kwh(double value) {
        return String.format(Locale.ROOT, "%.1f kWh", value);
    }

    private static String minutes(double value) {
        long m = Math.round(value);
        return m >= 60 ? m / 60 + " h " + m % 60 + " min" : m + " min";
    }
}
