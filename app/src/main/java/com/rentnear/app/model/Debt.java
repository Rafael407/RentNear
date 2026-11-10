package com.rentnear.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** debts/{claimId}: money a renter owes an owner after a claim. Due 10 days after it is created. */
public class Debt {
    public static final long DEADLINE_MS = 10L * 24 * 3600 * 1000;

    public String id;
    public String bookingId;
    public String itemTitle;
    public String debtorId;
    public String debtorName;
    public String creditorId;
    public String creditorName;
    public double amount;
    public boolean paid;
    public boolean collected;
    public long dueMs;
    public long createdMs;

    public static Debt from(DocumentSnapshot d) {
        Debt x = new Debt();
        x.id = d.getId();
        x.bookingId = d.getString("bookingId");
        x.itemTitle = d.getString("itemTitle");
        x.debtorId = d.getString("debtorId");
        x.debtorName = d.getString("debtorName");
        x.creditorId = d.getString("creditorId");
        x.creditorName = d.getString("creditorName");
        Double a = d.getDouble("amount");
        x.amount = a == null ? 0 : a;
        x.paid = "paid".equals(d.getString("status"));
        x.collected = Boolean.TRUE.equals(d.getBoolean("collected"));
        Timestamp due = d.getTimestamp("dueAt");
        x.dueMs = due == null ? 0 : due.toDate().getTime();
        Timestamp c = d.getTimestamp("createdAt");
        x.createdMs = c == null ? 0 : c.toDate().getTime();
        return x;
    }

    public boolean isOverdue(long nowMs) {
        return !paid && dueMs > 0 && nowMs > dueMs;
    }

    /** Whole days left, rounded up; 0 or less when overdue. */
    public long daysLeft(long nowMs) {
        long left = dueMs - nowMs;
        return left <= 0 ? 0 : (left + 86_399_999L) / 86_400_000L;
    }
}
