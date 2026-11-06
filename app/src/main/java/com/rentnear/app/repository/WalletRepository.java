package com.rentnear.app.repository;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.rentnear.app.model.WalletTx;
import com.rentnear.app.util.Done;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Simulated wallet: demo money only, no real payments. */
public class WalletRepository {
    public static final double STARTING_CREDIT = 2000.0;
    private static final int TX_LIMIT = 30;

    public interface BalanceListener {
        void onBalance(double balance);
        void onError(String message);
    }

    public interface TxListener {
        void onTx(List<WalletTx> list);
        void onError(String message);
    }

    public interface BalanceOnce {
        void onResult(@Nullable Double balance, @Nullable String error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference wallet(String uid) {
        return db.collection("wallets").document(uid);
    }

    public static Map<String, Object> txMap(String type, double amount, String label,
                                            @Nullable String bookingId) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("amount", amount);
        m.put("label", label);
        if (bookingId != null) m.put("bookingId", bookingId);
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    /** Creates the wallet with starting demo credit the first time a user opens the app. */
    public void ensure(String uid) {
        wallet(uid).get().addOnSuccessListener(snap -> {
            if (snap.exists()) return;
            Map<String, Object> w = new HashMap<>();
            w.put("balance", STARTING_CREDIT);
            w.put("createdAt", FieldValue.serverTimestamp());
            WriteBatch b = db.batch();
            b.set(wallet(uid), w);
            b.set(wallet(uid).collection("tx").document(),
                    txMap("welcome", STARTING_CREDIT, "Welcome credit (demo money)", null));
            b.commit();
        });
    }

    public ListenerRegistration listenBalance(String uid, BalanceListener l) {
        return wallet(uid).addSnapshotListener((snap, e) -> {
            if (e != null) {
                l.onError(e.getMessage());
                return;
            }
            if (snap == null || !snap.exists()) return;
            Double bal = snap.getDouble("balance");
            l.onBalance(bal == null ? 0 : bal);
        });
    }

    public ListenerRegistration listenTx(String uid, TxListener l) {
        return wallet(uid).collection("tx")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(TX_LIMIT)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(e.getMessage());
                        return;
                    }
                    List<WalletTx> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) list.add(WalletTx.from(d));
                    }
                    l.onTx(list);
                });
    }

    public void getBalance(String uid, BalanceOnce cb) {
        wallet(uid).get()
                .addOnSuccessListener(s -> {
                    if (!s.exists()) {
                        cb.onResult(null, "Wallet not ready yet. Try again in a moment.");
                        return;
                    }
                    Double bal = s.getDouble("balance");
                    cb.onResult(bal == null ? 0 : bal, null);
                })
                .addOnFailureListener(e -> cb.onResult(null, e.getMessage()));
    }

    /** Adds demo money. The rules allow at most 5000 per write. */
    public void topUp(String uid, double amount, Done done) {
        WriteBatch b = db.batch();
        b.update(wallet(uid), "balance", FieldValue.increment(amount));
        b.set(wallet(uid).collection("tx").document(),
                txMap("topup", amount, "Added demo money", null));
        b.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(e.getMessage() == null ? "Failed" : e.getMessage()));
    }
}
