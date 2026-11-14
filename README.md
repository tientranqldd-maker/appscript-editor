# Apps Script Touch 2.0 — WebView Android editor

**Version:** 2.0.0 debug (chưa kiểm thử OAuth với tài khoản thật).

App Android uses a **bundled local WebView editor**, not the Google Apps Script page inside WebView. Editing controls are mobile-first: project URL / Script ID input, file tabs and drawer, line numbers, source editor, text search, tab and bracket insertion, font scaling, focus mode, line wrap, dark mode, and local draft recovery.

## Cách dùng

1. Cài APK từ GitHub Releases.
2. Chạm **Google** để cấp quyền qua Google Play Services (màn hình của Google chạy ngoài WebView).
3. Dán URL editor dạng `https://script.google.com/home/projects/<SCRIPT_ID>/edit` hoặc chỉ Script ID, rồi chạm **Mở**. **Link `/macros/s/.../exec` không phải Script ID**.
4. Chọn tệp `.gs`, `.html`, `appsscript.json` để chỉnh sửa. Tìm kiếm, phóng to chữ, ẩn thanh công cụ đều hoạt động cục bộ.
5. Chạm **Lưu lên Google**. App kiểm tra xung đột, sao lưu bản cũ trong bộ nhớ riêng rồi gọi `projects.updateContent`. App **không tự Deploy** và không đổi deployment đang phát hành.
6. Chạm **Mở bản gốc ↗** để mở Apps Script trên Chrome Custom Tabs với cookies của trình duyệt.

## Điều kiện đăng nhập và API

Quyền Editor trên file là cần thiết nhưng **không đủ** để một ứng dụng Android bên thứ ba dùng Apps Script API.

- Bật Apps Script API trong dự án Google Cloud và cấu hình OAuth consent screen / test users (nếu cần).
- Tạo OAuth Android client cho package `com.tientran.appscripttouch` với **SHA-1 của chính APK** (lấy từ bước `signingReport` của GitHub Actions; vì build debug không dùng signing key ổn định nên có thể khác nhau giữa các lần build).
- Bật `Apps Script API` trong https://script.google.com/home/usersettings nếu tài khoản yêu cầu.
- Không thu thập mật khẩu. Không có backend, proxy hay bên thứ ba lưu access token.
- **WebView chỉ tải `file:///android_asset/editor.html`**; các link từ WebView mở trong Chrome Custom Tabs. Mã người dùng chỉ hiển thị trong `<textarea>`, không được chạy như HTML/JS.

## An toàn dữ liệu

Google Apps Script `projects.updateContent` là phương thức **thay thế toàn bộ tệp dự án**. Trước khi lưu, app tải lại nội dung để kiểm tra có ai thay đổi tệp hay chưa; nếu khác sẽ ngừng ghi. Sau đó tạo JSON sao lưu vào bộ nhớ riêng của ứng dụng. Bản nháp cũng được lưu cục bộ và có thể phục hồi. Không được xóa app khi chưa lưu/copy bản nháp vì gỡ app sẽ xóa dữ liệu cục bộ.

## Build

GitHub Actions tự build APK debug bằng Gradle 8.10.2, JDK 17, Android SDK 35, tải artifact và tạo **GitHub Release pre-release** `v2.0.0-debug.N`.

`gradle --no-daemon :app:assembleDebug`

**Giới hạn:** Chưa xác minh đăng nhập và lưu mã trên thiết bị thực; nếu OAuth client chưa được đăng ký đúng package/SHA-1, nút Google có thể bị Google từ chối. Chưa có ký release production ổn định.