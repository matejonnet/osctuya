package com.github.matejonnet.osctuya;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class ChannelConnection implements Closeable, Connection {

    private static final int PORT = 6668;

    private static final Logger logger = LoggerFactory.getLogger(ChannelConnection.class);
    private final SocketAddress address;
    private String bulbName;

    /**
     * {@link AsynchronousSocketChannel} is used to monitor if the connection is alive, using timeout on the read.
     */
    private AsynchronousSocketChannel clientChannel;
    private int keepRetryingForMillis = 500;
    private int maxRetries = 5;

    public ChannelConnection(String ip, String bulbName) {
        this.bulbName = bulbName;
        address = new InetSocketAddress(ip, PORT);
    }

    @Override
    public void connect() throws IOException {
        connect(1000); //TODO configurable
    }

    public void connect(int connectTimeoutMillis) throws IOException {
        connect(connectTimeoutMillis, 10000);  //TODO configurable
    }

    public void connect(int connectTimeoutMillis, int readTimeoutMillis) throws IOException {
        logger.debug("Connecting to {} ...", address);
        clientChannel = AsynchronousSocketChannel.open();
        Future<Void> connectFuture = clientChannel.connect(address);
        try {
            connectFuture.get(connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new IOException("Cannot connect to: " + bulbName, e);
        }
        logger.info("Connected to {}, address:{}.", bulbName, address);
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true) {
                logger.debug("About to read input for {}", bulbName);
                try {
                    ByteBuffer buffer = ByteBuffer.allocate(256);
                    Future<Integer> readFuture = clientChannel.read(buffer);
                    // disconnect if there is no traffic for a readTimeoutMillis, no traffic could mean
                    // - client was silently disconnected
                    // - no commands
                    Integer read = readFuture.get(readTimeoutMillis, TimeUnit.MILLISECONDS);
                    if (read == -1) { // when bulbs send end of stream it ignores the first command
                        logger.info("End of data for {}.", bulbName);
                        close();
                        break;
                    } else if (read == 0) {
                        logger.info("No data for {}.", bulbName); // should never be here because it's a blocking channel
                        Thread.sleep(250);
                    }
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    logger.info("No data for " + readTimeoutMillis + "ms from {}.", bulbName);
                    close();
                    break;
                }
            }
        });
    }

    @Override
    public void send(ByteBuffer buffer) throws IOException {
        logger.debug("Sending to {}", bulbName);
        try {
            fullyWrite(buffer);
        } catch (Exception e) {
            logger.warn("Retrying to {} because: {} ...", bulbName, e.getMessage());
            retry(buffer, Instant.now().plusMillis(keepRetryingForMillis), 0);
        }
    }

    private void retry(ByteBuffer buffer, Instant retryUntil, int retry) throws IOException {
        try {
            close();
            connect(300); //TODO configurable
            fullyWrite(buffer);
        } catch (Exception ex) {
            if (Instant.now().isBefore(retryUntil) && retry < maxRetries) {
                logger.info("Retrying again to {} because: {} ...", bulbName, ex.getMessage());
                try {
                    Thread.sleep(100 * retry);
                } catch (InterruptedException e) {
                    throw new IOException("Interrupted, cannot retry to " + bulbName + ".", ex);
                }
                retry(buffer, retryUntil, retry++);
            } else {
                throw new IOException("Cannot retry to " + bulbName + ".", ex);
            }
        }
    }

    private void fullyWrite(ByteBuffer buffer) throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int written = 0;
        while (written < buffer.capacity()) {
            Future<Integer> writeFuture = clientChannel.write(buffer);
            written += writeFuture.get(5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close()  {
        logger.debug("Closing connection to {}, address: {} ...", bulbName, address);
        try {
            if (clientChannel != null) {
                clientChannel.close();
            }
            logger.info("Connection closed {}, address: {}.", bulbName, address);
        } catch (IOException e) {
            logger.warn("Cannot close connection to {}.", bulbName);
        }
    }
}
