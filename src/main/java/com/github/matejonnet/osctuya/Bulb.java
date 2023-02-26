package com.github.matejonnet.osctuya;

import com.github.matejonnet.osctuya.config.Config;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Bulb implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Bulb.class);
    private final boolean alwaysSendPower;
    private String ip;
    private final String devId;
    private final String localKey;
    private final String name;
    private Connection connection;
    private final AtomicInteger sequence = new AtomicInteger();
    private Color lastColor = new Color(0, 0, 0);
    private boolean lastPower;
    private CircularFifoQueue<Command> sendQueue;
    private Semaphore semaphore = new Semaphore(0);
    private final ExecutorService executor = Executors.newScheduledThreadPool(2);
    private ScheduledExecutorService cancelMonitor = Executors.newScheduledThreadPool(1);
    private int commandRepeated = 0;
    private boolean logResponse;

    public Bulb(String ip, String devId, String localKey, String name, Config config) {
        this.ip = ip;
        this.devId = devId;
        this.localKey = localKey;
        this.name = name;
        this.alwaysSendPower = config.alwaysSendPower;
        this.logResponse = config.logResponse;

        sendQueue = new CircularFifoQueue<>(config.sendQueueSize);

        executor.execute(() -> {
            while (true) {
                try {
                    Command command = sendQueue.poll();
                    logger.debug("Command {}.", command);
                    if (command != null) {
                        Future<?> future = executor.submit(() -> {
                            try {
                                connection.send(command.byteBuffer());
                            } catch (IOException e) {
                                logger.error("Cannot set " + command.message() + " on bulb: " + name, e);
                            }
                        });
                        cancelMonitor.schedule(() -> future.cancel(true), config.commandTimeoutMillis, TimeUnit.MILLISECONDS);
                    }
                    // Because some messages could be dropped from the queue,
                    // loops without a command can happen until all the permits are acquired.
                    boolean timedOut = !semaphore.tryAcquire(config.repeatDelayMillis, TimeUnit.MILLISECONDS);
                    // there were no new messages in a given timeout
                    if (timedOut) {
                        if (command != null && commandRepeated < config.repeatCommandTimes) {
                            logger.debug("Repeating last command ...");
                            sendQueue.add(command);
                            commandRepeated++;
                        } else {
                            logger.debug("Waiting for new command ...");
                            semaphore.acquire();
                            logger.debug("New command received.");
                            commandRepeated = 0;
                        }
                    } else {
                        // new command received
                        commandRepeated = 0;
                    }
                } catch (Throwable e) {
                    logger.error("Cannot process command.", e);
                }
            }
        });
    }

    public void connect() throws IOException {
//        connection = new Connection(ip, logResponse, bytes -> decrypt(bytes));
        connection = new Connection(ip, logResponse, getName());
        connection.connect();
    }

    public String getName() {
        return name;
    }

    public void setPower(boolean on) {
        try {
            lastPower = on;
            ByteBuffer byteBuffer = generatePayload(Collections.singletonMap("20", on));
            send(new Command(byteBuffer, "power"), true);
        } catch (PayloadGenerationException e) {
            logger.error("Cannot set power on bulb: " + name, e);
        }
    }

    public void setBrightness(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new InvalidValueException("Brightness must be between 0 and 100.");
        }
        try {
            var value = 10 + (1000 - 10) * percentage / 100;
            ByteBuffer byteBuffer = generatePayload(Collections.singletonMap("22", value));
            send(new Command(byteBuffer, "brightness"));
        } catch (PayloadGenerationException e) {
            logger.error("Cannot set brightness on bulb: " + name, e);
        }
    }

    /**
     *
     * @param relativeValue 0 - 1000 (warm to cold)
     */
    public void setTemperature(int relativeValue) {
        if (relativeValue < 0 || relativeValue > 1000) {
            throw new InvalidValueException("Temperature must be between 0 and 1000.");
        }
        try {
            ByteBuffer byteBuffer = generatePayload(Collections.singletonMap("23", relativeValue));
            send(new Command(byteBuffer, "temperature"));
        } catch (PayloadGenerationException e) {
            logger.error("Cannot set temperature on bulb: " + name, e);
        }
    }

    public void setColor(Color color) {
        logger.debug("Setting color: {}", color);
        lastColor = color;
        String colorValue = Utils.getHexColor(color);
        var command = new HashMap<String, Object>();
        command.put("21","colour");
        command.put("24", colorValue);
        try {
            ByteBuffer colorBuffer = generatePayload(command);
            send(new Command(colorBuffer, "color"));
        } catch (PayloadGenerationException e) {
            logger.error("Cannot set color on bulb: " + name, e);
        }
    }

    public void updateRed(int red) {
        Color newColor = new Color(red, lastColor.getGreen(), lastColor.getBlue());
        setColor(newColor);
    }

    public void updateGreen(int green) {
        Color newColor = new Color(lastColor.getRed(), green, lastColor.getBlue());
        setColor(newColor);
    }

    public void updateBlue(int blue) {
        Color newColor = new Color(lastColor.getRed(), lastColor.getGreen(), blue);
        setColor(newColor);
    }

    /**
     * Turn on/off
     *      generate_payload(CONTROL, data={switch: on})
     *
     *              CONTROL: {  # Set Control Values on Device
     *             "hexByte": "07",
     *             "command": {"devId": "", "uid": "", "t": ""},
     *         }
     *
     * DEBUG:building payload=b'{"devId":"0123456789abcdef012345","uid":"0123456789abcdef012345","t":"1647038373","dps":{"20":true}}'
     */
    private ByteBuffer generatePayload(Map<String, Object> data) throws PayloadGenerationException {
        // json_data = payload_dict[self.dev_type][command]["command"]
        Map<String, Object> jsonData = new HashMap<>();
        jsonData.put("devId", devId);
        jsonData.put("uid", devId);
        jsonData.put("t", Long.toString(Instant.now().getEpochSecond()));
        jsonData.put("dps", data);

        String payload;
        try {
            payload = Mapper.getJson().writeValueAsString(jsonData);
        } catch (IOException e) {
            throw new PayloadGenerationException("Cannot create json.", e);
        }
        logger.debug("Payload: {}.", payload);

        byte[] encryptedPayload;
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(localKey.getBytes(StandardCharsets.UTF_8), "AES"));
            encryptedPayload = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new PayloadGenerationException("Cannot encode payload.", e);
        }

        ByteBuffer payloadBuffer = ByteBuffer.allocate(15 + encryptedPayload.length);
        payloadBuffer.put(0, "3.3".getBytes(StandardCharsets.UTF_8));
        payloadBuffer.put(15, encryptedPayload);

        return getTuyaMessage(payloadBuffer);
    }

    /**
     * Allocate buffer with room for payload + 24 bytes for
     * prefix, sequence, command, length, crc, and suffix
     *
     * The first half - 8 bytes 00:00:55:aa:00:00:00:6b - of our packet is static, except for the last (8th) byte. The 8th byte increments by one every time a new command is sent, but I've seen some results that add more than this using commands that were not on/off. At any rate, it is just a counter and I think it doesn't really matter what the value is.
     *  The second half - 00:00:00:07:00:00:00:9b - gives 2 pieces of information: the type of packet being sent and the length of the remaining the data/packet.
     *      So far I've found that the 4th byte is
     *          07 when sending commands,
     *          08 when receiving a reply from the device,
     *          '9e' for broadcast messages, and 0a when getting the status.
     *   The final value (the 16th byte of the whole prefix) is the size in bytes of the subsequent packet (obviously as a hex value).
     *      This is the length of the encrypted data in bytes plus the suffix - basically everything after the prefix.
     *      From my tests the suffix has always been 8 bytes.
     */
    private ByteBuffer getTuyaMessage(ByteBuffer payloadBuffer) {
        ByteBuffer tuyaMessage = ByteBuffer.allocate(payloadBuffer.capacity() + 24);
        // Add prefix
        tuyaMessage.put(0, HexFormat.of().parseHex("000055AA"));
        // Add sequence
        tuyaMessage.put(4, Utils.intToHexArray(sequence.getAndIncrement()));
        // Add message type
        tuyaMessage.put(8, HexFormat.of().parseHex("00000007"));
        // Add message size
        tuyaMessage.put(12, Utils.intToHexArray(payloadBuffer.capacity() + 8));
//        tuyaMessage.put(12, Utils.intToHexArray(payloadBuffer.capacity()));

        // Add payload, crc, and suffix
        tuyaMessage.put(16, payloadBuffer.array());

        Utils.Crc32 crc = new Utils.Crc32();
        crc.update(tuyaMessage.slice(0, payloadBuffer.capacity() + 16));
        tuyaMessage.put(payloadBuffer.capacity() + 16, crc.getValue());
        tuyaMessage.put(payloadBuffer.capacity() + 20, HexFormat.of().parseHex("0000AA55"));
        return tuyaMessage;
    }

    private void send(Command command) {
        send(command, false);
    }

    private void send(Command command, boolean ignoreAlwaysSendPower) {
        if (alwaysSendPower && !ignoreAlwaysSendPower) {
            setPower(lastPower);
        }
        sendQueue.add(command);
        semaphore.release();
    }

    private String decrypt(byte[] bytes) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(localKey.getBytes(StandardCharsets.UTF_8), "AES"));
            return new String(cipher.doFinal(bytes), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            logger.error("Cannot decrypt message.", e);
            return ""; //TODO
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                logger.error("Cannot close connection.", e);
            }
        }
    }

    private record Command (ByteBuffer byteBuffer, String message) {}

}
