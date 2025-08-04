package com.have_no_eyes_deer.bleawsgateway.ble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 多设备连接管理器
 * 负责管理多个BLE设备的连接状态、重连机制和资源限制
 */
public class DeviceConnectionManager {
    private static final String TAG = "DeviceConnectionManager";
    
    // 连接限制常量
    private static final int MAX_CONCURRENT_CONNECTIONS = 5; // Android设备通常支持5-7个并发连接
    private static final int RECONNECT_DELAY_MS = 3000; // 重连延迟3秒
    private static final int MAX_RECONNECT_ATTEMPTS = 3; // 最大重连次数
    
    private Context context;
    private Handler mainHandler;
    private ExecutorService executorService;
    
    // 连接池管理
    private Map<String, BluetoothGatt> activeConnections = new ConcurrentHashMap<>();
    private Map<String, DeviceConnectionInfo> connectionInfoMap = new ConcurrentHashMap<>();
    private Map<String, BluetoothGattCharacteristic> notifyCharacteristics = new ConcurrentHashMap<>();
    private Map<String, BluetoothGattCharacteristic> writeCharacteristics = new ConcurrentHashMap<>();
    
    // 连接队列管理
    private Map<String, ConnectionRequest> pendingConnections = new ConcurrentHashMap<>();
    
    // 监听器
    private DeviceConnectionListener connectionListener;
    
    /**
     * 设备连接信息
     */
    public static class DeviceConnectionInfo {
        public String deviceAddress;
        public String deviceName;
        public long connectTime;
        public int reconnectAttempts;
        public boolean isReconnecting;
        public ConnectionState state;
        
        public enum ConnectionState {
            DISCONNECTED,
            CONNECTING,
            CONNECTED,
            DISCOVERING_SERVICES,
            READY,
            RECONNECTING
        }
        
        public DeviceConnectionInfo(String address, String name) {
            this.deviceAddress = address;
            this.deviceName = name;
            this.connectTime = System.currentTimeMillis();
            this.reconnectAttempts = 0;
            this.isReconnecting = false;
            this.state = ConnectionState.DISCONNECTED;
        }
    }
    
    /**
     * 连接请求
     */
    public static class ConnectionRequest {
        public BluetoothDevice device;
        public long requestTime;
        public int priority; // 优先级，数字越大优先级越高
        
        public ConnectionRequest(BluetoothDevice device, int priority) {
            this.device = device;
            this.requestTime = System.currentTimeMillis();
            this.priority = priority;
        }
    }
    
    /**
     * 连接状态监听器
     */
    public interface DeviceConnectionListener {
        void onDeviceConnected(String deviceAddress, String deviceName);
        void onDeviceDisconnected(String deviceAddress, String deviceName);
        void onDeviceReconnecting(String deviceAddress, int attempt);
        void onConnectionFailed(String deviceAddress, String error);
        void onMaxConnectionsReached();
        void onConnectionPoolStatusChanged(int activeConnections, int maxConnections);
    }
    
    public DeviceConnectionManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newCachedThreadPool();
    }
    
    /**
     * 设置连接监听器
     */
    public void setConnectionListener(DeviceConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    /**
     * 连接设备
     */
    public boolean connectDevice(BluetoothDevice device, int priority) {
        String address = device.getAddress();
        
        // 检查是否已经连接
        if (activeConnections.containsKey(address)) {
            Log.d(TAG, "Device already connected: " + address);
            return true;
        }
        
        // 检查连接数量限制
        if (activeConnections.size() >= MAX_CONCURRENT_CONNECTIONS) {
            Log.w(TAG, "Max connections reached: " + MAX_CONCURRENT_CONNECTIONS);
            if (connectionListener != null) {
                connectionListener.onMaxConnectionsReached();
            }
            return false;
        }
        
        // 创建连接请求
        ConnectionRequest request = new ConnectionRequest(device, priority);
        pendingConnections.put(address, request);
        
        // 执行连接
        return executeConnection(device);
    }
    
    /**
     * 执行设备连接
     */
    private boolean executeConnection(BluetoothDevice device) {
        String address = device.getAddress();
        String name = device.getName() != null ? device.getName() : "Unknown Device";
        
        // 创建连接信息
        DeviceConnectionInfo info = new DeviceConnectionInfo(address, name);
        info.state = DeviceConnectionInfo.ConnectionState.CONNECTING;
        connectionInfoMap.put(address, info);
        
        Log.d(TAG, "Connecting to device: " + name + " (" + address + ")");
        
        // 创建GATT连接
        BluetoothGatt gatt = device.connectGatt(context, false, gattCallback);
        if (gatt != null) {
            activeConnections.put(address, gatt);
            notifyConnectionPoolStatusChanged();
            return true;
        } else {
            Log.e(TAG, "Failed to create GATT connection for: " + address);
            connectionInfoMap.remove(address);
            pendingConnections.remove(address);
            if (connectionListener != null) {
                connectionListener.onConnectionFailed(address, "Failed to create GATT connection");
            }
            return false;
        }
    }
    
    /**
     * 断开设备连接
     */
    public void disconnectDevice(String deviceAddress) {
        BluetoothGatt gatt = activeConnections.get(deviceAddress);
        if (gatt != null) {
            Log.d(TAG, "Disconnecting device: " + deviceAddress);
            gatt.disconnect();
        }
    }
    
    /**
     * 断开所有设备连接
     */
    public void disconnectAllDevices() {
        Log.d(TAG, "Disconnecting all devices");
        for (BluetoothGatt gatt : activeConnections.values()) {
            gatt.disconnect();
        }
    }
    
    /**
     * 获取活跃连接数量
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
    
    /**
     * 获取最大连接数量
     */
    public int getMaxConnectionCount() {
        return MAX_CONCURRENT_CONNECTIONS;
    }
    
    /**
     * 检查设备是否已连接
     */
    public boolean isDeviceConnected(String deviceAddress) {
        return activeConnections.containsKey(deviceAddress);
    }
    
    /**
     * 获取设备连接信息
     */
    public DeviceConnectionInfo getDeviceInfo(String deviceAddress) {
        return connectionInfoMap.get(deviceAddress);
    }
    
    /**
     * 获取所有已连接设备的地址
     */
    public String[] getConnectedDeviceAddresses() {
        return activeConnections.keySet().toArray(new String[0]);
    }
    
    /**
     * 获取通知特征
     */
    public BluetoothGattCharacteristic getNotifyCharacteristic(String deviceAddress) {
        return notifyCharacteristics.get(deviceAddress);
    }
    
    /**
     * 获取写入特征
     */
    public BluetoothGattCharacteristic getWriteCharacteristic(String deviceAddress) {
        return writeCharacteristics.get(deviceAddress);
    }
    
    /**
     * 启用通知
     */
    public boolean enableNotification(String deviceAddress) {
        BluetoothGatt gatt = activeConnections.get(deviceAddress);
        BluetoothGattCharacteristic characteristic = notifyCharacteristics.get(deviceAddress);
        
        if (gatt != null && characteristic != null) {
            gatt.setCharacteristicNotification(characteristic, true);
            return true;
        }
        return false;
    }
    
    /**
     * 发送数据
     */
    public boolean sendData(String deviceAddress, byte[] data) {
        BluetoothGatt gatt = activeConnections.get(deviceAddress);
        BluetoothGattCharacteristic characteristic = writeCharacteristics.get(deviceAddress);
        
        if (gatt != null && characteristic != null) {
            characteristic.setValue(data);
            return gatt.writeCharacteristic(characteristic);
        }
        return false;
    }
    
    /**
     * GATT回调
     */
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String deviceAddress = gatt.getDevice().getAddress();
            String deviceName = gatt.getDevice().getName() != null ? 
                gatt.getDevice().getName() : "Unknown Device";
            
            DeviceConnectionInfo info = connectionInfoMap.get(deviceAddress);
            if (info == null) {
                Log.w(TAG, "No connection info found for: " + deviceAddress);
                return;
            }
            
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Device connected: " + deviceName + " (" + deviceAddress + ")");
                info.state = DeviceConnectionInfo.ConnectionState.CONNECTED;
                info.reconnectAttempts = 0;
                info.isReconnecting = false;
                
                // 开始服务发现
                gatt.discoverServices();
                
                if (connectionListener != null) {
                    connectionListener.onDeviceConnected(deviceAddress, deviceName);
                }
                
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Device disconnected: " + deviceName + " (" + deviceAddress + ")");
                info.state = DeviceConnectionInfo.ConnectionState.DISCONNECTED;
                
                // 清理连接
                cleanupConnection(deviceAddress);
                
                if (connectionListener != null) {
                    connectionListener.onDeviceDisconnected(deviceAddress, deviceName);
                }
                
                // 尝试重连
                if (info.reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                    scheduleReconnect(deviceAddress);
                }
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            String deviceAddress = gatt.getDevice().getAddress();
            DeviceConnectionInfo info = connectionInfoMap.get(deviceAddress);
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered for: " + deviceAddress);
                info.state = DeviceConnectionInfo.ConnectionState.DISCOVERING_SERVICES;
                
                // 发现特征
                discoverCharacteristics(gatt);
                
                info.state = DeviceConnectionInfo.ConnectionState.READY;
                Log.d(TAG, "Device ready: " + deviceAddress);
                
            } else {
                Log.e(TAG, "Service discovery failed for: " + deviceAddress + ", status: " + status);
                if (connectionListener != null) {
                    connectionListener.onConnectionFailed(deviceAddress, "Service discovery failed");
                }
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // 数据接收回调，由BleManager处理
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            // 数据写入回调，由BleManager处理
        }
    };
    
    /**
     * 发现特征
     */
    private void discoverCharacteristics(BluetoothGatt gatt) {
        String deviceAddress = gatt.getDevice().getAddress();
        
        for (android.bluetooth.BluetoothGattService service : gatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                int properties = characteristic.getProperties();
                
                // 查找通知特征
                if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    notifyCharacteristics.put(deviceAddress, characteristic);
                    Log.d(TAG, "Found notify characteristic for: " + deviceAddress);
                }
                
                // 查找写入特征
                if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | 
                                  BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0) {
                    writeCharacteristics.put(deviceAddress, characteristic);
                    Log.d(TAG, "Found write characteristic for: " + deviceAddress);
                }
            }
        }
    }
    
    /**
     * 清理连接
     */
    private void cleanupConnection(String deviceAddress) {
        BluetoothGatt gatt = activeConnections.remove(deviceAddress);
        if (gatt != null) {
            gatt.close();
        }
        
        connectionInfoMap.remove(deviceAddress);
        notifyCharacteristics.remove(deviceAddress);
        writeCharacteristics.remove(deviceAddress);
        pendingConnections.remove(deviceAddress);
        
        notifyConnectionPoolStatusChanged();
    }
    
    /**
     * 安排重连
     */
    private void scheduleReconnect(String deviceAddress) {
        DeviceConnectionInfo info = connectionInfoMap.get(deviceAddress);
        if (info == null) return;
        
        info.reconnectAttempts++;
        info.isReconnecting = true;
        info.state = DeviceConnectionInfo.ConnectionState.RECONNECTING;
        
        Log.d(TAG, "Scheduling reconnect for: " + deviceAddress + 
              " (attempt " + info.reconnectAttempts + "/" + MAX_RECONNECT_ATTEMPTS + ")");
        
        if (connectionListener != null) {
            connectionListener.onDeviceReconnecting(deviceAddress, info.reconnectAttempts);
        }
        
        // 延迟重连
        mainHandler.postDelayed(() -> {
            if (info.isReconnecting && info.reconnectAttempts <= MAX_RECONNECT_ATTEMPTS) {
                // 从蓝牙适配器获取设备
                android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
                if (adapter != null) {
                    BluetoothDevice device = adapter.getRemoteDevice(deviceAddress);
                    executeConnection(device);
                }
            }
        }, RECONNECT_DELAY_MS * info.reconnectAttempts);
    }
    
    /**
     * 通知连接池状态变化
     */
    private void notifyConnectionPoolStatusChanged() {
        if (connectionListener != null) {
            connectionListener.onConnectionPoolStatusChanged(
                activeConnections.size(), MAX_CONCURRENT_CONNECTIONS);
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        disconnectAllDevices();
        executorService.shutdown();
        activeConnections.clear();
        connectionInfoMap.clear();
        notifyCharacteristics.clear();
        writeCharacteristics.clear();
        pendingConnections.clear();
    }
} 