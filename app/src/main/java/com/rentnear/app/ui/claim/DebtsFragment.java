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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentListBinding;
import com.rentnear.app.model.Debt;
import com.rentnear.app.util.Format;
import com.rentnear.app.viewmodel.DebtsViewModel;
import com.rentnear.app.workers.DebtReminderScheduler;

/** Money I owe after accepted claims, and money owed to me. */
public class DebtsFragment extends Fragment {
    private FragmentListBinding b;
    private DebtsViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentListBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(DebtsViewModel.class);
        b.tvTitle.setText(R.string.debts);
        b.tvSummary.setText(R.string.debts_summary);
        b.tvSummary.setVisibility(View.VISIBLE);
        b.tvEmpty.setText(R.string.no_debts);
        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        DebtAdapter adapter = new DebtAdapter(vm.me(), new DebtAdapter.Actions() {
            @Override
            public void pay(Debt d) {
                confirm(getString(R.string.btn_pay_debt, Format.money(d.amount)), () -> vm.pay(d));
            }

            @Override
            public void collect(Debt d) {
                vm.collect(d);
            }

            @Override
            public void ban(Debt d) {
                confirm(getString(R.string.btn_ban_debtor), () -> vm.ban(d));
            }
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        vm.debts().observe(getViewLifecycleOwner(), list -> {
            if (b == null || list == null) return;
            adapter.setItems(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            DebtReminderScheduler.sync(requireContext(), list, vm.me());
        });
        vm.busy().observe(getViewLifecycleOwner(), busy -> {
            if (b != null) b.progress.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.INVISIBLE);
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

    private void confirm(String title, Runnable action) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
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
