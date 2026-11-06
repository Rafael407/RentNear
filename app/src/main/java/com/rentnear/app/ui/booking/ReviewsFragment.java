package com.rentnear.app.ui.booking;

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

import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentReviewsBinding;
import com.rentnear.app.model.Review;
import com.rentnear.app.viewmodel.ReviewsViewModel;

import java.util.List;
import java.util.Locale;

/** Reviews for an item or about a person. Args: kind ("item" or "user"), id, title. */
public class ReviewsFragment extends Fragment {
    private FragmentReviewsBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentReviewsBinding.inflate(inflater, container, false);
        Bundle a = getArguments() == null ? new Bundle() : getArguments();
        ReviewsViewModel vm = new ViewModelProvider(this).get(ReviewsViewModel.class);
        vm.load(a.getString("kind", "user"), a.getString("id", ""));

        b.tvTitle.setText(a.getString("title", getString(R.string.reviews)));
        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        ReviewAdapter adapter = new ReviewAdapter();
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        vm.reviews().observe(getViewLifecycleOwner(), r -> {
            if (b == null || r == null) return;
            if (!r.isOk()) {
                Toast.makeText(requireContext(), r.error, Toast.LENGTH_LONG).show();
                return;
            }
            List<Review> list = r.data;
            adapter.setItems(list);
            b.tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            if (list.isEmpty()) {
                b.tvSummary.setText("");
            } else {
                double sum = 0;
                for (Review x : list) sum += x.rating;
                b.tvSummary.setText(String.format(Locale.US, "%.1f★ from %d review%s",
                        sum / list.size(), list.size(), list.size() == 1 ? "" : "s"));
            }
        });
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
