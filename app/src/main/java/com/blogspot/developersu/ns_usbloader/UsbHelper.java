package com.blogspot.developersu.ns_usbloader;

import static com.blogspot.developersu.ns_usbloader.NsConstants.REQUEST_NS_ACCESS_INTENT;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class UsbHelper implements Parcelable {

    public static final Creator<UsbHelper> CREATOR = new Creator<>() {
        @Override
        public UsbHelper createFromParcel(Parcel parcel) {
            return new UsbHelper(parcel);
        }

        @Override
        public UsbHelper[] newArray(int size) {
            return new UsbHelper[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeParcelable(ns, 0);
    }

    private static final int VID = 0x057e;
    private static final int PID = 0x3000;

    private UsbDevice ns;
    private UsbManager usbManager;

    private UsbHelper(Parcel parcel) {
        ns = parcel.readParcelable(UsbDevice.class.getClassLoader());
    }

    public UsbHelper(Context context, UsbDevice ns) {
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.ns = ns;
    }

    public UsbDevice get() {
        if (ns == null) {
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getVendorId() == VID && device.getProductId() == PID) {
                    ns = device;
                    break;
                }
            }
        }
        return ns;
    }

    public void restoreState(Context context) {
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public void setNsDetached() {
        ns = null;
    }

    /**
     * Checks permission and requests if needed
     * @return false if permission granted, otherwise false
     * */
    public boolean isNotHavePermission(Context context) {
        if (usbManager.hasPermission(ns))
            return false;

        usbManager.requestPermission(ns,
                PendingIntent.getBroadcast(context,
                        0,
                        new Intent(REQUEST_NS_ACCESS_INTENT),
                        PendingIntent.FLAG_IMMUTABLE));
        return true;
    }
}