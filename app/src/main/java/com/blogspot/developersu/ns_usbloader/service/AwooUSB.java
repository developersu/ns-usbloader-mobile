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

    private static final int CHUNK_SIZE = 16384;

    private static final byte[] TUL0  = "TUL0".getBytes();
    private static final byte[] PADDING = new byte[8];
    private static final String MAGIC = "TUC0";
    private static final byte[] STANDARD_REPLY = new byte[] { 0x54, 0x55, 0x43, 0x30, // 'TUC0'
                                                        0x01, 0x00, 0x00, 0x00, // CMD_TYPE_RESPONSE = 1
                                                        0x01, 0x00, 0x00, 0x00  };
    private static final byte[] TWELVE_ZERO_BYTES = new byte[12];

    private static final byte CMD_EXIT = 0x00;
    private static final byte CMD_FILE_RANGE_DEFAULT = 0x01;
    private static final byte CMD_FILE_RANGE_ALTERNATIVE = 0x02;

    AwooUSB(Context context,
            ArrayList<NSPElement> nspElements,
            Consumer<Integer> progressCallback,
            Consumer<ServiceResultingDataSet> serviceCallback,
            UsbDevice usbDevice,
            UsbManager usbManager) throws Exception {
        super(context, nspElements, progressCallback, serviceCallback, usbDevice, usbManager);
    }

    @Override
    protected void doTransfer() throws Exception {
        sendListOfFiles();
        proceedCommands();
        status = context.getResources().getString(R.string.status_uploaded);
        for (NSPElement element: nspElements) {
            element.setStatus(status);
        }
    }

    private void sendListOfFiles() throws Exception {
        byte[] fileNames = buildFileNamesToSend();
        byte[] fileNamesSize = intToArrLE(fileNames.length);

        writeUsb(TUL0, "AW Send list of files: handshake");
        writeUsb(fileNamesSize, "AW Send list of files: list length");
        writeUsb(PADDING, "AW Send list of files: padding");
        writeUsb(fileNames, "AW Send list of files: list itself");
    }
    private byte[] buildFileNamesToSend() {
        StringBuilder namesBuilder = new StringBuilder();
        for(NSPElement element: nspElements) {
            namesBuilder
                    .append(element.getFilename())   // Note: default string encoding: UTF-16
                    .append('\n');
        }
        return namesBuilder.toString().getBytes(); // android .getBytes() is UTF8
    }

    private void proceedCommands() throws Exception {
        while (true) {
            byte[] deviceReply = readUsb("Proceed commands read exception");

            if (isInvalidReply(deviceReply))
                continue;

            // 8th to 12th(excl) bytes in returned data stands for command ID as unsigned integer (Little-endian).
            // Actually, we have to compare arrays here, but in real world it can't be greater than 0/1/2
            // Protocol also specifies 4th byte to be 0x00 kinda indicating command is valid, but we ignore it
            switch (deviceReply[8]) {
                case CMD_EXIT:
                    return;
                case CMD_FILE_RANGE_DEFAULT:
                case CMD_FILE_RANGE_ALTERNATIVE:
                    fileRangeCmd();
            }
        }
    }
    private boolean isInvalidReply(byte[] reply) {
        return ! MAGIC.equals(new String(reply, 0,4));
    }

    private void fileRangeCmd() throws Exception {
        byte[] readData = readUsb("AW Failed getting meta");

        byte[] sizeAsBytes = Arrays.copyOfRange(readData, 0,8);
        long size = arrToLongLE(readData, 0);
        long offset = arrToLongLE(readData, 8);
        String fileName = new String(readUsb("AW Failed getting file name"), "UTF-8");
        /*
        Log.i("fileRangeCmd", String.format("\nReply to: %s" +
                        "%n         Offset: %-20d 0x%x" +
                        "%n         Size:   %-20d 0x%x",
                fileName,
                offset, offset,
                size, size));
         */
        // Send response header
        sendFileMetadata(sizeAsBytes);

        try (BufferedInputStream inStream = new BufferedInputStream(
                context.getContentResolver().openInputStream(findUriByName(fileName)))) {
            if (inStream.skip(offset) != offset)
                throw new Exception("AW Requested skip is out of file size. Nothing to transmit");

            long currentOffset = 0;     // 'End Offset' == receivedRangeSize
            int chunk = CHUNK_SIZE;
            int updateProgressPeriods = 0;

            while (currentOffset < size) {
                if ((currentOffset + chunk) >= size)
                    chunk = (int)(size - currentOffset); // TODO: consider revising

                byte[] readBuf = new byte[chunk];

                if (inStream.read(readBuf) != chunk)
                    throw new Exception("AW Reading of stream suddenly ended");

                writeUsb(readBuf, "AW Failure during NSP transmission.");
                currentOffset += chunk;

                if (updateProgressPeriods++ % 1024 == 0)       // Update progress per each 16mb
                    updateProgressBar((int) ((currentOffset+1)/(size/100+1)));
            }
        }
        resetProgressBar();
    }
    private void sendFileMetadata(byte[] sizeAsBytes) throws Exception{
        writeUsb(STANDARD_REPLY, "AW Response: [1/3]");
        writeUsb(sizeAsBytes, "AW Response: [2/3]");
        writeUsb(TWELVE_ZERO_BYTES, "AW Response: [3/3]");
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