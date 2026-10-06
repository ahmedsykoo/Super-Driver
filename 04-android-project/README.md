# Super Driver (Android) — T0 إلى T6

## الهيكل
- `engine/` وحدة Kotlin نقية (بلا android.*): `TextNormalizer`، `RideParser`، `RideEvaluator` + `ArabicFormat`. الاختبارات في `engine/src/test`.
- `app/` تطبيق Android:
  - `service/UberWatcherService` الخدمة (قراءة فقط) + `Nodes` + `TreeDumper` (النسخة الحقيقية في `src/debug`، ونسخة فارغة في `src/release`).
  - `overlay/OverlayController` النافذة العائمة (نوع النافذة في ثابت واحد `WINDOW_TYPE`).
  - `data/` Room (الإعدادات + كاش جلسة الدخول) و`Auth.kt` (Supabase Auth بكود إيميل OTP — التفاصيل والإعداد المطلوب في تعليق الملف). مفيش سجل رحلات، بقرار أحمد.
  - `ui/` شاشات: تسجيل الدخول، الرئيسية، الإعداد الأولي (checklist ٣ خطوات)، الإعدادات (مبنية بالكود، والنصوص كلها في `strings.xml`).
- `app/src/main/res/xml/accessibility_service_config.xml` فيه حزمة أوبر `com.ubercab.driver` (غير متحقق منها على جهاز).

## فتحه في Android Studio
1. File ← Open ← اختر مجلد `superdriver-android`.
2. اترك Studio يعمل Sync. لا يوجد `gradlew` ولا gradle-wrapper.jar (ملفات ثنائية)؛ Studio يستخدم `gradle-wrapper.properties` (Gradle 8.9). نسخ AGP 8.5.2 وKSP 2.0.21-1.0.28 وRoom 2.6.1 لم يُتحقق منها هنا؛ عدّلها لو اشتكى Sync.
3. شغّل `app` على جهاز (Debug).

## تشغيل الـ unit tests
من Android Studio: كليك يمين على `engine/src/test` ← Run. أو من الطرفية بعد Sync: `gradle :engine:test` (أو `./gradlew :engine:test` لو ولّدت wrapper).

## بناء APK من GitHub (من غير Android Studio)
`.github/workflows/android-debug-apk.yml` في جذر الريبو بيبني `assembleDebug` تلقائيًا مع كل push ويرفع الـ APK كـ artifact على تبويب Actions. مفيد لأن `gradlew`/`gradle-wrapper.jar` مش موجودين فعليًا في الريبو (بند فوق) — الـ workflow بيستخدم Gradle 8.9 مباشرة بدالهم. ملاحظة: الـ APK ده من غير مفاتيح Supabase (لأن `local.properties` متعمد يكون غير متتبع في git)، فشاشة تسجيل الدخول هتفشل فيه لحد ما تحط مفاتيحك وتبني نسخة محلية.

## سحب سجل فحص T2 (Debug فقط)
بعد فتح شاشة طلب في أوبر: `adb pull /sdcard/Android/data/com.superdriver.app/files/tree-dump.txt` أو `adb logcat -s SDTree`.
