package com.blogspot.developersu.ns_usbloader.service.utility;

import android.widget.Toast;

import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;

public class ServiceResultingDataSet {

    private final ArrayList<NSPElement> nspElements;
    private final String finalToastMessage;
    private final boolean isToastShort;

    public ServiceResultingDataSet(ArrayList<NSPElement> nspElements,
                                   String finalToastMessage,
                                   boolean isToastShort) {
        this.nspElements = nspElements;
        this.finalToastMessage = finalToastMessage;
        this.isToastShort = isToastShort;
    }

    public ArrayList<NSPElement> getNspElements() {
        return nspElements;
    }

    public String getFinalToastMessage() {
        return finalToastMessage;
    }

    public int getToastDuration() {
        return isToastShort?
                Toast.LENGTH_SHORT:
                Toast.LENGTH_LONG;
    }
}
