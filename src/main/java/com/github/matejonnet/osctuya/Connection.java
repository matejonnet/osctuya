package com.github.matejonnet.osctuya;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.time.Instant;

import static com.github.matejonnet.osctuya.Utils.bytesToHex;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Connection implements Closeable {

    private static final int PORT = 6668;

    private static final Logger logger = LoggerFactory.getLogger(Connection.class);
    private final SocketAddress address;

    private Socket clientSocket;
    OutputStream outputStream;
    private boolean sending;

    public Connection(String ip) {
        address = new InetSocketAddress(ip, PORT);
    }

    public void connect() throws IOException {
        connect(1000);
    }

    public void connect(int timeoutMillis) throws IOException {
        logger.info("Connecting to {} ...", address);
        clientSocket = new Socket();
        clientSocket.setKeepAlive(true);
        clientSocket.connect(address, timeoutMillis);
        outputStream = clientSocket.getOutputStream();
        logger.debug("Connected to {}.", address);
    }

    public synchronized void send(ByteBuffer buffer) throws IOException {
        logger.debug("Sending to {} : {} ...", address, bytesToHex(buffer.array()));
        try {
            outputStream.write(buffer.array());
            outputStream.flush();
        } catch (Exception e) {
            logger.info("Retrying to {} because: {} ...", address, e.getMessage());
            retry(buffer, Instant.now().plusMillis(200));
        }
    }

    private void retry(ByteBuffer buffer, Instant retryUntil) throws IOException {
        try {
            close();
            connect(100);
            outputStream.write(buffer.array());
            outputStream.flush();
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
