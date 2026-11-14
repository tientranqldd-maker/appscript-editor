# Apps Script Touch — ứng dụng Android sửa Google Apps Script

Mã nguồn ứng dụng Android **native** để mở và chỉnh sửa dự án Google Apps Script trên điện thoại, không dùng giao diện editor trên Chrome.

## Tính năng bản đầu

- Đăng nhập qua quyền Google Play Services AuthorizationClient, không thu thập mật khẩu.
- Dán **Script ID** hoặc URL dạng `https://script.google.com/home/projects/SCRIPT_ID/edit`.
- Mở, chọn, chỉnh sửa và lưu `.gs`, `.html`, `appsscript.json` của dự án hiện có.
- Thêm tệp `.gs` / `.html`, giữ nội dung sửa khi đổi tab tệp.
- Trước khi ghi: so sánh bản trên Google với thời điểm mở, tạo sao lưu nội bộ, yêu cầu bấm xác nhận.
- Không tự ý triển khai (Deploy), xóa tệp hoặc thay đổi dự án không được chọn.

## Tải APK

Chọn **Actions → Build Apps Script Touch APK → bản chạy mới nhất → Artifacts → AppsScriptTouch-debug-APK** rồi giải nén để lấy `app-debug.apk`.

APK mới tạo là **debug/test** và cần cấu hình Google OAuth trước khi có thể truy cập dự án của bạn. **Chưa có xác nhận kiểm thử chức năng đăng nhập/sửa/lưu trên thiết bị thực.**

## Bắt buộc để đăng nhập Google thành công

1. Tại Google Cloud Console, tạo/chọn dự án Cloud riêng, bật **Google Apps Script API**, thiết lập OAuth consent screen và Test users (nếu ở trạng thái Testing).
2. Tạo OAuth Client ID loại **Android** với package `com.tientran.appscripttouch` và **SHA-1 certificate** chính xác của file APK vừa build. SHA-1 có in ở bước `Print Android OAuth SHA-1 fingerprint` trong GitHub Actions. Mỗi lần build bằng debug keystore khác nhau có thể có SHA-1 khác; chỉ dùng một bản APK đã đăng ký hoặc dùng khóa ký ổn định do bạn quản lý khi phát hành.
3. Bật Apps Script API trong https://script.google.com/home/usersettings bằng đúng tài khoản sử dụng.
4. Cấp quyền truy cập phạm vi `https://www.googleapis.com/auth/script.projects`; tài khoản phải có quyền sửa dự án Apps Script bạn muốn mở.
5. Chạy ứng dụng → **Google** → cấp quyền → dán Script ID → **Mở dự án**.

Lưu ý: Link triển khai Web app (`/exec`) **không phải** Script ID. Mở trình soạn thảo gốc và lấy link `/projects/SCRIPT_ID/edit` hoặc vào **Project Settings → Script ID**.

Nguồn: https://developers.google.com/apps-script/api/how-tos/enable và https://developers.google.com/apps-script/api/reference/rest/v1/projects/updateContent

## Phát triển

- `app/src/main/java/com/tientran/appscripttouch/MainActivity.java`: UI và API đọc/ghi.
- `app/build.gradle`: cấu hình ứng dụng Android.
- `.github/workflows/build-apk.yml`: build tự động GitHub Actions.
- Android Studio mở trực tiếp repository hoặc chạy Gradle 8.10.2 + JDK 17 + Android SDK 35:
  `gradle :app:assembleDebug`.

## An toàn dữ liệu

`projects.updateContent` thay thế toàn bộ tệp trong dự án; ứng dụng gửi lại tất cả tệp hiện có (bao gồm manifest) và so sánh nội dung trước khi lưu. So sánh không phải khóa nguyên tử; vẫn không nên chỉnh một dự án đồng thời trên nhiều thiết bị. Bản sao lưu được giữ nội bộ dưới `files/local_backups` của ứng dụng, có thể mất khi gỡ ứng dụng. Không đưa OAuth token, API key, mã nguồn dự án riêng, hay signing keystore vào GitHub.
