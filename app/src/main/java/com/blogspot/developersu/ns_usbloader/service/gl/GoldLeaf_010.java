package com.blogspot.developersu.ns_usbloader.service.gl;

import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToIntLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToLongLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.intToArrLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.longToArrLE;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.service.UsbTransfer;
import com.blogspot.developersu.ns_usbloader.service.utility.Consumer;
import com.blogspot.developersu.ns_usbloader.service.utility.ServiceResultingDataSet;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.io.BufferedInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GoldLeaf v0.10+
 */
public class GoldLeaf_010 extends UsbTransfer {

    private final static int PACKET_SIZE = 4096;
    //                     CMD
    private final static byte[] EXCEPTION_CAUGHT =    Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F,
                                                                               0x00, 0x00, (byte) 0xF1, (byte) 0xBA}, PACKET_SIZE);
    private final static byte[] INVALID_INDEX =       Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F,
                                                                               0x00, 0x00, (byte) 0xF2, (byte) 0xBA}, PACKET_SIZE);
    //private final static byte[] SELECTION_CANCELLED = Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F,
    //                                                                           0x00, 0x00, (byte) 0xF4, (byte) 0xBA}, PACKET_SIZE);
    private final static byte[] CMD_GLCO_SUCCESS =    Arrays.copyOf(new byte[]{0x47, 0x4c, 0x43, 0x4F,
                                                                               0x00, 0x00, 0x00, 0x00}, PACKET_SIZE);
    private final static byte[] CMD_GLCO_SUCCESS_FLAG = new byte[]{0x47, 0x4c, 0x43, 0x4F, 0x00, 0x00, 0x00, 0x00};

    // System.out.println((356 & 0x1FF) | ((1 + 100) & 0x1FFF) << 9); // 52068 // 0x00 0x00 0xCB 0x64
    private final byte[] GL_OBJECT_TYPE_FILE = new byte[]{0x01, 0x00, 0x00, 0x00};
    //private final byte[] GL_OBJECT_TYPE_DIR = new byte[]{0x02, 0x00, 0x00, 0x00};

    protected long virtDriveSize;

    public GoldLeaf_010(Context context,
                        ArrayList<NSPElement> nspElements,
                        Consumer<Integer> progressCallback,
                        Consumer<ServiceResultingDataSet> serviceCallback,
                        UsbDevice usbDevice,
                        UsbManager usbManager) throws Exception {
        super(context, nspElements, progressCallback, serviceCallback, usbDevice, usbManager);

        // Calculate size of VIRT:/ drive
        for (NSPElement element: nspElements)
            virtDriveSize += element.getSize();
    }
    @Override
    protected void doTransfer() throws Exception {
        while (! Thread.interrupted()) {                          // Till user interrupted process.
            GlString glString1;
            byte[] readByte = readUsb("GL", PACKET_SIZE);

            if (notGLCI(readByte))
                continue;

            switch (GoldleafCmd.get(readByte[4])) {
                case GetDriveCount:
                    getDriveCount();
                    break;
                case GetDriveInfo:
                    getDriveInfo(arrToIntLE(readByte,8));
                    break;
                case GetSpecialPathCount:
                    getSpecialPathCount();
                    break;
                case GetDirectoryCount:
                    getDirectoryOrFileCount(readString(readByte).toString(), true);
                    break;
                case GetFileCount:
                    getDirectoryOrFileCount(readString(readByte).toString(), false);
                    break;
                case GetFile:
                    glString1 = readString(readByte);
                    getFile(glString1.toString(), arrToIntLE(readByte, glString1.length()+12));
                    break;
                case StatPath:
                    statPath(readString(readByte).toString());
                    break;
                case ReadFile:
                    glString1 = readString(readByte);
                    readFile(glString1.toString(),
                            arrToLongLE(readByte, 12+glString1.length()),
                            arrToLongLE(readByte, 12+glString1.length()+8));
                    break;
                case StartFile:
                case EndFile:
                    startOrEndFile();
                    break;
                case GetDirectory: // not supported for virtual drive
                case GetSpecialPath:
                    writeGL_FAIL(INVALID_INDEX, "Not supported "+readByte[4]);
                    break;
                case WriteFile:
                case Rename:
                case Delete:
                case Create:
                case SelectFile:
                case CMD_UNKNOWN:
                default:
                    writeGL_FAIL(EXCEPTION_CAUGHT, "Not supported "+readByte[4]);
            }
        }
        status = context.getResources().getString(R.string.status_uploaded);
        for (NSPElement element: nspElements) {
            element.setStatus(status);
        }
    }

    protected GlString readString(byte[] readByte) {
        return new GlString010(readByte, 8);
    }

    protected boolean notGLCI(byte[] inputBytes) {
        return ! "GLCI".equals(new String(inputBytes, 0, 4));
    }

    // Handle StartFile & EndFile
    protected void startOrEndFile() throws Exception {
        writeGL_PASS("GL 'StartFile' command");
    }
    // Handle GetDriveCount
    protected void getDriveCount() throws Exception {
        writeGL_PASS(intToArrLE(1),"GL 'ListDrives' command");
    }
    // Handle GetDriveInfo
    protected void getDriveInfo(int driveNo) throws Exception {
        if (driveNo != 0) {
            writeGL_FAIL(INVALID_INDEX, "GL 'GetDriveInfo' command [no such drive]");
            return;
        }

        byte[] driveLabel = "Virtual".getBytes();
        byte[] driveLabelLen = intToArrLE(driveLabel.length);
        byte[] driveLetter = "VIRT".getBytes();
        byte[] driveLetterLen = intToArrLE(driveLetter.length);
        byte[] totalFreeSpace = new byte[4];
        byte[] totalSize = Arrays.copyOfRange(longToArrLE(virtDriveSize), 0, 4);

        writeGL_PASS(List.of(
                driveLabelLen,
                driveLabel,
                driveLetterLen,
                driveLetter,
                totalFreeSpace,
                totalSize),"GL 'GetDriveInfo' command");
    }
    // Handle SpecialPathCount — none
    protected void getSpecialPathCount() throws Exception {
        writeGL_PASS(intToArrLE(0), "GL 'SpecialPathCount' command");
    }
    //GetDirectoryCount & GetFileCount
    protected void getDirectoryOrFileCount(String glFileName, boolean isGetDirectoryCount) throws Exception {
        if (! glFileName.equals("VIRT:/"))
            writeGL_FAIL(EXCEPTION_CAUGHT, "GetDirectoryOrFileCount"+(isGetDirectoryCount?"(dir) - ":"(file) - ")+glFileName);
        
        if (isGetDirectoryCount)
            writeGL_PASS("GL 'GetDirectoryCount' command");
        else
            writeGL_PASS(intToArrLE(nspElements.size()), "GL 'GetFileCount'. Count = "+nspElements.size());
    }
    // Handle GetFile
    protected void getFile(String glDirName, int subDirNo) throws Exception {
        if (! glDirName.equals("VIRT:/"))
            writeGL_FAIL(INVALID_INDEX, "GL 'GetFile' [no folders support?]");

        byte[] fileNameBytes = nspElements.get(subDirNo).getFilename().getBytes();
        writeGL_PASS(List.of(
                intToArrLE(fileNameBytes.length),
                fileNameBytes), "GL 'GetFile' command.");
    }
    // Handle StatPath
    protected void statPath(String glFileName) throws Exception {
        if (! glFileName.startsWith("VIRT:/"))
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL 'StatPath' [no such path]: "+glFileName);

        try {
            String fileName = glFileName.replaceFirst("^.*?:/", "");
            writeGL_PASS(List.of(
                    GL_OBJECT_TYPE_FILE,                                                                          // ← int
                    longToArrLE(find(fileName).getSize())), "GL 'StatPath' command for "+glFileName); // ← long
        }
        catch (Exception e) {
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL 'StatPath': "+e.getMessage());
        }
    }
    /**
     * Handle 'ReadFile'
     * @param glFileName full path including new file name in the end in format of Goldleaf
     * @param offset requested offset
     * @param size requested size
     * */
    protected void readFile(String glFileName, long offset, long size) throws Exception {
        if (! glFileName.startsWith("VIRT:/"))
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL 'StatPath' command [no such path]: "+glFileName);

        String fileName = glFileName.replaceFirst("^.*?:/", "");

        try (BufferedInputStream inStream = new BufferedInputStream(
                context.getContentResolver().openInputStream(find(fileName).getUri()))) {
            if (inStream.skip(offset) != offset)
                throw new Exception("Requested skip is out of file size. Nothing to transmit");
            
            byte[] readBuf = new byte[(int) size];
            int bytesRead = inStream.read(readBuf); // How many bytes we got?
            
            if (bytesRead != (int) size) {    // Let's check that we read expected size
                writeGL_FAIL(EXCEPTION_CAUGHT, "ReadFile failure"+offset+" "+size+" "+bytesRead);
                return;
            }
            writeGL_PASS(longToArrLE(size), "GL 'ReadFile' command"); // Reporting result
            writeGlUsb(readBuf, "GL 'ReadFile' file");
        }
        catch (Exception ioe) {
            writeGL_FAIL(EXCEPTION_CAUGHT, "GL 'ReadFile' chain: "+ioe.getMessage());
        }
    }

    /*           GL USB SPECIFIC               */
    protected void writeGL_PASS(String onFailureText) throws Exception {
        writeUsb(CMD_GLCO_SUCCESS, onFailureText);
    }
    protected void writeGL_PASS(byte[] message, String onFailureText) throws Exception {
        writeUsb(ByteBuffer.allocate(PACKET_SIZE)
                .put(CMD_GLCO_SUCCESS_FLAG)
                .put(message)
                .array(), onFailureText);
    }
    protected void writeGL_PASS(List<byte[]> messages, String onFailureText) throws Exception {
        ByteBuffer writeBuffer = ByteBuffer.allocate(PACKET_SIZE)
                .put(CMD_GLCO_SUCCESS_FLAG);
        for (byte[] message: messages)
            writeBuffer.put(message);
        writeUsb(writeBuffer.array(), onFailureText);
    }

    protected void writeGL_FAIL(byte[] failurePacket, String failureMessage) throws Exception {
        writeUsb(failurePacket, failureMessage);
    }

    protected void writeGlUsb(byte[] message, String errorMessage) throws Exception {
        if (message.length <= PACKET_SIZE) {
            writeUsb(message, errorMessage);
            return;
        }

        int pos = 0;
        while (pos < message.length) {
            byte[] chunk = Arrays.copyOfRange(message, pos, Math.min(pos + PACKET_SIZE, message.length));
            writeUsb(chunk, errorMessage);
            pos += PACKET_SIZE;
        }
    }
}