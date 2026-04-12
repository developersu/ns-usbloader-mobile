package com.blogspot.developersu.ns_usbloader.view.settings;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.blogspot.developersu.ns_usbloader.R;

public class IpContainingTextWatcher implements TextWatcher {
    private final Context context;
    private final EditText ipEditText;

    public IpContainingTextWatcher(Context context, EditText ipEditText) {
        this.ipEditText = ipEditText;
        this.context = context;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

    @Override
    public void afterTextChanged(Editable editable) {
        if (! editable.toString().matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"))
            ipEditText.setTextColor(Color.RED);
        else
            ipEditText.setTextColor(context.getResources().getColor(R.color.defaultTextColor));
    }
}