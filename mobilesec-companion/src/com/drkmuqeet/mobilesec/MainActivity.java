package com.drkmuqeet.mobilesec;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** A loopback-only UI for the upstream dashboard. No JavaScript-to-native bridge. */
public final class MainActivity extends Activity {
    private static final int FILE_REQUEST = 47;
    private static final int BG = Color.rgb(12, 19, 29), PANEL = Color.rgb(25, 37, 50), WHITE = Color.rgb(232, 244, 242);
    private SharedPreferences prefs;
    private LinearLayout shell;
    private WebView web;
    private ValueCallback<Uri[]> chooser;
    private int accent;
    private String port;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("preferences", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        accent = prefs.getInt("accent", 0xff55e9ba);
        port = prefs.getString("port", "8000");
        splash();
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setTextColor(WHITE); b.setTextSize(12); b.setAllCaps(false);
        b.setBackground(shape(PANEL, 12)); return b;
    }
    private void splash() {
        LinearLayout l = new LinearLayout(this); l.setGravity(Gravity.CENTER); l.setOrientation(LinearLayout.VERTICAL); l.setBackgroundColor(BG);
        TextView mark = text("✦", 66, accent); mark.setGravity(Gravity.CENTER); l.addView(mark);
        TextView title = text("DRK MOBILESEC", 25, WHITE); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER); l.addView(title);
        TextView subtitle = text("SECURITY, IN YOUR HANDS", 11, 0xff91a7a5); subtitle.setLetterSpacing(.18f); subtitle.setGravity(Gravity.CENTER); l.addView(subtitle);
        TextView credit = text("by @DRKMUQEET", 15, accent); credit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(32); l.addView(credit, cp);
        setContentView(l);
        mark.setAlpha(0); mark.setScaleX(.65f); mark.setScaleY(.65f); mark.animate().alpha(1).scaleX(1).scaleY(1).setDuration(700).start();
        l.setAlpha(0); l.animate().alpha(1).setDuration(450).start();
        new Handler(Looper.getMainLooper()).postDelayed(() -> { if (!isFinishing()) showHome(); }, 1500);
    }
    private void showHome() {
        if (web != null) { ((ViewGroup) web.getParent()).removeView(web); web.destroy(); web = null; }
        shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL); shell.setBackgroundColor(BG); setContentView(shell);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(16),dp(10),dp(16),dp(10));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("DRK MOBILESEC", 16, WHITE); title.setTypeface(null, Typeface.BOLD); labels.addView(title);
        labels.addView(text("COMPANION  /  @DRKMUQEET", 10, accent));
        header.addView(labels, new LinearLayout.LayoutParams(0,-2,1));
        Button setup = button("Settings"); setup.setOnClickListener(v -> settings()); header.addView(setup, new LinearLayout.LayoutParams(dp(86),dp(42)));
        shell.addView(header);
        LinearLayout actions = new LinearLayout(this); actions.setPadding(dp(12),0,dp(12),dp(10));
        Button connect = button("Open dashboard"); connect.setOnClickListener(v -> openDashboard());
        Button root = button("Root check"); root.setOnClickListener(v -> confirmRoot());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,dp(42),1); half.setMargins(dp(4),0,dp(4),0); actions.addView(connect,half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0,dp(42),1); half2.setMargins(dp(4),0,dp(4),0); actions.addView(root,half2); shell.addView(actions);
        web = new WebView(this); web.setBackgroundColor(BG);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(true); // Required for user-selected content URIs in uploads.
        s.setJavaScriptCanOpenWindowsAutomatically(false); s.setSupportMultipleWindows(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isLocal(uri)) return false;
                if (request.isForMainFrame() && request.hasGesture() && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException ignored) { toast("No browser available"); }
                }
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (isLocal(u) || "data".equals(u.getScheme()) || "blob".equals(u.getScheme())) return null;
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null); chooser = callback;
                try { startActivityForResult(params.createIntent(), FILE_REQUEST); return true; }
                catch (ActivityNotFoundException e) { chooser = null; callback.onReceiveValue(null); toast("No file picker found"); return false; }
            }
        });
        web.setDownloadListener((url, userAgent, disposition, mimetype, length) -> {
            Uri u = Uri.parse(url);
            if (!isLocal(u)) { toast("Only loopback report downloads are supported"); return; }
            try {
                DownloadManager.Request r = new DownloadManager.Request(u);
                r.setTitle("MobileSec report"); r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                String name = android.webkit.URLUtil.guessFileName(url, disposition, mimetype).replaceAll("[^a-zA-Z0-9._-]", "_");
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(r); toast("Downloading report");
            } catch (Exception e) { toast("Could not start download: " + e.getMessage()); }
        });
        shell.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        welcome();
    }
    private boolean isLocal(Uri uri) {
        return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == Integer.parseInt(port) && uri.getUserInfo() == null;
    }
    private void welcome() {
        String html = "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{background:#0c131d;color:#e8f4f2;font:16px sans-serif;padding:25px;line-height:1.55}h1{font-size:28px}small{color:#55e9ba;letter-spacing:2px}p{color:#afc1c2}.box{background:#192532;border-radius:15px;padding:18px;margin-top:20px}</style><small>YOUR SECURITY WORKSPACE</small><h1>Analyze with confidence.</h1><p>Connect to your own lu77U-MobileSec server to use its dashboard, scanners and reports.</p><div class='box'><b>1.</b> Start lu77U-MobileSec on your computer.<br><b>2.</b> Connect your phone over USB and run <code>adb reverse tcp:" + port + " tcp:" + port + "</code> on the computer.<br><b>3.</b> Tap Open dashboard.</div><p>Root check is optional and asks for approval. This app does not run the analysis engine itself.</p>";
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }
    private void openDashboard() { web.loadUrl("http://127.0.0.1:" + port + "/"); }
    private void settings() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(10),dp(24),0);
        TextView intro = text("Use the server's actual loopback port. Connect via USB adb reverse; do not expose the upstream server to your network.",14,0xff303a40); box.addView(intro);
        EditText entry = new EditText(this); entry.setInputType(2); entry.setText(port); entry.setHint("Server port"); box.addView(entry);
        TextView label = text("Accent color (app chrome and next launch)",14,0xff303a40); box.addView(label);
        LinearLayout colors = new LinearLayout(this);
        int[] options = {0xff55e9ba,0xff7db9ff,0xffbba1ff,0xffffb56b}; final int[] selected = {accent};
        for (int color : options) { Button b = new Button(this); b.setText("●"); b.setTextColor(color); b.setTextSize(24); b.setOnClickListener(v -> { selected[0] = color; toast("Accent selected"); }); colors.addView(b,new LinearLayout.LayoutParams(0,dp(55),1)); } box.addView(colors);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Companion settings").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            String p = entry.getText().toString().trim();
            try { int n = Integer.parseInt(p); if (n < 1 || n > 65535) throw new NumberFormatException(); port = Integer.toString(n); }
            catch (NumberFormatException ex) { entry.setError("Enter a port from 1 to 65535"); return; }
            accent = selected[0]; prefs.edit().putString("port",port).putInt("accent",accent).apply(); dialog.dismiss(); showHome();
        })); dialog.show();
    }
    private void confirmRoot() {
        new AlertDialog.Builder(this).setTitle("Optional root check")
          .setMessage("This requests superuser permission and runs only 'id' to verify the effective identity. No changes are made. The upstream scanner runs on your computer, not as root on this phone.")
          .setNegativeButton("Cancel",null).setPositiveButton("Request root", (d,w) -> rootCheck()).show();
    }
    private void rootCheck() {
        toast("Waiting for superuser approval…");
        new Thread(() -> {
            Process p = null; String result;
            try {
                p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
                if (!p.waitFor(20,TimeUnit.SECONDS)) { p.destroyForcibly(); result = "Root request timed out or was not approved."; }
                else { InputStream in = p.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[512]; int n; while ((n=in.read(buffer)) != -1 && out.size() < 4096) out.write(buffer,0,n); result = "Exit code: " + p.exitValue() + "\n" + out.toString("UTF-8"); }
            } catch (Exception e) { result = "Root unavailable or denied: " + e.getClass().getSimpleName(); }
            final String message = result; runOnUiThread(() -> { if (!isFinishing()) new AlertDialog.Builder(this).setTitle("Root check result").setMessage(message).setPositiveButton("OK",null).show(); });
        }).start();
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_SHORT).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request == FILE_REQUEST && chooser != null) { chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data)); chooser = null; }
    }
    @Override public void onBackPressed() { if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); }
    @Override protected void onDestroy() { if (chooser != null) { chooser.onReceiveValue(null); chooser = null; } if (web != null) { ((ViewGroup)web.getParent()).removeView(web); web.destroy(); web = null; } super.onDestroy(); }
}
