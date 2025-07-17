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

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);           // 启用 JS
        settings.setAllowFileAccess(true);             // 允许访问本地文件
        settings.setAllowContentAccess(true);          // 允许访问 content://
        settings.setDomStorageEnabled(true);           // 启用 localStorage/sessionStorage（用于 AWS 设置）
        settings.setMediaPlaybackRequiresUserGesture(false); // 如有音视频，可允许自动播放

        webView.setWebViewClient(new WebViewClient() {
            // Android < 7.0
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            // Android >= 7.0
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        // 加载 HTML 页面
        webView.loadUrl("file:///android_asset/web/realtime.html");
        // 如果你希望从 scan 页面开始： webView.loadUrl("file:///android_asset/web/scan.html");
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
