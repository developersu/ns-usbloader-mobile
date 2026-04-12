package com.blogspot.developersu.ns_usbloader.service;

import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_NSP_LIST;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_NS_DEVICE;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_NS_DEVICE_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_PHONE_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_PHONE_PORT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_SERVICE_CONTENT_PROTOCOL;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_GL_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_NET;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_UNKNOWN;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT;
import static com.blogspot.developersu.ns_usbloader.service.TransferTask.FOREGROUND_NOTIFICATION_ID;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;

public class TransferService extends Service {

    //private static final String TAG = CommunicationsService.class.getSimpleName();
    public static final String ACTION_START_TRANSFER = "com.blogspot.developersu.ns_usbloader.START_TRANSFER";
    public static final String CHANNEL_ID = "com.blogspot.developersu.ns_usbloader.CHAN_ID_FOREGROUND_SERVICE";

    private static Thread taskThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null ||
                !ACTION_START_TRANSFER.equals(intent.getAction()) ||
                isActive()) {
            return START_NOT_STICKY;
        }

        int protocol = intent.getIntExtra(NS_SERVICE_CONTENT_PROTOCOL, PROTO_UNKNOWN);
        ArrayList<NSPElement> nspElements = requireNonNull(
                intent.getParcelableArrayListExtra(NS_SERVICE_CONTENT_NSP_LIST));
        for (NSPElement nsp: nspElements)
            nsp.setStatus("");

        try {
            TransferTask task = getTask(protocol, intent, nspElements);
            taskThread = new Thread(task);
            createNotificationChannel();
            // Start foreground with notification
            Notification notification = task.buildInitialNotification();
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            taskThread.start();
        }
        catch (Exception e) {
            finish(nspElements);
        }

        return START_NOT_STICKY;
    }
    private TransferTask getTask(int protocol,
                                 Intent intent,
                                 ArrayList<NSPElement> nspElements) throws Exception {
        UsbDevice usbDevice;
        switch (protocol) {
            case PROTO_TF_USB:
                usbDevice = intent.getParcelableExtra(NS_SERVICE_CONTENT_NS_DEVICE);
                return new AwooUSB(getApplicationContext(),
                        nspElements,
                        this::finish,
                        usbDevice,
                        (UsbManager) getSystemService(Context.USB_SERVICE));
            case PROTO_GL_USB:
                usbDevice = intent.getParcelableExtra(NS_SERVICE_CONTENT_NS_DEVICE);
                return new GoldLeaf(getApplicationContext(),
                        nspElements,
                        this::finish,
                        usbDevice,
                        (UsbManager) getSystemService(Context.USB_SERVICE));
            case PROTO_TF_NET:
                return new AwooNET(getApplicationContext(),
                        nspElements,
                        this::finish,
                        intent.getStringExtra(NS_SERVICE_CONTENT_NS_DEVICE_IP),
                        intent.getStringExtra(NS_SERVICE_CONTENT_PHONE_IP),
                        intent.getIntExtra(NS_SERVICE_CONTENT_PHONE_PORT, 6042));
            default:
                throw new Exception("Incorrectly defined protocol "+protocol);
        }
    }

    // Updates main activity; stops foreground service
    public void finish(ArrayList<NSPElement> nspElements) {
        Intent execFinishIntent = new Intent(SERVICE_TRANSFER_TASK_FINISHED_INTENT);
        execFinishIntent.putExtra(NS_SERVICE_CONTENT_NSP_LIST, nspElements);
        sendBroadcast(execFinishIntent);

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        if (taskThread != null) {
            taskThread.interrupt(); // TODO: HANDLE
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Creates channel on new android
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O)
            return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_chan_name_progress),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_chan_desc_progress));

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    public static boolean isActive() {
        return taskThread != null && taskThread.isAlive();
    }
}