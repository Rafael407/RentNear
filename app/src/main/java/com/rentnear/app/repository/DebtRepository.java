package com.rentnear.app.repository;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rentnear.app.model.Debt;
import com.rentnear.app.util.Done;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debts: pay from the wallet (which unfreezes the account when it was the last open debt), collect
 * a repaid debt, and ban an account whose 10-day deadline has passed.
 */
public class DebtRepository {
    /** Trust score given back when a debt is repaid. */
    public static final int SCORE_RECOVERY = 10;

    public interface ListListener {
        void onDebts(List<Debt> list);
        void onError(String message);
    }

    public interface BanCallback {
        /** banned is true when this call banned the signed-in account. */
        void onResult(boolean banned, String error);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public List<ListenerRegistration> listenMine(String uid, ListListener l) {
        Map<String, Debt> owed = new HashMap<>();
        Map<String, Debt> owing = new HashMap<>();
        List<ListenerRegistration> regs = new ArrayList<>();
        regs.add(listen("debtorId", uid, owing, owed, l));
        regs.add(listen("creditorId", uid, owed, owing, l));
        return regs;
    }

    private ListenerRegistration listen(String field, String uid, Map<String, Debt> mine,
                                        Map<String, Debt> other, ListListener l) {
        return db.collection("debts").whereEqualTo(field, uid).limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(e.getMessage() == null ? "Failed" : e.getMessage());
                        return;
                    }
                    mine.clear();
                    snap.getDocuments().forEach(d -> mine.put(d.getId(), Debt.from(d)));
                    Map<String, Debt> all = new HashMap<>(other);
                    all.putAll(mine);
                    List<Debt> list = new ArrayList<>(all.values());
                    Collections.sort(list, (x, y) -> Long.compare(y.createdMs, x.createdMs));
                    l.onDebts(list);
                });
    }

    /** Debtor pays the full amount from the wallet. */
    public void pay(Debt d, String uid, Done done) {
        DocumentReference wallet = db.collection("wallets").document(uid);
        DocumentReference user = db.collection("users").document(uid);
        Tasks.whenAllSuccess(wallet.get(), user.get()).addOnSuccessListener(res -> {
            DocumentSnapshot w = (DocumentSnapshot) res.get(0);
            DocumentSnapshot u = (DocumentSnapshot) res.get(1);
            Double bal = w.getDouble("balance");
            if (bal == null || bal < d.amount) {
                done.onDone("Not enough wallet balance. Add demo money in Profile > Wallet.");
                return;
            }
            Long open = u.getLong("openDebts");
            long remaining = (open == null ? 1 : open) - 1;

            Map<String, Object> debt = new HashMap<>();
            debt.put("status", "paid");
            debt.put("paidAt", FieldValue.serverTimestamp());

            WriteBatch batch = db.batch();
            batch.update(db.collection("debts").document(d.id), debt);
            batch.update(wallet, "balance", FieldValue.increment(-d.amount));
            batch.set(wallet.collection("tx").document(),
                    WalletRepository.txMap("debt", -d.amount, "Damage claim: " + d.itemTitle, d.bookingId));
            batch.update(user,
                    "frozen", remaining > 0,
                    "trustScore", FieldValue.increment(SCORE_RECOVERY),
                    "openDebts", FieldValue.increment(-1),
                    "lastDebtId", d.id);
            batch.commit()
                    .addOnSuccessListener(v -> done.onDone(null))
                    .addOnFailureListener(e -> done.onDone(msg(e)));
        }).addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Creditor takes a repaid debt into their wallet. */
    public void collect(Debt d, String uid, Done done) {
        DocumentReference wallet = db.collection("wallets").document(uid);
        Map<String, Object> debt = new HashMap<>();
        debt.put("collected", true);

        WriteBatch batch = db.batch();
        batch.update(db.collection("debts").document(d.id), debt);
        batch.update(wallet, "balance", FieldValue.increment(d.amount), "lastDebtCollected", d.id);
        batch.set(wallet.collection("tx").document(),
                WalletRepository.txMap("debt_in", d.amount, "Damage claim paid: " + d.itemTitle, d.bookingId));
        batch.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Bans a debtor whose deadline has passed. Allowed for the debtor's own app and the person owed. */
    public void ban(Debt d, Done done) {
        db.collection("users").document(d.debtorId)
                .update("banned", true, "lastDebtId", d.id)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Called when the app opens: if one of my debts is past its deadline, my account is banned. */
    public void enforceOverdue(String uid, BanCallback cb) {
        db.collection("debts").whereEqualTo("debtorId", uid).get()
                .addOnSuccessListener(snap -> {
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot s : snap.getDocuments()) {
                        Debt d = Debt.from(s);
                        if (d.isOverdue(now)) {
                            ban(d, err -> cb.onResult(err == null, err));
                            return;
                        }
                    }
                    cb.onResult(false, null);
                })
                .addOnFailureListener(e -> cb.onResult(false, msg(e)));
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? "Failed" : e.getMessage();
    }
}
