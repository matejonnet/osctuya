package com.github.matejonnet.osctuya.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RepeatableExecutorPerBulbCommandScheduler implements CommandScheduler {

    public static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Map<String, PerBulb> bulbExecutors = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cancelMonitor = Executors.newScheduledThreadPool(1);

    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();


    @Override
    public void submit(BulbCommand bulbCommand) {
        String bulbName = bulbCommand.bulb().getName();
        PerBulb perBulb = bulbExecutors.computeIfAbsent(bulbName, (k) -> new PerBulb());

        perBulb.lock.lock();
        try {
            // when a new command is received cancel all scheduled repeats
            perBulb.futures.removeIf(commandFuture -> {
                if (commandFuture.isRepeat) {
                    commandFuture.future.cancel(false);
                    return true;
                } else {
                    return false;
                }
            });

            // remove and cancel first task if there are too many waiting
            if (perBulb.futures.size() >= 10) {
                log.warn("Removing stale commands for bulb ", bulbCommand.bulb().getName());
                perBulb.futures.poll().future.cancel(false);
            }
        } finally {
            perBulb.lock.unlock();
        }

        for (int i = 0; i < 5; i++) { //TODO configurable timeouts
            ScheduledFuture<?> future = perBulb.executor.schedule(() ->  bulbCommandProcessor.process(bulbCommand), 100 * i*i, TimeUnit.MILLISECONDS);
            perBulb.futures.offer(new CommandFuture(future, i > 0));
            cancelMonitor.schedule(() -> {
                log.warn("Cancelling delayed command {} for bulb {}.", bulbCommand.command().name(), bulbCommand.bulb().getName());
                future.cancel(true);
                perBulb.futures.remove(future);
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    private class CommandFuture {
        Future future;
        boolean isRepeat;

        Instant submitted;

        public CommandFuture(Future future, boolean isRepeat) {
            this.future = future;
            this.isRepeat = isRepeat;
            this.submitted = Instant.now();
        }
    }

    private class PerBulb {

        Lock lock = new ReentrantLock(true);
        ScheduledExecutorService executor;
        Queue<CommandFuture> futures;

        public PerBulb() {
            executor = Executors.newScheduledThreadPool(1);
            futures = new ConcurrentLinkedQueue<>();
        }
    }

}
