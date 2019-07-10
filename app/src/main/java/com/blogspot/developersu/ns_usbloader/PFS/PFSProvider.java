package com.blogspot.developersu.ns_usbloader.PFS;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Used in GoldLeaf USB protocol
 * */
public class PFSProvider {
    private static final byte[] PFS0 = new byte[]{(byte)0x50, (byte)0x46, (byte)0x53, (byte)0x30};  // PFS0, and what did you think?

    private BufferedInputStream bufferedInStream;
    private String nspFileName;
    private NCAFile[] ncaFiles;
    private long bodySize;
    private int ticketID = -1;

    public PFSProvider(InputStream inputStream, String nspFileName){
        if (inputStream == null || nspFileName == null)
            return;
        this.bufferedInStream = new BufferedInputStream(inputStream);      // TODO: refactor?
        this.nspFileName = nspFileName;
    }
    
    public boolean init() {
        if (nspFileName == null || bufferedInStream == null)
            return false;

        int filesCount;
        int stringTableSize;

        try {
            byte[] fileStartingBytes = new byte[12];
            // Read PFS0, files count, stringTableSize, padding (4 zero bytes)
            if (bufferedInStream.read(fileStartingBytes) != 12){
                bufferedInStream.close();
                return false;
            }
            // Check PFS0
            if (! Arrays.equals(PFS0, Arrays.copyOfRange(fileStartingBytes, 0, 4))){
                bufferedInStream.close();
                return false;
            }
            // Get files count
            filesCount = ByteBuffer.wrap(Arrays.copyOfRange(fileStartingBytes, 4, 8)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (filesCount <= 0 ) {
                bufferedInStream.close();
                return false;
            }
            // Get stringTableSize
            stringTableSize = ByteBuffer.wrap(Arrays.copyOfRange(fileStartingBytes, 8, 12)).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (stringTableSize <= 0 ){
                bufferedInStream.close();
                return false;
            }
            //*********************************************************************************************
            // Create NCA set
            this.ncaFiles = new NCAFile[filesCount];
            // Collect files from NSP
            byte[] ncaInfoArr = new byte[24];   // should be unsigned long, but.. java.. u know my pain man

            HashMap<Integer, Long> ncaNameOffsets = new LinkedHashMap<>();

            long nca_offset;
            long nca_size;
            long nca_name_offset;

            for (int i=0; i < filesCount; i++){
                if (bufferedInStream.read(ncaInfoArr) != 24) {
                    bufferedInStream.close();
                    return false;
                }

                nca_offset = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 4, 12)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                nca_size = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 12, 20)).order(ByteOrder.LITTLE_ENDIAN).getLong();
                nca_name_offset = ByteBuffer.wrap(Arrays.copyOfRange(ncaInfoArr, 20, 24)).order(ByteOrder.LITTLE_ENDIAN).getInt(); // yes, cast from int to long.

                NCAFile ncaFile = new NCAFile();
                ncaFile.setNcaOffset(nca_offset);
                ncaFile.setNcaSize(nca_size);
                this.ncaFiles[i] = ncaFile;

                ncaNameOffsets.put(i, nca_name_offset);
            }
            // Final offset
            byte[] bufForInt = new byte[4];
            if ((bufferedInStream.read(bufForInt) != 4))
                return false;

            // Calculate position including stringTableSize for body size offset
            //bodySize = bufferedInStream.getFilePointer()+stringTableSize;
            bodySize = filesCount*24+16+stringTableSize;
            //*********************************************************************************************
            bufferedInStream.mark(stringTableSize);
            // Collect file names from NCAs
            List<Byte> ncaFN;                 // Temporary
            byte[] b = new byte[1];                 // Temporary
            for (int i=0; i < filesCount; i++){
                ncaFN = new ArrayList<>();
                if (bufferedInStream.skip(ncaNameOffsets.get(i)) != ncaNameOffsets.get(i)) // Files cont * 24(bit for each meta-data) + 4 bytes goes after all of them  + 12 bit what were in the beginning
                    return false;
                while ((bufferedInStream.read(b)) != -1){
                    if (b[0] == 0x00)
                        break;
                    else
                        ncaFN.add(b[0]);
                }
                // TODO: CHANGE TO ncaFN.toArray();

                byte[] exchangeTempArray = new byte[ncaFN.size()];
                for (int j=0; j < ncaFN.size(); j++)
                    exchangeTempArray[j] = ncaFN.get(j);
                // Find and store ticket (.tik)
                if (new String(exchangeTempArray, "UTF-8").toLowerCase().endsWith(".tik"))
                    this.ticketID = i;
                this.ncaFiles[i].setNcaFileName(Arrays.copyOf(exchangeTempArray, exchangeTempArray.length));

                bufferedInStream.reset();
            }
            bufferedInStream.close();
        }
        catch (IOException ioe){
            return false;
        }
        return true;
    }
    /**
     * Return file name as byte array
     * */
    public byte[] getBytesNspFileName(){
        return nspFileName.getBytes();
    }
    /**
     * Return file name as String
     * */
    /*  Legacy code; leave for now
    public String getStringNspFileName(){
        return nspFileName;
    }
    */
    /**
     * Return file name length as byte array
     * */
    public byte[] getBytesNspFileNameLength(){
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(getBytesNspFileName().length).array();
    }
    /**
     * Return NCA count inside of file as byte array
     * */
    public byte[] getBytesCountOfNca(){
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(ncaFiles.length).array();
    }
    /**
     * Return NCA count inside of file as int
     * */
    public int getIntCountOfNca(){
        return ncaFiles.length;
    }
    /**
     * Return requested-by-number NCA file inside of file
     * */
    public NCAFile getNca(int ncaNumber){
        return ncaFiles[ncaNumber];
    }
    /**
     * Return bodySize
     * */
    public long getBodySize(){
        return bodySize;
    }
    /**
     * Return special NCA file: ticket
     * (sugar)
     * */
    public int getNcaTicketID(){
        return ticketID;
    }
}
