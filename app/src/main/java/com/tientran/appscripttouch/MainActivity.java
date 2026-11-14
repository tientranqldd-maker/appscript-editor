package com.tientran.appscripttouch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Apps Script Touch: trusted offline HTML editor in WebView; Google authorization
 * is performed by Google Play Services, not by a login page inside WebView.
 * The native bridge is never exposed to a remote web page.
 */
public class MainActivity extends Activity {
    private static final int AUTH_REQUEST = 504;
    private static final String SCOPE = "https://www.googleapis.com/auth/script.projects";
    private static final String HOME = "file:///android_asset/editor.html";
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{20,}$");
    private static final int MAX_DRAFT_BYTES = 7 * 1024 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private WebView web;
    private AuthorizationClient authorization;
    private String accessToken;
    private Runnable pendingAuthorizedAction;
    private JSONArray baselineFiles;
    private String loadedProjectId;
    private boolean busy;
    private boolean awaitingAuthorization;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff0b1525);
        getWindow().setNavigationBarColor(0xff0b1525);
        authorization = Identity.getAuthorizationClient(this);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true); // Only bundled editor.html is allowed.
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                if (HOME.equals(request.getUrl().toString())) return false;
                // All navigation from bundled HTML goes to the external browser, never
                // load foreign content into the privileged WebView.
                String url = request.getUrl().toString();
                if (url.startsWith("https://")) openCustomTab(url);
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (HOME.equals(url)) return super.shouldInterceptRequest(view, request);
                // Blocking remote resources protects the JavascriptInterface bridge.
                return new WebResourceResponse("text/plain", "UTF-8",
                        new java.io.ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new EditorBridge(), "AppBridge");
        setContentView(web);
        web.loadUrl(HOME);
    }

    private class EditorBridge {
        @JavascriptInterface public void ready() {
            runOnUiThread(() -> {
                JSONObject payload = new JSONObject();
                try {
                    payload.put("lastProject", getPreferences(0).getString("last_project", ""));
                    payload.put("connected", accessToken != null);
                    event("ready", payload);
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface public void authorize() { runOnUiThread(() -> requestAuthorization(null)); }
        @JavascriptInterface public void openProject(String raw) { runOnUiThread(() -> openProjectUi(raw)); }
        @JavascriptInterface public void saveProject(String rawJson) { runOnUiThread(() -> confirmSave(rawJson)); }
        @JavascriptInterface public void openOriginal(String raw) { runOnUiThread(() -> {
            String id = parseId(raw);
            if (id == null) id = loadedProjectId;
            if (id == null) openCustomTab("https://script.google.com/home");
            else openCustomTab("https://script.google.com/home/projects/" + id + "/edit");
        }); }
        @JavascriptInterface public void saveDraft(String raw) {
            if (raw == null || raw.length() > MAX_DRAFT_BYTES || loadedProjectId == null) return;
            final String id = loadedProjectId;
            executor.execute(() -> {
                try {
                    JSONObject obj = new JSONObject(raw);
                    if (!id.equals(obj.optString("id"))) return;
                    JSONArray arr = obj.optJSONArray("files");
                    if (arr == null || arr.length() == 0) return;
                    writeBytes(draftFile(id), raw.getBytes(StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface public void discardDraft() {
            if (loadedProjectId == null) return;
            final String id = loadedProjectId;
            executor.execute(() -> draftFile(id).delete());
        }
        @JavascriptInterface public void disconnect() { runOnUiThread(() -> {
            String previous = accessToken;
            accessToken = null;
            event("disconnected", new JSONObject());
            if (previous != null && !previous.isEmpty()) {
                authorization.clearToken(com.google.android.gms.auth.api.identity.ClearTokenRequest.builder()
                    .setToken(previous).build());
            }
        }); }
    }

    private void event(String type, JSONObject data) {
        runOnUiThread(() -> {
            try {
                JSONObject object = new JSONObject();
                object.put("type", type);
                object.put("data", data == null ? new JSONObject() : data);
                web.evaluateJavascript("window.AppReceive(" + JSONObject.quote(object.toString()) + ")", null);
            } catch (Exception ignored) { }
        });
    }
    private void status(String message, String kind) {
        try {
            event("status", new JSONObject().put("message", message).put("kind", kind));
        } catch (Exception ignored) { }
    }
    private void setBusy(boolean value, String message) {
        busy = value;
        try {
            event("busy", new JSONObject().put("value", value).put("message", message));
        } catch (Exception ignored) { }
    }
    private void showError(String title, String message) {
        runOnUiThread(() -> {
            status(message, "error");
            new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Đóng", null).show();
        });
    }

    private void requestAuthorization(Runnable after) {
        if (awaitingAuthorization) return;
        pendingAuthorizedAction = after;
        awaitingAuthorization = true;
        status("Đang mở màn hình cấp quyền Google…", "loading");
        AuthorizationRequest request = new AuthorizationRequest.Builder()
            .setRequestedScopes(Collections.singletonList(new Scope(SCOPE))).build();
        authorization.authorize(request)
            .addOnSuccessListener(this, result -> {
                try {
                    if (result.hasResolution()) {
                        PendingIntent pending = result.getPendingIntent();
                        if (pending == null) throw new Exception("Không nhận được màn hình cấp quyền Google.");
                        startIntentSenderForResult(pending.getIntentSender(), AUTH_REQUEST,
                                null, 0, 0, 0);
                    } else onAuthorized(result);
                } catch (Exception e) {
                    awaitingAuthorization = false;
                    pendingAuthorizedAction = null;
                    showError("Cấp quyền Google", describeAuthError(e));
                }
            })
            .addOnFailureListener(this, error -> {
                awaitingAuthorization = false;
                pendingAuthorizedAction = null;
                showError("Cấp quyền Google", describeAuthError(error));
            });
    }
    private String describeAuthError(Exception e) {
        String message = e.getMessage() == null ? e.toString() : e.getMessage();
        return message + "\n\nNếu Google từ chối ứng dụng, cần đăng ký OAuth Android cho đúng package và SHA-1 của APK, đồng thời bật Google Apps Script API. Quyền Editor trên file không thay thế quyền cho ứng dụng.";
    }
    private void onAuthorized(AuthorizationResult result) {
        awaitingAuthorization = false;
        accessToken = result.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            pendingAuthorizedAction = null;
            showError("Chưa có quyền", "Google chưa trả mã truy cập Apps Script.");
            return;
        }
        status("Google đã cấp quyền. Có thể mở dự án.", "success");
        event("authorized", new JSONObject());
        Runnable next = pendingAuthorizedAction;
        pendingAuthorizedAction = null;
        if (next != null) next.run();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode != AUTH_REQUEST) return;
        if (resultCode == RESULT_OK && intent != null) {
            try { onAuthorized(authorization.getAuthorizationResultFromIntent(intent)); }
            catch (Exception e) {
                awaitingAuthorization = false;
                pendingAuthorizedAction = null;
                showError("Google", describeAuthError(e));
            }
        } else {
            awaitingAuthorization = false;
            pendingAuthorizedAction = null;
            status("Chưa cấp quyền Google. Chạm 'Kết nối Google' để thử lại.", "warning");
        }
    }

    private String parseId(String raw) {
        if (raw == null) return null;
        String candidate = raw.trim();
        Matcher m = Pattern.compile("/projects/([A-Za-z0-9_-]{20,})(?:/|\\?|$)").matcher(candidate);
        if (m.find()) return m.group(1);
        return ID.matcher(candidate).matches() ? candidate : null;
    }
    private void openProjectUi(String raw) {
        if (busy) return;
        String id = parseId(raw);
        if (id == null) {
            showError("Chưa đúng liên kết", "Dán Script ID hoặc liên kết .../projects/SCRIPT_ID/edit.\n\nLink /macros/s/.../exec là đường triển khai, không phải Script ID.");
            return;
        }
        if (accessToken == null) requestAuthorization(() -> loadProject(id));
        else loadProject(id);
    }
    private void loadProject(String id) {
        setBusy(true, "Đang đọc mã nguồn Google…");
        executor.execute(() -> {
            try {
                JSONObject payload = api("GET", id, null);
                JSONArray files = payload.getJSONArray("files");
                if (files.length() == 0) throw new Exception("Dự án này chưa có tệp.");
                baselineFiles = files;
                loadedProjectId = id;
                getPreferences(0).edit().putString("last_project", id).apply();
                JSONObject data = new JSONObject().put("id", id).put("files", files);
                File saved = draftFile(id);
                if (saved.isFile() && saved.length() < MAX_DRAFT_BYTES) {
                    try {
                        String json = new String(java.nio.file.Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
                        JSONObject draft = new JSONObject(json);
                        if (id.equals(draft.optString("id")) && draft.has("files") &&
                                !sameContent(files, draft.getJSONArray("files"))) {
                            data.put("draft", draft.getJSONArray("files"));
                        } else saved.delete();
                    } catch (Exception ignored) { }
                }
                event("loaded", data);
                setBusy(false, "Đã mở " + files.length() + " tệp.");
            } catch (Exception e) {
                setBusy(false, "Không mở được dự án.");
                showError("Không mở được dự án", explainApiError(e));
            }
        });
    }
    private void confirmSave(String rawJson) {
        if (busy || loadedProjectId == null || baselineFiles == null) return;
        if (rawJson == null || rawJson.length() > MAX_DRAFT_BYTES) {
            showError("Dự án quá lớn", "Không thể gửi quá 7 MB mỗi lần lưu."); return;
        }
        try {
            JSONArray draft = new JSONArray(rawJson);
            if (draft.length() == 0) throw new Exception("Danh sách tệp trống.");
            int manifests = 0;
            Set<String> names = new HashSet<>();
            JSONArray normalized = new JSONArray();
            for (int i = 0; i < draft.length(); i++) {
                JSONObject f = draft.getJSONObject(i);
                String name = f.getString("name"), type = f.getString("type"), source = f.getString("source");
                if (!name.matches("[A-Za-z0-9_-]{1,120}"))
                    throw new Exception("Tên tệp không hợp lệ: " + name);
                if (!type.equals("JSON") && !type.equals("HTML") && !type.equals("SERVER_JS"))
                    throw new Exception("Loại tệp không hợp lệ: " + name);
                if (!names.add(name)) throw new Exception("Tên tệp bị trùng: " + name);
                if (name.equals("appsscript") && type.equals("JSON")) {
                    manifests++;
                    new JSONObject(source);
                }
                normalized.put(new JSONObject().put("name", name).put("type", type).put("source", source));
            }
            if (manifests != 1) throw new Exception("Cần giữ nguyên một tệp appsscript.json hợp lệ.");
            if (sameContent(normalized, baselineFiles)) {
                status("Không có thay đổi để lưu.", "info"); return;
            }
            new AlertDialog.Builder(this).setTitle("Lưu thay đổi lên Google?")
                .setMessage("Sẽ kiểm tra xung đột và lưu bản sao cục bộ trước khi gửi toàn bộ " + normalized.length() + " tệp lên Apps Script. Không tự Deploy.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (dialog, which) -> saveProject(normalized)).show();
        } catch (Exception e) { showError("Chưa thể lưu", e.getMessage()); }
    }
    private void saveProject(JSONArray draft) {
        final String id = loadedProjectId;
        final JSONArray baseline = baselineFiles;
        setBusy(true, "Đang kiểm tra thay đổi từ Google…");
        executor.execute(() -> {
            try {
                JSONArray remote = api("GET", id, null).getJSONArray("files");
                if (!sameContent(baseline, remote))
                    throw new Exception("Dự án trên Google đã bị chỉnh sửa sau khi bạn mở. Không ghi đè. Hãy sao chép mã đang sửa, tải lại dự án và đối chiếu.");
                backup(id, baseline);
                JSONObject updated = api("PUT", id, new JSONObject().put("files", draft));
                JSONArray result = updated.optJSONArray("files");
                if (result == null) result = draft;
                baselineFiles = result;
                draftFile(id).delete();
                event("saved", new JSONObject().put("files", result));
                setBusy(false, "Đã lưu lên Google và sao lưu bản cũ.");
            } catch (Exception e) {
                setBusy(false, "Chưa lưu: nội dung đang sửa vẫn được giữ trên điện thoại.");
                showError("Lưu thất bại", explainApiError(e));
            }
        });
    }
    private boolean sameContent(JSONArray a, JSONArray b) throws Exception {
        if (a.length() != b.length()) return false;
        Map<String, String> byName = new HashMap<>();
        for (int i=0; i<a.length(); i++) {
            JSONObject f=a.getJSONObject(i);
            byName.put(f.getString("name") + "\u0000" + f.getString("type"), f.optString("source", ""));
        }
        if (byName.size() != a.length()) return false;
        Set<String> visited = new HashSet<>();
        for (int i=0; i<b.length(); i++) {
            JSONObject f=b.getJSONObject(i);
            String key=f.getString("name") + "\u0000" + f.getString("type");
            if (!visited.add(key) || !byName.containsKey(key) ||
                    !byName.get(key).equals(f.optString("source", ""))) return false;
        }
        return true;
    }
    private File draftFile(String id) { return new File(getFilesDir(), "draft_" + id + ".json"); }
    private void writeBytes(File file, byte[] bytes) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.flush(); }
    }
    private void backup(String id, JSONArray old) throws Exception {
        File dir = new File(getFilesDir(), "local_backups");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục sao lưu.");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        File target = new File(dir, id + "_" + stamp + ".json");
        writeBytes(target, new JSONObject().put("files", old).toString(2).getBytes(StandardCharsets.UTF_8));
    }
    private JSONObject api(String method, String scriptId, JSONObject body) throws Exception {
        URL endpoint = new URL("https://script.googleapis.com/v1/projects/" + scriptId + "/content");
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        try {
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out=conn.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code=conn.getResponseCode();
            InputStream input = code>=200 && code<300 ? conn.getInputStream():conn.getErrorStream();
            if (input==null) throw new Exception("Google HTTP " + code);
            StringBuilder response=new StringBuilder();
            try (BufferedReader reader=new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line; while ((line=reader.readLine()) != null) response.append(line).append('\n');
            }
            if (code<200 || code>=300) throw new Exception("Google HTTP " + code + ": " + response);
            return new JSONObject(response.toString());
        } finally { conn.disconnect(); }
    }
    private String explainApiError(Exception e) {
        String message=e.getMessage()==null ? e.toString() : e.getMessage();
        if (message.contains("HTTP 401")) {
            accessToken=null;
            event("disconnected", new JSONObject());
            return "Phiên Google hết hạn. Nhấn Kết nối Google rồi thử lại.\n\n" + message;
        }
        if (message.contains("HTTP 403"))
            return message + "\n\nKiểm tra: tài khoản có quyền Editor, Apps Script API được bật và tài khoản được cấp phép OAuth.";
        return message;
    }
    private void openCustomTab(String url) {
        if (!url.startsWith("https://")) return;
        try { new CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, android.net.Uri.parse(url)); }
        catch (Exception e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
            catch (Exception ignored) { Toast.makeText(this, "Không có trình duyệt mở liên kết", Toast.LENGTH_SHORT).show(); }
        }
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("window.EditorApp && window.EditorApp.handleBack()", result -> {
            if ("true".equals(result)) return;
            if ("\"dirty\"".equals(result)) {
                new AlertDialog.Builder(this).setTitle("Có mã chưa lưu")
                    .setMessage("Thay đổi đã có bản nháp trong app, nhưng chưa lưu lên Google. Thoát?")
                    .setNegativeButton("Ở lại", null)
                    .setPositiveButton("Thoát", (d,w) -> finish()).show();
            } else finish();
        });
    }
    @Override protected void onDestroy() {
        if (web != null) {
            web.removeJavascriptInterface("AppBridge");
            web.destroy();
        }
        executor.shutdownNow();
        super.onDestroy();
    }
}