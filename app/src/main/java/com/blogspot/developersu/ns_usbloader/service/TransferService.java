package com.blogspot.developersu.ns_usbloader.service;

import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_CONTENT_LIST;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_FINAL_TOAST_DURATION;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_FINAL_TOAST_TEXT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_NS_DEVICE;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_NS_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_PHONE_IP;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_PHONE_PORT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NSS_PROTOCOL;
import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_PROGRESS;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_GL_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_NET;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_TF_USB;
import static com.blogspot.developersu.ns_usbloader.NsConstants.PROTO_UNKNOWN;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT;
import static com.blogspot.developersu.ns_usbloader.NsConstants.SERVICE_TRANSFER_TASK_PROGRESS_INTENT;
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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.service.gl.GoldLeaf_010;
import com.blogspot.developersu.ns_usbloader.service.utility.ServiceResultingDataSet;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;

public class TransferService extends Service {

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

        int protocol = intent.getIntExtra(NSS_PROTOCOL, PROTO_UNKNOWN);
        ArrayList<NSPElement> nspElements = requireNonNull(
                intent.getParcelableArrayListExtra(NSS_CONTENT_LIST));
        for (NSPElement nsp: nspElements)
            nsp.setStatus("");

        try {
            TransferTask task = getTask(protocol, intent, nspElements);
            taskThread = new Thread(task);
            createNotificationChannel();
            // Start foreground with notification
            Notification notification = task.getNotification();
            startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            taskThread.start();
        }
        catch (Exception e) {
            finish(new ServiceResultingDataSet(nspElements, e.getMessage(), false));
        }

        return START_NOT_STICKY;
    }
    private TransferTask getTask(int protocol,
                                 Intent intent,
                                 ArrayList<NSPElement> nspElements) throws Exception {
        UsbDevice usbDevice;
        switch (protocol) {
            case PROTO_TF_USB:
                usbDevice = intent.getParcelableExtra(NSS_NS_DEVICE);
                return new AwooUSB(getApplicationContext(),
                        nspElements,
                        this::progressUpdate,
                        this::finish,
                        usbDevice,
                        (UsbManager) getSystemService(Context.USB_SERVICE));
            case PROTO_GL_USB:
                usbDevice = intent.getParcelableExtra(NSS_NS_DEVICE);
                return new GoldLeaf_010(getApplicationContext(),
                        nspElements,
                        this::progressUpdate,
                        this::finish,
                        usbDevice,
                        (UsbManager) getSystemService(Context.USB_SERVICE));
            case PROTO_TF_NET:
                return new AwooNET(getApplicationContext(),
                        nspElements,
                        this::progressUpdate,
                        this::finish,
                        intent.getStringExtra(NSS_NS_IP),
                        intent.getStringExtra(NSS_PHONE_IP),
                        intent.getIntExtra(NSS_PHONE_PORT, 6042));
            default:
                throw new Exception("Incorrectly defined protocol "+protocol);
        }
    }

    public void progressUpdate(int progress) {
        Context context = getApplicationContext();
        boolean isIndeterminate = progress < 0;
        sendBroadcast(new Intent(SERVICE_TRANSFER_TASK_PROGRESS_INTENT)
                .putExtra(NS_PROGRESS, progress));

        NotificationManagerCompat.from(context)
                .notify(FOREGROUND_NOTIFICATION_ID, (
                        new NotificationCompat.Builder(context, CHANNEL_ID)
                                .setContentText(isIndeterminate?"":progress+"%")
                                .setContentTitle(context.getString(R.string.notification_transfer_in_progress))
                                .setSmallIcon(R.drawable.ic_notification)
                                .setOngoing(true)
                                .setProgress(100, progress, isIndeterminate)
                                .build()));
    }

    // Updates main activity; stops foreground service
    public void finish(ServiceResultingDataSet set) {
        sendBroadcast(new Intent(SERVICE_TRANSFER_TASK_FINISHED_INTENT)
                .putExtra(NSS_CONTENT_LIST, set.getNspElements())
                .putExtra(NSS_FINAL_TOAST_TEXT, set.getFinalToastMessage())
                .putExtra(NSS_FINAL_TOAST_DURATION, set.getToastDuration()));
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