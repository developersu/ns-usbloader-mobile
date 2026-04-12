package com.blogspot.developersu.ns_usbloader.view;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.blogspot.developersu.ns_usbloader.R;

import java.util.ArrayList;
import java.util.Collections;

public class NspItemsAdapter extends RecyclerView.Adapter<NspViewHolder> {

    private final ArrayList<NSPElement> elements;

    public NspItemsAdapter(ArrayList<NSPElement> elements) {
        this.elements = elements;
    }

    public void move(int fromPosition, int toPosition) {
        int position = fromPosition;
        if (fromPosition < toPosition){
            while(position < toPosition)
                Collections.swap(elements, position, ++position);
        }
        else {
            while(position > toPosition)
                Collections.swap(elements, position, --position);
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public NspViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new NspViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.nsp_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull NspViewHolder holder, int position) {
        holder.setData(elements.get(position));
    }

    @Override
    public int getItemCount() {
        return elements.size();
    }
}