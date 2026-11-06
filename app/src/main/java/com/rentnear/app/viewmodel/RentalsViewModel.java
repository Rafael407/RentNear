package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.Booking;
import com.rentnear.app.repository.BookingRepository;

import java.util.ArrayList;
import java.util.List;

/** Rentals tab: only the selected role's list is listened to, to save Firestore reads. */
public class RentalsViewModel extends ViewModel {
    private final BookingRepository repo = new BookingRepository();
    private final MutableLiveData<String> role = new MutableLiveData<>(BookingRepository.ROLE_RENTER);
    private final MutableLiveData<List<Booking>> bookings = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration registration;
    private boolean visible;

    public LiveData<String> role() { return role; }
    public LiveData<List<Booking>> bookings() { return bookings; }
    public LiveData<String> error() { return error; }

    public void setRole(String r) {
        if (r.equals(role.getValue())) return;
        role.setValue(r);
        bookings.setValue(new ArrayList<>());
        if (visible) attach();
    }

    public void start() {
        visible = true;
        attach();
    }

    public void stop() {
        visible = false;
        detach();
    }

    private void attach() {
        detach();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        String r = role.getValue();
        registration = repo.listenRole(r, uid, new BookingRepository.ListListener() {
            @Override
            public void onBookings(List<Booking> list) {
                // Ignore a late result from a role the user has already switched away from.
                if (r.equals(role.getValue())) bookings.setValue(list);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    private void detach() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    public void clearError() { error.setValue(null); }

    @Override
    protected void onCleared() {
        detach();
    }
}
