package com.github.matejonnet.osctuya;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class PayloadGenerationException extends Throwable {
    public PayloadGenerationException(String message, Throwable e) {
        super(message, e);
    }
}
