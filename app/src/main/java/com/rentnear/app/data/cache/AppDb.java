package com.rentnear.app.data.cache;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {CachedSearch.class}, version = 1, exportSchema = false)
public abstract class AppDb extends RoomDatabase {
    private static volatile AppDb instance;

    public abstract CacheDao cacheDao();

    public static AppDb get(Context context) {
        if (instance == null) {
            synchronized (AppDb.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDb.class, "rentnear.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
