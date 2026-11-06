package com.rentnear.app.ui.profile;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rentnear.app.databinding.ItemLeaderBinding;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.util.Tiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ranked list of users. My own row is shown in bold. */
public class LeaderAdapter extends RecyclerView.Adapter<LeaderAdapter.Holder> {
    public interface OnClick {
        void onClick(UserProfile user);
    }

    private final List<UserProfile> items = new ArrayList<>();
    private final String me;
    private final OnClick onClick;
    private boolean byPoints = true;

    public LeaderAdapter(String me, OnClick onClick) {
        this.me = me == null ? "" : me;
        this.onClick = onClick;
    }

    public void setItems(List<UserProfile> list, boolean byPoints) {
        this.byPoints = byPoints;
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemLeaderBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        UserProfile u = items.get(position);
        boolean mine = me.equals(u.uid);
        h.v.tvRank.setText("#" + (position + 1));
        h.v.tvName.setText((u.name == null || u.name.isEmpty() ? "User" : u.name) + (mine ? " (you)" : ""));
        h.v.tvName.setTypeface(null, mine ? Typeface.BOLD : Typeface.NORMAL);
        h.v.tvSub.setText(Tiers.forPoints(u.points) + (u.trusted ? "  ·  Trusted" : ""));
        h.v.tvValue.setText(byPoints ? u.points + " pts"
                : String.format(Locale.US, "%.0f", u.trustScore));
        h.itemView.setOnClickListener(v -> onClick.onClick(u));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemLeaderBinding v;

        Holder(ItemLeaderBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }
}
