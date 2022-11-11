package com.github.matejonnet.osctuya;

import com.github.matejonnet.osctuya.config.BulbConfig;
import com.github.matejonnet.osctuya.config.ConfigReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ConfigurationTest {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationTest.class);

    @Test
    public void shouldReadTheConfig() throws IOException {
        List<BulbConfig> bulbs = ConfigReader.getBulbs(new File(CommnadsRemoteTest.class.getClassLoader().getResource("bulbs.yaml").getFile()));
        log.info("toString: {}", bulbs);
        Assertions.assertTrue(bulbs.size() > 0);
    }
}
