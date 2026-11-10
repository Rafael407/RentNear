package com.rentnear.app.repository;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Online / last seen, kept in the Firebase Realtime Database (presence/{uid}). Realtime Database is
 * used here because its onDisconnect() hook marks a user offline even when the app is killed.
 * Everything is wrapped in try/catch: if the database was not created in the console the rest of the
 * app keeps working and the chat header simply shows nothing.
 */
public class PresenceRepository {
    public interface Listener {
        /** @param online true when connected; lastSeen is epoch millis (0 if unknown) */
        void onPresence(boolean online, long lastSeen);
    }

    private ValueEventListener connectedListener;
    private DatabaseReference connectedRef;
    private DatabaseReference watchRef;
    private ValueEventListener watchListener;

    /** Marks me online now and offline (with a last-seen time) when the connection drops. */
    public void goOnline() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || connectedListener != null) return;
        try {
            FirebaseDatabase db = FirebaseDatabase.getInstance();
            DatabaseReference mine = db.getReference("presence").child(uid);
            connectedRef = db.getReference(".info/connected");
            connectedListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot s) {
                    if (!Boolean.TRUE.equals(s.getValue(Boolean.class))) return;
                    mine.onDisconnect().setValue(state(false));
                    mine.setValue(state(true));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) { }
            };
            connectedRef.addValueEventListener(connectedListener);
        } catch (Exception ignored) {
            connectedListener = null;
        }
    }

    /** Marks me offline right away (the app went to the background). */
    public void goOffline() {
        String uid = FirebaseAuth.getInstance().getUid();
        try {
            if (connectedRef != null && connectedListener != null) {
                connectedRef.removeEventListener(connectedListener);
            }
            connectedListener = null;
            if (uid != null) {
                FirebaseDatabase.getInstance().getReference("presence").child(uid).setValue(state(false));
            }
        } catch (Exception ignored) {
        }
    }

    /** Watches another user's presence until {@link #stopWatching()}. */
    public void watch(String uid, Listener l) {
        stopWatching();
        if (uid == null) return;
        try {
            watchRef = FirebaseDatabase.getInstance().getReference("presence").child(uid);
            watchListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot s) {
                    Boolean on = s.child("online").getValue(Boolean.class);
                    Long seen = s.child("lastSeen").getValue(Long.class);
                    l.onPresence(Boolean.TRUE.equals(on), seen == null ? 0 : seen);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError e) { }
            };
            watchRef.addValueEventListener(watchListener);
        } catch (Exception ignored) {
            watchRef = null;
            watchListener = null;
        }
    }

    public void stopWatching() {
        if (watchRef != null && watchListener != null) watchRef.removeEventListener(watchListener);
        watchRef = null;
        watchListener = null;
    }

    private static Map<String, Object> state(boolean online) {
        Map<String, Object> m = new HashMap<>();
        m.put("online", online);
        m.put("lastSeen", ServerValue.TIMESTAMP);
        return m;
    }
}
