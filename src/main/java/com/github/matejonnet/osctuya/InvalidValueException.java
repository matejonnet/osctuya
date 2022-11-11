package com.github.matejonnet.osctuya;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class InvalidValueException extends RuntimeException {

    public InvalidValueException(String message) {
        super(message);
    }

    public InvalidValueException(String message, Throwable e) {
        super(message, e);
    }
}
