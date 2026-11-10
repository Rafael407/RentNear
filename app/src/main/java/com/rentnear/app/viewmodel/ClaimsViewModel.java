package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.Claim;
import com.rentnear.app.repository.ClaimRepository;

import java.util.List;

/** My claims, as owner or renter. */
public class ClaimsViewModel extends ViewModel {
    private final ClaimRepository repo = new ClaimRepository();
    private final MutableLiveData<List<Claim>> claims = new MutableLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private List<ListenerRegistration> regs;

    public LiveData<List<Claim>> claims() { return claims; }
    public LiveData<String> error() { return error; }

    public void start() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (regs != null || uid == null) return;
        regs = repo.listenMine(uid, new ClaimRepository.ListListener() {
            @Override
            public void onClaims(List<Claim> list) {
                claims.setValue(list);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    public void stop() {
        if (regs != null) {
            for (ListenerRegistration r : regs) r.remove();
            regs = null;
        }
    }

    public void clearError() { error.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
