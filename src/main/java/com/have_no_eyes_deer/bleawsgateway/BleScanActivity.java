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
import com.have_no_eyes_deer.bleawsgateway.ble.BleDataListener;
import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;

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
        private String cachedDeviceName;

        public DeviceItem(BluetoothDevice device, int rssi) {
            this.device = device;
            this.rssi = rssi;
            this.discoveryTime = System.currentTimeMillis();
            this.isConnected = false;
            this.connectionStatus = "Not Connected";
            this.cachedDeviceName = null;
        }

        @Override
        public String toString() {
            String name = getDeviceName();
            String status = isConnected ? "Connected" : connectionStatus;
            return String.format("%s\nAddress: %s\nRSSI: %d dBm\nStatus: %s", 
                name, device.getAddress(), rssi, status);
        }

        public String getDeviceAddress() {
            return device.getAddress();
        }

        public String getDeviceName() {
            if (cachedDeviceName != null) {
                return cachedDeviceName;
            }
            
            String name = device.getName();
            if (name != null && !name.trim().isEmpty()) {
                cachedDeviceName = name;
                return name;
            } else {
                // 如果设备名字为空，尝试从连接信息中获取
                cachedDeviceName = "Unknown Device";
                return cachedDeviceName;
            }
        }
        
        public void updateDeviceName(String name) {
            if (name != null && !name.trim().isEmpty()) {
                this.cachedDeviceName = name;
            }
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
        deviceAdapter = new ArrayAdapter<>(this, R.layout.device_list_item);
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
                    tvScanStatus.setText(getString(R.string.scanning));
                    btnScan.setText(getString(R.string.stop_scan));
                });
            }

            @Override
            public void onScanStopped() {
                mainHandler.post(() -> {
                    isScanning = false;
                    tvScanStatus.setText(getString(R.string.scan_stopped));
                    btnScan.setText(getString(R.string.start_scan));
                });
            }
        });
        
        // 添加数据监听器来监听连接状态变化
        bleManager.addDataListener(new BleDataListener() {
            @Override
            public void onDataReceived(BleDataModel data) {
                // 数据接收时的处理
            }

            @Override
            public void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
                mainHandler.post(() -> {
                    // 更新设备连接状态
                    DeviceItem item = deviceMap.get(deviceAddress);
                    if (item != null) {
                        item.isConnected = isConnected;
                        item.connectionStatus = isConnected ? "Connected" : "Not Connected";
                        if (deviceName != null && !deviceName.trim().isEmpty() && !deviceName.equals("Unknown Device")) {
                            item.updateDeviceName(deviceName);
                        }
                        deviceAdapter.notifyDataSetChanged();
                        updateDeviceCount();
                        updateConnectionPoolStatus();
                        
                        // 显示连接状态变化提示
                        String statusText = isConnected ? "Connected" : "Disconnected";
                        Toast.makeText(BleScanActivity.this,
                            "Device " + item.getDeviceName() + " " + statusText,
                            Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error, String deviceAddress) {
                mainHandler.post(() -> {
                    Toast.makeText(BleScanActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDataSent(String deviceAddress, byte[] data) {
                // 数据发送时的处理
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
            existingItem.connectionStatus = existingItem.isConnected ? "已连接" : "未连接";
            
            // 尝试更新设备名字
            String deviceName = device.getName();
            if (deviceName != null && !deviceName.trim().isEmpty()) {
                existingItem.updateDeviceName(deviceName);
            }
        } else {
            // 添加新设备
            DeviceItem newItem = new DeviceItem(device, rssi);
            newItem.isConnected = bleManager.isDeviceConnected(address);
            newItem.connectionStatus = newItem.isConnected ? "已连接" : "未连接";
            
            // 尝试获取设备名字
            String deviceName = device.getName();
            if (deviceName != null && !deviceName.trim().isEmpty()) {
                newItem.updateDeviceName(deviceName);
            }
            
            deviceMap.put(address, newItem);
            discoveredDevices.add(newItem);
            deviceAdapter.add(newItem);
        }

        updateDeviceCount();
        updateConnectionPoolStatus();
        deviceAdapter.notifyDataSetChanged();
    }

    private void connectDevice(DeviceItem item) {
        if (bleManager.connectToDevice(item.device)) {
            Toast.makeText(this, "Connecting: " + item.getDeviceName(), Toast.LENGTH_SHORT).show();
            item.connectionStatus = "Connecting...";
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Connection failed. Maybe max connections reached", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectDevice(DeviceItem item) {
        bleManager.disconnectDevice(item.getDeviceAddress());
        Toast.makeText(this, "Disconnect: " + item.getDeviceName(), Toast.LENGTH_SHORT).show();
        item.isConnected = false;
        item.connectionStatus = "Not Connected";
        deviceAdapter.notifyDataSetChanged();
    }

    private void connectSelectedDevices() {
        int connectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (!item.isConnected && !item.connectionStatus.equals("Connecting...")) {
                if (bleManager.connectToDevice(item.device)) {
                    connectedCount++;
                    item.connectionStatus = "Connecting...";
                }
            }
        }
        
        if (connectedCount > 0) {
            Toast.makeText(this, "Trying to connect " + connectedCount + " devices", Toast.LENGTH_SHORT).show();
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "No connectable devices", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectSelectedDevices() {
        int disconnectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (item.isConnected) {
                bleManager.disconnectDevice(item.getDeviceAddress());
                item.isConnected = false;
                item.connectionStatus = "未连接";
                disconnectedCount++;
            }
        }
        
        if (disconnectedCount > 0) {
            Toast.makeText(this, "Disconnected " + disconnectedCount + " devices", Toast.LENGTH_SHORT).show();
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "No connected devices", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeviceDetails(DeviceItem item) {
        String details = String.format(
            "Device Details:\n" +
            "Name: %s\n" +
            "Address: %s\n" +
            "RSSI: %d dBm\n" +
            "Discovered At: %s\n" +
            "Connection Status: %s\n" +
            "Device Type: %s",
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
                    return "Classic";
                case BluetoothDevice.DEVICE_TYPE_LE:
                    return "Low Energy";
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    return "Dual Mode";
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
        
        tvDeviceCount.setText(getString(R.string.device_count, totalCount, connectedCount));
    }

    private void updateConnectionPoolStatus() {
        int activeConnections = bleManager.getActiveConnectionCount();
        int maxConnections = bleManager.getMaxConnectionCount();
        tvConnectionPool.setText(getString(R.string.connection_pool, activeConnections, maxConnections));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要蓝牙权限才能使用此功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新设备状态
        for (DeviceItem item : discoveredDevices) {
            item.isConnected = bleManager.isDeviceConnected(item.getDeviceAddress());
            item.connectionStatus = item.isConnected ? "Connected" : "Not Connected";
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
            // 不要断开连接，只清理扫描相关的资源
            bleManager.cleanup(false);
        }
    }
} 