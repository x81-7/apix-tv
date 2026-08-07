# خطة إعادة الهيكلة الأمنية الشاملة (NVP + Tostinfo + Realtime)

## نظرة عامة
إعادة هيكلة أنظمة الحظر والـ VIP وحماية SSL إلى طبقة C++ مستقلة (`nvp.cpp`)، مع بناء نظام تشخيص ذكي (`tostinfo.cpp`) يعمل فقط في Debug، وتفعيل مزامنة لحظية للقنوات والإشعارات.

---

## المهمة 1 — إنشاء `nvp.cpp` (Net + VIP + Punish)
**ملف جديد:** `android/app/src/main/cpp/nvp.cpp`

يحتوي على دوال JNI مستقلة تحت namespace `Java_com_apix_app_Net_*`:
- `nvpCheckBan(deviceId, sigHash)` → يستدعي `/functions/v1/device-handshake` ويرجّع الحالة (ACTIVE/BAN/WIPE).
- `nvpCheckVip(deviceId)` → يستدعي `/functions/v1/check-vip` ويتحقق من `vipToken` عبر HS256.
- `nvpVerifySsl(hostname, spkiHash)` → تحقق SPKI pinning داخل C++.
- `nvpCheckVpn(currentIp, allowlistJson)` → إذا كان VPN نشطاً والـ IP خارج القائمة → `_exit(0)` فوري.

**قاعدة:** لا يُستدعى من `x.kt` مطلقاً. المستدعي الوحيد هو `Net.java` عبر `System.loadLibrary("v")` ثم `native` declarations.

**البناء:** إضافة `nvp.cpp` إلى `CMakeLists.txt` في نفس target `v` (يشارك نفس الـ HMAC/ENC defines لكن معزول منطقياً).

## المهمة 2 — تحديث `Net.java`
- إضافة `native` methods: `nvpCheckBan`, `nvpCheckVip`, `nvpVerifySsl`, `nvpCheckVpn`.
- عند بدء التطبيق: `Net.nvpCheckVpn(...)` قبل أي طلب شبكي.
- استبدال `verifyPins()` باستدعاء `nvpVerifySsl()`.
- `VipChecker.java` يستدعي `Net.nvpCheckVip()` بدل الاتصال المباشر.
- `HandshakeClient.java` يستدعي `Net.nvpCheckBan()` بدل الاتصال المباشر.

## المهمة 3 — `tostinfo.cpp` (نظام Toast الذكي)
**ملف جديد:** `android/app/src/main/cpp/tostinfo.cpp`

منطق العمل الدقيق:
```
if (isRelease || !debugToastsEnabled) → _exit(0)  // صامت فوراً
else → showToast("خطر من [file] :: [func]") → sleep 5s → _exit(0)
```

- دالة JNI: `Java_com_apix_app_security_TostInfo_report(String file, String func)`.
- يستقبل `debugToastsEnabled` من `SharedPreferences` عبر setter من Java يُضبط من `system_settings.debug_kill_toasts`.
- `BuildConfig.DEBUG=false` → قتل فوري بلا استثناء.

**Java wrapper:** `android/app/src/main/java/com/apix/app/security/TostInfo.java` — يمرر context للتوست فقط عند الحاجة.

## المهمة 4 — تنظيف ملفات الحماية القديمة
- `g1.java`, `g2.java`, `g3.java`, `GuardRunner.java`, `SplashActivity.java`, `AppVerifier` (إن وجد):
  - استبدال كل `Toast.makeText(...)` بـ `TostInfo.report("g1", "check")`.
  - استبدال كل النصوص العربية/الإنجليزية الوصفية بمصفوفات XOR (استخدام `Obf.java` الموجود).
- في `sec.cpp` و `n1/n2/n3.cpp`: تحويل النصوص الحساسة إلى XOR compile-time، واستدعاء `tostinfo_report(__FILE__, __func__)` قبل `_exit`.

## المهمة 5 — المزامنة اللحظية (Realtime)
- الاعتماد على `RealtimeNotificationManager.java` الموجود (يعمل عبر Worker WebSocket).
- إضافة اشتراك جديد على جدول `channels` و `sub_channels` و `app_notifications`.
- عند وصول `postgres_changes` event:
  - `channels/sub_channels` → تحديث `SecureCacheManager` للسجل المعني فقط (delta update، لا إعادة تحميل كاملة) وإطلاق `LocalBroadcast` (`APIX_CHANNEL_UPDATED`).
  - `app_notifications` → عرض notification فوري + تحديث UI.
- الشاشات (`HomeScreen`, `SubChannelsScreen`, `ComposeActivity`) تسجّل receiver لتحديث الـ Compose state لحظياً.

## المهمة 6 — لوحة التحكم
**ملف:** `src/components/admin/SecurityConfigManager.tsx`
- إضافة toggle: **"تفعيل رسائل القتل في نسخة الديباج"** يُحفظ في `system_settings` تحت key `debug_kill_toasts`.
- تحذير واضح: يعمل فقط على نسخ Debug، ولا تأثير له على المستخدمين النهائيين.
- الأندرويد يقرأ القيمة من الـ handshake response أو من `cached-data` ويخزّنها في `SharedPreferences` (`debug_kill_toasts=1/0`).

---

## قواعد أمنية
- `nvp.cpp` معزول عن `x.kt` — الجافا هي الوسيط الوحيد.
- كل النصوص الحساسة في C++ عبر `constexpr XOR` (على نمط `sec.cpp` الحالي).
- `_exit(0)` بدل `System.exit` في C++ لتفادي hooks.
- Release build: `TostInfo.report()` يتحول إلى `_exit(0)` فوراً بغض النظر عن الـ toggle.

## المراجعة النهائية
- تأكيد أن `Ban/VIP/SSL` تعمل حصرياً من `nvp.cpp` عبر `Net.java`.
- تأكيد أن `tostinfo` لا يعرض شيئاً في Release أبداً.
- تأكيد نظافة `SplashActivity` و `g1/g2/g3` من أي Toast مباشر أو نصوص plaintext.
- Realtime يحدّث خلية واحدة فقط دون إعادة تحميل كامل.
