package com.rentnear.app.ui.common;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.rentnear.app.databinding.ItemPhotoBinding;

import java.util.ArrayList;
import java.util.List;

/** Horizontal strip of photos from URLs. */
public class PhotoStripAdapter extends RecyclerView.Adapter<PhotoStripAdapter.Holder> {
    private final List<String> urls = new ArrayList<>();

    public void setUrls(List<String> newUrls) {
        urls.clear();
        urls.addAll(newUrls);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemPhotoBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Glide.with(h.b.ivPhoto).load(urls.get(position)).centerCrop().into(h.b.ivPhoto);
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemPhotoBinding b;

        Holder(ItemPhotoBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
