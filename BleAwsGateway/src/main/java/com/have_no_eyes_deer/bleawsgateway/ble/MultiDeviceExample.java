package com.have_no_eyes_deer.bleawsgateway.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.have_no_eyes_deer.bleawsgateway.model.BleDataModel;

import java.util.List;

/**
 * 多设备连接使用示例
 * 展示如何使用新的多设备连接功能
 */
public class MultiDeviceExample {
    private static final String TAG = "MultiDeviceExample";
    
    private BleManager bleManager;
    private Context context;
    
    public MultiDeviceExample(Context context) {
        this.context = context;
        this.bleManager = new BleManager(context);
        setupListeners();
    }
    
    /**
     * 设置监听器
     */
    private void setupListeners() {
        bleManager.addDataListener(new BleDataListener() {
            @Override
            public void onDataReceived(BleDataModel data) {
                Log.d(TAG, "Received data : " + data.getDeviceName() + " - " + data.getDataString());
            }
            
            @Override
            public void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
                Log.d(TAG, "Connect state changed: " + deviceName + " - " + (isConnected ? "connected" : "not connected"));
            }
            
            @Override
            public void onError(String error, String deviceAddress) {
                Log.e(TAG, "Error: " + error + (deviceAddress != null ? " [" + deviceAddress + "]" : ""));
            }
            
            @Override
            public void onDataSent(String deviceAddress, byte[] data) {
                Log.d(TAG, "Data send to: " + deviceAddress);
            }
        });
    }
    
    /**
     * 示例1: 连接单个设备
     */
    public void connectSingleDevice(BluetoothDevice device) {
        Log.d(TAG, "Connect single device: " + device.getName());
        
        // 使用默认优先级连接
        boolean success = bleManager.connectToDevice(device);
        if (success) {
            Log.d(TAG, "Connection request sent");
        } else {
            Log.e(TAG, "Connection request failed");
        }
    }
    
    /**
     * 示例2: 连接多个设备（带优先级）
     */
    public void connectMultipleDevices(List<BluetoothDevice> devices) {
        Log.d(TAG, "Connect multiple devices, total: " + devices.size());
        
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice device = devices.get(i);
            // 设置优先级，数字越大优先级越高
            int priority = devices.size() - i; // 第一个设备优先级最高
            
            boolean success = bleManager.connectToDevice(device, priority);
            Log.d(TAG, "device " + device.getName() + " connection request: " + (success ? "succeed" : "fail"));
        }
    }
    
    /**
     * 示例3: 批量发送数据到所有连接的设备
     */
    public void sendDataToAllDevices(String message) {
        List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
        Log.d(TAG, "Send data to " + connectedDevices.size() + " devices");
        
        for (String deviceAddress : connectedDevices) {
            boolean success = bleManager.sendData(deviceAddress, message.getBytes());
            Log.d(TAG, "send to " + deviceAddress + ": " + (success ? "successfully" : "failed"));
        }
    }
    
    /**
     * 示例4: 向特定设备发送数据
     */
    public void sendDataToDevice(String deviceAddress, String message) {
        if (bleManager.isDeviceConnected(deviceAddress)) {
            boolean success = bleManager.sendData(deviceAddress, message.getBytes());
            Log.d(TAG, "send to " + deviceAddress + ": " + (success ? "successfully" : "failed"));
        } else {
            Log.w(TAG, "device " + deviceAddress + " not connected");
        }
    }
    
    /**
     * 示例5: 获取连接状态信息
     */
    public void printConnectionStatus() {
        int activeCount = bleManager.getActiveConnectionCount();
        int maxCount = bleManager.getMaxConnectionCount();
        List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
        
        Log.d(TAG, "=== connection state ===");
        Log.d(TAG, "Active Connections: " + activeCount + "/" + maxCount);
        Log.d(TAG, "Connected devices:");
        
        for (String address : connectedDevices) {
            DeviceConnectionManager.DeviceConnectionInfo info = bleManager.getDeviceInfo(address);
            if (info != null) {
                Log.d(TAG, "  - " + info.deviceName + " (" + address + ") - state: " + info.state);
            } else {
                Log.d(TAG, "  - " + address + " (INFO not available)");
            }
        }
    }
    
    /**
     * 示例6: 断开特定设备
     */
    public void disconnectDevice(String deviceAddress) {
        Log.d(TAG, "disconnect device: " + deviceAddress);
        bleManager.disconnectDevice(deviceAddress);
    }
    
    /**
     * 示例7: 断开所有设备
     */
    public void disconnectAllDevices() {
        Log.d(TAG, "disconnect all devices");
        bleManager.disconnectAllDevices();
    }
    
    /**
     * 示例8: 启用所有设备的通知
     */
    public void enableNotificationsForAll() {
        List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
        Log.d(TAG, "Enable notifications for " + connectedDevices.size() + " devices");
        
        for (String deviceAddress : connectedDevices) {
            boolean success = bleManager.enableNotification(deviceAddress);
            Log.d(TAG, "Enable notifications for " + deviceAddress + ": " + (success ? "successfully" : "failed"));
        }
    }
    
    /**
     * 示例9: 扫描并自动连接设备
     */
    public void scanAndAutoConnect() {
        Log.d(TAG, "Start scanning and automatically connect");
        
        // 设置扫描结果监听器
        bleManager.setScanResultListener(new BleManager.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                Log.d(TAG, "Discover devices: " + device.getName() + " (RSSI: " + rssi + ")");
                
                // 自动连接特定名称的设备
                if (device.getName() != null && device.getName().contains("Sensor")) {
                    Log.d(TAG, "Automatically connect: " + device.getName());
                    bleManager.connectToDevice(device);
                }
            }
            
            @Override
            public void onScanStarted() {
                Log.d(TAG, "Start scanning");
            }
            
            @Override
            public void onScanStopped() {
                Log.d(TAG, "Scan finished");
            }
        });
        
        // 开始扫描
        bleManager.startScan();
    }
    
    /**
     * 示例10: 监控连接池状态
     */
    public void monitorConnectionPool() {
        int activeCount = bleManager.getActiveConnectionCount();
        int maxCount = bleManager.getMaxConnectionCount();
        
        if (activeCount >= maxCount) {
            Log.w(TAG, "The connection pool is full. (" + activeCount + "/" + maxCount + ")");
        } else {
            Log.d(TAG, "connection pool state: " + activeCount + "/" + maxCount);
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (bleManager != null) {
            bleManager.cleanup();
        }
    }
    
    /**
     * 获取BLE管理器实例
     */
    public BleManager getBleManager() {
        return bleManager;
    }
} 