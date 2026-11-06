package com.rentnear.app.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.util.Result;

/** Reads and creates users/{uid} documents. */
public class UserRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference doc(String uid) {
        return db.collection("users").document(uid);
    }

    /** Creates the profile document only if it does not exist yet (safe for repeat Google sign-ins). */
    public void ensureProfile(@NonNull String uid, String name, String email, String photoUrl,
                              MutableLiveData<Result<UserProfile>> out) {
        doc(uid).get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                out.setValue(Result.ok(UserProfile.from(snap)));
            } else {
                doc(uid).set(UserProfile.newUserMap(name, email, photoUrl))
                        .addOnSuccessListener(v -> doc(uid).get().addOnSuccessListener(
                                s -> out.setValue(Result.ok(UserProfile.from(s)))))
                        .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
            }
        }).addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public void load(@NonNull String uid, MutableLiveData<Result<UserProfile>> out) {
        doc(uid).get()
                .addOnSuccessListener(s -> {
                    if (s.exists()) out.setValue(Result.ok(UserProfile.from(s)));
                    else out.setValue(Result.fail("Profile not found"));
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }
}
