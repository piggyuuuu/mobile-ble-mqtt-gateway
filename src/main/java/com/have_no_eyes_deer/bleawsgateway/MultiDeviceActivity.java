package com.have_no_eyes_deer.bleawsgateway;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
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
import java.util.List;

/**
 * Multi-Device Connection Management Activity
 */
@SuppressLint("MissingPermission")
public class MultiDeviceActivity extends AppCompatActivity implements BleDataListener {
    private static final String TAG = "MultiDeviceActivity";
    private static final int REQUEST_PERMISSIONS = 1001;
    
    private BleManager bleManager;
    private Handler mainHandler;
    
    // UI components
    private Button btnScan, btnConnectAll, btnDisconnectAll, btnBack;
    private ListView listViewDevices, listViewConnected;
    private TextView tvScanStatus, tvConnectionStatus, tvReceivedData;
    private TextView tvConnectionPool;
    
    // Data adapters
    private ArrayAdapter<String> discoveredAdapter;
    private ArrayAdapter<String> connectedAdapter;
    
    // Data lists
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private List<String> connectedDevices = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_device);
        
        initializeViews();
        initializeBleManager();
        setupListeners();
        checkPermissions();
    }
    
    private void initializeViews() {
        btnScan = findViewById(R.id.btnScan);
        btnConnectAll = findViewById(R.id.btnConnectAll);
        btnDisconnectAll = findViewById(R.id.btnDisconnectAll);
        btnBack = findViewById(R.id.btnBack);
        
        listViewDevices = findViewById(R.id.listViewDevices);
        listViewConnected = findViewById(R.id.listViewConnected);
        
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        tvConnectionPool = findViewById(R.id.tvConnectionPool);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize adapters
        discoveredAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        connectedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        
        listViewDevices.setAdapter(discoveredAdapter);
        listViewConnected.setAdapter(connectedAdapter);
    }
    
    private void initializeBleManager() {
        bleManager = new BleManager(this);
        bleManager.addDataListener(this);
        bleManager.setScanResultListener(new BleManager.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                mainHandler.post(() -> {
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        String deviceInfo = device.getName() != null ? 
                            device.getName() + " (" + device.getAddress() + ")" :
                            "Unknown Device (" + device.getAddress() + ")";
                        discoveredAdapter.add(deviceInfo);
                    }
                });
            }
            
            @Override
            public void onScanStarted() {
                mainHandler.post(() -> {
                    tvScanStatus.setText(getString(R.string.scanning));
                    btnScan.setText(getString(R.string.stop_scan));
                });
            }
            
            @Override
            public void onScanStopped() {
                mainHandler.post(() -> {
                    tvScanStatus.setText(getString(R.string.scan_stopped));
                    btnScan.setText(getString(R.string.start_scan));
                });
            }
        });
    }
    
    private void setupListeners() {
        btnScan.setOnClickListener(v -> {
            if (bleManager.isScanning()) {
                bleManager.stopScan();
            } else {
                startScan();
            }
        });
        
        btnConnectAll.setOnClickListener(v -> connectAllDevices());
        
        btnDisconnectAll.setOnClickListener(v -> disconnectAllDevices());
        
        btnBack.setOnClickListener(v -> finish());
        
        // Device list click events
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = discoveredDevices.get(position);
            connectDevice(device);
        });
        
        // Connected device list click events
        listViewConnected.setOnItemClickListener((parent, view, position, id) -> {
            String deviceAddress = connectedDevices.get(position);
            disconnectDevice(deviceAddress);
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
        discoveredAdapter.clear();
        bleManager.startScan();
    }
    
    private void connectDevice(BluetoothDevice device) {
        if (bleManager.connectToDevice(device)) {
            Toast.makeText(this, getString(R.string.connecting_device, device.getName()), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, getString(R.string.connection_failed), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void connectAllDevices() {
        int connectedCount = 0;
        for (BluetoothDevice device : discoveredDevices) {
            if (!bleManager.isDeviceConnected(device.getAddress())) {
                if (bleManager.connectToDevice(device)) {
                    connectedCount++;
                }
            }
        }
        Toast.makeText(this, getString(R.string.trying_connect_devices, connectedCount), Toast.LENGTH_SHORT).show();
    }
    
    private void disconnectDevice(String deviceAddress) {
        bleManager.disconnectDevice(deviceAddress);
        Toast.makeText(this, getString(R.string.disconnecting_devices), Toast.LENGTH_SHORT).show();
    }
    
    private void disconnectAllDevices() {
        bleManager.disconnectAllDevices();
        Toast.makeText(this, getString(R.string.disconnect_all_devices), Toast.LENGTH_SHORT).show();
    }
    
    private void updateConnectionStatus() {
        List<String> connectedAddresses = bleManager.getConnectedDeviceAddresses();
        connectedDevices.clear();
        connectedAdapter.clear();
        
        for (String address : connectedAddresses) {
            DeviceConnectionManager.DeviceConnectionInfo info = bleManager.getDeviceInfo(address);
            String deviceInfo = info != null ? 
                info.deviceName + " (" + address + ")" :
                "Unknown Device (" + address + ")";
            connectedDevices.add(address);
            connectedAdapter.add(deviceInfo);
        }
        
        tvConnectionStatus.setText(getString(R.string.connected_device_count, connectedAddresses.size()));
        tvConnectionPool.setText(getString(R.string.connection_pool, 
            bleManager.getActiveConnectionCount(), bleManager.getMaxConnectionCount()));
    }
    
    // BleDataListener implementation
    @Override
    public void onDataReceived(BleDataModel data) {
        mainHandler.post(() -> {
            String dataInfo = String.format("[%s] %s: %s", 
                data.getDeviceName(), data.getDeviceAddress(), data.getDataString());
            tvReceivedData.append(dataInfo + "\n");
        });
    }
    
    @Override
    public void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
        mainHandler.post(() -> {
            String status = isConnected ? "Connected" : "Disconnected";
            String message = String.format("Device %s (%s) %s", deviceName, deviceAddress, status);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            
            updateConnectionStatus();
        });
    }
    
    @Override
    public void onError(String error, String deviceAddress) {
        mainHandler.post(() -> {
            String message = deviceAddress != null ? 
                String.format("Error [%s]: %s", deviceAddress, error) : error;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            Log.e(TAG, message);
        });
    }
    
    @Override
    public void onDataSent(String deviceAddress, byte[] data) {
        mainHandler.post(() -> {
            String message = String.format("Data sent to device %s", deviceAddress);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth permissions required to use this feature", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStatus();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.cleanup();
        }
    }
} 