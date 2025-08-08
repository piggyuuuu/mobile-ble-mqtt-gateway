package com.have_no_eyes_deer.bleawsgateway;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.provider.OpenableColumns;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;

// Performance monitoring related imports
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import android.graphics.Color;
import android.content.res.ColorStateList;
import com.have_no_eyes_deer.bleawsgateway.monitor.PerformanceDataManager;
import com.have_no_eyes_deer.bleawsgateway.monitor.ResourceMonitor;
import com.have_no_eyes_deer.bleawsgateway.monitor.MockDataGenerator;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import com.have_no_eyes_deer.bleawsgateway.ble.BleManager;
import com.have_no_eyes_deer.bleawsgateway.ble.DeviceConnectionManager;

@SuppressLint("MissingPermission")
public class MainActivity extends AppCompatActivity {
    // ==== BLE constants ====
    private static final String CCC_UUID_STR = "00002902-0000-1000-8000-00805f9b34fb";
    private final UUID CCC_UUID = UUID.fromString(CCC_UUID_STR);
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic notifyCharacteristic, writeCharacteristic;
    private Handler handler = new Handler();
    private boolean scanning = false;
    private static final long SCAN_PERIOD = 10_000;
    private static final int REQUEST_PERMISSIONS = 1001;
    
    // BLE manager
    private BleManager bleManager;

    // ==== UI elements ====
    private Button btnBleScan, btnAwsSettings, btnConnectAws, btnDiagnoseAws, btnPerformanceTest;
    private Button btnSendR, btnSendY, btnSendB; // Quick send buttons
    private ListView listViewConnectedDevices;
    private ArrayAdapter<String> connectedDevicesAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private TextView tvConnectionStatus, tvReceivedData;
    private ToggleButton toggleReceive;
    
    // Currently selected device
    private String selectedDeviceAddress = null;
    
    // BLE data sending test controls
    private EditText etSendData;
    private Button btnSendData, btnClearLog, btnCopyLog, btnDetailedLog;
    private Spinner spinnerSendType, spinnerReceiveType;
    private boolean detailedLoggingEnabled = false;
    
    // Data type constants
    private static final String[] DATA_TYPES = {"UTF-8 Text", "Hexadecimal", "Byte Array"};
    private static final int TYPE_UTF8 = 0;
    private static final int TYPE_HEX = 1;
    private static final int TYPE_BYTES = 2;

    // ==== AWS IoT constants ====
    private static final String PREFS_NAME        = "AwsPrefs";
    private static final String KEY_ENDPOINT      = "endpoint";
    private static final String KEY_KEY_URI       = "keyUri";
    private static final String KEY_CRED_URI      = "credUri";
    private static final String KEYSTORE_NAME     = "iot_keystore";
    private static final String KEYSTORE_PASSWORD = "iot_passwd";
    private static final String CERTIFICATE_ID    = "iot_cert";
    private TextView tvAwsStatus;

    private AWSIotMqttManager mqttManager;
    
    // ==== Performance monitoring related variables ====
    private PerformanceDataManager performanceManager;
    private MockDataGenerator mockDataGenerator;
    private LineChart lineChart;
    private LineChart lineChartThroughput, lineChartLatency;
    private LinearLayout layoutDualCharts;
    private Button btnResourceChart, btnThroughputChart, btnCostChart;
    private TextView tvCpuUsage, tvMemUsage, tvNetworkUsage;
    private int currentChartType = 0; // 0=Resource, 1=Throughput+Latency, 2=Cost
    private float chartEntryIndex = 0;
    
    // Chart data storage
    private List<Entry> cpuEntries = new ArrayList<>();
    private List<Entry> memoryEntries = new ArrayList<>();
    private List<Entry> networkEntries = new ArrayList<>();
    private List<Entry> bleThrouputEntries = new ArrayList<>();
    private List<Entry> mqttThroughputEntries = new ArrayList<>();
    private List<Entry> latencyEntries = new ArrayList<>();
    private List<Entry> costEntries = new ArrayList<>();

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set full-screen mode and adapt status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
        
        setContentView(R.layout.activity_main);


        // bind views
        btnBleScan         = findViewById(R.id.btnBleScan);
        listViewConnectedDevices = findViewById(R.id.listViewConnectedDevices);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvAwsStatus        = findViewById(R.id.tvAwsStatus);
        toggleReceive      = findViewById(R.id.toggleReceive);
        tvReceivedData     = findViewById(R.id.tvReceivedData);
        btnAwsSettings     = findViewById(R.id.btnAwsSettings);
        btnConnectAws      = findViewById(R.id.btnConnectAws);
        btnDiagnoseAws     = findViewById(R.id.btnDiagnoseAws);
        btnPerformanceTest = findViewById(R.id.btnPerformanceTest);
        
        // Quick send buttons binding
        btnSendR           = findViewById(R.id.btnSendR);
        btnSendY           = findViewById(R.id.btnSendY);
        btnSendB           = findViewById(R.id.btnSendB);
        
        // BLE data sending test controls binding
        etSendData         = findViewById(R.id.etSendData);
        btnSendData        = findViewById(R.id.btnSendData);
        btnClearLog        = findViewById(R.id.btnClearLog);
        btnCopyLog         = findViewById(R.id.btnCopyLog);
        btnDetailedLog     = findViewById(R.id.btnDetailedLog);
        spinnerSendType    = findViewById(R.id.spinnerSendType);
        spinnerReceiveType = findViewById(R.id.spinnerReceiveType);
        
        // Performance monitoring components binding
        lineChart          = findViewById(R.id.lineChart);
        lineChartThroughput = findViewById(R.id.lineChartThroughput);
        lineChartLatency   = findViewById(R.id.lineChartLatency);
        layoutDualCharts   = findViewById(R.id.layoutDualCharts);
        btnResourceChart   = findViewById(R.id.btnResourceChart);
        btnThroughputChart = findViewById(R.id.btnThroughputChart);
        btnCostChart       = findViewById(R.id.btnCostChart);
        tvCpuUsage         = findViewById(R.id.tvCpuUsage);
        tvMemUsage         = findViewById(R.id.tvMemUsage);
        tvNetworkUsage     = findViewById(R.id.tvNetworkUsage);

        // set Spinner adapter
        ArrayAdapter<String> dataTypeAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, DATA_TYPES);
        dataTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSendType.setAdapter(dataTypeAdapter);
        spinnerReceiveType.setAdapter(dataTypeAdapter);
        
        // default selection UTF-8
        spinnerSendType.setSelection(TYPE_UTF8);
        spinnerReceiveType.setSelection(TYPE_UTF8);
        
        // set initial input hint
        updateInputHint(TYPE_UTF8);
        
        // set send type change listener, update input box hint
        spinnerSendType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateInputHint(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        connectedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listViewConnectedDevices.setAdapter(connectedDevicesAdapter);

        // AWS settings page jump
        btnAwsSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AWSSettingsActivity.class))
        );

        // click to trigger AWS IoT connection
        btnConnectAws.setOnClickListener(v -> connectAwsIot());
        
        // click to diagnose connection
        btnDiagnoseAws.setOnClickListener(v -> diagnoseAwsConnection());
        
        // Navigate to BLE scan interface
        btnBleScan.setOnClickListener(v -> {
            Toast.makeText(this, "Navigating to BLE scan interface...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, BleScanActivity.class));
        });
        
        // Navigate to performance test interface
        btnPerformanceTest.setOnClickListener(v -> {
            Toast.makeText(this, "Navigating to performance test interface...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, PerformanceTestActivity.class));
        });

        // BLE initialization
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        
        // Initialize BLE manager
        bleManager = new BleManager(this);

        // Request location & Bluetooth permissions
        checkAndRequestPermissions();

        // Remove old scan button logic, now using dedicated BLE scan interface

        // Remove old device list click event, now using dedicated BLE scan interface

        // receive/send switch
        toggleReceive.setOnCheckedChangeListener((btn, isChecked) -> {
            if (notifyCharacteristic == null || bluetoothGatt == null) return;
            if (isChecked) {
                startReceiving();
                sendStartCommand();
            } else {
                stopReceiving();
            }
        });

        // BLE data sending test button event
        btnSendData.setOnClickListener(v -> {
            String data = etSendData.getText().toString().trim();
            if (!data.isEmpty()) {
                sendTestData(data);
                etSendData.setText(""); // Clear input field
            } else {
                Toast.makeText(this, "Please enter data to send", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearLog.setOnClickListener(v -> tvReceivedData.setText(""));
        
        // Copy log button
        btnCopyLog.setOnClickListener(v -> copyLogToClipboard());
        
        // Detailed log toggle button
        btnDetailedLog.setOnClickListener(v -> toggleDetailedLogging());
        
        // Quick send buttons event
        btnSendR.setOnClickListener(v -> sendQuickCommand("R"));
        btnSendY.setOnClickListener(v -> sendQuickCommand("Y"));
        btnSendB.setOnClickListener(v -> sendQuickCommand("B"));
        
        // Connected device list click event
        listViewConnectedDevices.setOnItemClickListener((parent, view, position, id) -> {
            String[] connectedAddresses = getConnectedDeviceAddresses();
            if (position < connectedAddresses.length) {
                selectedDeviceAddress = connectedAddresses[position];
                showDeviceOptionsDialog(selectedDeviceAddress);
            }
        });
    }

    /** Added: External access to BLE Scanner */
    public BluetoothLeScanner getBleScanner() {
        return bleScanner;
    }

    // ===== BLE methods =====

    private void checkAndRequestPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this, perms.toArray(new String[0]), REQUEST_PERMISSIONS
            );
        }
        
        // Initialize performance monitoring
        initializePerformanceMonitoring();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getName() != null && !deviceList.contains(device)) {
                deviceList.add(device);
                // Remove old UI update logic, now using dedicated BLE scan interface
            }
        }
    };

    private void startBleScan() {
        deviceList.clear();
        scanning = true;
        handler.postDelayed(this::stopBleScan, SCAN_PERIOD);
        bleScanner.startScan(scanCallback);
    }

    private void stopBleScan() {
        bleScanner.stopScan(scanCallback);
        scanning = false;
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(BluetoothDevice device) {
                        runOnUiThread(() -> {
                    tvConnectionStatus.setText("BLE: Connecting...");
                    toggleReceive.setEnabled(false);
                    toggleReceive.setChecked(false);
                    tvReceivedData.setText("");
                });
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override public void onConnectionStateChange(@NonNull BluetoothGatt gatt,
                                                      int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread(() -> {
                    tvConnectionStatus.setText("BLE: Connected");
                    // Enable send test controls
                    etSendData.setEnabled(true);
                    btnSendData.setEnabled(true);
                    spinnerSendType.setEnabled(true);
                    spinnerReceiveType.setEnabled(true);
                });
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    tvConnectionStatus.setText("BLE: Disconnected");
                    toggleReceive.setEnabled(false);
                    toggleReceive.setChecked(false);
                    // Disable send test controls
                    etSendData.setEnabled(false);
                    btnSendData.setEnabled(false);
                    spinnerSendType.setEnabled(false);
                    spinnerReceiveType.setEnabled(false);
                });
                gatt.close();
            }
        }

        @Override public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            for (BluetoothGattService svc : gatt.getServices()) {
                for (BluetoothGattCharacteristic chr : svc.getCharacteristics()) {
                    int props = chr.getProperties();
                    if (notifyCharacteristic == null
                            && (props & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        notifyCharacteristic = chr;
                    }
                    if (writeCharacteristic == null
                            && (props & (BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                        writeCharacteristic = chr;
                    }
                }
                if (notifyCharacteristic != null && writeCharacteristic != null) break;
            }
            runOnUiThread(() ->
                    toggleReceive.setEnabled(notifyCharacteristic != null)
            );
        }

        @Override public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                                      @NonNull BluetoothGattCharacteristic characteristic) {
            byte[] rawData = characteristic.getValue();
            String deviceAddress = gatt.getDevice().getAddress();
            String deviceName = gatt.getDevice().getName();
            
            // Record BLE data received by performance monitor
            if (performanceManager != null) {
                performanceManager.recordBleMessage(deviceAddress, new String(rawData));
            }
            
            runOnUiThread(() -> {
                // 1. Display data according to selected receive type
                int receiveType = spinnerReceiveType.getSelectedItemPosition();
                String displayData;
                String formatName = DATA_TYPES[receiveType];
                
                switch (receiveType) {
                    case TYPE_UTF8:
                        displayData = new String(rawData, StandardCharsets.UTF_8);
                        break;
                    case TYPE_HEX:
                        displayData = byteArrayToHexString(rawData);
                        break;
                    case TYPE_BYTES:
                        displayData = byteArrayToString(rawData);
                        break;
                    default:
                        displayData = new String(rawData, StandardCharsets.UTF_8);
                        formatName = DATA_TYPES[TYPE_UTF8];
                        break;
                }
                
                String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
                appendLog(String.format("[%s] Received (%s): %s (%d bytes)", 
                    timestamp, formatName, displayData, rawData.length));
                
                // 2. Forward to AWS IoT (using UTF-8 format)
                appendDetailedLog("Checking MQTT forwarding conditions...");
                appendDetailedLog("mqttManager status: " + (mqttManager != null ? "Initialized" : "Not initialized"));
                
                if (mqttManager != null) {
                    try {
                        String utf8Data = new String(rawData, StandardCharsets.UTF_8);
                        String cleanDeviceName = deviceName != null ? deviceName : "Unknown";
                        
                        appendDetailedLog("Raw data: '" + utf8Data + "'");
                        appendDetailedLog("Data length: " + rawData.length + " bytes");
                        
                        // Check if temperature data (format: T1:23.5C)
                        String jsonMessage;
                        boolean isTemperatureData = utf8Data.matches("T\\d+:[\\d.]+C");
                        appendDetailedLog("Temperature data match: " + isTemperatureData);
                        
                        if (isTemperatureData) {
                            appendDetailedLog("Processing temperature data...");
                            // Parse temperature data
                            String[] parts = utf8Data.split(":");
                            String sampleNumber = parts[0].substring(1); // Remove 'T'
                            String temperature = parts[1].replace("C", "");
                            
                            appendDetailedLog("Sample number: " + sampleNumber + ", Temperature: " + temperature);
                            
                            jsonMessage = String.format(
                                "{\"device\":\"%s\",\"deviceName\":\"%s\",\"timestamp\":\"%s\",\"type\":\"temperature\",\"sampleNumber\":%s,\"temperature\":%s,\"unit\":\"C\",\"rawData\":\"%s\"}", 
                                escapeJsonString(deviceAddress),
                                escapeJsonString(cleanDeviceName),
                                getIsoTimestamp(),
                                sampleNumber,
                                temperature,
                                escapeJsonString(utf8Data)
                            );
                        } else {
                            appendDetailedLog("Processing general sensor data...");
                            // General sensor data
                            jsonMessage = String.format(
                                "{\"device\":\"%s\",\"deviceName\":\"%s\",\"timestamp\":\"%s\",\"data\":\"%s\",\"dataLength\":%d,\"type\":\"sensor_data\"}", 
                                escapeJsonString(deviceAddress),
                                escapeJsonString(cleanDeviceName),
                                getIsoTimestamp(),
                                escapeJsonString(utf8Data),
                                rawData.length
                            );
                        }
                        
                        // Send to AWS IoT
                        String topic = "devices/" + deviceAddress.replace(":", "") + "/data";
                        appendDetailedLog("Preparing to send to topic: " + topic);
                        appendDetailedLog("JSON content: " + jsonMessage);
                        
                        // Record MQTT send start time
                        if (performanceManager != null) {
                            performanceManager.recordMqttSendStart();
                        }
                        
                        mqttManager.publishString(jsonMessage, topic, AWSIotMqttQos.QOS0);
                        appendLog("→ Sent to AWS IoT: " + topic);
                        
                        // Record MQTT send completion
                        if (performanceManager != null) {
                            performanceManager.recordMqttSendComplete(true);
                        }
                        
                    } catch (Exception e) {
                        appendLog("→ MQTT send failed: " + e.getClass().getSimpleName());
                        appendDetailedLog("   Error message: " + e.getMessage());
                        
                        // Record MQTT send failure
                        if (performanceManager != null) {
                            performanceManager.recordMqttSendComplete(false);
                        }
                        e.printStackTrace();
                    }
                } else {
                    appendLog("→ MQTT manager not initialized, cannot forward data");
                }
            });
        }
    };

    private void startReceiving() {
        bluetoothGatt.setCharacteristicNotification(notifyCharacteristic, true);
        BluetoothGattDescriptor desc = notifyCharacteristic.getDescriptor(CCC_UUID);
        if (desc != null) {
            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(desc);
        }
    }

    private void stopReceiving() {
        bluetoothGatt.setCharacteristicNotification(notifyCharacteristic, false);
        BluetoothGattDescriptor desc = notifyCharacteristic.getDescriptor(CCC_UUID);
        if (desc != null) {
            desc.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(desc);
        }
    }

    private void sendStartCommand() {
        if (writeCharacteristic == null || bluetoothGatt == null) return;
        writeCharacteristic.setValue("s".getBytes(StandardCharsets.UTF_8));
        bluetoothGatt.writeCharacteristic(writeCharacteristic);
    }

    // ===== AWS IoT connection logic =====

    private void connectAwsIot() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String endpoint   = prefs.getString(KEY_ENDPOINT, "");
        String keyUriStr  = prefs.getString(KEY_KEY_URI, null);
        String certUriStr = prefs.getString(KEY_CRED_URI, null);

        if (endpoint.isEmpty() || keyUriStr == null || certUriStr == null) {
            Toast.makeText(this, "Please configure AWS IoT settings first", Toast.LENGTH_SHORT).show();
            return;
        }

        // check network connection
        if (!isNetworkAvailable()) {
            runOnUiThread(() -> {
                appendLog("Error: No network connection");
                appendLog("Please check WiFi or mobile data connection");
                tvAwsStatus.setText("AWS: Network Error");
            });
            return;
        }

        // verify Endpoint format
        if (!isValidEndpoint(endpoint)) {
            runOnUiThread(() -> {
                appendLog("Error: Endpoint format incorrect");
                appendLog("Correct format: xxxxxx-ats.iot.region.amazonaws.com");
            });
            return;
        }

        Uri keyUri  = Uri.parse(keyUriStr);
        Uri certUri = Uri.parse(certUriStr);
        String keystorePath = getFilesDir().getAbsolutePath();

        runOnUiThread(() -> {
            appendLog("Starting AWS IoT connection...");
            appendLog("Endpoint: " + endpoint);
            tvAwsStatus.setText("AWS: Connecting...");
        });

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                // Verify and load certificates
                runOnUiThread(() -> appendLog("Verifying certificate files..."));
                
                String certPem = slurp(getContentResolver().openInputStream(certUri));
                String keyPem  = slurp(getContentResolver().openInputStream(keyUri));
                
                // Verify certificate format
                if (!isValidCertificate(certPem)) {
                    runOnUiThread(() -> appendLog("Error: Certificate file format invalid"));
                    return;
                }
                
                if (!isValidPrivateKey(keyPem)) {
                    runOnUiThread(() -> appendLog("Error: Private key file format invalid"));
                    return;
                }

                runOnUiThread(() -> appendLog("Certificate files verified successfully"));

                // Create or load keystore
                if (!AWSIotKeystoreHelper.isKeystorePresent(keystorePath, KEYSTORE_NAME)) {
                    runOnUiThread(() -> appendLog("Creating new keystore..."));
                    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                            CERTIFICATE_ID, certPem, keyPem,
                            keystorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD
                    );
                } else {
                    runOnUiThread(() -> appendLog("Using existing keystore"));
                }

                KeyStore ks = AWSIotKeystoreHelper.getIotKeystore(
                        CERTIFICATE_ID, keystorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD
                );

                runOnUiThread(() -> appendLog("Keystore loaded successfully"));

                // Create MQTT manager
                String clientId = "BleGateway_" + System.currentTimeMillis();
                runOnUiThread(() -> appendLog("Client ID: " + clientId));
                
                mqttManager = new AWSIotMqttManager(clientId, endpoint);
                
                // Set connection parameters
                mqttManager.setKeepAlive(30);  // Increase keep-alive time
                
                runOnUiThread(() -> appendLog("Starting MQTT connection..."));

                mqttManager.connect(ks, new AWSIotMqttClientStatusCallback() {
                    @Override
                    public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                        runOnUiThread(() -> {
                            String statusMsg = "AWS IoT Status: " + status.name();
                            tvAwsStatus.setText("AWS: " + status.name());
                            appendLog("Connection status changed: " + status.name());

                            switch (status) {
                                case Connecting:
                                    appendLog("Establishing connection...");
                                    break;
                                    
                                case Connected:
                                    appendLog("AWS IoT connected successfully!");
                                    Toast.makeText(MainActivity.this, "AWS IoT connected successfully", Toast.LENGTH_SHORT).show();
                                    
                                    // Send test message
                                    try {
                                        String topic = "test/bleawsgateway";
                                        String jsonMessage = String.format(
                                            "{\"message\":\"Gateway connected\",\"timestamp\":\"%s\",\"status\":\"online\",\"type\":\"connection\"}",
                                            getIsoTimestamp()
                                        );
                                        mqttManager.publishString(jsonMessage, topic, AWSIotMqttQos.QOS0);
                                        appendLog("Test message sent to: " + topic);
                                    } catch (Exception e) {
                                        appendLog("Test message failed: " + e.getMessage());
                                    }
                                    break;
                                    
                                case Reconnecting:
                                    appendLog("Connection lost, reconnecting...");
                                    break;
                                    
                                case ConnectionLost:
                                    appendLog("Connection lost");
                                    if (throwable != null) {
                                        appendLog("Reason: " + throwable.getMessage());
                                        throwable.printStackTrace();
                                    }
                                    break;
                                    
                                default:
                                    if (throwable != null) {
                                        appendLog("Connection error: " + throwable.getClass().getSimpleName());
                                        appendLog("Error details: " + throwable.getMessage());
                                        
                                        // Common error solutions
                                        String errorMsg = throwable.getMessage();
                                        if (errorMsg != null) {
                                            if (errorMsg.contains("certificate")) {
                                                appendLog("Suggestion: Check certificate files");
                                            } else if (errorMsg.contains("endpoint")) {
                                                appendLog("Suggestion: Check Endpoint address format");
                                            } else if (errorMsg.contains("network") || errorMsg.contains("timeout")) {
                                                appendLog("Suggestion: Check network connection");
                                            } else if (errorMsg.contains("authorization") || errorMsg.contains("forbidden")) {
                                                appendLog("Suggestion: Check device certificate permissions");
                                            }
                                        }
                                        throwable.printStackTrace();
                                    }
                                    break;
                            }
                        });
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("AWS connection exception: " + e.getClass().getSimpleName());
                    appendLog("Error message: " + e.getMessage());
                    
                    tvAwsStatus.setText("AWS: Connection Failed");
                    
                    Toast.makeText(MainActivity.this, "AWS connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                });
            }
        });
    }

    private String slurp(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private void sendTestData(String data) {
        if (writeCharacteristic == null || bluetoothGatt == null) {
            Toast.makeText(this, "Please connect to device first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            byte[] dataBytes;
            int sendType = spinnerSendType.getSelectedItemPosition();
            
            switch (sendType) {
                case TYPE_UTF8:
                    dataBytes = data.getBytes(StandardCharsets.UTF_8);
                    break;
                case TYPE_HEX:
                    dataBytes = hexStringToByteArray(data);
                    if (dataBytes == null) {
                        Toast.makeText(this, "Hex format error! Please enter even number of characters, e.g.: 41424344", Toast.LENGTH_LONG).show();
                        return;
                    }
                    break;
                case TYPE_BYTES:
                    dataBytes = parseByteArray(data);
                    if (dataBytes == null) {
                        Toast.makeText(this, "Byte array format error! Please use comma-separated values 0-255, e.g.: 65,66,67,68", Toast.LENGTH_LONG).show();
                        return;
                    }
                    break;
                default:
                    dataBytes = data.getBytes(StandardCharsets.UTF_8);
                    break;
            }
            
            writeCharacteristic.setValue(dataBytes);
            boolean success = bluetoothGatt.writeCharacteristic(writeCharacteristic);
            
            if (success) {
                String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
                String formatName = DATA_TYPES[sendType];
                tvReceivedData.append(String.format("[%s] send data(%s): %s (%d bytes)\n", 
                    timestamp, formatName, data, dataBytes.length));
            } else {
                tvReceivedData.append("send data failed: BLE write operation failed\n");
            }
        } catch (Exception e) {
            tvReceivedData.append("send data exception: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }
    
    // hex string to byte array
    private byte[] hexStringToByteArray(String hexString) {
        try {
            hexString = hexString.replaceAll("\\s+", ""); // remove spaces
            if (hexString.length() % 2 != 0) {
                return null; // length must be even
            }
            
            byte[] result = new byte[hexString.length() / 2];
            for (int i = 0; i < hexString.length(); i += 2) {
                result[i / 2] = (byte) Integer.parseInt(hexString.substring(i, i + 2), 16);
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
    
    // parse byte array string (e.g.: 65,66,67,68)
    private byte[] parseByteArray(String byteString) {
        try {
            String[] parts = byteString.split(",");
            byte[] result = new byte[parts.length];
            for (int i = 0; i < parts.length; i++) {
                int value = Integer.parseInt(parts[i].trim());
                if (value < 0 || value > 255) {
                    return null; // byte value must be in range 0-255
                }
                result[i] = (byte) value;
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    // byte array to hex string
    private String byteArrayToHexString(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    // byte array to string (e.g.: 65,66,67,68)
    private String byteArrayToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(b).append(",");
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1); // remove last comma
        }
        return sb.toString();
    }

    private void updateInputHint(int position) {
        String hint = "";
        switch (position) {
            case TYPE_UTF8:
                hint = "Enter UTF-8 text";
                break;
            case TYPE_HEX:
                hint = "Enter hex string (e.g.: 41424344)";
                break;
            case TYPE_BYTES:
                hint = "Enter byte array (e.g.: 65,66,67,68)";
                break;
        }
        etSendData.setHint(hint);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // handle permission grant results as needed
    }

    // verify Endpoint format
    private boolean isValidEndpoint(String endpoint) {
        return endpoint.matches("^[a-zA-Z0-9]+-ats\\.iot\\.[a-z0-9-]+\\.amazonaws\\.com$");
    }

    // verify certificate format
    private boolean isValidCertificate(String certPem) {
        return certPem != null && 
               certPem.contains("-----BEGIN CERTIFICATE-----") && 
               certPem.contains("-----END CERTIFICATE-----");
    }

    // verify private key format
    private boolean isValidPrivateKey(String keyPem) {
        return keyPem != null && 
               ((keyPem.contains("-----BEGIN PRIVATE KEY-----") && keyPem.contains("-----END PRIVATE KEY-----")) ||
                (keyPem.contains("-----BEGIN RSA PRIVATE KEY-----") && keyPem.contains("-----END RSA PRIVATE KEY-----")));
    }
    
    // JSON string escape
    private String escapeJsonString(String str) {
        if (str == null) return "null";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    // get ISO 8601 format timestamp
    private String getIsoTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    // network check
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    // Check MQTT connection status
    private boolean isMqttConnected() {
        return mqttManager != null;
        // Note: AWS IoT SDK doesn't have direct connection status check method
        // We can only check if manager is initialized
    }
    
    // Copy log to clipboard
    private void copyLogToClipboard() {
        String logContent = tvReceivedData.getText().toString();
        if (logContent.isEmpty()) {
            Toast.makeText(this, "Log is empty, nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("BLE Gateway Log", logContent);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(this, "Log copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    // Toggle detailed logging
    private void toggleDetailedLogging() {
        detailedLoggingEnabled = !detailedLoggingEnabled;
        btnDetailedLog.setText(detailedLoggingEnabled ? "Simple Log" : "Detailed Log");
        String message = detailedLoggingEnabled ? "Detailed logging enabled" : "Simple logging enabled";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        appendLog("=== " + message + " ===");
    }
    
    // Centralized logging method
    private void appendLog(String message) {
        runOnUiThread(() -> tvReceivedData.append(message + "\n"));
    }
    
    // Detailed logging method
    private void appendDetailedLog(String message) {
        if (detailedLoggingEnabled) {
            appendLog(message);
        }
    }
    
    // AWS connection diagnosis
    public void diagnoseAwsConnection() {
        runOnUiThread(() -> {
            appendLog("=== AWS Connection Diagnosis Started ===");
            appendLog("Network connection status: " + (isNetworkAvailable() ? "Connected" : "Disconnected"));
            appendLog("MQTT manager status: " + (mqttManager != null ? "Initialized" : "Not initialized"));
            appendLog("Endpoint: " + getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_ENDPOINT, "N/A"));
            appendLog("Certificate file: " + getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_CRED_URI, "N/A"));
            appendLog("Private key file: " + getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_KEY_URI, "N/A"));
            appendLog("Keystore path: " + getFilesDir().getAbsolutePath());
            appendLog("Certificate ID: " + CERTIFICATE_ID);
            appendLog("Keystore name: " + KEYSTORE_NAME);
            
            // Test sending capability
            if (mqttManager != null) {
                try {
                    String testTopic = "test/diagnosis";
                    String testMessage = "{\"test\":\"connectivity\",\"timestamp\":\"" + getIsoTimestamp() + "\"}";
                    mqttManager.publishString(testMessage, testTopic, AWSIotMqttQos.QOS0);
                    appendLog("Diagnostic message test: Sent to " + testTopic);
                } catch (Exception e) {
                    appendLog("Diagnostic message failed: " + e.getMessage());
                }
                
                appendLog("");
                appendLog("【AWS IoT Console Monitoring Guide】");
                appendLog("1. Login to AWS IoT Console");
                appendLog("2. Click 'Test' -> 'MQTT test client' in left menu");
                appendLog("3. Subscribe to these topics:");
                appendLog("   - test/# (monitor all test messages)");
                appendLog("   - devices/# (monitor all device data)");
                appendLog("   - devices/+/data (monitor device data only)");
                appendLog("4. Click 'Subscribe' button to start monitoring");
                appendLog("5. Connect BLE device and send 's' command for temperature data");
            }
            appendLog("=== AWS Connection Diagnosis Completed ===");
        });
    }
    
    // ==================== Performance monitoring methods ====================
    
    private void initializePerformanceMonitoring() {
        // Initialize performance data manager
        performanceManager = new PerformanceDataManager(this);
        
        // Initialize mock data generator
        mockDataGenerator = new MockDataGenerator(performanceManager);
        
        performanceManager.addListener(new PerformanceDataManager.PerformanceDataListener() {
            @Override
            public void onResourceDataUpdate(PerformanceDataManager.ResourceData data) {
                runOnUiThread(() -> {
                    updateResourceUI(data);
                    updateResourceChart(data);
                    incrementChartIndex();
                });
            }
            
            @Override
            public void onThroughputDataUpdate(PerformanceDataManager.ThroughputData data) {
                runOnUiThread(() -> updateThroughputChart(data));
            }
            
            @Override
            public void onLatencyDataUpdate(PerformanceDataManager.LatencyData data) {
                runOnUiThread(() -> updateLatencyChart(data));
            }
            
            @Override
            public void onCostDataUpdate(PerformanceDataManager.CostData data) {
                runOnUiThread(() -> updateCostChart(data));
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> appendLog("Monitor Error: " + error));
            }
        });
        
        // Set chart switch button event
        setupChartSwitchButtons();
        
        // Initialize charts
        setupChart();
        
        // Add initial 0 value data points to ensure charts start from 0
        initializeChartWithZeroData();
        
        // Start monitoring
        performanceManager.startMonitoring();
        
        // If using mock data, start mock data generation
        if (MockDataGenerator.isUsingMockData()) {
            mockDataGenerator.startMockDataGeneration();
            appendLog("Mock data generator started - " + mockDataGenerator.getMockDataStats());
        } else {
            appendLog("Using real BLE/MQTT data");
        }
    }
    
    private void setupChartSwitchButtons() {
        btnResourceChart.setOnClickListener(v -> switchChart(0));
        btnThroughputChart.setOnClickListener(v -> switchChart(1));
        btnCostChart.setOnClickListener(v -> switchChart(2));
    }
    
    private void switchChart(int chartType) {
        currentChartType = chartType;
        
        // Update button styles
        resetChartButtonStyles();
        switch (chartType) {
            case 0: // Resource chart
                btnResourceChart.setBackgroundColor(Color.parseColor("#2196F3"));
                btnResourceChart.setTextColor(Color.WHITE);
                btnResourceChart.invalidate();
                // Show single chart, hide dual charts
                lineChart.setVisibility(View.VISIBLE);
                layoutDualCharts.setVisibility(View.GONE);
                setupChart();
                updateCurrentChart();
                Log.d("MainActivity", "Switched to Resource chart");
                break;
            case 1: // Throughput+Latency dual charts
                btnThroughputChart.setBackgroundColor(Color.parseColor("#2196F3"));
                btnThroughputChart.setTextColor(Color.WHITE);
                btnThroughputChart.invalidate();
                // Hide single chart, show dual charts
                lineChart.setVisibility(View.GONE);
                layoutDualCharts.setVisibility(View.VISIBLE);
                setupDualCharts();
                updateDualCharts();
                Log.d("MainActivity", "Switched to Throughput+Latency chart");
                break;
            case 2: // Cost chart
                btnCostChart.setBackgroundColor(Color.parseColor("#2196F3"));
                btnCostChart.setTextColor(Color.WHITE);
                btnCostChart.invalidate();
                // Show single chart, hide dual charts
                lineChart.setVisibility(View.VISIBLE);
                layoutDualCharts.setVisibility(View.GONE);
                setupChart();
                updateCurrentChart();
                Log.d("MainActivity", "Switched to Cost chart");
                break;
        }
    }
    
    private void resetChartButtonStyles() {
        btnResourceChart.setBackgroundColor(Color.parseColor("#CCCCCC"));
        btnResourceChart.setTextColor(Color.parseColor("#666666"));
        btnThroughputChart.setBackgroundColor(Color.parseColor("#CCCCCC"));
        btnThroughputChart.setTextColor(Color.parseColor("#666666"));
        btnCostChart.setBackgroundColor(Color.parseColor("#CCCCCC"));
        btnCostChart.setTextColor(Color.parseColor("#666666"));
        
        // Force button display refresh
        btnResourceChart.invalidate();
        btnThroughputChart.invalidate();
        btnCostChart.invalidate();
    }
    
    private void setupChart() {
        // Basic configuration
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true);
        lineChart.setDrawGridBackground(false);
        lineChart.setBackgroundColor(Color.WHITE);
        
        // Hide description text
        Description description = new Description();
        description.setEnabled(false);
        lineChart.setDescription(description);
        
        // X-axis configuration
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.LTGRAY);
        xAxis.setTextColor(Color.DKGRAY);
        xAxis.setGranularity(5f); // Display one label every 5 data points
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                // Calculate seconds since current time (points further to the left have smaller values, earlier time)
                int secondsAgo = Math.max(0, (int) (chartEntryIndex - value));
                int minutes = secondsAgo / 60;
                int seconds = secondsAgo % 60;
                return String.format("%02d:%02d", minutes, seconds);
            }
        });
        
        // Y-axis configuration
        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setTextColor(Color.DKGRAY);
        leftAxis.setAxisMinimum(0f);
        
        YAxis rightAxis = lineChart.getAxisRight();
        rightAxis.setEnabled(false); // Hide right Y-axis
        
        // Legend configuration
        Legend legend = lineChart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(12f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }
    
    private void updateResourceUI(PerformanceDataManager.ResourceData data) {
        tvCpuUsage.setText(String.format(Locale.getDefault(), "CPU: %.1f%%", data.cpuUsagePercent));
        tvMemUsage.setText(String.format(Locale.getDefault(), "Memory: %dMB", data.memoryUsageMB));
        tvNetworkUsage.setText(String.format(Locale.getDefault(), "Network: %.1fKB/s", data.networkSpeedKBps));
        
        if (currentChartType == 0) {
            updateResourceChart(data);
        }
    }
    
    private void updateResourceChart(PerformanceDataManager.ResourceData data) {
        // Add new data points
        cpuEntries.add(new Entry(chartEntryIndex, data.cpuUsagePercent));
        memoryEntries.add(new Entry(chartEntryIndex, (float) data.memoryUsageMB / data.totalMemoryMB * 100)); // Convert to percentage
        networkEntries.add(new Entry(chartEntryIndex, Math.min(data.networkSpeedKBps, 100))); // Limit network display range
        
        // Limit data points, keep last 60 points
        if (cpuEntries.size() > 60) {
            cpuEntries.remove(0);
            memoryEntries.remove(0);
            networkEntries.remove(0);
        }
        
        // Create data sets
        List<ILineDataSet> dataSets = new ArrayList<>();
        
        // CPU data set
        LineDataSet cpuDataSet = new LineDataSet(new ArrayList<>(cpuEntries), "CPU Usage (%)");
        cpuDataSet.setColor(Color.parseColor("#FF6B35"));
        cpuDataSet.setLineWidth(2f);
        cpuDataSet.setDrawCircles(false);
        cpuDataSet.setValueTextSize(0f);
        cpuDataSet.setDrawFilled(false);
        dataSets.add(cpuDataSet);
        
        // Memory data set
        LineDataSet memoryDataSet = new LineDataSet(new ArrayList<>(memoryEntries), "Memory Usage (%)");
        memoryDataSet.setColor(Color.parseColor("#4CAF50"));
        memoryDataSet.setLineWidth(2f);
        memoryDataSet.setDrawCircles(false);
        memoryDataSet.setValueTextSize(0f);
        memoryDataSet.setDrawFilled(false);
        dataSets.add(memoryDataSet);
        
        // Network data set
        LineDataSet networkDataSet = new LineDataSet(new ArrayList<>(networkEntries), "Network Speed (KB/s)");
        networkDataSet.setColor(Color.parseColor("#2196F3"));
        networkDataSet.setLineWidth(2f);
        networkDataSet.setDrawCircles(false);
        networkDataSet.setValueTextSize(0f);
        networkDataSet.setDrawFilled(false);
        dataSets.add(networkDataSet);
        
        // Update chart
        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);
        
        // Auto-scroll to latest data
        if (chartEntryIndex > 30) {
            lineChart.setVisibleXRangeMaximum(30);
            lineChart.moveViewToX(chartEntryIndex);
        }
        
        lineChart.invalidate();
        // chartEntryIndex is now managed by incrementChartIndex()
    }
    
    private void updateThroughputChart(PerformanceDataManager.ThroughputData data) {
        // Only add MQTT successful upload data points
        mqttThroughputEntries.add(new Entry(chartEntryIndex, data.mqttUploadedPerSecond));
        
        // Limit data points  
        if (mqttThroughputEntries.size() > 60) {
            mqttThroughputEntries.remove(0);
        }
        
        // If current chart is Throughput+Latency, update dual charts
        if (currentChartType == 1) {
            updateThroughputDisplayInDualChart();
        }
        
        Log.d("MainActivity", "Throughput updated: " + data.mqttUploadedPerSecond + " at index " + chartEntryIndex);
    }
    
    private void updateLatencyChart(PerformanceDataManager.LatencyData data) {
        // Add latency data points
        latencyEntries.add(new Entry(chartEntryIndex, data.totalLatencyMs));
        
        // Limit data points
        if (latencyEntries.size() > 60) {
            latencyEntries.remove(0);
        }
        
        // If current chart is Throughput+Latency, update dual charts
        if (currentChartType == 1) {
            updateLatencyDisplayInDualChart();
        }
        
        Log.d("MainActivity", "Latency updated: " + data.totalLatencyMs + "ms at index " + chartEntryIndex);
    }
    
    
    private void incrementChartIndex() {
        chartEntryIndex++;
        // Auto-scroll logic
        if (chartEntryIndex > 30) {
            if (currentChartType == 0 && lineChart != null) {
                lineChart.setVisibleXRangeMaximum(30);
                lineChart.moveViewToX(chartEntryIndex - 30);
                lineChart.invalidate();
            }
        }
    }
    
    private void updateCostChart(PerformanceDataManager.CostData data) {
        // Add cost data points
        costEntries.add(new Entry(chartEntryIndex, (float) data.totalCostUSD * 1000)); // Convert to milli-USD for display
        
        // Limit data points
        if (costEntries.size() > 60) {
            costEntries.remove(0);
        }
        
        // If current chart is Cost, update
        if (currentChartType == 2) {
            updateCostDisplay();
        }
    }
    
    private void updateThroughputDisplay() {
        List<ILineDataSet> dataSets = new ArrayList<>();
        
        // BLE throughput data set
        LineDataSet bleDataSet = new LineDataSet(new ArrayList<>(bleThrouputEntries), "BLE Messages/sec");
        bleDataSet.setColor(Color.parseColor("#FF6B35"));
        bleDataSet.setLineWidth(2f);
        bleDataSet.setDrawCircles(false);
        bleDataSet.setValueTextSize(0f);
        dataSets.add(bleDataSet);
        
        // MQTT throughput data set
        LineDataSet mqttDataSet = new LineDataSet(new ArrayList<>(mqttThroughputEntries), "MQTT Messages/sec");
        mqttDataSet.setColor(Color.parseColor("#4CAF50"));
        mqttDataSet.setLineWidth(2f);
        mqttDataSet.setDrawCircles(false);
        mqttDataSet.setValueTextSize(0f);
        dataSets.add(mqttDataSet);
        
        updateChartDisplay(dataSets);
    }
    
    private void updateLatencyDisplay() {
        List<ILineDataSet> dataSets = new ArrayList<>();
        
        LineDataSet latencyDataSet = new LineDataSet(new ArrayList<>(latencyEntries), "End-to-End Latency (ms)");
        latencyDataSet.setColor(Color.parseColor("#2196F3"));
        latencyDataSet.setLineWidth(2f);
        latencyDataSet.setDrawCircles(false);
        latencyDataSet.setValueTextSize(0f);
        dataSets.add(latencyDataSet);
        
        updateChartDisplay(dataSets);
    }
    
    private void updateCostDisplay() {
        List<ILineDataSet> dataSets = new ArrayList<>();
        
        LineDataSet costDataSet = new LineDataSet(new ArrayList<>(costEntries), "Total Cost (mUSD)");
        costDataSet.setColor(Color.parseColor("#FF9800"));
        costDataSet.setLineWidth(2f);
        costDataSet.setDrawCircles(false);
        costDataSet.setValueTextSize(0f);
        dataSets.add(costDataSet);
        
        updateChartDisplay(dataSets);
    }
    
    private void updateChartDisplay(List<ILineDataSet> dataSets) {
        LineData lineData = new LineData(dataSets);
        lineChart.setData(lineData);
        
        // Auto-scroll to latest data
        if (chartEntryIndex > 30) {
            lineChart.setVisibleXRangeMaximum(30);
            lineChart.moveViewToX(chartEntryIndex);
        }
        
        lineChart.invalidate();
    }
    
    private void updateCurrentChart() {
        // Update historical data based on current chart type
        switch (currentChartType) {
            case 0: // Resource chart
                if (!cpuEntries.isEmpty()) {
                    List<ILineDataSet> dataSets = new ArrayList<>();
                    
                    LineDataSet cpuDataSet = new LineDataSet(new ArrayList<>(cpuEntries), "CPU Usage (%)");
                    cpuDataSet.setColor(Color.parseColor("#FF6B35"));
                    cpuDataSet.setLineWidth(2f);
                    cpuDataSet.setDrawCircles(false);
                    cpuDataSet.setValueTextSize(0f);
                    dataSets.add(cpuDataSet);
                    
                    LineDataSet memoryDataSet = new LineDataSet(new ArrayList<>(memoryEntries), "Memory Usage (%)");
                    memoryDataSet.setColor(Color.parseColor("#4CAF50"));
                    memoryDataSet.setLineWidth(2f);
                    memoryDataSet.setDrawCircles(false);
                    memoryDataSet.setValueTextSize(0f);
                    dataSets.add(memoryDataSet);
                    
                    LineDataSet networkDataSet = new LineDataSet(new ArrayList<>(networkEntries), "Network Speed (KB/s)");
                    networkDataSet.setColor(Color.parseColor("#2196F3"));
                    networkDataSet.setLineWidth(2f);
                    networkDataSet.setDrawCircles(false);
                    networkDataSet.setValueTextSize(0f);
                    dataSets.add(networkDataSet);
                    
                    updateChartDisplay(dataSets);
                }
                break;
            case 1: // Throughput+Latency dual charts
                updateDualCharts();
                break;
            case 2: // Cost chart
                updateCostDisplay();
                break;
        }
    }
    
    private void setupDualCharts() {
        // Set throughput chart
        setupSingleChart(lineChartThroughput, "Throughput Chart");
        
        // Set latency chart
        setupSingleChart(lineChartLatency, "Latency Chart");
    }
    
    private void setupSingleChart(LineChart chart, String title) {
        // Basic configuration
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);
        chart.setDrawGridBackground(false);
        chart.setBackgroundColor(Color.WHITE);
        
        // Hide description text
        Description description = new Description();
        description.setEnabled(false);
        chart.setDescription(description);
        
        // X-axis configuration
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.LTGRAY);
        xAxis.setTextColor(Color.DKGRAY);
        xAxis.setGranularity(5f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int secondsAgo = Math.max(0, (int) (chartEntryIndex - value));
                int minutes = secondsAgo / 60;
                int seconds = secondsAgo % 60;
                return String.format("%02d:%02d", minutes, seconds);
            }
        });
        
        // Y-axis configuration
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);
        leftAxis.setTextColor(Color.DKGRAY);
        leftAxis.setAxisMinimum(0f);
        
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(false);
        
        // Legend configuration
        Legend legend = chart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextSize(10f);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.LEFT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }
    
    private void updateDualCharts() {
        updateThroughputDisplayInDualChart();
        updateLatencyDisplayInDualChart();
    }
    
    private void updateThroughputDisplayInDualChart() {
        try {
            List<ILineDataSet> dataSets = new ArrayList<>();
            
            if (!mqttThroughputEntries.isEmpty() && mqttThroughputEntries.size() > 0) {
                // Validate data
                List<Entry> validEntries = new ArrayList<>();
                for (Entry entry : mqttThroughputEntries) {
                    if (entry != null && !Float.isNaN(entry.getY()) && !Float.isInfinite(entry.getY())) {
                        validEntries.add(entry);
                    }
                }
                
                if (!validEntries.isEmpty()) {
                    LineDataSet mqttDataSet = new LineDataSet(validEntries, "MQTT Uploaded/sec");
                    mqttDataSet.setColor(Color.parseColor("#4CAF50"));
                    mqttDataSet.setLineWidth(2f);
                    mqttDataSet.setDrawCircles(false);
                    mqttDataSet.setValueTextSize(0f);
                    dataSets.add(mqttDataSet);
                }
            }
            
            if (!dataSets.isEmpty() && lineChartThroughput != null) {
                LineData lineData = new LineData(dataSets);
                lineChartThroughput.setData(lineData);
                
                if (chartEntryIndex > 30) {
                    lineChartThroughput.setVisibleXRangeMaximum(30);
                    lineChartThroughput.moveViewToX(chartEntryIndex - 30);
                }
                
                lineChartThroughput.invalidate();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error updating throughput chart: " + e.getMessage());
        }
    }
    
    private void updateLatencyDisplayInDualChart() {
        try {
            List<ILineDataSet> dataSets = new ArrayList<>();
            
            if (!latencyEntries.isEmpty() && latencyEntries.size() > 0) {
                // Validate data
                List<Entry> validEntries = new ArrayList<>();
                for (Entry entry : latencyEntries) {
                    if (entry != null && !Float.isNaN(entry.getY()) && !Float.isInfinite(entry.getY()) && entry.getY() >= 0) {
                        validEntries.add(entry);
                    }
                }
                
                if (!validEntries.isEmpty()) {
                    LineDataSet latencyDataSet = new LineDataSet(validEntries, "End-to-End Latency (ms)");
                    latencyDataSet.setColor(Color.parseColor("#2196F3"));
                    latencyDataSet.setLineWidth(2f);
                    latencyDataSet.setDrawCircles(false);
                    latencyDataSet.setValueTextSize(0f);
                    dataSets.add(latencyDataSet);
                }
            }
            
            if (!dataSets.isEmpty() && lineChartLatency != null) {
                LineData lineData = new LineData(dataSets);
                lineChartLatency.setData(lineData);
                
                if (chartEntryIndex > 30) {
                    lineChartLatency.setVisibleXRangeMaximum(30);
                    lineChartLatency.moveViewToX(chartEntryIndex - 30);
                }
                
                lineChartLatency.invalidate();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error updating latency chart: " + e.getMessage());
        }
    }
    
    private void initializeChartWithZeroData() {
        try {
            // Clear existing data
            cpuEntries.clear();
            memoryEntries.clear();
            networkEntries.clear();
            bleThrouputEntries.clear();
            mqttThroughputEntries.clear();
            latencyEntries.clear();
            costEntries.clear();
            
            // Reset index
            chartEntryIndex = 0;
            
            // Add initial 0 value data points
            for (int i = 0; i < 5; i++) {
                cpuEntries.add(new Entry(chartEntryIndex, 0f));
                memoryEntries.add(new Entry(chartEntryIndex, 0f));
                networkEntries.add(new Entry(chartEntryIndex, 0f));
                mqttThroughputEntries.add(new Entry(chartEntryIndex, 0f));
                latencyEntries.add(new Entry(chartEntryIndex, 0f));
                costEntries.add(new Entry(chartEntryIndex, 0f));
                chartEntryIndex++;
            }
            
            Log.d("MainActivity", "Chart initialized with zero data, entries: " + mqttThroughputEntries.size());
            
            // Show initial resource chart
            updateCurrentChart();
        } catch (Exception e) {
            Log.e("MainActivity", "Error initializing chart data: " + e.getMessage());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (performanceManager != null) {
            performanceManager.startMonitoring();
        }
        
        // Resume mock data generation
        if (mockDataGenerator != null && MockDataGenerator.isUsingMockData()) {
            mockDataGenerator.startMockDataGeneration();
            appendLog("Mock data generator resumed");
        }
        
        // Update connected device list
        updateConnectedDevicesList();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Do not stop monitoring on onPause, to avoid interruption when switching apps
        // Changed to stop only on onDestroy
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (performanceManager != null) {
            performanceManager.cleanup();
        }
        if (mockDataGenerator != null) {
            mockDataGenerator.stopMockDataGeneration();
        }
    }

    /**
     * Update connected device list
     */
    private void updateConnectedDevicesList() {
        connectedDevicesAdapter.clear();
        
        // Get all connected devices
        String[] connectedAddresses = getConnectedDeviceAddresses();
        
        for (String address : connectedAddresses) {
            String deviceName = getDeviceNameByAddress(address);
            String deviceInfo = deviceName + " (" + address + ")";
            connectedDevicesAdapter.add(deviceInfo);
        }
        
        // Update connection status display
        tvConnectionStatus.setText("BLE: " + connectedAddresses.length + " devices connected");
        
        // Update quick send buttons status
        updateQuickSendButtons();
    }

    /**
     * Get list of connected device addresses
     */
    private String[] getConnectedDeviceAddresses() {
        if (bleManager != null) {
            List<String> addresses = bleManager.getConnectedDeviceAddresses();
            return addresses.toArray(new String[0]);
        }
        return new String[0];
    }

    /**
     * Get device name by address
     */
    private String getDeviceNameByAddress(String address) {
        if (bleManager != null) {
            DeviceConnectionManager.DeviceConnectionInfo info = bleManager.getDeviceInfo(address);
            if (info != null) {
                return info.deviceName;
            }
        }
        return "Unknown Device";
    }
    
    /**
     * Send quick command to selected device
     */
    private void sendQuickCommand(String command) {
        if (selectedDeviceAddress == null) {
            Toast.makeText(this, "Please select a connected device first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!bleManager.isDeviceConnected(selectedDeviceAddress)) {
            Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show();
            selectedDeviceAddress = null;
            updateQuickSendButtons();
            return;
        }
        
        // Send UTF-8 string
        boolean success = bleManager.sendCommand(selectedDeviceAddress, command);
        if (success) {
            appendLog("Sent command '" + command + "' to device: " + selectedDeviceAddress);
            Toast.makeText(this, "Command sent: " + command, Toast.LENGTH_SHORT).show();
        } else {
            appendLog("Send command failed: " + command);
            Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Show device operation options dialog
     */
    private void showDeviceOptionsDialog(String deviceAddress) {
        String deviceName = getDeviceNameByAddress(deviceAddress);
        
        String[] options = {"View device info", "Disconnect"};
        
        new AlertDialog.Builder(this)
            .setTitle("Device Options: " + deviceName)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // View device info
                        showDeviceInfo(deviceAddress);
                        break;
                    case 1: // Disconnect
                        disconnectDevice(deviceAddress);
                        break;
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Show device detailed info
     */
    private void showDeviceInfo(String deviceAddress) {
        if (bleManager == null) return;
        
        DeviceConnectionManager.DeviceConnectionInfo info = bleManager.getDeviceInfo(deviceAddress);
        if (info == null) return;
        
        StringBuilder infoText = new StringBuilder();
        infoText.append("Device Name: ").append(info.deviceName).append("\n");
        infoText.append("Device Address: ").append(info.deviceAddress).append("\n");
        infoText.append("Connection Time: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(new java.util.Date(info.connectTime))).append("\n");
        infoText.append("Connection State: ").append(info.state.name()).append("\n");
        infoText.append("Reconnect Attempts: ").append(info.reconnectAttempts).append("\n");
        infoText.append("Is Reconnecting: ").append(info.isReconnecting ? "Yes" : "No");
        
        new AlertDialog.Builder(this)
            .setTitle("Device Info")
            .setMessage(infoText.toString())
            .setPositiveButton("OK", null)
            .show();
    }
    
    /**
     * Disconnect specified device
     */
    private void disconnectDevice(String deviceAddress) {
        if (bleManager == null) return;
        
        String deviceName = getDeviceNameByAddress(deviceAddress);
        
        new AlertDialog.Builder(this)
            .setTitle("Disconnect")
            .setMessage("Are you sure you want to disconnect device '" + deviceName + "'?")
            .setPositiveButton("Yes", (dialog, which) -> {
                bleManager.disconnectDevice(deviceAddress);
                if (selectedDeviceAddress != null && selectedDeviceAddress.equals(deviceAddress)) {
                    selectedDeviceAddress = null;
                    updateQuickSendButtons();
                }
                Toast.makeText(this, "Disconnected device: " + deviceName, Toast.LENGTH_SHORT).show();
                updateConnectedDevicesList();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    /**
     * Update quick send buttons status
     */
    private void updateQuickSendButtons() {
        boolean hasSelectedDevice = selectedDeviceAddress != null && 
                                  bleManager != null && 
                                  bleManager.isDeviceConnected(selectedDeviceAddress);
        
        btnSendR.setEnabled(hasSelectedDevice);
        btnSendY.setEnabled(hasSelectedDevice);
        btnSendB.setEnabled(hasSelectedDevice);
        
        // Update button colors
        if (hasSelectedDevice) {
            btnSendR.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5722")));
            btnSendY.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFC107")));
            btnSendB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")));
        } else {
            btnSendR.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
            btnSendY.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
            btnSendB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
        }
    }
}
