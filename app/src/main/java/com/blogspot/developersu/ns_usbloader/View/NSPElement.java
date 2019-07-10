package com.blogspot.developersu.ns_usbloader.View;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public class NSPElement implements Parcelable {
    private Uri uri;
    private String filename;
    private long size;
    private String status;
    private boolean selected;

    public NSPElement(Uri uri, String filename, long size){
        this.uri = uri;
        this.filename = filename;
        this.size = size;
        this.status = "";
        this.selected = false;
    }

    public Uri getUri() { return uri; }
    public String getFilename() { return filename; }
    public long getSize() { return size; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }

    /*-----------------------
    / Parcelable shit next
    /-----------------------*/
    private NSPElement(Parcel parcel){
        uri = Uri.parse(parcel.readString());
        filename = parcel.readString();
        size = parcel.readLong();
        status = parcel.readString();
        selected = parcel.readByte() == 0x1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel outParcel, int flags) {
        outParcel.writeString(uri.toString());
        outParcel.writeString(filename);
        outParcel.writeLong(size);
        outParcel.writeString(status);
        outParcel.writeByte((byte) (selected ? 0x1 : 0x0));
    }

    public static final Parcelable.Creator<NSPElement> CREATOR
            = new Parcelable.Creator<NSPElement>() {
        public NSPElement createFromParcel(Parcel in) {
            return new NSPElement(in);
        }

        public NSPElement[] newArray(int size) {
            return new NSPElement[size];
        }
    };
}
