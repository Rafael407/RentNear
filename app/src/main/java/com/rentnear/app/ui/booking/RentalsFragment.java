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
import com.rentnear.app.databinding.FragmentRentalsBinding;
import com.rentnear.app.repository.BookingRepository;
import com.rentnear.app.viewmodel.RentalsViewModel;

/** Rentals tab: bookings where I am the renter, or the owner. */
public class RentalsFragment extends Fragment {
    private FragmentRentalsBinding b;
    private RentalsViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentRentalsBinding.inflate(inflater, container, false);
        vm = new ViewModelProvider(this).get(RentalsViewModel.class);

        BookingAdapter adapter = new BookingAdapter(booking -> {
            Bundle args = new Bundle();
            args.putString("bookingId", booking.id);
            NavHostFragment.findNavController(this).navigate(R.id.bookingDetailFragment, args);
        });
        b.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.recycler.setAdapter(adapter);

        boolean lending = BookingRepository.ROLE_OWNER.equals(vm.role().getValue());
        b.toggleRole.check(lending ? R.id.btnLending : R.id.btnRenting);
        b.toggleRole.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            vm.setRole(id == R.id.btnLending ? BookingRepository.ROLE_OWNER : BookingRepository.ROLE_RENTER);
        });

        vm.bookings().observe(getViewLifecycleOwner(), list -> {
            adapter.submitList(list);
            com.rentnear.app.workers.ReminderScheduler.sync(requireContext(), list,
                    com.google.firebase.auth.FirebaseAuth.getInstance().getUid());
            boolean empty = list == null || list.isEmpty();
            b.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            boolean owner = BookingRepository.ROLE_OWNER.equals(vm.role().getValue());
            b.tvEmpty.setText(owner ? R.string.no_rentals_lending : R.string.no_rentals_renting);
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
