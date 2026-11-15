package com.tientran.appscripttouch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.text.InputType;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Standalone Firefox/GeckoView engine for the original Apps Script editor.
 * No Apps Script API, OAuth client, tokens, DOM injection or HTTP proxy.
 * Web login, code edits and saves are handled by Google's website.
 */
public final class MainActivity extends Activity {
    private static final String HOME = "https://script.google.com/home";
    private static GeckoRuntime runtime;

    private GeckoView view;
    private GeckoSession active;
    private final Deque<GeckoSession> suspended = new ArrayDeque<>();
    private EditText address;
    private TextView status;
    private ProgressBar progress;
    private Button pcButton;
    private boolean desktop = true;
    private float textZoom = 1f;
    private String current = HOME;
    private WebExtension.Port codeZoomPort;

    private int dp(int i) { return Math.round(getResources().getDisplayMetrics().density * i); }

    private Button makeButton(String text, boolean primary) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setPadding(dp(3), 0, dp(3), 0);
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(31,102,200)));
        } else {
            b.setTextColor(Color.rgb(15,55,94));
        }
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        desktop = getPreferences(0).getBoolean("desktop", true);
        textZoom = getPreferences(0).getFloat("text_zoom", 1f);
        buildLayout();

        // Single GeckoRuntime per app process.
        if (runtime == null) {
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .forceUserScalableEnabled(true)
                    .inputAutoZoomEnabled(false)
                    .automaticFontSizeAdjustment(false)
                    .fontSizeFactor(1f)
                    .build();
            runtime = GeckoRuntime.create(getApplicationContext(), settings);
        } else {
            runtime.getSettings().setForceUserScalableEnabled(true);
            runtime.getSettings().setInputAutoZoomEnabled(false);
            runtime.getSettings().setFontSizeFactor(textZoom);
        }

        active = createSession(true);
        view.setSession(active);
        installEditorZoomExtension();
        String last = getPreferences(0).getString("last_project", HOME);
        address.setText(last);
        active.loadUri(last);
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("  Apps Script Touch  ·  Firefox");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setBackgroundColor(Color.rgb(15,43,78));
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout addressRow = new LinearLayout(this);
        addressRow.setPadding(dp(6), dp(2), dp(6), dp(2));
        root.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(53)));
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(13);
        address.setSelectAllOnFocus(true);
        address.setHint("Link Google Sheets, Apps Script hoặc địa chỉ web");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressRow.addView(address, new LinearLayout.LayoutParams(0, -1, 1f));
        Button open = makeButton("Mở", true);
        addressRow.addView(open, new LinearLayout.LayoutParams(dp(70), -1));
        open.setOnClickListener(v -> loadAddress());
        address.setOnEditorActionListener((v,action,event)->{ loadAddress(); return true; });

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(2), 0, dp(2), 0);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(43)));
        Button back=makeButton("‹",false);
        Button forward=makeButton("›",false);
        Button reload=makeButton("↻",false);
        pcButton=makeButton("PC",false);
        Button smaller=makeButton("A−",false);
        Button larger=makeButton("A+",false);
        Button chrome=makeButton("↗",false);
        Button[] buttons={back,forward,reload,pcButton,smaller,larger,chrome};
        float[] widths={0.65f,0.65f,0.65f,0.90f,0.8f,0.8f,0.65f};
        for(int i=0;i<buttons.length;i++)bar.addView(buttons[i],new LinearLayout.LayoutParams(0,-1,widths[i]));

        back.setOnClickListener(v->{ if (!suspended.isEmpty()) leavePopup(); else if(active!=null)active.goBack(); });
        forward.setOnClickListener(v->{ if(active!=null)active.goForward(); });
        reload.setOnClickListener(v->{ if(active!=null)active.reload(); });
        pcButton.setOnClickListener(v->{
            desktop=!desktop;
            getPreferences(0).edit().putBoolean("desktop",desktop).apply();
            applyDesktopMode(active, true);
        });
        smaller.setOnClickListener(v->changeTextZoom(-0.1f));
        larger.setOnClickListener(v->changeTextZoom(0.1f));
        chrome.setOnClickListener(v->openExternal());

        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));

        view = new GeckoView(this);
        root.addView(view,new LinearLayout.LayoutParams(-1,0,1f));

        status=new TextView(this);
        status.setTextSize(11);
        status.setTextColor(Color.rgb(80,92,111));
        status.setBackgroundColor(Color.rgb(242,246,252));
        status.setPadding(dp(9),dp(7),dp(9),dp(7));
        status.setText("A− / A+ điều chỉnh chữ vùng mã Apps Script, không tải lại trang.");
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        pcButton.setText(desktop?"PC ✓":"Mobile");
    }

    private GeckoSession createSession(boolean openNow) {
        GeckoSession s = new GeckoSession();
        s.setContentDelegate(new GeckoSession.ContentDelegate(){});
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
            @Override public void onLocationChange(GeckoSession sess, String url,
                    List<GeckoSession.PermissionDelegate.ContentPermission> perms, Boolean userGesture) {
                if (sess != active) return;
                if (url != null && url.startsWith("https://")) {
                    current=url;
                    address.setText(url);
                    Uri u=Uri.parse(url);
                    if("script.google.com".equalsIgnoreCase(u.getHost()))
                        getPreferences(0).edit().putString("last_project",url).apply();
                }
            }
            @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession from, String uri) {
                // Return unopened session, GeckoView opens it after callback.
                GeckoSession popup=createSession(false);
                if (active != null) suspended.push(active);
                active=popup;
                view.releaseSession();
                view.post(() -> {
                    if(active==popup) view.setSession(popup);
                });
                status.setText("Đang mở cửa sổ mới. Nhấn ‹ để trở lại dự án.");
                return GeckoResult.fromValue(popup);
            }
            @Override public void onCanGoBack(GeckoSession sess, boolean canGoBack) {}
        });
        s.setProgressDelegate(new GeckoSession.ProgressDelegate(){
            @Override public void onPageStart(GeckoSession sess,String url){
                if(sess==active){progress.setVisibility(View.VISIBLE);status.setText("Đang mở "+displayHost(url));}
            }
            @Override public void onProgressChange(GeckoSession sess,int percent){
                if(sess==active)progress.setProgress(percent);
            }
            @Override public void onPageStop(GeckoSession sess,boolean success){
                if(sess!=active)return;
                progress.setVisibility(View.GONE);
                if(success && isGoogleSheet(current))
                    status.setText("Đã mở Google Sheets. Để vào mã liên kết: Tiện ích mở rộng > Apps Script (nếu trình duyệt hiển thị menu).");
                else
                    status.setText(success?"Trang đã tải. Google quản lý việc chỉnh sửa và lưu mã."
                            :"Không tải được trang. Thử mở bằng trình duyệt ngoài ↗.");
            }
        });
        if (openNow) s.open(runtime);
        applyDesktopMode(s,false);
        return s;
    }

    private void applyDesktopMode(GeckoSession s, boolean reload) {
        if(s==null)return;
        GeckoSessionSettings settings=s.getSettings();
        settings.setUserAgentMode(desktop ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                                        : GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        settings.setViewportMode(desktop ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                                       : GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        if(pcButton!=null)pcButton.setText(desktop?"PC ✓":"Mobile");
        if(reload) {
            new AlertDialog.Builder(this)
              .setTitle("Tải lại để đổi chế độ?")
              .setMessage("Nếu đang sửa mã mà chưa lưu trên trang Google, tải lại có thể làm mất phần sửa.")
              .setNegativeButton("Hủy",(d,w)->{
                 desktop=!desktop;
                 settings.setUserAgentMode(desktop?GeckoSessionSettings.USER_AGENT_MODE_DESKTOP:GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
                 settings.setViewportMode(desktop?GeckoSessionSettings.VIEWPORT_MODE_DESKTOP:GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
                 pcButton.setText(desktop?"PC ✓":"Mobile");
                 getPreferences(0).edit().putBoolean("desktop",desktop).apply();
              })
              .setPositiveButton("Tải lại",(d,w)->s.reload()).show();
        }
    }

    // Changes editor typography only. Never modifies source code.
    private void changeTextZoom(float delta) {
        float next=Math.max(0.65f, Math.min(1.8f,
                Math.round((textZoom+delta)*20f)/20f));
        if(next==textZoom)return;
        textZoom=next;
        getPreferences(0).edit().putFloat("text_zoom",next).apply();
        sendEditorZoom();
    }

    private void sendEditorZoom() {
        if(codeZoomPort==null){
            status.setText("Zoom Code.gs: "+Math.round(textZoom*100)+"%. Đang kết nối tiện ích…");
            return;
        }
        try {
            JSONObject m=new JSONObject();
            m.put("type","codeZoom");
            m.put("factor",textZoom);
            codeZoomPort.postMessage(m);
            status.setText("Cỡ chữ Code.gs: "+Math.round(textZoom*100)+"%. Không tải lại trang.");
        }catch(Exception e){
            status.setText("Không điều chỉnh được chữ. Thử chụm hai ngón.");
        }
    }

    private void installEditorZoomExtension() {
        runtime.getWebExtensionController().ensureBuiltIn(
             "resource://android/assets/code-zoom/","codezoom@appscripttouch.local")
             .accept(extension -> extension.setMessageDelegate(
                 new WebExtension.MessageDelegate(){
                     @Override public void onConnect(WebExtension.Port port) {
                         codeZoomPort=port;
                         port.setDelegate(new WebExtension.PortDelegate(){
                             @Override public void onPortMessage(Object message,WebExtension.Port source){}
                             @Override public void onDisconnect(WebExtension.Port source){
                                 if(codeZoomPort==source)codeZoomPort=null;
                             }
                         });
                         sendEditorZoom();
                     }
                 },"appscripttouch"),
                 error -> status.setText("Chưa cài được tiện ích zoom: "+error.getMessage()));
    }

    private void loadAddress() {
        String target=parseTarget(address.getText().toString());
        if(target==null){
            new AlertDialog.Builder(this).setTitle("Liên kết không hợp lệ")
                    .setMessage("Hãy nhập địa chỉ web HTTPS hoặc liên kết Google Sheets / Apps Script hợp lệ.")
                    .setPositiveButton("OK",null).show();
            return;
        }
        InputMethodManager imm=(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null)imm.hideSoftInputFromWindow(address.getWindowToken(),0);
        address.clearFocus();
        while(!suspended.isEmpty())suspended.pop().close();
        if(active!=null)active.loadUri(target);
    }

    private String parseTarget(String source){
        String v=source.trim();
        if(v.isEmpty())return HOME;
        // A raw Script ID opens the Apps Script editor. Spreadsheet IDs must
        // arrive as a full Sheets link; they cannot identify the bound script.
        if(v.matches("[A-Za-z0-9_-]{20,}"))
            return "https://script.google.com/home/projects/"+v+"/edit";
        if("youtube".equalsIgnoreCase(v)||"youtube.com".equalsIgnoreCase(v))
            return "https://www.youtube.com/";
        if("appscript".equalsIgnoreCase(v)||"apps script".equalsIgnoreCase(v))
            return HOME;
        if(!v.contains("://")) {
            if(v.matches("(?i)^[A-Za-z0-9.-]+\\.[A-Za-z]{2,}([/:?#].*)?$"))
                v="https://"+v;
            else
                return "https://www.google.com/search?q="+Uri.encode(v);
        }
        try {
            Uri u=Uri.parse(v);
            if(!"https".equalsIgnoreCase(u.getScheme()))return null;
            String host=u.getHost();
            if(host==null||host.length()==0||host.contains(" ")||host.startsWith("."))
                return null;
            // Open Google Sheets and Docs directly instead of rejecting them.
            // Do not manufacture a Script ID from a spreadsheet ID.
            return u.toString();
        } catch(Exception e){return null;}
    }

    private boolean isGoogleSheet(String uri){
        try {
            Uri u=Uri.parse(uri);
            return "docs.google.com".equalsIgnoreCase(u.getHost())
                    && u.getPath()!=null && u.getPath().startsWith("/spreadsheets/");
        }catch(Exception e){return false;}
    }

    private String displayHost(String uri){
        try{return Uri.parse(uri).getHost();}catch(Exception e){return "Google";}
    }

    private void openExternal(){
        String url=current.startsWith("https://")?current:HOME;
        Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage("org.mozilla.firefox");
        try{startActivity(intent);}
        catch(ActivityNotFoundException ex){
            intent.setPackage(null);
            startActivity(intent);
        }
    }

    private void leavePopup(){
        if(suspended.isEmpty())return;
        GeckoSession popup=active;
        active=suspended.pop();
        view.releaseSession();
        view.setSession(active);
        popup.close();
        status.setText("Đã trở lại cửa sổ trước.");
    }

    @Override public void onBackPressed(){
        if(!suspended.isEmpty()){leavePopup();return;}
        if(active!=null){active.goBack();return;}
        super.onBackPressed();
    }

    @Override protected void onDestroy(){
        if(view!=null)view.releaseSession();
        if(active!=null&&active.isOpen())active.close();
        while(!suspended.isEmpty()){
            GeckoSession previous=suspended.pop();
            if(previous.isOpen())previous.close();
        }
        codeZoomPort=null;
        super.onDestroy();
    }
}