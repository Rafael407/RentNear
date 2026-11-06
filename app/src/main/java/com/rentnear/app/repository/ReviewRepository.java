package com.rentnear.app.repository;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.rentnear.app.model.Booking;
import com.rentnear.app.model.Review;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.util.Done;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviews and what they change. Writing a review is one transaction: the review itself, the
 * reviewee's trust score and points, the reviewer's own points, and (when the renter reviews) the
 * item's rating average. The rules check each of these against the new review.
 */
public class ReviewRepository {
    /** Points the reviewer earns for writing a review. */
    public static final int REVIEWER_POINTS = 5;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Trust score change for the reviewee: 5 stars +2, 4 +1, 3 0, 2 -1, 1 -2. */
    public static int scoreDelta(int rating) {
        return rating - 3;
    }

    /** Points the reviewee earns: 2 per star. */
    public static int revieweePoints(int rating) {
        return rating * 2;
    }

    public interface HasReviewed {
        void onResult(boolean reviewed, String error);
    }

    public void hasReviewed(String bookingId, String me, HasReviewed cb) {
        db.collection("reviews").document(Review.idFor(bookingId, me)).get()
                .addOnSuccessListener(s -> cb.onResult(s.exists(), null))
                .addOnFailureListener(e -> cb.onResult(false, e.getMessage()));
    }

    public void submit(Booking b, String me, int rating, String comment, Done done) {
        boolean asRenter = me.equals(b.renterId);
        String reviewee = asRenter ? b.ownerId : b.renterId;
        String reviewerName = asRenter ? b.renterName : b.ownerName;
        if (reviewerName == null || reviewerName.isEmpty()) reviewerName = "User";
        if (reviewerName.length() > 60) reviewerName = reviewerName.substring(0, 60);
        final String name = reviewerName;
        String id = Review.idFor(b.id, me);

        DocumentReference reviewRef = db.collection("reviews").document(id);
        DocumentReference revieweeRef = db.collection("users").document(reviewee);
        DocumentReference meRef = db.collection("users").document(me);
        DocumentReference itemRef = db.collection("items").document(b.itemId);

        db.runTransaction(tx -> {
            // All reads first.
            if (tx.get(reviewRef).exists()) throw new IllegalStateException("You already reviewed this booking.");
            double avg = 0;
            long count = 0;
            if (asRenter) {
                com.google.firebase.firestore.DocumentSnapshot item = tx.get(itemRef);
                Double a = item.getDouble("ratingAvg");
                Long c = item.getLong("ratingCount");
                avg = a == null ? 0 : a;
                count = c == null ? 0 : c;
            }

            Map<String, Object> m = new HashMap<>();
            m.put("bookingId", b.id);
            m.put("itemId", b.itemId);
            m.put("reviewerId", me);
            m.put("reviewerName", name);
            m.put("revieweeId", reviewee);
            m.put("role", asRenter ? "renter" : "owner");
            m.put("rating", rating);
            m.put("comment", comment == null ? "" : comment.trim());
            m.put("createdAt", FieldValue.serverTimestamp());
            tx.set(reviewRef, m);

            tx.update(revieweeRef,
                    "trustScore", FieldValue.increment(scoreDelta(rating)),
                    "points", FieldValue.increment(revieweePoints(rating)),
                    "lastReviewId", id);
            tx.update(meRef,
                    "points", FieldValue.increment(REVIEWER_POINTS),
                    "lastReviewId", id);
            if (asRenter) {
                long newCount = count + 1;
                double newAvg = (avg * count + rating) / newCount;
                tx.update(itemRef, "ratingAvg", newAvg, "ratingCount", newCount, "lastReviewId", id);
            }
            return null;
        }).addOnSuccessListener(v -> done.onDone(null))
          .addOnFailureListener(e -> done.onDone(e.getMessage()));
    }

    public void forItem(String itemId, MutableLiveData<Result<List<Review>>> out) {
        load(db.collection("reviews").whereEqualTo("itemId", itemId), out, "renter");
    }

    /** Reviews written about a person (as owner or as renter). */
    public void forUser(String uid, MutableLiveData<Result<List<Review>>> out) {
        load(db.collection("reviews").whereEqualTo("revieweeId", uid), out, null);
    }

    // Sorted on the device so no composite index has to be created in the console.
    private void load(Query q, MutableLiveData<Result<List<Review>>> out, String onlyRole) {
        q.limit(100).get()
                .addOnSuccessListener(snap -> {
                    List<Review> list = new ArrayList<>();
                    snap.getDocuments().forEach(d -> {
                        Review r = Review.from(d);
                        if (onlyRole == null || onlyRole.equals(r.role)) list.add(r);
                    });
                    Collections.sort(list, (x, y) -> Long.compare(y.createdMs, x.createdMs));
                    out.setValue(Result.ok(list));
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    /** Top users by points or trust score. Banned accounts are hidden. */
    public void leaderboard(boolean byPoints, MutableLiveData<Result<List<UserProfile>>> out) {
        db.collection("users").orderBy(byPoints ? "points" : "trustScore", Query.Direction.DESCENDING)
                .limit(30).get()
                .addOnSuccessListener(snap -> {
                    List<UserProfile> list = new ArrayList<>();
                    snap.getDocuments().forEach(d -> {
                        UserProfile u = UserProfile.from(d);
                        if (!u.banned) list.add(u);
                    });
                    out.setValue(Result.ok(list));
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }
}
