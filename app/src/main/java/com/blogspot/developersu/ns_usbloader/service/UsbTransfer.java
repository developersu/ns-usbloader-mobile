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

public abstract class UsbTransfer extends TransferTask {

    private UsbDeviceConnection deviceConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    protected UsbTransfer(Context context,
                          ArrayList<NSPElement> nspElements,
                          Consumer<Integer> progressCallback,
                          Consumer<ServiceResultingDataSet> serviceCallback,
                          UsbDevice usbDevice,
                          UsbManager usbManager) throws Exception{
        super(context, nspElements, progressCallback, serviceCallback);

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

    // Find NSPElement in nspElements
    protected NSPElement find(String name) throws Exception {
        for (NSPElement nsp: nspElements) {
            if (nsp.getFilename().equals(name)) {
                return nsp;
            }
        }
        throw new Exception("NSP file not found "+name);
    }

    /**
     * Send byte array to USB-device
     * */
    protected void writeUsb(byte[] message, String errorMessage) throws Exception {
        if (currentThread().isInterrupted())
            return;

        int bytesSent = deviceConnection.bulkTransfer(epOut, message, message.length, 5050); // timeout 0 → unlimited
        if (bytesSent != message.length) {
                throw new Exception(errorMessage+" ("+bytesSent+"/"+message.length+")");
        }
    }

    /**
     * Read USB-device response (for chunk of 512)
     * */
    protected byte[] readUsb(String errorMessage) throws Exception {
        return readUsb(errorMessage, 512);
    }

    // This would fail on reading something huge. Fits 4096, but must be 512. See `epIn.getMaxPacketSize()`
    protected byte[] readUsb(String errorMessage, int chunkSize) throws Exception {
        byte[] readBuffer = new byte[chunkSize];

        while (! currentThread().isInterrupted()) {
            int readResult = deviceConnection.bulkTransfer(epIn, readBuffer, chunkSize, 1000);
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
