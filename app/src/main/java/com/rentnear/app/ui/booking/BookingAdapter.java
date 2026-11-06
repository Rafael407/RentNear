package com.rentnear.app.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.rentnear.app.R;
import com.rentnear.app.databinding.ItemBookingBinding;
import com.rentnear.app.model.Booking;
import com.rentnear.app.util.DateFmt;
import com.rentnear.app.util.Format;

import java.util.Objects;

/** RecyclerView adapter for the Rentals tab. */
public class BookingAdapter extends ListAdapter<Booking, BookingAdapter.Holder> {
    public interface OnClick {
        void onClick(Booking booking);
    }

    private final OnClick onClick;

    public BookingAdapter(OnClick onClick) {
        super(new DiffUtil.ItemCallback<Booking>() {
            @Override
            public boolean areItemsTheSame(@NonNull Booking x, @NonNull Booking y) {
                return x.id.equals(y.id);
            }

            @Override
            public boolean areContentsTheSame(@NonNull Booking x, @NonNull Booking y) {
                return Objects.equals(x.status, y.status) && x.startMs == y.startMs
                        && x.endMs == y.endMs && x.total == y.total;
            }
        });
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemBookingBinding.inflate(LayoutInflater.from(parent.getContext()),
                parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Booking bk = getItem(position);
        Context c = h.b.getRoot().getContext();
        String me = FirebaseAuth.getInstance().getUid();
        if (me == null) me = "";

        h.b.tvTitle.setText(bk.itemTitle);
        h.b.tvWho.setText(c.getString(R.string.booking_with, bk.otherName(me)));
        h.b.tvDates.setText(DateFmt.range(c, bk.startMs, bk.endMs));
        h.b.tvTotal.setText(Format.money(bk.total));
        h.b.tvStatus.setText(bk.statusLabel());
        h.b.tvStatus.setTextColor(bk.statusColor());
        Glide.with(h.b.ivPhoto).load(bk.itemPhoto).centerCrop().into(h.b.ivPhoto);
        h.b.getRoot().setOnClickListener(v -> onClick.onClick(bk));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemBookingBinding b;

        Holder(ItemBookingBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
