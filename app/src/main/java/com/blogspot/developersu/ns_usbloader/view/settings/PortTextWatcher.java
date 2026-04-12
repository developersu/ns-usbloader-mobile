package com.blogspot.developersu.ns_usbloader.view.settings;

import static java.lang.Integer.parseInt;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.blogspot.developersu.ns_usbloader.R;

public class PortTextWatcher implements TextWatcher {

    private final static String PORT_FILTER_REGEXP = "^\\d{1,5}";

    private final Context context;
    private final EditText port;

    public PortTextWatcher(Context context, EditText port) {
        this.port = port;
        this.context = context;
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) { }

    @Override
    public void afterTextChanged(Editable editable) {
        String contentString = editable.toString();
        if (contentString.matches(PORT_FILTER_REGEXP)) {
            if (parseInt(contentString) < 1024)
                port.setTextColor(Color.RED);
            else
                port.setTextColor(context.getResources().getColor(R.color.defaultTextColor));
        }
    }
}