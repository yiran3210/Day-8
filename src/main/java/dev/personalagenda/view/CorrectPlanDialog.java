package dev.personalagenda.view;

import dev.personalagenda.data.DailyPlanRepository;
import dev.personalagenda.data.DailyPlanRepository.CorrectableItem;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public final class CorrectPlanDialog {

    private CorrectPlanDialog() {
    }

    public static boolean show(Stage owner) {
        LocalDate date = LocalDate.now();
        DailyPlanRepository repository = new DailyPlanRepository();

        List<CorrectableItem> items;
        try {
            items = repository.findCorrectableItems(date);
        } catch (SQLException ex) {
            message(owner, Alert.AlertType.ERROR,
                    "Unable to load plan", ex.getMessage());
            return false;
        }

        if (items.isEmpty()) {
            message(owner, Alert.AlertType.INFORMATION,
                    "No recorded plan",
                    "There are no recorded plan items for today.");
            return false;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Day 8 · Correct Plan");
        dialog.setHeaderText("Correct today's recorded plan");

        ButtonType saveType = new ButtonType(
                "Save Correction", ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane().getButtonTypes().addAll(
                saveType, ButtonType.CANCEL);

        ComboBox<CorrectableItem> itemBox = new ComboBox<>(
                FXCollections.observableArrayList(items));

        itemBox.setMaxWidth(Double.MAX_VALUE);
        itemBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(CorrectableItem item) {
                if (item == null)
                    return "";
                return item.title()
                        + (item.stepId() == null ? " · Task" : " · Step");
            }

            @Override
            public CorrectableItem fromString(String text) {
                return null;
            }
        });

        Label currentLabel = new Label();
        TextField minutesInput = new TextField();
        minutesInput.setPromptText("Minutes (0 to exclude)");

        TextArea reasonInput = new TextArea();
        reasonInput.setPromptText("Reason for correction (required)");
        reasonInput.setPrefRowCount(2);
        reasonInput.setWrapText(true);

        itemBox.valueProperty().addListener((obs, oldItem, item) -> {
            if (item != null) {
                currentLabel.setText(
                        "Original: " + item.originalMinutes()
                                + " min   ·   Effective: "
                                + item.effectiveMinutes() + " min");
                minutesInput.setText(
                        Integer.toString(item.effectiveMinutes()));
            }
        });

        itemBox.getSelectionModel().selectFirst();

        VBox content = new VBox(
                10,
                new Label("Recorded Task / Step"),
                itemBox,
                currentLabel,
                new Label("Correct effective planned minutes"),
                minutesInput,
                new Label("Reason"),
                reasonInput,
                new Label("0 min excludes this item from today's statistics."));
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(460);

        boolean[] saved = { false };

        dialog.getDialogPane().lookupButton(saveType)
                .addEventFilter(ActionEvent.ACTION, event -> {

                    CorrectableItem item = itemBox.getValue();
                    String reason = reasonInput.getText().trim();
                    int minutes;

                    try {
                        minutes = Integer.parseInt(
                                minutesInput.getText().trim());

                        if (minutes < 0) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException ex) {
                        message(owner, Alert.AlertType.WARNING,
                                "Invalid minutes",
                                "Enter a non-negative whole number.");
                        event.consume();
                        return;
                    }

                    if (item == null || reason.isBlank()) {
                        message(owner, Alert.AlertType.WARNING,
                                "Missing information",
                                "Select an item and provide a correction reason.");
                        event.consume();
                        return;
                    }

                    if (!LocalDate.now().equals(date)) {
                        message(owner, Alert.AlertType.WARNING,
                                "Date changed",
                                "The date has changed. Reopen Correct Plan.");
                        event.consume();
                        return;
                    }

                    if (minutes == item.effectiveMinutes()) {
                        message(owner, Alert.AlertType.INFORMATION,
                                "No change",
                                "The effective planned minutes are unchanged.");
                        event.consume();
                        return;
                    }

                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.initOwner(owner);
                    confirm.setTitle("Confirm Plan Correction");
                    confirm.setHeaderText(item.title());
                    confirm.setContentText(
                            item.effectiveMinutes() + " → " + minutes
                                    + " min\n\nReason: " + reason
                                    + "\n\nThe original snapshot will be preserved.");

                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        event.consume();
                        return;
                    }

                    try {
                        saved[0] = repository.correctPlannedMinutes(
                                date, item.itemKey(), minutes, reason);

                        if (!saved[0]) {
                            event.consume();
                        }
                    } catch (SQLException | IllegalArgumentException ex) {
                        message(owner, Alert.AlertType.ERROR,
                                "Correction failed", ex.getMessage());
                        event.consume();
                    }
                });

        dialog.showAndWait();
        return saved[0];
    }

    private static void message(
            Stage owner,
            Alert.AlertType type,
            String header,
            String details) {

        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle("Day 8 · Correct Plan");
        alert.setHeaderText(header);
        alert.setContentText(details);
        alert.showAndWait();
    }
}