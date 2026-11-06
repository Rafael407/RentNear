package com.rentnear.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;

import com.rentnear.app.databinding.FragmentPlaceholderBinding;

/** Stand-in for tabs built in later steps; shows the destination label from the nav graph. */
public class PlaceholderFragment extends Fragment {
    private FragmentPlaceholderBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentPlaceholderBinding.inflate(inflater, container, false);
        NavDestination dest = NavHostFragment.findNavController(this).getCurrentDestination();
        b.tvTitle.setText(dest == null ? "" : dest.getLabel());
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
