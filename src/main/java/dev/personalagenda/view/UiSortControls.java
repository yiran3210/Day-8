
package dev.personalagenda.view;

import dev.personalagenda.data.UiSortOrderRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class UiSortControls {

    private static final String PREFIX = "day8-ui-sort|";

    private UiSortControls() {
    }

    public static HBox attach(
            String scope,
            String itemId,
            VBox itemRow,
            VBox list,
            Runnable refresh,
            Consumer<String> showError) {

        itemRow.setUserData(itemId);

        UiSortOrderRepository repository = new UiSortOrderRepository();

        String tokenPrefix = PREFIX + scope + "|";

        Label handle = new Label("☰");
        handle.setTooltip(new Tooltip(
                "Drag to reorder within this list"));
        handle.setStyle(
                "-fx-cursor: move; -fx-padding: 5px;");

        handle.setOnDragDetected(event -> {
            Dragboard drag = handle.startDragAndDrop(
                    TransferMode.MOVE);

            ClipboardContent content = new ClipboardContent();
            content.putString(tokenPrefix + itemId);
            drag.setContent(content);

            event.consume();
        });

        itemRow.setOnDragOver(event -> {
            Dragboard drag = event.getDragboard();

            if (drag.hasString()
                    && drag.getString().startsWith(tokenPrefix)
                    && !Objects.equals(
                            drag.getString(),
                            tokenPrefix + itemId)) {

                event.acceptTransferModes(TransferMode.MOVE);
            }

            event.consume();
        });

        itemRow.setOnDragDropped(event -> {
            Dragboard drag = event.getDragboard();
            boolean success = false;

            if (drag.hasString()
                    && drag.getString().startsWith(tokenPrefix)) {

                String sourceId = drag.getString()
                        .substring(tokenPrefix.length());

                try {
                    repository.move(
                            scope,
                            ids(list),
                            sourceId,
                            itemId,
                            event.getY() >= itemRow.getHeight() / 2);

                    success = true;
                    event.setDropCompleted(true);
                    refresh.run();

                } catch (SQLException
                        | IllegalArgumentException ex) {
                    showError.accept(
                            "Unable to reorder: " + ex.getMessage());
                }
            }

            if (!success) {
                event.setDropCompleted(false);
            }

            event.consume();
        });

        Button up = new Button("↑");
        Button down = new Button("↓");

        up.setTooltip(new Tooltip("Move one position up"));
        down.setTooltip(new Tooltip("Move one position down"));

        up.setOnAction(event -> shift(scope, itemId, -1, list,
                repository, refresh, showError));

        down.setOnAction(event -> shift(scope, itemId, 1, list,
                repository, refresh, showError));

        return new HBox(2, handle, up, down);
    }

    private static List<String> ids(VBox list) {
        return list.getChildren().stream()
                .map(Node::getUserData)
                .map(value -> (String) value)
                .toList();
    }

    private static void shift(
            String scope,
            String itemId,
            int direction,
            VBox list,
            UiSortOrderRepository repository,
            Runnable refresh,
            Consumer<String> showError) {

        List<String> shown = ids(list);
        int pos = shown.indexOf(itemId);
        int neighbor = pos + direction;

        if (pos < 0
                || neighbor < 0
                || neighbor >= shown.size()) {
            return;
        }

        try {
            repository.move(
                    scope,
                    shown,
                    itemId,
                    shown.get(neighbor),
                    direction > 0);

            refresh.run();

        } catch (SQLException
                | IllegalArgumentException ex) {
            showError.accept(
                    "Unable to reorder: " + ex.getMessage());
        }
    }
}
