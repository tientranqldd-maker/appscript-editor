package com.tientran.appscripttouch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.text.InputType;
import android.text.TextUtils;

import java.util.Locale;

/**
 * Browser-only Apps Script launcher.
 * No Google API client, no OAuth client, no access tokens, no JavascriptInterface.
 * Google pages are opened as regular websites inside Android WebView.
 */
public final class MainActivity extends Activity {
    private static final String HOME = "https://script.google.com/home";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

    private WebView browser;
    private EditText urlField;
    private TextView status;
    private Button desktopButton;
    private ProgressBar progress;
    private String mobileUa;
    private boolean desktop;

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private Button button(String title, boolean primary) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : Color.rgb(19, 53, 89));
        if (primary) b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(35, 110, 210)));
        return b;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        desktop = getPreferences(0).getBoolean("desktop", true);
        buildUi();
        String start = getPreferences(0).getString("last_url", HOME);
        if (savedState != null) {
            browser.restoreState(savedState);
            if (browser.getUrl() == null) browser.loadUrl(start);
        } else {
            browser.loadUrl(start);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        TextView heading = new TextView(this);
        heading.setBackgroundColor(Color.rgb(18, 48, 82));
        heading.setTextColor(Color.WHITE);
        heading.setText("  Apps Script Touch  ·  WEBVIEW");
        heading.setTextSize(18);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinHeight(dp(52));
        root.addView(heading, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout addressBar = new LinearLayout(this);
        addressBar.setPadding(dp(6), dp(3), dp(6), dp(2));
        addressBar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(addressBar, new LinearLayout.LayoutParams(-1, dp(54)));

        urlField = new EditText(this);
        urlField.setSingleLine(true);
        urlField.setTextSize(13);
        urlField.setHint("Dán liên kết Apps Script đã chia sẻ");
        urlField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlField.setSelectAllOnFocus(true);
        urlField.setText(HOME);
        addressBar.addView(urlField, new LinearLayout.LayoutParams(0, -1, 1f));

        Button go = button("Mở", true);
        addressBar.addView(go, new LinearLayout.LayoutParams(dp(72), -1));
        go.setOnClickListener(v -> {
            String target = parseTarget(urlField.getText().toString());
            if (target == null) {
                showInfo("Liên kết không hợp lệ",
                        "Hãy dán liên kết trên script.google.com, hoặc Script ID. Không dùng liên kết /exec của Web App.");
                return;
            }
            hideKeyboard();
            browser.loadUrl(target);
        });
        urlField.setOnEditorActionListener((v, actionId, event) -> {
            go.performClick();
            return true;
        });

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setPadding(dp(3), 0, dp(3), 0);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(47)));

        Button back = button("‹", false);
        Button next = button("›", false);
        Button refresh = button("↻", false);
        desktopButton = button("PC: BẬT", false);
        Button outside = button("Chrome ↗", false);
        toolbar.addView(back, new LinearLayout.LayoutParams(0, -1, 0.6f));
        toolbar.addView(next, new LinearLayout.LayoutParams(0, -1, 0.6f));
        toolbar.addView(refresh, new LinearLayout.LayoutParams(0, -1, 0.7f));
        toolbar.addView(desktopButton, new LinearLayout.LayoutParams(0, -1, 1.3f));
        toolbar.addView(outside, new LinearLayout.LayoutParams(0, -1, 1.7f));
        back.setOnClickListener(v -> { if (browser.canGoBack()) browser.goBack(); });
        next.setOnClickListener(v -> { if (browser.canGoForward()) browser.goForward(); });
        refresh.setOnClickListener(v -> browser.reload());
        desktopButton.setOnClickListener(v -> {
            desktop = !desktop;
            getPreferences(0).edit().putBoolean("desktop", desktop).apply();
            applyDesktopMode(true);
        });
        outside.setOnClickListener(v -> openChrome(browser.getUrl() == null ? HOME : browser.getUrl()));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        browser = new WebView(this);
        configureBrowser();
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1f));

        status = new TextView(this);
        status.setTextColor(Color.rgb(77, 89, 105));
        status.setBackgroundColor(Color.rgb(244, 247, 250));
        status.setTextSize(11);
        status.setPadding(dp(9), dp(7), dp(9), dp(7));
        status.setText("Chỉ mở trang Google. Không sử dụng Google API hay OAuth riêng.");
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
    }

    private void configureBrowser() {
        WebSettings s = browser.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setSafeBrowsingEnabled(true);
        mobileUa = s.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);
        applyDesktopMode(false);

        browser.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith("https://script.google.com/")) {
                    getPreferences(0).edit().putString("last_url", url).apply();
                }
                if (url != null) urlField.setText(url);
                status.setText("Trang Google · " + (desktop ? "giao diện PC" : "giao diện mobile")
                        + " · Google tự xử lý việc lưu trong trang");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("Không tải được trang. Có thể mở bằng Chrome ↗.");
                }
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void applyDesktopMode(boolean reload) {
        browser.getSettings().setUserAgentString(desktop ? DESKTOP_UA : mobileUa);
        browser.setInitialScale(desktop ? 75 : 0);
        if (desktopButton != null) desktopButton.setText(desktop ? "PC: BẬT" : "PC: TẮT");
        if (reload) browser.reload();
    }

    private String parseTarget(String raw) {
        String v = raw.trim();
        if (v.isEmpty()) return HOME;
        if (v.matches("[A-Za-z0-9_-]{20,}")) {
            return "https://script.google.com/home/projects/" + v + "/edit";
        }
        if (!v.contains("://")) v = "https://" + v;
        try {
            Uri uri = Uri.parse(v);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return null;
            if (!"script.google.com".equalsIgnoreCase(uri.getHost())) return null;
            if (uri.getPath() != null && uri.getPath().contains("/exec")) return null;
            return uri.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void openChrome(String url) {
        String safe = url != null && url.startsWith("https://") ? url : HOME;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safe));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage("com.android.chrome");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            intent.setPackage(null);
            try { startActivity(intent); }
            catch (Exception ex) { showInfo("Không tìm thấy trình duyệt", "Hãy cài Chrome hoặc trình duyệt khác."); }
        }
    }

    private void openExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(intent);
        } catch (Exception e) {
            status.setText("Không mở được liên kết bên ngoài.");
        }
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(urlField.getWindowToken(), 0);
        urlField.clearFocus();
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
    }

    @Override
    public void onBackPressed() {
        if (browser != null && browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (browser != null) browser.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (browser != null) {
            ((ViewGroup) browser.getParent()).removeView(browser);
            browser.destroy();
            browser = null;
        }
        super.onDestroy();
    }
}