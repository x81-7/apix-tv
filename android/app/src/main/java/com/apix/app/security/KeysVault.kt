package com.apix.app.security

/**
 * Native Bridge for C++ Vault. 
 * Connects Java/Kotlin logic to unreadable machine code.
 */
object KeysVault {
    
    init {
        System.loadLibrary("apix_vault")
    }

    external fun getEncryptionSecretKey(): String
    external fun getAppApiHmacSecret(): String
    external fun getInternalKeySalt(): String
    external fun getExternalPanelDecryptionKey(): String
    external fun getSupabaseUrl(): String
    external fun getSupabaseAnonKey(): String
}
