package com.blogspot.developersu.ns_usbloader;

import static android.provider.OpenableColumns.DISPLAY_NAME;
import static android.provider.OpenableColumns.SIZE;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public class NsUtils {

    private NsUtils() {}

    public static String getFileNameFromUri(Uri item, Context context){
        String result = null;
        Cursor cursor = context.getContentResolver().query(item, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getString(cursor.getColumnIndexOrThrow(DISPLAY_NAME));
            cursor.close();
        }

        return result;
    }

    public static long getFileSizeFromUri(Uri item, Context context){
        long result = -1;
        Cursor cursor = context.getContentResolver().query(item, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getLong(cursor.getColumnIndexOrThrow(SIZE));
            cursor.close();
        }

        return result;
    }

    public static void nsSnack(View view, String text){
        Snackbar.make(view,
                text,
                Snackbar.LENGTH_LONG).show();
    }

    public static void getAlertWindow(Context context, String title, String message){
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss())
                .create()
                .show();
    }
}