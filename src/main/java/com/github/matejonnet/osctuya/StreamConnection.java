package com.github.matejonnet.osctuya;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.Executors;

import static com.github.matejonnet.osctuya.Utils.bytesToHex;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class StreamConnection implements Closeable, Connection {

    private static final int PORT = 6668;

    private static final Logger logger = LoggerFactory.getLogger(StreamConnection.class);
    private final SocketAddress address;
    private String bulbName;

    private Socket clientSocket;
    OutputStream outputStream;

    public StreamConnection(String ip, String bulbName) {
        this.bulbName = bulbName;
        address = new InetSocketAddress(ip, PORT);
    }

    @Override
    public void connect() throws IOException {
        connect(1000); //TODO configurable
    }

    public void connect(int timeoutMillis) throws IOException {
        logger.debug("Connecting to {} ...", address);
        clientSocket = new Socket();
        clientSocket.connect(address, timeoutMillis);
        outputStream = clientSocket.getOutputStream();
        logger.info("Connected to {}, address:{}.", bulbName, address);
        InputStream inputStream = clientSocket.getInputStream();
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                logger.info("Reading input for {}", bulbName);
                try {
                    int b = inputStream.read();
//                    logger.info("Read from {}", bulbName);
                    if (b == -1) { // when bulbs send end of stream it ignores the first command
                        close();
                        break;
                    }
                } catch (IOException e) {
                    logger.warn("Cannot read stream from {}.", bulbName);
                    close();
                    break;
                }
            }
        });
    }

    @Override
    public void send(ByteBuffer buffer) throws IOException {
        logger.info("Sending to {} ...", bulbName); // TODO log level
        logger.debug("Sending to {} : {} ...", bulbName, bytesToHex(buffer.array()));
        try {
            outputStream.write(buffer.array());
        } catch (Exception e) {
            logger.warn("Retrying to {} because: {} ...", bulbName, e.getMessage());
            retry(buffer, Instant.now().plusMillis(200), 5);
        }
    }

    private void retry(ByteBuffer buffer, Instant retryUntil, int maxRetries) throws IOException {
        try {
            close();
            connect(300); //TODO configurable
            outputStream.write(buffer.array());
//            outputStream.flush();
        } catch (Exception ex) {
            if (Instant.now().isBefore(retryUntil) && maxRetries > 0) {
                logger.info("Retrying again to {} because: {} ...", bulbName, ex.getMessage());
                retry(buffer, retryUntil, --maxRetries);
            } else {
                throw new IOException("Cannot retry to " + bulbName + ".", ex);
            }
        }
    }

    @Override
    public void close()  {
        logger.debug("Closing connection to {}, address: {} ...", bulbName, address);
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
            logger.info("Connection closed {}, address: {}.", bulbName, address);
        } catch (IOException e) {
            logger.warn("Cannot close connection to {}.", bulbName);
        }
    }
}
