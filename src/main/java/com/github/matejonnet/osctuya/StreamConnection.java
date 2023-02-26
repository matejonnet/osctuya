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
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;

import static com.github.matejonnet.osctuya.Utils.bytesToHex;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class StreamConnection implements Closeable {

    private static final int PORT = 6668;

    private static final Logger logger = LoggerFactory.getLogger(StreamConnection.class);
    private final SocketAddress address;

    private Socket clientSocket;
    OutputStream outputStream;
    private boolean logResponse;
    private InputStream inputStream;

    public StreamConnection(String ip, boolean logResponse) {
        address = new InetSocketAddress(ip, PORT);
        this.logResponse = logResponse;
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                logger.info("Response: {}", new String(inputStream.readAllBytes()));;
            }
        });
    }

    public void connect() throws IOException {
        connect(1000); //TODO configurable
    }

    public void connect(int timeoutMillis) throws IOException {
        logger.debug("Connecting to {} ...", address);
        clientSocket = new Socket();
        clientSocket.connect(address, timeoutMillis);
        outputStream = clientSocket.getOutputStream();
        logger.info("Connected to {}.", address);
        inputStream = clientSocket.getInputStream();
    }

    public void send(ByteBuffer buffer) throws IOException {
        logger.info("Sending to {} ...", address);
        logger.debug("Sending to {} : {} ...", address, bytesToHex(buffer.array()));
        try {
            outputStream.write(buffer.array());
//            outputStream.flush();
//            close();
            if (logResponse) {
                try {
                    String response = new String(clientSocket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    logger.info("Response from {}: {}.", address, response);
                } catch (SocketTimeoutException e) {
                    logger.warn("Unable to read from " +  address  + ".", e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.info("Retrying to {} because: {} ...", address, e.getMessage());
            retry(buffer, Instant.now().plusMillis(200));
        }
    }

    private void retry(ByteBuffer buffer, Instant retryUntil) throws IOException {
        try {
            close();
            connect(300); //TODO configurable
            outputStream.write(buffer.array());
//            outputStream.flush();
        } catch (Exception ex) {
            if (Instant.now().isBefore(retryUntil)) {
                logger.info("Retrying again to {} because: {} ...", address, ex.getMessage());
                retry(buffer, retryUntil);
            } else {
                throw new IOException("Cannot retry to " + address + ".", ex);
            }
        }
    }

    @Override
    public void close()  {
        logger.debug("Closing connection to {} ...", address);
        try {
            if (outputStream != null) {
                outputStream.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
            logger.debug("Connection closed {}.", address);
        } catch (IOException e) {
            logger.warn("Cannot close connection to {}.", address);
        }
    }
}
