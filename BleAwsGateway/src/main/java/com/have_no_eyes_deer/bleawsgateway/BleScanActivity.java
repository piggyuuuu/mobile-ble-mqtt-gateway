package com.have_no_eyes_deer.bleawsgateway;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.have_no_eyes_deer.bleawsgateway.ble.BleManager;
import com.have_no_eyes_deer.bleawsgateway.ble.DeviceConnectionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BLE扫描专用Activity
 * 负责扫描和显示所有发现的设备状态
 */
@SuppressLint("MissingPermission")
public class BleScanActivity extends AppCompatActivity {
    private static final String TAG = "BleScanActivity";
    private static final int REQUEST_PERMISSIONS = 1001;
    private static final long SCAN_PERIOD = 10_000; // 扫描10秒

    private BleManager bleManager;
    private Handler mainHandler;

    // UI组件
    private Button btnScan, btnBack, btnConnectSelected, btnDisconnectSelected;
    private ListView listViewDevices;
    private TextView tvScanStatus, tvDeviceCount, tvConnectionPool;
    private ArrayAdapter<DeviceItem> deviceAdapter;

    // 数据管理
    private List<DeviceItem> discoveredDevices = new ArrayList<>();
    private Map<String, DeviceItem> deviceMap = new HashMap<>();
    private boolean isScanning = false;

    /**
     * 设备信息项
     */
    public static class DeviceItem {
        public BluetoothDevice device;
        public int rssi;
        public long discoveryTime;
        public boolean isConnected;
        public String connectionStatus;

        public DeviceItem(BluetoothDevice device, int rssi) {
            this.device = device;
            this.rssi = rssi;
            this.discoveryTime = System.currentTimeMillis();
            this.isConnected = false;
            this.connectionStatus = "not connected";
        }

        @Override
        public String toString() {
            String name = device.getName() != null ? device.getName() : "Unknown Device";
            String status = isConnected ? "connected" : connectionStatus;
            return String.format("%s\naddress: %s\nRSSI: %d dBm\nstate: %s",
                name, device.getAddress(), rssi, status);
        }

        public String getDeviceAddress() {
            return device.getAddress();
        }

        public String getDeviceName() {
            return device.getName() != null ? device.getName() : "Unknown Device";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_scan);

        initializeViews();
        initializeBleManager();
        setupListeners();
        checkPermissions();
    }

    private void initializeViews() {
        btnScan = findViewById(R.id.btnScan);
        btnBack = findViewById(R.id.btnBack);
        btnConnectSelected = findViewById(R.id.btnConnectSelected);
        btnDisconnectSelected = findViewById(R.id.btnDisconnectSelected);

        listViewDevices = findViewById(R.id.listViewDevices);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        tvConnectionPool = findViewById(R.id.tvConnectionPool);

        mainHandler = new Handler(Looper.getMainLooper());

        // 初始化适配器
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listViewDevices.setAdapter(deviceAdapter);
    }

    private void initializeBleManager() {
        bleManager = new BleManager(this);
        
        // 设置扫描结果监听器
        bleManager.setScanResultListener(new BleManager.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                mainHandler.post(() -> addOrUpdateDevice(device, rssi));
            }

            @Override
            public void onScanStarted() {
                mainHandler.post(() -> {
                    isScanning = true;
                    tvScanStatus.setText("Scanning...");
                    btnScan.setText("Stop Scan");
                });
            }

            @Override
            public void onScanStopped() {
                mainHandler.post(() -> {
                    isScanning = false;
                    tvScanStatus.setText("Scan Stopped");
                    btnScan.setText("Scan beginning");
                });
            }
        });
    }

    private void setupListeners() {
        btnScan.setOnClickListener(v -> {
            if (isScanning) {
                stopScan();
            } else {
                startScan();
            }
        });

        btnBack.setOnClickListener(v -> finish());

        btnConnectSelected.setOnClickListener(v -> connectSelectedDevices());

        btnDisconnectSelected.setOnClickListener(v -> disconnectSelectedDevices());

        // 设备列表点击事件
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            DeviceItem item = discoveredDevices.get(position);
            if (item.isConnected) {
                disconnectDevice(item);
            } else {
                connectDevice(item);
            }
        });

        // 长按显示设备详情
        listViewDevices.setOnItemLongClickListener((parent, view, position, id) -> {
            DeviceItem item = discoveredDevices.get(position);
            showDeviceDetails(item);
            return true;
        });
    }

    private void checkPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSIONS);
        }
    }

    private void startScan() {
        discoveredDevices.clear();
        deviceMap.clear();
        deviceAdapter.clear();
        updateDeviceCount();
        
        bleManager.startScan();
        
        // 10秒后自动停止扫描
        mainHandler.postDelayed(this::stopScan, SCAN_PERIOD);
    }

    private void stopScan() {
        if (isScanning) {
            bleManager.stopScan();
        }
    }

    private void addOrUpdateDevice(BluetoothDevice device, int rssi) {
        String address = device.getAddress();
        DeviceItem existingItem = deviceMap.get(address);

        if (existingItem != null) {
            // 更新现有设备信息
            existingItem.rssi = rssi;
            existingItem.discoveryTime = System.currentTimeMillis();
            existingItem.isConnected = bleManager.isDeviceConnected(address);
            existingItem.connectionStatus = existingItem.isConnected ? "connected" : "not connected";
        } else {
            // 添加新设备
            DeviceItem newItem = new DeviceItem(device, rssi);
            newItem.isConnected = bleManager.isDeviceConnected(address);
            newItem.connectionStatus = newItem.isConnected ? "connected" : "not connected";
            
            deviceMap.put(address, newItem);
            discoveredDevices.add(newItem);
            deviceAdapter.add(newItem);
        }

        updateDeviceCount();
        updateConnectionPoolStatus();
    }

    private void connectDevice(DeviceItem item) {
        if (bleManager.connectToDevice(item.device)) {
            Toast.makeText(this, "Connecting the device: " + item.getDeviceName(), Toast.LENGTH_SHORT).show();
            item.connectionStatus = "connecting...";
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Connection failed. It may have reached the maximum number of connections.", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectDevice(DeviceItem item) {
        bleManager.disconnectDevice(item.getDeviceAddress());
        Toast.makeText(this, "Disconnect the device connection: " + item.getDeviceName(), Toast.LENGTH_SHORT).show();
        item.isConnected = false;
        item.connectionStatus = "not connected";
        deviceAdapter.notifyDataSetChanged();
    }

    private void connectSelectedDevices() {
        int connectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (!item.isConnected && !item.connectionStatus.equals("connecting...")) {
                if (bleManager.connectToDevice(item.device)) {
                    connectedCount++;
                    item.connectionStatus = "connecting...";
                }
            }
        }
        
        if (connectedCount > 0) {
            Toast.makeText(this, "Try to connect " + connectedCount + " devices", Toast.LENGTH_SHORT).show();
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "There are no devices to connect.", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectSelectedDevices() {
        int disconnectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (item.isConnected) {
                bleManager.disconnectDevice(item.getDeviceAddress());
                item.isConnected = false;
                item.connectionStatus = "not connected";
                disconnectedCount++;
            }
        }
        
        if (disconnectedCount > 0) {
            Toast.makeText(this, "disconnect " + disconnectedCount + " devices", Toast.LENGTH_SHORT).show();
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "There are no devices to disconnect.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeviceDetails(DeviceItem item) {
        String details = String.format(
            "device detail:\n" +
            "name: %s\n" +
            "address: %s\n" +
            "RSSI: %d dBm\n" +
            "diacovered time: %s\n" +
            "connect state: %s\n" +
            "device type: %s",
            item.getDeviceName(),
            item.getDeviceAddress(),
            item.rssi,
            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(item.discoveryTime)),
            item.connectionStatus,
            getDeviceTypeString(item.device)
        );
        
        Toast.makeText(this, details, Toast.LENGTH_LONG).show();
    }

    private String getDeviceTypeString(BluetoothDevice device) {
        try {
            int deviceType = device.getType();
            switch (deviceType) {
                case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                    return "Classic Bluetooth";
                case BluetoothDevice.DEVICE_TYPE_LE:
                    return "BLE";
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    return "Dual-mode Bluetooth";
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                default:
                    return "Unknown";
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private void updateDeviceCount() {
        int totalCount = discoveredDevices.size();
        int connectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (item.isConnected) {
                connectedCount++;
            }
        }
        
        tvDeviceCount.setText(String.format("Tatol: %d (connected: %d)", totalCount, connectedCount));
    }

    private void updateConnectionPoolStatus() {
        int activeConnections = bleManager.getActiveConnectionCount();
        int maxConnections = bleManager.getMaxConnectionCount();
        tvConnectionPool.setText(String.format("Devices: %d/%d", activeConnections, maxConnections));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Requires Bluetooth permission", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新设备状态
        for (DeviceItem item : discoveredDevices) {
            item.isConnected = bleManager.isDeviceConnected(item.getDeviceAddress());
            item.connectionStatus = item.isConnected ? "connected" : "not connected";
        }
        deviceAdapter.notifyDataSetChanged();
        updateDeviceCount();
        updateConnectionPoolStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isScanning) {
            stopScan();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.cleanup();
        }
    }
} 