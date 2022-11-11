package com.github.matejonnet.osctuya;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Mapper {

    private static Map<String, ObjectMapper> instance = new ConcurrentHashMap<>();

    public static ObjectMapper getJson() {
        return instance.computeIfAbsent("json", (k) -> new ObjectMapper());
    }

    public static ObjectMapper getYaml() {
        return instance.computeIfAbsent("yaml", (k) -> {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            return mapper;
        });
    }

}
