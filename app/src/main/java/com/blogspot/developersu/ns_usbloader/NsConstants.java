package com.blogspot.developersu.ns_usbloader;

public class NsConstants {
    public static final String NS_RESULT_RECEIVER = "RECEIVER";
    // Request permissions to access NS USB device
    public static final String REQUEST_NS_ACCESS_INTENT = "com.blogspot.developersu.ns_usbloader.ACTION_USB_PERMISSION";
    // Get in BroadcastReceiver and MainActivity's broadcastReceiver information regarding finished process
    public static final String SERVICE_TRANSFER_TASK_FINISHED_INTENT = "com.blogspot.developersu.ns_usbloader.SERVICE_TRANSFER_TASK_FINISHED";
    // To get data inside IntentService
    public static final String SERVICE_CONTENT_NSP_LIST = "NSP_LIST";
    public static final String SERVICE_CONTENT_PROTOCOL = "PROTOCOL";
    public static final String SERVICE_CONTENT_NS_DEVICE = "DEVICE";
    // Result Reciever possible codes
    public static final int NS_RESULT_PROGRESS_INDETERMINATE = -1;  // upper limit would be 0; value would be 0
    public static final int NS_RESULT_PROGRESS_VALUE = 0;
    // Declare TF/GL names
    public static final int PROTO_TF_USB = 10;
    public static final int PROTO_TF_NET = 20;
    public static final int PROTO_GL_USB = 30;
}
