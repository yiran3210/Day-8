
package dev.personalagenda.view;

import java.time.LocalTime;

import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

public class ClockFace extends Pane {

    private static final double C = 80;

    private final Line hourHand = new Line();
    private final Line minuteHand = new Line();
    private final Line secondHand = new Line();

    public ClockFace() {

        setPrefSize(160, 160);
        setMinSize(160, 160);
        setMaxSize(160, 160);

        Circle face = new Circle(C, C, 76);
        face.setFill(Color.web("#F8FAFD"));
        face.setStroke(Color.web("#DCE4EF"));
        face.setStrokeWidth(2);

        getChildren().add(face);

        for (int i = 0; i < 12; i++) {

            double angle = 2 * Math.PI * i / 12;

            double x1 = C + 63 * Math.sin(angle);
            double y1 = C - 63 * Math.cos(angle);
            double x2 = C + 69 * Math.sin(angle);
            double y2 = C - 69 * Math.cos(angle);

            Line tick = new Line(x1, y1, x2, y2);
            tick.setStroke(Color.web("#65758A"));
            tick.setStrokeWidth(2);

            getChildren().add(tick);
        }

        setupHand(hourHand, 5, "#334155");
        setupHand(minuteHand, 3, "#5F91BC");
        setupHand(secondHand, 1.5, "#D06D72");

        Circle centre = new Circle(C, C, 4);
        centre.setFill(Color.web("#334155"));
        getChildren().add(centre);

        setTime(LocalTime.now());
    }

    private void setupHand(
            Line hand, double width, String color) {

        hand.setStartX(C);
        hand.setStartY(C);
        hand.setStroke(Color.web(color));
        hand.setStrokeWidth(width);

        getChildren().add(hand);
    }

    public void setTime(LocalTime time) {

        double hour = (time.getHour() % 12)
                + time.getMinute() / 60.0;

        setAngle(hourHand, hour / 12.0, 40);

        double minute = time.getMinute()
                + time.getSecond() / 60.0;

        setAngle(minuteHand, minute / 60.0, 55);
        setAngle(secondHand, time.getSecond() / 60.0, 61);
    }

    private void setAngle(
            Line hand, double fraction, double length) {

        double angle = 2 * Math.PI * fraction;

        hand.setEndX(C + length * Math.sin(angle));
        hand.setEndY(C - length * Math.cos(angle));
    }
}
