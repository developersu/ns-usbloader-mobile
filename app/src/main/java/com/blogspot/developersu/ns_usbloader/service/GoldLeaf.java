package com.blogspot.developersu.ns_usbloader.service;

import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToIntLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.longToArrLE;
import static java.util.Locale.US;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.pfs.PFSProvider;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Arrays;


class GoldLeaf extends UsbTransfer {

    private static final String GL_READ_FAILURE_1 = "GL initGoldLeafProtocol read failure";
    private static final String GL_READ_FAILURE_2 = "GL handleNSPContent read failure";
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

    private final Uri nspUri;
    private final PFSProvider pfsElement;

    GoldLeaf(Context context,
             ArrayList<NSPElement> nspElements,
             Consumer<ArrayList<NSPElement>> serviceCallback,
             UsbDevice usbDevice,
             UsbManager usbManager) throws Exception {
        super(context, nspElements, serviceCallback, usbDevice, usbManager);
        this.nspUri = nspElements.get(0).getUri();
        this.pfsElement = new PFSProvider(context.getContentResolver().openInputStream(nspUri),
                nspElements.get(0).getFilename());
        if (! pfsElement.init())
            throw new Exception("GL File provided have incorrect structure and won't be uploaded.");
    }

    @Override
    protected void doTransfer() throws Exception {
        initGoldLeafProtocol();
        status = context.getResources().getString(R.string.status_uploaded);
        nspElements.get(0).setStatus(status);
    }

    private void initGoldLeafProtocol() throws Exception {
        writeUsb(CMD_GLUC, "GL Initiating GoldLeaf connection: 1/2");
        writeUsb(CMD_ConnectionRequest, "GL Initiating GoldLeaf connection: 2/2");

        while (true) {
            byte[] readByte = readUsb(GL_READ_FAILURE_1);

            if (Arrays.equals(readByte, CMD_GLUC)) {
                readByte = readUsb(GL_READ_FAILURE_1);

                if (Arrays.equals(readByte, CMD_ConnectionResponse)) {
                    handleConnectionResponse();
                    continue;
                }
                if (Arrays.equals(readByte, CMD_Start)) {
                    handleStart();
                    continue;
                }
                if (Arrays.equals(readByte, CMD_NSPContent)) {
                    handleNSPContent(true);
                    continue;
                }
                if (Arrays.equals(readByte, CMD_NSPTicket)) {
                    handleNSPContent(false);
                    continue;
                }
                if (Arrays.equals(readByte, CMD_Finish)) {
                    break;
                }
            }
        }
    }
    /**
     * ConnectionResponse command handler
     * */
    private void handleConnectionResponse() throws Exception {
        writeUsb(CMD_GLUC, "GL 'ConnectionResponse' [1/4]");
        writeUsb(CMD_NSPName, "GL 'ConnectionResponse' [2/4]");
        writeUsb(pfsElement.getBytesNspFileNameLength(), "GL 'ConnectionResponse' [3/4]");
        writeUsb(pfsElement.getBytesNspFileName(), "GL 'ConnectionResponse' [4/4]");
    }
    /**
     * Start command handler
     * */
    private void handleStart() throws Exception {
        writeUsb(CMD_GLUC, "GL Handle 'Start' command: [Send command prepare]");
        writeUsb(CMD_NSPData, "GL Handle 'Start' command: [Send command]");
        writeUsb(pfsElement.getBytesCountOfNca(), "GL Handle 'Start' command: [Send length]");

        int ncaCount = pfsElement.getIntCountOfNca();
        final String glErrorNotification = "GL Handle 'Start' command: File # %d/%d step: [%d/4]";

        for (int i = 0; i < ncaCount; i++) {
            writeUsb(pfsElement.getNca(i).getNcaFileNameLength(),
                    String.format(US, glErrorNotification, i, ncaCount, 1));
            writeUsb(pfsElement.getNca(i).getNcaFileName(),
                    String.format(US, glErrorNotification, i, ncaCount, 2));
            writeUsb(longToArrLE(pfsElement.getBodySize()+pfsElement.getNca(i).getNcaOffset()),
                    String.format(US, glErrorNotification, i, ncaCount, 3));
            writeUsb(longToArrLE(pfsElement.getNca(i).getNcaSize()),
                    String.format(US, glErrorNotification, i, ncaCount, 4));
        }
    }
    /**
     * NSPContent command handler
     * */
    private void handleNSPContent(boolean isItRawRequest) throws Exception {
        int requestedNcaID;

        if (isItRawRequest) {
            byte[] readByte = readUsb(GL_READ_FAILURE_2);
            if (readByte.length != 4)
                throw new Exception("GL Handle 'Content' command: [Read requested ID] (!=4)");
            requestedNcaID = arrToIntLE(readByte, 0);
        }
        else {
            requestedNcaID = pfsElement.getNcaTicketID();
        }

        long realNcaOffset = pfsElement.getNca(requestedNcaID).getNcaOffset()+pfsElement.getBodySize();
        long realNcaSize = pfsElement.getNca(requestedNcaID).getNcaSize();
        long readFrom = 0;
        int readPice = 16384; // 8mb NOTE: consider switching to 1mb 1048576

        BufferedInputStream bufferedInStream =
                new BufferedInputStream(context.getContentResolver().openInputStream(nspUri));
        if (bufferedInStream.skip(realNcaOffset) != realNcaOffset)
            throw new Exception("GL Failed to skip NCA offset");

        int updateProgressPeriods = 0;
        while (readFrom < realNcaSize) {
            if (readPice > (realNcaSize - readFrom))
                readPice = (int)(realNcaSize - readFrom);    // TODO: Consider revising
            byte[] readBuf = new byte[readPice];
            if (bufferedInStream.read(readBuf) != readPice)
                throw new Exception("GL Failed to read data from file");

            writeUsb(readBuf, "GL Failed to write data into NS.");

            readFrom += readPice;
            if (updateProgressPeriods++ % 1024 == 0) // Update progress bar after every 16mb goes to NS
                updateProgressBar((int) ((readFrom+1)/(realNcaSize/100+1)));
        }
        bufferedInStream.close();

        resetProgressBar();
    }
}