package com.apix.app.security;

import android.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSigner {
    public static String generateSignature(String data) {
        try {
            // Securely retrieves the secret from the C++ Vault
            String secret = KeysVault.INSTANCE.getAppApiHmacSecret();
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            
            byte[] hash = sha256_HMAC.doFinal(data.getBytes("UTF-8"));
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
