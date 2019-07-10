package com.blogspot.developersu.ns_usbloader;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class NsNotificationPopUp {
    public static void getAlertWindow(Context context, String titile, String message){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titile)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                });
        builder.create().show();
    }
}
