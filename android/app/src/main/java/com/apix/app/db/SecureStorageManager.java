package com.apix.app.db;

import android.content.Context;
import androidx.room.Room;
import net.sqlcipher.database.SupportFactory;
import com.apix.app.security.KeysVault;

public class SecureStorageManager {
    private static volatile SecureStorageManager instance;
    private AppDatabase database;

    private SecureStorageManager(Context context) {
        // جلب كلمة مرور تشفير قاعدة البيانات من C++ NDK
        String dbPassword = KeysVault.INSTANCE.getInternalKeySalt(); 
        byte[] passphrase = dbPassword.getBytes();
        
        // تفعيل محرك SQLCipher
        SupportFactory factory = new SupportFactory(passphrase);

        database = Room.databaseBuilder(context.getApplicationContext(),
                AppDatabase.class, "apix_secure_vault.db")
                .openHelperFactory(factory) // تفعيل التشفير
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries() // <-- تم إضافة هذا السطر للسماح بالقراءة السريعة (Instant UI)
                .build();
    }

    public static SecureStorageManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SecureStorageManager.class) {
                if (instance == null) {
                    instance = new SecureStorageManager(context);
                }
            }
        }
        return instance;
    }

    public AppDatabase getDb() {
        return database;
    }
    
    public void saveString(String key, String value) {
        SecureCacheEntity entity = new SecureCacheEntity();
        entity.key = key;
        entity.value = value;
        entity.timestamp = System.currentTimeMillis();
        database.secureCacheDao().put(entity);
    }

    public String getString(String key) {
        SecureCacheEntity entity = database.secureCacheDao().get(key);
        return entity != null ? entity.value : null;
    }
    
    public Long getTimestamp(String key) {
        SecureCacheEntity entity = database.secureCacheDao().get(key);
        return entity != null ? entity.timestamp : 0L;
    }
}
