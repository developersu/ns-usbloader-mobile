package com.blogspot.developersu.ns_usbloader.view.settings;

import android.view.View;
import android.widget.AdapterView;

import com.blogspot.developersu.ns_usbloader.view.ApplicationTheme;

public class OnThemeChangedListener implements AdapterView.OnItemSelectedListener {

    @Override
    public void onItemSelected(AdapterView<?> adapterView,
                               View view,
                               int selectedItemPosition,
                               long selectedItemId) {
        ApplicationTheme.setApplicationTheme(selectedItemPosition);
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {}
}