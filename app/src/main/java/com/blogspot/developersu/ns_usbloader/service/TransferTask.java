package com.blogspot.developersu.ns_usbloader.service;

import static com.blogspot.developersu.ns_usbloader.service.TransferService.CHANNEL_ID;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;

public abstract class TransferTask implements Runnable {

    private static final String TAG = TransferTask.class.getSimpleName();

    public static final int FOREGROUND_NOTIFICATION_ID = 1;

    protected ArrayList<NSPElement> nspElements;
    protected String status;

    protected final Context context;

    private final Consumer<ArrayList<NSPElement>> serviceCallback;

    public TransferTask(Context context,
                        ArrayList<NSPElement> nspElements,
                        Consumer<ArrayList<NSPElement>> serviceCallback) {
        this.context = context;
        this.nspElements = nspElements;
        this.serviceCallback = serviceCallback;
        this.status = context.getResources().getString(R.string.status_unkown);;
    }

    @NonNull
    public Notification buildInitialNotification() {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_transfer_in_progress))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .build();
    }

    @Override
    public void run() {
        try {
            doTransfer();
            Toast.makeText(context, context.getString(R.string.transfers_service_stopped), Toast.LENGTH_SHORT)
                    .show();
        }
        catch (Exception e) {
            Log.e(TAG, requireNonNull(e.getMessage()));
            Toast.makeText(context, context.getString(R.string.transfers_service_stopped) + " " + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }
        finally {
            close();
            serviceCallback.accept(nspElements);
            //removeNotification();
        }
    }

    protected abstract void doTransfer() throws Exception;
    protected abstract void close();

    protected void updateProgressBar(long currentOffset, long count) {
        updateProgressBar((int) (currentOffset/(count/100.0)));
    }

    protected void updateProgressBar(int progress) {
        updateNotification(new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentText(progress+"%")
                //.setContentTitle(context.getString(R.string.notification_transfer_in_progress))
                //.setSmallIcon(R.drawable.ic_notification)
                //.setOngoing(true)
                .setProgress(100, progress, false) //indeterminate=false → прогресс-бар
                .build());
    }
    protected void resetProgressBar() {
        updateNotification(new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentText("")
                .setProgress(100, 0, true)
                .build());
    }

    private void updateNotification(Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(FOREGROUND_NOTIFICATION_ID, notification);
        }
        catch (SecurityException se) {
            Log.e(TAG, requireNonNull(se.getMessage()));
        }
    }
/*
    private void removeNotification() {
        NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID);
    }
 */
}
