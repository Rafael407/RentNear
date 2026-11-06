package com.rentnear.app.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/** reviews/{bookingId_reviewerUid}: one review per person per completed booking. */
public class Review {
    public String id;
    public String bookingId;
    public String itemId;
    public String reviewerId;
    public String reviewerName;
    public String revieweeId;
    /** "renter" = the renter reviewed the owner and item; "owner" = the owner reviewed the renter. */
    public String role;
    public int rating;
    public String comment;
    public long createdMs;

    public static Review from(DocumentSnapshot d) {
        Review r = new Review();
        r.id = d.getId();
        r.bookingId = d.getString("bookingId");
        r.itemId = d.getString("itemId");
        r.reviewerId = d.getString("reviewerId");
        r.reviewerName = d.getString("reviewerName");
        r.revieweeId = d.getString("revieweeId");
        r.role = d.getString("role");
        Long n = d.getLong("rating");
        r.rating = n == null ? 0 : n.intValue();
        r.comment = d.getString("comment");
        Timestamp t = d.getTimestamp("createdAt");
        r.createdMs = t == null ? 0 : t.toDate().getTime();
        return r;
    }

    public static String idFor(String bookingId, String reviewerUid) {
        return bookingId + "_" + reviewerUid;
    }
}
