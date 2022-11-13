package com.github.matejonnet.osctuya;

import com.github.matejonnet.osctuya.config.BulbConfig;
import com.github.matejonnet.osctuya.config.Config;
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
        Config config = ConfigReader.getConfig(new File(CommnadsRemoteTest.class.getClassLoader().getResource("config.yaml").getFile()));
        Assertions.assertEquals("127.0.0.1", config.getBindHost());

        List<BulbConfig> bulbs = config.getBulbs();
        log.info("toString: {}", bulbs);
        Assertions.assertTrue(bulbs.size() > 0);
    }
}
