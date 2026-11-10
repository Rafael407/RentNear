package com.rentnear.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** claims/{bookingId}: the owner says the returned item was damaged and asks for money. */
public class Claim {
    public static final String OPEN = "open";
    public static final String OFFERED = "offered";
    public static final String ACCEPTED = "accepted";
    public static final String DISPUTED = "disputed";
    public static final String WITHDRAWN = "withdrawn";
    public static final String UPHELD = "upheld";
    public static final String REJECTED = "rejected";

    public String id;
    public String bookingId;
    public String itemId;
    public String itemTitle;
    public String ownerId;
    public String ownerName;
    public String renterId;
    public String renterName;
    public double amount;
    public double declaredValue;
    public double partialOffer;
    public double resolvedAmount;
    public String reason;
    public String status;
    public long createdMs;

    public static Claim from(DocumentSnapshot d) {
        Claim c = new Claim();
        c.id = d.getId();
        c.bookingId = d.getString("bookingId");
        c.itemId = d.getString("itemId");
        c.itemTitle = d.getString("itemTitle");
        c.ownerId = d.getString("ownerId");
        c.ownerName = d.getString("ownerName");
        c.renterId = d.getString("renterId");
        c.renterName = d.getString("renterName");
        c.amount = num(d.getDouble("amount"));
        c.declaredValue = num(d.getDouble("declaredValue"));
        c.partialOffer = num(d.getDouble("partialOffer"));
        c.resolvedAmount = num(d.getDouble("resolvedAmount"));
        c.reason = d.getString("reason");
        c.status = d.getString("status");
        Timestamp t = d.getTimestamp("createdAt");
        c.createdMs = t == null ? 0 : t.toDate().getTime();
        return c;
    }

    private static double num(Double v) {
        return v == null ? 0 : v;
    }

    public String statusLabel() {
        switch (status == null ? "" : status) {
            case OPEN: return "Waiting for renter";
            case OFFERED: return "Partial offer made";
            case ACCEPTED: return "Accepted (debt created)";
            case DISPUTED: return "Disputed (admin will decide)";
            case WITHDRAWN: return "Withdrawn";
            case UPHELD: return "Upheld by admin";
            case REJECTED: return "Rejected by admin";
            default: return "Unknown";
        }
    }

    public String otherName(String me) {
        String n = me.equals(ownerId) ? renterName : ownerName;
        return n == null || n.isEmpty() ? "User" : n;
    }
}
