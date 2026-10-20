package com.vgrid;

import com.vgrid.controller.AppController;
import com.vgrid.controller.Screen;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * VGrid - Smart Electric Vehicle Charging Management System (frontend demo).
 * Runs fully offline: all data is sample data kept in memory.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        AppController app = new AppController();

        Scene scene = new Scene(app.getRoot(), 1320, 840);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/style.css"), "style.css not found").toExternalForm());
        app.installShortcuts(scene);

        stage.setTitle("VGrid - Smart EV Charging (Demo)");
        stage.setMinWidth(980);
        stage.setMinHeight(660);
        stage.setScene(scene);
        app.navigate(Screen.DASHBOARD);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
