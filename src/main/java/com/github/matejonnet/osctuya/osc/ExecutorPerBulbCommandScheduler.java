package com.github.matejonnet.osctuya.osc;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExecutorPerBulbCommandScheduler implements CommandScheduler {

    private final Map<String, ExecutorService> bulbExecutors = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cancelMonitor = Executors.newScheduledThreadPool(1);

    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();

    @Override
    public void submit(BulbCommand bulbCommand) {
        String bulbName = bulbCommand.bulb().getName();
        ExecutorService bulbExecutor = bulbExecutors.computeIfAbsent(bulbName, (k) -> newBulbExecutor(10));

        Future future = bulbExecutor.submit(() -> bulbCommandProcessor.process(bulbCommand));
        cancelMonitor.schedule(() -> future.cancel(true), 500, TimeUnit.MILLISECONDS);
    }

    private ExecutorService newBulbExecutor(int capacity) {

        // not good to drop old messages as it might be a color change and we lose the data of the individual color
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity) {
            Lock lock = new ReentrantLock(true);
            public boolean offer(Runnable e) {
                Objects.requireNonNull(e);
                lock.lock();
                try {
                    if (remainingCapacity() == 0) {
                        poll();
                    }
                    return super.offer(e);
                } finally {
                    lock.unlock();
                }
            }
        };
        return new ThreadPoolExecutor(1, 1, 1, TimeUnit.HOURS, queue);
    }
}
