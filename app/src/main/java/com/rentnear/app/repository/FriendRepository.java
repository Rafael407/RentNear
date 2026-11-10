package com.rentnear.app.repository;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.rentnear.app.model.FriendRequest;
import com.rentnear.app.util.Done;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Friends: requests by email, accept or decline, and the friend list that decides who can see
 * Friends-only listings.
 */
public class FriendRepository {
    public interface IdsCallback {
        void onResult(Set<String> ids);
    }

    public interface RequestsListener {
        void onRequests(List<FriendRequest> list);
        void onError(String message);
    }

    /** uid -> name. */
    public interface FriendsListener {
        void onFriends(Map<String, String> friends);
        void onError(String message);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference friendDoc(String owner, String friend) {
        return db.collection("friends").document(owner).collection("list").document(friend);
    }

    /** Friend ids of a user, read once. On any failure the result is empty (only public items show). */
    public void friendIds(@Nullable String uid, IdsCallback cb) {
        if (uid == null) {
            cb.onResult(new HashSet<>());
            return;
        }
        db.collection("friends").document(uid).collection("list").get()
                .addOnSuccessListener(s -> {
                    Set<String> ids = new HashSet<>();
                    s.getDocuments().forEach(d -> ids.add(d.getId()));
                    cb.onResult(ids);
                })
                .addOnFailureListener(e -> cb.onResult(new HashSet<>()));
    }

    public ListenerRegistration listenFriends(String uid, FriendsListener l) {
        return db.collection("friends").document(uid).collection("list")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(msg(e));
                        return;
                    }
                    Map<String, String> m = new LinkedHashMap<>();
                    snap.getDocuments().forEach(d -> {
                        String n = d.getString("name");
                        m.put(d.getId(), n == null || n.isEmpty() ? "User" : n);
                    });
                    l.onFriends(m);
                });
    }

    /** Pending requests sent to me (incoming) or by me (outgoing). */
    public ListenerRegistration listenRequests(String uid, boolean incoming, RequestsListener l) {
        return db.collection("friendRequests").whereEqualTo(incoming ? "to" : "from", uid)
                .whereEqualTo("status", "pending").limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(msg(e));
                        return;
                    }
                    List<FriendRequest> list = new ArrayList<>();
                    snap.getDocuments().forEach(d -> list.add(FriendRequest.from(d)));
                    l.onRequests(list);
                });
    }

    /** Sends a request to the account with this email. */
    public void sendByEmail(String email, Done done) {
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        if (me == null) {
            done.onDone("Not signed in");
            return;
        }
        String wanted = email.trim().toLowerCase();
        if (wanted.isEmpty()) {
            done.onDone("Enter an email address.");
            return;
        }
        db.collection("users").whereEqualTo("email", wanted).limit(1).get().addOnSuccessListener(s -> {
            if (s.isEmpty()) {
                done.onDone("No account uses that email.");
                return;
            }
            String other = s.getDocuments().get(0).getId();
            String otherName = s.getDocuments().get(0).getString("name");
            if (other.equals(me.getUid())) {
                done.onDone("That is your own email.");
                return;
            }
            friendDoc(me.getUid(), other).get().addOnSuccessListener(f -> {
                if (f.exists()) {
                    done.onDone("You are already friends.");
                    return;
                }
                db.collection("friendRequests").document(other + "_" + me.getUid()).get()
                        .addOnSuccessListener(back -> {
                            if (back.exists() && "pending".equals(back.getString("status"))) {
                                done.onDone("They already sent you a request. Accept it below.");
                                return;
                            }
                            createRequest(me, other, otherName, done);
                        })
                        .addOnFailureListener(e -> done.onDone(msg(e)));
            }).addOnFailureListener(e -> done.onDone(msg(e)));
        }).addOnFailureListener(e -> done.onDone(msg(e)));
    }

    private void createRequest(FirebaseUser me, String other, @Nullable String otherName, Done done) {
        DocumentReference ref = db.collection("friendRequests").document(me.getUid() + "_" + other);
        ref.get().addOnSuccessListener(old -> {
            Map<String, Object> m = new HashMap<>();
            m.put("status", "pending");
            m.put("createdAt", FieldValue.serverTimestamp());
            if (old.exists()) {
                if ("pending".equals(old.getString("status"))) {
                    done.onDone("Request already sent.");
                    return;
                }
                ref.update(m).addOnSuccessListener(v -> done.onDone(null))
                        .addOnFailureListener(e -> done.onDone(msg(e)));
                return;
            }
            m.put("from", me.getUid());
            m.put("to", other);
            m.put("fromName", clip(me.getDisplayName()));
            m.put("toName", clip(otherName));
            ref.set(m).addOnSuccessListener(v -> done.onDone(null))
                    .addOnFailureListener(e -> done.onDone(msg(e)));
        }).addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Accepting creates both friend documents in the same batch as the status change. */
    public void accept(FriendRequest r, Done done) {
        Map<String, Object> mineForThem = new HashMap<>();
        mineForThem.put("name", clip(r.toName));
        mineForThem.put("since", FieldValue.serverTimestamp());
        Map<String, Object> theirsForMe = new HashMap<>();
        theirsForMe.put("name", clip(r.fromName));
        theirsForMe.put("since", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.update(db.collection("friendRequests").document(r.id), "status", "accepted");
        batch.set(friendDoc(r.to, r.from), theirsForMe);
        batch.set(friendDoc(r.from, r.to), mineForThem);
        batch.commit().addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    public void decline(FriendRequest r, Done done) {
        db.collection("friendRequests").document(r.id).update("status", "declined")
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    /** Cancels a request I sent. */
    public void cancel(FriendRequest r, Done done) {
        db.collection("friendRequests").document(r.id).delete()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    public void remove(String me, String friendUid, Done done) {
        WriteBatch batch = db.batch();
        batch.delete(friendDoc(me, friendUid));
        batch.delete(friendDoc(friendUid, me));
        batch.commit().addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(msg(e)));
    }

    private static String clip(@Nullable String s) {
        if (s == null || s.isEmpty()) return "User";
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String msg(Exception e) {
        return e.getMessage() == null ? "Failed" : e.getMessage();
    }
}
