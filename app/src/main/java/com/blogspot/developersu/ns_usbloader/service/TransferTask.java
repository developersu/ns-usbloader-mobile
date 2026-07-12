package com.blogspot.developersu.ns_usbloader.service;

import static com.blogspot.developersu.ns_usbloader.NsConstants.NS_RESULT_PROGRESS_INDETERMINATE;
import static com.blogspot.developersu.ns_usbloader.service.TransferService.CHANNEL_ID;
import static java.util.Objects.requireNonNull;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.blogspot.developersu.ns_usbloader.MainActivity;
import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.service.utility.Consumer;
import com.blogspot.developersu.ns_usbloader.service.utility.ServiceResultingDataSet;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;

public abstract class TransferTask implements Runnable {

    private static final String TAG = TransferTask.class.getSimpleName();
    public static final int FOREGROUND_NOTIFICATION_ID = 1;

    protected ArrayList<NSPElement> nspElements;
    protected String status;

    protected final Context context;

    private final Consumer<Integer> progressCallback;
    private final Consumer<ServiceResultingDataSet> finishCallback;

    public TransferTask(Context context,
                        ArrayList<NSPElement> nspElements,
                        Consumer<Integer> progressCallback,
                        Consumer<ServiceResultingDataSet> finishCallback) {
        this.context = context;
        this.nspElements = nspElements;
        this.progressCallback = progressCallback;
        this.finishCallback = finishCallback;
        this.status = context.getResources().getString(R.string.status_unkown);

    }

    @NonNull
    public Notification getNotification() {
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notification_transfer_in_progress))
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, 0, true)
                .setContentIntent(getPendingIntent()) // invoke activity appearance on click
                .build();
    }
    /**
     * Get MainActivity when user clicks on notification
     * */
    private PendingIntent getPendingIntent() {
        Intent innerIntent = new Intent(context, MainActivity.class)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setAction(Intent.ACTION_MAIN)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(context, 0, innerIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void run() {
        String finalToastMessage = context.getString(R.string.transfers_service_stopped);
        boolean toastLengthShort = true;
        try {
            doTransfer();
        }
        catch (Exception e) {
            Log.e(TAG, requireNonNull(e.getMessage()));
            finalToastMessage = context.getString(R.string.transfers_service_stopped) + " " + e.getMessage();
            toastLengthShort = false;
        }
        finally {
            close();
            finishCallback.accept(new ServiceResultingDataSet(nspElements, finalToastMessage, toastLengthShort));
            //removeNotification();
        }
    }

    protected abstract void doTransfer() throws Exception;
    protected abstract void close();

    protected void updateProgressBar(long currentOffset, long count) {
        updateProgressBar((int) (currentOffset/(count/100.0)));
    }

    protected void updateProgressBar(int progress) {
        progressCallback.accept(progress);
    }
    protected void resetProgressBar() {
        progressCallback.accept(NS_RESULT_PROGRESS_INDETERMINATE);
    }
/*
    private void removeNotification() {
        NotificationManagerCompat.from(context).cancel(FOREGROUND_NOTIFICATION_ID);
    }
 */
}
