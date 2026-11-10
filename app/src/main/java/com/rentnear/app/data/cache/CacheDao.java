package com.rentnear.app.data.cache;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void save(CachedSearch row);

    @Query("SELECT * FROM cached_search WHERE `key` = 'nearby' LIMIT 1")
    CachedSearch load();

    @Query("DELETE FROM cached_search")
    void clear();
}
