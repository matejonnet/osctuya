package com.github.matejonnet.osctuya.config;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Value
@Builder
@Jacksonized
public class Osc {
    private OscAddress addresses;
}
