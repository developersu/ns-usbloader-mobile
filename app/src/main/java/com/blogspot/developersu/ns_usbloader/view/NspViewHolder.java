package com.blogspot.developersu.ns_usbloader.view;

import android.view.View;
import android.widget.CheckedTextView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.blogspot.developersu.ns_usbloader.R;

import java.util.Locale;

public class NspViewHolder extends RecyclerView.ViewHolder {
    private final CheckedTextView nspCTV;
    private final TextView sizeTV;
    private final TextView statusTV;
    private NSPElement element;

    NspViewHolder(View itemTV) {
        super(itemTV);
        nspCTV = itemTV.findViewById(R.id.checkedCTV);
        sizeTV = itemTV.findViewById(R.id.sizeTV);
        statusTV = itemTV.findViewById(R.id.statusTV);
        nspCTV.setOnClickListener(e->{
            nspCTV.toggle();
            element.setSelected(nspCTV.isChecked());
        });
    }

    void setData(NSPElement element) {
        this.element = element;
        nspCTV.setText(element.getFilename());
        sizeTV.setText(getCuteSize(element.getSize()));
        nspCTV.setChecked(element.isSelected());
        statusTV.setText(element.getStatus());
    }

    private String getCuteSize(long byteSize) {
        final Locale locale = Locale.getDefault();
        double size;

        if ((size = byteSize/1024.0/1024.0/1024.0) > 1)
            return String.format(locale, "%.2f GB", size);
        else if ((size = byteSize/1024.0/1024.0) > 1)
            return String.format(locale, "%.2f MB", size);
        else
            return String.format(locale, "%.2f kB", byteSize/1024.0);
    }

    public NSPElement getData(){
        return element;
    }
}