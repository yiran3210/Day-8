package dev.personalagenda.view;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class IconResourcesTest {

    @Test
    void iconsHaveExpectedSizesAndTransparentCorners()
            throws Exception {

        int[] sizes = { 16, 32, 64, 128, 256, 512 };

        for (int size : sizes) {
            String path = "/icons/agenda-icon-" + size + ".png";

            try (InputStream input = AppIcons.class.getResourceAsStream(path)) {

                assertNotNull(input, path);

                BufferedImage image = ImageIO.read(input);

                assertNotNull(image, path);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());

                assertTrue(
                        image.getColorModel().hasAlpha(),
                        path + " has no alpha channel");

                int cornerAlpha = (image.getRGB(0, 0) >>> 24) & 0xFF;

                assertEquals(
                        0, cornerAlpha,
                        path + " has an opaque corner");
            }
        }
    }
}