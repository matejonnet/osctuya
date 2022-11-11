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
public class OscAddress {
    private Optional<String> power;
    private Optional<String> brighnes;
    private Optional<String> temperature;
    private Optional<String> red;
    private Optional<String> green;
    private Optional<String> blue;

}
