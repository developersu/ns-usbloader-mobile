package com.blogspot.developersu.ns_usbloader;

public class NsConstants {
    public static final String NS_RESULT_RECEIVER = "RECEIVER";
    // Request permissions to access NS USB device
    public static final String REQUEST_NS_ACCESS_INTENT = "com.blogspot.developersu.ns_usbloader.ACTION_USB_PERMISSION";
    // Get in BroadcastReceiver and MainActivity's broadcastReceiver information regarding finished process
    public static final String SERVICE_TRANSFER_TASK_FINISHED_INTENT = "com.blogspot.developersu.ns_usbloader.SERVICE_TRANSFER_TASK_FINISHED";
    // To get data inside Service
    public static final String NSS_CONTENT_LIST = "NSP_LIST";
    public static final String NSS_PROTOCOL = "PROTOCOL";
    public static final String NSS_NS_DEVICE = "DEVICE";
    public static final String NSS_NS_IP = "DEVICE_IP";
    public static final String NSS_PHONE_IP = "PHONE_IP";
    public static final String NSS_PHONE_PORT = "PHONE_PORT";
    // Result Receiver possible codes
    public static final int NS_RESULT_PROGRESS_INDETERMINATE = -1;  // upper limit would be 0; value would be 0
    public static final int NS_RESULT_PROGRESS_VALUE = 0;
    // Declare TF/GL names
    public static final int PROTO_UNKNOWN = -1;
    public static final int PROTO_TF_USB = 10;
    public static final int PROTO_TF_NET = 20;
    public static final int PROTO_GL_USB = 30;

    public static final int NS_NOTIFICATION_TRANSFER_ID = 1;
    public static final Class<?> MAIN_ACTIVITY_CLASS = MainActivity.class;
    // Default settings
    public static String DEFAULT_NS_IP = "192.168.1.42";
    public static String DEFAULT_PHONE_IP = "192.168.1.142";
    public static int DEFAULT_PHONE_PORT = 6042;
}
