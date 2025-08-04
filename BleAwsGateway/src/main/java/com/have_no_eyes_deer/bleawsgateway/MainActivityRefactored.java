package com.have_no_eyes_deer.bleawsgateway;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.have_no_eyes_deer.bleawsgateway.ble.BleDataListener;
import com.have_no_eyes_deer.bleawsgateway.ble.BleManager;
import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;
import com.have_no_eyes_deer.bleawsgateway.mqtt.MqttDataSender;

import java.util.ArrayList;
import java.util.List;


public class MainActivityRefactored extends AppCompatActivity implements BleDataListener {
    
    private static final int REQUEST_PERMISSIONS = 1001;
    
    // UI components
    private Button btnScan;
    private ListView listViewDevices;
    private ArrayAdapter<String> deviceAdapter;
    private TextView tvConnectionStatus, tvReceivedData, tvAwsStatus;
    private ToggleButton toggleReceive;
    
    // core modules
    private BleManager bleManager;
    private MqttDataSender mqttSender; 
    
    // data management
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private String currentConnectedDevice = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        initializeModules();
        setupEventListeners();
        requestPermissions();
    }
    
    private void initializeViews() {
        btnScan = findViewById(R.id.btnScan);
        listViewDevices = findViewById(R.id.listViewDevices);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        tvAwsStatus = findViewById(R.id.tvAwsStatus);
        toggleReceive = findViewById(R.id.toggleReceive);
        
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listViewDevices.setAdapter(deviceAdapter);
    }
    
    private void initializeModules() {
        // initialize BLE manager
        bleManager = new BleManager(this);
        bleManager.addDataListener(this); // MainActivity implements BleDataListener interface
        
        // set scan result listener
        bleManager.setScanResultListener(new BleManager.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                runOnUiThread(() -> {
                    discoveredDevices.add(device);
                    String displayName = device.getName() + " (" + device.getAddress() + ") [" + rssi + "dBm]";
                    deviceAdapter.add(displayName);
                    deviceAdapter.notifyDataSetChanged();
                });
            }
            
            @Override
            public void onScanStarted() {
                runOnUiThread(() -> {
                    btnScan.setText("Stop Scan");
                    discoveredDevices.clear();
                    deviceAdapter.clear();
                    deviceAdapter.notifyDataSetChanged();
                });
            }
            
            @Override
            public void onScanStopped() {
                runOnUiThread(() -> btnScan.setText("Scan BLE Devices"));
            }
        });
        
        
    }
    
    private void setupEventListeners() {
        // scan button
        btnScan.setOnClickListener(v -> {
            if (bleManager.isScanning()) {
                bleManager.stopScan();
            } else {
                bleManager.startScan();
            }
        });
        
        // device list click
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (position < discoveredDevices.size()) {
                BluetoothDevice device = discoveredDevices.get(position);
                connectToDevice(device);
            }
        });
        
        // receive data switch
        toggleReceive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (currentConnectedDevice != null) {
                if (isChecked) {
                    bleManager.enableNotification(currentConnectedDevice);
                    bleManager.sendCommand(currentConnectedDevice, "s"); // send start command
                } else {
                    bleManager.disableNotification(currentConnectedDevice);
                }
            }
        });
    }
    
    private void connectToDevice(BluetoothDevice device) {
        tvConnectionStatus.setText("connecting: " + device.getName());
        toggleReceive.setEnabled(false);
        bleManager.connectToDevice(device);
    }
    
    // ======================== BleDataListener implementation ========================
    
    @Override
    public void onDataReceived(BleDataModel data) {
        runOnUiThread(() -> {
            // 1. display on UI
            String displayText = String.format("[%s] %s: %s\n", 
                data.getTimestamp(), data.getDeviceAddress(), data.getDataString());
            tvReceivedData.append(displayText);
            
            // 2. forward to MQTT (if MQTT module is available)
            if (mqttSender != null && mqttSender.isConnected()) {
                String topic = generateTopicForDevice(data);
                boolean sent = mqttSender.sendData(data, topic);
                if (sent) {
                    tvReceivedData.append("→ forwarded to AWS IoT\n");
                } else {
                    tvReceivedData.append("→ MQTT send failed\n");
                }
            }
        });
    }
    
    @Override
    public void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
        runOnUiThread(() -> {
            if (isConnected) {
                currentConnectedDevice = deviceAddress;
                tvConnectionStatus.setText("connected: " + deviceName);
                toggleReceive.setEnabled(true);
                
                // automatically enable notification
                bleManager.enableNotification(deviceAddress);
            } else {
                currentConnectedDevice = null;
                    tvConnectionStatus.setText("disconnected");
                toggleReceive.setEnabled(false);
                toggleReceive.setChecked(false);
            }
        });
    }
    
    @Override
    public void onError(String error, String deviceAddress) {
        runOnUiThread(() -> {
            Toast.makeText(this, "BLE error: " + error, Toast.LENGTH_SHORT).show();
            tvReceivedData.append("error: " + error + "\n");
        });
    }
    
    @Override
    public void onDataSent(String deviceAddress, byte[] data) {
        runOnUiThread(() -> {
            String message = "send success: " + new String(data) + "\n";
            tvReceivedData.append(message);
        });
    }
    
    // ======================== utility methods ========================
    
    private String generateTopicForDevice(BleDataModel data) {
        // generate different MQTT topics based on data type
        String deviceId = data.getDeviceAddress().replace(":", "");
        switch (data.getDataType()) {
            case SENSOR_DATA:
                return "sensors/" + deviceId + "/data";
            case STATUS_UPDATE:
                return "devices/" + deviceId + "/status";
            case COMMAND_RESPONSE:
                return "devices/" + deviceId + "/response";
            default:
                return "devices/" + deviceId + "/raw";
        }
    }
    
    // ======================== permission management ========================
    
    private void requestPermissions() {
        List<String> permissions = new ArrayList<>();
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Bluetooth permission is required to work", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    // ======================== lifecycle management ========================
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.cleanup();
        }
        if (mqttSender != null) {
            mqttSender.disconnect();
        }
    }
    
    // ======================== MQTT module integration example ========================
    
    /**
     * @param mqttImplementation MQTT implementation class instance
     */
    public void initializeMqttModule(MqttDataSender mqttImplementation) {
        this.mqttSender = mqttImplementation;
        
        // set MQTT status listener
        mqttSender.setStatusListener(new MqttDataSender.MqttStatusListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> tvAwsStatus.setText("AWS: connected"));
            }
            
            @Override
            public void onDisconnected() {
                runOnUiThread(() -> tvAwsStatus.setText("AWS: disconnected"));
            }
            
            @Override
            public void onMessageSent(String topic, String message) {
                runOnUiThread(() -> 
                    tvReceivedData.append("→ MQTT send: " + topic + "\n"));
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> 
                    tvReceivedData.append("MQTT error: " + error + "\n"));
            }
        });
        
        // try to connect
        mqttSender.connect();
    }
} 