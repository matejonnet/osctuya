package com.github.matejonnet.osctuya;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Color;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Bulb implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Bulb.class);

    private String ip;
    private final String devId;
    private final String localKey;
    private final String name;
    private Connection connection;

    private final AtomicInteger sequence = new AtomicInteger();
    private Color lastColor = new Color(0, 0, 0);

    public Bulb(String ip, String devId, String localKey, String name) {
        this.ip = ip;
        this.devId = devId;
        this.localKey = localKey;
        this.name = name;
    }

    public void connect() throws IOException {
        connection = new Connection(ip);
        connection.connect();
    }

    public String getName() {
        return name;
    }

    public void setPower(boolean on) {
        try {
            ByteBuffer byteBuffer = generatePayload(Collections.singletonMap("20", on));
            connection.send(byteBuffer);
        } catch (PayloadGenerationException | IOException e) {
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
            connection.send(byteBuffer);
        } catch (PayloadGenerationException | IOException e) {
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
            connection.send(byteBuffer);
        } catch (PayloadGenerationException | IOException e) {
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
            connection.send(colorBuffer);
        } catch (PayloadGenerationException | IOException e) {
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

        // Add payload, crc, and suffix
        tuyaMessage.put(16, payloadBuffer.array());

        Utils.Crc32 crc = new Utils.Crc32();
        crc.update(tuyaMessage.slice(0, payloadBuffer.capacity() + 16));
        tuyaMessage.put(payloadBuffer.capacity() + 16, crc.getValue());
        tuyaMessage.put(payloadBuffer.capacity() + 20, HexFormat.of().parseHex("0000AA55"));
        return tuyaMessage;
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

}
