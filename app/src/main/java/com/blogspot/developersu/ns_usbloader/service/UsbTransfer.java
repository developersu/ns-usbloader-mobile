package com.blogspot.developersu.ns_usbloader.service;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.ResultReceiver;

import java.util.Arrays;

abstract class UsbTransfer extends TransferTask {

    private UsbDeviceConnection deviceConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    UsbTransfer(ResultReceiver resultReceiver, Context context, UsbDevice usbDevice, UsbManager usbManager) throws Exception{
        super(resultReceiver, context);

        if (usbManager == null) {
            finish();
            return;
        }

        usbInterface = usbDevice.getInterface(0);
        epIn = usbInterface.getEndpoint(0); // For bulk read
        epOut = usbInterface.getEndpoint(1); // For bulk write

        deviceConnection = usbManager.openDevice(usbDevice);

        if ( ! deviceConnection.claimInterface(usbInterface, false)) {
            issueDescription = "USB: failed to claim interface";
            throw new Exception("USB: failed to claim interface");
        }
    }

    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    boolean writeUsb(byte[] message){
        int bytesWritten;
        while (! interrupt){
            bytesWritten = deviceConnection.bulkTransfer(epOut, message, message.length, 5050); // timeout 0 - unlimited
            if (bytesWritten != 0)
                return (bytesWritten != message.length);
        }
        return false;
    }
    /**
     * Reading what USB device responded.
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    byte[] readUsb(){
        byte[] readBuffer = new byte[512];
        int readResult;
        while (! interrupt) {
            readResult = deviceConnection.bulkTransfer(epIn, readBuffer, 512, 1000); // timeout 0 - unlimited
            if (readResult > 0)
                return Arrays.copyOf(readBuffer, readResult);
        }
        return null;
    }

    void finish(){
        deviceConnection.releaseInterface(usbInterface);
        deviceConnection.close();
    }
}
