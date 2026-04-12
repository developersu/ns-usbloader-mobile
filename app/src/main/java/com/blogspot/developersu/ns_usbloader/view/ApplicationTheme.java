package com.blogspot.developersu.ns_usbloader.view;

import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
import static androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES;
import static androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode;

public class ApplicationTheme {
    private static final int SYSTEM_DEFAULT = 0;
    private static final int DAY_THEME = 1;
    private static final int NIGHT_THEME = 2;

    private ApplicationTheme() {}

    public static void setApplicationTheme(int itemId) {
        switch (itemId) {
            case SYSTEM_DEFAULT:
                setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            case DAY_THEME:
                setDefaultNightMode(MODE_NIGHT_NO);
                break;
            case NIGHT_THEME:
                setDefaultNightMode(MODE_NIGHT_YES);
        }
    }
}