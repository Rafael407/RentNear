package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.Claim;
import com.rentnear.app.repository.ClaimRepository;
import com.rentnear.app.util.Done;

/** One claim, live, with the actions each side can take. */
public class ClaimViewModel extends ViewModel {
    private final ClaimRepository repo = new ClaimRepository();
    private final String me;
    private final MutableLiveData<Claim> claim = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();
    private String claimId;
    private ListenerRegistration registration;

    public ClaimViewModel() {
        String uid = FirebaseAuth.getInstance().getUid();
        me = uid == null ? "" : uid;
    }

    public String me() { return me; }
    public LiveData<Claim> claim() { return claim; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<String> error() { return error; }
    public LiveData<String> info() { return info; }

    public void init(String id) {
        if (claimId == null) claimId = id;
    }

    public void start() {
        if (claimId == null || registration != null) return;
        registration = repo.listenOne(claimId, new ClaimRepository.OneListener() {
            @Override
            public void onClaim(Claim c) {
                if (c == null) error.setValue("Claim not found");
                else claim.setValue(c);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private Done finish(String message) {
        return err -> {
            busy.setValue(false);
            if (err != null) error.setValue(err);
            else info.setValue(message);
        };
    }

    public void accept() {
        Claim c = claim.getValue();
        if (c == null) return;
        busy.setValue(true);
        repo.accept(c, finish("Claim accepted. A debt was created; pay it within 10 days."));
    }

    public void dispute() {
        Claim c = claim.getValue();
        if (c == null) return;
        busy.setValue(true);
        repo.dispute(c, finish("Claim disputed. An admin will review it."));
    }

    public void offerPartial(double amount) {
        Claim c = claim.getValue();
        if (c == null) return;
        if (amount <= 0 || amount >= c.amount) {
            error.setValue("Offer must be more than 0 and less than the claim.");
            return;
        }
        busy.setValue(true);
        repo.offerPartial(c, amount, finish("Offer sent to the owner."));
    }

    public void acceptOffer() {
        Claim c = claim.getValue();
        if (c == null) return;
        busy.setValue(true);
        repo.acceptOffer(c, finish("Offer accepted. A debt was created for the renter."));
    }

    public void rejectOffer() {
        Claim c = claim.getValue();
        if (c == null) return;
        busy.setValue(true);
        repo.rejectOffer(c, finish("Offer rejected. An admin will review the claim."));
    }

    public void withdraw() {
        Claim c = claim.getValue();
        if (c == null) return;
        busy.setValue(true);
        repo.withdraw(c, finish("Claim withdrawn."));
    }

    public void clearError() { error.setValue(null); }
    public void clearInfo() { info.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
