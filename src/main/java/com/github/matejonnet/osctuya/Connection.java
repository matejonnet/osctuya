package com.github.matejonnet.osctuya;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Connection {
    void connect() throws IOException;

    void send(ByteBuffer buffer) throws IOException;

    void close();
}
