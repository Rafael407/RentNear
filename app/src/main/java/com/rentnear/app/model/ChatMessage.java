package com.rentnear.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior;

import java.util.Map;

/** chats/{chatId}/messages/{id}: senderId, type (text|image|offer), body, offer{}. */
public class ChatMessage {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_OFFER = "offer";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_COUNTERED = "countered";

    public static final String KIND_OFFER = "offer";
    public static final String KIND_COUNTER = "counter";

    public String id;
    public String senderId;
    public String type;
    public String body;      // text, or image URL
    public long createdAtMs;

    // Offer fields (type == offer)
    public double offerAmount;
    public String offerUnit;
    public String offerStatus;
    public String offerKind;

    @SuppressWarnings("unchecked")
    public static ChatMessage from(DocumentSnapshot d) {
        ChatMessage m = new ChatMessage();
        m.id = d.getId();
        m.senderId = d.getString("senderId");
        m.type = d.getString("type");
        m.body = d.getString("body");
        Timestamp t = d.getTimestamp("createdAt", ServerTimestampBehavior.ESTIMATE);
        m.createdAtMs = t == null ? System.currentTimeMillis() : t.toDate().getTime();
        Object o = d.get("offer");
        if (o instanceof Map) {
            Map<String, Object> om = (Map<String, Object>) o;
            Object amt = om.get("amount");
            m.offerAmount = amt instanceof Number ? ((Number) amt).doubleValue() : 0;
            m.offerUnit = str(om.get("unit"));
            m.offerStatus = str(om.get("status"));
            m.offerKind = str(om.get("kind"));
        }
        return m;
    }

    public boolean isOffer() {
        return TYPE_OFFER.equals(type);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
