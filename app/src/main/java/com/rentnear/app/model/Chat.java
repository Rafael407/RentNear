package com.rentnear.app.model;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;
import com.google.firebase.Timestamp;

import java.util.Map;

/** chats/{itemId_renterId}: participants, item summary, last message preview, unread counters. */
public class Chat {
    public String id;
    public String itemId;
    public String itemTitle;
    public String itemPhoto;
    public String itemUnit;     // "hour" or "day": the unit offers are made in
    public String ownerId;
    public String renterId;
    public String ownerName;
    public String renterName;
    public String lastMessage;
    public String lastSenderId;
    public long lastMessageAtMs;
    public long unreadOwner;
    public long unreadRenter;

    /** Price both sides agreed in chat (0 if none). Booking in step 4 starts from this. */
    public double acceptedAmount;
    public String acceptedUnit;

    @SuppressWarnings("unchecked")
    public static Chat from(DocumentSnapshot d) {
        Chat c = new Chat();
        c.id = d.getId();
        c.itemId = d.getString("itemId");
        c.itemTitle = d.getString("itemTitle");
        c.itemPhoto = d.getString("itemPhoto");
        c.itemUnit = d.getString("itemUnit") == null ? "day" : d.getString("itemUnit");
        c.ownerId = d.getString("ownerId");
        c.renterId = d.getString("renterId");
        c.ownerName = d.getString("ownerName");
        c.renterName = d.getString("renterName");
        c.lastMessage = d.getString("lastMessage");
        c.lastSenderId = d.getString("lastSenderId");
        Timestamp t = d.getTimestamp("lastMessageAt", ServerTimestampBehavior.ESTIMATE);
        c.lastMessageAtMs = t == null ? 0 : t.toDate().getTime();

        Object u = d.get("unread");
        if (u instanceof Map) {
            Map<String, Object> um = (Map<String, Object>) u;
            c.unreadOwner = asLong(um.get(c.ownerId));
            c.unreadRenter = asLong(um.get(c.renterId));
        }
        Object a = d.get("acceptedOffer");
        if (a instanceof Map) {
            Map<String, Object> am = (Map<String, Object>) a;
            Object amt = am.get("amount");
            c.acceptedAmount = amt instanceof Number ? ((Number) amt).doubleValue() : 0;
            c.acceptedUnit = am.get("unit") == null ? null : am.get("unit").toString();
        }
        return c;
    }

    public String otherUid(String me) {
        return me.equals(ownerId) ? renterId : ownerId;
    }

    public String otherName(String me) {
        String n = me.equals(ownerId) ? renterName : ownerName;
        return n == null || n.isEmpty() ? "User" : n;
    }

    public long unreadFor(String me) {
        return me.equals(ownerId) ? unreadOwner : unreadRenter;
    }

    private static long asLong(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0;
    }
}
