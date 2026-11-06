package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.Booking;
import com.rentnear.app.repository.BookingRepository;
import com.rentnear.app.util.QrCode;

/** Owner's QR screen: loads (or creates) the codes and watches the booking so the screen can close. */
public class QrViewModel extends ViewModel {
    private final BookingRepository repo = new BookingRepository();

    private final MutableLiveData<String> payload = new MutableLiveData<>();
    private final MutableLiveData<Booking> booking = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private String bookingId;
    private String kind;
    private ListenerRegistration registration;

    public LiveData<String> payload() { return payload; }
    public LiveData<Booking> booking() { return booking; }
    public LiveData<String> error() { return error; }
    public String kind() { return kind; }

    public void init(String id, String kind) {
        if (bookingId != null) return;
        this.bookingId = id;
        this.kind = kind;
        repo.getOrCreateCodes(id, (handover, ret, err) -> {
            if (err != null) {
                error.setValue(err);
                return;
            }
            String code = "handover".equals(kind) ? handover : ret;
            payload.setValue(QrCode.payload(id, kind, code));
        });
    }

    public void start() {
        if (bookingId == null || registration != null) return;
        registration = repo.listenOne(bookingId, new BookingRepository.OneListener() {
            @Override
            public void onBooking(Booking b) {
                booking.setValue(b);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    public void clearError() { error.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
