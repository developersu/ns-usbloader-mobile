package com.blogspot.developersu.ns_usbloader.view.settings;

import android.text.InputFilter;
import android.text.Spanned;

public class IpInputFilter implements InputFilter {

    private final static String IP_FILTER_REGEXP =
            "^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?";

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
            if (! resultingTxt.matches(IP_FILTER_REGEXP))
                return "";
            else {
                String[] splits = resultingTxt.split("\\.");
                for (String split : splits) {
                    if (Integer.parseInt(split) > 255)
                        return "";
                }
            }
        }
        return null;
    }
}