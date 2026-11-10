package com.rentnear.app.model;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

/** users/{uid}: name, photoUrl, trusted, trustScore, points, frozen, banned, pastDamage. */
public class UserProfile {
    public String uid;
    public String name;
    public String email;
    public String photoUrl;
    public boolean trusted;      // "Trusted" badge, granted by an admin (replaces ID verification)
    public double trustScore;    // can go negative
    public long points;
    public boolean frozen;
    public boolean banned;
    public boolean pastDamage;
    /** Latest warning from an admin, or empty. */
    public String warning;

    public static Map<String, Object> newUserMap(String name, String email, String photoUrl) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("photoUrl", photoUrl == null ? "" : photoUrl);
        m.put("trusted", false);
        m.put("trustScore", 0.0);
        m.put("points", 0L);
        m.put("frozen", false);
        m.put("banned", false);
        m.put("pastDamage", false);
        m.put("createdAt", com.google.firebase.Timestamp.now());
        return m;
    }

    public static UserProfile from(DocumentSnapshot d) {
        UserProfile u = new UserProfile();
        u.uid = d.getId();
        u.name = d.getString("name");
        u.email = d.getString("email");
        u.photoUrl = d.getString("photoUrl");
        u.trusted = Boolean.TRUE.equals(d.getBoolean("trusted"));
        Double ts = d.getDouble("trustScore");
        u.trustScore = ts == null ? 0 : ts;
        Long p = d.getLong("points");
        u.points = p == null ? 0 : p;
        u.frozen = Boolean.TRUE.equals(d.getBoolean("frozen"));
        u.banned = Boolean.TRUE.equals(d.getBoolean("banned"));
        u.pastDamage = Boolean.TRUE.equals(d.getBoolean("pastDamage"));
        u.warning = d.getString("warning");
        return u;
    }
}
