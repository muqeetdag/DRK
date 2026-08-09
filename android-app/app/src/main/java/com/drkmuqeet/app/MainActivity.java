package com.drkmuqeet.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * DRKMUQEET - Device Tuning Console (Native APK wrapper)
 * - WebView loads http://127.0.0.1:8080 dashboard when available
 * - Fallback native UI uses root to run `drk` commands directly
 * - Safe: never disables thermal protection, never bypasses banking protections
 */
public class MainActivity extends Activity {

    private static final String TAG = "DRK";
    private static final String LOCAL_URL = "http://127.0.0.1:8080";
    private static final String CONTACT_URL = "https://t.me/DRKMUQEETT";

    private WebView webView;
    private LinearLayout fallbackLayout;
    private TextView profileText, batteryText, thermalText, uptimeText, statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root container
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#08090c"));
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Header
        TextView header = new TextView(this);
        header.setText("DRKMUQEET • Device Tuning Console");
        header.setTextColor(Color.parseColor("#c6ff45"));
        header.setTextSize(14);
        header.setPadding(24, 24, 24, 12);
        root.addView(header);

        TextView sub = new TextView(this);
        sub.setText("Begonia-aware, reversible tuning. Thermal safeguards stay enabled.");
        sub.setTextColor(Color.parseColor("#939baa"));
        sub.setTextSize(11);
        sub.setPadding(24, 0, 24, 16);
        root.addView(sub);

        // WebView - primary UI
        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Show fallback if local server unreachable
                if (failingUrl.contains("127.0.0.1")) {
                    showFallback();
                }
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep local URLs inside WebView, open external (telegram) externally via intent
                if (url.contains("t.me")) {
                    // Let WebView handle? For simplicity, let it load
                    return false;
                }
                if (url.startsWith(LOCAL_URL)) {
                    return false;
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(webView);

        // Fallback layout (hidden by default)
        fallbackLayout = createFallbackUI();
        fallbackLayout.setVisibility(View.GONE);
        root.addView(fallbackLayout);

        setContentView(root);

        // Try to load local dashboard
        webView.loadUrl(LOCAL_URL);

        // Also fetch stats periodically for fallback UI
        refreshStats();
    }

    private LinearLayout createFallbackUI() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(24, 24, 24, 24);

        statusText = new TextView(this);
        statusText.setText("Local dashboard unavailable (127.0.0.1:8080). Using direct root control.");
        statusText.setTextColor(Color.parseColor("#ff6174"));
        statusText.setPadding(0,0,0,20);
        container.addView(statusText);

        profileText = makeMetric(container, "ACTIVE PROFILE", "Loading…");
        batteryText = makeMetric(container, "BATTERY", "—");
        thermalText = makeMetric(container, "THERMAL", "—");
        uptimeText = makeMetric(container, "UPTIME", "—");

        // Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.VERTICAL);
        btnRow.setPadding(0, 20, 0, 0);

        Button perfBtn = makeButton("Apply performance", "#c6ff45", "#101300");
        perfBtn.setOnClickListener(v -> runDrkCommand("performance"));
        btnRow.addView(perfBtn);

        Button balBtn = makeButton("Apply balanced", "transparent", "#f0f2f5");
        balBtn.setOnClickListener(v -> runDrkCommand("balanced"));
        btnRow.addView(balBtn);

        Button resetBtn = makeButton("Restore defaults", "transparent", "#ff6174");
        resetBtn.setOnClickListener(v -> confirmReset());
        btnRow.addView(resetBtn);

        Button toggleBtn = makeButton("Toggle (root)", "#11131a", "#f0f2f5");
        toggleBtn.setOnClickListener(v -> runDrkCommand("toggle"));
        btnRow.addView(toggleBtn);

        container.addView(btnRow);

        TextView notice = new TextView(this);
        notice.setText("\nWhat this app will not do:\nIt does not bypass banking protections, falsify integrity, defeat ads or paid features, modify games, or disable thermal protection.");
        notice.setTextColor(Color.parseColor("#939baa"));
        notice.setTextSize(10);
        notice.setPadding(0, 30, 0, 0);
        container.addView(notice);

        TextView contact = new TextView(this);
        contact.setText("\nContact developer: @DRKMUQEETT on Telegram");
        contact.setTextColor(Color.parseColor("#c6ff45"));
        contact.setPadding(0, 20, 0, 0);
        contact.setOnClickListener(v -> webView.loadUrl(CONTACT_URL));
        container.addView(contact);

        scroll.addView(container);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.addView(scroll);
        return wrapper;
    }

    private TextView makeMetric(LinearLayout parent, String label, String value) {
        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor("#939baa"));
        lbl.setTextSize(9);
        parent.addView(lbl);
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Color.parseColor("#c6ff45"));
        val.setTextSize(18);
        val.setPadding(0,0,0,16);
        parent.addView(val);
        return val;
    }

    private Button makeButton(String text, String bg, String fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setPadding(20,24,20,24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 12);
        b.setLayoutParams(lp);
        try {
            if (!bg.equals("transparent")) {
                b.setBackgroundColor(Color.parseColor(bg));
            }
            b.setTextColor(Color.parseColor(fg));
        } catch (Exception ignored) {}
        return b;
    }

    private void showFallback() {
        runOnUiThread(() -> {
            webView.setVisibility(View.GONE);
            fallbackLayout.setVisibility(View.VISIBLE);
        });
    }

    private void refreshStats() {
        new AsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Void... voids) {
                // Try HTTP first
                try {
                    URL url = new URL(LOCAL_URL + "/cgi-bin/control?action=stats");
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setConnectTimeout(2000);
                    con.setReadTimeout(2000);
                    con.setRequestMethod("GET");
                    int code = con.getResponseCode();
                    if (code == 200) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        return new JSONObject(sb.toString());
                    }
                } catch (Exception e) {
                    Log.d(TAG, "HTTP stats failed, trying drk CLI: " + e.getMessage());
                }
                // Fallback to CLI
                try {
                    String out = execRootCommand("drk stats");
                    if (out != null && out.trim().startsWith("{")) {
                        return new JSONObject(out.trim());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "CLI stats failed", e);
                }
                return null;
            }
            @Override
            protected void onPostExecute(JSONObject obj) {
                if (obj != null) {
                    profileText.setText(obj.optString("profile", "balanced"));
                    batteryText.setText(obj.optString("battery", "—") + "% charge");
                    thermalText.setText(obj.optString("temperature", "—") + " raw");
                    uptimeText.setText(obj.optString("uptime", "—") + " sec");
                }
            }
        }.execute();
    }

    private void runDrkCommand(String action) {
        // First try HTTP control endpoint (when dashboard server is running)
        new AsyncTask<String, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(String... params) {
                String act = params[0];
                // Try HTTP
                try {
                    URL url = new URL(LOCAL_URL + "/cgi-bin/control?action=" + act);
                    HttpURLConnection con = (HttpURLConnection) url.openConnection();
                    con.setConnectTimeout(2000);
                    con.setReadTimeout(2000);
                    int code = con.getResponseCode();
                    if (code == 200) {
                        return true;
                    }
                } catch (Exception ignored) {}
                // Fallback to root CLI
                String result = execRootCommand("drk " + act);
                return result != null;
            }
            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    Toast.makeText(MainActivity.this, "Applied: " + action, Toast.LENGTH_SHORT).show();
                    refreshStats();
                    // If webview visible, reload
                    if (webView.getVisibility() == View.VISIBLE) {
                        webView.reload();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Failed. Need root & module installed.", Toast.LENGTH_LONG).show();
                }
            }
        }.execute(action);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Restore defaults?")
                .setMessage("This will reset CPU governors to schedutil (if available), I/O read-ahead to 128 KiB and animations to 1x.")
                .setPositiveButton("Reset", (d,w) -> runDrkCommand("reset"))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String execRootCommand(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            while ((line = err.readLine()) != null) output.append(line).append("\n");
            p.waitFor();
            return output.toString();
        } catch (Exception e) {
            Log.e(TAG, "Root exec failed", e);
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack() && webView.getVisibility() == View.VISIBLE) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
