package com.github.matejonnet.osctuya.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.github.matejonnet.osctuya.Mapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ConfigReader {

    public static List<BulbConfig> getBulbs(File configFile) throws IOException {
        Mapper.getYaml().registerModule(new Jdk8Module());
        return Mapper.getYaml().readValue(configFile, new TypeReference<>() {});
    }
}
