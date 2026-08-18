package com.drkmuqeet.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final String DASHBOARD_URL = "http://127.0.0.1:8080/";
    private WebView webView;
    private View errorView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showDashboard();
    }

    private void showDashboard() {
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setBackgroundColor(Color.rgb(8, 9, 12));
        webView.setWebViewClient(new DashboardClient());
        setContentView(webView);
        webView.loadUrl(DASHBOARD_URL);
    }

    private void showUnavailable() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 32, 48, 32);
        root.setBackgroundColor(Color.rgb(8, 9, 12));

        TextView title = new TextView(this);
        title.setText("DRKMUQEET dashboard unavailable");
        title.setTextColor(Color.rgb(198, 255, 69));
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);

        TextView details = new TextView(this);
        details.setText("Install and enable the DRKMUQEET Magisk module, then start its local dashboard on 127.0.0.1:8080.");
        details.setTextColor(Color.rgb(220, 224, 230));
        details.setTextSize(15);
        details.setGravity(Gravity.CENTER);
        details.setPadding(0, 20, 0, 28);

        Button retry = new Button(this);
        retry.setText("Retry");
        retry.setOnClickListener(view -> showDashboard());

        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        root.addView(details, new LinearLayout.LayoutParams(-1, -2));
        root.addView(retry, new LinearLayout.LayoutParams(-2, -2));
        errorView = root;
        setContentView(errorView);
    }

    private static boolean isDashboardUri(Uri uri) {
        return uri != null
                && "http".equalsIgnoreCase(uri.getScheme())
                && ("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                && (uri.getPort() == 8080 || uri.getPort() == -1);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private final class DashboardClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isDashboardUri(uri)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                // There is no handler installed for this external link.
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                showUnavailable();
            }
        }
    }
}
