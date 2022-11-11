package com.github.matejonnet.osctuya.osc;

import com.github.matejonnet.osctuya.Bulb;
import com.github.matejonnet.osctuya.config.BulbConfig;
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
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class OSCApplication extends OSCPortIn {

    private static final Logger log = LoggerFactory.getLogger(OSCApplication.class);
    private final BlockingQueue<BulbCommand> commandQueue = new ArrayBlockingQueue<>(1000);
    private final Executor executor = Executors.newScheduledThreadPool(4);

    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();

    public OSCApplication(SocketAddress address, File configFile) throws IOException {
        super(address);

        log.info("Loading config {}.", configFile);
        List<BulbConfig> bulbConfigs = ConfigReader.getBulbs(configFile);
        Set<BulbWithAddresses> bulbsWithAddresses = getBulbsWithAddresses(bulbConfigs);

        Consumer<BulbCommand> onMessage = (bulbCommand) -> {
            if (!commandQueue.offer(bulbCommand)) { //TODO each bulb should have it's own queue
                log.warn("Ignoring command, queue is full!");
            }
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

        executor.execute(() -> {
            while (true) {
                try {
                    BulbCommand bulbCommand = commandQueue.take();
                    bulbCommandProcessor.process(bulbCommand);
                } catch (InterruptedException e) {
                    log.error("Interrupted while waiting for new element.", e);
                } catch (Throwable e) {
                    log.error("Cannot process command.", e);
                }
            }
        });
    }

    private Set<BulbWithAddresses> getBulbsWithAddresses(List<BulbConfig> bulbConfigs) {
        return bulbConfigs.stream()
                .filter(bc -> {
                    boolean enabled = bc.getEnabled().isEmpty() || bc.getEnabled().get().equals(true);
                    if (!enabled) {
                        log.info("Build {} is disabled.", bc.getName());
                    }
                    return enabled;
                })
                .map(bc -> getBulbWithAddresses(bc))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private BulbWithAddresses getBulbWithAddresses(BulbConfig bc) {
        Bulb bulb = new Bulb(bc.getIp(), bc.getId(), bc.getKey(), bc.getName());
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
        new OSCApplication(new InetSocketAddress("127.0.0.1", 7770), new File(configPath));
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
