package com.github.matejonnet.osctuya.config;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class Config {
    public final String bindHost;
    public final Integer bindPort;

    /**
     * Sends "power on" before every command.
     * Used to workaround responsiveness issues.
     */
    public final boolean alwaysSendPowerOn;

    public final List<BulbConfig> bulbs;
}