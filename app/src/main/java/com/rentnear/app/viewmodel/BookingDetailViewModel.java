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
import com.rentnear.app.model.Claim;
import com.rentnear.app.repository.BookingRepository;
import com.rentnear.app.repository.ClaimRepository;
import com.rentnear.app.repository.ReviewRepository;
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
    private final ReviewRepository reviews = new ReviewRepository();
    private final ClaimRepository claims = new ClaimRepository();
    private final String me;

    private final MutableLiveData<Booking> booking = new MutableLiveData<>();
    private final MutableLiveData<Pending> pending = new MutableLiveData<>(new Pending());
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();
    /** Whether I have already reviewed this booking; null until known. */
    private final MutableLiveData<Boolean> reviewed = new MutableLiveData<>();
    /** The claim filed for this booking, if any; checked once the item has been returned. */
    private final MutableLiveData<Claim> claim = new MutableLiveData<>();
    private final MutableLiveData<Boolean> claimChecked = new MutableLiveData<>(false);

    private String bookingId;
    private ListenerRegistration registration;
    private boolean overdueMarked;
    private boolean reviewChecked;
    private boolean claimLookup;

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
    public LiveData<Boolean> reviewed() { return reviewed; }
    public LiveData<Claim> claim() { return claim; }
    public LiveData<Boolean> claimChecked() { return claimChecked; }

    public void init(String id) {
        if (bookingId == null) bookingId = id;
    }

    public void start() {
        if (bookingId == null || registration != null) return;
        registration = repo.listenOne(bookingId, new BookingRepository.OneListener() {
            @Override
            public void onBooking(Booking b) {
                booking.setValue(b);
                if (!claimLookup && (Booking.RETURNED.equals(b.status) || Booking.COMPLETED.equals(b.status))) {
                    claimLookup = true;
                    findClaim(b);
                }
                if (!reviewChecked && Booking.COMPLETED.equals(b.status)) {
                    reviewChecked = true;
                    reviews.hasReviewed(b.id, me, (done, err) -> {
                        if (err == null) reviewed.setValue(done);
                    });
                }
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

    // ------------------------------------------------------------------ step 5: reviews

    /** Saves my review of this completed booking; it also updates scores, points and the item rating. */
    public void submitReview(int rating, String comment) {
        Booking b = booking.getValue();
        if (b == null || rating < 1 || rating > 5) return;
        busy.setValue(true);
        reviews.submit(b, me, rating, comment, err -> {
            busy.setValue(false);
            if (err != null) {
                error.setValue(err);
                return;
            }
            reviewed.setValue(true);
            info.setValue("Thanks! Review saved. You earned " + ReviewRepository.REVIEWER_POINTS + " points.");
        });
    }

    // ------------------------------------------------------------------ step 6: claims

    private void findClaim(Booking b) {
        claims.find(b, me, (c, err) -> {
            if (err == null) {
                claim.setValue(c);
                claimChecked.setValue(true);
            } else {
                claimLookup = false; // try again on the next update
            }
        });
    }

    /** Owner: asks for money for damage. Allowed for 24 hours after the return. */
    public void fileClaim(double amount, String reason) {
        Booking b = booking.getValue();
        if (b == null) return;
        if (!ClaimRepository.canFile(b)) {
            error.setValue("Claims can only be filed within 24 hours of the return.");
            return;
        }
        if (amount <= 0 || amount > b.declaredValue) {
            error.setValue("The amount must be more than 0 and at most the item's replacement value.");
            return;
        }
        if (reason == null || reason.trim().isEmpty()) {
            error.setValue("Describe the damage.");
            return;
        }
        busy.setValue(true);
        claims.create(b, amount, reason, err -> {
            if (err != null) {
                busy.setValue(false);
                error.setValue(err);
                return;
            }
            info.setValue("Claim sent to the renter.");
            busy.setValue(false);
            findClaim(b);
        });
    }

    public void clearError() { error.setValue(null); }
    public void clearInfo() { info.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
