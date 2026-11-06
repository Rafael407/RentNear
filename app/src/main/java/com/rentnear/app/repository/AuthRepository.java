package com.rentnear.app.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.util.Result;

/** Firebase Auth: email/password, Google, email verification. */
public class AuthRepository {
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final UserRepository users = new UserRepository();

    @Nullable
    public FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

    public void signUp(String name, String email, String password,
                       MutableLiveData<Result<UserProfile>> out) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> {
                    FirebaseUser u = r.getUser();
                    if (u == null) {
                        out.setValue(Result.fail("Sign-up failed"));
                        return;
                    }
                    u.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(name).build());
                    u.sendEmailVerification();
                    users.ensureProfile(u.getUid(), name, email, null, out);
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public void signIn(String email, String password, MutableLiveData<Result<UserProfile>> out) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> users.load(r.getUser().getUid(), out))
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public void signInWithGoogle(String idToken, MutableLiveData<Result<UserProfile>> out) {
        AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(cred)
                .addOnSuccessListener(r -> {
                    FirebaseUser u = r.getUser();
                    if (u == null) {
                        out.setValue(Result.fail("Google sign-in failed"));
                        return;
                    }
                    String photo = u.getPhotoUrl() == null ? null : u.getPhotoUrl().toString();
                    users.ensureProfile(u.getUid(),
                            u.getDisplayName() == null ? "User" : u.getDisplayName(),
                            u.getEmail(), photo, out);
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public void resendVerification(MutableLiveData<Result<Boolean>> out) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            out.setValue(Result.fail("Not signed in"));
            return;
        }
        u.sendEmailVerification()
                .addOnSuccessListener(v -> out.setValue(Result.ok(true)))
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    /** Reloads the user from the server and reports whether the email is now verified. */
    public void checkVerified(MutableLiveData<Result<Boolean>> out) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            out.setValue(Result.fail("Not signed in"));
            return;
        }
        u.reload()
                .addOnSuccessListener(v -> out.setValue(Result.ok(u.isEmailVerified())))
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    /** Loads the profile of an already signed-in user (auto-login on app start). */
    public void restoreSession(MutableLiveData<Result<UserProfile>> out) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        users.load(u.getUid(), out);
    }

    public void signOut() {
        auth.signOut();
    }
}
