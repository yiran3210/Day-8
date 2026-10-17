package dev.personalagenda.view;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

public final class UiLayout {

    private UiLayout() {
        // Utility class: do not instantiate.
    }

    public static HBox rightActions(Button... buttons) {

        HBox row = new HBox(8, buttons);

        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMaxWidth(Double.MAX_VALUE);

        for (Button button : buttons) {
            button.setMinWidth(Region.USE_PREF_SIZE);
        }

        return row;
    }
}