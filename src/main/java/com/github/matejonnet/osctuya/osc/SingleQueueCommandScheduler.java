package com.github.matejonnet.osctuya.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SingleQueueCommandScheduler implements CommandScheduler {

    public static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BlockingQueue<BulbCommand> commandQueue = new ArrayBlockingQueue<>(1000);
    private final Executor executor = Executors.newScheduledThreadPool(4);


    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();

    public SingleQueueCommandScheduler() {
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

    @Override
    public void submit(BulbCommand bulbCommand) {
        if (!commandQueue.offer(bulbCommand)) { //TODO each bulb should have it's own queue
            log.warn("Ignoring command, queue is full!");
        }
    }
}
