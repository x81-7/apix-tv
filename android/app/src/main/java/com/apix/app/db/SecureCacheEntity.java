package com.apix.app.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "secure_cache")
public class SecureCacheEntity {
    @PrimaryKey
    @NonNull
    public String key = "";
    public String value;
    public long timestamp;
}
