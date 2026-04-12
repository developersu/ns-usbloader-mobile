package com.blogspot.developersu.ns_usbloader.pfs;

import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToIntLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.arrToLongLE;
import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.intToArrLE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Used in GoldLeaf USB protocol
 * */
public class PFSProvider {
    private static final byte[] PFS0 = new byte[]{0x50, 0x46, 0x53, 0x30};

    private BufferedInputStream bufferedInStream;
    private String nspFileName;
    private NCAFile[] ncaFiles;
    private long bodySize;
    private int ticketID = -1;

    public PFSProvider(InputStream inputStream, String nspFileName){
        if (inputStream == null || nspFileName == null)
            return;
        this.bufferedInStream = new BufferedInputStream(inputStream); // TODO: consider refactoring
        this.nspFileName = nspFileName;
    }
    
    public boolean init() {
        if (nspFileName == null || bufferedInStream == null)
            return false;

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

            int filesCount = arrToIntLE(fileStartingBytes, 4);
            if (filesCount <= 0 ) {
                bufferedInStream.close();
                return false;
            }

            int stringTableSize = arrToIntLE(fileStartingBytes, 8);
            if (stringTableSize <= 0 ) {
                bufferedInStream.close();
                return false;
            }
            //*********************************************************************************************
            // Create NCA set
            this.ncaFiles = new NCAFile[filesCount];
            // Collect files from NSP
            byte[] ncaInfoArr = new byte[24];   // should be unsigned long

            HashMap<Integer, Long> ncaNameOffsets = new LinkedHashMap<>();

            for (int i=0; i < filesCount; i++) {
                if (bufferedInStream.read(ncaInfoArr) != 24) {
                    bufferedInStream.close();
                    return false;
                }

                long nca_offset = arrToLongLE(ncaInfoArr, 4);
                long nca_size = arrToLongLE(ncaInfoArr, 12);
                long nca_name_offset = arrToIntLE(ncaInfoArr, 20); // cast int → long.

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
            bodySize = filesCount*24L+16+stringTableSize;
            //*********************************************************************************************
            bufferedInStream.mark(stringTableSize);
            // Collect file names from NCAs

            byte[] b = new byte[1];                 // Temporary
            for (int i=0; i < filesCount; i++) {
                List<Byte> ncaFN = new ArrayList<>();
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
                    ticketID = i;
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
     * File name as byte array
     * */
    public byte[] getBytesNspFileName(){
        return nspFileName.getBytes();
    }
    /**
     * File name length as byte array
     * */
    public byte[] getBytesNspFileNameLength(){
        return intToArrLE(getBytesNspFileName().length);
    }
    /**
     * NCA count inside of file as byte array
     * */
    public byte[] getBytesCountOfNca(){
        return intToArrLE(ncaFiles.length);
    }
    /**
     * NCA count inside of file as int
     * */
    public int getIntCountOfNca(){
        return ncaFiles.length;
    }
    /**
     * Requested-by-number NCA file inside of file
     * */
    public NCAFile getNca(int ncaNumber){
        return ncaFiles[ncaNumber];
    }

    public long getBodySize(){
        return bodySize;
    }
    /**
     * Special NCA file: ticket
     * */
    public int getNcaTicketID(){
        return ticketID;
    }
}
