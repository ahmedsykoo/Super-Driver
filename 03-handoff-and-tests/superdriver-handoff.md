# Handoff — Super Driver (المجلس الأعلى)

## الحالة
أحمد (مصري، مباشر، عايز نتيجة مش شرح) بيبني "Super Driver": تطبيق Android لسواقين Uber مصر. بيقرأ شاشة طلب الرحلة بـ Accessibility، يحسب سعر/كم، ويعرض badge عائم (أخضر/أصفر/أحمر). قراءة وعرض فقط، من غير أي ضغط.
المراحل 0–3 (تحقق، مواصفات، تصميم، حزمة تسليم) اتوافق عليها. كود T0–T6 كتبه Claude Code وراجعته أنا، وسلّمت الـzip. أنا مبكتبش كود بنفسي (تعليمات أحمد الثابتة).
المرحلة الحالية: 4 (الاختبار) — مستنيين بناء وتجربة أحمد على جهازه.

## المراجع (متكررش محتواها)
- /mnt/user-data/outputs/SuperDriver-Handoff-for-Coding-Agent.md — الحزمة المعتمدة (قواعد، قراءة، T0–T9)
- /mnt/user-data/outputs/SuperDriver-Test-Plan-and-Acceptance.md — خطة الاختبار وقائمة القبول
- /mnt/user-data/outputs/SuperDriver-Android-Project.zip (المصدر: /mnt/user-data/outputs/superdriver-android/)
- التصميم المعتمد: https://claude.ai/artifact/SsJQY1iByGPoxE9PRYQMDM
- الذاكرة: /projects/01a0fdd7-b2de-7489-8d28-7114077259aa/areas/super-driver.md

## ما اتأكد وما لم يتأكد
- متأكد من القراءة: مفيش performAction/clicks، مفيش INTERNET، مفيش كود دفع/Supabase، Boost مش بيتجمع، الرقم غير المؤكد = badge رمادي.
- الوكيل أبلغ إن 22 اختبار engine نجحوا؛ أنا مقدرتش أعيد تشغيلهم (مفيش Maven/Android SDK في البيئة).
- مفيش build ولا تجربة جهاز. غير مؤكد: TYPE_ACCESSIBILITY_OVERLAY من غير SYSTEM_ALERT_WINDOW، باكدج Uber `com.ubercab.driver`، هل الشاشة نص ولا Canvas، شكل الـbadge (الوكيل ماشافش ملف التصميم).

## قرارات مفتوحة عند أحمد
1. قناة التوزيع: Play Store ولا APK مباشر (بتحدد T7/T8 والدفع، ومخاطرة سياسة Accessibility في Play).
2. الحكم بعد التقريب لرقم عشري (5.96→6.0 أخضر) ولا قبل التقريب.
3. نص الخصوصية العربي + applicationId `com.superdriver.app`.
4. صور شاشة الطلب بالإنجليزي.

## الخطوة الجاية
أحمد يبني debug بـ Android Studio، يشغّل T2 (تفريغ الشجرة) على طلب حقيقي، ويرجّع: الشجرة نص؟ الـoverlay اشتغل؟ اسم باكدج Uber الفعلي؟ بعدها قائمة القبول. T7 (Supabase/اشتراك 7 أيام تجربة، 50ج شهري، 575ج سنوي، تحقق server-side) وT8 (فودافون كاش أولاً ثم Paymob) مش مصرّح بيهم لسه.

## قواعد المجلس
رد بالعربي المصري؛ مرحلة بمرحلة بموافقة صريحة؛ مفيش اختراع APIs ولا تغيير تقنية بدون سؤال؛ artifact لكل مرحلة؛ ملخص "تم/الجاي" في آخر كل مرحلة؛ مفيش ادعاء بتنفيذ ما لم يحدث. أحمد مش عايز شاشات إضافية (privacy/stepper) دلوقتي.

## المهارات المقترحة
- superpowers:systematic-debugging (لو نتيجة T2 أو الـbuild فيها مشاكل)
- superpowers:verification-before-completion (قبل أي ادعاء إن حاجة اشتغلت)
- Supabase / vercel (T7 بعد قرار التوزيع)
- أي skill للـOCR/ML Kit لو T2 أثبتت إن الشاشة Canvas
