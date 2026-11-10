package com.rentnear.app.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.WorkManager;

import com.rentnear.app.R;
import com.rentnear.app.data.cache.ListingCache;
import com.rentnear.app.databinding.FragmentSettingsBinding;
import com.rentnear.app.util.Prefs;

/** Search radius, reminders on or off, and clearing the saved listings. */
public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentSettingsBinding.inflate(inflater, container, false);
        b.btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

        int km = Prefs.radiusKm();
        b.radiusGroup.check(km <= 5 ? R.id.radius5 : km >= 20 ? R.id.radius20 : R.id.radius10);
        b.radiusGroup.setOnCheckedChangeListener((g, id) -> {
            if (id == R.id.radius5) Prefs.setRadiusKm(5);
            else if (id == R.id.radius20) Prefs.setRadiusKm(20);
            else Prefs.setRadiusKm(10);
        });

        b.swReminders.setChecked(Prefs.remindersOn());
        b.swReminders.setOnCheckedChangeListener((v, on) -> {
            Prefs.setRemindersOn(on);
            // Reminders are rescheduled the next time bookings or debts are opened.
            if (!on) WorkManager.getInstance(requireContext().getApplicationContext()).cancelAllWork();
        });

        b.btnClearCache.setOnClickListener(v -> ListingCache.clear(requireContext(), () -> {
            if (getContext() != null) {
                Toast.makeText(requireContext(), R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show();
            }
        }));
        return b.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
