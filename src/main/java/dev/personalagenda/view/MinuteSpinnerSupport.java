package dev.personalagenda.view;

import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

public final class MinuteSpinnerSupport {

    private MinuteSpinnerSupport() {
    }

    public static void enable(Spinner<Integer> spinner) {
        spinner.setEditable(true);

        spinner.getEditor().setOnAction(event -> {
            try {
                read(spinner);
            } catch (IllegalArgumentException ex) {
                spinner.getEditor().selectAll();
            }
        });

        spinner.getEditor().focusedProperty().addListener(
                (obs, wasFocused, isFocused) -> {
                    if (wasFocused && !isFocused) {
                        try {
                            read(spinner);
                        } catch (IllegalArgumentException ignored) {
                            // Keep the invalid text for save-time validation.
                        }
                    }
                });
    }

    public static int read(Spinner<Integer> spinner) {
        String text = spinner.getEditor().getText().trim();

        if (!text.matches("[0-9]+")) {
            throw new IllegalArgumentException(
                    "Minutes must be a whole number.");
        }

        int minutes;

        try {
            minutes = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "Minutes are outside the supported range.");
        }

        if (!(spinner.getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory factory)) {
            throw new IllegalStateException(
                    "Expected an integer minutes spinner.");
        }

        if (minutes < factory.getMin()
                || minutes > factory.getMax()) {
            throw new IllegalArgumentException(
                    "Minutes must be between "
                            + factory.getMin() + " and "
                            + factory.getMax() + ".");
        }

        spinner.getValueFactory().setValue(minutes);
        return minutes;
    }
}