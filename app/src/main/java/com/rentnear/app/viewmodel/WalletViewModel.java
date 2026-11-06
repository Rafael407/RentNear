package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.WalletTx;
import com.rentnear.app.repository.WalletRepository;

import java.util.ArrayList;
import java.util.List;

/** Wallet screen: balance and history, live while visible, plus demo top-up. */
public class WalletViewModel extends ViewModel {
    private final WalletRepository repo = new WalletRepository();
    private final String uid;

    private final MutableLiveData<Double> balance = new MutableLiveData<>();
    private final MutableLiveData<List<WalletTx>> tx = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration balanceReg;
    private ListenerRegistration txReg;

    public WalletViewModel() {
        String u = FirebaseAuth.getInstance().getUid();
        uid = u == null ? "" : u;
    }

    public LiveData<Double> balance() { return balance; }
    public LiveData<List<WalletTx>> tx() { return tx; }
    public LiveData<String> error() { return error; }

    public void start() {
        if (uid.isEmpty() || balanceReg != null) return;
        repo.ensure(uid);
        balanceReg = repo.listenBalance(uid, new WalletRepository.BalanceListener() {
            @Override public void onBalance(double b) { balance.setValue(b); }
            @Override public void onError(String m) { error.setValue(m); }
        });
        txReg = repo.listenTx(uid, new WalletRepository.TxListener() {
            @Override public void onTx(List<WalletTx> list) { tx.setValue(list); }
            @Override public void onError(String m) { error.setValue(m); }
        });
    }

    public void stop() {
        if (balanceReg != null) balanceReg.remove();
        if (txReg != null) txReg.remove();
        balanceReg = null;
        txReg = null;
    }

    public void topUp(double amount) {
        repo.topUp(uid, amount, err -> {
            if (err != null) error.setValue(err);
        });
    }

    public void clearError() { error.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
