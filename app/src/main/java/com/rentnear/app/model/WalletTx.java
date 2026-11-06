package com.rentnear.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;

/** wallets/{uid}/tx/{id}: one wallet movement. Amount is signed (negative = money out). */
public class WalletTx {
    public String id;
    public String type;
    public String label;
    public String bookingId;
    public double amount;
    public long createdAtMs;

    public static WalletTx from(DocumentSnapshot d) {
        WalletTx t = new WalletTx();
        t.id = d.getId();
        t.type = d.getString("type");
        t.label = d.getString("label");
        t.bookingId = d.getString("bookingId");
        Double a = d.getDouble("amount");
        t.amount = a == null ? 0 : a;
        Timestamp ts = d.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE);
        t.createdAtMs = ts == null ? System.currentTimeMillis() : ts.toDate().getTime();
        return t;
    }
}
