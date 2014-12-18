package com.fidesmo.tutorials.hellofidesmo;

import java.util.Arrays;
import java.util.Locale;

/**
 * Utility functions for the HelloFidesmo example app written for the Fidesmo Android tutorial
 */
public class Utils {

    final private static char[] hexArray = "0123456789ABCDEF".toCharArray();

    final private static String AID_PREFIX = "A00000061700";
    final private static String AID_SUFFIX = "0101";
    final private static String SELECT_HEADER = "00A40400";

    /**
     * Encodes a byte array into a hexadecimal string having two characters per byte
     * @param bytes the input byte[]
     * @return the resulting hex string
     */
    public static String encodeHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int i = 0; i < bytes.length; i++ ) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Decodes a hexadecimal string into a byte array
     * @param hexString a hex string, two characters per byte
     * @return the decoded byte array
     */
    public static byte[] decodeHex(String hexString) {
        if ((hexString.length() & 0x01) != 0) {
            throw new IllegalArgumentException("Odd number of characters.");
        }
        char[] hexChars = hexString.toUpperCase(Locale.ROOT).toCharArray();
        byte[] result = new byte[hexChars.length / 2];
        for (int i = 0; i < hexChars.length; i += 2) {
            result[i / 2] = (byte) (Arrays.binarySearch(hexArray, hexChars[i]) * 16 +
                    Arrays.binarySearch(hexArray, hexChars[i + 1]));
        }
        return result;
    }

    /**
     * Builds the SELECT command APDU from a cardlet's application ID:
     * - builds the cardlet's AID appending the prefix and suffix applied by Fidesmo
     * - builds the command concatenating the header, AID lenght and AID
     * @param appId the application ID, assigned by Fidesmo
     * @return SELECT command APDU
     */
    public static byte[] selectApdu(String appId) {
        String cardletAid = AID_PREFIX + appId + AID_SUFFIX;
        byte[] aidLength = new byte[] {(byte)(cardletAid.length()/2)};
        String selectApdu = SELECT_HEADER + Utils.encodeHex(aidLength) + cardletAid;
        return decodeHex(selectApdu);
    }

    /**
     * Extracts the status bytes of a response APDU - these are always the last two bytes
     * @param response A response APDU
     * @return the two status bytes
     */
    public static byte[] statusBytes(byte[] response) {
        return new byte[] {response[response.length -2], response[response.length-1]};
    }

    /**
     * Extracts the data payload of a response APDU: everything except the last two status bytes
     * @param response A response APDU
     * @return the data payload of the response
     */
    public static byte[] responseData(byte[] response) {
        return Arrays.copyOfRange(response, 0, response.length - 2);
    }

}