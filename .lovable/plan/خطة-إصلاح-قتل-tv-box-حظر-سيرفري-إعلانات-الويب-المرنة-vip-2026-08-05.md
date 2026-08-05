# خطة إصلاح: قتل TV Box + حظر سيرفري + إعلانات الويب المرنة + VIP

## المهمة 1 — منع القتل الصامت على TV Box الصينية

**السبب المرجح:** `sec.cpp::Java_com_apix_app_x_gd` يستدعي `punish_silent()` عند:
- `scan_maps()` — بعض ROM الصينية تحوي مكتبات تحتوي كلمة `magisk` أو `xposed` مدمجة في الفيرموير.
- `scan_frida_port()` — بورت 27042/27043 قد يُستخدم من خدمات cast صينية.
- `scan_proxy_ports()` — بورتات 8080/8888/1080 مفتوحة من خدمات cast/DLNA/HTTP-relay في TV Box.

**الحل (بدون تعطيل الحماية على الهواتف):**
1. توسيع `isRealTvBoxHardware()` وتصدير دالة C++ `is_tv_box()` تفحص:
   - `PackageManager.hasSystemFeature("android.software.leanback")`
   - `UI_MODE_TYPE_TELEVISION`
   - رقاقات معروفة (Amlogic/Rockchip/Allwinner/MTK)
   - غياب ميزة الهاتف `FEATURE_TELEPHONY`
2. في `gd()`:
   - إذا `is_tv_box() == true`: تخطّي `scan_proxy_ports()` و`scan_frida_port()` كاملاً (هذه بورتات cast شرعية على TV).
   - إبقاء `scan_maps()` و`scan_files()` — إنما يُستثنى منها الكلمات العامة (`magisk` فقط داخل `/data/local/tmp/` أو `/system/xbin/`).
3. تمرير علم TV Box من Kotlin إلى JNI عبر `System.setProperty("apix.is_tv","1")` عند الإقلاع.

## المهمة 2 — الحظر السيرفري الفعّال + قائمة VPN IPs من اللوحة

**2.a — حذف المحتوى فور الحظر:**
- التطبيق يستدعي `device-handshake` قبل تحميل القنوات. إن أرجع `status != ACTIVE`:
  - `Enforcement.wipeChannelCache()` (موجود) — سيتم توسعته لحذف: SQLCipher DB، ملفات الكاش، صور Coil، تفضيلات القنوات.
  - نقل رسالة "أنت محظور بسبب استخدام غير شرعي" من الكود إلى `ban_reason` القادم من السيرفر (موجود جزئياً).
- في `device-handshake/index.ts`: عند حظر device_id، إضافة `signature_hash` و`android_id` كذلك إلى قائمة الحظر بحيث لا يعبر حتى لو غيّر id.

**2.b — قائمة VPN IPs في اللوحة:**
- إزالة أي IP مُشفَّر داخل الكود (Firewall/AppFirewall).
- جدول `system_settings` مفتاح `vpn_allowed_ips` = `["1.2.3.4","5.6.7.8"]` — تحرير من `SecurityConfigManager`.
- `device-handshake` يقرأ IP الطالب من `x-forwarded-for` وعند `vpn_active=true`:
  - إن كان IP ضمن `vpn_allowed_ips` → `status=ACTIVE`.
  - وإلا → `status=VPN_BLOCK` + `wipe=true`.
- التطبيق عند `VPN_BLOCK`: يستدعي `Enforcement.silentExit()` مباشرة.

## المهمة 3 — نظام إعلانات ويب مرن + VIP يتخطى الكل

**اللوحة (`AdConfigManager`):** جدول جديد `web_ad_scope` بمفاتيح مستقلة (كل واحد تفعيل/إيقاف):
- `on_app_open` — عند فتح التطبيق.
- `on_external_links` — عند فتح رابط خارجي (موجود).
- `on_internal_channels` — عند فتح أي قناة داخلية.
- `on_side_channels_only` — فقط قنوات القائمة الجانبية.

**التطبيق:**
- `WebAdActivity` يقرأ الإعدادات ويقرر عرض الإعلان بناءً على `scope` الممرَّر (`app_open`/`external`/`internal`/`side`).
- في `PlayerScreen.loadStream()` / `ComposeActivity` تُمرَّر الـ scope الصحيح.
- **حراسة VIP:** قبل أي عرض إعلان (ويب/AdMob/انترستيشيال) نداء واحد:
  ```kotlin
  if (VipChecker.isVipDevice(context)) return  // تخطي كامل
  ```
- `VipChecker` (موجود) يعتمد على `device_id` مقارنةً بقائمة `vip_device_ids` في `system_settings`.

## الملفات المتأثرة
- `android/app/src/main/cpp/sec.cpp` + `CMakeLists.txt`
- `android/app/src/main/java/com/apix/app/security/DeviceIntegrity.java`
- `android/app/src/main/java/com/apix/app/security/Enforcement.java`
- `android/app/src/main/java/com/apix/app/SplashActivity.java`
- `android/app/src/main/java/com/apix/app/AdManager.java` + `WebAdActivity.java`
- `android/app/src/main/java/com/apix/app/ApixApplication.kt`
- إزالة IPs المُشفَّرة من `AppFirewall.java` (إن وُجد)
- `supabase/functions/device-handshake/index.ts`
- `src/components/admin/SecurityConfigManager.tsx` (حقل VPN IPs متعدد)
- `src/components/admin/AdConfigManager.tsx` (توسعة نطاقات الويب)

## ملاحظة
لا يمكنني بناء/اختبار الأندرويد داخل Lovable. سأكتب الكود مرجعياً وأعتمد عليك للبناء عبر GitHub Actions.
