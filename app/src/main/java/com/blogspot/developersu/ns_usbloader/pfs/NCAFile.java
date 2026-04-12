package com.blogspot.developersu.ns_usbloader.pfs;

import static com.blogspot.developersu.ns_usbloader.DataConvertUtils.intToArrLE;

/**
 * Data class to hold NCA, tik, XML etc. meta-information
 */
public class NCAFile {
    //private int ncaNumber;
    private byte[] ncaFileName;
    private long ncaOffset;
    private long ncaSize;

    //public void setNcaNumber(int ncaNumber){ this.ncaNumber = ncaNumber; }

    public void setNcaFileName(byte[] ncaFileName) {
        this.ncaFileName = ncaFileName;
    }

    public void setNcaOffset(long ncaOffset) {
        this.ncaOffset = ncaOffset;
    }

    public void setNcaSize(long ncaSize) {
        this.ncaSize = ncaSize;
    }

    //public int getNcaNumber() {return this.ncaNumber; }

    public byte[] getNcaFileName() {
        return ncaFileName;
    }

    public byte[] getNcaFileNameLength() {
        return intToArrLE(ncaFileName.length);
    }

    public long getNcaOffset() {
        return ncaOffset;
    }

    public long getNcaSize() {
        return ncaSize;
    }
}
