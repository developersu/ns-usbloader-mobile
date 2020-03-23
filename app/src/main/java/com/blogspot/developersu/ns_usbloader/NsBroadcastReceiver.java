package com.blogspot.developersu.ns_usbloader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NsBroadcastReceiver extends BroadcastReceiver {

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null)
            return;
        switch (intent.getAction()) {
            case UsbManager.ACTION_USB_DEVICE_ATTACHED: {
                showNotification(context, intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                break;
            }
            case UsbManager.ACTION_USB_DEVICE_DETACHED: {
                hideNotification(context);
                break;
            }
            case NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT:
                String issues = intent.getStringExtra("ISSUES");
                if (issues != null) {
                    Toast.makeText(context, context.getString(R.string.transfers_service_stopped) + " " + issues, Toast.LENGTH_LONG).show();
                    break;
                }
                Toast.makeText(context, context.getString(R.string.transfers_service_stopped), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void showNotification(Context context, UsbDevice usbDevice){
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, NsConstants.NOTIFICATION_NS_CONNECTED_CHAN_ID);
        notification.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.ns_connected_info))
                //.setAutoCancel(true)
                .setOngoing(true)       // Prevent swipe-notification-to-remove
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class).putExtra(UsbManager.EXTRA_DEVICE, usbDevice), 0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence notificationChanName = context.getString(R.string.notification_chan_name_usb);
            String notificationChanDesc = context.getString(R.string.notification_chan_desc_usb);

            NotificationChannel channel = new NotificationChannel(
                    NsConstants.NOTIFICATION_NS_CONNECTED_CHAN_ID,
                    notificationChanName,
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(notificationChanDesc);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            notificationManager.notify(NsConstants.NOTIFICATION_NS_CONNECTED_ID, notification.build());
        }
        else {
            NotificationManagerCompat.from(context).notify(NsConstants.NOTIFICATION_NS_CONNECTED_ID, notification.build());
        }
    }

    private void hideNotification(Context context){
        NotificationManagerCompat.from(context).cancel(NsConstants.NOTIFICATION_NS_CONNECTED_ID);
    }
}