package com.blogspot.developersu.ns_usbloader.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.blogspot.developersu.ns_usbloader.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

public class NspItemsAdapter extends RecyclerView.Adapter<NspItemsAdapter.NspViewHolder> {
    private ArrayList<NSPElement> mDataset;

    public static class NspViewHolder extends RecyclerView.ViewHolder{
        CheckedTextView nspCTV;
        TextView sizeTV;
        TextView statusTV;
        NSPElement nspElement;

        NspViewHolder(View itemTV) {
            super(itemTV);
            nspCTV = itemTV.findViewById(R.id.checkedCTV);
            sizeTV = itemTV.findViewById(R.id.sizeTV);
            statusTV = itemTV.findViewById(R.id.statusTV);
            nspCTV.setOnClickListener(e->{
                nspCTV.toggle();
                nspElement.setSelected(nspCTV.isChecked());
            });
        }

        void setData(NSPElement element){
            nspElement = element;
            nspCTV.setText(element.getFilename());
            sizeTV.setText(getCuteSize(element.getSize()));
            nspCTV.setChecked(element.isSelected());
            statusTV.setText(element.getStatus());
        }

        private String getCuteSize(long byteSize){
            if (byteSize/1024.0/1024.0/1024.0 > 1)
                return String.format(Locale.getDefault(), "%.2f", byteSize/1024.0/1024.0/1024.0)+" GB";
            else if (byteSize/1024.0/1024.0 > 1)
                return String.format(Locale.getDefault(), "%.2f", byteSize/1024.0/1024.0)+" MB";
            else
                return String.format(Locale.getDefault(), "%.2f", byteSize/1024.0)+" kB";
        }

        public NSPElement getData(){
            return nspElement;
        }
    }

    public NspItemsAdapter(ArrayList<NSPElement> mDataset){
        this.mDataset = mDataset;
    }

    public void move(int fromPosition, int toPostition){
        if (fromPosition < toPostition){
            int i = fromPosition;
            while(i < toPostition)
                Collections.swap(mDataset, i, ++i);
        }
        else {
            int i = fromPosition;
            while(i > toPostition)
                Collections.swap(mDataset, i, --i);
        }
        notifyItemMoved(fromPosition, toPostition);
    }

    @NonNull
    @Override
    public NspViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View tv = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.nsp_item, parent, false);
        return new NspViewHolder(tv);
    }

    @Override
    public void onBindViewHolder(@NonNull NspViewHolder holder, int position) {
        holder.setData(mDataset.get(position));
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}