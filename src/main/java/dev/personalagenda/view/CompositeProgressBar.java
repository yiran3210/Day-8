
package dev.personalagenda.view;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Line;

public class CompositeProgressBar extends Pane {

        private final DoubleProperty completedRatio = new SimpleDoubleProperty(0);

        private final DoubleProperty todayRatio = new SimpleDoubleProperty(0);

        private final DoubleProperty expectedRatio = new SimpleDoubleProperty(0);

        private final Line expectedMarker = new Line();

        private final Rectangle completed = new Rectangle();
        private final Rectangle today = new Rectangle();
        private final Rectangle future = new Rectangle();

        public CompositeProgressBar() {

                setMinHeight(16);
                setPrefHeight(16);
                setMaxHeight(16);
                setMaxWidth(Double.MAX_VALUE);

                completed.widthProperty().bind(
                                widthProperty().multiply(completedRatio));

                today.layoutXProperty().bind(
                                completed.widthProperty());
                today.widthProperty().bind(
                                widthProperty().multiply(todayRatio));

                future.layoutXProperty().bind(
                                completed.widthProperty().add(today.widthProperty()));
                future.widthProperty().bind(
                                widthProperty()
                                                .subtract(completed.widthProperty())
                                                .subtract(today.widthProperty()));

                for (Rectangle part : new Rectangle[] {
                                completed, today, future }) {

                        part.heightProperty().bind(heightProperty());
                }

                future.setFill(Color.web("#F0F3F7"));

                getChildren().addAll(
                                future, completed, today);

                expectedMarker.startXProperty().bind(
                                widthProperty().multiply(expectedRatio));

                expectedMarker.endXProperty().bind(
                                widthProperty().multiply(expectedRatio));

                expectedMarker.endYProperty().bind(heightProperty());

                expectedMarker.setStroke(Color.web("#354052"));
                expectedMarker.setStrokeWidth(2);
                expectedMarker.setMouseTransparent(true);

                getChildren().add(expectedMarker);

                Rectangle clip = new Rectangle();
                clip.widthProperty().bind(widthProperty());
                clip.heightProperty().bind(heightProperty());
                clip.setArcWidth(14);
                clip.setArcHeight(14);
                setClip(clip);

                setColor(Color.web("#5F91BC"));

        }

        public void setColor(Color color) {
                completed.setFill(color);
                today.setFill(
                                color.interpolate(Color.WHITE, 0.68));
        }

        public void setValues(
                        double completedFraction,
                        double todayPendingFraction) {

                double done = Math.max(
                                0, Math.min(1, completedFraction));

                double pending = Math.max(
                                0, Math.min(1 - done, todayPendingFraction));

                completedRatio.set(done);
                todayRatio.set(pending);
        }

        public void setExpected(double fraction) {
                expectedRatio.set(
                                Math.max(0, Math.min(1, fraction)));
        }
}
