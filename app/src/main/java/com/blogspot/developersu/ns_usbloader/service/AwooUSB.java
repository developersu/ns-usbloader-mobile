package com.blogspot.developersu.ns_usbloader.service;

import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToLongLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.intToArrLE;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.service.utility.Consumer;
import com.blogspot.developersu.ns_usbloader.service.utility.ServiceResultingDataSet;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Arrays;

class AwooUSB extends UsbTransfer {

    private final byte[] magic = new byte[] { 0x54, 0x55, 0x43, 0x30 };         // eq. 'TUC0'
    private final byte[] replyConstArray = new byte[] { 0x54, 0x55, 0x43, 0x30, // 'TUC0'
                                                        0x01, 0x00, 0x00, 0x00, // CMD_TYPE_RESPONSE = 1
                                                        0x01, 0x00, 0x00, 0x00  };

    AwooUSB(Context context,
            ArrayList<NSPElement> nspElements,
            Consumer<ServiceResultingDataSet> serviceCallback,
            UsbDevice usbDevice,
            UsbManager usbManager) throws Exception {
        super(context, nspElements, serviceCallback, usbDevice, usbManager);
    }

    @Override
    protected void doTransfer() throws Exception {
        sendListOfNSP();
        proceedCommands();
        status = context.getResources().getString(R.string.status_uploaded);
        for (NSPElement element: nspElements) {
            element.setStatus(status);
        }
    }

    private void sendListOfNSP() throws Exception {
        writeUsb("TUL0".getBytes(), "AW Send list of files: handshake failure");

        //Collect file names: add every title to stringBuilder
        StringBuilder nspNamesStringBuilder = new StringBuilder();
        for(NSPElement element: nspElements) {
            nspNamesStringBuilder.append(element.getFilename());  // Note: default string encoding: UTF-16
            nspNamesStringBuilder.append('\n');
        }

        byte[] nspNames = nspNamesStringBuilder.toString().getBytes(); // android .getBytes() is UTF8
        byte[] nspListSize = intToArrLE(nspNames.length);

        writeUsb(nspListSize, "AW Send list of files: [send list length]");
        writeUsb(new byte[8], "AW Send list of files: [send padding]");
        writeUsb(nspNames, "AW Send list of files: [send list itself]");
    }

    private void proceedCommands() throws Exception {
        while (true) {
            byte[] receivedArray = readUsb("Proceed commands read exception");

            // Bytes from 0 to 3 should contain 'magic' TUC0, so must be verified like this
            if (!Arrays.equals(Arrays.copyOfRange(receivedArray, 0,4), magic))
                continue;

            // 8th to 12th(excl) bytes in returned data stands for command ID as unsigned integer (Little-endian).
            // Actually, we have to compare arrays here, but in real world it can't be greater than 0/1/2
            // Protocol also specifies 4th byte to be 0x00 kinda indicating command is valid, but we ignore it
            if (receivedArray[8] == 0x00) {   // 0x00 - exit
                return;                  // All interaction with USB device should be ended (expected);
            }                                 // 0x01 - file range; 0x02 — unknown bug
            else if ((receivedArray[8] == 0x01) || (receivedArray[8] == 0x02)) {
                fileRangeCmd();
            }
        }
    }

    private void fileRangeCmd() throws Exception {
        byte[] receivedArray = readUsb("AW Unable to get meta information @fileRangeCmd()");

        // range_offset of the requested file. At first will be 0x10.
        long receivedRangeSize = arrToLongLE(receivedArray, 0);
        byte[] receivedRangeSizeRAW = Arrays.copyOfRange(receivedArray, 0,8);
        long receivedRangeOffset = arrToLongLE(receivedArray, 8);

        // Requesting UTF-8 file name required:
        receivedArray = readUsb("AW Unable to get file name fileRangeCmd()");
        String requestedNspName = new String(receivedArray, "UTF-8");

        // Send response header. receivedRangeSize in 'RAW' format as received
        writeUsb(replyConstArray, "AW Response: [1/3]");
        writeUsb(receivedRangeSizeRAW, "AW Response: [2/3]");
        writeUsb(new byte[12], "AW Response: [3/3]");

        BufferedInputStream bufferedInStream = new BufferedInputStream(
                context.getContentResolver().openInputStream(findUriByName(requestedNspName)));

        if (bufferedInStream.skip(receivedRangeOffset) != receivedRangeOffset)
            throw new Exception("AW Requested skip is out of file size. Nothing to transmit");

        long readFrom = 0;     // 'End Offset' == receivedRangeSize
        int readPice = 16384;  // 8388608 = 8Mb
        int updateProgressPeriods = 0;

        while (readFrom < receivedRangeSize) {
            if ((readFrom + readPice) >= receivedRangeSize)
                readPice = (int)(receivedRangeSize - readFrom); // TODO: consider revising

            byte[] readBuf = new byte[readPice];
            if (bufferedInStream.read(readBuf) != readPice)
                throw new Exception("AW Reading of stream suddenly ended");

            writeUsb(readBuf, "AW Failure during NSP transmission.");
            readFrom += readPice;

            if (updateProgressPeriods++ % 1024 == 0)       // Update progress per each 16mb
                updateProgressBar((int) ((readFrom+1)/(receivedRangeSize/100+1)));
        }
        bufferedInStream.close();

        resetProgressBar();
    }

    private Uri findUriByName(String name) throws Exception {
        for (NSPElement nsp: nspElements) {
            if (nsp.getFilename().equals(name)) {
                return nsp.getUri();
            }
        }
        throw new Exception("NSP file not found by name "+name);
    }
}