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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.content.Intent;         // ← 新增
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    // Client Characteristic Configuration descriptor UUID
    private static final UUID CCC_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;
    public BluetoothLeScanner bleScanner;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;

    private Handler handler = new Handler();
    private boolean scanning = false;

    private static final long SCAN_PERIOD = 10_000; // 10 seconds
    private static final int REQUEST_PERMISSIONS = 1001;

    private Button btnScan;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();

    private TextView tvConnectionStatus;
    private ToggleButton toggleReceive;
    private TextView tvReceivedData;
    private Button btnAwsSettings;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // bind views
        btnScan            = findViewById(R.id.btnScan);
        listView           = findViewById(R.id.listViewDevices);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        toggleReceive      = findViewById(R.id.toggleReceive);
        tvReceivedData     = findViewById(R.id.tvReceivedData);
        btnAwsSettings     = findViewById(R.id.btnAwsSettings);  // ← 新增绑定
        listView           = findViewById(R.id.listViewDevices);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        listView.setAdapter(adapter);
        btnAwsSettings = findViewById(R.id.btnAwsSettings);
        btnAwsSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AwsSettingsActivity.class);
            startActivity(intent);
        });
        // init Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先打开蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        // permissions
        checkAndRequestPermissions();

        // scan button
        btnScan.setOnClickListener(v -> {
            if (!scanning) startBleScan();
            else           stopBleScan();
        });

        // device select
        listView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = deviceList.get(position);
            connectToDevice(device);
        });

        // receive toggle
        toggleReceive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (notifyCharacteristic == null || bluetoothGatt == null) return;
            if (isChecked) {
                startReceiving();
                sendStartCommand();
            } else {
                stopReceiving();
            }
        });
    }

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
            ActivityCompat.requestPermissions(this,
                    perms.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
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
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt, int status, int newState) {
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

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            // 找到第一个支持 NOTIFY 和第一个支持 WRITE 的特征
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
            runOnUiThread(() -> toggleReceive.setEnabled(notifyCharacteristic != null));
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic) {
            byte[] raw = characteristic.getValue();
            // 显式用 UTF-8 解码
            String data = new String(raw, StandardCharsets.UTF_8);
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

    /** 向板子发送字符 's' */
    private void sendStartCommand() {
        if (writeCharacteristic == null || bluetoothGatt == null) return;
        byte[] cmd = "s".getBytes(StandardCharsets.UTF_8);
        writeCharacteristic.setValue(cmd);
        bluetoothGatt.writeCharacteristic(writeCharacteristic);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
