package com.rentnear.app.ui.home;

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;
import com.rentnear.app.R;
import com.rentnear.app.databinding.FragmentHomeBinding;
import com.rentnear.app.util.LocationProvider;
import com.rentnear.app.viewmodel.HomeViewModel;

/** Home: Map and Browse tabs sharing one HomeViewModel (scoped to this fragment). */
public class HomeFragment extends Fragment {
    private FragmentHomeBinding b;
    private HomeViewModel vm;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> loadLocation());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(HomeViewModel.class);

        b.pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return position == 0 ? new MapFragment() : new BrowseFragment();
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });
        b.pager.setUserInputEnabled(false); // swiping would fight with map panning
        new TabLayoutMediator(b.tabs, b.pager, (tab, position) ->
                tab.setText(position == 0 ? R.string.map_tab : R.string.browse_tab)).attach();

        b.fabAdd.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.createListingFragment));

        if (vm.location().getValue() == null) {
            if (LocationProvider.hasPermission(requireContext())) {
                loadLocation();
            } else {
                permissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        }
        return b.getRoot();
    }

    private void loadLocation() {
        if (!isAdded()) return;
        LocationProvider.current(requireContext(), (loc, real) -> {
            if (!isAdded()) return;
            if (!real) {
                Toast.makeText(requireContext(), R.string.default_location_notice,
                        Toast.LENGTH_LONG).show();
            }
            vm.setLocation(loc);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
