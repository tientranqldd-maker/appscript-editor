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

## v4.2 - Phóng chữ trong khung Code.gs
- Nút A− / A+ gửi hệ số zoom tới WebExtension cài trong GeckoView. Extension chỉ hoạt động với trang script.google.com, tác động hiển thị mã (Monaco/CodeMirror), không đọc hay gửi mã nguồn qua mạng.
- Không tải lại trang khi bấm zoom, hạn chế mất phần mã chưa lưu.
- Sửa luồng popup GeckoSession để tránh mở một session hai lần.
- Chưa thể đảm bảo mọi phiên bản trình biên tập Google sẽ nhận CSS zoom. Cần xác minh trực tiếp việc thay cỡ chữ, gõ và lưu.

## v4.3: Hai ngón trực tiếp trên vùng mã (pinch-to-code-zoom)

- Android nhận cử chỉ bằng `ScaleGestureDetector` trước GeckoView, vì editor Monaco bên trong Google Apps Script không chuyển cử chỉ pinch cho cơ chế zoom bình thường.
- Khi hai ngón bắt đầu ở khu vực mã của trang `script.google.com/home/projects/...`, Android gửi lệnh **cỡ chữ mã** qua WebExtension `code-zoom` vốn có; không gọi Apps Script API.
- Một ngón vẫn chạm, kéo cuộn, chọn và gõ mã bình thường. Các nút A−/A+ giữ nguyên.
- Chặn sự kiện pinch chỉ trong vùng phía phải của editor (nhận diện bằng vị trí tương đối trong GeckoView), không chặn menu/thanh công cụ APK.
- Thao tác zoom không tải lại trang. Giới hạn 65%–180%, cập nhật liên tục có giới hạn tần suất.
- **Cần thử trên điện thoại**: APK build thành công không xác nhận được cử chỉ hai ngón khi Google thay đổi bố cục; nếu menu Files mở rộng và vùng mã dịch sang trái, có thể cần cải thiện nhận diện ranh giới editor.

## v4.4 - Trang Apps Script phóng to/thu nhỏ đồng bộ
- Hai ngón tác động đến toàn trang Apps Script, gồm Files, code, menu và bố cục (thay vì zoom riêng chữ code).
- Menu của APK mặc định ẩn; nút ☰ nhỏ để hiện ô nhập link, chuyển PC, điều hướng, A−/A+, mở trình duyệt.
- Nút ✎ mở bảng cảm ứng 6 lệnh: Chọn tất cả / Sao chép / Dán / Cắt / Hoàn tác / Làm lại. Khi chọn văn bản GeckoView có thể hiện tự động.
- Các thao tác soạn thảo dùng SelectionActionDelegate hoặc phím Ctrl+A/C/V/X/Z/Y gửi vào GeckoView; không dùng Apps Script API, không đọc nội dung mã.
- Giữ nguyên đăng nhập Google trong GeckoView và mọi chức năng website gốc.
- Đã build APK, **chưa xác minh cảm ứng và clipboard trên điện thoại**. Không tự động đụng dữ liệu Google.
