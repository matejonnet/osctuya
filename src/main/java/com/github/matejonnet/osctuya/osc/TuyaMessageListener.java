package com.github.matejonnet.osctuya.osc;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCMessageEvent;
import com.illposed.osc.OSCMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class TuyaMessageListener implements OSCMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TuyaMessageListener.class);

    private Set<BulbWithAddresses> bulbsWithAddresses;
    private Consumer<BulbCommand> onCommand;

    public TuyaMessageListener(Set<BulbWithAddresses> bulbsWithAddressees, Consumer<BulbCommand> onCommand) {
        this.bulbsWithAddresses = bulbsWithAddressees;
        this.onCommand = onCommand;
    }

    @Override
    public void acceptMessage(OSCMessageEvent oscMessageEvent) {
        try {
            OSCMessage message = oscMessageEvent.getMessage();
            String address = message.getAddress();
            getBulbCommand(address, message.getArguments())
                    .ifPresentOrElse(
                        bulbCommand -> {
                            log.debug("Received addr:{}, arg:{}.", message.getAddress(), message.getArguments());
                            onCommand.accept(bulbCommand);
                        },
                        () -> log.debug("Ignoring unmapped address: {}.", address)
                    );
        } catch (Throwable e) {
            log.error("Failed to handle input message.", e);
        }
    }

    private Optional<BulbCommand> getBulbCommand(String address, List<Object> arguments) {
        for (BulbWithAddresses bulbWithAddresses : bulbsWithAddresses) {
            Optional<TuyaCommand> maybeCommand = bulbWithAddresses.getCommand(address);
            if (maybeCommand.isPresent()) {
                return Optional.of(new BulbCommand(bulbWithAddresses.getBulb(), maybeCommand.get(), arguments));
            }
        }
        return Optional.empty();
    }

}
