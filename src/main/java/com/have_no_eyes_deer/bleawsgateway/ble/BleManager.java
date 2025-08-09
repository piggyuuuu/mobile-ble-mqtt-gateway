package com.have_no_eyes_deer.bleawsgateway.ble;

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
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;

import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import android.util.Log;
import java.util.Arrays;

/**
 * BLE Manager - Handles all BLE operations
 */
public class BleManager {
    
    private static final UUID CCC_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final long SCAN_PERIOD = 10_000; // 10s scan period
    
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private Handler mainHandler;
    
    // device connection management
    private DeviceConnectionManager connectionManager;
    private Map<String, BluetoothGatt> connectedDevices = new HashMap<>();
    private Map<String, BluetoothGattCharacteristic> notifyCharacteristics = new HashMap<>();
    private Map<String, BluetoothGattCharacteristic> writeCharacteristics = new HashMap<>();
    
    // scanning related
    private boolean isScanning = false;
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    
    // listener
    private List<BleDataListener> dataListeners = new ArrayList<>();
    private ScanResultListener scanResultListener;
    
    public interface ScanResultListener {
        void onDeviceFound(BluetoothDevice device, int rssi);
        void onScanStarted();
        void onScanStopped();
    }
    
    public BleManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.connectionManager = new DeviceConnectionManager(context);
        initializeBluetooth();
        setupConnectionManager();
    }
    
    private void initializeBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }
    
    private void setupConnectionManager() {
        connectionManager.setConnectionListener(new DeviceConnectionManager.DeviceConnectionListener() {
            @Override
            public void onDeviceConnected(String deviceAddress, String deviceName) {
                notifyConnectionStateChanged(deviceAddress, true, deviceName);
            }
            
            @Override
            public void onDeviceDisconnected(String deviceAddress, String deviceName) {
                notifyConnectionStateChanged(deviceAddress, false, deviceName);
            }
            
            @Override
            public void onDeviceReconnecting(String deviceAddress, int attempt) {
                notifyError("Reconnecting device: " + deviceAddress + " (attempt " + attempt + ")", deviceAddress);
            }
            
            @Override
            public void onConnectionFailed(String deviceAddress, String error) {
                notifyError("Connection failed: " + error, deviceAddress);
            }
            
            @Override
            public void onMaxConnectionsReached() {
                notifyError("Maximum connections reached", null);
            }
            
            @Override
            public void onConnectionPoolStatusChanged(int activeConnections, int maxConnections) {
                // 可以在这里添加连接池状态变化的处理
            }
        });

        // 转发底层GATT数据到BleManager的数据通知
        connectionManager.setGattDataListener((deviceAddress, deviceName, characteristic) -> {
            String serviceUuid = characteristic.getService().getUuid().toString();
            String charUuid = characteristic.getUuid().toString();
            byte[] rawData = characteristic.getValue();
            String dataString = new String(rawData, java.nio.charset.StandardCharsets.UTF_8);
            BleDataModel data = new BleDataModel(
                deviceAddress, deviceName, serviceUuid, charUuid, rawData, dataString
            );
            notifyDataReceived(data);
        });
    }
    
    // ======================== listener management ========================
    
    public void addDataListener(BleDataListener listener) {
        if (!dataListeners.contains(listener)) {
            dataListeners.add(listener);
        }
    }
    
    public void removeDataListener(BleDataListener listener) {
        dataListeners.remove(listener);
    }
    
    public void setScanResultListener(ScanResultListener listener) {
        this.scanResultListener = listener;
    }
    
    private void notifyDataReceived(BleDataModel data) {
        mainHandler.post(() -> {
            for (BleDataListener listener : dataListeners) {
                listener.onDataReceived(data);
            }
        });
    }
    
    private void notifyConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
        Log.d("BleManager", "notifyConnectionStateChanged: " + deviceAddress + " connected: " + isConnected + " name: " + deviceName);
        Log.d("BleManager", "dataListeners count: " + dataListeners.size());
        
        mainHandler.post(() -> {
            for (BleDataListener listener : dataListeners) {
                Log.d("BleManager", "Notifying listener: " + listener.getClass().getSimpleName());
                listener.onConnectionStateChanged(deviceAddress, isConnected, deviceName);
            }
            // 通知扫描结果监听器连接状态变化
            if (scanResultListener != null) {
                // 这里可以添加一个回调方法来通知连接状态变化
                // 暂时通过重新扫描来更新设备状态
            }
        });
    }
    
    private void notifyError(String error, String deviceAddress) {
        mainHandler.post(() -> {
            for (BleDataListener listener : dataListeners) {
                listener.onError(error, deviceAddress);
            }
        });
    }
    
    // ======================== scanning ========================
    
    public boolean startScan() {
        if (bleScanner == null || isScanning) {
            return false;
        }
        
        if (!hasPermissions()) {
            notifyError("Bluetooth permission missing", null);
            return false;
        }
        
        discoveredDevices.clear();
        isScanning = true;
        
        if (scanResultListener != null) {
            scanResultListener.onScanStarted();
        }
        
        // set scan timeout
        mainHandler.postDelayed(this::stopScan, SCAN_PERIOD);
        
        bleScanner.startScan(scanCallback);
        return true;
    }
    
    public void stopScan() {
        if (bleScanner != null && isScanning) {
            bleScanner.stopScan(scanCallback);
            isScanning = false;
            if (scanResultListener != null) {
                scanResultListener.onScanStopped();
            }
        }
    }
    
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            // 移除设备名字过滤条件，允许显示所有发现的设备
            if (!discoveredDevices.contains(device)) {
                discoveredDevices.add(device);
                if (scanResultListener != null) {
                    scanResultListener.onDeviceFound(device, result.getRssi());
                }
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            notifyError("scan failed: " + errorCode, null);
        }
    };
    
    // ======================== connection ========================
    
    public boolean connectToDevice(BluetoothDevice device) {
        return connectToDevice(device, 1); // 默认优先级为1
    }
    
    public boolean connectToDevice(BluetoothDevice device, int priority) {
        if (device == null || !hasPermissions()) {
            return false;
        }
        
        String address = device.getAddress();
        if (connectionManager.isDeviceConnected(address)) {
            notifyError("device already connected", address);
            return false;
        }
        
        return connectionManager.connectDevice(device, priority);
    }
    
    public boolean connectToDevice(String deviceAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        return connectToDevice(device);
    }
    
    public void disconnectDevice(String deviceAddress) {
        connectionManager.disconnectDevice(deviceAddress);
    }
    
    public void disconnectAllDevices() {
        connectionManager.disconnectAllDevices();
    }
    
    // ======================== GATT callback ========================
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String deviceAddress = gatt.getDevice().getAddress();
            String deviceName = gatt.getDevice().getName();
            
            Log.d("BleManager", "onConnectionStateChange: " + deviceAddress + " status: " + status + " newState: " + newState);
            
            // 如果设备名字为空，尝试从连接管理器中获取
            if (deviceName == null || deviceName.trim().isEmpty()) {
                DeviceConnectionManager.DeviceConnectionInfo info = connectionManager.getDeviceInfo(deviceAddress);
                if (info != null && info.deviceName != null && !info.deviceName.trim().isEmpty()) {
                    deviceName = info.deviceName;
                }
            }
            
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d("BleManager", "Device connected: " + deviceAddress + " name: " + deviceName);
                // 添加到已连接设备列表
                connectedDevices.put(deviceAddress, gatt);
                Log.d("BleManager", "Added to connectedDevices, size: " + connectedDevices.size());
                notifyConnectionStateChanged(deviceAddress, true, deviceName);
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d("BleManager", "Device disconnected: " + deviceAddress + " name: " + deviceName);
                notifyConnectionStateChanged(deviceAddress, false, deviceName);
                connectedDevices.remove(deviceAddress);
                notifyCharacteristics.remove(deviceAddress);
                writeCharacteristics.remove(deviceAddress);
                // 不要自动关闭GATT连接，让连接管理器处理
                // gatt.close();
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                discoverCharacteristics(gatt);
            } else {
                notifyError("service discovery failed", gatt.getDevice().getAddress());
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            String deviceAddress = gatt.getDevice().getAddress();
            String deviceName = gatt.getDevice().getName();
            String serviceUuid = characteristic.getService().getUuid().toString();
            String charUuid = characteristic.getUuid().toString();
            
            // create data model and notify listener
            byte[] rawData = characteristic.getValue();
            String dataString = new String(rawData, java.nio.charset.StandardCharsets.UTF_8);
            BleDataModel data = new BleDataModel(
                deviceAddress, deviceName, serviceUuid, charUuid, rawData, dataString
            );
            
            notifyDataReceived(data);
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post(() -> {
                    for (BleDataListener listener : dataListeners) {
                        listener.onDataSent(gatt.getDevice().getAddress(), characteristic.getValue());
                    }
                });
            } else {
                notifyError("data sending failed", gatt.getDevice().getAddress());
            }
        }
    };
    
    // ======================== characteristic discovery and management ========================
    
    private void discoverCharacteristics(BluetoothGatt gatt) {
        String deviceAddress = gatt.getDevice().getAddress();
        
        for (BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                int properties = characteristic.getProperties();
                
                // find notify characteristic
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    notifyCharacteristics.put(deviceAddress, characteristic);
                }
                
                // find write characteristic
                if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | 
                                  BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                    writeCharacteristics.put(deviceAddress, characteristic);
                }
            }
        }
    }
    
    // ======================== data operation ========================
    
    public boolean enableNotification(String deviceAddress) {
        BluetoothGatt gatt = connectedDevices.get(deviceAddress);
        BluetoothGattCharacteristic characteristic = notifyCharacteristics.get(deviceAddress);
        
        if (gatt != null && characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true);
            
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                return gatt.writeDescriptor(descriptor);
            }
        }
        
        // 尝试使用连接管理器
        return connectionManager.enableNotification(deviceAddress);
    }
    
    public boolean disableNotification(String deviceAddress) {
        BluetoothGatt gatt = connectedDevices.get(deviceAddress);
        BluetoothGattCharacteristic characteristic = notifyCharacteristics.get(deviceAddress);
        
        if (gatt != null && characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, false);
            
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_UUID);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                return gatt.writeDescriptor(descriptor);
            }
        }
        return false;
    }
    
    public boolean sendData(String deviceAddress, byte[] data) {
        BluetoothGatt gatt = connectedDevices.get(deviceAddress);
        BluetoothGattCharacteristic characteristic = writeCharacteristics.get(deviceAddress);
        
        if (gatt != null && characteristic != null) {
            characteristic.setValue(data);
            return gatt.writeCharacteristic(characteristic);
        }
        
        // 尝试使用连接管理器
        return connectionManager.sendData(deviceAddress, data);
    }
    
    public boolean sendCommand(String deviceAddress, String command) {
        return sendData(deviceAddress, command.getBytes());
    }
    
    // ======================== utility methods ========================
    
    private boolean hasPermissions() {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
               == PackageManager.PERMISSION_GRANTED;
    }
    
    public boolean isScanning() {
        return isScanning;
    }
    
    public boolean isDeviceConnected(String deviceAddress) {
        return connectedDevices.containsKey(deviceAddress) || connectionManager.isDeviceConnected(deviceAddress);
    }
    
    public List<String> getConnectedDeviceAddresses() {
        List<String> addresses = new ArrayList<>(connectedDevices.keySet());
        // 添加连接管理器中的设备
        String[] managerAddresses = connectionManager.getConnectedDeviceAddresses();
        for (String address : managerAddresses) {
            if (!addresses.contains(address)) {
                addresses.add(address);
            }
        }
        
        // 添加调试信息
        Log.d("BleManager", "getConnectedDeviceAddresses: " + addresses.size() + " devices");
        Log.d("BleManager", "connectedDevices: " + connectedDevices.keySet());
        Log.d("BleManager", "managerAddresses: " + Arrays.toString(managerAddresses));
        
        return addresses;
    }
    
    public int getActiveConnectionCount() {
        return connectionManager.getActiveConnectionCount();
    }
    
    public int getMaxConnectionCount() {
        return connectionManager.getMaxConnectionCount();
    }
    
    public DeviceConnectionManager.DeviceConnectionInfo getDeviceInfo(String deviceAddress) {
        return connectionManager.getDeviceInfo(deviceAddress);
    }
    
    public List<BluetoothDevice> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }
    
    // ======================== cleanup ========================
    
    public void cleanup() {
        cleanup(true); // 默认断开所有连接
    }
    
    public void cleanup(boolean disconnectAll) {
        stopScan();
        if (disconnectAll) {
            disconnectAllDevices();
        }
        dataListeners.clear();
        if (connectionManager != null) {
            connectionManager.cleanup(disconnectAll);
        }
    }
} 