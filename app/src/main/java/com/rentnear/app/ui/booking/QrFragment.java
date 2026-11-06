package com.rentnear.app.ui.booking;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentQrBinding;
import com.rentnear.app.model.Booking;
import com.rentnear.app.util.QrCode;
import com.rentnear.app.viewmodel.QrViewModel;

/**
 * Owner shows this screen and the renter scans it. It closes by itself once the other person's
 * scan has moved the booking on (handover recorded, or item returned).
 */
public class QrFragment extends Fragment {
    private FragmentQrBinding b;
    private QrViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentQrBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(QrViewModel.class);

        Bundle args = getArguments();
        String bookingId = args == null ? null : args.getString("bookingId");
        String kind = args == null ? "handover" : args.getString("kind", "handover");
        if (bookingId != null) vm.init(bookingId, kind);

        boolean handover = "handover".equals(kind);
        b.tvQrTitle.setText(handover ? R.string.qr_title_handover : R.string.qr_title_return);
        b.tvQrHelp.setText(handover ? R.string.qr_help_handover : R.string.qr_help_return);
        b.btnClose.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        vm.payload().observe(getViewLifecycleOwner(), text -> {
            if (text == null || b == null) return;
            try {
                Bitmap bmp = QrCode.generate(text, 800);
                b.ivQr.setImageBitmap(bmp);
                b.progress.setVisibility(View.GONE);
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.qr_failed, Toast.LENGTH_LONG).show();
            }
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e == null) return;
            Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            vm.clearError();
            if (b != null) b.progress.setVisibility(View.GONE);
        });
        vm.booking().observe(getViewLifecycleOwner(), bk -> {
            if (bk == null || b == null) return;
            boolean moved = handover ? !Booking.PAID.equals(bk.status)
                    : Booking.RETURNED.equals(bk.status) || Booking.COMPLETED.equals(bk.status);
            if (moved) {
                Toast.makeText(requireContext(),
                        handover ? R.string.qr_done_handover : R.string.qr_done_return,
                        Toast.LENGTH_LONG).show();
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
        return b.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        vm.start();
    }

    @Override
    public void onStop() {
        vm.stop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
