# سیاست حریم خصوصی — ARVIO (PersonalVault)

آخرین به‌روزرسانی: ۲۰۲۶

## خلاصه
ARVIO یک اپلیکیشن کاملاً آفلاین است. هیچ داده‌ای از گوشی شما به هیچ سروری (نه سرورهای ما، نه هیچ شخص ثالثی) ارسال نمی‌شود.

## چه داده‌هایی ذخیره می‌شوند؟
- یادداشت‌های متنی، عکس‌ها، فایل‌ها و اسناد اسکن‌شده‌ای که خودتان در اپ اضافه می‌کنید.
- رمز عبور (PIN) به‌صورت هش‌شده و نمکی (salted hash)، نه به‌صورت متن ساده.
- یادآوری‌هایی که تنظیم می‌کنید (عنوان، تاریخ، ساعت).
- تنظیمات اپ (تم، زبان، فعال/غیرفعال بودن قفل).

همه‌ی این اطلاعات فقط و فقط روی حافظه‌ی داخلی گوشی شما، در یک پایگاه‌داده‌ی محلی (Room/SQLite) ذخیره می‌شوند.

## این داده‌ها کجا می‌روند؟
هیچ‌جا. اپ به اینترنت متصل نمی‌شود و هیچ سروری ندارد. هیچ SDK تبلیغاتی، آنالیتیکس، یا ردیابی در اپ استفاده نشده است.

بکاپ خودکار ابری (Android Auto Backup) برای این اپ **غیرفعال** است، بنابراین محتوای ولت شما هرگز به‌صورت خودکار به گوگل‌درایو یا سرویس‌های مشابه ارسال نمی‌شود.

## دسترسی‌هایی که اپ درخواست می‌کند
- **دوربین**: فقط زمانی که خودتان روی «اسکن سند» بزنید.
- **نوتیفیکیشن**: برای نمایش یادآوری‌هایی که خودتان تنظیم کرده‌اید.
- **هشدار دقیق (Alarms & reminders)**: برای اینکه یادآوری‌ها سر وقت دقیق زنگ بزنند.

هیچ‌کدام از این دسترسی‌ها برای جمع‌آوری یا ارسال داده به کار نمی‌روند.

## حذف داده‌ها
حذف اپ از گوشی، تمام داده‌های ذخیره‌شده (یادداشت‌ها، عکس‌ها، فایل‌ها، یادآوری‌ها، تنظیمات) را برای همیشه پاک می‌کند، چون هیچ نسخه‌ای جای دیگری ذخیره نشده.

## تماس
برای سوالات مربوط به حریم خصوصی یا پشتیبانی، به آدرس Newlifetech25@hotmail.com ایمیل بزن.

---

# Privacy Policy — ARVIO (PersonalVault)

Last updated: 2026

## Summary
ARVIO is a fully offline app. No data ever leaves your device — not to our servers (we don't have any), and not to any third party.

## What data is stored
- Text notes, photos, files, and scanned documents that you add yourself.
- Your PIN, stored as a salted cryptographic hash — never as plain text.
- Reminders you create (title, date, time).
- App settings (theme, language, whether lock is enabled).

All of this lives only in a local database (Room/SQLite) on your device's internal storage.

## Where this data goes
Nowhere. The app makes no network requests and has no backend server. It contains no advertising, analytics, or tracking SDKs.

Android's automatic cloud backup is explicitly **disabled** for this app, so vault contents are never silently uploaded to Google Drive or similar services.

## Permissions requested
- **Camera**: only when you tap "scan document".
- **Notifications**: to show reminders you set yourself.
- **Exact alarms**: so reminders ring at the precise time you chose.

None of these permissions are used to collect or transmit data.

## Data deletion
Uninstalling the app permanently deletes everything stored (notes, photos, files, reminders, settings), since nothing is stored anywhere else.

## Contact
For privacy or support questions, email Newlifetech25@hotmail.com.
