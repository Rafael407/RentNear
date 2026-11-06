package com.rentnear.app.ui.home;

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
import com.rentnear.app.databinding.FragmentBrowseBinding;
import com.rentnear.app.model.Item;
import com.rentnear.app.ui.common.CategoryPicker;
import com.rentnear.app.util.Categories;
import com.rentnear.app.viewmodel.HomeViewModel;

/** Browse tab: RecyclerView feed with a ListView category filter and sort chips. */
public class BrowseFragment extends Fragment {
    private FragmentBrowseBinding b;
    private HomeViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentBrowseBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(requireParentFragment()).get(HomeViewModel.class);

        ItemAdapter adapter = new ItemAdapter(this::openItem);
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        b.refresh.setOnRefreshListener(() -> vm.refresh());
        b.btnCategory.setOnClickListener(v -> CategoryPicker.show(requireContext(),
                Categories.forFilter(), vm.category().getValue(), vm::setCategory));
        b.chipSort.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipPrice) vm.setSort(HomeViewModel.Sort.PRICE);
            else if (id == R.id.chipRating) vm.setSort(HomeViewModel.Sort.RATING);
            else vm.setSort(HomeViewModel.Sort.DISTANCE);
        });

        vm.category().observe(getViewLifecycleOwner(), c ->
                b.btnCategory.setText(getString(R.string.category_label, c)));
        vm.shown().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            b.tvEmpty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.loading().observe(getViewLifecycleOwner(), l -> b.refresh.setRefreshing(Boolean.TRUE.equals(l)));
        vm.error().observe(getViewLifecycleOwner(), e -> {
            if (e != null) Toast.makeText(requireContext(), e, Toast.LENGTH_LONG).show();
        });
        return b.getRoot();
    }

    private void openItem(Item item) {
        Bundle args = new Bundle();
        args.putString("itemId", item.id);
        NavHostFragment.findNavController(requireParentFragment())
                .navigate(R.id.itemDetailFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
