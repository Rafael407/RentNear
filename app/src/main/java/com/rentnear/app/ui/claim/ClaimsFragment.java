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

import com.google.firebase.auth.FirebaseAuth;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentListBinding;
import com.rentnear.app.viewmodel.ClaimsViewModel;

/** Claims I filed or received. */
public class ClaimsFragment extends Fragment {
    private FragmentListBinding b;
    private ClaimsViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentListBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(ClaimsViewModel.class);
        b.tvTitle.setText(R.string.claims);
        b.tvEmpty.setText(R.string.no_claims);
        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        ClaimAdapter adapter = new ClaimAdapter(FirebaseAuth.getInstance().getUid(), c -> {
            Bundle args = new Bundle();
            args.putString("claimId", c.id);
            NavHostFragment.findNavController(this).navigate(R.id.claimFragment, args);
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        vm.claims().observe(getViewLifecycleOwner(), list -> {
            if (b == null || list == null) return;
            adapter.setItems(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
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
}
