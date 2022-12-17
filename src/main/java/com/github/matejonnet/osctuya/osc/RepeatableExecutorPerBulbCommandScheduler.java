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

    /**
     * Drop old commands when there are more than maxEnqueuedCommands waiting.
     */
    private int maxEnqueuedCommands;

    /**
     * Repeat last command repeatCommandTimes.
     */
    private int repeatCommandTimes = 5;

    /**
     * Repeat commands after the delay.
     */
    private long repeatDelayMillis = 100;

    /**
     * Should be >=300ms, because there is a retry in the Bulb.connection.
     */
    private long commandTimeoutMillis = 300;

    public RepeatableExecutorPerBulbCommandScheduler() {
    }


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
            if (perBulb.futures.size() >= maxEnqueuedCommands) {
                // not good to drop old messages as it might be a color change and we lose the data of the individual color
                log.warn("Removing stale commands for bulb ", bulbCommand.bulb().getName());
                perBulb.futures.poll().future.cancel(false);
            }
        } finally {
            perBulb.lock.unlock();
        }

        /**
         * repeatDelayMillis=100
         * commandTimeoutMillis=500
         *
         * scheduled0 runAt:0 timeoutAt:500
         * scheduled1 runAt:100 timeoutAt:600; actualRun due to scheduled0 occupying the thread at 500
         * scheduled2 runAt:400 timeoutAt:900; actualRun due to scheduled1 occupying the thread at 600
         *
         * repeatDelayMillis=100
         * commandTimeoutMillis=300
         *
         * scheduled0 runAt:0 timeoutAt:300
         * scheduled1 runAt:100 timeoutAt:400; actualRun due to scheduled0 occupying the thread at 300
         * scheduled2 runAt:400 timeoutAt:700; actualRun due to scheduled1 occupying the thread at 400
         *
         * repeatDelayMillis=200
         * commandTimeoutMillis=300
         *
         * scheduled0 runAt:0 timeoutAt:300
         * scheduled1 runAt:200 timeoutAt:500; actualRun due to scheduled0 occupying the thread at 300
         * scheduled2 runAt:800 timeoutAt:1100; actualRun due to scheduled1 occupying the thread at 800
         */
        for (int i = 0; i < repeatCommandTimes; i++) {
            long scheduleAfter = repeatDelayMillis * i * i;
            ScheduledFuture<?> future = perBulb.executor.schedule(() -> bulbCommandProcessor.process(bulbCommand), scheduleAfter, TimeUnit.MILLISECONDS);
            perBulb.futures.offer(new CommandFuture(future, i > 0));
            cancelMonitor.schedule(() -> {
                log.warn("Cancelling command {} for bulb {}.", bulbCommand.command().name(), bulbCommand.bulb().getName());
                future.cancel(true);
                perBulb.futures.remove(future);
            }, scheduleAfter + commandTimeoutMillis, TimeUnit.MILLISECONDS);
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
