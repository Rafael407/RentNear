package com.rentnear.app.data.cache;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** The last nearby search, stored as one JSON list so the Browse tab can show it instantly or offline. */
@Entity(tableName = "cached_search")
public class CachedSearch {
    @PrimaryKey
    @NonNull
    public String key = "nearby";
    public String json;
    public double lat;
    public double lng;
    public double radiusM;
    public long savedAtMs;
}
