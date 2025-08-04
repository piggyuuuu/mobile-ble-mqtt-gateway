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
            this.connectionStatus = "未连接";
        }

        @Override
        public String toString() {
            String name = device.getName() != null ? device.getName() : "Unknown Device";
            String status = isConnected ? "已连接" : connectionStatus;
            return String.format("%s\n地址: %s\nRSSI: %d dBm\n状态: %s", 
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
                    tvScanStatus.setText("扫描中...");
                    btnScan.setText("停止扫描");
                });
            }

            @Override
            public void onScanStopped() {
                mainHandler.post(() -> {
                    isScanning = false;
                    tvScanStatus.setText("扫描已停止");
                    btnScan.setText("开始扫描");
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
            existingItem.connectionStatus = existingItem.isConnected ? "已连接" : "未连接";
        } else {
            // 添加新设备
            DeviceItem newItem = new DeviceItem(device, rssi);
            newItem.isConnected = bleManager.isDeviceConnected(address);
            newItem.connectionStatus = newItem.isConnected ? "已连接" : "未连接";
            
            deviceMap.put(address, newItem);
            discoveredDevices.add(newItem);
            deviceAdapter.add(newItem);
        }

        updateDeviceCount();
        updateConnectionPoolStatus();
    }

    private void connectDevice(DeviceItem item) {
        if (bleManager.connectToDevice(item.device)) {
            Toast.makeText(this, "正在连接设备: " + item.getDeviceName(), Toast.LENGTH_SHORT).show();
            item.connectionStatus = "连接中...";
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "连接失败，可能已达到最大连接数", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectDevice(DeviceItem item) {
        bleManager.disconnectDevice(item.getDeviceAddress());
        Toast.makeText(this, "断开设备连接: " + item.getDeviceName(), Toast.LENGTH_SHORT).show();
        item.isConnected = false;
        item.connectionStatus = "未连接";
        deviceAdapter.notifyDataSetChanged();
    }

    private void connectSelectedDevices() {
        int connectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (!item.isConnected && !item.connectionStatus.equals("连接中...")) {
                if (bleManager.connectToDevice(item.device)) {
                    connectedCount++;
                    item.connectionStatus = "连接中...";
                }
            }
        }
        
        if (connectedCount > 0) {
            Toast.makeText(this, "尝试连接 " + connectedCount + " 个设备", Toast.LENGTH_SHORT).show();
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "没有可连接的设备", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "断开 " + disconnectedCount + " 个设备连接", Toast.LENGTH_SHORT).show();
            deviceAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "没有已连接的设备", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeviceDetails(DeviceItem item) {
        String details = String.format(
            "设备详情:\n" +
            "名称: %s\n" +
            "地址: %s\n" +
            "RSSI: %d dBm\n" +
            "发现时间: %s\n" +
            "连接状态: %s\n" +
            "设备类型: %s",
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
                    return "经典蓝牙";
                case BluetoothDevice.DEVICE_TYPE_LE:
                    return "低功耗蓝牙";
                case BluetoothDevice.DEVICE_TYPE_DUAL:
                    return "双模蓝牙";
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN:
                default:
                    return "未知类型";
            }
        } catch (Exception e) {
            return "未知类型";
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
        
        tvDeviceCount.setText(String.format("设备总数: %d (已连接: %d)", totalCount, connectedCount));
    }

    private void updateConnectionPoolStatus() {
        int activeConnections = bleManager.getActiveConnectionCount();
        int maxConnections = bleManager.getMaxConnectionCount();
        tvConnectionPool.setText(String.format("连接池: %d/%d", activeConnections, maxConnections));
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
            item.connectionStatus = item.isConnected ? "已连接" : "未连接";
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