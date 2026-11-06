package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseUser;
import com.rentnear.app.model.UserProfile;
import com.rentnear.app.repository.AuthRepository;
import com.rentnear.app.util.Result;

/** Shared by the login, signup and verify screens. */
public class AuthViewModel extends ViewModel {
    private final AuthRepository repo = new AuthRepository();

    private final MutableLiveData<Result<UserProfile>> authResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> verifyResult = new MutableLiveData<>();
    private final MutableLiveData<Result<Boolean>> resendResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public LiveData<Result<UserProfile>> authResult() { return authResult; }
    public LiveData<Result<Boolean>> verifyResult() { return verifyResult; }
    public LiveData<Result<Boolean>> resendResult() { return resendResult; }
    public LiveData<Boolean> loading() { return loading; }

    public FirebaseUser currentUser() { return repo.currentUser(); }

    public void signUp(String name, String email, String password) {
        loading.setValue(true);
        repo.signUp(name, email, password, authResult);
    }

    public void signIn(String email, String password) {
        loading.setValue(true);
        repo.signIn(email, password, authResult);
    }

    public void signInWithGoogle(String idToken) {
        loading.setValue(true);
        repo.signInWithGoogle(idToken, authResult);
    }

    public void restoreSession() {
        loading.setValue(true);
        repo.restoreSession(authResult);
    }

    public void checkVerified() { repo.checkVerified(verifyResult); }

    public void resendVerification() { repo.resendVerification(resendResult); }

    public void signOut() { repo.signOut(); }

    /** Call after consuming an authResult so a rotation does not replay it. */
    public void clearAuthResult() {
        authResult.setValue(null);
        loading.setValue(false);
    }
}
