package com.github.matejonnet.osctuya.osc;

import com.github.matejonnet.osctuya.Bulb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bulb with mapped OSC addresses and commands
 *
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class BulbWithAddresses {

    /**
     * Map contains address and {@link TuyaCommand} mappings.
     */
    private Map<String, CommandMapping> commands = new HashMap<>();
    private Bulb bulb;

    public BulbWithAddresses(Bulb bulb) {
        this.bulb = bulb;
    }

    public void putMapping(TuyaCommand command, String address) {
        commands.put(address, new CommandMapping(address, command));
    }

    public Optional<TuyaCommand> getCommand(String address) {
        CommandMapping commandMapping = commands.get(address);
        if (commandMapping != null) {
            return Optional.of(commandMapping.command());
        } else {
            return Optional.empty();
        }
    }

    public Bulb getBulb() {
        return bulb;
    }
}
