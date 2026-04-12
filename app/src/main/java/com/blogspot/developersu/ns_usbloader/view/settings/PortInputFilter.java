package com.blogspot.developersu.ns_usbloader.view.settings;

import android.text.InputFilter;
import android.text.Spanned;

public class PortInputFilter implements InputFilter {

    @Override
    public CharSequence filter(CharSequence charSequence,
                               int start,
                               int end,
                               Spanned destination,
                               int dStart,
                               int dEnd) {
        if (end > start) {
            String destTxt = destination.toString();
            String resultingTxt = destTxt.substring(0, dStart) +
                    charSequence.subSequence(start, end) +
                    destTxt.substring(dEnd);
            if (! resultingTxt.matches ("^[0-9]+"))
                return "";
            if (Integer.parseInt(resultingTxt) > 65535)
                return "";
        }
        return null;
    }
}