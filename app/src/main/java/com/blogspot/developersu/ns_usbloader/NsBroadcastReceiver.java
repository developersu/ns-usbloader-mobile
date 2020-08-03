package com.blogspot.developersu.ns_usbloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class NsBroadcastReceiver extends BroadcastReceiver {

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null)
            return;
        if (intent.getAction().equals(NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT)){
            String issues = intent.getStringExtra("ISSUES");
            if (issues != null) {
                Toast.makeText(context, context.getString(R.string.transfers_service_stopped) + " " + issues, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(context, context.getString(R.string.transfers_service_stopped), Toast.LENGTH_SHORT).show();
        }
    }
    // .setContentIntent(PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class).putExtra(UsbManager.EXTRA_DEVICE, usbDevice), 0));
}