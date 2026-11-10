package com.rentnear.app.repository;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rentnear.app.model.Booking;
import com.rentnear.app.model.Claim;
import com.rentnear.app.model.Debt;
import com.rentnear.app.util.Done;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Damage claims. The owner files one within 24 hours of the return; the renter accepts, disputes
 * or offers less. Accepting (by the renter, or by the owner for a partial offer) creates the debt
 * and freezes the debtor in the same batch; the rules check the three writes together.
 */
public class ClaimRepository {
    /** Trust score lost when a debt starts. */
    public static final int SCORE_PENALTY = 20;
    public static final long CLAIM_WINDOW_MS = 24L * 3600 * 1000;

    public interface ListListener {
        void onClaims(List<Claim> list);
        void onError(String message);
    }

    public interface OneListener {
        void onClaim(@Nullable Claim c);
        void onError(String message);
    }

    public interface FindCallback {
        void onResult(@Nullable Claim claim, @Nullable String error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference ref(String id) {
        return db.collection("claims").document(id);
    }

    /** True while the owner can still file a claim for this booking. */
    public static boolean canFile(Booking b) {
        return (Booking.RETURNED.equals(b.status) || Booking.COMPLETED.equals(b.status))
                && b.returnedAtMs > 0
                && System.currentTimeMillis() <= b.returnedAtMs + CLAIM_WINDOW_MS;
    }

    public void create(Booking b, double amount, String reason, Done done) {
        Map<String, Object> m = new HashMap<>();
        m.put("bookingId", b.id);
        m.put("itemId", b.itemId);
        m.put("itemTitle", b.itemTitle == null ? "" : b.itemTitle);
        m.put("ownerId", b.ownerId);
        m.put("ownerName", b.ownerName == null ? "" : b.ownerName);
        m.put("renterId", b.renterId);
        m.put("renterName", b.renterName == null ? "" : b.renterName);
        m.put("amount", amount);
        m.put("declaredValue", b.declaredValue);
        m.put("reason", reason.trim());
        m.put("status", Claim.OPEN);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        ref(b.id).set(m)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Finds the claim for a booking. Filtering by my own id is what lets the rules allow the query. */
    public void find(Booking b, String me, FindCallback cb) {
        String field = me.equals(b.ownerId) ? "ownerId" : "renterId";
        db.collection("claims").whereEqualTo("bookingId", b.id).whereEqualTo(field, me).limit(1).get()
                .addOnSuccessListener(s -> cb.onResult(
                        s.isEmpty() ? null : Claim.from(s.getDocuments().get(0)), null))
                .addOnFailureListener(e -> cb.onResult(null, msg(e)));
    }

    public ListenerRegistration listenOne(String id, OneListener l) {
        return ref(id).addSnapshotListener((s, e) -> {
            if (e != null) {
                l.onError(msg(e));
                return;
            }
            l.onClaim(s == null || !s.exists() ? null : Claim.from(s));
        });
    }

    /** Claims where I am the owner or the renter, newest first. */
    public List<ListenerRegistration> listenMine(String uid, ListListener l) {
        Map<String, Claim> owner = new HashMap<>();
        Map<String, Claim> renter = new HashMap<>();
        List<ListenerRegistration> regs = new ArrayList<>();
        regs.add(listen("ownerId", uid, owner, renter, l));
        regs.add(listen("renterId", uid, renter, owner, l));
        return regs;
    }

    private ListenerRegistration listen(String field, String uid, Map<String, Claim> mine,
                                        Map<String, Claim> other, ListListener l) {
        return db.collection("claims").whereEqualTo(field, uid).limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(msg(e));
                        return;
                    }
                    mine.clear();
                    snap.getDocuments().forEach(d -> mine.put(d.getId(), Claim.from(d)));
                    Map<String, Claim> all = new HashMap<>(other);
                    all.putAll(mine);
                    List<Claim> list = new ArrayList<>(all.values());
                    Collections.sort(list, (x, y) -> Long.compare(y.createdMs, x.createdMs));
                    l.onClaims(list);
                });
    }

    // ------------------------------------------------------------------ responses

    /** Renter accepts the full amount: debt created, account frozen. */
    public void accept(Claim c, Done done) {
        resolve(c, c.amount, done);
    }

    /** Owner accepts the renter's smaller offer: debt for the offered amount. */
    public void acceptOffer(Claim c, Done done) {
        resolve(c, c.partialOffer, done);
    }

    private void resolve(Claim c, double amount, Done done) {
        long now = System.currentTimeMillis();
        Map<String, Object> debt = new HashMap<>();
        debt.put("claimId", c.id);
        debt.put("bookingId", c.bookingId);
        debt.put("itemTitle", c.itemTitle == null ? "" : c.itemTitle);
        debt.put("debtorId", c.renterId);
        debt.put("debtorName", c.renterName == null ? "" : c.renterName);
        debt.put("creditorId", c.ownerId);
        debt.put("creditorName", c.ownerName == null ? "" : c.ownerName);
        debt.put("amount", amount);
        debt.put("status", "open");
        debt.put("collected", false);
        debt.put("dueAt", new Timestamp(new Date(now + Debt.DEADLINE_MS)));
        debt.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> claim = new HashMap<>();
        claim.put("status", Claim.ACCEPTED);
        claim.put("resolvedAmount", amount);
        claim.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.update(ref(c.id), claim);
        batch.set(db.collection("debts").document(c.id), debt);
        batch.update(db.collection("users").document(c.renterId),
                "frozen", true,
                "pastDamage", true,
                "trustScore", FieldValue.increment(-SCORE_PENALTY),
                "openDebts", FieldValue.increment(1),
                "lastDebtId", c.id);
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    public void dispute(Claim c, Done done) {
        setStatus(c, Claim.DISPUTED, done);
    }

    public void rejectOffer(Claim c, Done done) {
        setStatus(c, Claim.DISPUTED, done);
    }

    public void withdraw(Claim c, Done done) {
        setStatus(c, Claim.WITHDRAWN, done);
    }

    private void setStatus(Claim c, String status, Done done) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("updatedAt", FieldValue.serverTimestamp());
        ref(c.id).update(m)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    public void offerPartial(Claim c, double amount, Done done) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", Claim.OFFERED);
        m.put("partialOffer", amount);
        m.put("updatedAt", FieldValue.serverTimestamp());
        ref(c.id).update(m)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? "Failed" : e.getMessage();
    }
}
