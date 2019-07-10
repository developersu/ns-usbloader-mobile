package com.blogspot.developersu.ns_usbloader.Service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import com.blogspot.developersu.ns_usbloader.NsConstants;
import com.blogspot.developersu.ns_usbloader.PFS.PFSProvider;
import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.View.NSPElement;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommunicationsService extends IntentService {
    private static final String SERVICE_TAG = "com.blogspot.developersu.ns_usbloader.Service.CommunicationsService";

    private static AtomicBoolean isActive = new AtomicBoolean(false);
    private static AtomicBoolean interrupt = new AtomicBoolean(false);

    private ResultReceiver resultReceiver;

    public static boolean isServiceActive(){
        return isActive.get();
    }
    public static void cancel(){
        interrupt.set(true);
    }

    public CommunicationsService() {
        super(SERVICE_TAG);
    }

    private ArrayList<NSPElement> nspElements;
    private UsbDeviceConnection deviceConnection;
    private UsbInterface usbInterface;
    private UsbEndpoint epIn;
    private UsbEndpoint epOut;

    private String status;
    private String issueDescription;

    @Override
    protected void onHandleIntent(Intent intent) {
        isActive.set(true);
        interrupt.set(false);
        status = getResources().getString(R.string.status_failed_to_upload);
        resultReceiver = intent.getParcelableExtra(NsConstants.NS_RESULT_RECEIVER);
        nspElements = intent.getParcelableArrayListExtra(NsConstants.SERVICE_CONTENT_NSP_LIST);
        final int protocol = intent.getIntExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, -1);  // -1 since it's impossible
        UsbDevice usbDevice = intent.getParcelableExtra(NsConstants.SERVICE_CONTENT_NS_DEVICE);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (nspElements == null || usbDevice == null || usbManager == null || protocol < 0) {
            reportExecutionFinish();
            return;
        }
        // Start process
        usbInterface = usbDevice.getInterface(0);
        epIn = usbInterface.getEndpoint(0); // For bulk read
        epOut = usbInterface.getEndpoint(1); // For bulk write

        deviceConnection = usbManager.openDevice(usbDevice);
        if ( ! deviceConnection.claimInterface(usbInterface, false))
            return;

        if (protocol == NsConstants.PROTO_TF_USB){
            new TinFoil();
        }
        else if (protocol == NsConstants.PROTO_GL_USB){
            new GoldLeaf();
        }

        for (NSPElement e: nspElements)
            e.setStatus(status);
        /*
        Log.i("LPR", "Status " +status);
        Log.i("LPR", "issue " +issueDescription);
        Log.i("LPR", "Interrupt " +interrupt.get());
        Log.i("LPR", "Active " +isActive.get());
        */
        reportExecutionFinish();
    }

    private void resetProgressBar(){
        resultReceiver.send(NsConstants.NS_RESULT_PROGRESS_INDETERMINATE, Bundle.EMPTY);
    }

    private void updateProgressBar(int currentPosition){
        Bundle bundle = new Bundle();
        bundle.putInt("POSITION", currentPosition);
        resultReceiver.send(NsConstants.NS_RESULT_PROGRESS_VALUE, bundle);
    }

    private void reportExecutionFinish(){
        deviceConnection.releaseInterface(usbInterface);
        deviceConnection.close();
        isActive.set(false);
        Intent executionFinishIntent = new Intent(NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT);
        executionFinishIntent.putExtra(NsConstants.SERVICE_CONTENT_NSP_LIST, nspElements);
        if (issueDescription != null) {
            executionFinishIntent.putExtra("ISSUES", issueDescription);
        }
        this.sendBroadcast(executionFinishIntent);
        interrupt.set(false);
    }
    /*============================================================================================*/
    /**
     * Sending any byte array to USB device
     * @return 'false' if no issues
     *          'true' if errors happened
     * */
    private boolean writeToUsb(byte[] message){
        int result;
        result = deviceConnection.bulkTransfer(epOut, message, message.length, 0);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint OUT = 0x01
        //Log.i("LPR", "RES: "+result);
        return (result != message.length);
    }
    /**
     * Reading what USB device responded.
     * @return byte array if data read successful
     *         'null' if read failed
     * */
    private byte[] readFromUsb(){
        byte[] readBuffer = new byte[512];
        // We can limit it to 32 bytes, but there is a non-zero chance to got OVERFLOW from libusb.
        int result;
        result = deviceConnection.bulkTransfer(epIn, readBuffer, 512, 0);  // last one is TIMEOUT. 0 stands for unlimited. Endpoint IN = 0x81

        if (result > 0)
            return Arrays.copyOf(readBuffer, result);
        return null;
    }
    /*============================================================================================*/
    private class TinFoil{
        TinFoil(){

            if (!sendListOfNSP())
                return;

            if (proceedCommands())                              // REPORT SUCCESS
                status = getResources().getString(R.string.status_uploaded);                            // Don't change status that is already set to FAILED TODO: FIX
        }
        // Send what NSP will be transferred

        private boolean sendListOfNSP(){
            // Send list of NSP files:
            // Proceed "TUL0"
            if (writeToUsb("TUL0".getBytes())) {  // new byte[]{(byte) 0x54, (byte) 0x55, (byte) 0x76, (byte) 0x30} //"US-ASCII"?
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
            if (writeToUsb(nspListSize)) {                                           // size of the list we're going to transfer goes...
                issueDescription = "TF Send list of files: [send list length]";
                return false;
            }

            if (writeToUsb(new byte[8])) {                                           // 8 zero bytes goes...
                issueDescription = "TF Send list of files: [send padding]";
                return false;
            }

            if (writeToUsb(nspListNames)) {                                           // list of the names goes...
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
                if (interrupt.get())     // Check if user interrupted process.
                    return false;
                receivedArray = readFromUsb();
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
            receivedArray = readFromUsb();
            if (receivedArray == null) {
                issueDescription = "TF Unable to get meta information @fileRangeCmd()";
                return false;
            }

            // range_offset of the requested file. In the begining it will be 0x10.
            long receivedRangeSize = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 0,8)).order(ByteOrder.LITTLE_ENDIAN).getLong();
            byte[] receivedRangeSizeRAW = Arrays.copyOfRange(receivedArray, 0,8);
            long receivedRangeOffset = ByteBuffer.wrap(Arrays.copyOfRange(receivedArray, 8,16)).order(ByteOrder.LITTLE_ENDIAN).getLong();

            // Requesting UTF-8 file name required:
            receivedArray = readFromUsb();
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
            if (!sendResponse(receivedRangeSizeRAW))   // Get receivedRangeSize in 'RAW' format exactly as it has been received. It's simply.
                return false;                          // issueDescription handled by method

            try {
                BufferedInputStream bufferedInStream = null;

                for (NSPElement e: nspElements){
                    if (e.getFilename().equals(receivedRequestedNSP)){
                        InputStream elementIS = getContentResolver().openInputStream(e.getUri());
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

                byte[] readBuf ;//= new byte[1048576];        // eq. Allocate 1mb

                if (bufferedInStream.skip(receivedRangeOffset) != receivedRangeOffset){
                    issueDescription = "TF Requested skip is out of file size. Nothing to transmit.";
                    return false;
                }

                long readFrom = 0;
                // 'End Offset' equal to receivedRangeSize.
                int readPice = 16384;                     // = 8Mb

                while (readFrom < receivedRangeSize){
                    if (interrupt.get())     // Check if user interrupted process.
                        return true;
                    if ((readFrom + readPice) >= receivedRangeSize )
                        readPice = (int)(receivedRangeSize - readFrom);    // TODO: Troubles could raise here

                    readBuf = new byte[readPice];                         // TODO: not perfect moment, consider refactoring.

                    if (bufferedInStream.read(readBuf) != readPice) {
                        issueDescription = "TF Reading of stream suddenly ended";
                        return false;
                    }
                    //write to USB
                    if (writeToUsb(readBuf)) {
                        issueDescription = "TF Failure during NSP transmission.";
                        return false;
                    }
                    readFrom += readPice;

                    updateProgressBar((int) ((readFrom+1)/(receivedRangeSize/100+1)));
                    Log.i("LPR", "CO: "+readFrom+"RRS: "+receivedRangeSize+"RES: "+(readFrom+1/(receivedRangeSize/100+1)));
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
         * @return true if everything OK
         *         false if failed
         * */
        private boolean sendResponse(byte[] rangeSize){                                 // This method as separate function itself for application needed as a cookie in the middle of desert.
            if (writeToUsb(new byte[] { (byte) 0x54, (byte) 0x55, (byte) 0x43, (byte) 0x30,    // 'TUC0'
                    (byte) 0x01,                                                // CMD_TYPE_RESPONSE = 1
                    (byte) 0x00, (byte) 0x00, (byte) 0x00,                      // kinda padding. Guys, didn't you want to use integer value for CMD semantic?
                    (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00} )       // Send integer value of '1' in Little-endian format.
            ){
                issueDescription = "TF Sending response: [1/3]";
                return false;
            }

            if(writeToUsb(rangeSize)) {                                                          // Send EXACTLY what has been received
                issueDescription = "TF Sending response: [2/3]";
                return false;
            }

            if(writeToUsb(new byte[12])) {                                                       // kinda another one padding
                issueDescription = "TF Sending response: [3/3] FAIL";
                return false;
            }
            return true;
        }

    }
    /**
     * GoldLeaf processing
     * */
    private class GoldLeaf{
        //                     CMD                                G     L     U     C
        private final byte[] CMD_GLUC =               new byte[]{0x47, 0x4c, 0x55, 0x43};
        private final byte[] CMD_ConnectionRequest =  new byte[]{0x00, 0x00, 0x00, 0x00};    // Write-only command
        private final byte[] CMD_NSPName =            new byte[]{0x02, 0x00, 0x00, 0x00};    // Write-only command
        private final byte[] CMD_NSPData =            new byte[]{0x04, 0x00, 0x00, 0x00};    // Write-only command

        private final byte[] CMD_ConnectionResponse = new byte[]{0x01, 0x00, 0x00, 0x00};
        private final byte[] CMD_Start =              new byte[]{0x03, 0x00, 0x00, 0x00};
        private final byte[] CMD_NSPContent =         new byte[]{0x05, 0x00, 0x00, 0x00};
        private final byte[] CMD_NSPTicket =          new byte[]{0x06, 0x00, 0x00, 0x00};
        private final byte[] CMD_Finish =             new byte[]{0x07, 0x00, 0x00, 0x00};

        GoldLeaf(){
            String fileName;
            InputStream fileInputStream;
            try {
                fileInputStream = getContentResolver().openInputStream(nspElements.get(0).getUri());
                fileName = nspElements.get(0).getFilename();
            }
            catch (java.io.FileNotFoundException fnfe){
                issueDescription = "GL FileNotFoundException @GoldLeaf()";
                return;
            }

            PFSProvider pfsElement = new PFSProvider(fileInputStream, fileName);
            if (!pfsElement.init()) {
                issueDescription = "GL File provided have incorrect structure and won't be uploaded.";
                status = getResources().getString(R.string.status_wrong_file);
                return;
            }

            if (initGoldLeafProtocol(pfsElement))
                status = getResources().getString(R.string.status_uploaded);                    // else - no change status that is already set to FAILED
        }

        private boolean initGoldLeafProtocol(PFSProvider pfsElement){
            // Go parse commands
            byte[] readByte;

            // Go connect to GoldLeaf
            if (writeToUsb(CMD_GLUC)){
                issueDescription = "GL Initiating GoldLeaf connection: 1/2";
                return false;
            }

            if (writeToUsb(CMD_ConnectionRequest)){
                issueDescription = "GL Initiating GoldLeaf connection: 2/2";
                return false;
            }

            while (true) {
                readByte = readFromUsb();
                if (readByte == null)
                    return false;
                if (Arrays.equals(readByte, CMD_GLUC)) {
                    readByte = readFromUsb();
                    if (readByte == null)
                        return false;
                    if (Arrays.equals(readByte, CMD_ConnectionResponse)) {
                        if (!handleConnectionResponse(pfsElement))
                            return false;
                        continue;
                    }
                    if (Arrays.equals(readByte, CMD_Start)) {
                        if (!handleStart(pfsElement))
                            return false;
                        continue;
                    }
                    if (Arrays.equals(readByte, CMD_NSPContent)) {
                        if (!handleNSPContent(pfsElement, true))
                            return false;
                        continue;
                    }
                    if (Arrays.equals(readByte, CMD_NSPTicket)) {
                        if (!handleNSPContent(pfsElement, false))
                            return false;
                        continue;
                    }
                    if (Arrays.equals(readByte, CMD_Finish)) {  // All good
                        break;
                    }
                }
            }
            return true;
        }
        /**
         * ConnectionResponse command handler
         * */
        private boolean handleConnectionResponse(PFSProvider pfsElement){
            if (writeToUsb(CMD_GLUC)) {
                issueDescription = "GL 'ConnectionResponse' command: INFO: [1/4]";
                return false;
            }

            if (writeToUsb(CMD_NSPName)) {
                issueDescription = "GL 'ConnectionResponse' command: INFO: [2/4]";
                return false;
            }

            if (writeToUsb(pfsElement.getBytesNspFileNameLength())) {
                issueDescription = "GL 'ConnectionResponse' command: INFO: [3/4]";
                return false;
            }

            if (writeToUsb(pfsElement.getBytesNspFileName())) {
                issueDescription = "GL 'ConnectionResponse' command: INFO: [4/4]";
                return false;
            }

            return true;
        }
        /**
         * Start command handler
         * */
        private boolean handleStart(PFSProvider pfsElement){

            if (writeToUsb(CMD_GLUC)) {
                issueDescription = "GL Handle 'Start' command: [Send command prepare]";
                return false;
            }

            if (writeToUsb(CMD_NSPData)) {
                issueDescription = "GL Handle 'Start' command: [Send command]";
                return false;
            }

            if (writeToUsb(pfsElement.getBytesCountOfNca())) {
                issueDescription = "GL Handle 'Start' command: [Send length]";
                return false;
            }

            int ncaCount = pfsElement.getIntCountOfNca();

            for (int i = 0; i < ncaCount; i++){
                if (writeToUsb(pfsElement.getNca(i).getNcaFileNameLength())) {
                    issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [1/4]";
                    return false;
                }

                if (writeToUsb(pfsElement.getNca(i).getNcaFileName())) {
                    issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [2/4]";
                    return false;
                }

                if (writeToUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getBodySize()+pfsElement.getNca(i).getNcaOffset()).array())) {   // offset. real.
                    issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [3/4]";
                    return false;
                }

                if (writeToUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getNca(i).getNcaSize()).array())) {  // size
                    issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [4/4]";
                    return false;
                }
            }
            return true;
        }
        /**
         * NSPContent command handler
         * isItRawRequest - if True, just ask NS what's needed
         *                - if False, send ticket
         * */
        private boolean handleNSPContent(PFSProvider pfsElement, boolean isItRawRequest){
            int requestedNcaID;

            if (isItRawRequest) {
                byte[] readByte = readFromUsb();
                if (readByte == null || readByte.length != 4) {
                    issueDescription = "GL Handle 'Content' command: [Read requested ID]";
                    return false;
                }
                requestedNcaID = ByteBuffer.wrap(readByte).order(ByteOrder.LITTLE_ENDIAN).getInt();
            }
            else {
                requestedNcaID = pfsElement.getNcaTicketID();
            }

            long realNcaOffset = pfsElement.getNca(requestedNcaID).getNcaOffset()+pfsElement.getBodySize();
            long realNcaSize = pfsElement.getNca(requestedNcaID).getNcaSize();

            long readFrom = 0;

            int readPice = 16384; // 8mb NOTE: consider switching to 1mb 1048576
            byte[] readBuf;

            try{
                BufferedInputStream bufferedInStream = new BufferedInputStream(getContentResolver().openInputStream(nspElements.get(0).getUri()));      // TODO: refactor?
                if (bufferedInStream.skip(realNcaOffset) != realNcaOffset) {
                    issueDescription = "GL Failed to skip NCA offset";
                    return false;
                }

                while (readFrom < realNcaSize){
                    if (interrupt.get())     // Check if user interrupted process.
                        return false;

                    if (readPice > (realNcaSize - readFrom))
                        readPice = (int)(realNcaSize - readFrom);    // TODO: Troubles could raise here
                    readBuf = new byte[readPice];
                    if (bufferedInStream.read(readBuf) != readPice) {
                        issueDescription = "GL Failed to read data from file.";
                        return false;
                    }

                    if (writeToUsb(readBuf)) {
                        issueDescription = "GL Failed to write data into NS.";
                        return false;
                    }

                    readFrom += readPice;

                    updateProgressBar((int) ((readFrom+1)/(realNcaSize/100+1)));
                }
                bufferedInStream.close();

                resetProgressBar();
            }
            catch (java.io.IOException ioe){
                issueDescription = "GL Failed to read NCA ID "+requestedNcaID+". IO Exception: "+ioe.getMessage();
                return false;
            }
            return true;
        }
    }
}
