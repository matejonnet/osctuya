package com.github.matejonnet.osctuya.osc;

import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class BulbCommandProcessor {

    public void process(BulbCommand bulbCommand) {
        switch (bulbCommand.command()) {
            case POWER:
                bulbCommand.bulb().setPower(parsePower(bulbCommand.arguments()));
                break;
            case BRIGHTNESS:
                bulbCommand.bulb().setBrightness(parseBrightness(bulbCommand.arguments()));
                break;
            case TEMPERATURE:
                bulbCommand.bulb().setTemperature(parseTemperature(bulbCommand.arguments()));
                break;
            case RED:
                bulbCommand.bulb().updateRed(parseColor(bulbCommand.arguments()));
                break;
            case GREEN:
                bulbCommand.bulb().updateGreen(parseColor(bulbCommand.arguments()));
                break;
            case BLUE:
                bulbCommand.bulb().updateBlue(parseColor(bulbCommand.arguments()));
                break;
        }

    }

    private boolean parsePower(List<Object> arguments) {
        return (Float)arguments.get(0) > 0.1;
    }

    private int parseBrightness(List<Object> arguments) {
        return Math.round((Float)arguments.get(0) * 100);
    }

    private int parseTemperature(List<Object> arguments) {
        return Math.round((Float)arguments.get(0) * 1000);
    }

    private int parseColor(List<Object> arguments) {
        return Math.round(255 * (float)arguments.get(0));
    }
}
