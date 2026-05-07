package com.apix.app.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface SecureCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(SecureCacheEntity entity);

    @Query("SELECT * FROM secure_cache WHERE `key` = :key LIMIT 1")
    SecureCacheEntity get(String key);

    @Query("DELETE FROM secure_cache")
    void clearAll();
}
