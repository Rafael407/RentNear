package com.rentnear.app.model;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** items/{id}: ownerId, title, photos[], price, declaredValue, geohash, lat, lng, audience, active. */
public class Item {
    public static final String AUDIENCE_EVERYONE = "everyone";
    public static final String AUDIENCE_FRIENDS = "friends";
    public static final String UNIT_HOUR = "hour";
    public static final String UNIT_DAY = "day";

    public String id;
    public String ownerId;
    public String ownerName;
    public String title;
    public String description;
    public String category;
    public String priceUnit;
    public String address;
    public String audience;
    public String geohash;
    public List<String> photos = new ArrayList<>();
    public double price;
    public double declaredValue;
    public double lat;
    public double lng;
    public double ratingAvg;
    public long ratingCount;
    public boolean active;

    /** Client-side only: metres from the viewer. Not stored in Firestore. */
    public double distanceM;

    public String firstPhoto() {
        return photos.isEmpty() ? null : photos.get(0);
    }

    /** Price normalised to per-hour so hourly and daily listings can be sorted together. */
    public double pricePerHour() {
        return UNIT_DAY.equals(priceUnit) ? price / 24.0 : price;
    }

    @SuppressWarnings("unchecked")
    public static Item from(DocumentSnapshot d) {
        Item i = new Item();
        i.id = d.getId();
        i.ownerId = d.getString("ownerId");
        i.ownerName = d.getString("ownerName");
        i.title = d.getString("title");
        i.description = d.getString("description");
        i.category = d.getString("category");
        i.priceUnit = d.getString("priceUnit");
        i.address = d.getString("address");
        i.audience = d.getString("audience");
        i.geohash = d.getString("geohash");
        Object p = d.get("photos");
        if (p instanceof List) {
            for (Object o : (List<Object>) p) if (o != null) i.photos.add(o.toString());
        }
        i.price = num(d.getDouble("price"));
        i.declaredValue = num(d.getDouble("declaredValue"));
        i.lat = num(d.getDouble("lat"));
        i.lng = num(d.getDouble("lng"));
        i.ratingAvg = num(d.getDouble("ratingAvg"));
        Long rc = d.getLong("ratingCount");
        i.ratingCount = rc == null ? 0 : rc;
        i.active = Boolean.TRUE.equals(d.getBoolean("active"));
        return i;
    }

    public static Map<String, Object> newMap(String ownerId, String ownerName, String title,
                                             String description, String category, double price,
                                             String priceUnit, double declaredValue, String address,
                                             double lat, double lng, String audience,
                                             List<String> photos) {
        Map<String, Object> m = new HashMap<>();
        m.put("ownerId", ownerId);
        m.put("ownerName", ownerName);
        m.put("title", title);
        m.put("description", description);
        m.put("category", category);
        m.put("price", price);
        m.put("priceUnit", priceUnit);
        m.put("declaredValue", declaredValue);
        m.put("address", address);
        m.put("lat", lat);
        m.put("lng", lng);
        m.put("geohash", GeoFireUtils.getGeoHashForLocation(new GeoLocation(lat, lng)));
        m.put("audience", audience);
        m.put("photos", photos);
        m.put("active", true);
        m.put("ratingAvg", 0.0);
        m.put("ratingCount", 0L);
        m.put("createdAt", FieldValue.serverTimestamp());
        return m;
    }

    private static double num(Double d) {
        return d == null ? 0 : d;
    }
}
