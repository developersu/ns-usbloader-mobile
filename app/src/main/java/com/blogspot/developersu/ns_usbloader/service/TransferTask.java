package com.blogspot.developersu.ns_usbloader.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.blogspot.developersu.ns_usbloader.MainActivity;
import com.blogspot.developersu.ns_usbloader.NsConstants;
import com.blogspot.developersu.ns_usbloader.R;

abstract class TransferTask {

    private final static boolean isModernAndroidOs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    private NotificationManager notificationManager;

    private NotificationCompat.Builder notificationBuilder;

    private ResultReceiver resultReceiver;
    Context context;

    String issueDescription;
    String status = "";

    volatile boolean interrupt;

    TransferTask(ResultReceiver resultReceiver, Context context){
        this.interrupt = false;
        this.resultReceiver = resultReceiver;
        this.context = context;

        this.createNotificationChannel();
        this.notificationBuilder = new NotificationCompat.Builder(context, NsConstants.NOTIFICATION_FOREGROUND_SERVICE_CHAN_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentTitle(context.getString(R.string.notification_transfer_in_progress))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0));
    }

    String getIssueDescription() {
        return issueDescription;
    }

    String getStatus() {
        return status;
    }

    void resetProgressBar(){
        resultReceiver.send(NsConstants.NS_RESULT_PROGRESS_INDETERMINATE, Bundle.EMPTY);
        resetNotificationProgressBar();
    }

    void updateProgressBar(int currentPosition){
        Bundle bundle = new Bundle();
        bundle.putInt("POSITION", currentPosition);
        resultReceiver.send(NsConstants.NS_RESULT_PROGRESS_VALUE, bundle);
        updateNotificationProgressBar(currentPosition);
    }
    /**
     * Main work routine here
     * @return true if issue, false if not
     * */
    abstract boolean run();
    /**
     * What shall we do in case of user interruption
     * */
    void cancel(){
        interrupt = true;
    }

    private void updateNotificationProgressBar(int value){
        final Notification notify = notificationBuilder.setProgress(100, value, false).setContentText(value+"%").build();
        if (isModernAndroidOs) {
            notificationManager.notify(NsConstants.NOTIFICATION_TRANSFER_ID, notify);
            return;
        }
        NotificationManagerCompat.from(context).notify(NsConstants.NOTIFICATION_TRANSFER_ID, notify);
    }

    private void resetNotificationProgressBar(){
        final Notification notify = notificationBuilder.setProgress(0, 0, true).setContentText("").build();

        if (isModernAndroidOs) {
            notificationManager.notify(NsConstants.NOTIFICATION_TRANSFER_ID, notify);
            return;
        }
        NotificationManagerCompat.from(context).notify(NsConstants.NOTIFICATION_TRANSFER_ID, notify);
    }

    private void createNotificationChannel(){
        if (isModernAndroidOs) {
            CharSequence notificationChanName = context.getString(R.string.notification_chan_name_progress);
            String notificationChanDesc = context.getString(R.string.notification_chan_desc_progress);

            NotificationChannel notificationChannel = new NotificationChannel(
                    NsConstants.NOTIFICATION_FOREGROUND_SERVICE_CHAN_ID,
                    notificationChanName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription(notificationChanDesc);
            notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
