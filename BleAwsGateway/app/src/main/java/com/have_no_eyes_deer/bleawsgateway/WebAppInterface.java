package com.have_no_eyes_deer.bleawsgateway;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class WebAppInterface {
    private Context context;
    private WebView webView;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private Map<String, BluetoothDevice> devices = new HashMap<>();

    public WebAppInterface(Context ctx, WebView wv) {
        context = ctx;
        webView = wv;
        scanner = ((MainActivity)ctx).bleScanner;
    }

    @JavascriptInterface
    public void startScan() {
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                String addr = device.getAddress();
                devices.put(addr, device);
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", device.getName() != null ? device.getName() : "unknown");
                    obj.put("address", addr);
                    final String js = "onDeviceFound(" + obj.toString() + ")";
                    webView.post(() -> webView.evaluateJavascript(js, null));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        scanner.startScan(scanCallback);
    }

    @JavascriptInterface
    public void stopScan() {
        if (scanCallback != null) {
            scanner.stopScan(scanCallback);
        }
    }

    @JavascriptInterface
    public void connectDevice(String address) {
        BluetoothDevice device = devices.get(address);
        if (device == null) return;
        device.connectGatt(context, false, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                String state = newState == BluetoothGatt.STATE_CONNECTED ? "connected" : "disconnected";
                String js = "onConnectionState('" + address + "','" + state + "')";
                webView.post(() -> webView.evaluateJavascript(js, null));
            }
        });
    }
}
