package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.FriendRequest;
import com.rentnear.app.repository.FriendRepository;
import com.rentnear.app.util.Done;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Friends, incoming and outgoing requests, and the actions on them. */
public class FriendsViewModel extends ViewModel {
    private final FriendRepository repo = new FriendRepository();
    private final String me;
    private final MutableLiveData<Map<String, String>> friends = new MutableLiveData<>(new LinkedHashMap<>());
    private final MutableLiveData<List<FriendRequest>> incoming = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<FriendRequest>> outgoing = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();
    private final List<ListenerRegistration> regs = new ArrayList<>();

    public FriendsViewModel() {
        String uid = FirebaseAuth.getInstance().getUid();
        me = uid == null ? "" : uid;
    }

    public LiveData<Map<String, String>> friends() { return friends; }
    public LiveData<List<FriendRequest>> incoming() { return incoming; }
    public LiveData<List<FriendRequest>> outgoing() { return outgoing; }
    public LiveData<Boolean> busy() { return busy; }
    public LiveData<String> error() { return error; }
    public LiveData<String> info() { return info; }

    public void start() {
        if (!regs.isEmpty() || me.isEmpty()) return;
        regs.add(repo.listenFriends(me, new FriendRepository.FriendsListener() {
            @Override
            public void onFriends(Map<String, String> m) { friends.setValue(m); }

            @Override
            public void onError(String message) { error.setValue(message); }
        }));
        regs.add(repo.listenRequests(me, true, listener(incoming)));
        regs.add(repo.listenRequests(me, false, listener(outgoing)));
    }

    private FriendRepository.RequestsListener listener(MutableLiveData<List<FriendRequest>> target) {
        return new FriendRepository.RequestsListener() {
            @Override
            public void onRequests(List<FriendRequest> list) { target.setValue(list); }

            @Override
            public void onError(String message) { error.setValue(message); }
        };
    }

    public void stop() {
        for (ListenerRegistration r : regs) r.remove();
        regs.clear();
    }

    private Done finish(String message) {
        return err -> {
            busy.setValue(false);
            if (err != null) error.setValue(err);
            else if (message != null) info.setValue(message);
        };
    }

    public void add(String email) {
        busy.setValue(true);
        repo.sendByEmail(email, finish("Friend request sent."));
    }

    public void accept(FriendRequest r) {
        busy.setValue(true);
        repo.accept(r, finish("You are now friends."));
    }

    public void decline(FriendRequest r) {
        busy.setValue(true);
        repo.decline(r, finish("Request declined."));
    }

    public void cancel(FriendRequest r) {
        busy.setValue(true);
        repo.cancel(r, finish("Request cancelled."));
    }

    public void remove(String friendUid) {
        busy.setValue(true);
        repo.remove(me, friendUid, finish("Friend removed."));
    }

    public void clearError() { error.setValue(null); }
    public void clearInfo() { info.setValue(null); }

    @Override
    protected void onCleared() {
        stop();
    }
}
