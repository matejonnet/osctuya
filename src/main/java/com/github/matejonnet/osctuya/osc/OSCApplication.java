package com.github.matejonnet.osctuya.osc;

import com.github.matejonnet.osctuya.Bulb;
import com.github.matejonnet.osctuya.config.BulbConfig;
import com.github.matejonnet.osctuya.config.Config;
import com.github.matejonnet.osctuya.config.ConfigReader;
import com.github.matejonnet.osctuya.config.OscAddress;
import com.illposed.osc.OSCBadDataEvent;
import com.illposed.osc.OSCBadDataListener;
import com.illposed.osc.OSCMessageListener;
import com.illposed.osc.messageselector.JavaRegexAddressMessageSelector;
import com.illposed.osc.transport.OSCPortIn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class OSCApplication extends OSCPortIn {

    private static final Logger log = LoggerFactory.getLogger(OSCApplication.class);

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    public OSCApplication(Config config) throws IOException {
        super(new InetSocketAddress(config.bindHost, config.bindPort));

        CommandScheduler commandScheduler = new RepeatableExecutorPerBulbCommandScheduler();

        Set<BulbWithAddresses> bulbsWithAddresses = getBulbsWithAddresses(config.getBulbs(), config);

        Consumer<BulbCommand> onMessage = (bulbCommand) -> {
            commandScheduler.submit(bulbCommand);
        };
        OSCMessageListener listener = new TuyaMessageListener(bulbsWithAddresses, onMessage);
        // select all messages
        getDispatcher().addListener(new JavaRegexAddressMessageSelector(".*"), listener);
        // log errors to console
        getDispatcher().addBadDataListener(new PrintBadDataListener());
        // never stop listening
        setResilient(true);
        setDaemonListener(false);
        startListening();
        log.info("# Listening for OSC Packets via {} ...", getTransport());

        bulbsWithAddresses.parallelStream().forEach(bulbWithAddresses -> {
            try {
                bulbWithAddresses.getBulb().connect();
            } catch (IOException e) {
                log.error("Cannot connect bulb {}.", bulbWithAddresses.getBulb().getName());
            }
        });
    }

    private Set<BulbWithAddresses> getBulbsWithAddresses(List<BulbConfig> bulbConfigs, Config config) {
        return bulbConfigs.stream()
                .filter(bc -> {
                    boolean enabled = bc.getEnabled().isEmpty() || bc.getEnabled().get().equals(true);
                    if (!enabled) {
                        log.info("Build {} is disabled.", bc.getName());
                    }
                    return enabled;
                })
                .map(bc -> getBulbWithAddresses(bc, config))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private BulbWithAddresses getBulbWithAddresses(BulbConfig bc, Config config) {
        Bulb bulb = new Bulb(bc.getIp(), bc.getId(), bc.getKey(), bc.getName(), config);
        BulbWithAddresses bulbWithAddresses = new BulbWithAddresses(bulb);
        OscAddress addresses = bc.getOsc().getAddresses();

        addresses.getPower().ifPresent(v -> bulbWithAddresses.putMapping(TuyaCommand.POWER, v));
        addresses.getBrighnes().ifPresent(v -> bulbWithAddresses.putMapping(TuyaCommand.BRIGHTNESS, v));
        addresses.getTemperature().ifPresent(v -> bulbWithAddresses.putMapping(TuyaCommand.TEMPERATURE, v));
        addresses.getRed().ifPresent(v -> bulbWithAddresses.putMapping(TuyaCommand.RED, v));
        addresses.getGreen().ifPresent(v -> bulbWithAddresses.putMapping(TuyaCommand.GREEN, v));
        addresses.getBlue().ifPresent(v -> bulbWithAddresses.putMapping(TuyaCommand.BLUE, v));
        return bulbWithAddresses;
    }

    public static void main(String[] args) throws IOException {
        log.info("Args: {}.", args);
        String configPath;
        if (args.length == 1) {
            configPath = args[0];
        } else {
            log.error("Missing arguments: first argument must be a path to the bulbs config.");
            return;
        }

        File configFile = new File(configPath);
        log.info("Loading config from {}.", configFile);
        Config config = ConfigReader.getConfig(configFile);


        new OSCApplication(config);
    }

    private final class PrintBadDataListener implements OSCBadDataListener {

        PrintBadDataListener() {
            // declared only for setting the access level
        }

        @Override
        public void badDataReceived(final OSCBadDataEvent evt) {
            if (log.isWarnEnabled()) {
                log.warn("Bad packet received ...", evt.getException());
                log.warn("### Received data (bad): ###{}###",
                        new String(evt.getData().array(), StandardCharsets.UTF_8));
            }
        }
    }
}
