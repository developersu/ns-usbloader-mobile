package com.blogspot.developersu.ns_usbloader;

import java.nio.ByteBuffer;

import static java.nio.ByteOrder.LITTLE_ENDIAN;

public class DataConvertUtils {

    private DataConvertUtils() {}

    /**
     * Convert INT (Little endian) value to bytes-array representation
     * */
    public static byte[] intToArrLE(int value){
        return ByteBuffer.allocate(4).order(LITTLE_ENDIAN)
                .putInt(value)
                .array();
    }
    /**
     * Convert LONG (Little endian) value to bytes-array representation
     * */
    public static byte[] longToArrLE(long value){
        return ByteBuffer.allocate(8).order(LITTLE_ENDIAN)
                .putLong(value)
                .array();
    }
    /**
     * Convert bytes-array to INT value (Little endian)
     * */
    public static int arrToIntLE(byte[] byteArrayWithInt, int intStartPosition){
        return ByteBuffer.wrap(byteArrayWithInt).order(LITTLE_ENDIAN)
                .getInt(intStartPosition);
    }
    /**
     * Convert bytes-array to LONG value (Little endian)
     * */
    public static long arrToLongLE(byte[] byteArrayWithLong, int intStartPosition){
        return ByteBuffer.wrap(byteArrayWithLong).order(LITTLE_ENDIAN)
                .getLong(intStartPosition);
    }
}
