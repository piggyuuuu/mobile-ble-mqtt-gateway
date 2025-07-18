package com.have_no_eyes_deer.bleawsgateway;

import android.Manifest;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
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

import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // ==== BLE 配置 ====
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

    // ==== UI 元素 ====
    private Button btnScan, btnAwsSettings, btnConnectAws;
    private ListView listViewDevices;
    private ArrayAdapter<String> adapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private TextView tvConnectionStatus, tvReceivedData;
    private ToggleButton toggleReceive;

    // ==== AWS IoT 配置常量 ====
    private static final String PREFS_NAME        = "AwsPrefs";
    private static final String KEY_ENDPOINT      = "endpoint";
    private static final String KEY_KEY_URI       = "keyUri";
    private static final String KEY_CRED_URI      = "credUri";
    private static final String KEYSTORE_NAME     = "iot_keystore";
    private static final String KEYSTORE_PASSWORD = "iot_passwd";
    private static final String CERTIFICATE_ID    = "iot_cert";
    private TextView tvAwsStatus;

    private AWSIotMqttManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // 绑定视图
        btnScan            = findViewById(R.id.btnScan);
        listViewDevices    = findViewById(R.id.listViewDevices);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvAwsStatus        = findViewById(R.id.tvAwsStatus);
        toggleReceive      = findViewById(R.id.toggleReceive);
        tvReceivedData     = findViewById(R.id.tvReceivedData);
        btnAwsSettings     = findViewById(R.id.btnAwsSettings);
        btnConnectAws      = findViewById(R.id.btnConnectAws);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listViewDevices.setAdapter(adapter);

        // AWS 设置页面跳转
        btnAwsSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AwsSettingsActivity.class))
        );

        // 点击后触发 AWS IoT 连接
        btnConnectAws.setOnClickListener(v -> connectAwsIot());

        // BLE 初始化
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先打开蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        // 请求定位 & 蓝牙权限
        checkAndRequestPermissions();

        // 扫描按钮逻辑
        btnScan.setOnClickListener(v -> {
            if (!scanning) startBleScan();
            else           stopBleScan();
        });

        // 点击列表设备连接
        listViewDevices.setOnItemClickListener((parent, view, position, id) ->
                connectToDevice(deviceList.get(position))
        );

        // 收发切换
        toggleReceive.setOnCheckedChangeListener((btn, isChecked) -> {
            if (notifyCharacteristic == null || bluetoothGatt == null) return;
            if (isChecked) {
                startReceiving();
                sendStartCommand();
            } else {
                stopReceiving();
            }
        });
    }

    /** ← 新增：供外部访问 BLE Scanner */
    public BluetoothLeScanner getBleScanner() {
        return bleScanner;
    }

    // ===== BLE 方法集 =====

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
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getName() != null && !deviceList.contains(device)) {
                deviceList.add(device);
                adapter.add(device.getName() + " (" + device.getAddress() + ")");
                adapter.notifyDataSetChanged();
            }
        }
    };

    private void startBleScan() {
        deviceList.clear();
        adapter.clear();
        btnScan.setText("停止扫描");
        scanning = true;
        handler.postDelayed(this::stopBleScan, SCAN_PERIOD);
        bleScanner.startScan(scanCallback);
    }

    private void stopBleScan() {
        bleScanner.stopScan(scanCallback);
        btnScan.setText("扫描 BLE 设备");
        scanning = false;
    }

    private void connectToDevice(BluetoothDevice device) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("状态：连接中...");
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
                runOnUiThread(() -> tvConnectionStatus.setText("状态：已连接"));
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    tvConnectionStatus.setText("状态：已断开");
                    toggleReceive.setEnabled(false);
                    toggleReceive.setChecked(false);
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
            String data = new String(characteristic.getValue(), StandardCharsets.UTF_8);
            runOnUiThread(() -> tvReceivedData.append(data + "\n"));
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

    // ===== AWS IoT 连接逻辑 =====

    private void connectAwsIot() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String endpoint   = prefs.getString(KEY_ENDPOINT, "");
        String keyUriStr  = prefs.getString(KEY_KEY_URI, null);
        String certUriStr = prefs.getString(KEY_CRED_URI, null);

        if (endpoint.isEmpty() || keyUriStr == null || certUriStr == null) {
            Toast.makeText(this, "请先在“AWS IoT 设置”中完成配置", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri keyUri  = Uri.parse(keyUriStr);
        Uri certUri = Uri.parse(certUriStr);
        String keystorePath = getFilesDir().getAbsolutePath();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (!AWSIotKeystoreHelper.isKeystorePresent(keystorePath, KEYSTORE_NAME)) {
                    String certPem = slurp(getContentResolver().openInputStream(certUri));
                    String keyPem  = slurp(getContentResolver().openInputStream(keyUri));
                    AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                            CERTIFICATE_ID, certPem, keyPem,
                            keystorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD
                    );
                }
                KeyStore ks = AWSIotKeystoreHelper.getIotKeystore(
                        CERTIFICATE_ID, keystorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD
                );
                String clientId = UUID.randomUUID().toString();
                mqttManager = new AWSIotMqttManager(clientId, endpoint);
                mqttManager.setKeepAlive(10);
                mqttManager.connect(ks, new AWSIotMqttClientStatusCallback() {
                    @Override
                    public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    MainActivity.this,
                                    "AWS IoT 连接状态: " + status,
                                    Toast.LENGTH_SHORT
                            ).show();

                            // —— 新增：更新右侧状态栏 ——
                            tvAwsStatus.setText("AWS: " + status.name());

                            // 如果连接成功，发布 "connected" 并写日志
                            if (status == AWSIotMqttClientStatus.Connected) {
                                try {
                                    String topic = "test/bleawsgateway";
                                    mqttManager.publishString(
                                            "connected",
                                            topic,
                                            AWSIotMqttQos.QOS0
                                    );
                                    // —— 在日志框追加 ——
                                    tvReceivedData.append("AWS connected → topic: " + topic + "\n");
                                } catch (Exception e) {
                                    tvReceivedData.append("AWS publish error: " + e.getMessage() + "\n");
                                    e.printStackTrace();
                                }
                            } else if (status == AWSIotMqttClientStatus.ConnectionLost && throwable != null) {
                                // 连接断开或失败时，也记录一下
                                tvReceivedData.append("AWS error: " + throwable.getMessage() + "\n");
                            }
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "AWS 连接异常: " + e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // 可根据需要在此处理权限授予结果
    }
}
