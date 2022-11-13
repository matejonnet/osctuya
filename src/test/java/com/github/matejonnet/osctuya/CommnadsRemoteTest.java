package com.github.matejonnet.osctuya;

import com.github.matejonnet.osctuya.config.Config;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class CommnadsRemoteTest {

    private static final Logger logger = LoggerFactory.getLogger(CommnadsRemoteTest.class);

    private static final String DEVICE_ID = "0123456789abcdef012345";
    private static final String DEVICE_IP = "192.168.0.100";
    private static final String DEVICE_KEY = "0123456789abcdef";

    @Disabled
    @Test
    public void runCommands() throws IOException, InterruptedException {

        Config config = Config.builder().build();
        Bulb bulb = new Bulb(DEVICE_IP, DEVICE_ID, DEVICE_KEY, DEVICE_IP, config);
        bulb.connect();

        bulb.setPower(true);
        bulb.setBrightness(100);
//        bulb.setTemperature(0);
//        bulb.setColor(new Color(247, 0, 255));
//        Thread.sleep(5000L);

        logger.info("Dimm 0 -> 100 ...");
//        bulb.setPower(true);
//            for (int i = 0; i <= 20; i++) {
//                int percentage = i * 5;
//                logger.info("  brightness: {}.", percentage);
//                bulb.setBrightness(percentage);
//                Thread.sleep(250L);
//            }

        logger.info("Strobo on/off ...");
        for (int i = 0; i < 1000; i++) {
            bulb.setPower(true);
            Thread.sleep(1500L); //min 200
            bulb.setPower(false);
            Thread.sleep(1500L);
        }

        logger.info("Dimm on/off ...");
        bulb.setPower(true);
        for (int i = 0; i < 4; i++) {
            bulb.setBrightness(0);
            Thread.sleep(1000L);
            bulb.setBrightness(100);
            Thread.sleep(1000L);
        }

        logger.info("Color temperature ...");
        for (int i = 50; i > 0; i--) {
            int value = i * 20;
            logger.info("  temperature: {}.", value);
            bulb.setTemperature(value);
            Thread.sleep(250L);
        }

        logger.info("Red 0 -> 250 ...");
        for (int i = 0; i < 50; i++) {
            bulb.setColor(new Color(i*5, 0, 0));
            Thread.sleep(250L);
        }

        logger.info("Rainbow ...");
        for (Color rgbColor : getRainbowColors()) {
            bulb.setColor(rgbColor);
            Thread.sleep(500L);
        }
        bulb.setTemperature(200);
        bulb.setBrightness(0);

        Thread.sleep(500L);

        bulb.close();

    }

    private static ArrayList<Color> getRainbowColors() {
        var colors = new ArrayList<Color>();
        for (int r=0; r<100; r++) colors.add(new Color(r*255/100,       255,         0));
        for (int g=100; g>0; g--) colors.add(new Color(      255, g*255/100,         0));
        for (int b=0; b<100; b++) colors.add(new Color(      255,         0, b*255/100));
        for (int r=100; r>0; r--) colors.add(new Color(r*255/100,         0,       255));
        for (int g=0; g<100; g++) colors.add(new Color(        0, g*255/100,       255));
        for (int b=100; b>0; b--) colors.add(new Color(        0,       255, b*255/100));
        colors.add(new Color(0, 255, 0));
        return colors;
    }

}
