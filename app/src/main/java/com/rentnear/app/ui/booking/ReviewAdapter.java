package com.rentnear.app.ui.booking;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rentnear.app.databinding.ItemReviewBinding;
import com.rentnear.app.model.Review;
import com.rentnear.app.util.Tiers;

import java.util.ArrayList;
import java.util.List;

/** List of reviews: stars, who wrote it and the comment. */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {
    private final List<Review> items = new ArrayList<>();

    public void setItems(List<Review> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemReviewBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Review r = items.get(position);
        h.v.tvStars.setText(Tiers.stars(r.rating));
        String who = r.reviewerName == null || r.reviewerName.isEmpty() ? "User" : r.reviewerName;
        h.v.tvName.setText(who + ("owner".equals(r.role) ? " (owner)" : " (renter)"));
        boolean has = r.comment != null && !r.comment.isEmpty();
        h.v.tvComment.setVisibility(has ? View.VISIBLE : View.GONE);
        h.v.tvComment.setText(has ? r.comment : "");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemReviewBinding v;

        Holder(ItemReviewBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }
}
