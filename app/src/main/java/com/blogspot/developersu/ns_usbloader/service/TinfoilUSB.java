package com.blogspot.developersu.ns_usbloader.service;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.ResultReceiver;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

class TinfoilUSB extends UsbTransfer {
    private ArrayList<NSPElement> nspElements;

    private byte[] replyConstArray = new byte[] { (byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30,   // 'TUC0'
                                                  (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00,   // CMD_TYPE_RESPONSE = 1
                                                  (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00  };

    TinfoilUSB(ResultReceiver resultReceiver,
               Context context,
               UsbDevice usbDevice,
               UsbManager usbManager,
               ArrayList<NSPElement> nspElements) throws Exception{
        super(resultReceiver, context, usbDevice, usbManager);
        this.nspElements = nspElements;
    }

    @Override
    boolean run(){
        if (! sendListOfNSP()) {
            finish();
            return true;
        }

        if (proceedCommands())                              // REPORT SUCCESS
            status = context.getResources().getString(R.string.status_uploaded);  // Don't change status that is already set to FAILED TODO: FIX
        finish();

        return false;
    }

    // Send what NSP will be transferred
    private boolean sendListOfNSP(){
        // Send list of NSP files:
        // Proceed "TUL0"
        if (writeUsb("TUL0".getBytes())) {  // new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x76, (byte) 0x30} //"US-ASCII"?
            issueDescription  = "TF Send list of files: handshake failure";
            return false;
        }
        //Collect file names
        StringBuilder nspListNamesBuilder = new StringBuilder();    // Add every title to one stringBuilder
        for(NSPElement element: nspElements) {
            nspListNamesBuilder.append(element.getFilename());   // And here we come with java string default encoding (UTF-16)
            nspListNamesBuilder.append('\n');
        }

        byte[] nspListNames = nspListNamesBuilder.toString().getBytes(); // android's .getBytes() default == UTF8
        ByteBuffer byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);         // integer = 4 bytes; BTW Java is stored in big-endian format
        byteBuffer.putInt(nspListNames.length);                                                             // This way we obtain length in int converted to byte array in correct Big-endian order. Trust me.
        byte[] nspListSize = byteBuffer.array();

        // Sending NSP list
        if (writeUsb(nspListSize)) {                                           // size of the list we're going to transfer goes...
            issueDescription = "TF Send list of files: [send list length]";
            return false;
        }

        if (writeUsb(new byte[8])) {                                           // 8 zero bytes goes...
            issueDescription = "TF Send list of files: [send padding]";
            return false;
        }

        if (writeUsb(nspListNames)) {                                           // list of the names goes...
            issueDescription = "TF Send list of files: [send list itself]";
            return false;
        }

        return true;
    }

    // After we sent commands to NS, this chain starts

    private boolean proceedCommands(){
        final byte[] magic = new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30};  // eq. 'TUC0' @ UTF-8 (actually ASCII lol, u know what I mean)

        byte[] receivedArray;

        while (true){
            receivedArray = readUsb();
            if (receivedArray == null)
                return false;             // catches exception

            if (!Arrays.equals(Arrays.copyOfRange(receivedArray, 0,4), magic))      // Bytes from 0 to 3 should contain 'magic' TUC0, so must be verified like this
                continue;

            // 8th to 12th(explicits) bytes in returned data stands for command ID as unsigned integer (Little-endian). Actually, we have to compare arrays here, but in real world it can't be greater then 0/1/2, thus:
            // BTW also protocol specifies 4th byte to be 0x00 kinda indicating that that this command is valid. But, as you may see, never happens other situation when it's not = 0.
            if (receivedArray[8] == 0x00){                           //0x00 - exit
                return true;                     // All interaction with USB device should be ended (expected);
            }
            else if ((receivedArray[8] == 0x01) || (receivedArray[8] == 0x02)){           //0x01 - file range; 0x02 unknown bug on backend side (dirty hack).
                if (!fileRangeCmd())    // issueDescription inside
                    return false;
            }
        }
    }
    /**
     * This is what returns requested file (files)
     * Executes multiple times
     * @return 'true' if everything is ok
     *          'false' is error/exception occurs
     * */

    private boolean fileRangeCmd(){
        byte[] receivedArray;
        // Here we take information of what other side wants
        receivedArray = readUsb();
        if (receivedArray == null) {
            issueDescription = "TF Unable to get meta information @fileRangeCmd()";
            return false;
        }

        // range_offset of the requested file. In the begining it will be 0x10.
        long receivedRangeSize = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 0,8)).order(ByteOrder.LITTLE_ENDIAN).getLong();
        byte[] receivedRangeSizeRAW = Arrays.copyOfRange(receivedArray, 0,8);
        long receivedRangeOffset = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 8,16)).order(ByteOrder.LITTLE_ENDIAN).getLong();

        // Requesting UTF-8 file name required:
        receivedArray = readUsb();
        if (receivedArray == null) {
            issueDescription = "TF Unable to get file name @fileRangeCmd()";
            return false;
        }
        String receivedRequestedNSP;
        try {
            receivedRequestedNSP = new String(receivedArray, "UTF-8"); //TODO:FIX
        }
        catch (java.io.UnsupportedEncodingException uee){
            issueDescription = "TF UnsupportedEncodingException @fileRangeCmd()";
            return false;
        }

        // Sending response header
        if (sendResponse(receivedRangeSizeRAW))   // Get receivedRangeSize in 'RAW' format exactly as it has been received. It's simply.
            return false;                          // issueDescription handled by method

        try {
            BufferedInputStream bufferedInStream = null;

            for (NSPElement e: nspElements){
                if (e.getFilename().equals(receivedRequestedNSP)){
                    InputStream elementIS = context.getContentResolver().openInputStream(e.getUri());
                    if (elementIS == null) {
                        issueDescription = "TF Unable to obtain InputStream";
                        return false;
                    }
                    bufferedInStream = new BufferedInputStream(elementIS);      // TODO: refactor?
                    break;
                }
            }

            if (bufferedInStream == null) {
                issueDescription = "TF Unable to create BufferedInputStream";
                return false;
            }

            byte[] readBuf;//= new byte[1048576];        // eq. Allocate 1mb

            if (bufferedInStream.skip(receivedRangeOffset) != receivedRangeOffset){
                issueDescription = "TF Requested skip is out of file size. Nothing to transmit.";
                return false;
            }

            long readFrom = 0;
            // 'End Offset' equal to receivedRangeSize.
            int readPice = 16384;                     // 8388608 = 8Mb
            int updateProgressPeriods = 0;

            while (readFrom < receivedRangeSize){
                if ((readFrom + readPice) >= receivedRangeSize )
                    readPice = (int)(receivedRangeSize - readFrom);    // TODO: Troubles could raise here

                readBuf = new byte[readPice];                         // TODO: not perfect moment, consider refactoring.

                if (bufferedInStream.read(readBuf) != readPice) {
                    issueDescription = "TF Reading of stream suddenly ended";
                    return false;
                }
                //write to USB
                if (writeUsb(readBuf)) {
                    issueDescription = "TF Failure during NSP transmission.";
                    return false;
                }
                readFrom += readPice;

                if (updateProgressPeriods++ % 1024 == 0) // Update progress bar after every 16mb goes to NS
                    updateProgressBar((int) ((readFrom+1)/(receivedRangeSize/100+1))); // This shit takes too much time
                //Log.i("LPR", "CO: "+readFrom+"RRS: "+receivedRangeSize+"RES: "+(readFrom+1/(receivedRangeSize/100+1)));
            }
            bufferedInStream.close();

            resetProgressBar();
        } catch (java.io.IOException ioe){
            issueDescription = "TF IOException: "+ioe.getMessage();
            return false;
        }
        return true;
    }
    /**
     * Send response header.
     * @return false if everything OK
     *         true if failed
     * */
    private boolean sendResponse(byte[] rangeSize){
        if (writeUsb(replyConstArray)){
            issueDescription = "TF Response: [1/3]";
            return true;
        }

        if(writeUsb(rangeSize)) {                                                          // Send EXACTLY what has been received
            issueDescription = "TF Response: [2/3]";
            return true;
        }

        if(writeUsb(new byte[12])) {                                                       // kinda another one padding
            issueDescription = "TF Response: [3/3]";
            return true;
        }
        return false;
    }

}
