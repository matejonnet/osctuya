package com.github.matejonnet.osctuya.osc;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.*;

public class QueuePerBulbCommandScheduler implements CommandScheduler {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private Map<String, CircularFifoQueue<BulbCommand>> bulbQueues = new ConcurrentHashMap<>();

    private Semaphore semaphore = new Semaphore(0);

    private final ExecutorService executor = Executors.newScheduledThreadPool(4);

    private ScheduledExecutorService cancelMonitor = Executors.newScheduledThreadPool(1);

    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();

    public QueuePerBulbCommandScheduler() {
        executor.execute(() -> {
            while (true) {
                try {
                    for (var bulbQueue : bulbQueues.values()) {
                        BulbCommand bulbCommand = bulbQueue.poll();
                        if (bulbCommand != null) {
                            Future<?> future = executor.submit(() -> bulbCommandProcessor.process(bulbCommand));
                            cancelMonitor.schedule(() -> future.cancel(true), 1000, TimeUnit.MILLISECONDS);
                        }
                        // Because some messages could be dropped from the queue,
                        // loops without a command can happen until all the permits are acquired.
                        semaphore.acquire();
                    }
                } catch (Throwable e) {
                    log.error("Cannot process command.", e);
                }
            }
        });
    }

    @Override
    public void submit(BulbCommand bulbCommand) {
        String bulbName = bulbCommand.bulb().getName();
        CircularFifoQueue bulbQueue = bulbQueues.computeIfAbsent(bulbName, (k) -> new CircularFifoQueue<>(10));
        bulbQueue.offer(bulbCommand);
        semaphore.release();
    }


}
