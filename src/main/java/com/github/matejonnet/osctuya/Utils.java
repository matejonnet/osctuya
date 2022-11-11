package com.github.matejonnet.osctuya;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class Utils {
    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);
    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    public static String getHexColor(Color rgbColor) {
        return getHexColor(rgbColor.getRed(), rgbColor.getGreen(), rgbColor.getBlue());
    }

    public static String getHexColor(int r, int g, int b) {
        float[] hsv = new float[3];
        Color.RGBtoHSB(r, g, b, hsv);
        Integer[] hsvArray = new Integer[]{Math.round(hsv[0] * 360), Math.round(hsv[1] * 1000), Math.round(hsv[2] * 1000)};
        StringBuilder colorValue = new StringBuilder();
        for (int v : hsvArray) {
            colorValue.append(String.format("%04X", v));
        }
        return colorValue.toString();
    }

    public static byte[] intToHexArray(int value) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(value);
        return buffer.array();
    }

    public static byte[] longToHexArray(long value) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(value);
        return buffer.array();
    }

    public static class Crc32 {

        private final CRC32 crc = new CRC32();

        public void update(ByteBuffer buffer) {
            crc.update(buffer);
        }

        /**
         *
         * @return array of 4 bytes
         */
        public byte[] getValue() {
            ByteBuffer buffer = ByteBuffer.allocate(8);
            buffer.putLong(crc.getValue());
            byte[] result = new byte[4];
            buffer.get(4, result);
            return result;
        }
    }
}
