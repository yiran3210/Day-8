package dev.personalagenda.view;

import java.net.URL;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class AppIcons {

    private AppIcons() {
    }

    public static void apply(Stage stage) {

        int[] sizes = { 16, 32, 64, 256 };

        for (int size : sizes) {

            String path = "/icons/agenda-icon-" + size + ".png";
            URL resource = AppIcons.class.getResource(path);

            if (resource == null) {
                throw new IllegalStateException(
                        "Missing icon resource: " + path);
            }

            stage.getIcons().add(
                    new Image(resource.toExternalForm()));
        }
    }
}