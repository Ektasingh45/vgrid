package com.vgrid;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Screen;
import com.vgrid.dao.DataLayer;
import com.vgrid.data.AppConfig;
import com.vgrid.data.DataLayerFactory;
import com.vgrid.model.User;
import com.vgrid.model.UserSettings;
import com.vgrid.service.StationImportService;
import com.vgrid.service.UserSession;
import com.vgrid.view.LoginView;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * VGrid - Smart Electric Vehicle Charging Management System.
 *
 * Start-up: show the login page, connect to MySQL in the background (falling back to offline mode
 * if MySQL is not available), then, after sign-in, load the user's city and open the main app.
 */
public class MainApp extends Application {
    private Stage stage;
    private Scene scene;
    private LoginView loginView;
    private AppConfig config;
    private DataLayerFactory.Result connection;
    private AppController app;

    /** What the sign-in background work produces. */
    private record SignInData(UserSettings settings, String stationWarning) {
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        scene = new Scene(new StackPane(), 1320, 840);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/style.css"), "style.css not found").toExternalForm());

        // Anything that fails unexpectedly (e.g. MySQL stopped while the app is open) becomes a message, not a crash.
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            if (app != null) {
                app.showError(error);
            }
        });

        stage.setTitle("VGrid - Smart EV Charging");
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        stage.setScene(scene);
        showLogin();
        stage.show();
        connect();
    }

    /** Talks to MySQL on a background thread, so the window never freezes while connecting. */
    private void connect() {
        loginView.showConnecting();
        config = AppConfig.load(); // read db.properties again, so edits are picked up by Retry
        AppConfig current = config;
        Task<DataLayerFactory.Result> task = new Task<>() {
            @Override
            protected DataLayerFactory.Result call() {
                return DataLayerFactory.open(current);
            }
        };
        task.setOnSucceeded(e -> {
            connection = task.getValue();
            loginView.showConnected(connection);
            stage.setTitle(connection.layer().isDatabase()
                    ? "VGrid - Smart EV Charging" : "VGrid - Smart EV Charging (Offline Mode)");
        });
        task.setOnFailed(e -> task.getException().printStackTrace());
        Thread thread = new Thread(task, "vgrid-db-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void showLogin() {
        app = null;
        loginView = new LoginView(this::openApp, this::connect);
        if (connection != null) {
            loginView.showConnected(connection);
        }
        scene.setRoot(loginView);
    }

    /**
     * After sign-in: load the user's settings and make sure their city's stations are in the database
     * (downloaded from Open Charge Map the first time). This runs in the background, then the app opens.
     */
    private void openApp(User user, boolean newAccount) {
        DataLayer data = connection.layer();
        StationImportService importService = new StationImportService(data.stations(), config);
        loginView.showBusy("Loading charging stations…");

        Task<SignInData> task = new Task<>() {
            @Override
            protected SignInData call() throws Exception {
                UserSettings settings = data.settings().loadSettings(user.id());
                try {
                    return new SignInData(settings, importService.ensureLoaded(settings.getCity()).warning());
                } catch (IOException e) {
                    // Open the app anyway; the city simply has no stations until the problem is fixed.
                    return new SignInData(settings, e.getMessage());
                }
            }
        };
        task.setOnSucceeded(e -> {
            SignInData result = task.getValue();
            UserSession session = new UserSession(user, result.settings());
            app = new AppController(data, session, importService, this::showLogin);
            if (newAccount) {
                app.getNotificationService().welcome();
            }
            scene.setRoot(app.getRoot());
            app.navigate(Screen.DASHBOARD);
            if (result.stationWarning() != null) {
                app.showToast(result.stationWarning(), AppController.ToastType.INFO);
            } else {
                app.showToast(newAccount ? "Welcome to VGrid, " + user.name() + "! Your account is ready."
                        : "Signed in as " + user.name(), AppController.ToastType.SUCCESS);
            }
        });
        task.setOnFailed(e -> loginView.showSignInProblem(task.getException()));
        Thread thread = new Thread(task, "vgrid-sign-in");
        thread.setDaemon(true);
        thread.start();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
