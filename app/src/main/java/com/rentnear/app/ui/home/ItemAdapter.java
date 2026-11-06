package com.rentnear.app.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.rentnear.app.R;
import com.rentnear.app.databinding.ItemListingBinding;
import com.rentnear.app.model.Item;
import com.rentnear.app.util.Format;

import java.util.Objects;

/** RecyclerView adapter for the Browse feed. */
public class ItemAdapter extends ListAdapter<Item, ItemAdapter.Holder> {
    public interface OnClick {
        void onClick(Item item);
    }

    private final OnClick onClick;

    public ItemAdapter(OnClick onClick) {
        super(new DiffUtil.ItemCallback<Item>() {
            @Override
            public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
                return a.id.equals(b.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
                return Objects.equals(a.title, b.title) && a.price == b.price
                        && a.distanceM == b.distanceM && a.ratingAvg == b.ratingAvg;
            }
        });
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemListingBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Item i = getItem(position);
        h.b.tvTitle.setText(i.title);
        h.b.tvPrice.setText(Format.price(i));
        String rating = i.ratingCount == 0 ? "New" : String.format(java.util.Locale.US, "%.1f★", i.ratingAvg);
        h.b.tvMeta.setText(h.b.getRoot().getContext().getString(R.string.meta_line,
                Format.distance(i.distanceM), i.category == null ? "" : i.category, rating));
        Glide.with(h.b.ivPhoto).load(i.firstPhoto()).centerCrop().into(h.b.ivPhoto);
        h.b.getRoot().setOnClickListener(v -> onClick.onClick(i));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemListingBinding b;

        Holder(ItemListingBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
