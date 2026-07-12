package com.blogspot.developersu.ns_usbloader.view;

import static com.blogspot.developersu.ns_usbloader.NsConstants.REQUEST_NS_ACCESS_INTENT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_PROGRESS_INTENT;

import android.content.IntentFilter;
import android.hardware.usb.UsbManager;

public class NsMainIntentFilter extends IntentFilter {

    public NsMainIntentFilter() {
        super();
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        addAction(REQUEST_NS_ACCESS_INTENT);
        addAction(SERVICE_TRANSFER_TASK_PROGRESS_INTENT);
        addAction(SERVICE_TRANSFER_TASK_FINISHED_INTENT);
    }
}
