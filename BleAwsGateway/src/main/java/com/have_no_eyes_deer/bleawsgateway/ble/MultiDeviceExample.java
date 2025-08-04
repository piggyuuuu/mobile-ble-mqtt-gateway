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
                Log.d(TAG, "收到数据: " + data.getDeviceName() + " - " + data.getDataString());
            }
            
            @Override
            public void onConnectionStateChanged(String deviceAddress, boolean isConnected, String deviceName) {
                Log.d(TAG, "连接状态变化: " + deviceName + " - " + (isConnected ? "已连接" : "已断开"));
            }
            
            @Override
            public void onError(String error, String deviceAddress) {
                Log.e(TAG, "错误: " + error + (deviceAddress != null ? " [" + deviceAddress + "]" : ""));
            }
            
            @Override
            public void onDataSent(String deviceAddress, byte[] data) {
                Log.d(TAG, "数据已发送到: " + deviceAddress);
            }
        });
    }
    
    /**
     * 示例1: 连接单个设备
     */
    public void connectSingleDevice(BluetoothDevice device) {
        Log.d(TAG, "连接单个设备: " + device.getName());
        
        // 使用默认优先级连接
        boolean success = bleManager.connectToDevice(device);
        if (success) {
            Log.d(TAG, "连接请求已发送");
        } else {
            Log.e(TAG, "连接请求失败");
        }
    }
    
    /**
     * 示例2: 连接多个设备（带优先级）
     */
    public void connectMultipleDevices(List<BluetoothDevice> devices) {
        Log.d(TAG, "连接多个设备，数量: " + devices.size());
        
        for (int i = 0; i < devices.size(); i++) {
            BluetoothDevice device = devices.get(i);
            // 设置优先级，数字越大优先级越高
            int priority = devices.size() - i; // 第一个设备优先级最高
            
            boolean success = bleManager.connectToDevice(device, priority);
            Log.d(TAG, "设备 " + device.getName() + " 连接请求: " + (success ? "成功" : "失败"));
        }
    }
    
    /**
     * 示例3: 批量发送数据到所有连接的设备
     */
    public void sendDataToAllDevices(String message) {
        List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
        Log.d(TAG, "向 " + connectedDevices.size() + " 个设备发送数据");
        
        for (String deviceAddress : connectedDevices) {
            boolean success = bleManager.sendData(deviceAddress, message.getBytes());
            Log.d(TAG, "发送到 " + deviceAddress + ": " + (success ? "成功" : "失败"));
        }
    }
    
    /**
     * 示例4: 向特定设备发送数据
     */
    public void sendDataToDevice(String deviceAddress, String message) {
        if (bleManager.isDeviceConnected(deviceAddress)) {
            boolean success = bleManager.sendData(deviceAddress, message.getBytes());
            Log.d(TAG, "发送到 " + deviceAddress + ": " + (success ? "成功" : "失败"));
        } else {
            Log.w(TAG, "设备 " + deviceAddress + " 未连接");
        }
    }
    
    /**
     * 示例5: 获取连接状态信息
     */
    public void printConnectionStatus() {
        int activeCount = bleManager.getActiveConnectionCount();
        int maxCount = bleManager.getMaxConnectionCount();
        List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
        
        Log.d(TAG, "=== 连接状态 ===");
        Log.d(TAG, "活跃连接: " + activeCount + "/" + maxCount);
        Log.d(TAG, "已连接设备:");
        
        for (String address : connectedDevices) {
            DeviceConnectionManager.DeviceConnectionInfo info = bleManager.getDeviceInfo(address);
            if (info != null) {
                Log.d(TAG, "  - " + info.deviceName + " (" + address + ") - 状态: " + info.state);
            } else {
                Log.d(TAG, "  - " + address + " (信息不可用)");
            }
        }
    }
    
    /**
     * 示例6: 断开特定设备
     */
    public void disconnectDevice(String deviceAddress) {
        Log.d(TAG, "断开设备: " + deviceAddress);
        bleManager.disconnectDevice(deviceAddress);
    }
    
    /**
     * 示例7: 断开所有设备
     */
    public void disconnectAllDevices() {
        Log.d(TAG, "断开所有设备");
        bleManager.disconnectAllDevices();
    }
    
    /**
     * 示例8: 启用所有设备的通知
     */
    public void enableNotificationsForAll() {
        List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
        Log.d(TAG, "为 " + connectedDevices.size() + " 个设备启用通知");
        
        for (String deviceAddress : connectedDevices) {
            boolean success = bleManager.enableNotification(deviceAddress);
            Log.d(TAG, "启用通知 " + deviceAddress + ": " + (success ? "成功" : "失败"));
        }
    }
    
    /**
     * 示例9: 扫描并自动连接设备
     */
    public void scanAndAutoConnect() {
        Log.d(TAG, "开始扫描并自动连接");
        
        // 设置扫描结果监听器
        bleManager.setScanResultListener(new BleManager.ScanResultListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi) {
                Log.d(TAG, "发现设备: " + device.getName() + " (RSSI: " + rssi + ")");
                
                // 自动连接特定名称的设备
                if (device.getName() != null && device.getName().contains("Sensor")) {
                    Log.d(TAG, "自动连接传感器设备: " + device.getName());
                    bleManager.connectToDevice(device);
                }
            }
            
            @Override
            public void onScanStarted() {
                Log.d(TAG, "扫描开始");
            }
            
            @Override
            public void onScanStopped() {
                Log.d(TAG, "扫描结束");
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
            Log.w(TAG, "连接池已满 (" + activeCount + "/" + maxCount + ")");
        } else {
            Log.d(TAG, "连接池状态: " + activeCount + "/" + maxCount);
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