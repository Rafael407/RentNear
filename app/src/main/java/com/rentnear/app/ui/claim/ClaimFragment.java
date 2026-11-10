package com.rentnear.app.ui.claim;

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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rentnear.app.R;
import com.rentnear.app.databinding.DialogClaimBinding;
import com.rentnear.app.databinding.FragmentClaimBinding;
import com.rentnear.app.model.Claim;
import com.rentnear.app.util.Format;
import com.rentnear.app.viewmodel.ClaimViewModel;

/** One claim, with the actions that fit my role and its status. Args: claimId. */
public class ClaimFragment extends Fragment {
    private FragmentClaimBinding b;
    private ClaimViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentClaimBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(ClaimViewModel.class);
        String id = getArguments() == null ? null : getArguments().getString("claimId");
        if (id != null) vm.init(id);

        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.btnDebts.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.debtsFragment));
        vm.claim().observe(getViewLifecycleOwner(), this::show);
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (b == null) return;
            boolean on = Boolean.TRUE.equals(busy);
            b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            if (on) {
                b.btnPrimary.setEnabled(false);
                b.btnSecondary.setEnabled(false);
                b.btnTertiary.setEnabled(false);
            } else {
                show(vm.claim().getValue());
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

    private void show(@Nullable Claim c) {
        if (c == null || b == null) return;
        String me = vm.me();
        boolean owner = me.equals(c.ownerId);
        boolean renter = me.equals(c.renterId);

        b.tvTitle.setText(c.itemTitle);
        b.tvStatus.setText(c.statusLabel());
        b.tvWho.setText(getString(R.string.claim_between,
                c.ownerName == null ? "Owner" : c.ownerName,
                c.renterName == null ? "Renter" : c.renterName));
        b.tvAmount.setText(getString(R.string.claim_amount_line, Format.money(c.amount)));
        b.tvReason.setText(getString(R.string.claim_reason_line, c.reason == null ? "" : c.reason));
        if (Claim.OFFERED.equals(c.status)) {
            b.tvOffer.setText(getString(R.string.claim_offer_line, Format.money(c.partialOffer)));
            b.tvOffer.setVisibility(View.VISIBLE);
        } else if (Claim.ACCEPTED.equals(c.status)) {
            b.tvOffer.setText(getString(R.string.claim_resolved_line, Format.money(c.resolvedAmount)));
            b.tvOffer.setVisibility(View.VISIBLE);
        } else {
            b.tvOffer.setVisibility(View.GONE);
        }

        hide(b.btnPrimary);
        hide(b.btnSecondary);
        hide(b.btnTertiary);
        b.tvHint.setVisibility(View.GONE);
        b.btnDebts.setVisibility(Claim.ACCEPTED.equals(c.status) ? View.VISIBLE : View.GONE);
        b.btnBooking.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("bookingId", c.bookingId);
            NavHostFragment.findNavController(this).navigate(R.id.bookingDetailFragment, args);
        });

        switch (c.status == null ? "" : c.status) {
            case Claim.OPEN:
                if (renter) {
                    button(b.btnPrimary, R.string.claim_accept,
                            () -> confirm(R.string.claim_accept, () -> vm.accept()));
                    button(b.btnSecondary, R.string.claim_offer, () -> offerDialog(c));
                    button(b.btnTertiary, R.string.claim_dispute,
                            () -> confirm(R.string.claim_dispute, () -> vm.dispute()));
                    hint(R.string.hint_claim_renter_open);
                } else if (owner) {
                    button(b.btnSecondary, R.string.claim_withdraw,
                            () -> confirm(R.string.claim_withdraw, () -> vm.withdraw()));
                    hint(R.string.hint_claim_owner_open);
                }
                break;
            case Claim.OFFERED:
                if (owner) {
                    button(b.btnPrimary, R.string.claim_accept_offer,
                            () -> confirm(R.string.claim_accept_offer, () -> vm.acceptOffer()));
                    button(b.btnSecondary, R.string.claim_reject_offer,
                            () -> confirm(R.string.claim_reject_offer, () -> vm.rejectOffer()));
                    hint(R.string.hint_claim_owner_offered);
                } else if (renter) {
                    hint(R.string.hint_claim_renter_offered);
                }
                break;
            case Claim.ACCEPTED:
                hint(R.string.hint_claim_accepted);
                break;
            case Claim.DISPUTED:
                hint(R.string.hint_claim_disputed);
                break;
            default:
                break;
        }
    }

    /** The renter proposes paying less than the claim. */
    private void offerDialog(Claim c) {
        DialogClaimBinding d = DialogClaimBinding.inflate(getLayoutInflater());
        d.etAmount.setHint(R.string.offer_amount_hint);
        d.reasonLayout.setVisibility(View.GONE);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.claim_offer_dialog_title)
                .setView(d.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.claim_send, (dlg, w) -> {
                    double amount = 0;
                    try {
                        amount = Double.parseDouble(String.valueOf(d.etAmount.getText()).trim());
                    } catch (NumberFormatException ignored) {
                        // handled as 0 below
                    }
                    vm.offerPartial(amount);
                })
                .show();
    }

    private void button(MaterialButton btn, int textRes, Runnable action) {
        btn.setText(textRes);
        btn.setEnabled(true);
        btn.setOnClickListener(v -> action.run());
        btn.setVisibility(View.VISIBLE);
    }

    private void hide(View v) {
        v.setVisibility(View.GONE);
    }

    private void hint(int res) {
        b.tvHint.setText(res);
        b.tvHint.setVisibility(View.VISIBLE);
    }

    private void confirm(int titleRes, Runnable action) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(titleRes)
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
