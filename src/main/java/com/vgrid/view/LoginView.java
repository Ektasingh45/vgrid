package com.vgrid.view;

import com.vgrid.dao.DataAccessException;
import com.vgrid.data.DataLayerFactory;
import com.vgrid.model.User;
import com.vgrid.service.AuthService;
import com.vgrid.view.components.Icons;
import com.vgrid.view.components.Ui;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.function.BiConsumer;

/**
 * The first screen: sign in or create an account.
 * Left: brand panel. Right: the form card, demo accounts and the database connection status.
 */
public class LoginView extends HBox {

    private enum Mode { SIGN_IN, SIGN_UP }

    private final BiConsumer<User, Boolean> onSignedIn;
    private AuthService auth;
    private Mode mode = Mode.SIGN_IN;

    private final Label title = Ui.label("", "auth-title");
    private final Label subtitle = Ui.wrapLabel("", "auth-subtitle");
    private final TextField nameField = new TextField();
    private final TextField emailField = new TextField();
    private final PasswordInput password = new PasswordInput("Enter your password");
    private final PasswordInput confirm = new PasswordInput("Type the password again");
    private final VBox nameRow = Ui.field("Full name", inputBox(Icons.PERSON, nameField));
    private final VBox confirmRow = Ui.field("Confirm password", confirm.box);
    private final Label errorText = Ui.wrapLabel("", "notice-text");
    private final HBox errorBox = new HBox(10, Icons.create(Icons.WARNING, 16), errorText);
    private final Button submit = Ui.primaryButton("Sign In", Icons.ARROW_RIGHT);
    private boolean busy;
    private final Label switchText = Ui.label("", "muted");
    private final Button switchLink = new Button();
    private final Label statusPill = Ui.label("", "status-pill");
    private final Label connectionText = Ui.wrapLabel("", "notice-text");
    private final HBox connectionNotice;
    private final VBox card = new VBox(18);

    /**
     * onSignedIn receives the user and true when the account was just created.
     * onRetry is called when the user asks to try connecting to MySQL again.
     */
    public LoginView(BiConsumer<User, Boolean> onSignedIn, Runnable onRetry) {
        this.onSignedIn = onSignedIn;
        getStyleClass().add("auth-root");

        Button retry = Ui.ghostButton("Retry", Icons.REFRESH);
        retry.setOnAction(e -> onRetry.run());
        connectionNotice = new HBox(10, Icons.create(Icons.DATABASE, 16), connectionText, retry);
        connectionNotice.setAlignment(Pos.CENTER_LEFT);
        connectionNotice.getStyleClass().addAll("notice", "notice-warning");
        HBox.setHgrow(connectionText, Priority.ALWAYS);
        show(connectionNotice, false);

        Node brand = buildBrandPanel();
        Node side = buildFormSide();
        HBox.setHgrow(side, Priority.ALWAYS);
        getChildren().addAll(brand, side);

        setMode(Mode.SIGN_IN, false);
        showConnecting();
    }

    // ------------------------------------------------------------------ connection status

    /** While MySQL is being contacted: the form is visible but cannot be submitted yet. */
    public void showConnecting() {
        auth = null;
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(14, 14);
        spinner.setMaxSize(14, 14);
        setStatus("Connecting to database…", spinner, "status-wait");
        show(connectionNotice, false);
        updateSubmit();
    }

    /** Called once DataLayerFactory has decided where the data comes from. */
    public void showConnected(DataLayerFactory.Result result) {
        auth = new AuthService(result.layer().users());
        switch (result.status()) {
            case MYSQL -> setStatus("Connected · " + result.layer().description(), dot("status-dot-ok"), "status-ok");
            case OFFLINE_CHOSEN -> setStatus("Offline demo mode", dot("status-dot-warn"), "status-warn");
            case OFFLINE_FALLBACK -> {
                setStatus("Offline demo mode", dot("status-dot-warn"), "status-warn");
                connectionText.setText("Couldn't connect to MySQL: " + result.problem()
                        + " You can still sign in, but nothing will be saved after you close the app.");
                show(connectionNotice, true);
            }
        }
        updateSubmit();
    }

    private void setStatus(String text, Node graphic, String tone) {
        statusPill.setText(text);
        statusPill.setGraphic(graphic);
        statusPill.getStyleClass().removeAll("status-ok", "status-warn", "status-wait");
        statusPill.getStyleClass().add(tone);
    }

    private static Region dot(String tone) {
        Region dot = new Region();
        dot.getStyleClass().addAll("status-dot", tone);
        return dot;
    }

    // ------------------------------------------------------------------ left: brand panel

    private Node buildBrandPanel() {
        VBox brandText = new VBox(0, Ui.label("VGrid", "auth-brand-name"), Ui.label("Smart EV Charging", "auth-brand-tagline"));
        HBox brand = new HBox(12, Ui.iconTile(Icons.BOLT, "brand", 44, 24), brandText);
        brand.setAlignment(Pos.CENTER_LEFT);

        Node art = buildOrbit();
        Label headline = Ui.wrapLabel("Charge smarter.\nNever get stranded.", "auth-headline");
        Label lead = Ui.wrapLabel("Find a compatible charger, book a one-hour slot, and get ranked alternatives "
                + "if your charger fails.", "auth-lead");
        VBox features = new VBox(16,
                feature(Icons.SPARKLE, "Explainable picks", "Every recommendation tells you why it was chosen."),
                feature(Icons.SHIELD, "Reservation protection", "Move to a free compatible charger in one step."),
                feature(Icons.PLUG, "Connector-aware", "CCS2, Type 2, CHAdeMO and Bharat DC-001."));
        Label foot = Ui.label("PBL Project · Smart EV Charging Management System", "auth-foot");

        Region top = new Region();
        Region bottom = new Region();
        VBox.setVgrow(top, Priority.ALWAYS);
        VBox.setVgrow(bottom, Priority.ALWAYS);
        VBox panel = new VBox(26, brand, top, art, new VBox(12, headline, lead), features, bottom, foot);
        panel.getStyleClass().add("auth-brand");
        panel.setMinWidth(400);
        panel.setMinHeight(0);
        panel.prefWidthProperty().bind(widthProperty().multiply(0.42));
        panel.setMaxWidth(560);

        // On short windows, drop the picture first and then the feature list so nothing overlaps.
        heightProperty().addListener((obs, o, h) -> {
            show(art, h.doubleValue() >= 760);
            show(features, h.doubleValue() >= 600);
        });
        return panel;
    }

    /** The bolt with three orbiting icons, like the title slide of the presentation. */
    private static Node buildOrbit() {
        double size = 232;
        double c = size / 2;
        Pane pane = new Pane();
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);

        Circle ring = new Circle(c, c, 104, Color.TRANSPARENT);
        ring.setStroke(Color.web("#4BE3A0", 0.35));
        ring.setStrokeWidth(1.5);
        Circle middle = new Circle(c, c, 74, Color.web("#16A36A", 0.20));
        Circle inner = new Circle(c, c, 48, new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2BD48C")), new Stop(1, Color.web("#11865A"))));
        inner.setEffect(new DropShadow(30, Color.web("#16A36A", 0.55)));

        StackPane bolt = new StackPane(Icons.create(Icons.BOLT, 40, "auth-orbit-bolt"));
        bolt.setPrefSize(size, size);
        bolt.setMouseTransparent(true);

        pane.getChildren().addAll(ring, middle, inner, bolt);
        double[] angles = {-55, 18, 150};
        String[] icons = {Icons.PIN, Icons.CLOCK, Icons.PLUG};
        for (int i = 0; i < angles.length; i++) {
            double a = Math.toRadians(angles[i]);
            StackPane node = new StackPane(Icons.create(icons[i], 19));
            node.getStyleClass().add("auth-orbit-node");
            node.setPrefSize(42, 42);
            node.resizeRelocate(c + 104 * Math.cos(a) - 21, c + 104 * Math.sin(a) - 21, 42, 42);
            pane.getChildren().add(node);
        }

        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.8), middle);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();

        StackPane holder = new StackPane(pane);
        holder.setAlignment(Pos.CENTER_LEFT);
        return holder;
    }

    private static Node feature(String icon, String title, String text) {
        VBox texts = new VBox(2, Ui.label(title, "auth-feature-title"), Ui.wrapLabel(text, "auth-feature-text"));
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox row = new HBox(14, Ui.iconTile(icon, "hero", 38, 18), texts);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ------------------------------------------------------------------ right: form

    private Node buildFormSide() {
        nameField.setPromptText("e.g. Ekta Singh");
        emailField.setPromptText("you@example.com");
        submit.getStyleClass().add("btn-lg");
        submit.setContentDisplay(ContentDisplay.RIGHT);
        submit.setDefaultButton(true);
        Ui.grow(submit);
        submit.setOnAction(e -> submitForm());

        errorBox.setAlignment(Pos.CENTER_LEFT);
        errorBox.getStyleClass().addAll("notice", "notice-danger");
        HBox.setHgrow(errorText, Priority.ALWAYS);
        show(errorBox, false);

        // Typing again hides the last error.
        for (TextField field : new TextField[]{nameField, emailField, password.hidden, confirm.hidden}) {
            field.textProperty().addListener((obs, o, n) -> show(errorBox, false));
        }

        switchLink.getStyleClass().add("link-button");
        switchLink.setOnAction(e -> setMode(mode == Mode.SIGN_IN ? Mode.SIGN_UP : Mode.SIGN_IN, true));
        HBox switchRow = new HBox(6, switchText, switchLink);
        switchRow.setAlignment(Pos.CENTER);

        card.getChildren().addAll(new VBox(6, title, subtitle), connectionNotice, nameRow,
                Ui.field("Email", inputBox(Icons.MAIL, emailField)), Ui.field("Password", password.box),
                confirmRow, errorBox, submit, switchRow);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(440);
        card.setMinWidth(340);
        card.setMaxHeight(Region.USE_PREF_SIZE); // as tall as its content, centred vertically

        StackPane center = new StackPane(card);
        center.setPadding(new Insets(12, 32, 24, 32));
        ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        center.minHeightProperty().bind(scroll.heightProperty().subtract(2));

        HBox top = new HBox(Ui.hSpacer(), statusPill);
        top.setPadding(new Insets(22, 28, 0, 28));
        top.setAlignment(Pos.CENTER_RIGHT);
        Label foot = Ui.label("Passwords are stored as salted PBKDF2 hashes, never as plain text.", "small-muted");
        HBox bottom = new HBox(Icons.create(Icons.LOCK, 13, "icon-faint"), foot);
        bottom.setSpacing(6);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(0, 0, 18, 0));

        BorderPane side = new BorderPane(scroll);
        side.setTop(top);
        side.setBottom(bottom);
        side.getStyleClass().add("auth-side");
        return side;
    }

    private void setMode(Mode newMode, boolean animate) {
        mode = newMode;
        boolean signUp = mode == Mode.SIGN_UP;
        title.setText(signUp ? "Create your account" : "Welcome back");
        subtitle.setText(signUp
                ? "Your bookings, alerts and preferences will be saved to your account."
                : "Sign in to find chargers and manage your bookings.");
        submit.setText(signUp ? "Create Account" : "Sign In");
        switchText.setText(signUp ? "Already have an account?" : "New to VGrid?");
        switchLink.setText(signUp ? "Sign in" : "Create an account");
        password.hidden.setPromptText(signUp ? "At least " + AuthService.MIN_PASSWORD_LENGTH + " characters"
                : "Enter your password");
        show(nameRow, signUp);
        show(confirmRow, signUp);
        show(errorBox, false);
        updateSubmit();

        if (animate) {
            FadeTransition fade = new FadeTransition(Duration.millis(200), card);
            fade.setFromValue(0.35);
            fade.setToValue(1);
            fade.play();
        }
        Platform.runLater(() -> (signUp ? nameField : emailField).requestFocus());
    }

    private void updateSubmit() {
        busy = false;
        submit.setDisable(auth == null);
        if (auth == null) {
            submit.setText("Connecting…");
        } else {
            submit.setText(mode == Mode.SIGN_UP ? "Create Account" : "Sign In");
        }
    }

    /** After a successful sign-in, while the user's city and stations load. */
    public void showBusy(String text) {
        busy = true;
        submit.setDisable(true);
        submit.setText(text);
        show(errorBox, false);
    }

    /** Loading after sign-in failed (e.g. the database stopped): show why and allow another try. */
    public void showSignInProblem(Throwable error) {
        updateSubmit();
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        showError("Could not open your account: " + cause.getMessage());
    }

    private void submitForm() {
        if (auth == null || busy) {
            return;
        }
        try {
            if (mode == Mode.SIGN_IN) {
                onSignedIn.accept(auth.signIn(emailField.getText(), password.text()), false);
            } else {
                onSignedIn.accept(auth.register(nameField.getText(), emailField.getText(), password.text(),
                        confirm.text()), true);
            }
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        } catch (DataAccessException ex) {
            showError("The database did not respond. Check that MySQL is running, then press Retry. ("
                    + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()) + ")");
        }
    }

    private void showError(String message) {
        errorText.setText(message);
        show(errorBox, true);
        TranslateTransition shake = new TranslateTransition(Duration.millis(55), card);
        shake.setFromX(0);
        shake.setByX(8);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> card.setTranslateX(0));
        shake.play();
    }

    // ------------------------------------------------------------------ helpers

    private static HBox inputBox(String icon, Node field) {
        HBox box = new HBox(10, Icons.create(icon, 18), field);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("auth-input");
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    /** A password box with an eye button that shows or hides the characters. */
    private static final class PasswordInput {
        final PasswordField hidden = new PasswordField();
        final TextField shown = new TextField();
        final HBox box;

        PasswordInput(String prompt) {
            hidden.setPromptText(prompt);
            shown.promptTextProperty().bind(hidden.promptTextProperty());
            shown.textProperty().bindBidirectional(hidden.textProperty());
            shown.setVisible(false);

            Button eye = new Button();
            eye.setGraphic(Icons.create(Icons.EYE, 18));
            eye.getStyleClass().add("eye-button");
            eye.setFocusTraversable(false);
            eye.setTooltip(new Tooltip("Show password"));
            eye.setOnAction(e -> {
                boolean reveal = !shown.isVisible();
                shown.setVisible(reveal);
                hidden.setVisible(!reveal);
                eye.setGraphic(Icons.create(reveal ? Icons.EYE_OFF : Icons.EYE, 18));
                eye.getTooltip().setText(reveal ? "Hide password" : "Show password");
                TextField active = reveal ? shown : hidden;
                active.requestFocus();
                active.positionCaret(active.getText().length());
            });

            box = inputBox(Icons.LOCK, new StackPane(hidden, shown));
            box.getChildren().add(eye);
        }

        String text() {
            return hidden.getText();
        }
    }
}
