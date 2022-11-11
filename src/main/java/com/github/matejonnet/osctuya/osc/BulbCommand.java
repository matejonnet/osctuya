package com.github.matejonnet.osctuya.osc;

import com.github.matejonnet.osctuya.Bulb;

import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public record BulbCommand(Bulb bulb, TuyaCommand command, List<Object> arguments) {

}
