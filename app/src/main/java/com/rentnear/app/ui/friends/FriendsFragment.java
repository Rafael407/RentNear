package com.rentnear.app.ui.friends;

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
import com.rentnear.app.databinding.FragmentFriendsBinding;
import com.rentnear.app.model.FriendRequest;
import com.rentnear.app.viewmodel.FriendsViewModel;

import java.util.List;
import java.util.Map;

/** Add friends by email, answer requests, and see or remove friends. */
public class FriendsFragment extends Fragment {
    private FragmentFriendsBinding b;
    private FriendsViewModel vm;
    private FriendsAdapter adapter;
    private final androidx.activity.result.ActivityResultLauncher<String> contactsPermission =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) pickContact();
                        else Toast.makeText(requireContext(), R.string.contacts_permission_denied,
                                Toast.LENGTH_SHORT).show();
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentFriendsBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(FriendsViewModel.class);

        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        // Runtime permission, then a query on the Contacts content provider.
        b.btnContacts.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.READ_CONTACTS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pickContact();
            } else {
                contactsPermission.launch(android.Manifest.permission.READ_CONTACTS);
            }
        });
        b.btnAdd.setOnClickListener(v -> {
            vm.add(String.valueOf(b.etEmail.getText()));
            b.etEmail.setText("");
        });

        adapter = new FriendsAdapter(new FriendsAdapter.Actions() {
            @Override
            public void accept(FriendRequest r) { vm.accept(r); }

            @Override
            public void decline(FriendRequest r) { vm.decline(r); }

            @Override
            public void cancel(FriendRequest r) { vm.cancel(r); }

            @Override
            public void remove(String friendUid, String name) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.friend_remove_title, name))
                        .setMessage(R.string.are_you_sure)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (d, w) -> vm.remove(friendUid))
                        .show();
            }
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        vm.friends().observe(getViewLifecycleOwner(), x -> refresh());
        vm.incoming().observe(getViewLifecycleOwner(), x -> refresh());
        vm.outgoing().observe(getViewLifecycleOwner(), x -> refresh());
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

    private void pickContact() {
        com.rentnear.app.util.ContactsPicker.show(requireContext(), email -> {
            if (b != null) b.etEmail.setText(email);
        });
    }

    private void refresh() {
        if (b == null) return;
        List<FriendRequest> in = vm.incoming().getValue();
        List<FriendRequest> out = vm.outgoing().getValue();
        Map<String, String> fr = vm.friends().getValue();
        if (in == null || out == null || fr == null) return;
        adapter.setData(in, out, fr);
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
