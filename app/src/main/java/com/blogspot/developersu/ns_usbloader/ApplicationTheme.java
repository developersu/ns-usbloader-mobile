package com.blogspot.developersu.ns_usbloader;

import androidx.appcompat.app.AppCompatDelegate;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;

public class ApplicationTheme {
    private static final int SYSTEM_DEFAULT = 0;
    private static final int DAY_THEME = 1;
    private static final int NIGHT_THEME = 2;

    private ApplicationTheme(){}

    public static void setApplicationTheme(int itemId){
        switch (itemId){
            case SYSTEM_DEFAULT:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case DAY_THEME:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_NO);
                break;
            case NIGHT_THEME:
                AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES);
        }
    }
}
