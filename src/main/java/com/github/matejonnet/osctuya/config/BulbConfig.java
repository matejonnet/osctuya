package com.github.matejonnet.osctuya.config;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Optional;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Value
@Builder
@Jacksonized
public class BulbConfig {

    private final String name;
    private final String ip;
    private final String id;
    private final String key;
    private final String mac;
    private final Osc osc;
    private final Optional<Boolean> enabled;

}
