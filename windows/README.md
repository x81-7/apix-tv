# APiX TV — Windows Desktop App (PC)

مشروع مستقل تماماً عن `android/` و `ios/`. لا يعدّل أي ملف فيهما.
يستخدم **Compose Multiplatform Desktop (JVM)** مع **JCEF (Java Chromium Embedded Framework)**
لتشغيل روابط DRM (ClearKey / Widevine / EME / MSE) بدعم كامل لـ Custom Headers.

## التشغيل محلياً
```
cd windows
./gradlew :app:run
```

## بناء MSI / EXE
```
./gradlew :app:packageMsi
./gradlew :app:packageExe
```

## التصميم
تم نسخ تصميم التطبيق من `android/app/src/main/java/com/apix/app/ui/` ليكون مطابقاً
للأندرويد بصرياً (نفس الألوان، نفس البطاقات، نفس Sidebar).

## الميزات
- ✅ Supabase REST + Realtime مباشر (نفس مفاتيح الأندرويد)
- ✅ JCEF Player يدعم Shaka + DASH/MPD + ClearKey/Widevine + Custom Headers
- ✅ VLCJ Fallback Player للـ HLS/MP4 المباشر
- ✅ تخزين محلي مشفر AES-GCM في `%APPDATA%/APiXTV/cache.enc`
- ✅ Hardware-bound UUID (Anti-Tamper) محفوظ في Windows Registry
- ✅ External payload deep-links: `apix://<payload>` و `https://apix-panal.vercel.app/watch.html?id=<payload>`
- ✅ CI/CD: GitHub Actions ينتج `.msi` + `.exe`

## ملاحظة
حجم التطبيق ~150MB بسبب JCEF (Chromium مدمج) — هذا ضروري لدعم EME/DRM بالكامل.