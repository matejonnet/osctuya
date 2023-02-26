package com.github.matejonnet.osctuya;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Connection implements Closeable {

    private static final int PORT = 6668;

    private static final Logger logger = LoggerFactory.getLogger(Connection.class);
    private final SocketAddress address;
    private final ScheduledExecutorService executorService;

    private SocketChannel clientSocket;
    private boolean logResponse;
    private String bulbName;

    //    public Connection(String ip, boolean logResponse, Function<byte[], String> cipher) {
    public Connection(String ip, boolean logResponse, String bulbName) {
        address = new InetSocketAddress(ip, PORT);
        this.logResponse = logResponse;
        this.bulbName = bulbName;
        executorService = Executors.newScheduledThreadPool(10);
        executorService.scheduleAtFixedRate(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(512);
                int totalRead = 0;
//                while (true) {
                    logger.info("Trying to read response from {}.", bulbName);
                    logger.info("Trying to read response from {}.", executorService.toString());
                    if (clientSocket.isConnected()) {
                        logger.info("Reading response from {}.", bulbName);
                        int read = clientSocket.read(buffer);
                        totalRead += read;
    //                    logger.info("Raw response: {}, data: {}.", read, Utils.bytesToHex(buffer.array()));
                        if (read == -1) {
                            logger.info("Received EOM from {}, closing connection...", bulbName); // close connection because bulb does not react to first message after this.
                            close();
//                            Thread.sleep(500); // TODO improve response reading
                        }
                        if (!buffer.hasRemaining()) {
    //                        logger.info("Flipping buffer ...");
                            buffer.flip();
    //                        logger.info("Response: {}", cipher.apply(buffer.array()));
                            buffer = ByteBuffer.allocate(512);
                        } else if (read == 0) {
//                            Thread.sleep(100); // TODO improve response reading
                        }
                    } else {
//                        Thread.sleep(500); // TODO improve response reading
                    }
//                }
            } catch (IOException e) {
                logger.error("Cannot read response.", e);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void connect() throws IOException {
        connect(1000); //TODO configurable
    }

    public void connect(int timeoutMillis) throws IOException {
        logger.debug("Connecting to {}, address: {} ...", bulbName, address);
        clientSocket = SocketChannel.open();
        clientSocket.connect(address);
        logger.info("Connected to {}, address: {}.", bulbName, address);
    }

    public synchronized void send(ByteBuffer buffer) throws IOException {
        logger.info("Sending to {} ...", bulbName);
//        logger.info("Sending to {} : {} ...", address, bytesToHex(buffer.array()));
        try {
            writeToChannel(buffer);
        } catch (Exception e) {
            logger.info("Retrying to {} because: {} ...", bulbName, e.getMessage());
            retry(buffer, Instant.now().plusMillis(200));
        }
    }

    private void retry(ByteBuffer buffer, Instant retryUntil) throws IOException {
        try {
            close();
            connect(300); //TODO configurable
            writeToChannel(buffer);
        } catch (Exception ex) {
            if (Instant.now().isBefore(retryUntil)) {
                logger.info("Retrying again to {} because: {} ...", address, ex.getMessage());
                retry(buffer, retryUntil);
            } else {
                throw new IOException("Cannot retry to " + address + ".", ex);
            }
        }
    }

    private void writeToChannel(ByteBuffer buffer) throws IOException {
        int written = 0;
        while (written < buffer.capacity()) {
            written += clientSocket.write(buffer);
        }
    }

    @Override
    public void close()  {
        logger.debug("Closing connection to {} ...", address);
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
            logger.debug("Connection closed {}.", address);
        } catch (IOException e) {
            logger.warn("Cannot close connection to {}.", address);
        }
    }
}
