package com.apix.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple image loader with memory cache
 * No external dependencies (no Glide/Picasso)
 */
public class ImageLoader {

    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, Bitmap> cache = new ConcurrentHashMap<>();

    public static void load(String url, ImageView imageView) {
        if (url == null || url.isEmpty()) return;

        // Check cache
        Bitmap cached = cache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        // Set placeholder
        imageView.setImageResource(android.R.color.darker_gray);
        imageView.setTag(url);

        WeakReference<ImageView> weakRef = new WeakReference<>(imageView);

        executor.execute(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                InputStream inputStream = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                conn.disconnect();

                if (bitmap != null) {
                    // Scale down if too large
                    if (bitmap.getWidth() > 500 || bitmap.getHeight() > 500) {
                        float scale = Math.min(500f / bitmap.getWidth(), 500f / bitmap.getHeight());
                        bitmap = Bitmap.createScaledBitmap(bitmap,
                            (int)(bitmap.getWidth() * scale),
                            (int)(bitmap.getHeight() * scale), true);
                    }

                    cache.put(url, bitmap);

                    final Bitmap finalBitmap = bitmap;
                    mainHandler.post(() -> {
                        ImageView iv = weakRef.get();
                        if (iv != null && url.equals(iv.getTag())) {
                            iv.setImageBitmap(finalBitmap);
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    public static void clearCache() {
        cache.clear();
    }
}
