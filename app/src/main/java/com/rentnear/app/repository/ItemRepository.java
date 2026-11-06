package com.rentnear.app.repository;

import androidx.lifecycle.MutableLiveData;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryBounds;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.rentnear.app.model.Item;
import com.rentnear.app.util.Result;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Listings: create, load one, and geohash range search for nearby items. */
public class ItemRepository {
    /** Reads per geohash bound are capped so one search stays well under the free daily quota. */
    private static final int LIMIT_PER_BOUND = 30;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void create(Map<String, Object> data, MutableLiveData<Result<String>> out) {
        db.collection("items").add(data)
                .addOnSuccessListener(ref -> out.setValue(Result.ok(ref.getId())))
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    public void get(String id, MutableLiveData<Result<Item>> out) {
        db.collection("items").document(id).get()
                .addOnSuccessListener(d -> {
                    if (d.exists()) out.setValue(Result.ok(Item.from(d)));
                    else out.setValue(Result.fail("Item not found"));
                })
                .addOnFailureListener(e -> out.setValue(Result.fail(e.getMessage())));
    }

    /**
     * Nearby search: a circle around {@code center} is covered by a few geohash ranges; each range
     * is one Firestore query on the single indexed field "geohash". False positives at the
     * range edges are removed by an exact distance check. One-shot reads (no live listener)
     * keep quota use low.
     */
    public void nearby(GeoLocation center, double radiusM, String viewerUid,
                       MutableLiveData<Result<List<Item>>> out) {
        List<GeoQueryBounds> bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusM);
        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        for (GeoQueryBounds b : bounds) {
            tasks.add(db.collection("items")
                    .orderBy("geohash")
                    .startAt(b.startHash)
                    .endAt(b.endHash)
                    .limit(LIMIT_PER_BOUND)
                    .get());
        }

        Tasks.whenAllComplete(tasks).addOnCompleteListener(done -> {
            Map<String, Item> found = new LinkedHashMap<>();
            String firstError = null;
            int failures = 0;
            for (Task<QuerySnapshot> t : tasks) {
                if (!t.isSuccessful()) {
                    failures++;
                    if (firstError == null && t.getException() != null) {
                        firstError = t.getException().getMessage();
                    }
                    continue;
                }
                for (DocumentSnapshot d : t.getResult().getDocuments()) {
                    Item item = Item.from(d);
                    if (!item.active) continue;
                    double dist = GeoFireUtils.getDistanceBetween(
                            new GeoLocation(item.lat, item.lng), center);
                    if (dist > radiusM) continue;
                    if (!visibleTo(item, viewerUid)) continue;
                    item.distanceM = dist;
                    found.put(item.id, item);
                }
            }
            if (failures == tasks.size() && !tasks.isEmpty()) {
                out.setValue(Result.fail(firstError == null ? "Could not load listings" : firstError));
            } else {
                out.setValue(Result.ok(new ArrayList<>(found.values())));
            }
        });
    }

    /**
     * Friends-only items are visible to their owner for now. Step 2 has no friends list yet, so
     * other users do not see them until the friends feature lands.
     */
    private static boolean visibleTo(Item item, String viewerUid) {
        if (Item.AUDIENCE_FRIENDS.equals(item.audience)) {
            return item.ownerId != null && item.ownerId.equals(viewerUid);
        }
        return true;
    }
}
