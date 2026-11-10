package com.rentnear.app.ui.claim;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.rentnear.app.databinding.ItemClaimBinding;
import com.rentnear.app.model.Claim;
import com.rentnear.app.util.Format;

import java.util.ArrayList;
import java.util.List;

/** Rows for the Claims screen. */
public class ClaimAdapter extends RecyclerView.Adapter<ClaimAdapter.Holder> {
    public interface OnClick {
        void onClick(Claim claim);
    }

    private final List<Claim> items = new ArrayList<>();
    private final String me;
    private final OnClick onClick;

    public ClaimAdapter(String me, OnClick onClick) {
        this.me = me == null ? "" : me;
        this.onClick = onClick;
    }

    public void setItems(List<Claim> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemClaimBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Claim c = items.get(position);
        boolean owner = me.equals(c.ownerId);
        h.v.tvTitle.setText(c.itemTitle);
        h.v.tvSub.setText((owner ? "Against " : "From ") + c.otherName(me) + "  ·  "
                + Format.money(c.amount));
        h.v.tvStatus.setText(c.statusLabel());
        h.itemView.setOnClickListener(v -> onClick.onClick(c));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemClaimBinding v;

        Holder(ItemClaimBinding v) {
            super(v.getRoot());
            this.v = v;
        }
    }
}
