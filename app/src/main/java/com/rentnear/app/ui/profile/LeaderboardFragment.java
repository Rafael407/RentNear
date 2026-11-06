package com.rentnear.app.ui.profile;

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
import com.rentnear.app.databinding.FragmentLeaderboardBinding;
import com.rentnear.app.viewmodel.LeaderboardViewModel;

/** Top users by points or trust score. Tap a row to read the reviews about that person. */
public class LeaderboardFragment extends Fragment {
    private FragmentLeaderboardBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentLeaderboardBinding.inflate(inflater, container, false);
        LeaderboardViewModel vm = new ViewModelProvider(this).get(LeaderboardViewModel.class);

        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        LeaderAdapter adapter = new LeaderAdapter(FirebaseAuth.getInstance().getUid(), user -> {
            Bundle args = new Bundle();
            args.putString("kind", "user");
            args.putString("id", user.uid);
            args.putString("title", getString(R.string.reviews_about, user.name == null ? "User" : user.name));
            NavHostFragment.findNavController(this).navigate(R.id.reviewsFragment, args);
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        b.toggleRank.check(vm.byPoints() ? R.id.btnByPoints : R.id.btnByTrust);
        b.toggleRank.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            b.progress.setVisibility(View.VISIBLE);
            vm.setByPoints(id == R.id.btnByPoints);
        });

        vm.users().observe(getViewLifecycleOwner(), r -> {
            if (b == null || r == null) return;
            b.progress.setVisibility(View.INVISIBLE);
            if (!r.isOk()) {
                Toast.makeText(requireContext(), r.error, Toast.LENGTH_LONG).show();
                return;
            }
            adapter.setItems(r.data, vm.byPoints());
        });

        b.progress.setVisibility(View.VISIBLE);
        vm.refresh();
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
