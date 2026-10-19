package dev.personalagenda.view;

import dev.personalagenda.data.Database;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/** Configure the database DIRECTORY for the NEXT app launch only. */
public final class DataLocationDialog {

    private DataLocationDialog() {
    }

    public static void show(Stage owner) {
        Path current = Database.databasePath();

        if (Database.hasExternalDataOverride()) {
            message(owner, Alert.AlertType.INFORMATION,
                    "Data location is externally configured",
                    "A JVM property or AGENDA_DATA_DIR controls this launch. "
                            + "Remove that override before choosing a folder here.\n\n"
                            + "Current database: " + current);
            return;
        }

        Alert overview = new Alert(Alert.AlertType.CONFIRMATION);
        overview.initOwner(owner);
        overview.setTitle("Day 8 · Data location");
        overview.setHeaderText("Current database location");
        overview.setContentText(current
                + "\n\nChoose OK to select a different data folder. "
                + "Changes will apply only after restarting Day 8.");
        if (overview.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Day 8 · Choose data folder");

        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent)) {
            chooser.setInitialDirectory(parent.toFile());
        }

        File folder = chooser.showDialog(owner);
        if (folder == null)
            return;

        Path directory = folder.toPath().toAbsolutePath().normalize();
        Path selectedDatabase = directory.resolve("agenda.db");
        boolean existing = Files.isRegularFile(selectedDatabase);

        if (directory.equals(current.getParent())) {
            message(owner, Alert.AlertType.INFORMATION,
                    "Already in use", "This is the current data folder.");
            return;
        }

        String explanation = existing
                ? "Day 8 will use the existing agenda.db in this folder after a full restart."
                : "There is no agenda.db here. Day 8 will create a NEW, EMPTY database "
                        + "in this folder after a full restart.";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(owner);
        confirm.setTitle("Day 8 · Data location");
        confirm.setHeaderText(existing ? "Switch to existing database?"
                : "Create a separate empty database?");
        confirm.setContentText(
                explanation + "\n\nSelected: " + selectedDatabase
                        + "\nCurrent: " + current
                        + "\n\nNo files are moved or deleted. "
                        + "The currently running app will keep using its existing database.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            Database.setDataDirectoryForNextLaunch(directory);
            message(owner, Alert.AlertType.INFORMATION,
                    "Data location saved",
                    "Fully exit Day 8 (including the Mini Widget), "
                            + "then launch it again to apply this change.\n\n"
                            + "Next database: " + selectedDatabase);
        } catch (Exception ex) {
            message(owner, Alert.AlertType.ERROR,
                    "Unable to change data location", ex.getMessage());
        }
    }

    private static void message(Stage owner, Alert.AlertType type,
            String header, String details) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle("Day 8 · Data location");
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }
}
