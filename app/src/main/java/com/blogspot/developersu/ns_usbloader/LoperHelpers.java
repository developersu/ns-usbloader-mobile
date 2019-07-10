package com.blogspot.developersu.ns_usbloader;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

public class LoperHelpers {

    public static String getFileNameFromUri(Uri item, Context context){

        String result = null;

        Cursor cursor = context.getContentResolver().query(item, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getString(
                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    );
            cursor.close();
        }

        return result;
    }

    public static long getFileSizeFromUri(Uri item, Context context){
        long result = -1;
        Cursor cursor =  context.getContentResolver().query(item, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            result = cursor.getLong(
                    cursor.getColumnIndex(OpenableColumns.SIZE)
                    );
            cursor.close();
        }

        return result;
    }
}