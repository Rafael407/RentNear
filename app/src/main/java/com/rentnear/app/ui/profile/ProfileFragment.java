package com.rentnear.app.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.databinding.FragmentProfileBinding;
import com.rentnear.app.ui.auth.LoginActivity;
import com.rentnear.app.viewmodel.AuthViewModel;

/** Minimal profile for step 1: name, email, score, points, sign out. Expanded in step 5. */
public class ProfileFragment extends Fragment {
    private FragmentProfileBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        AuthViewModel vm = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        FirebaseUser u = vm.currentUser();
        if (u != null) {
            b.tvName.setText(u.getDisplayName() == null ? "" : u.getDisplayName());
            b.tvEmail.setText(u.getEmail());
        }

        // Load score and points from Firestore.
        MutableLiveData<com.rentnear.app.util.Result<com.rentnear.app.model.UserProfile>> live =
                new MutableLiveData<>();
        live.observe(getViewLifecycleOwner(), r -> {
            if (b == null || r == null || !r.isOk()) return;
            b.tvName.setText(r.data.name);
            b.tvStats.setText("Trust score: " + r.data.trustScore + "   Points: " + r.data.points);
        });
        if (u != null) new com.rentnear.app.repository.UserRepository().load(u.getUid(), live);

        b.btnWallet.setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                        .navigate(com.rentnear.app.R.id.walletFragment));
        b.btnSignOut.setOnClickListener(v -> {
            vm.signOut();
            Intent i = new Intent(requireContext(), LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
