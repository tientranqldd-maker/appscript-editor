package com.tientran.appscripttouch;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.GoogleIdentity;
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
import java.util.ArrayList;
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
 * Native Android editor for existing Google Apps Script projects.
 * Uses Google's AuthorizationClient (never collects account passwords) and
 * the official Apps Script API. No WebView, third-party proxy, or remote server.
 */
public class MainActivity extends Activity {
    private static final int AUTH_REQUEST = 504;
    private static final String SCOPE = "https://www.googleapis.com/auth/script.projects";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AuthorizationClient authorization;
    private String accessToken;
    private Runnable pendingAuthorizedAction;

    private EditText projectInput;
    private EditText codeEditor;
    private TextView status;
    private Spinner fileSpinner;
    private ArrayAdapter<String> fileAdapter;
    private final ArrayList<ScriptFile> files = new ArrayList<>();
    private JSONArray baselineFiles;
    private String loadedProjectId;
    private int selectedFile = -1;
    private boolean switchInProgress;
    private boolean busy;
    private Button loadBtn, saveBtn, addBtn;

    static class ScriptFile {
        String name, type, source;
        ScriptFile(String name, String type, String source) {
            this.name = name; this.type = type; this.source = source;
        }
        String displayName() {
            return name + ("SERVER_JS".equals(type) ? ".gs" : "HTML".equals(type) ? ".html" : ".json");
        }
        JSONObject asJson() throws Exception {
            JSONObject obj = new JSONObject();
            obj.put("name", name); obj.put("type", type); obj.put("source", source);
            return obj;
        }
    }

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        authorization = GoogleIdentity.getAuthorizationClient(this);
        setupUi();
        projectInput.setText(getPreferences(0).getString("last_project", ""));
    }

    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(12);
        b.setAllCaps(false); b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }
    private void setupUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(246, 248, 253));
        root.setPadding(dp(12), dp(10), dp(12), dp(6));
        setContentView(root);
        TextView heading = new TextView(this);
        heading.setText("Apps Script Touch"); heading.setTextSize(22); heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(Color.rgb(18, 69, 129));
        root.addView(heading);
        TextView subtitle = new TextView(this);
        subtitle.setText("Sửa Code.gs, HTML và appsscript.json trực tiếp trên điện thoại");
        subtitle.setTextSize(12); subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle);

        projectInput = new EditText(this);
        projectInput.setSingleLine(true); projectInput.setTextSize(13);
        projectInput.setHint("Dán link /projects/…/edit hoặc Script ID");
        projectInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(projectInput, new LinearLayout.LayoutParams(-1, dp(47)));

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(actions, new LinearLayout.LayoutParams(-1, dp(48)));
        Button login = button("Google");
        loadBtn = button("Mở dự án"); saveBtn = button("Lưu lên Google");
        actions.addView(login, new LinearLayout.LayoutParams(0, -1, 0.75f));
        actions.addView(loadBtn, new LinearLayout.LayoutParams(0, -1, 1.1f));
        actions.addView(saveBtn, new LinearLayout.LayoutParams(0, -1, 1.3f));
        login.setOnClickListener(v -> authorize(null));
        loadBtn.setOnClickListener(v -> openProject());
        saveBtn.setOnClickListener(v -> confirmSave());

        LinearLayout filesHeader = new LinearLayout(this); filesHeader.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(filesHeader, new LinearLayout.LayoutParams(-1, dp(50)));
        fileSpinner = new Spinner(this);
        fileAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        fileSpinner.setAdapter(fileAdapter);
        filesHeader.addView(fileSpinner, new LinearLayout.LayoutParams(0, -1, 1));
        addBtn = button("+ Tệp"); filesHeader.addView(addBtn, new LinearLayout.LayoutParams(dp(76), -1));
        addBtn.setOnClickListener(v -> showAddFileDialog());
        fileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switchFile(position);
            }
        });

        codeEditor = new EditText(this);
        codeEditor.setGravity(Gravity.TOP | Gravity.START);
        codeEditor.setTypeface(Typeface.MONOSPACE);
        codeEditor.setTextSize(13);
        codeEditor.setPadding(dp(10), dp(12), dp(10), dp(12));
        codeEditor.setBackgroundColor(Color.WHITE);
        codeEditor.setTextColor(Color.rgb(30, 36, 45));
        codeEditor.setHint("Mở dự án để xem mã nguồn");
        codeEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeEditor.setHorizontallyScrolling(false);
        root.addView(codeEditor, new LinearLayout.LayoutParams(-1, 0, 1));

        status = new TextView(this); status.setTextSize(12);
        status.setTextColor(Color.rgb(60, 70, 85)); status.setPadding(dp(1), dp(6), dp(1), dp(3));
        status.setText("Chưa đăng nhập. Mã chỉ tải về và lưu qua API Google.");
        root.addView(status);
        refreshButtons();
    }
    private void message(String m) { runOnUiThread(() -> status.setText(m)); }
    private void alert(String title, String detail) {
        runOnUiThread(() -> new AlertDialog.Builder(this).setTitle(title).setMessage(detail)
                .setPositiveButton("Đóng", null).show());
    }
    private void refreshButtons() {
        loadBtn.setEnabled(!busy);
        saveBtn.setEnabled(!busy && baselineFiles != null && !files.isEmpty());
        addBtn.setEnabled(!busy && baselineFiles != null);
    }
    private void setBusy(boolean value, String m) {
        runOnUiThread(() -> { busy = value; refreshButtons(); status.setText(m); });
    }
    private void authorize(Runnable after) {
        pendingAuthorizedAction = after;
        AuthorizationRequest request = new AuthorizationRequest.Builder()
                .setRequestedScopes(Collections.singletonList(new Scope(SCOPE))).build();
        message("Đang xin quyền truy cập Apps Script…");
        authorization.authorize(request)
                .addOnSuccessListener(this, result -> {
                    try {
                        if (result.hasResolution()) {
                            PendingIntent resolution = result.getPendingIntent();
                            startIntentSenderForResult(resolution.getIntentSender(), AUTH_REQUEST,
                                    null, 0, 0, 0);
                        } else {
                            onAuthorized(result);
                        }
                    } catch (Exception e) { alert("Không đăng nhập được", e.getMessage()); }
                })
                .addOnFailureListener(this, e -> alert("Không đăng nhập được",
                        e.getMessage() + "\nKiểm tra Google Play Services và cấu hình OAuth Android."));
    }
    private void onAuthorized(AuthorizationResult result) {
        accessToken = result.getAccessToken();
        if (accessToken == null || accessToken.isEmpty()) {
            alert("Chưa cấp quyền", "Google không trả access token."); return;
        }
        message("Đã cấp quyền Apps Script. Có thể mở dự án.");
        Runnable todo = pendingAuthorizedAction; pendingAuthorizedAction = null;
        if (todo != null) todo.run();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != AUTH_REQUEST) return;
        if (result == RESULT_OK && data != null) {
            try { onAuthorized(authorization.getAuthorizationResultFromIntent(data)); }
            catch (Exception e) { alert("Lỗi xác thực", e.getMessage()); }
        } else message("Đã hủy cấp quyền Google.");
    }

    private String parseId(String input) {
        String str = input.trim();
        Matcher match = Pattern.compile("/projects/([A-Za-z0-9_-]+)/?").matcher(str);
        if (match.find()) return match.group(1);
        if (str.matches("[A-Za-z0-9_-]{20,}")) return str;
        return null;
    }
    private void openProject() {
        String id = parseId(projectInput.getText().toString());
        if (id == null) {
            alert("Thiếu Script ID", "Hãy dán liên kết dạng https://script.google.com/home/projects/SCRIPT_ID/edit.\nKhông dùng link web app /exec vì đó là Deployment ID.");
            return;
        }
        if (baselineFiles != null && hasDraftChanges()) {
            new AlertDialog.Builder(this).setTitle("Có nội dung chưa lưu")
                .setMessage("Mở dự án khác sẽ bỏ các thay đổi chưa gửi lên Google.")
                .setNegativeButton("Hủy", null).setPositiveButton("Tiếp tục", (d, w) -> loadWithAuth(id)).show();
        } else loadWithAuth(id);
    }
    private void loadWithAuth(String id) {
        if (accessToken == null) authorize(() -> loadProject(id));
        else loadProject(id);
    }
    private void loadProject(String id) {
        setBusy(true, "Đang đọc dự án từ Google…");
        executor.execute(() -> {
            try {
                JSONObject content = api("GET", id, null);
                JSONArray arr = content.getJSONArray("files");
                if (arr.length() == 0) throw new Exception("Dự án không có tệp.");
                ArrayList<ScriptFile> loaded = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.getJSONObject(i);
                    loaded.add(new ScriptFile(f.getString("name"), f.getString("type"), f.optString("source", "")));
                }
                runOnUiThread(() -> {
                    baselineFiles = arr; loadedProjectId = id;
                    files.clear(); files.addAll(loaded); selectedFile = -1;
                    switchInProgress = true;
                    fileAdapter.clear(); for (ScriptFile f : files) fileAdapter.add(f.displayName());
                    fileAdapter.notifyDataSetChanged();
                    switchInProgress = false;
                    fileSpinner.setSelection(0);
                    switchFile(0);
                    getPreferences(0).edit().putString("last_project", id).apply();
                    setBusy(false, "Đã mở " + files.size() + " tệp. Sửa rồi nhấn 'Lưu lên Google'.");
                });
            } catch (Exception e) { setBusy(false, "Mở thất bại"); alert("Không mở được dự án", explainError(e)); }
        });
    }
    private void syncCurrentEdit() {
        if (selectedFile >= 0 && selectedFile < files.size())
            files.get(selectedFile).source = codeEditor.getText().toString();
    }
    private void switchFile(int index) {
        if (switchInProgress || index < 0 || index >= files.size()) return;
        syncCurrentEdit();
        selectedFile = index;
        String source = files.get(index).source;
        codeEditor.setText(source);
        codeEditor.setSelection(0);
    }
    private void showAddFileDialog() {
        if (baselineFiles == null) return;
        EditText input = new EditText(this);
        input.setSingleLine(); input.setHint("ViDu.gs hoặc giao_dien.html");
        new AlertDialog.Builder(this).setTitle("Thêm tệp mới")
            .setView(input).setNegativeButton("Hủy", null)
            .setPositiveButton("Thêm", (d, which) -> {
                String raw = input.getText().toString().trim();
                boolean html = raw.endsWith(".html"), js = raw.endsWith(".gs");
                if (!html && !js) { alert("Tên không hợp lệ", "Tên tệp phải kết thúc .gs hoặc .html"); return; }
                String name = raw.substring(0, raw.lastIndexOf('.'));
                if (!name.matches("[A-Za-z0-9_-]{1,70}")) {
                    alert("Tên không hợp lệ", "Tên chỉ gồm chữ Latin, số, dấu - và _. "); return;
                }
                for (ScriptFile f : files) if (f.name.equalsIgnoreCase(name)) {
                    alert("Trùng tên", "Dự án đã có tệp " + name); return;
                }
                syncCurrentEdit();
                files.add(new ScriptFile(name, html ? "HTML" : "SERVER_JS", ""));
                fileAdapter.add(files.get(files.size() - 1).displayName());
                fileAdapter.notifyDataSetChanged(); fileSpinner.setSelection(files.size() - 1);
            }).show();
    }
    private boolean hasDraftChanges() {
        syncCurrentEdit();
        if (baselineFiles == null || files.size() != baselineFiles.length()) return baselineFiles != null;
        try {
            return !sameContent(baselineFiles, draftArray());
        } catch (Exception e) { return true; }
    }
    private void confirmSave() {
        if (baselineFiles == null || loadedProjectId == null) return;
        if (!hasDraftChanges()) { Toast.makeText(this, "Không có thay đổi", Toast.LENGTH_SHORT).show(); return; }
        try {
            // Ensure unchanged manifest is present; the API deletes omitted files.
            boolean manifest = false;
            for (ScriptFile f : files) if (f.name.equals("appsscript") && f.type.equals("JSON")) {
                manifest = true; new JSONObject(f.source);
            }
            if (!manifest) throw new Exception("Không thấy appsscript.json");
        } catch (Exception e) { alert("Chưa thể lưu", "Manifest appsscript.json phải là JSON hợp lệ. " + e.getMessage()); return; }
        new AlertDialog.Builder(this).setTitle("Lưu dự án lên Google?")
            .setMessage("Ứng dụng sẽ tạo bản sao lưu trước, kiểm tra dự án có bị sửa từ thiết bị khác hay không, sau đó gửi toàn bộ tệp bằng Apps Script API.")
            .setNegativeButton("Hủy", null).setPositiveButton("Lưu", (d, which) -> save()).show();
    }
    private void save() {
        syncCurrentEdit();
        final String id = loadedProjectId;
        final JSONArray oldSnapshot = baselineFiles;
        final JSONArray toSave;
        try { toSave = draftArray(); }
        catch (Exception e) { alert("Lỗi mã", e.getMessage()); return; }
        setBusy(true, "Đang đối chiếu dữ liệu trên Google…");
        executor.execute(() -> {
            try {
                JSONArray remote = api("GET", id, null).getJSONArray("files");
                if (!sameContent(oldSnapshot, remote)) {
                    throw new IllegalStateException("CONFLICT: Dự án đã được sửa ở nơi khác. Không ghi đè. Hãy sao chép phần mã mới rồi tải lại dự án.");
                }
                backup(id, oldSnapshot);
                JSONObject request = new JSONObject(); request.put("files", toSave);
                JSONObject res = api("PUT", id, request);
                JSONArray newBaseline = res.optJSONArray("files");
                if (newBaseline == null) newBaseline = toSave;
                final JSONArray safeBaseline = newBaseline;
                runOnUiThread(() -> {
                    baselineFiles = safeBaseline;
                    setBusy(false, "Đã lưu thành công " + files.size() + " tệp lên Google. Có bản sao lưu cục bộ.");
                    Toast.makeText(this, "Đã lưu thành công", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) { setBusy(false, "Chưa lưu - dữ liệu trên Google vẫn giữ nguyên."); alert("Lưu thất bại", explainError(e)); }
        });
    }
    private JSONArray draftArray() throws Exception {
        JSONArray arr = new JSONArray();
        for (ScriptFile f : files) arr.put(f.asJson());
        return arr;
    }
    /** Compare by name/type/source, ignoring read-only functionSet metadata and ordering. */
    private boolean sameContent(JSONArray a, JSONArray b) throws Exception {
        if (a.length() != b.length()) return false;
        Map<String,String> left = new HashMap<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject f = a.getJSONObject(i);
            left.put(f.getString("name") + "\u0000" + f.getString("type"), f.optString("source", ""));
        }
        if (left.size() != a.length()) return false;
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < b.length(); i++) {
            JSONObject f = b.getJSONObject(i);
            String key = f.getString("name") + "\u0000" + f.getString("type");
            if (!seen.add(key) || !left.containsKey(key) || !left.get(key).equals(f.optString("source", ""))) return false;
        }
        return true;
    }
    private void backup(String id, JSONArray snapshot) throws Exception {
        File dir = new File(getFilesDir(), "local_backups");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("Không tạo được thư mục sao lưu");
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File out = new File(dir, id + "_" + stamp + ".json");
        try (FileOutputStream stream = new FileOutputStream(out)) {
            stream.write(new JSONObject().put("files", snapshot).toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
    private JSONObject api(String method, String scriptId, JSONObject body) throws Exception {
        URL endpoint = new URL("https://script.googleapis.com/v1/projects/" + scriptId + "/content");
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        try {
            conn.setConnectTimeout(20000); conn.setReadTimeout(30000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setRequestProperty("Accept", "application/json");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) { out.write(data); }
            }
            int responseCode = conn.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) throw new Exception("Google HTTP " + responseCode);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            }
            if (responseCode < 200 || responseCode >= 300)
                throw new Exception("Google HTTP " + responseCode + ": " + sb);
            return new JSONObject(sb.toString());
        } finally { conn.disconnect(); }
    }
    private String explainError(Exception e) {
        String s = e.getMessage();
        if (s == null) s = e.toString();
        if (s.contains("HTTP 403")) return s + "\n\nHãy bật Apps Script API trong Google Cloud và bật Apps Script API access tại https://script.google.com/home/usersettings.";
        if (s.contains("HTTP 401")) {
            accessToken = null;
            return "Phiên Google hết hạn. Nhấn Google để đăng nhập lại.\n\n" + s;
        }
        return s;
    }
    @Override public void onBackPressed() {
        if (baselineFiles != null && hasDraftChanges()) {
            new AlertDialog.Builder(this).setTitle("Có mã chưa lưu")
                .setMessage("Thoát ứng dụng và bỏ thay đổi chưa lưu?")
                .setNegativeButton("Ở lại", null).setPositiveButton("Thoát", (d, w) -> super.onBackPressed()).show();
        } else super.onBackPressed();
    }
    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
