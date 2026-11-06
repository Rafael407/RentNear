package com.rentnear.app.repository;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rentnear.app.model.Booking;
import com.rentnear.app.util.Done;
import com.rentnear.app.util.Pricing;
import com.rentnear.app.util.Result;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bookings: request, accept/decline/cancel, and wallet payment. Status changes are also checked
 * by firestore.rules. Lists use one equality filter and are sorted here, so no composite index
 * is needed.
 */
public class BookingRepository {
    private static final int LIMIT = 50;

    public static final String ROLE_RENTER = "renter";
    public static final String ROLE_OWNER = "owner";

    public interface ListListener {
        void onBookings(List<Booking> list);
        void onError(String message);
    }

    public interface OneListener {
        void onBooking(Booking b);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference ref(String id) {
        return db.collection("bookings").document(id);
    }

    public void create(Map<String, Object> data, MutableLiveData<Result<String>> out) {
        db.collection("bookings").add(data)
                .addOnSuccessListener(r -> out.setValue(Result.ok(r.getId())))
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public ListenerRegistration listenRole(String role, String uid, ListListener l) {
        String field = ROLE_OWNER.equals(role) ? "ownerId" : "renterId";
        return db.collection("bookings")
                .whereEqualTo(field, uid)
                .limit(LIMIT)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(e.getMessage());
                        return;
                    }
                    List<Booking> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) list.add(Booking.from(d));
                    }
                    list.sort((a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
                    l.onBookings(list);
                });
    }

    public ListenerRegistration listenOne(String id, OneListener l) {
        return ref(id).addSnapshotListener((snap, e) -> {
            if (e != null) {
                l.onError(e.getMessage());
                return;
            }
            if (snap != null && snap.exists()) l.onBooking(Booking.from(snap));
        });
    }

    private void setStatus(String id, String status, Done done) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("status", status);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        ref(id).update(upd)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /**
     * Owner accepts a request, unless it overlaps a booking they already accepted for the same item.
     * (Checked in the app; the rules cannot query other bookings.)
     */
    public void accept(Booking b, Done done) {
        db.collection("bookings")
                .whereEqualTo("ownerId", b.ownerId)
                .whereEqualTo("itemId", b.itemId)
                .get()
                .addOnSuccessListener(snap -> {
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Booking o = Booking.from(d);
                        if (o.id.equals(b.id) || !o.blocksDates()) continue;
                        if (o.startMs < b.endMs && b.startMs < o.endMs) {
                            done.onDone("This overlaps another accepted booking for the same item.");
                            return;
                        }
                    }
                    setStatus(b.id, Booking.ACCEPTED, done);
                })
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    public void decline(Booking b, Done done) {
        setStatus(b.id, Booking.DECLINED, done);
    }

    public void cancel(Booking b, Done done) {
        setStatus(b.id, Booking.CANCELLED, done);
    }

    /**
     * Renter pays from the demo wallet. One batch: booking becomes paid (money held on the booking
     * as escrow), wallet balance drops by the total, and a wallet history line is added.
     */
    public void pay(Booking b, String uid, Done done) {
        DocumentReference wallet = db.collection("wallets").document(uid);
        Map<String, Object> upd = new HashMap<>();
        upd.put("status", Booking.PAID);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        upd.put("paidAt", FieldValue.serverTimestamp());
        upd.put("escrow", b.total);

        WriteBatch batch = db.batch();
        batch.update(ref(b.id), upd);
        batch.update(wallet, "balance", FieldValue.increment(-b.total));
        batch.set(wallet.collection("tx").document(),
                WalletRepository.txMap("payment", -b.total, "Rental: " + b.itemTitle, b.id));
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    // ------------------------------------------------------------------ step 4b

    public interface CodesCallback {
        void onCodes(String handoverCode, String returnCode, String error);
    }

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static String randomCode() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(CODE_CHARS.charAt(r.nextInt(CODE_CHARS.length())));
        return sb.toString();
    }

    /** Owner only: reads the handover/return codes, creating them the first time. */
    public void getOrCreateCodes(String bookingId, CodesCallback cb) {
        DocumentReference c = ref(bookingId).collection("secret").document("codes");
        c.get().addOnSuccessListener(s -> {
            if (s.exists()) {
                cb.onCodes(s.getString("handoverCode"), s.getString("returnCode"), null);
                return;
            }
            String h = randomCode();
            String r = randomCode();
            Map<String, Object> m = new HashMap<>();
            m.put("handoverCode", h);
            m.put("returnCode", r);
            m.put("createdAt", FieldValue.serverTimestamp());
            c.set(m)
                    .addOnSuccessListener(v -> cb.onCodes(h, r, null))
                    .addOnFailureListener(e -> cb.onCodes(null, null, msg(e)));
        }).addOnFailureListener(e -> cb.onCodes(null, null, msg(e)));
    }

    /** Saves one group of condition photos (beforeOwner, afterOwner, ...). */
    public void addPhotos(String bookingId, String key, List<String> urls, Done done) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("photos." + key, urls);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        ref(bookingId).update(upd)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Renter scanned the handover QR and took photos: paid -> handed_over. */
    public void handover(String bookingId, String scannedCode, List<String> urls, Done done) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("status", Booking.HANDED_OVER);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        upd.put("handedOverAt", FieldValue.serverTimestamp());
        upd.put("scannedCode", scannedCode);
        upd.put("photos.beforeRenter", urls);
        ref(bookingId).update(upd)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /**
     * Renter scanned the return QR and took photos: in use -> returned. Any late fee is taken from
     * the renter's wallet (or whatever is left in it) in the same batch.
     */
    public void returnItem(Booking b, String scannedCode, List<String> urls, String uid,
                           double balance, Done done) {
        double fee = Pricing.lateFee(b.rate, b.total, b.unit, b.endMs, System.currentTimeMillis());
        double charged = Math.min(fee, balance);

        Map<String, Object> upd = new HashMap<>();
        upd.put("status", Booking.RETURNED);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        upd.put("returnedAt", FieldValue.serverTimestamp());
        upd.put("scannedReturnCode", scannedCode);
        upd.put("photos.afterRenter", urls);
        upd.put("lateFee", fee);
        upd.put("lateFeeCharged", charged);

        WriteBatch batch = db.batch();
        batch.update(ref(b.id), upd);
        if (charged > 0) {
            DocumentReference wallet = db.collection("wallets").document(uid);
            batch.update(wallet, "balance", FieldValue.increment(-charged));
            batch.set(wallet.collection("tx").document(),
                    WalletRepository.txMap("late_fee", -charged, "Late fee: " + b.itemTitle, b.id));
        }
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Owner confirms the return with photos: returned -> completed, and collects the payout. */
    public void confirmReturn(Booking b, List<String> urls, String uid, Done done) {
        double payout = b.escrow + b.lateFeeCharged;
        DocumentReference wallet = db.collection("wallets").document(uid);

        Map<String, Object> upd = new HashMap<>();
        upd.put("status", Booking.COMPLETED);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        upd.put("completedAt", FieldValue.serverTimestamp());
        upd.put("payoutCollected", true);
        upd.put("photos.afterOwner", urls);

        WriteBatch batch = db.batch();
        batch.update(ref(b.id), upd);
        batch.update(wallet, "balance", FieldValue.increment(payout), "lastPayoutBooking", b.id);
        batch.set(wallet.collection("tx").document(),
                WalletRepository.txMap("payout", payout, "Payout: " + b.itemTitle, b.id));
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Renter closes a returned booking when the owner has not acted for 24 hours. */
    public void completeAfterDeadline(Booking b, Done done) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("status", Booking.COMPLETED);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        upd.put("completedAt", FieldValue.serverTimestamp());
        ref(b.id).update(upd)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Owner collects a payout that was not collected when the rental closed. */
    public void collectPayout(Booking b, String uid, Done done) {
        double payout = b.escrow + b.lateFeeCharged;
        DocumentReference wallet = db.collection("wallets").document(uid);

        Map<String, Object> upd = new HashMap<>();
        upd.put("payoutCollected", true);
        upd.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.update(ref(b.id), upd);
        batch.update(wallet, "balance", FieldValue.increment(payout), "lastPayoutBooking", b.id);
        batch.set(wallet.collection("tx").document(),
                WalletRepository.txMap("payout", payout, "Payout: " + b.itemTitle, b.id));
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Best effort: marks an in-use booking overdue after its due time. */
    public void markOverdue(Booking b) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("status", Booking.OVERDUE);
        upd.put("updatedAt", FieldValue.serverTimestamp());
        ref(b.id).update(upd);
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? "Failed" : e.getMessage();
    }
}
