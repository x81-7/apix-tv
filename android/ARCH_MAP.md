# APiX — Security Architecture Map

## ملفات الحماية (مموهة)

| الاسم الحقيقي        | الاسم في الكود | الوظيفة                  |
|----------------------|----------------|--------------------------|
| KeysVault            | g4             | JNI Bridge للـ C++ Vault |
| HmacSigner           | g5             | توقيع طلبات Supabase     |
| AppDatabase          | p1             | Room DB مشفر SQLCipher   |
| ChannelEntity        | p2             | جدول القنوات             |
| CategoryEntity       | p3             | جدول التصنيفات           |
| ChannelDao           | p4             | استعلامات القنوات        |
| CategoryDao          | p5             | استعلامات التصنيفات      |
| OfflineDataRepository| p6             | منطق Offline-First       |
| channels (table)     | t2             | اسم جدول القنوات         |
| categories (table)   | t3             | اسم جدول التصنيفات       |
| apix_secure.db       | x.db           | اسم ملف قاعدة البيانات   |
| libapix_vault        | libv           | اسم مكتبة NDK            |

## تدفق المفاتيح