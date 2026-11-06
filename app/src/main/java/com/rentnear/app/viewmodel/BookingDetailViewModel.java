package com.rentnear.app.viewmodel;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.data.cloudinary.CloudinaryUploader;
import com.rentnear.app.model.Booking;
import com.rentnear.app.repository.BookingRepository;
import com.rentnear.app.repository.WalletRepository;
import com.rentnear.app.util.Done;
import com.rentnear.app.util.QrCode;

import java.util.ArrayList;
import java.util.List;

/**
 * One booking, live while the screen is visible, plus the actions each side can take. Scans and
 * photos collected for a step that has not been confirmed yet are kept in {@link Pending}.
 */
public class BookingDetailViewModel extends ViewModel {
    /** Things collected on this screen but not yet written to the booking. */
    public static class Pending {
        public String handoverCode;
        public String returnCode;
        public List<String> beforeRenter = new ArrayList<>();
        public List<String> afterRenter = new ArrayList<>();
        public List<String> afterOwner = new ArrayList<>();
    }

    private final BookingRepository repo = new BookingRepository();
    private final WalletRepository wallet = new WalletRepository();
    private final String me;

    private final MutableLiveData<Booking> booking = new MutableLiveData<>();
    private final MutableLiveData<Pending> pending = new MutableLiveData<>(new Pending());
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();

    private String bookingId;
    private ListenerRegistration registration;
    private boolean overdueMarked;

    public BookingDetailViewModel() {
        String uid = FirebaseAuth.getInstance().getUid();
        me = uid == null ? "" : uid;
    }

    public String me() { return me; }
    public LiveData<Booking> booking() { return booking; }
    public LiveData<Pending> pending() { return pending; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<String> error() { return error; }
    public LiveData<String> info() { return info; }

    public void init(String id) {
        if (bookingId == null) bookingId = id;
    }

    public void start() {
        if (bookingId == null || registration != null) return;
        registration = repo.listenOne(bookingId, new BookingRepository.OneListener() {
            @Override
            public void onBooking(Booking b) {
                booking.setValue(b);
                // Best effort: either side may record that an in-use booking is now past its due time.
                if (!overdueMarked && Booking.HANDED_OVER.equals(b.status) && b.isPastDue()) {
                    overdueMarked = true;
                    repo.markOverdue(b);
                }
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

    /** Finishes a write: stops the spinner, shows the error or message, and clears collected data. */
    private Done finish(String successMessage) {
        return err -> {
            busy.setValue(false);
            if (err != null) {
                error.setValue(err);
            } else {
                pending.setValue(new Pending());
                if (successMessage != null) info.setValue(successMessage);
            }
        };
    }

    // ------------------------------------------------------------------ step 4a actions

    public void accept() {
        Booking b = booking.getValue();
        if (b == null) return;
        busy.setValue(true);
        repo.accept(b, finish("Booking accepted. The renter can now pay."));
    }

    public void decline() {
        Booking b = booking.getValue();
        if (b == null) return;
        busy.setValue(true);
        repo.decline(b, finish("Request declined."));
    }

    public void cancel() {
        Booking b = booking.getValue();
        if (b == null) return;
        busy.setValue(true);
        repo.cancel(b, finish("Booking cancelled."));
    }

    /** Checks the demo wallet has enough first, so the user gets a clear message instead of a rules error. */
    public void pay() {
        Booking b = booking.getValue();
        if (b == null) return;
        busy.setValue(true);
        wallet.getBalance(me, (balance, err) -> {
            if (err != null) {
                busy.setValue(false);
                error.setValue(err);
                return;
            }
            if (balance < b.total) {
                busy.setValue(false);
                error.setValue("Not enough wallet balance. Add demo money in Profile > Wallet.");
                return;
            }
            repo.pay(b, me, finish("Paid. The amount is held until the item is returned."));
        });
    }

    // ------------------------------------------------------------------ step 4b: scans and photos

    /** @param kind "handover" or "return" */
    public void onScanned(String kind, String text) {
        Booking b = booking.getValue();
        if (b == null) return;
        String code = QrCode.parse(text, b.id, kind);
        if (code == null) {
            error.setValue("That QR code is not for this step of this booking.");
            return;
        }
        Pending p = pending.getValue();
        if (p == null) p = new Pending();
        if ("handover".equals(kind)) p.handoverCode = code;
        else p.returnCode = code;
        pending.setValue(p);
    }

    /**
     * Uploads photos taken by the camera screen. The owner's "before" photos are saved to the booking
     * straight away; the others wait for the confirm button of their step.
     *
     * @param key beforeOwner, beforeRenter, afterRenter or afterOwner
     */
    public void onPhotos(Context context, String key, List<String> uris) {
        Booking b = booking.getValue();
        if (b == null || uris == null || uris.isEmpty()) return;
        List<Uri> list = new ArrayList<>();
        for (String s : uris) list.add(Uri.parse(s));

        busy.setValue(true);
        CloudinaryUploader.uploadAll(context, list, (urls, err) -> {
            if (err != null || urls == null) {
                busy.setValue(false);
                error.setValue(err == null ? "Upload failed" : err);
                return;
            }
            if ("beforeOwner".equals(key)) {
                repo.addPhotos(b.id, key, urls, finish("Photos saved. Now show the handover QR code."));
                return;
            }
            Pending p = pending.getValue();
            if (p == null) p = new Pending();
            switch (key) {
                case "beforeRenter": p.beforeRenter = urls; break;
                case "afterRenter": p.afterRenter = urls; break;
                case "afterOwner": p.afterOwner = urls; break;
                default: break;
            }
            busy.setValue(false);
            pending.setValue(p);
        });
    }

    // ------------------------------------------------------------------ step 4b: confirmations

    /** Renter: scanned the handover QR and took photos, so the booking moves to in use. */
    public void confirmHandover() {
        Booking b = booking.getValue();
        Pending p = pending.getValue();
        if (b == null || p == null || p.handoverCode == null || p.beforeRenter.isEmpty()) return;
        busy.setValue(true);
        repo.handover(b.id, p.handoverCode, p.beforeRenter,
                finish("Handover recorded. The rental is now in use."));
    }

    /** Renter: scanned the return QR and took photos. Any late fee is taken from the wallet. */
    public void returnItem() {
        Booking b = booking.getValue();
        Pending p = pending.getValue();
        if (b == null || p == null || p.returnCode == null || p.afterRenter.isEmpty()) return;
        busy.setValue(true);
        wallet.getBalance(me, (balance, err) -> {
            if (err != null) {
                busy.setValue(false);
                error.setValue(err);
                return;
            }
            repo.returnItem(b, p.returnCode, p.afterRenter, me, balance,
                    finish("Item returned. The owner will confirm it."));
        });
    }

    /** Owner: confirms the returned item with photos and collects the held payment. */
    public void confirmReturn() {
        Booking b = booking.getValue();
        Pending p = pending.getValue();
        if (b == null || p == null || p.afterOwner.isEmpty()) return;
        busy.setValue(true);
        repo.confirmReturn(b, p.afterOwner, me,
                finish("Return confirmed. The payment is in your wallet."));
    }

    /** Renter: closes a returned booking when the owner has not acted for 24 hours. */
    public void closeAfterDeadline() {
        Booking b = booking.getValue();
        if (b == null) return;
        busy.setValue(true);
        repo.completeAfterDeadline(b, finish("Rental closed."));
    }

    /** Owner: collects a payout that was not collected when the rental closed. */
    public void collectPayout() {
        Booking b = booking.getValue();
        if (b == null) return;
        busy.setValue(true);
        repo.collectPayout(b, me, finish("Payout collected."));
    }

    public void clearError() { error.setValue(null); }
    public void clearInfo() { info.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
