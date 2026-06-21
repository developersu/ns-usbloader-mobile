package com.blogspot.developersu.ns_usbloader.service;

import static java.lang.Thread.currentThread;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

import com.blogspot.developersu.ns_usbloader.service.utility.Consumer;
import com.blogspot.developersu.ns_usbloader.service.utility.ServiceResultingDataSet;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;
import java.util.Arrays;

abstract class UsbTransfer extends TransferTask {

    private UsbDeviceConnection deviceConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    protected UsbTransfer(Context context,
                          ArrayList<NSPElement> nspElements,
                          Consumer<ServiceResultingDataSet> serviceCallback,
                          UsbDevice usbDevice,
                          UsbManager usbManager) throws Exception{
        super(context, nspElements, serviceCallback);

        if (usbManager == null) {
            close();
            return;
        }

        usbInterface = usbDevice.getInterface(0);
        epIn = usbInterface.getEndpoint(0); // For bulk read
        epOut = usbInterface.getEndpoint(1); // For bulk write
        deviceConnection = usbManager.openDevice(usbDevice);

        if ( ! deviceConnection.claimInterface(usbInterface, false)) {
            throw new Exception("USB: failed to claim interface");
        }
    }

    /**
     * Send byte array to USB-device
     * */
    protected void writeUsb(byte[] message, String errorMessage) throws Exception {
        while (! currentThread().isInterrupted()) {                        // timeout 0 → unlimited
            int bytesSent = deviceConnection.bulkTransfer(epOut, message, message.length, 5050);
            if (bytesSent != message.length)
                throw new Exception(errorMessage);
        }
    }

    /**
     * Read USB-device response
     * */
    protected byte[] readUsb(String errorMessage) throws Exception {
        byte[] readBuffer = new byte[512];
        while (! currentThread().isInterrupted()) {
            int readResult = deviceConnection.bulkTransfer(epIn, readBuffer, 512, 1000);
            if (readResult > 0)
                return Arrays.copyOf(readBuffer, readResult);
        }
        throw new Exception(errorMessage);
    }

    @Override
    protected void close() {
        deviceConnection.releaseInterface(usbInterface);
        deviceConnection.close();
    }
}
