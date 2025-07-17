package com.example.myapplication;

import android.content.Context;
import android.webkit.JavascriptInterface;

public class WebAppInterface {
    Context mContext;
    android.webkit.WebView webView;

    public WebAppInterface(Context c, android.webkit.WebView w) {
        mContext = c;
        webView = w;
    }
    @JavascriptInterface
    public void scanBLE() {
        ((MainActivity) mContext).runOnUiThread(() -> {
            ((MainActivity) mContext).startBLEScan();
        });
    }
    // 让 Java 向网页注入状态或日志
    public void updateStatus(final String type, final String value) {
        webView.post(() -> {
            String script = "updateStatusFromApp('" + type + "', '" + value + "')";
            webView.evaluateJavascript(script, null);
        });
    }
}
