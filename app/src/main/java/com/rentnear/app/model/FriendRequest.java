package com.rentnear.app.model;

import com.google.firebase.firestore.DocumentSnapshot;

/** friendRequests/{from}_{to}. */
public class FriendRequest {
    public String id;
    public String from;
    public String to;
    public String fromName;
    public String toName;
    public String status;

    public static FriendRequest from(DocumentSnapshot d) {
        FriendRequest r = new FriendRequest();
        r.id = d.getId();
        r.from = d.getString("from");
        r.to = d.getString("to");
        r.fromName = d.getString("fromName");
        r.toName = d.getString("toName");
        r.status = d.getString("status");
        return r;
    }
}
