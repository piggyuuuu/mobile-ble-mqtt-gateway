import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private WebView webView;
    private WebAppInterface jsBridge;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        jsBridge = new WebAppInterface(this, webView);
        webView.addJavascriptInterface(jsBridge, "Android");

        webView.loadUrl("file:///android_asset/web/realtime.html");

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_CODE_PERMISSIONS);
        } else {
            startBLEScan();
        }
    }

    public void startBLEScan() {
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            jsBridge.updateStatus("BLE", "❌ Scanner unavailable");
            return;
        }

        jsBridge.updateStatus("BLE", "✅ Scanning...");
        bleScanner.startScan(new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                String name = result.getDevice().getName();
                if (name == null) name = "Unknown";
                jsBridge.updateStatus("log", "Found device: " + name);
            }

            @Override
            public void onScanFailed(int errorCode) {
                jsBridge.updateStatus("BLE", "❌ Scan failed: " + errorCode);
            }
        });

        // 停止扫描（例如10秒后）
        webView.postDelayed(() -> {
            bleScanner.stopScan(new ScanCallback() {});
            jsBridge.updateStatus("BLE", "✅ Scan complete");
        }, 10000);
    }

    // 权限回调
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBLEScan();
            } else {
                jsBridge.updateStatus("BLE", "❌ Permission denied");
            }
        }
    }
}
