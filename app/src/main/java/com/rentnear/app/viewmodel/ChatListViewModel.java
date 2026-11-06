package com.rentnear.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.model.Chat;
import com.rentnear.app.repository.ChatRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Scoped to MainActivity so the chat list and the bottom-nav unread badge share one live
 * listener. MainActivity starts it in onStart and stops it in onStop (app open = live updates).
 */
public class ChatListViewModel extends ViewModel {
    private final ChatRepository repo = new ChatRepository();
    private final MutableLiveData<List<Chat>> chats = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> totalUnread = new MutableLiveData<>(0);
    private final MutableLiveData<String> error = new MutableLiveData<>();

    private ListenerRegistration registration;
    private String uid;

    public LiveData<List<Chat>> chats() { return chats; }
    public LiveData<Integer> totalUnread() { return totalUnread; }
    public LiveData<String> error() { return error; }
    public String uid() { return uid; }

    public void start() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null || registration != null) return;
        uid = u.getUid();
        registration = repo.listenChats(uid, new ChatRepository.ChatsListener() {
            @Override
            public void onChats(List<Chat> list) {
                int unread = 0;
                for (Chat c : list) unread += (int) c.unreadFor(uid);
                chats.setValue(list);
                totalUnread.setValue(unread);
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

    public void clearError() {
        error.setValue(null);
    }

    @Override
    protected void onCleared() {
        stop();
    }
}
