package com.rentnear.app.viewmodel;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.rentnear.app.data.cloudinary.CloudinaryUploader;
import com.rentnear.app.model.Chat;
import com.rentnear.app.model.ChatMessage;
import com.rentnear.app.repository.ChatRepository;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One open conversation: messages (live while visible), offers, block state, report. */
public class ChatViewModel extends ViewModel {
    private final ChatRepository repo = new ChatRepository();
    private final String me;

    private final MutableLiveData<Chat> chat = new MutableLiveData<>();
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> blockedByMe = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> blockedMe = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> sending = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> info = new MutableLiveData<>();

    private String chatId;
    private ListenerRegistration registration;
    private boolean visible;
    private String lastMarkedMessageId;

    private final MutableLiveData<Result<Chat>> loadResult = new MutableLiveData<>();
    private final androidx.lifecycle.Observer<Result<Chat>> loadObserver = r -> {
        if (r == null) return;
        if (r.isOk()) {
            chat.setValue(r.data);
            refreshBlockState();
            // If the screen is already visible, start listening now that the chat is known.
            if (visible) attachMessages();
        } else {
            error.setValue(r.error);
        }
    };

    public ChatViewModel() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        me = u == null ? "" : u.getUid();
        loadResult.observeForever(loadObserver);
    }

    public String me() { return me; }
    public LiveData<Chat> chat() { return chat; }
    public LiveData<List<ChatMessage>> messages() { return messages; }
    public LiveData<Boolean> blockedByMe() { return blockedByMe; }
    public LiveData<Boolean> blockedMe() { return blockedMe; }
    public LiveData<Boolean> sending() { return sending; }
    public LiveData<String> error() { return error; }
    public LiveData<String> info() { return info; }

    public void init(String id) {
        if (chatId != null) return;
        chatId = id;
        repo.loadChat(id, loadResult);
    }

    /** Called from the fragment's onStart: attach the live listener for this open chat only. */
    public void start() {
        visible = true;
        attachMessages();
    }

    public void stop() {
        visible = false;
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void attachMessages() {
        if (chatId == null || chat.getValue() == null || registration != null) return;
        registration = repo.listenMessages(chatId, new ChatRepository.MessagesListener() {
            @Override
            public void onMessages(List<ChatMessage> list) {
                messages.setValue(list);
                markReadIfNeeded(list);
            }

            @Override
            public void onError(String message) {
                error.setValue(message);
            }
        });
    }

    /** One write per new incoming message, not per snapshot, to save quota. */
    private void markReadIfNeeded(List<ChatMessage> list) {
        if (!visible) return;
        for (int i = list.size() - 1; i >= 0; i--) {
            ChatMessage m = list.get(i);
            if (!me.equals(m.senderId)) {
                if (!m.id.equals(lastMarkedMessageId)) {
                    lastMarkedMessageId = m.id;
                    repo.markRead(chatId, me);
                }
                return;
            }
        }
    }

    private void refreshBlockState() {
        Chat c = chat.getValue();
        if (c == null) return;
        repo.blockState(me, c.otherUid(me), (byMe, byThem) -> {
            blockedByMe.setValue(byMe);
            blockedMe.setValue(byThem);
        });
    }

    private boolean canSend() {
        Chat c = chat.getValue();
        if (c == null) {
            error.setValue("Chat is still loading");
            return false;
        }
        if (Boolean.TRUE.equals(blockedByMe.getValue()) || Boolean.TRUE.equals(blockedMe.getValue())) {
            error.setValue("You cannot message this user");
            return false;
        }
        return true;
    }

    private final ChatRepository.Done sendDone = err -> {
        sending.setValue(false);
        if (err != null) error.setValue(err);
    };

    public void sendText(String text) {
        if (!canSend()) return;
        sending.setValue(true);
        repo.sendText(chat.getValue(), me, text, sendDone);
    }

    public void sendImage(Context context, Uri uri) {
        if (!canSend()) return;
        sending.setValue(true);
        CloudinaryUploader.uploadAll(context, Collections.singletonList(uri), (urls, err) -> {
            if (err != null || urls == null || urls.isEmpty()) {
                sending.setValue(false);
                error.setValue(err == null ? "Upload failed" : err);
                return;
            }
            repo.sendImage(chat.getValue(), me, urls.get(0), sendDone);
        });
    }

    /** New offer in the item's own price unit. */
    public void sendOffer(double amount, String unit) {
        if (!canSend()) return;
        sending.setValue(true);
        repo.sendOffer(chat.getValue(), me, amount, unit, ChatMessage.KIND_OFFER, sendDone);
    }

    public void acceptOffer(ChatMessage offer) {
        if (!canSend()) return;
        repo.respondToOffer(chat.getValue(), me, offer, ChatMessage.STATUS_ACCEPTED, err -> {
            if (err != null) error.setValue(err);
            else reloadChat(); // pick up the agreed price banner
        });
    }

    public void declineOffer(ChatMessage offer) {
        if (!canSend()) return;
        repo.respondToOffer(chat.getValue(), me, offer, ChatMessage.STATUS_DECLINED, sendDone);
    }

    public void counterOffer(ChatMessage offer, double amount) {
        if (!canSend()) return;
        repo.counterOffer(chat.getValue(), me, offer, amount, err -> {
            if (err != null) error.setValue(err);
            else reloadChat();
        });
    }

    private void reloadChat() {
        if (chatId != null) repo.loadChat(chatId, loadResult);
    }

    public void block() {
        Chat c = chat.getValue();
        if (c == null) return;
        repo.block(me, c.otherUid(me), err -> {
            if (err != null) error.setValue(err);
            else blockedByMe.setValue(true);
        });
    }

    public void unblock() {
        Chat c = chat.getValue();
        if (c == null) return;
        repo.unblock(me, c.otherUid(me), err -> {
            if (err != null) error.setValue(err);
            else blockedByMe.setValue(false);
        });
    }

    public void report(String reason) {
        Chat c = chat.getValue();
        if (c == null) return;
        repo.reportChat(c, me, reason, err -> {
            if (err != null) error.setValue(err);
            else info.setValue("Report sent. An admin will review it.");
        });
    }

    public void clearError() { error.setValue(null); }
    public void clearInfo() { info.setValue(null); }

    @Override
    protected void onCleared() {
        loadResult.removeObserver(loadObserver);
        stop();
    }
}
