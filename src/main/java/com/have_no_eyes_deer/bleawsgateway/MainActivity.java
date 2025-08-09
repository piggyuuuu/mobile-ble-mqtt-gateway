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
import android.view.MotionEvent;
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
import android.widget.ImageView;

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
import java.util.Set;
import java.util.HashSet;

import com.have_no_eyes_deer.bleawsgateway.ble.BleManager;
import com.have_no_eyes_deer.bleawsgateway.ble.DeviceConnectionManager;
import com.have_no_eyes_deer.bleawsgateway.ble.BleDataListener;
import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;

import java.util.HashMap;
import java.util.Map;

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
    
    // BLE扫描相关
    private Button btnScan, btnConnectSelected, btnDisconnectSelected;
    private ListView listViewDevices;
    private TextView tvScanStatus, tvDeviceCount, tvConnectionPool;
    private ArrayAdapter<DeviceItem> deviceAdapter;
    private List<DeviceItem> discoveredDevices = new ArrayList<>();
    private Map<String, DeviceItem> deviceMap = new HashMap<>();
    private boolean isScanning = false;

    // 原有的BLE相关
    private BleManager bleManager;
    private ListView listViewConnectedDevices;
    private ArrayAdapter<String> connectedDevicesAdapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private TextView tvConnectionStatus, tvReceivedData;
    private TextView tvBleConnectionStatus;
    private ImageView ivBleStatusDot, ivAwsStatusDot;
    private ToggleButton toggleReceive;
    
    // AWS相关按钮
    private Button btnAwsSettings, btnConnectAws, btnDiagnoseAws, btnPerformanceTest;
    
    // 快速发送按钮
    private Button btnSendR, btnSendY, btnSendB;
    
    // Currently selected device (single)
    private String selectedDeviceAddress = null;
    // Multiple selection set for connected devices
    private final Set<String> selectedDeviceAddresses = new HashSet<>();
    private final java.util.List<String> connectedAddressesCache = new java.util.ArrayList<>();

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
            }
            
            return "Unknown Device";
        }

        public void updateDeviceName(String name) {
            if (name != null && !name.trim().isEmpty()) {
                this.cachedDeviceName = name;
            }
        }
    }
    
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

        // 初始化本地 BluetoothAdapter，后续用于名称回退获取
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Exception ignored) {}

        // 初始化UI组件
        initializeViews();
        
        // 初始化BLE管理器
        bleManager = new BleManager(this);
        
        // 请求权限
        checkAndRequestPermissions();
        
        // 设置事件监听器
        setupListeners();
        
        // 初始化性能监控
        initializePerformanceMonitoring();
    }

    private void initializeViews() {
        // 原有的UI组件
        btnAwsSettings = findViewById(R.id.btnAwsSettings);
        btnConnectAws = findViewById(R.id.btnConnectAws);
        btnDiagnoseAws = findViewById(R.id.btnDiagnoseAws);
        btnPerformanceTest = findViewById(R.id.btnPerformanceTest);
        btnSendR = findViewById(R.id.btnSendR);
        btnSendY = findViewById(R.id.btnSendY);
        btnSendB = findViewById(R.id.btnSendB);
        listViewConnectedDevices = findViewById(R.id.listViewConnectedDevices);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        tvBleConnectionStatus = findViewById(R.id.tvBleConnectionStatus);
        tvAwsStatus = findViewById(R.id.tvAwsStatus);
        ivBleStatusDot = findViewById(R.id.ivBleStatusDot);
        ivAwsStatusDot = findViewById(R.id.ivAwsStatusDot);
        // 默认置为红色（未连接）
        setBleIndicator(false);
        setAwsIndicator(false);
        toggleReceive = findViewById(R.id.toggleReceive);
        etSendData = findViewById(R.id.etSendData);
        btnSendData = findViewById(R.id.btnSendData);
        btnClearLog = findViewById(R.id.btnClearLog);
        btnCopyLog = findViewById(R.id.btnCopyLog);
        btnDetailedLog = findViewById(R.id.btnDetailedLog);
        spinnerSendType = findViewById(R.id.spinnerSendType);
        spinnerReceiveType = findViewById(R.id.spinnerReceiveType);
        // 初始化发送/接收格式下拉框（默认UTF-8）
        ArrayAdapter<String> sendTypeAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_item_black, DATA_TYPES);
        sendTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black);
        spinnerSendType.setAdapter(sendTypeAdapter);
        spinnerSendType.setSelection(TYPE_UTF8);
        spinnerSendType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateInputHint(position);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        // 初始化接收格式（默认UTF-8）
        ArrayAdapter<String> receiveTypeAdapter = new ArrayAdapter<>(
                this, R.layout.spinner_item_black, DATA_TYPES);
        receiveTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_black);
        spinnerReceiveType.setAdapter(receiveTypeAdapter);
        spinnerReceiveType.setSelection(TYPE_UTF8);
        // 设置初始输入框提示
        updateInputHint(TYPE_UTF8);

        // 新增的BLE扫描相关UI组件
        btnScan = findViewById(R.id.btnScan);
        btnConnectSelected = findViewById(R.id.btnConnectSelected);
        btnDisconnectSelected = findViewById(R.id.btnDisconnectSelected);
        listViewDevices = findViewById(R.id.listViewDevices);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        tvDeviceCount = findViewById(R.id.tvDeviceCount);
        tvConnectionPool = findViewById(R.id.tvConnectionPool);

        // 图表相关组件
        lineChart = findViewById(R.id.lineChart);
        lineChartThroughput = findViewById(R.id.lineChartThroughput);
        lineChartLatency = findViewById(R.id.lineChartLatency);
        layoutDualCharts = findViewById(R.id.layoutDualCharts);
        btnResourceChart = findViewById(R.id.btnResourceChart);
        btnThroughputChart = findViewById(R.id.btnThroughputChart);
        btnCostChart = findViewById(R.id.btnCostChart);
        tvCpuUsage = findViewById(R.id.tvCpuUsage);
        tvMemUsage = findViewById(R.id.tvMemUsage);
        tvNetworkUsage = findViewById(R.id.tvNetworkUsage);

        // 初始化适配器
        connectedDevicesAdapter = new ArrayAdapter<>(this, R.layout.checked_device_list_item);
        listViewConnectedDevices.setAdapter(connectedDevicesAdapter);
        listViewConnectedDevices.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        deviceAdapter = new ArrayAdapter<>(this, R.layout.device_list_item);
        listViewDevices.setAdapter(deviceAdapter);

        // 允许 ListView 在 ScrollView 中独立垂直滚动
        enableListInnerScroll(listViewDevices);
        enableListInnerScroll(listViewConnectedDevices);

        // 确保“发送格式”和“输入框”可用，满足可选择UTF-8并可输入内容
        try {
            spinnerSendType.setEnabled(true);
            spinnerReceiveType.setEnabled(true);
            etSendData.setEnabled(true);
            btnSendData.setEnabled(true);
            toggleReceive.setEnabled(true);
        } catch (Exception ignored) {}
    }

    private void setBleIndicator(boolean connected) {
        if (ivBleStatusDot != null) {
            ivBleStatusDot.setImageResource(connected ? R.drawable.indicator_green : R.drawable.indicator_red);
        }
    }

    private void setAwsIndicator(boolean connected) {
        if (ivAwsStatusDot != null) {
            ivAwsStatusDot.setImageResource(connected ? R.drawable.indicator_green : R.drawable.indicator_red);
        }
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
        
        // 添加BLE连接状态监听器
        if (bleManager != null) {
            bleManager.addDataListener(new BleDataListener() {
                @Override
                public void onDataReceived(BleDataModel data) {
                    // 多设备接收路径：将接收内容输出到日志，并按选择的接收格式显示
                    runOnUiThread(() -> {
                        try {
                            int receiveType = spinnerReceiveType.getSelectedItemPosition();
                            byte[] raw = data.getRawData();
                            String displayData;
                            String formatName = DATA_TYPES[receiveType];
                            switch (receiveType) {
                                case TYPE_UTF8:
                                    displayData = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                                    break;
                                case TYPE_HEX:
                                    displayData = byteArrayToHexString(raw);
                                    break;
                                case TYPE_BYTES:
                                    displayData = byteArrayToString(raw);
                                    break;
                                default:
                                    displayData = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                                    formatName = DATA_TYPES[TYPE_UTF8];
                                    break;
                            }

                            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(new java.util.Date());
                            String devName = (data.getDeviceName() != null && !data.getDeviceName().trim().isEmpty()) ? data.getDeviceName() : "Unknown";
                            String line = String.format("[%s] %s (%s) → Received (%s): %s (%d bytes)",
                                    timestamp, devName, data.getDeviceAddress(), formatName, displayData, raw != null ? raw.length : 0);
                            logAndScroll(line);

                            // 性能统计
                            if (performanceManager != null) {
                                performanceManager.recordBleMessage(data.getDeviceAddress(), new String(raw, java.nio.charset.StandardCharsets.UTF_8));
                            }

                            // 转发到 AWS IoT（若可用）
                            if (mqttManager != null) {
                                try {
                                    String utf8Data = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                                    String cleanDeviceName = devName;

                                    // 识别温度数据（形如 T1:23.5C）
                                    String jsonMessage;
                                    boolean isTemperatureData = utf8Data.matches("T\\d+:[\\d.]+C");

                                    if (isTemperatureData) {
                                        String[] parts = utf8Data.split(":");
                                        String sampleNumber = parts[0].substring(1); // 去掉 'T'
                                        String temperature = parts[1].replace("C", "");

                                        jsonMessage = String.format(
                                                "{\"device\":\"%s\",\"deviceName\":\"%s\",\"timestamp\":\"%s\",\"type\":\"temperature\",\"sampleNumber\":%s,\"temperature\":%s,\"unit\":\"C\",\"rawData\":\"%s\"}",
                                                escapeJsonString(data.getDeviceAddress()),
                                                escapeJsonString(cleanDeviceName),
                                                getIsoTimestamp(),
                                                sampleNumber,
                                                temperature,
                                                escapeJsonString(utf8Data)
                                        );
                                    } else {
                                        jsonMessage = String.format(
                                                "{\"device\":\"%s\",\"deviceName\":\"%s\",\"timestamp\":\"%s\",\"data\":\"%s\",\"dataLength\":%d,\"type\":\"sensor_data\"}",
                                                escapeJsonString(data.getDeviceAddress()),
                                                escapeJsonString(cleanDeviceName),
                                                getIsoTimestamp(),
                                                escapeJsonString(utf8Data),
                                                raw != null ? raw.length : 0
                                        );
                                    }

                                    String topic = "devices/" + data.getDeviceAddress().replace(":", "") + "/data";
                                    if (performanceManager != null) performanceManager.recordMqttSendStart();
                                    mqttManager.publishString(jsonMessage, topic, com.amazonaws.mobileconnectors.iot.AWSIotMqttQos.QOS0);
                                    appendLog("→ Sent to AWS IoT: " + topic);
                                    if (performanceManager != null) performanceManager.recordMqttSendComplete(true);
                                } catch (Exception e) {
                                    appendLog("→ MQTT send failed: " + e.getClass().getSimpleName());
                                    if (performanceManager != null) performanceManager.recordMqttSendComplete(false);
                                }
                            } else {
                                appendDetailedLog("MQTT manager not initialized, skip forwarding");
                            }
                        } catch (Exception e) {
                            appendLog("Receive parse error: " + e.getMessage());
                        }
                    });
                }

                @Override
                public void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
                    runOnUiThread(() -> {
                        // 添加调试信息
                        Log.d("MainActivity", "Connection state changed: " + deviceAddress + 
                              " connected: " + isConnected + " name: " + deviceName);
                        
                        // 更新设备连接状态
                        DeviceItem item = deviceMap.get(deviceAddress);
                        if (item != null) {
                            item.isConnected = isConnected;
                            item.connectionStatus = isConnected ? "已连接" : "未连接";
                            if (deviceName != null && !deviceName.trim().isEmpty() && !deviceName.equals("Unknown Device")) {
                                item.updateDeviceName(deviceName);
                            }
                            deviceAdapter.notifyDataSetChanged();
                        }
                        
                        // 强制刷新连接设备列表
                        updateConnectedDevicesList();
                        
                        // 强制刷新适配器
                        if (connectedDevicesAdapter != null) {
                            connectedDevicesAdapter.notifyDataSetChanged();
                        }
                        
                        // 更新状态信息
                        updateDeviceCount();
                        updateConnectionPoolStatus();
                        
                        // 显示连接状态变化提示
                        String statusText = isConnected ? "已连接" : "已断开";
                        String deviceDisplayName = deviceName != null && !deviceName.trim().isEmpty() ? 
                            deviceName : "Unknown Device";
                        Toast.makeText(MainActivity.this, 
                            "设备 " + deviceDisplayName + " " + statusText, 
                            Toast.LENGTH_SHORT).show();
                        
                        // 添加调试信息
                        Log.d("MainActivity", "Connection state change completed, adapter count: " + 
                              (connectedDevicesAdapter != null ? connectedDevicesAdapter.getCount() : 0));
                    });
                }

                @Override
                public void onError(String error, String deviceAddress) {
                    runOnUiThread(() -> {
                        appendLog("BLE错误: " + error);
                        Toast.makeText(MainActivity.this, "BLE错误: " + error, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onDataSent(String deviceAddress, byte[] data) {
                    // 数据发送时的处理
                }
            });

            // 设置扫描结果监听器
            bleManager.setScanResultListener(new BleManager.ScanResultListener() {
                @Override
                public void onDeviceFound(BluetoothDevice device, int rssi) {
                    runOnUiThread(() -> addOrUpdateDevice(device, rssi));
                }

                @Override
                public void onScanStarted() {
                    runOnUiThread(() -> {
                        isScanning = true;
                        tvScanStatus.setText(getString(R.string.scanning));
                        btnScan.setText(getString(R.string.stop_scan));
                    });
                }

                @Override
                public void onScanStopped() {
                    runOnUiThread(() -> {
                        isScanning = false;
                        tvScanStatus.setText(getString(R.string.scan_stopped));
                        btnScan.setText(getString(R.string.start_scan));
                    });
                }
            });
        }
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



    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private void connectToDevice(BluetoothDevice device) {
        runOnUiThread(() -> {
            tvBleConnectionStatus.setText("BLE: Connecting...");
            toggleReceive.setEnabled(false);
            toggleReceive.setChecked(false);
            tvReceivedData.setText("");
            setBleIndicator(false);
        });
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override public void onConnectionStateChange(@NonNull BluetoothGatt gatt,
                                                      int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread(() -> {
                    tvBleConnectionStatus.setText("BLE: Connected");
                        setBleIndicator(true);
                    // Enable send test controls
                    etSendData.setEnabled(true);
                    btnSendData.setEnabled(true);
                    spinnerSendType.setEnabled(true);
                    spinnerReceiveType.setEnabled(true);
                });
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    tvBleConnectionStatus.setText("BLE: Disconnected");
                        setBleIndicator(false);
                    toggleReceive.setEnabled(false);
                    toggleReceive.setChecked(false);
                    // Disable send test controls
                    etSendData.setEnabled(false);
                    btnSendData.setEnabled(false);
                    spinnerSendType.setEnabled(false);
                    spinnerReceiveType.setEnabled(false);
                });
                // 不要自动关闭GATT连接，让BleManager处理
                // gatt.close();
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
                setAwsIndicator(false);
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
            setAwsIndicator(false);
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
                                    setAwsIndicator(false);
                                    break;
                                    
                                case Connected:
                                    appendLog("AWS IoT connected successfully!");
                                    Toast.makeText(MainActivity.this, "AWS IoT connected successfully", Toast.LENGTH_SHORT).show();
                                    setAwsIndicator(true);
                                    
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
                                    setAwsIndicator(false);
                                    break;
                                    
                                case ConnectionLost:
                                    appendLog("Connection lost");
                                    setAwsIndicator(false);
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
                    setAwsIndicator(false);
                    
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
                        logAndScroll("Send failed: invalid HEX string");
                        return;
                    }
                    break;
                case TYPE_BYTES:
                    dataBytes = parseByteArray(data);
                    if (dataBytes == null) {
                        Toast.makeText(this, "Byte array format error! Please use comma-separated values 0-255, e.g.: 65,66,67,68", Toast.LENGTH_LONG).show();
                        logAndScroll("Send failed: invalid byte array string");
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
                logAndScroll("BLE data testing: writeCharacteristic success");
            } else {
                tvReceivedData.append("send data failed: BLE write operation failed\n");
                logAndScroll("BLE data testing: writeCharacteristic failed");
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

    // 统一记录并滚动到最新
    private void logAndScroll(String line) {
        appendLog(line);
        try {
            final android.widget.ScrollView logScroll = (android.widget.ScrollView) ((android.view.View) tvReceivedData.getParent());
            logScroll.post(() -> logScroll.fullScroll(android.view.View.FOCUS_DOWN));
        } catch (Exception ignored) {}
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
        if (btnResourceChart != null) {
            btnResourceChart.setOnClickListener(v -> switchChart(0));
        }
        if (btnThroughputChart != null) {
            btnThroughputChart.setOnClickListener(v -> switchChart(1));
        }
        if (btnCostChart != null) {
            btnCostChart.setOnClickListener(v -> switchChart(2));
        }
    }
    
    private void switchChart(int chartType) {
        currentChartType = chartType;
        
        // Update button styles
        resetChartButtonStyles();
        switch (chartType) {
            case 0: // Resource chart
                if (btnResourceChart != null) {
                    btnResourceChart.setBackgroundColor(Color.parseColor("#2196F3"));
                    btnResourceChart.setTextColor(Color.WHITE);
                    btnResourceChart.invalidate();
                }
                // Show single chart, hide dual charts
                if (lineChart != null) lineChart.setVisibility(View.VISIBLE);
                if (layoutDualCharts != null) layoutDualCharts.setVisibility(View.GONE);
                setupChart();
                updateCurrentChart();
                Log.d("MainActivity", "Switched to Resource chart");
                break;
            case 1: // Throughput+Latency dual charts
                if (btnThroughputChart != null) {
                    btnThroughputChart.setBackgroundColor(Color.parseColor("#2196F3"));
                    btnThroughputChart.setTextColor(Color.WHITE);
                    btnThroughputChart.invalidate();
                }
                // Hide single chart, show dual charts
                if (lineChart != null) lineChart.setVisibility(View.GONE);
                if (layoutDualCharts != null) layoutDualCharts.setVisibility(View.VISIBLE);
                setupDualCharts();
                updateDualCharts();
                Log.d("MainActivity", "Switched to Throughput+Latency chart");
                break;
            case 2: // Cost chart
                if (btnCostChart != null) {
                    btnCostChart.setBackgroundColor(Color.parseColor("#2196F3"));
                    btnCostChart.setTextColor(Color.WHITE);
                    btnCostChart.invalidate();
                }
                // Show single chart, hide dual charts
                if (lineChart != null) lineChart.setVisibility(View.VISIBLE);
                if (layoutDualCharts != null) layoutDualCharts.setVisibility(View.GONE);
                setupChart();
                updateCurrentChart();
                Log.d("MainActivity", "Switched to Cost chart");
                break;
        }
    }
    
    private void resetChartButtonStyles() {
        if (btnResourceChart != null) {
            btnResourceChart.setBackgroundColor(Color.parseColor("#CCCCCC"));
            btnResourceChart.setTextColor(Color.parseColor("#666666"));
            btnResourceChart.invalidate();
        }
        if (btnThroughputChart != null) {
            btnThroughputChart.setBackgroundColor(Color.parseColor("#CCCCCC"));
            btnThroughputChart.setTextColor(Color.parseColor("#666666"));
            btnThroughputChart.invalidate();
        }
        if (btnCostChart != null) {
            btnCostChart.setBackgroundColor(Color.parseColor("#CCCCCC"));
            btnCostChart.setTextColor(Color.parseColor("#666666"));
            btnCostChart.invalidate();
        }
    }
    
    private void setupChart() {
        if (lineChart == null) return;
        
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
        if (tvCpuUsage != null) {
            tvCpuUsage.setText(String.format(Locale.getDefault(), "CPU: %.1f%%", data.cpuUsagePercent));
        }
        if (tvMemUsage != null) {
            tvMemUsage.setText(String.format(Locale.getDefault(), "Memory: %dMB", data.memoryUsageMB));
        }
        if (tvNetworkUsage != null) {
            tvNetworkUsage.setText(String.format(Locale.getDefault(), "Network: %.1fKB/s", data.networkSpeedKBps));
        }
        
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
        
        // 强制更新连接设备列表
        updateConnectedDevicesList();
        
        // 强制刷新适配器
        if (connectedDevicesAdapter != null) {
            connectedDevicesAdapter.notifyDataSetChanged();
        }
        
        // 更新设备状态
        updateDeviceCount();
        updateConnectionPoolStatus();
        
        // 添加调试信息
        Log.d("MainActivity", "onResume completed, adapter count: " + 
              (connectedDevicesAdapter != null ? connectedDevicesAdapter.getCount() : 0));
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // 不要停止监控，避免切换应用时中断
        // 改为只在onDestroy时停止
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
        
        // 停止扫描
        if (isScanning) {
            stopScan();
        }
        
        // 不要在这里清理BLE连接，让BleManager保持连接状态
        // if (bleManager != null) {
        //     bleManager.cleanup();
        // }
    }

    /**
     * Update connected device list
     */
    private void updateConnectedDevicesList() {
        connectedDevicesAdapter.clear();
        connectedAddressesCache.clear();
        
        // Get all connected devices
        String[] connectedAddresses = getConnectedDeviceAddresses();
        
        // 添加调试信息
        Log.d("MainActivity", "updateConnectedDevicesList called, found " + connectedAddresses.length + " connected devices");
        
        for (String address : connectedAddresses) {
            String deviceName = getDeviceNameByAddress(address);
            // 确保设备名字不为空
            if (deviceName == null || deviceName.trim().isEmpty() || deviceName.equals("Unknown Device")) {
                deviceName = "Unknown Device";
            }
            String deviceInfo = deviceName + " (" + address + ")";
            connectedDevicesAdapter.add(deviceInfo);
            connectedAddressesCache.add(address);
            Log.d("MainActivity", "Added device to list: " + deviceInfo);
        }
        
        // Update connection status display
        String statusText = connectedAddresses.length == 1 ? "device connected" : "devices connected";
        tvBleConnectionStatus.setText("BLE: " + connectedAddresses.length + " " + statusText);
        setBleIndicator(connectedAddresses.length > 0);
        
        // Update quick send buttons status
        updateQuickSendButtons();
        
        // 添加调试信息
        Log.d("MainActivity", "Updated connected devices list, adapter count: " + connectedDevicesAdapter.getCount());
        
        // 同步勾选状态
        restoreConnectedSelectionChecks();

        // 强制刷新适配器
        if (connectedDevicesAdapter != null) {
            connectedDevicesAdapter.notifyDataSetChanged();
            Log.d("MainActivity", "Forced adapter refresh, final count: " + connectedDevicesAdapter.getCount());
        }
    }

    private void restoreConnectedSelectionChecks() {
        // 按缓存勾选状态刷新 ListView 复选框
        for (int i = 0; i < connectedAddressesCache.size(); i++) {
            String address = connectedAddressesCache.get(i);
            listViewConnectedDevices.setItemChecked(i, selectedDeviceAddresses.contains(address));
        }
        // 更新按钮可用状态
        updateQuickSendButtons();
    }

    /**
     * Get list of connected device addresses
     */
    private String[] getConnectedDeviceAddresses() {
        if (bleManager != null) {
            List<String> addresses = bleManager.getConnectedDeviceAddresses();
            Log.d("MainActivity", "getConnectedDeviceAddresses: " + addresses.size() + " devices");
            Log.d("MainActivity", "Addresses: " + addresses);
            return addresses.toArray(new String[0]);
        }
        Log.d("MainActivity", "getConnectedDeviceAddresses: bleManager is null");
        return new String[0];
    }

    /**
     * Get device name by address
     */
    private String getDeviceNameByAddress(String address) {
        if (bleManager != null) {
            DeviceConnectionManager.DeviceConnectionInfo info = bleManager.getDeviceInfo(address);
            if (info != null) {
                // 优先使用连接管理器缓存的名称
                if (info.deviceName != null && !info.deviceName.trim().isEmpty() && !"Unknown Device".equals(info.deviceName)) {
                    return info.deviceName;
                }
            }
            
            // 如果从连接管理器获取不到，尝试从蓝牙适配器获取
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                String deviceName = device.getName();
                if (deviceName != null && !deviceName.trim().isEmpty() && !"Unknown Device".equals(deviceName)) {
                    return deviceName;
                }
            } catch (Exception e) {
                Log.w("MainActivity", "Failed to get device name for: " + address, e);
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
        boolean hasSelectedDevices = !selectedDeviceAddresses.isEmpty();
        
        btnSendR.setEnabled(hasSelectedDevices);
        btnSendY.setEnabled(hasSelectedDevices);
        btnSendB.setEnabled(hasSelectedDevices);
        
        // Update button colors
        if (hasSelectedDevices) {
            btnSendR.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF5722")));
            btnSendY.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFC107")));
            btnSendB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3")));
        } else {
            btnSendR.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
            btnSendY.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
            btnSendB.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GRAY));
        }
    }

    // 群发快捷命令到多选设备
    private void sendQuickCommandToSelected(String command) {
        if (bleManager == null || selectedDeviceAddresses.isEmpty()) {
            Toast.makeText(this, "Please select at least one connected device", Toast.LENGTH_SHORT).show();
            return;
        }
        int success = 0;
        for (String address : new java.util.HashSet<>(selectedDeviceAddresses)) {
            if (bleManager.isDeviceConnected(address)) {
                boolean ok = bleManager.sendCommand(address, command);
                if (ok) success++;
            }
        }
        String msg = "Sent '" + command + "' to " + success + "/" + selectedDeviceAddresses.size();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        logAndScroll(msg);
    }

    private void setupListeners() {
        // 原有的监听器
        btnAwsSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AWSSettingsActivity.class))
        );

        btnConnectAws.setOnClickListener(v -> connectAwsIot());
        btnDiagnoseAws.setOnClickListener(v -> diagnoseAwsConnection());
        btnPerformanceTest.setOnClickListener(v -> {
            Toast.makeText(this, "Navigating to performance test interface...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, PerformanceTestActivity.class));
        });

        // BLE扫描相关监听器
        btnScan.setOnClickListener(v -> {
            if (isScanning) {
                stopScan();
            } else {
                startScan();
            }
        });

        btnConnectSelected.setOnClickListener(v -> connectSelectedDevices());
        btnDisconnectSelected.setOnClickListener(v -> disconnectSelectedDevices());

        // 设备列表点击事件
        listViewDevices.setOnItemClickListener((parent, view, position, id) -> {
            DeviceItem item = deviceAdapter.getItem(position);
            if (item != null) {
                if (item.isConnected) {
                    disconnectDevice(item);
                } else {
                    connectDevice(item);
                }
            }
        });

        listViewDevices.setOnItemLongClickListener((parent, view, position, id) -> {
            DeviceItem item = deviceAdapter.getItem(position);
            if (item != null) {
                showDeviceDetails(item);
                return true;
            }
            return false;
        });

        // 已连接设备列表点击事件（切换多选）
        listViewConnectedDevices.setOnItemClickListener((parent, view, position, id) -> {
            if (position < connectedAddressesCache.size()) {
                String address = connectedAddressesCache.get(position);
                boolean nowChecked = listViewConnectedDevices.isItemChecked(position);
                if (nowChecked) selectedDeviceAddresses.add(address); else selectedDeviceAddresses.remove(address);
                updateQuickSendButtons();
            }
        });

        // 长按显示操作菜单
        listViewConnectedDevices.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < connectedAddressesCache.size()) {
                String address = connectedAddressesCache.get(position);
                showDeviceOptionsDialog(address);
                return true;
            }
            return false;
        });

        // 快速发送按钮事件（群发到多选设备）
        btnSendR.setOnClickListener(v -> sendQuickCommandToSelected("R"));
        btnSendY.setOnClickListener(v -> sendQuickCommandToSelected("Y"));
        btnSendB.setOnClickListener(v -> sendQuickCommandToSelected("B"));

        // 数据发送测试按钮事件
        btnSendData.setOnClickListener(v -> {
            String data = etSendData.getText().toString().trim();
            if (!data.isEmpty()) {
                sendTestData(data);
                etSendData.setText("");
                logAndScroll("Local send click: length=" + data.length());
            } else {
                Toast.makeText(this, "Please enter data to send", Toast.LENGTH_SHORT).show();
            }
        });

        btnClearLog.setOnClickListener(v -> tvReceivedData.setText(""));
        btnCopyLog.setOnClickListener(v -> copyLogToClipboard());
        btnDetailedLog.setOnClickListener(v -> toggleDetailedLogging());

        // 接收开关事件（群开关通知）
        toggleReceive.setOnCheckedChangeListener((btn, isChecked) -> {
            if (bleManager == null || selectedDeviceAddresses.isEmpty()) return;
            int success = 0;
            for (String address : new java.util.HashSet<>(selectedDeviceAddresses)) {
                boolean ok = isChecked ? bleManager.enableNotification(address)
                                       : bleManager.disableNotification(address);
                if (ok) success++;
            }
            String action = isChecked ? "Start receiving" : "Stop receiving";
            String msg = action + ": " + success + "/" + selectedDeviceAddresses.size();
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            logAndScroll(msg);
            if (isChecked) {
                // 发送开始命令（如果你的外设需要约定的启动命令）
                for (String address : new java.util.HashSet<>(selectedDeviceAddresses)) {
                    bleManager.sendCommand(address, "s");
                }
                logAndScroll("Sent start command 's' to selected devices");
            }
        });
    }

    /**
     * 使 ListView 在外层 ScrollView 中可独立滚动
     */
    private void enableListInnerScroll(ListView listView) {
        if (listView == null) return;
        listView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                default:
                    break;
            }
            return false; // 让 ListView 自己处理滚动
        });
    }

    // ===== BLE扫描相关方法 =====

    private void startScan() {
        if (bleManager != null && !isScanning) {
            isScanning = true;
            btnScan.setText(getString(R.string.stop_scan));
            tvScanStatus.setText(getString(R.string.scanning));
            
            // 清空之前的设备列表
            discoveredDevices.clear();
            deviceMap.clear();
            deviceAdapter.clear();
            
            // 开始扫描
            if (bleManager.startScan()) {
                Log.d("MainActivity", "BLE扫描已开始");
            } else {
                Log.e("MainActivity", "BLE扫描启动失败");
                isScanning = false;
                btnScan.setText(getString(R.string.start_scan));
                tvScanStatus.setText(getString(R.string.scan_failed));
            }
        }
    }

    private void stopScan() {
        if (bleManager != null && isScanning) {
            bleManager.stopScan();
            isScanning = false;
            btnScan.setText(getString(R.string.start_scan));
            tvScanStatus.setText(getString(R.string.scan_stopped));
            Log.d("MainActivity", "BLE扫描已停止");
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
            existingItem.connectionStatus = existingItem.isConnected ? "Connected" : "Not Connected";
            
            // 尝试更新设备名字
            String deviceName = device.getName();
            if (deviceName != null && !deviceName.trim().isEmpty()) {
                existingItem.updateDeviceName(deviceName);
            }
        } else {
            // 添加新设备
            DeviceItem newItem = new DeviceItem(device, rssi);
            newItem.isConnected = bleManager.isDeviceConnected(address);
            newItem.connectionStatus = newItem.isConnected ? "Connected" : "Not Connected";
            
            deviceMap.put(address, newItem);
            discoveredDevices.add(newItem);
            deviceAdapter.add(newItem);
        }
        
        deviceAdapter.notifyDataSetChanged();
        updateDeviceCount();
        updateConnectionPoolStatus();
    }

    private void connectDevice(DeviceItem item) {
        if (bleManager != null && !item.isConnected) {
            Log.d("MainActivity", "连接设备: " + item.getDeviceName() + " (" + item.getDeviceAddress() + ")");
            bleManager.connectToDevice(item.device);
        }
    }

    private void disconnectDevice(DeviceItem item) {
        if (bleManager != null && item.isConnected) {
            Log.d("MainActivity", "断开设备: " + item.getDeviceName() + " (" + item.getDeviceAddress() + ")");
            bleManager.disconnectDevice(item.getDeviceAddress());
        }
    }

    private void connectSelectedDevices() {
        int connectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (!item.isConnected) {
                connectDevice(item);
                connectedCount++;
            }
        }
        if (connectedCount > 0) {
            Toast.makeText(this, "正在连接 " + connectedCount + " 个设备", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有可连接的设备", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectSelectedDevices() {
        int disconnectedCount = 0;
        for (DeviceItem item : discoveredDevices) {
            if (item.isConnected) {
                disconnectDevice(item);
                disconnectedCount++;
            }
        }
        if (disconnectedCount > 0) {
            Toast.makeText(this, "正在断开 " + disconnectedCount + " 个设备", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有已连接的设备", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeviceDetails(DeviceItem item) {
        String details = String.format("Device Name: %s\nDevice Address: %s\nRSSI: %d dBm\nConnection Status: %s\nDiscovered At: %s",
            item.getDeviceName(),
            item.getDeviceAddress(),
            item.rssi,
            item.connectionStatus,
            new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(item.discoveryTime))
        );
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Device Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show();
    }

    private void updateDeviceCount() {
        int totalDevices = discoveredDevices.size();
        int connectedDevices = 0;
        for (DeviceItem item : discoveredDevices) {
            if (item.isConnected) {
                connectedDevices++;
            }
        }
        tvDeviceCount.setText(getString(R.string.device_count, totalDevices, connectedDevices));
    }

    private void updateConnectionPoolStatus() {
        if (bleManager != null) {
            int activeConnections = bleManager.getActiveConnectionCount();
            int maxConnections = bleManager.getMaxConnectionCount();
            tvConnectionPool.setText(getString(R.string.connection_pool, activeConnections, maxConnections));
        }
    }
}

