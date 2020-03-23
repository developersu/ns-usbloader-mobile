package com.blogspot.developersu.ns_usbloader.service;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.ResultReceiver;

import com.blogspot.developersu.ns_usbloader.pfs.PFSProvider;
import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

class GoldLeaf extends UsbTransfer {

    private ArrayList<NSPElement> nspElements;
    private PFSProvider pfsElement;

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


    GoldLeaf(ResultReceiver resultReceiver,
             Context context,
             UsbDevice usbDevice,
             UsbManager usbManager,
             ArrayList<NSPElement> nspElements) throws Exception {
        super(resultReceiver, context, usbDevice, usbManager);

        this.nspElements = nspElements;
        String fileName;
        InputStream fileInputStream;

        fileInputStream = context.getContentResolver().openInputStream(nspElements.get(0).getUri());
        fileName = nspElements.get(0).getFilename();
        pfsElement = new PFSProvider(fileInputStream, fileName);
        if (! pfsElement.init())
            throw new Exception("GL File provided have incorrect structure and won't be uploaded.");
    }

    @Override
    boolean run() {
        if (initGoldLeafProtocol(pfsElement))
            status = context.getResources().getString(R.string.status_uploaded);                    // else - no change status that is already set to FAILED

        finish();
        return false;
    }

    private boolean initGoldLeafProtocol(PFSProvider pfsElement){
        // Go parse commands
        byte[] readByte;

        // Go connect to GoldLeaf
        if (writeUsb(CMD_GLUC)){
            issueDescription = "GL Initiating GoldLeaf connection: 1/2";
            return false;
        }

        if (writeUsb(CMD_ConnectionRequest)){
            issueDescription = "GL Initiating GoldLeaf connection: 2/2";
            return false;
        }

        while (true) {
            readByte = readUsb();
            if (readByte == null)
                return false;
            if (Arrays.equals(readByte, CMD_GLUC)) {
                readByte = readUsb();
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
        if (writeUsb(CMD_GLUC)) {
            issueDescription = "GL 'ConnectionResponse' command: INFO: [1/4]";
            return false;
        }

        if (writeUsb(CMD_NSPName)) {
            issueDescription = "GL 'ConnectionResponse' command: INFO: [2/4]";
            return false;
        }

        if (writeUsb(pfsElement.getBytesNspFileNameLength())) {
            issueDescription = "GL 'ConnectionResponse' command: INFO: [3/4]";
            return false;
        }

        if (writeUsb(pfsElement.getBytesNspFileName())) {
            issueDescription = "GL 'ConnectionResponse' command: INFO: [4/4]";
            return false;
        }

        return true;
    }
    /**
     * Start command handler
     * */
    private boolean handleStart(PFSProvider pfsElement){

        if (writeUsb(CMD_GLUC)) {
            issueDescription = "GL Handle 'Start' command: [Send command prepare]";
            return false;
        }

        if (writeUsb(CMD_NSPData)) {
            issueDescription = "GL Handle 'Start' command: [Send command]";
            return false;
        }

        if (writeUsb(pfsElement.getBytesCountOfNca())) {
            issueDescription = "GL Handle 'Start' command: [Send length]";
            return false;
        }

        int ncaCount = pfsElement.getIntCountOfNca();

        for (int i = 0; i < ncaCount; i++){
            if (writeUsb(pfsElement.getNca(i).getNcaFileNameLength())) {
                issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [1/4]";
                return false;
            }

            if (writeUsb(pfsElement.getNca(i).getNcaFileName())) {
                issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [2/4]";
                return false;
            }

            if (writeUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getBodySize()+pfsElement.getNca(i).getNcaOffset()).array())) {   // offset. real.
                issueDescription = "GL Handle 'Start' command: File # "+i+"/"+ncaCount+" step: [3/4]";
                return false;
            }

            if (writeUsb(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(pfsElement.getNca(i).getNcaSize()).array())) {  // size
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
            byte[] readByte = readUsb();
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
            BufferedInputStream bufferedInStream = new BufferedInputStream(context.getContentResolver().openInputStream(nspElements.get(0).getUri()));      // TODO: refactor?
            if (bufferedInStream.skip(realNcaOffset) != realNcaOffset) {
                issueDescription = "GL Failed to skip NCA offset";
                return false;
            }
            int updateProgressPeriods = 0;
            while (readFrom < realNcaSize){
                if (readPice > (realNcaSize - readFrom))
                    readPice = (int)(realNcaSize - readFrom);    // TODO: Troubles could raise here
                readBuf = new byte[readPice];
                if (bufferedInStream.read(readBuf) != readPice) {
                    issueDescription = "GL Failed to read data from file.";
                    return false;
                }

                if (writeUsb(readBuf)) {
                    issueDescription = "GL Failed to write data into NS.";
                    return false;
                }

                readFrom += readPice;
                if (updateProgressPeriods++ % 1024 == 0) // Update progress bar after every 16mb goes to NS
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
