package me.featherx.browser;

import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

/**
 * JavaScript bridge exposed to web pages as window.FeatherX
 * All @JavascriptInterface methods run on a background thread.
 */
public class WebAppInterface {
    private final Context context;
    private final WebView webView;

    public WebAppInterface(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    @JavascriptInterface
    public String getAppVersion() {
        return "1.0.0";
    }

    @JavascriptInterface
    public void showToast(String msg) {
        ((android.app.Activity) context).runOnUiThread(() ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        );
    }

    @JavascriptInterface
    public String getUserAgent() {
        return webView.getSettings().getUserAgentString();
    }
}
