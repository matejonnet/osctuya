package com.github.matejonnet.osctuya.osc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DirectCommandScheduler implements CommandScheduler {

    public static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Executor executor = Executors.newScheduledThreadPool(4);


    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();

    @Override
    public void submit(BulbCommand bulbCommand) {
        bulbCommandProcessor.process(bulbCommand);
    }
}
