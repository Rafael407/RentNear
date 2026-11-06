package com.rentnear.app.ui.wallet;

import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.rentnear.app.databinding.FragmentWalletBinding;
import com.rentnear.app.databinding.ItemTxBinding;
import com.rentnear.app.model.WalletTx;
import com.rentnear.app.util.Format;
import com.rentnear.app.viewmodel.WalletViewModel;

/** Demo wallet: balance, top-up, and history. */
public class WalletFragment extends Fragment {
    private FragmentWalletBinding b;
    private WalletViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentWalletBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(WalletViewModel.class);

        TxAdapter adapter = new TxAdapter();
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        b.btnAdd500.setOnClickListener(v -> vm.topUp(500));
        b.btnAdd1000.setOnClickListener(v -> vm.topUp(1000));
        b.btnAdd2000.setOnClickListener(v -> vm.topUp(2000));

        vm.balance().observe(getViewLifecycleOwner(), bal ->
                b.tvBalance.setText(bal == null ? "" : Format.money(bal)));
        vm.tx().observe(getViewLifecycleOwner(), adapter::submitList);
        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e == null) return;
            Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
            vm.clearError();
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

    private static class TxAdapter extends ListAdapter<WalletTx, TxAdapter.Holder> {
        TxAdapter() {
            super(new DiffUtil.ItemCallback<WalletTx>() {
                @Override
                public boolean areItemsTheSame(@NonNull WalletTx x, @NonNull WalletTx y) {
                    return x.id.equals(y.id);
                }

                @Override
                public boolean areContentsTheSame(@NonNull WalletTx x, @NonNull WalletTx y) {
                    return x.amount == y.amount && x.createdAtMs == y.createdAtMs;
                }
            });
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemTxBinding.inflate(LayoutInflater.from(parent.getContext()),
                    parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            WalletTx t = getItem(position);
            h.b.tvLabel.setText(t.label);
            h.b.tvWhen.setText(DateUtils.getRelativeTimeSpanString(t.createdAtMs,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
            boolean out = t.amount < 0;
            h.b.tvAmount.setText((out ? "-" : "+") + Format.money(Math.abs(t.amount)));
            h.b.tvAmount.setTextColor(out ? 0xFFB3261E : 0xFF0F766E);
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ItemTxBinding b;

            Holder(ItemTxBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }
    }
}
