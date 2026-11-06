package com.rentnear.app.ui.booking;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentBookingDetailBinding;
import com.rentnear.app.model.Booking;
import com.rentnear.app.ui.common.PhotoStripAdapter;
import com.rentnear.app.util.DateFmt;
import com.rentnear.app.util.Format;
import com.rentnear.app.util.Pricing;
import com.rentnear.app.viewmodel.BookingDetailViewModel;
import com.rentnear.app.viewmodel.BookingDetailViewModel.Pending;
import com.rentnear.app.workers.ReminderScheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One booking: details and the actions that fit the viewer's role and the booking's status, from the
 * request through payment, QR handover, return, and payout.
 */
public class BookingDetailFragment extends Fragment {
    private static final String[] PHOTO_KEYS = {"beforeOwner", "beforeRenter", "afterRenter", "afterOwner"};
    private static final long RENTER_CLOSE_AFTER_MS = 24L * 3600 * 1000;

    private FragmentBookingDetailBinding b;
    private BookingDetailViewModel vm;
    private PhotoStripAdapter gallery;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        vm = new ViewModelProvider(this).get(BookingDetailViewModel.class);

        // Results from the camera screen (QR scans and condition photos).
        FragmentManager fm = getParentFragmentManager();
        fm.setFragmentResultListener("scan_handover", this, (k, r) ->
                vm.onScanned("handover", r.getString(CameraFragment.RESULT_TEXT)));
        fm.setFragmentResultListener("scan_return", this, (k, r) ->
                vm.onScanned("return", r.getString(CameraFragment.RESULT_TEXT)));
        for (String key : PHOTO_KEYS) {
            fm.setFragmentResultListener("photos_" + key, this, (k, r) ->
                    vm.onPhotos(requireContext(), key, r.getStringArrayList(CameraFragment.RESULT_URIS)));
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentBookingDetailBinding.inflate(inflater, container, false);
        String id = getArguments() == null ? null : getArguments().getString("bookingId");
        if (id != null) vm.init(id);

        gallery = new PhotoStripAdapter();
        b.rvPhotos.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        b.rvPhotos.setAdapter(gallery);

        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        vm.booking().observe(getViewLifecycleOwner(), this::show);
        vm.pending().observe(getViewLifecycleOwner(), p -> show(vm.booking().getValue()));
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            boolean on = Boolean.TRUE.equals(busy);
            b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            if (on) {
                b.btnPrimary.setEnabled(false);
                b.btnSecondary.setEnabled(false);
                b.btnStep1.setEnabled(false);
                b.btnStep2.setEnabled(false);
            } else {
                show(vm.booking().getValue());
            }
        });
        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e == null) return;
            Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            vm.clearError();
        });
        vm.info().observe(getViewLifecycleOwner(), i -> {
            if (i == null) return;
            Toast.makeText(requireContext(), i, Toast.LENGTH_SHORT).show();
            vm.clearInfo();
        });
        return b.getRoot();
    }

    private void show(@Nullable Booking bk) {
        if (bk == null || b == null) return;
        String me = vm.me();
        boolean isOwner = me.equals(bk.ownerId);
        boolean isRenter = me.equals(bk.renterId);
        String other = bk.otherName(me);
        Pending p = vm.pending().getValue();
        if (p == null) p = new Pending();

        Glide.with(b.ivPhoto).load(bk.itemPhoto).centerCrop().into(b.ivPhoto);
        b.tvTitle.setText(bk.itemTitle);
        b.tvStatus.setText(bk.statusLabel());
        b.tvStatus.setTextColor(bk.statusColor());
        b.tvWho.setText(getString(R.string.booking_with, other));
        b.tvDates.setText(DateFmt.range(requireContext(), bk.startMs, bk.endMs));
        b.tvPriceLine.setText(getString(R.string.price_line, Format.money(bk.rate), bk.units)
                + " " + bk.unit + (bk.units > 1 ? "s" : ""));
        b.tvTotal.setText(getString(R.string.total_line, Format.money(bk.total)));
        if (bk.note != null && !bk.note.isEmpty()) {
            b.tvNote.setText("“" + bk.note + "”");
            b.tvNote.setVisibility(View.VISIBLE);
        } else {
            b.tvNote.setVisibility(View.GONE);
        }
        if (bk.lateFee > 0) {
            b.tvLate.setText(getString(R.string.late_fee_line, Format.money(bk.lateFee),
                    Format.money(bk.lateFeeCharged)));
            b.tvLate.setVisibility(View.VISIBLE);
        } else {
            b.tvLate.setVisibility(View.GONE);
        }
        showGallery(bk);

        b.btnStep1.setVisibility(View.GONE);
        b.btnStep2.setVisibility(View.GONE);
        b.btnPrimary.setVisibility(View.GONE);
        b.btnSecondary.setVisibility(View.GONE);
        b.tvHint.setVisibility(View.GONE);
        boolean idle = !Boolean.TRUE.equals(vm.busy().getValue());

        String status = bk.status == null ? "" : bk.status;
        switch (status) {
            case Booking.REQUESTED:
                if (isOwner) {
                    primary(getString(R.string.accept_request), true, v -> vm.accept());
                    secondary(getString(R.string.decline_request),
                            v -> confirm(R.string.decline_request, () -> vm.decline()));
                    hint(getString(R.string.hint_owner_requested));
                } else if (isRenter) {
                    secondary(getString(R.string.cancel_request),
                            v -> confirm(R.string.cancel_request, () -> vm.cancel()));
                    hint(getString(R.string.hint_renter_requested));
                }
                break;

            case Booking.ACCEPTED:
                if (isRenter) {
                    primary(getString(R.string.pay_wallet, Format.money(bk.total)), true,
                            v -> confirm(R.string.pay_wallet, () -> vm.pay(), Format.money(bk.total)));
                    secondary(getString(R.string.cancel_booking),
                            v -> confirm(R.string.cancel_booking, () -> vm.cancel()));
                    hint(getString(R.string.hint_renter_accepted));
                } else if (isOwner) {
                    secondary(getString(R.string.cancel_booking),
                            v -> confirm(R.string.cancel_booking, () -> vm.cancel()));
                    hint(getString(R.string.hint_owner_accepted));
                }
                break;

            case Booking.PAID:
                if (isOwner) {
                    if (!bk.hasPhotos("beforeOwner")) {
                        step(b.btnStep1, getString(R.string.btn_take_condition_photos),
                                v -> openCamera(CameraFragment.MODE_PHOTO, "photos_beforeOwner"));
                        hint(getString(R.string.hint_paid_owner_photos));
                    } else {
                        primary(getString(R.string.btn_show_handover_qr), true,
                                v -> openQr(bk, "handover"));
                        hint(getString(R.string.hint_paid_owner_qr));
                    }
                } else if (isRenter) {
                    if (!bk.hasPhotos("beforeOwner")) {
                        hint(getString(R.string.hint_paid_renter_wait));
                    } else {
                        step(b.btnStep1, p.handoverCode != null ? getString(R.string.step_scanned)
                                        : getString(R.string.step_scan_handover),
                                v -> openCamera(CameraFragment.MODE_SCAN, "scan_handover"));
                        step(b.btnStep2, photoLabel(p.beforeRenter.size(), R.string.step_photos),
                                v -> openCamera(CameraFragment.MODE_PHOTO, "photos_beforeRenter"));
                        primary(getString(R.string.btn_confirm_handover),
                                idle && p.handoverCode != null && !p.beforeRenter.isEmpty(),
                                v -> vm.confirmHandover());
                        hint(getString(R.string.hint_paid_renter));
                    }
                }
                break;

            case Booking.HANDED_OVER:
            case Booking.OVERDUE:
                if (isOwner) {
                    primary(getString(R.string.btn_show_return_qr), true, v -> openQr(bk, "return"));
                    hint(getString(R.string.hint_use_owner));
                } else if (isRenter) {
                    step(b.btnStep1, p.returnCode != null ? getString(R.string.step_scanned)
                                    : getString(R.string.step_scan_return),
                            v -> openCamera(CameraFragment.MODE_SCAN, "scan_return"));
                    step(b.btnStep2, photoLabel(p.afterRenter.size(), R.string.step_photos),
                            v -> openCamera(CameraFragment.MODE_PHOTO, "photos_afterRenter"));
                    primary(getString(R.string.btn_return_item),
                            idle && p.returnCode != null && !p.afterRenter.isEmpty(),
                            v -> confirm(R.string.btn_return_item, () -> vm.returnItem()));
                    if (bk.isPastDue()) {
                        double fee = Pricing.lateFee(bk.rate, bk.total, bk.unit, bk.endMs,
                                System.currentTimeMillis());
                        hint(getString(R.string.hint_use_renter_late, Format.money(fee), bk.unit));
                    } else {
                        hint(getString(R.string.hint_use_renter, DateFmt.one(requireContext(), bk.endMs)));
                    }
                }
                break;

            case Booking.RETURNED: {
                double payout = bk.escrow + bk.lateFeeCharged;
                if (isOwner) {
                    step(b.btnStep1, p.afterOwner.isEmpty() ? getString(R.string.btn_after_photos)
                                    : getString(R.string.btn_after_photos_done, p.afterOwner.size()),
                            v -> openCamera(CameraFragment.MODE_PHOTO, "photos_afterOwner"));
                    primary(getString(R.string.btn_confirm_return, Format.money(payout)),
                            idle && !p.afterOwner.isEmpty(),
                            v -> confirm(R.string.btn_confirm_return, () -> vm.confirmReturn(),
                                    Format.money(payout)));
                    hint(getString(R.string.hint_returned_owner, Format.money(payout)));
                } else if (isRenter) {
                    boolean canClose = bk.returnedAtMs > 0
                            && System.currentTimeMillis() >= bk.returnedAtMs + RENTER_CLOSE_AFTER_MS;
                    if (canClose) {
                        primary(getString(R.string.btn_close_rental), true,
                                v -> confirm(R.string.btn_close_rental, () -> vm.closeAfterDeadline()));
                        hint(getString(R.string.hint_returned_renter_late));
                    } else {
                        hint(getString(R.string.hint_returned_renter));
                    }
                }
                break;
            }

            case Booking.COMPLETED:
                if (isOwner && !bk.payoutCollected) {
                    double payout = bk.escrow + bk.lateFeeCharged;
                    primary(getString(R.string.btn_collect_payout, Format.money(payout)), true,
                            v -> vm.collectPayout());
                    hint(getString(R.string.hint_collect_payout));
                } else {
                    hint(getString(R.string.hint_completed));
                }
                break;

            default:
                break;
        }

        // Keep due-time reminders in step with what the app just learned about this booking.
        ReminderScheduler.sync(requireContext(), Collections.singletonList(bk), me);

        b.btnChat.setText(getString(R.string.message_other, other));
        b.btnChat.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("chatId", bk.itemId + "_" + bk.renterId);
            NavHostFragment.findNavController(this).navigate(R.id.chatFragment, args);
        });
        b.btnItem.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("itemId", bk.itemId);
            NavHostFragment.findNavController(this).navigate(R.id.itemDetailFragment, args);
        });
    }

    private void showGallery(Booking bk) {
        List<String> all = new ArrayList<>();
        for (String key : PHOTO_KEYS) {
            List<String> l = bk.photos.get(key);
            if (l != null) all.addAll(l);
        }
        gallery.setUrls(all);
        int vis = all.isEmpty() ? View.GONE : View.VISIBLE;
        b.tvPhotosHeader.setVisibility(vis);
        b.rvPhotos.setVisibility(vis);
    }

    private String photoLabel(int count, int emptyRes) {
        return count == 0 ? getString(emptyRes) : getString(R.string.step_photos_done, count);
    }

    private void openCamera(String mode, String key) {
        Bundle args = new Bundle();
        args.putString(CameraFragment.ARG_MODE, mode);
        args.putString(CameraFragment.ARG_KEY, key);
        NavHostFragment.findNavController(this).navigate(R.id.cameraFragment, args);
    }

    private void openQr(Booking bk, String kind) {
        Bundle args = new Bundle();
        args.putString("bookingId", bk.id);
        args.putString("kind", kind);
        NavHostFragment.findNavController(this).navigate(R.id.qrFragment, args);
    }

    private void primary(String text, boolean enabled, View.OnClickListener l) {
        b.btnPrimary.setText(text);
        b.btnPrimary.setEnabled(enabled);
        b.btnPrimary.setOnClickListener(l);
        b.btnPrimary.setVisibility(View.VISIBLE);
    }

    private void secondary(String text, View.OnClickListener l) {
        b.btnSecondary.setText(text);
        b.btnSecondary.setEnabled(true);
        b.btnSecondary.setOnClickListener(l);
        b.btnSecondary.setVisibility(View.VISIBLE);
    }

    private void step(MaterialButton btn, String text, View.OnClickListener l) {
        btn.setText(text);
        btn.setEnabled(true);
        btn.setOnClickListener(l);
        btn.setVisibility(View.VISIBLE);
    }

    private void hint(String text) {
        b.tvHint.setText(text);
        b.tvHint.setVisibility(View.VISIBLE);
    }

    private void confirm(int titleRes, Runnable action, Object... titleArgs) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(titleRes, titleArgs))
                .setMessage(R.string.are_you_sure)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> action.run())
                .show();
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
