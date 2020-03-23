package com.blogspot.developersu.ns_usbloader.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.ResultReceiver;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.blogspot.developersu.ns_usbloader.MainActivity;
import com.blogspot.developersu.ns_usbloader.NsConstants;
import com.blogspot.developersu.ns_usbloader.R;
import com.blogspot.developersu.ns_usbloader.view.NSPElement;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class CommunicationsService extends IntentService {
    private static final String SERVICE_TAG = "com.blogspot.developersu.ns_usbloader.Service.CommunicationsService";
    private static final int PROTOCOL_UNKNOWN = -1;

    private static AtomicBoolean isActive = new AtomicBoolean(false);

    private String issueDescription;
    private TransferTask transferTask;

    private ArrayList<NSPElement> nspElements;
    private String status = "";

    UsbDevice usbDevice;

    String nsIp;
    String phoneIp;
    int phonePort;

    public CommunicationsService() {super(SERVICE_TAG);}
    public static boolean isServiceActive(){
        return isActive.get();
    }

    protected void onHandleIntent(Intent intent) {
        isActive.set(true);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getBaseContext(), NsConstants.NOTIFICATION_FOREGROUND_SERVICE_CHAN_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentTitle(getString(R.string.notification_transfer_in_progress))
                .setProgress(0, 0, true)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0));

        startForeground(NsConstants.NOTIFICATION_TRANSFER_ID, notificationBuilder.build());


        ResultReceiver resultReceiver = intent.getParcelableExtra(NsConstants.NS_RESULT_RECEIVER);
        nspElements = intent.getParcelableArrayListExtra(NsConstants.SERVICE_CONTENT_NSP_LIST);
        final int protocol = intent.getIntExtra(NsConstants.SERVICE_CONTENT_PROTOCOL, PROTOCOL_UNKNOWN);

        // Clear statuses
        for (NSPElement e: nspElements)
            e.setStatus("");

        try {
            switch (protocol){
                case NsConstants.PROTO_TF_USB:
                    getDataForUsbTransfer(intent);
                    transferTask = new TinfoilUSB(resultReceiver, getApplicationContext(), usbDevice, (UsbManager) getSystemService(Context.USB_SERVICE), nspElements);
                    break;
                case NsConstants.PROTO_GL_USB:
                    getDataForUsbTransfer(intent);
                    transferTask = new GoldLeaf(resultReceiver, getApplicationContext(), usbDevice, (UsbManager) getSystemService(Context.USB_SERVICE), nspElements);
                    break;
                case NsConstants.PROTO_TF_NET:
                    getDataForNetTransfer(intent);
                    transferTask = new TinfoilNET(resultReceiver, getApplicationContext(), nspElements, nsIp, phoneIp, phonePort);
                    break;
                default:
                    finish();
            }
        }
        catch (Exception e){
            this.issueDescription = e.getMessage();
            status = getString(R.string.status_failed_to_upload);
            finish();
            return;
        }

        transferTask.run();
        this.issueDescription = transferTask.getIssueDescription();
        status = transferTask.getStatus();
        /*
        Log.i("LPR", "Status " +status);
        Log.i("LPR", "issue " +issueDescription);
        Log.i("LPR", "Interrupt " +transferTask.interrupt.get());
        Log.i("LPR", "Active " +isActive.get());
        */
        finish();
        stopForeground(true);
        // Now we have to hide what has to be hidden. This part of code MUST be here right after stopForeground():
        this.hideNotification(getApplicationContext());
    }

    private void getDataForUsbTransfer(Intent intent){
        this.usbDevice = intent.getParcelableExtra(NsConstants.SERVICE_CONTENT_NS_DEVICE);
    }

    private void getDataForNetTransfer(Intent intent){
        this.nsIp = intent.getStringExtra(NsConstants.SERVICE_CONTENT_NS_DEVICE_IP);
        this.phoneIp = intent.getStringExtra(NsConstants.SERVICE_CONTENT_PHONE_IP);
        this.phonePort = intent.getIntExtra(NsConstants.SERVICE_CONTENT_PHONE_PORT, 6042);
    }

    private void finish(){
        // Set status if not already set
        for (NSPElement e: nspElements)
            if (e.getStatus().isEmpty())
                e.setStatus(status);
        isActive.set(false);
        Intent executionFinishIntent = new Intent(NsConstants.SERVICE_TRANSFER_TASK_FINISHED_INTENT);
        executionFinishIntent.putExtra(NsConstants.SERVICE_CONTENT_NSP_LIST, nspElements);
        if (issueDescription != null) {
            executionFinishIntent.putExtra("ISSUES", issueDescription);
        }
        this.sendBroadcast(executionFinishIntent);
    }

    void hideNotification(Context context){
        NotificationManagerCompat.from(context).cancel(NsConstants.NOTIFICATION_TRANSFER_ID);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (transferTask != null)
            transferTask.cancel();
    }
}