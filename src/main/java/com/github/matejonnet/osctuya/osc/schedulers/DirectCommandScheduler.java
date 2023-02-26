package com.github.matejonnet.osctuya.osc.schedulers;

import com.github.matejonnet.osctuya.osc.BulbCommand;
import com.github.matejonnet.osctuya.osc.BulbCommandProcessor;
import com.github.matejonnet.osctuya.osc.CommandScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class DirectCommandScheduler implements CommandScheduler {

    public static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final BulbCommandProcessor bulbCommandProcessor = new BulbCommandProcessor();

    @Override
    public void submit(BulbCommand bulbCommand) {
        bulbCommandProcessor.process(bulbCommand);
    }
}
