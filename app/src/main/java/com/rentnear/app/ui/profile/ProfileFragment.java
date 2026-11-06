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

/** Profile: name, email, trust score, points and level, wallet, reviews about me, leaderboard, sign out. */
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
            b.tvStats.setText(getString(com.rentnear.app.R.string.profile_stats,
                    String.format(java.util.Locale.US, "%.0f", r.data.trustScore), r.data.points,
                    com.rentnear.app.util.Tiers.forPoints(r.data.points)));
            b.tvBadge.setVisibility(r.data.trusted ? View.VISIBLE : View.GONE);
        });
        if (u != null) new com.rentnear.app.repository.UserRepository().load(u.getUid(), live);

        b.btnWallet.setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                        .navigate(com.rentnear.app.R.id.walletFragment));
        b.btnLeaderboard.setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this)
                        .navigate(com.rentnear.app.R.id.leaderboardFragment));
        b.btnMyReviews.setOnClickListener(v -> {
            if (u == null) return;
            Bundle args = new Bundle();
            args.putString("kind", "user");
            args.putString("id", u.getUid());
            args.putString("title", getString(com.rentnear.app.R.string.my_reviews));
            androidx.navigation.fragment.NavHostFragment.findNavController(this)
                    .navigate(com.rentnear.app.R.id.reviewsFragment, args);
        });
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
