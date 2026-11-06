package com.rentnear.app.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.rentnear.app.model.Chat;
import com.rentnear.app.model.ChatMessage;
import com.rentnear.app.model.Item;
import com.rentnear.app.util.Format;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat on Firestore. Live listeners are only attached by the screens that are open
 * (chat list, open conversation) so reads stay inside the free quota.
 */
public class ChatRepository {
    private static final int MESSAGE_LIMIT = 50;
    private static final int CHAT_LIMIT = 50;

    public interface Done {
        /** @param error null on success */
        void onDone(@Nullable String error);
    }

    public interface ChatsListener {
        void onChats(List<Chat> chats);
        void onError(String message);
    }

    public interface MessagesListener {
        void onMessages(List<ChatMessage> messages);
        void onError(String message);
    }

    public interface BlockStateListener {
        void onState(boolean blockedByMe, boolean blockedMe);
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static String chatId(String itemId, String renterUid) {
        return itemId + "_" + renterUid;
    }

    private DocumentReference chatRef(String chatId) {
        return db.collection("chats").document(chatId);
    }

    // ---------------------------------------------------------------- open / load

    /** Returns the chat id for (item, current user as renter), creating the chat if needed. */
    public void openChat(Item item, FirebaseUser me, MutableLiveData<Result<String>> out) {
        String id = chatId(item.id, me.getUid());
        chatRef(id).get().addOnSuccessListener(snap -> {
            if (snap.exists()) {
                out.setValue(Result.ok(id));
                return;
            }
            String renterName = me.getDisplayName() == null || me.getDisplayName().isEmpty()
                    ? "User" : me.getDisplayName();
            Map<String, Object> unread = new HashMap<>();
            unread.put(item.ownerId, 0L);
            unread.put(me.getUid(), 0L);

            Map<String, Object> m = new HashMap<>();
            List<String> participants = new ArrayList<>();
            participants.add(item.ownerId);
            participants.add(me.getUid());
            m.put("participants", participants);
            m.put("itemId", item.id);
            m.put("itemTitle", item.title);
            m.put("itemPhoto", item.firstPhoto() == null ? "" : item.firstPhoto());
            m.put("itemUnit", item.priceUnit == null ? Item.UNIT_DAY : item.priceUnit);
            m.put("ownerId", item.ownerId);
            m.put("renterId", me.getUid());
            m.put("ownerName", item.ownerName == null ? "User" : item.ownerName);
            m.put("renterName", renterName);
            m.put("lastMessage", "");
            m.put("lastSenderId", "");
            m.put("lastMessageAt", FieldValue.serverTimestamp());
            m.put("unread", unread);
            chatRef(id).set(m)
                    .addOnSuccessListener(v -> out.setValue(Result.ok(id)))
                    .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
        }).addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public void loadChat(String chatId, MutableLiveData<Result<Chat>> out) {
        chatRef(chatId).get()
                .addOnSuccessListener(d -> {
                    if (d.exists()) out.setValue(Result.ok(Chat.from(d)));
                    else out.setValue(Result.fail("Chat not found"));
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    // ---------------------------------------------------------------- live listeners

    /** Chats that have at least one message, newest first (sorted here to avoid a composite index). */
    public ListenerRegistration listenChats(String uid, ChatsListener l) {
        return db.collection("chats")
                .whereArrayContains("participants", uid)
                .limit(CHAT_LIMIT)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(e.getMessage());
                        return;
                    }
                    List<Chat> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Chat c = Chat.from(d);
                            if (c.lastMessage != null && !c.lastMessage.isEmpty()) list.add(c);
                        }
                    }
                    list.sort((a, b) -> Long.compare(b.lastMessageAtMs, a.lastMessageAtMs));
                    l.onChats(list);
                });
    }

    /** Latest messages of one chat, oldest first. */
    public ListenerRegistration listenMessages(String chatId, MessagesListener l) {
        return chatRef(chatId).collection("messages")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MESSAGE_LIMIT)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        l.onError(e.getMessage());
                        return;
                    }
                    List<ChatMessage> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) list.add(ChatMessage.from(d));
                    }
                    Collections.reverse(list);
                    l.onMessages(list);
                });
    }

    // ---------------------------------------------------------------- sending

    public void sendText(Chat chat, String me, String text, Done done) {
        Map<String, Object> msg = baseMessage(me, ChatMessage.TYPE_TEXT);
        msg.put("body", text);
        sendMessage(chat, me, msg, preview(text), done);
    }

    public void sendImage(Chat chat, String me, String url, Done done) {
        Map<String, Object> msg = baseMessage(me, ChatMessage.TYPE_IMAGE);
        msg.put("body", url);
        sendMessage(chat, me, msg, "📷 Photo", done);
    }

    public void sendOffer(Chat chat, String me, double amount, String unit, String kind, Done done) {
        sendMessage(chat, me, offerMessage(me, amount, unit, kind),
                offerPreview(kind, amount, unit), done);
    }

    private Map<String, Object> baseMessage(String me, String type) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("senderId", me);
        msg.put("type", type);
        msg.put("createdAt", FieldValue.serverTimestamp());
        return msg;
    }

    private Map<String, Object> offerMessage(String me, double amount, String unit, String kind) {
        Map<String, Object> offer = new HashMap<>();
        offer.put("amount", amount);
        offer.put("unit", unit);
        offer.put("status", ChatMessage.STATUS_PENDING);
        offer.put("kind", kind);
        Map<String, Object> msg = baseMessage(me, ChatMessage.TYPE_OFFER);
        msg.put("offer", offer);
        return msg;
    }

    private void sendMessage(Chat chat, String me, Map<String, Object> msg, String preview, Done done) {
        WriteBatch b = db.batch();
        b.set(chatRef(chat.id).collection("messages").document(), msg);
        Map<String, Object> upd = bump(chat, me, preview);
        if (ChatMessage.TYPE_OFFER.equals(msg.get("type"))) {
            // A new offer supersedes any earlier agreement.
            upd.put("acceptedOffer", FieldValue.delete());
        }
        b.update(chatRef(chat.id), upd);
        commit(b, done);
    }

    /** Accept or decline an offer you received. Accepting also records the agreed price on the chat. */
    public void respondToOffer(Chat chat, String me, ChatMessage offer, String newStatus, Done done) {
        WriteBatch b = db.batch();
        b.update(chatRef(chat.id).collection("messages").document(offer.id),
                "offer.status", newStatus);

        boolean accepted = ChatMessage.STATUS_ACCEPTED.equals(newStatus);
        String text = (accepted ? "Offer accepted: " : "Offer declined: ")
                + Format.money(offer.offerAmount) + " / " + offer.offerUnit;
        Map<String, Object> upd = bump(chat, me, text);
        if (accepted) {
            Map<String, Object> agreed = new HashMap<>();
            agreed.put("amount", offer.offerAmount);
            agreed.put("unit", offer.offerUnit);
            agreed.put("messageId", offer.id);
            upd.put("acceptedOffer", agreed);
        }
        b.update(chatRef(chat.id), upd);
        commit(b, done);
    }

    /** Answer an offer with a new price: marks the old offer countered and sends a counter-offer. */
    public void counterOffer(Chat chat, String me, ChatMessage offer, double amount, Done done) {
        WriteBatch b = db.batch();
        b.update(chatRef(chat.id).collection("messages").document(offer.id),
                "offer.status", ChatMessage.STATUS_COUNTERED);
        b.set(chatRef(chat.id).collection("messages").document(),
                offerMessage(me, amount, offer.offerUnit, ChatMessage.KIND_COUNTER));
        Map<String, Object> upd = bump(chat, me,
                offerPreview(ChatMessage.KIND_COUNTER, amount, offer.offerUnit));
        upd.put("acceptedOffer", FieldValue.delete());
        b.update(chatRef(chat.id), upd);
        commit(b, done);
    }

    /** Preview text, last-sender and the other person's unread counter. */
    private Map<String, Object> bump(Chat chat, String me, String preview) {
        Map<String, Object> upd = new HashMap<>();
        upd.put("lastMessage", preview);
        upd.put("lastMessageAt", FieldValue.serverTimestamp());
        upd.put("lastSenderId", me);
        upd.put("unread." + chat.otherUid(me), FieldValue.increment(1));
        return upd;
    }

    private void commit(WriteBatch b, Done done) {
        b.commit()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(e.getMessage() == null ? "Failed" : e.getMessage()));
    }

    private static String preview(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "…" : text;
    }

    private static String offerPreview(String kind, double amount, String unit) {
        return (ChatMessage.KIND_COUNTER.equals(kind) ? "Counter-offer: " : "Offer: ")
                + Format.money(amount) + " / " + unit;
    }

    public void markRead(String chatId, String me) {
        chatRef(chatId).update("unread." + me, 0L);
    }

    // ---------------------------------------------------------------- block and report

    private DocumentReference blockRef(String blocker, String blocked) {
        return db.collection("blocks").document(blocker).collection("list").document(blocked);
    }

    public void blockState(String me, String other, BlockStateListener l) {
        Task<DocumentSnapshot> byMe = blockRef(me, other).get();
        Task<DocumentSnapshot> byThem = blockRef(other, me).get();
        Tasks.whenAllComplete(byMe, byThem).addOnCompleteListener(t ->
                l.onState(byMe.isSuccessful() && byMe.getResult().exists(),
                        byThem.isSuccessful() && byThem.getResult().exists()));
    }

    public void block(String me, String other, Done done) {
        Map<String, Object> m = new HashMap<>();
        m.put("createdAt", FieldValue.serverTimestamp());
        blockRef(me, other).set(m)
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(e.getMessage() == null ? "Failed" : e.getMessage()));
    }

    public void unblock(String me, String other, Done done) {
        blockRef(me, other).delete()
                .addOnSuccessListener(v -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(e.getMessage() == null ? "Failed" : e.getMessage()));
    }

    /** Files a report for the admin queue (reports/{id}); users cannot read reports back. */
    public void reportChat(Chat chat, String me, String reason, Done done) {
        Map<String, Object> r = new HashMap<>();
        r.put("targetType", "chat");
        r.put("targetId", chat.id);
        r.put("reportedUid", chat.otherUid(me));
        r.put("itemId", chat.itemId);
        r.put("reporterId", me);
        r.put("reason", reason);
        r.put("status", "open");
        r.put("createdAt", FieldValue.serverTimestamp());
        db.collection("reports").add(r)
                .addOnSuccessListener(ref -> done.onDone(null))
                .addOnFailureListener(e -> done.onDone(e.getMessage() == null ? "Failed" : e.getMessage()));
    }
}
