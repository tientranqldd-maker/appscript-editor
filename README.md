# Apps Script Touch 3.0 — WebView thuần (không API)

Ứng dụng Android mở **trực tiếp** trang `https://script.google.com/home` trong WebView, có chế độ desktop, làm mới, điều hướng và nút mở Chrome.

**Đã loại bỏ:** Google Apps Script REST API, OAuth SDK, Google Identity, access token, bridge JavaScript, trình chỉnh sửa HTML cục bộ và nút lưu qua API. Không có server hay proxy.

- Dán link `https://script.google.com/home/projects/<SCRIPT_ID>/edit` hoặc chỉ Script ID.
- Chế độ **PC: BẬT/TẮT** giả lập trình duyệt máy tính.
- Quay lại, tiến tới, tải lại, mở cùng trang trong Chrome.
- Google kiểm soát đăng nhập, quyền chia sẻ, thao tác sửa và lưu nội dung trên trang chính thức.
- Nếu Google chặn đăng nhập WebView hoặc editor không hoạt động trên điện thoại, dùng nút **Chrome ↗**. WebView không thể vượt hạn chế từ Google; app không can thiệp, không giả lập login qua API.

Phát hành GitHub Releases là **APK debug**; chưa xác minh được chỉnh sửa/lưu Apps Script trong WebView trên thiết bị thực. Lưu ý bản 3 dùng cùng applicationId với bản trước nhưng debug signing key mỗi lần build có thể khác; có thể cần gỡ bản cũ nếu Android báo không thể cập nhật. Nếu có bản nháp chưa lưu trên v2, sao chép ra ngoài trước khi gỡ ứng dụng.

Build: `gradle --no-daemon :app:assembleDebug`.
