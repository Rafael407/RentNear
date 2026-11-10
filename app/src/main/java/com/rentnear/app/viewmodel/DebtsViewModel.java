package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.Debt;
import com.rentnear.app.repository.DebtRepository;
import com.rentnear.app.util.Done;

import java.util.List;

/** My debts (money I owe) and credits (money owed to me), with pay, collect and ban actions. */
public class DebtsViewModel extends ViewModel {
    private final DebtRepository repo = new DebtRepository();
    private final String me;
    private final MutableLiveData<List<Debt>> debts = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();
    private List<ListenerRegistration> regs;

    public DebtsViewModel() {
        String uid = FirebaseAuth.getInstance().getUid();
        me = uid == null ? "" : uid;
    }

    public String me() { return me; }
    public LiveData<List<Debt>> debts() { return debts; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<String> error() { return error; }
    public LiveData<String> info() { return info; }

    public void start() {
        if (regs != null || me.isEmpty()) return;
        regs = repo.listenMine(me, new DebtRepository.ListListener() {
            @Override
            public void onDebts(List<Debt> list) {
                debts.setValue(list);
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

    private Done finish(String message) {
        return err -> {
            busy.setValue(false);
            if (err != null) error.setValue(err);
            else info.setValue(message);
        };
    }

    public void pay(Debt d) {
        busy.setValue(true);
        repo.pay(d, me, finish("Debt paid. Your account is unfrozen if this was your last debt."));
    }

    public void collect(Debt d) {
        busy.setValue(true);
        repo.collect(d, me, finish("Collected into your wallet."));
    }

    public void ban(Debt d) {
        busy.setValue(true);
        repo.ban(d, finish("The account was banned for missing the deadline."));
    }

    public void clearError() { error.setValue(null); }
    public void clearInfo() { info.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
