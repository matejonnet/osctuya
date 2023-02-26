package com.github.matejonnet.osctuya;

import java.nio.ByteBuffer;
import java.util.Optional;

public class TuyaParser {

    ByteBuffer messageBuffer = ByteBuffer.allocate(512); // TOOD size ?

    public Optional<byte[]> parse(ByteBuffer payloadBuffer) {
//        byte[] response;
//        payloadBuffer.flip();
//        String hex = bytesToHex(payloadBuffer.array());
//        int endPos = hex.indexOf("0000AA55");
//        if (endPos > -1) {
//            messageBuffer.put(Utils.hexToBytes(hex.substring(0, endPos)));
//            messageBuffer.flip();
//            response = messageBuffer.array();
//            messageBuffer = ByteBuffer.allocate(512); // TODO size ?
//            messageBuffer.put(Utils.hexToBytes(hex.substring(endPos+8)));
//
//            int messageSize = Utils.hexToInt(messageBuffer.slice(12, 4));
//            ByteBuffer payload = messageBuffer.slice(16, messageSize);
//        } else {
//            messageBuffer.put(Utils.hexToBytes(hex));
//            response = null;
//        }
//
//        ByteBuffer tuyaMessage = ByteBuffer.allocate(payloadBuffer.capacity() + 24);
//        // Add prefix
//        tuyaMessage.put(0, HexFormat.of().parseHex("000055AA"));
//        // Add sequence
//        tuyaMessage.put(4, Utils.intToHexArray(sequence.getAndIncrement()));
//        // Add message type
//        tuyaMessage.put(8, HexFormat.of().parseHex("00000007"));
//        // Add message size
//        tuyaMessage.put(12, Utils.intToHexArray(payloadBuffer.capacity() + 8));
//
//        // Add payload, crc, and suffix
//        tuyaMessage.put(16, payloadBuffer.array());
//
//        Utils.Crc32 crc = new Utils.Crc32();
//        crc.update(tuyaMessage.slice(0, payloadBuffer.capacity() + 16));
//        tuyaMessage.put(payloadBuffer.capacity() + 16, crc.getValue());
//        tuyaMessage.put(payloadBuffer.capacity() + 20, HexFormat.of().parseHex("0000AA55"));
//        return tuyaMessage;
        return Optional.empty();
    }
}
