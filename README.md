# Apps Script Touch v4.1 — GeckoView browser (experimental)

An Android application with the **Firefox GeckoView** rendering engine embedded in the APK. Opens the **original** Google Apps Script website in a browser view without Google Apps Script REST API, Google OAuth app clients, tokens, or content scraping. Google controls access, editing, save, and login.

## Changes from v3
- Replaces Android WebView with Mozilla GeckoView (separate Gecko engine, not Chrome Android System WebView).
- Opens https://script.google.com/home and project editor links **inside APK**.
- Allows direct navigation, optional desktop viewport, login-window handling, pinch-to-zoom even on sites disabling user zoom, and text size A-/A+.
- Chrome/Firefox ↗ opens the current site in an external browser as a fallback.
- No additional permissions beyond INTERNET.

## Important limitation
A different engine **does not guarantee Google will allow embedded sign-in**, nor that the Apps Script web editor supports Android input. **Building an APK does not verify login or editing.** If Google rejects GeckoView, it cannot be bypassed by changing User-Agent or pretending to be a supported browser. No password is collected by the APK.

## Build
Github Actions builds a **debug APK** for arm64-v8a Android. GeckoView dependency from Mozilla Maven. The APK will be larger than v3. Use the [Releases](https://github.com/tientranqldd-maker/appscript-editor/releases) page once the CI workflow is successful. The debug signing certificate may change between builds, so upgrading previous debug APKs may require uninstalling (back up any data first).

## v4.1 — hỗ trợ liên kết Google Sheets

- Nhận trực tiếp `https://docs.google.com/spreadsheets/d/.../edit?usp=sharing` thay vì từ chối vì không thuộc domain `script.google.com`.
- Mở được link Google Sheets, Google Docs, Apps Script hoặc trang HTTPS khác bằng động cơ GeckoView. Gõ `youtube` để mở YouTube.
- Link Google Sheets **không phải** Script ID. Không có API nên app không tự tra mã Apps Script đính kèm Sheet. Mở Sheet trước, từ giao diện desktop chọn **Tiện ích mở rộng → Apps Script** nếu menu này hiển thị.
- Google vẫn quyết định việc đăng nhập và quyền sửa/lưu. Chưa xác minh được chỉnh sửa thật trong GeckoView trên máy Android.
