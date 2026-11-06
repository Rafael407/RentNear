package com.rentnear.app.ui.booking;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentBookingRequestBinding;
import com.rentnear.app.model.Item;
import com.rentnear.app.util.DateFmt;
import com.rentnear.app.util.Format;
import com.rentnear.app.util.Pricing;
import com.rentnear.app.util.Result;
import com.rentnear.app.viewmodel.BookingRequestViewModel;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.LongConsumer;

/** Pick start and end, see the price, send the request. Opened from the item page or an agreed offer. */
public class BookingRequestFragment extends Fragment {
    private FragmentBookingRequestBinding b;
    private BookingRequestViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentBookingRequestBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(BookingRequestViewModel.class);

        Bundle args = getArguments();
        String itemId = args == null ? null : args.getString("itemId");
        double rate = args == null ? 0 : args.getDouble("rate", 0);
        if (itemId != null) vm.init(itemId, rate);

        vm.item().observe(getViewLifecycleOwner(), r -> {
            if (r == null || b == null) return;
            if (!r.isOk()) {
                Toast.makeText(requireContext(), r.error, Toast.LENGTH_LONG).show();
                return;
            }
            vm.onItemLoaded(r.data);
            b.tvTitle.setText(r.data.title);
            Glide.with(b.ivPhoto).load(r.data.firstPhoto()).centerCrop().into(b.ivPhoto);
            refresh();
        });
        vm.profile().observe(getViewLifecycleOwner(), r -> refresh());
        vm.start().observe(getViewLifecycleOwner(), s -> refresh());
        vm.end().observe(getViewLifecycleOwner(), e -> refresh());
        vm.busy().observe(getViewLifecycleOwner(), busy -> refresh());

        b.btnStart.setOnClickListener(v -> {
            Long s = vm.start().getValue();
            pickDateTime(s == null ? System.currentTimeMillis() : s, vm::setStart);
        });
        b.btnEnd.setOnClickListener(v -> {
            Long e = vm.end().getValue();
            pickDateTime(e == null ? System.currentTimeMillis() : e, vm::setEnd);
        });
        b.btnSubmit.setOnClickListener(v -> {
            CharSequence n = b.etNote.getText();
            vm.submit(n == null ? "" : n.toString().trim());
        });

        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e == null) return;
            Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            vm.clearError();
        });
        vm.created().observe(getViewLifecycleOwner(), r -> {
            if (r == null) return;
            vm.consumeResult();
            if (!r.isOk()) {
                Toast.makeText(requireContext(), r.error, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(requireContext(), R.string.request_sent, Toast.LENGTH_SHORT).show();
            Bundle a = new Bundle();
            a.putString("bookingId", r.data);
            NavHostFragment.findNavController(this).navigate(R.id.bookingDetailFragment, a,
                    new NavOptions.Builder().setPopUpTo(R.id.bookingRequestFragment, true).build());
        });
        return b.getRoot();
    }

    private void refresh() {
        if (b == null) return;
        Result<Item> ir = vm.item().getValue();
        Long s = vm.start().getValue();
        Long e = vm.end().getValue();
        boolean loaded = ir != null && ir.isOk() && s != null && e != null;
        boolean busy = Boolean.TRUE.equals(vm.busy().getValue());

        b.progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        if (!loaded) {
            b.btnSubmit.setEnabled(false);
            return;
        }
        Item i = ir.data;
        b.tvRate.setText(Format.money(vm.rate()) + " / " + i.priceUnit);
        b.btnStart.setText(DateFmt.one(requireContext(), s));
        b.btnEnd.setText(DateFmt.one(requireContext(), e));

        int units = Pricing.units(s, e, i.priceUnit);
        double total = Pricing.total(vm.rate(), units);
        b.tvDuration.setText(getString(R.string.duration_line, units,
                i.priceUnit + (units > 1 ? "s" : ""), Format.money(vm.rate())));
        b.tvTotal.setText(getString(R.string.total_line, Format.money(total)));

        boolean trustBlocked = vm.needsTrust();
        b.tvTrustNotice.setVisibility(trustBlocked && vm.profile().getValue() != null
                ? View.VISIBLE : View.GONE);
        b.btnSubmit.setEnabled(!busy && !trustBlocked);
    }

    /** Date picker, then time picker; returns local epoch millis. */
    private void pickDateTime(long initialMs, LongConsumer cb) {
        Calendar init = Calendar.getInstance();
        init.setTimeInMillis(initialMs);

        // MaterialDatePicker works in UTC midnight; convert the local date to that.
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        utc.clear();
        utc.set(init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));

        MaterialDatePicker<Long> dp = MaterialDatePicker.Builder.datePicker()
                .setSelection(utc.getTimeInMillis())
                .build();
        dp.addOnPositiveButtonClickListener(selection -> {
            Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            picked.setTimeInMillis(selection);
            int y = picked.get(Calendar.YEAR);
            int m = picked.get(Calendar.MONTH);
            int d = picked.get(Calendar.DAY_OF_MONTH);

            MaterialTimePicker tp = new MaterialTimePicker.Builder()
                    .setTimeFormat(DateFormat.is24HourFormat(requireContext())
                            ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(init.get(Calendar.HOUR_OF_DAY))
                    .setMinute(init.get(Calendar.MINUTE))
                    .build();
            tp.addOnPositiveButtonClickListener(v2 -> {
                Calendar c = Calendar.getInstance();
                c.clear();
                c.set(y, m, d, tp.getHour(), tp.getMinute(), 0);
                cb.accept(c.getTimeInMillis());
            });
            tp.show(getChildFragmentManager(), "time");
        });
        dp.show(getChildFragmentManager(), "date");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
