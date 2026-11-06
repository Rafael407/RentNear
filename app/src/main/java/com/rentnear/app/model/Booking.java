package com.rentnear.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** bookings/{id}: item, owner, renter, dates, price, status. */
public class Booking {
    public static final String REQUESTED = "requested";
    public static final String ACCEPTED = "accepted";
    public static final String DECLINED = "declined";
    public static final String CANCELLED = "cancelled";
    public static final String PAID = "paid";
    public static final String HANDED_OVER = "handed_over";
    public static final String RETURNED = "returned";
    public static final String COMPLETED = "completed";
    public static final String OVERDUE = "overdue";
    public static final String DISPUTED = "disputed";

    public String id;
    public String itemId;
    public String itemTitle;
    public String itemPhoto;
    public String ownerId;
    public String ownerName;
    public String renterId;
    public String renterName;
    public String unit;
    public String status;
    public String note;
    public long startMs;
    public long endMs;
    public long createdAtMs;
    public double rate;
    public double total;
    public double escrow;
    public double declaredValue;
    public int units;

    // Step 4b
    public long handedOverAtMs;
    public long returnedAtMs;
    public long completedAtMs;
    public double lateFee;
    public double lateFeeCharged;
    public boolean payoutCollected;
    /** Condition photos by key: beforeOwner, beforeRenter, afterRenter, afterOwner. */
    public final Map<String, List<String>> photos = new HashMap<>();

    public boolean hasPhotos(String key) {
        List<String> l = photos.get(key);
        return l != null && !l.isEmpty();
    }

    /** In use and past the due time. */
    public boolean isPastDue() {
        return (HANDED_OVER.equals(status) || OVERDUE.equals(status))
                && System.currentTimeMillis() > endMs;
    }

    @SuppressWarnings("unchecked")
    public static Booking from(DocumentSnapshot d) {
        Booking b = new Booking();
        b.id = d.getId();
        b.itemId = d.getString("itemId");
        b.itemTitle = d.getString("itemTitle");
        b.itemPhoto = d.getString("itemPhoto");
        b.ownerId = d.getString("ownerId");
        b.ownerName = d.getString("ownerName");
        b.renterId = d.getString("renterId");
        b.renterName = d.getString("renterName");
        b.unit = d.getString("unit");
        b.status = d.getString("status");
        b.note = d.getString("note");
        b.startMs = millis(d.getTimestamp("start", ServerTimestampBehavior.ESTIMATE));
        b.endMs = millis(d.getTimestamp("end", ServerTimestampBehavior.ESTIMATE));
        b.createdAtMs = millis(d.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE));
        b.rate = num(d.getDouble("rate"));
        b.total = num(d.getDouble("total"));
        b.escrow = num(d.getDouble("escrow"));
        b.declaredValue = num(d.getDouble("declaredValue"));
        Long u = d.getLong("units");
        b.units = u == null ? 1 : u.intValue();

        b.handedOverAtMs = millis(d.getTimestamp("handedOverAt", ServerTimestampBehavior.ESTIMATE));
        b.returnedAtMs = millis(d.getTimestamp("returnedAt", ServerTimestampBehavior.ESTIMATE));
        b.completedAtMs = millis(d.getTimestamp("completedAt", ServerTimestampBehavior.ESTIMATE));
        b.lateFee = num(d.getDouble("lateFee"));
        b.lateFeeCharged = num(d.getDouble("lateFeeCharged"));
        b.payoutCollected = Boolean.TRUE.equals(d.getBoolean("payoutCollected"));
        Object p = d.get("photos");
        if (p instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) p).entrySet()) {
                List<String> urls = new ArrayList<>();
                if (e.getValue() instanceof List) {
                    for (Object o : (List<Object>) e.getValue()) if (o != null) urls.add(o.toString());
                }
                b.photos.put(e.getKey(), urls);
            }
        }
        return b;
    }

    public static Map<String, Object> newMap(Item item, String renterId, String renterName,
                                             long startMs, long endMs, double rate, int units,
                                             String note) {
        Map<String, Object> m = new HashMap<>();
        m.put("itemId", item.id);
        m.put("itemTitle", item.title);
        m.put("itemPhoto", item.firstPhoto() == null ? "" : item.firstPhoto());
        m.put("ownerId", item.ownerId);
        m.put("ownerName", item.ownerName == null ? "User" : item.ownerName);
        m.put("renterId", renterId);
        m.put("renterName", renterName);
        m.put("chatId", item.id + "_" + renterId);
        m.put("unit", item.priceUnit);
        m.put("status", REQUESTED);
        m.put("note", note);
        m.put("start", new Timestamp(new Date(startMs)));
        m.put("end", new Timestamp(new Date(endMs)));
        m.put("rate", rate);
        m.put("units", units);
        m.put("total", rate * units);
        m.put("declaredValue", item.declaredValue);
        m.put("createdAt", FieldValue.serverTimestamp());
        m.put("updatedAt", FieldValue.serverTimestamp());
        return m;
    }

    /** True once the owner has agreed, so the item is not free for other bookings in this window. */
    public boolean blocksDates() {
        return ACCEPTED.equals(status) || PAID.equals(status)
                || HANDED_OVER.equals(status) || OVERDUE.equals(status);
    }

    public String otherName(String me) {
        String n = me.equals(ownerId) ? renterName : ownerName;
        return n == null || n.isEmpty() ? "User" : n;
    }

    public String statusLabel() {
        switch (status == null ? "" : status) {
            case REQUESTED: return "Requested";
            case ACCEPTED: return "Accepted - awaiting payment";
            case DECLINED: return "Declined";
            case CANCELLED: return "Cancelled";
            case PAID: return "Paid";
            case HANDED_OVER: return isPastDue() ? "Overdue" : "In use";
            case RETURNED: return "Returned";
            case COMPLETED: return "Completed";
            case OVERDUE: return "Overdue";
            case DISPUTED: return "Disputed";
            default: return status;
        }
    }

    /** Colour for the status label. */
    public int statusColor() {
        if (isPastDue()) return 0xFFB3261E;
        switch (status == null ? "" : status) {
            case ACCEPTED:
            case PAID:
            case HANDED_OVER:
            case COMPLETED:
                return 0xFF0F766E;
            case DECLINED:
            case CANCELLED:
                return 0xFF6B7280;
            case OVERDUE:
            case DISPUTED:
                return 0xFFB3261E;
            default:
                return 0xFFB45309;
        }
    }

    private static long millis(Timestamp t) {
        return t == null ? 0 : t.toDate().getTime();
    }

    private static double num(Double d) {
        return d == null ? 0 : d;
    }
}
