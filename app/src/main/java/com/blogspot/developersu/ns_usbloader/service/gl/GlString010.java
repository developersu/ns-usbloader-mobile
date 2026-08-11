package com.blogspot.developersu.ns_usbloader.service.gl;

import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToIntLE;
import androidx.annotation.NonNull;

public class GlString010 implements GlString {

    private final int length;
    private final String string;

    public GlString010(byte[] inputBytes, int startPosition){
        this.length = arrToIntLE(inputBytes, startPosition);
        this.string = new String(inputBytes, startPosition+4, length);
    }

    public int length(){
        return length;
    }

    @NonNull
    @Override
    public String toString(){
        return string;
    }
}