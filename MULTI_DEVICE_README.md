# BLE多设备连接功能实现

## 概述

本项目实现了Android BLE Central模式下的多设备连接管理功能，支持同时连接多个BLE Peripheral设备，并提供完整的连接池管理、断线重连、资源限制等功能。

## 核心特性

### 1. 连接池管理
- 支持最多5个并发BLE连接（可配置）
- 使用`ConcurrentHashMap`管理活跃连接
- 自动处理连接数量限制

### 2. 断线重连机制
- 自动检测设备断开
- 智能重连策略（最多3次重试）
- 递增延迟重连（3秒、6秒、9秒）

### 3. 连接状态管理
- 详细的连接状态跟踪
- 设备信息管理
- 连接优先级支持

### 4. 并发数据操作
- 线程安全的数据发送
- 异步特征读写
- 设备间数据隔离

## 架构设计

### 核心类

#### 1. DeviceConnectionManager
```java
// 连接池管理
private Map<String, BluetoothGatt> activeConnections = new ConcurrentHashMap<>();
private Map<String, DeviceConnectionInfo> connectionInfoMap = new ConcurrentHashMap<>();

// 连接状态枚举
public enum ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, 
    DISCOVERING_SERVICES, READY, RECONNECTING
}
```

#### 2. BleManager (增强版)
```java
// 集成连接管理器
private DeviceConnectionManager connectionManager;

// 支持优先级连接
public boolean connectToDevice(BluetoothDevice device, int priority)
```

#### 3. MultiDeviceActivity
- 多设备连接的用户界面
- 设备扫描和连接管理
- 实时状态显示

## 使用方法

### 1. 基本连接操作

```java
// 创建BLE管理器
BleManager bleManager = new BleManager(context);

// 连接单个设备
bleManager.connectToDevice(device);

// 带优先级连接
bleManager.connectToDevice(device, 5); // 高优先级

// 检查连接状态
boolean isConnected = bleManager.isDeviceConnected(deviceAddress);
```

### 2. 批量操作

```java
// 连接多个设备
for (BluetoothDevice device : devices) {
    bleManager.connectToDevice(device);
}

// 向所有设备发送数据
List<String> connectedDevices = bleManager.getConnectedDeviceAddresses();
for (String address : connectedDevices) {
    bleManager.sendData(address, data);
}

// 断开所有设备
bleManager.disconnectAllDevices();
```

### 3. 状态监控

```java
// 获取连接池状态
int activeCount = bleManager.getActiveConnectionCount();
int maxCount = bleManager.getMaxConnectionCount();

// 获取设备详细信息
DeviceConnectionManager.DeviceConnectionInfo info = 
    bleManager.getDeviceInfo(deviceAddress);
```

### 4. 监听器设置

```java
bleManager.addDataListener(new BleDataListener() {
    @Override
    public void onDataReceived(BleDataModel data) {
        // 处理接收到的数据
    }
    
    @Override
    public void onConnectionStateChanged(String deviceAddress, 
                                       boolean isConnected, String deviceName) {
        // 处理连接状态变化
    }
    
    @Override
    public void onError(String error, String deviceAddress) {
        // 处理错误
    }
});
```

## 配置参数

### 连接限制
```java
// DeviceConnectionManager.java
private static final int MAX_CONCURRENT_CONNECTIONS = 5; // 最大并发连接数
private static final int RECONNECT_DELAY_MS = 3000;      // 重连延迟
private static final int MAX_RECONNECT_ATTEMPTS = 3;     // 最大重连次数
```

### 扫描参数
```java
// BleManager.java
private static final long SCAN_PERIOD = 10_000; // 扫描周期10秒
```

## 用户界面

### MultiDeviceActivity功能
1. **设备扫描**: 扫描并显示可用的BLE设备
2. **连接管理**: 单个或批量连接设备
3. **状态显示**: 实时显示连接池状态
4. **数据监控**: 显示接收到的数据
5. **批量操作**: 一键连接/断开所有设备

### 界面布局
- 左侧：发现的设备列表
- 右侧：已连接的设备列表
- 底部：数据接收区域
- 顶部：控制按钮和状态显示

## 性能优化

### 1. 内存管理
- 使用`ConcurrentHashMap`避免并发问题
- 及时清理断开的连接
- 合理管理连接对象生命周期

### 2. 线程安全
- 主线程处理UI更新
- 后台线程处理BLE操作
- 使用Handler确保线程安全

### 3. 资源限制
- 自动限制连接数量
- 智能重连策略
- 连接超时处理

## 错误处理

### 常见错误及解决方案

1. **连接失败**
   - 检查设备是否在范围内
   - 确认设备支持BLE
   - 检查权限设置

2. **连接数量超限**
   - 系统自动拒绝新连接
   - 提示用户断开其他设备
   - 显示连接池状态

3. **断线重连失败**
   - 自动重试机制
   - 用户手动重连选项
   - 错误日志记录

## 测试建议

### 1. 功能测试
- 单设备连接测试
- 多设备并发连接测试
- 断线重连测试
- 数据收发测试

### 2. 性能测试
- 连接稳定性测试
- 内存使用监控
- 电池消耗测试
- 并发数据处理测试

### 3. 兼容性测试
- 不同Android版本测试
- 不同设备厂商测试
- 不同BLE设备测试

## 扩展功能

### 1. 设备分组
```java
// 按设备类型分组管理
Map<String, List<String>> deviceGroups = new HashMap<>();
```

### 2. 数据过滤
```java
// 根据设备类型过滤数据
public void filterDataByDeviceType(String deviceType)
```

### 3. 连接策略
```java
// 自定义连接策略
public interface ConnectionStrategy {
    boolean shouldConnect(BluetoothDevice device);
    int getPriority(BluetoothDevice device);
}
```

## 注意事项

1. **权限要求**: 需要`BLUETOOTH_CONNECT`权限
2. **设备限制**: Android设备通常支持5-7个并发连接
3. **电池优化**: 长时间连接可能影响电池寿命
4. **稳定性**: 建议在稳定的网络环境下使用

## 更新日志

### v1.0.0
- 实现基础多设备连接功能
- 添加连接池管理
- 实现断线重连机制
- 创建用户界面

### 计划功能
- 设备分组管理
- 高级连接策略
- 数据统计分析
- 云端同步功能 