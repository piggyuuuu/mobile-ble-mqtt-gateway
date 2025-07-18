package com.have_no_eyes_deer.bleawsgateway;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
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

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.regions.Regions;

import java.io.File;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS    = 1001;
    private static final int REQUEST_AWS_SETTINGS   = 2001;
    private static final long SCAN_PERIOD           = 10_000; // 10s
    private static final UUID  CCC_UUID             =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // BLE
    private BluetoothAdapter       bluetoothAdapter;
    public  BluetoothLeScanner     bleScanner;
    private BluetoothGatt          bluetoothGatt;
    private BluetoothGattCharacteristic notifyCharacteristic;
    private BluetoothGattCharacteristic writeCharacteristic;

    // UI & state
    private Button      btnScan;
    private ListView    listView;
    private ArrayAdapter<String> adapter;
    private List<BluetoothDevice> deviceList = new ArrayList<>();
    private Handler     handler = new Handler();
    private boolean     scanning = false;

    // AWS IoT
    private Button      btnAwsSettings;
    private TextView    tvAwsStatus;
    private AWSIotMqttManager mqttManager;
    private KeyStore    keyStore    = null;
    private String      certAlias   = "default";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ① 只调用一次 setContentView
        setContentView(R.layout.activity_main);

        // ② 一次性绑定所有控件
        btnScan        = findViewById(R.id.btnScan);
        listView       = findViewById(R.id.listViewDevices);
        btnAwsSettings = findViewById(R.id.btnAwsSettings);
        tvAwsStatus    = findViewById(R.id.tvAwsStatus);

        // 设置 BLE 列表适配器
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>());
        listView.setAdapter(adapter);

        // 初始化蓝牙
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "请先打开蓝牙", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        bleScanner = bluetoothAdapter.getBluetoothLeScanner();

        // 请求权限
        checkAndRequestPermissions();

        // ③ 设置点击事件
        btnScan.setOnClickListener(v -> {
            if (!scanning) startBleScan();
            else           stopBleScan();
        });

        listView.setOnItemClickListener((parent, view, pos, id) -> {
            BluetoothDevice device = deviceList.get(pos);
            connectToDevice(device);
        });

        btnAwsSettings.setOnClickListener(v -> {
            // 调试用 Toast，确认点击监听生效
            Toast.makeText(this, "跳转到 AWS 设置", Toast.LENGTH_SHORT).show();
            startActivityForResult(
                    new Intent(this, AWSSettingsActivity.class),
                    REQUEST_AWS_SETTINGS
            );
        });
    }


    /** 权限检查与申请 */
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

    /** 扫描回调 */
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int cbType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device.getName() != null && !deviceList.contains(device)) {
                deviceList.add(device);
                adapter.add(device.getName() + " (" + device.getAddress() + ")");
                adapter.notifyDataSetChanged();
            }
        }
    };

    /** 开始 / 停止 扫描 */
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

    /** 发起 GATT 连接 */
    private void connectToDevice(BluetoothDevice device) {
        runOnUiThread(() -> Toast.makeText(this,
                "连接: " + device.getName(), Toast.LENGTH_SHORT).show());
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }

    /** GATT 回调，处理连接 & 服务发现 & 通知 */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(@NonNull BluetoothGatt gatt,
                                            int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "已连接", Toast.LENGTH_SHORT).show());
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this,
                                "已断开", Toast.LENGTH_SHORT).show());
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(@NonNull BluetoothGatt gatt, int status) {
            // 找到第一个可 Notify 和第一个可 Write 特征
            for (BluetoothGattService svc : gatt.getServices()) {
                for (BluetoothGattCharacteristic chr
                        : svc.getCharacteristics()) {
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
                if (notifyCharacteristic != null
                        && writeCharacteristic  != null) break;
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic chr) {
            String data = new String(chr.getValue(), java.nio.charset.StandardCharsets.UTF_8);
            runOnUiThread(() -> {
                // 这里你可以把 data 显示在一个 TextView 或日志里
                Toast.makeText(MainActivity.this,
                        "接收到: " + data, Toast.LENGTH_SHORT).show();
            });
        }
    };

    /** 发送单字节 's' 命令（UTF-8） */
    private void sendStartCommand() {
        if (writeCharacteristic == null || bluetoothGatt == null) return;
        byte[] cmd = "s".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeCharacteristic.setValue(cmd);
        bluetoothGatt.writeCharacteristic(writeCharacteristic);
    }

    /** AWS 设置返回后，触发连接 */
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_AWS_SETTINGS && res == RESULT_OK) {
            connectAwsIot();
        }
    }

    /** AWS IoT 连接逻辑 */
    private void connectAwsIot() {
        SharedPreferences prefs = getSharedPreferences("aws_iot", MODE_PRIVATE);
        String endpoint   = prefs.getString("endpoint", "");
        String clientId   = prefs.getString("clientId", "");
        String certPath   = prefs.getString("certPath", "");
        String keyPath    = prefs.getString("keyPath", "");
        String cognitoId  = prefs.getString("cognitoId", "");

        if (endpoint.isEmpty() || clientId.isEmpty()
                || certPath.isEmpty()  || keyPath.isEmpty()
                || cognitoId.isEmpty()) {
            tvAwsStatus.setText("AWS 状态：设置不完整");
            return;
        }
        tvAwsStatus.setText("AWS 状态：连接中…");

        // 1. 初始化 Cognito 身份池
        CognitoCachingCredentialsProvider credsProvider =
                new CognitoCachingCredentialsProvider(
                        getApplicationContext(),
                        cognitoId,
                        Regions.AP_SOUTHEAST_2
                );

        // 2. 初始化 MQTT 管理器
        mqttManager = new AWSIotMqttManager(clientId, endpoint);
        mqttManager.setKeepAlive(10);

        // 3. 读取 PEM 证书和私钥文件内容
        String certPem, keyPem;
        try {
            certPem = readFileAsString(certPath);
            keyPem  = readFileAsString(keyPath);
        } catch (Exception e) {
            tvAwsStatus.setText("AWS 状态：读取证书失败");
            e.printStackTrace();
            return;
        }

        // 4. keystore 存放配置
        String keystorePath     = getFilesDir().getPath();
        String keystoreName     = "aws_keystore";
        String keystorePassword = "";  // 如果有密码则填写

        try {
            // 保存证书和私钥到 keystore 文件
            AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                    clientId,
                    certPem,
                    keyPem,
                    keystorePath,
                    keystoreName,
                    keystorePassword
            );
            // 从文件加载到内存 KeyStore
            keyStore = AWSIotKeystoreHelper.getIotKeystore(
                    clientId,
                    keystorePath,
                    keystoreName,
                    keystorePassword
            );
        } catch (Exception e) {
            tvAwsStatus.setText("AWS 状态：证书加载失败");
            e.printStackTrace();
            return;
        }

        // 5. 发起 MQTT 连接并更新状态回调
        mqttManager.connect(keyStore, new AWSIotMqttClientStatusCallback() {
            @Override
            public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                runOnUiThread(() ->
                        tvAwsStatus.setText("AWS 状态：" + status.name())
                );
            }
        });
    }

    /** 辅助：将指定路径的文件读成 UTF-8 字符串 */
    private String readFileAsString(String path) throws Exception {
        FileInputStream fis = new FileInputStream(new File(path));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        fis.close();
        return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
}