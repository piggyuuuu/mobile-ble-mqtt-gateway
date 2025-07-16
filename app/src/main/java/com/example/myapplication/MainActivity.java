package com.example.myapplication;

import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 获取 WebView 引用
        webView = findViewById(R.id.webview);

        // 2. 启用 JavaScript 并允许访问 assets 目录下的文件
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);

        // 3. 拦截所有跳转，在当前 WebView 内加载
        webView.setWebViewClient(new WebViewClient() {
            // Android < 24
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            // Android >= 24
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        // 4. 加载初始页面（assets 下的 scan.html）
        webView.loadUrl("file:///android_asset/scan.html");
    }

    // 按“返回”键时，如果 WebView 可以后退，则后退；否则执行默认 back
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
