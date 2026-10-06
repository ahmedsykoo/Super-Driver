# Super Driver (Android) — T0 إلى T6

## الهيكل
- `engine/` وحدة Kotlin نقية (بلا android.*): `TextNormalizer`، `RideParser`، `RideEvaluator` + `ArabicFormat`. الاختبارات في `engine/src/test`.
- `app/` تطبيق Android:
  - `service/UberWatcherService` الخدمة (قراءة فقط) + `Nodes` + `TreeDumper` (النسخة الحقيقية في `src/debug`، ونسخة فارغة في `src/release`).
  - `overlay/OverlayController` النافذة العائمة (نوع النافذة في ثابت واحد `WINDOW_TYPE`).
  - `data/` Room: الإعدادات + سجل الطلبات (قرار التخزين: Room للاثنين لأنه مطلوب أصلاً للسجل).
  - `ui/` شاشات: الرئيسية، الإعداد الأولي، الإعدادات، السجل (مبنية بالكود، والنصوص كلها في `strings.xml`).
- `app/src/main/res/xml/accessibility_service_config.xml` فيه حزمة أوبر `com.ubercab.driver` (غير متحقق منها على جهاز).

## فتحه في Android Studio
1. File ← Open ← اختر مجلد `superdriver-android`.
2. اترك Studio يعمل Sync. لا يوجد `gradlew` ولا gradle-wrapper.jar (ملفات ثنائية)؛ Studio يستخدم `gradle-wrapper.properties` (Gradle 8.9). نسخ AGP 8.5.2 وKSP 2.0.21-1.0.28 وRoom 2.6.1 لم يُتحقق منها هنا؛ عدّلها لو اشتكى Sync.
3. شغّل `app` على جهاز (Debug).

## تشغيل الـ unit tests
من Android Studio: كليك يمين على `engine/src/test` ← Run. أو من الطرفية بعد Sync: `gradle :engine:test` (أو `./gradlew :engine:test` لو ولّدت wrapper).

## سحب سجل فحص T2 (Debug فقط)
بعد فتح شاشة طلب في أوبر: `adb pull /sdcard/Android/data/com.superdriver.app/files/tree-dump.txt` أو `adb logcat -s SDTree`.
