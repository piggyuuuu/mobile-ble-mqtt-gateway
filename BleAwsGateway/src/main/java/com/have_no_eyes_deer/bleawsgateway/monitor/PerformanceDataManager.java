package com.have_no_eyes_deer.bleawsgateway.monitor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 统一的性能数据管理器 - 收集BLE、MQTT、系统资源等所有监控数据
 */
public class PerformanceDataManager {
    
    private static final int MAX_DATA_POINTS = 60; // 保留60个数据点
    private static final double AWS_IOT_MESSAGE_COST = 0.000001; // AWS IoT消息单价(美元)
    
    private Context context;
    private Handler handler;
    private ResourceMonitor resourceMonitor;
    private List<PerformanceDataListener> listeners = new ArrayList<>();
    
    // 数据存储
    private List<ResourceData> resourceHistory = new CopyOnWriteArrayList<>();
    private List<ThroughputData> throughputHistory = new CopyOnWriteArrayList<>();
    private List<LatencyData> latencyHistory = new CopyOnWriteArrayList<>();
    private List<CostData> costHistory = new CopyOnWriteArrayList<>();
    
    // 统计计数器
    private long totalBleMessages = 0;
    private long totalMqttMessages = 0;
    private long totalMqttMessagesSent = 0;
    private long totalMqttMessagesFailed = 0;
    private double totalCostUSD = 0.0;
    
    public interface PerformanceDataListener {
        void onResourceDataUpdate(ResourceData data);
        void onThroughputDataUpdate(ThroughputData data);
        void onLatencyDataUpdate(LatencyData data);
        void onCostDataUpdate(CostData data);
        void onError(String error);
    }
    
    // 数据模型类
    public static class ResourceData {
        public long timestamp;
        public float cpuUsagePercent;
        public long memoryUsageMB;
        public float networkSpeedKBps;
        public long totalMemoryMB;
        
        public ResourceData(long timestamp, float cpuUsage, long memoryUsage, 
                          float networkSpeed, long totalMemory) {
            this.timestamp = timestamp;
            this.cpuUsagePercent = cpuUsage;
            this.memoryUsageMB = memoryUsage;
            this.networkSpeedKBps = networkSpeed;
            this.totalMemoryMB = totalMemory;
        }
    }
    
    public static class ThroughputData {
        public long timestamp;
        public int mqttUploadedPerSecond;  // 每秒成功上传到AWS的MQTT消息数
        
        public ThroughputData(long timestamp, int mqttUploaded) {
            this.timestamp = timestamp;
            this.mqttUploadedPerSecond = mqttUploaded;
        }
    }
    
    public static class LatencyData {
        public long timestamp;
        public long bleReceiveTime;    // BLE消息接收时间戳
        public long awsCompleteTime;   // AWS上传完成时间戳
        public long totalLatencyMs;    // BLE→AWS总延迟(ms)
        
        public LatencyData(long timestamp, long bleTime, long awsTime) {
            this.timestamp = timestamp;
            this.bleReceiveTime = bleTime;
            this.awsCompleteTime = awsTime;
            this.totalLatencyMs = awsTime - bleTime;
        }
    }
    
    public static class CostData {
        public long timestamp;
        public long messagesInLastPeriod;
        public double costInLastPeriodUSD;
        public double totalCostUSD;
        public double dailyProjectedCostUSD;
        
        public CostData(long timestamp, long messages, double periodCost, double totalCost) {
            this.timestamp = timestamp;
            this.messagesInLastPeriod = messages;
            this.costInLastPeriodUSD = periodCost;
            this.totalCostUSD = totalCost;
            // 基于当前速率计算每日预估成本
            this.dailyProjectedCostUSD = periodCost * 86400; // 24小时 * 3600秒
        }
    }
    
    public PerformanceDataManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        initializeResourceMonitor();
    }
    
    private void initializeResourceMonitor() {
        resourceMonitor = new ResourceMonitor(context);
        resourceMonitor.addListener(new ResourceMonitor.ResourceMonitorListener() {
            @Override
            public void onResourceDataUpdate(ResourceMonitor.ResourceData data) {
                ResourceData resourceData = new ResourceData(
                    data.timestamp, data.cpuUsagePercent, data.memoryUsageMB,
                    data.networkSpeedKBps, data.totalMemoryMB
                );
                
                addResourceData(resourceData);
                notifyResourceListeners(resourceData);
            }
            
            @Override
            public void onError(String error) {
                notifyErrorListeners("资源监控错误: " + error);
            }
        });
    }
    
    public void addListener(PerformanceDataListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(PerformanceDataListener listener) {
        listeners.remove(listener);
    }
    
    public void startMonitoring() {
        if (resourceMonitor != null) {
            resourceMonitor.startMonitoring();
        }
        startPeriodicDataCollection();
    }
    
    public void stopMonitoring() {
        if (resourceMonitor != null) {
            resourceMonitor.stopMonitoring();
        }
        handler.removeCallbacksAndMessages(null);
    }
    
    private void startPeriodicDataCollection() {
        handler.postDelayed(this::collectPeriodicData, 1000); // 保持1秒间隔收集数据
    }
    
    private void collectPeriodicData() {
        collectThroughputData();
        collectCostData();
        
        // 调度下次收集
        handler.postDelayed(this::collectPeriodicData, 1000); // 保持1秒间隔收集数据
    }
    
    // ==================== BLE数据接口 ====================
    
    private long lastBleMessageTime = 0;
    private Map<String, Long> bleMessageTimestamps = new ConcurrentHashMap<>(); // 存储每条BLE消息的时间戳
    
    public void recordBleMessage(String deviceAddress, String data) {
        long currentTime = System.currentTimeMillis();
        totalBleMessages++;
        lastBleMessageTime = currentTime;
        
        // 为每条BLE消息生成唯一ID并记录时间戳
        String messageId = deviceAddress + "_" + currentTime + "_" + totalBleMessages;
        bleMessageTimestamps.put(messageId, currentTime);
        
        // 清理过期的时间戳记录(超过30秒)
        bleMessageTimestamps.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 30000);
    }
    
    // ==================== MQTT数据接口 ====================
    
    private int mqttSuccessInLastSecond = 0;
    private long mqttSuccessCountForSecond = 0;
    private long lastMqttCountReset = System.currentTimeMillis();
    private Map<String, Long> mqttSendStartTimes = new ConcurrentHashMap<>();
    
    public void recordMqttSendStart() {
        long currentTime = System.currentTimeMillis();
        String messageId = "mqtt_" + currentTime + "_" + totalMqttMessages;
        mqttSendStartTimes.put(messageId, currentTime);
    }
    
    public void recordMqttSendComplete(boolean success) {
        long currentTime = System.currentTimeMillis();
        
        if (success) {
            totalMqttMessages++;
            totalMqttMessagesSent++;
            
            // 计算每秒成功上传的MQTT消息数
            if (currentTime - lastMqttCountReset >= 1000) {
                mqttSuccessInLastSecond = (int) mqttSuccessCountForSecond;
                mqttSuccessCountForSecond = 0;
                lastMqttCountReset = currentTime;
            }
            mqttSuccessCountForSecond++;
            
            // 计算BLE→AWS总延迟
            Long bleStartTime = findMatchingBleTimestamp();
            if (bleStartTime != null) {
                LatencyData latencyData = new LatencyData(currentTime, bleStartTime, currentTime);
                addLatencyData(latencyData);
                notifyLatencyListeners(latencyData);
            }
            
        } else {
            totalMqttMessagesFailed++;
        }
        
        // 清理过期的MQTT发送记录
        mqttSendStartTimes.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 30000);
    }
    
    // 匹配最近的BLE消息时间戳
    private Long findMatchingBleTimestamp() {
        if (bleMessageTimestamps.isEmpty()) return null;
        
        // 返回最近的BLE消息时间戳
        return bleMessageTimestamps.values().stream()
            .max(Long::compareTo)
            .orElse(null);
    }
    
    // ==================== 数据收集方法 ====================
    
    private void collectThroughputData() {
        long timestamp = System.currentTimeMillis();
        ThroughputData data = new ThroughputData(timestamp, mqttSuccessInLastSecond);
        
        addThroughputData(data);
        notifyThroughputListeners(data);
    }
    
    private void collectCostData() {
        long timestamp = System.currentTimeMillis();
        long messagesInPeriod = mqttSuccessInLastSecond;
        double periodCost = messagesInPeriod * AWS_IOT_MESSAGE_COST;
        totalCostUSD += periodCost;
        
        CostData data = new CostData(timestamp, messagesInPeriod, periodCost, totalCostUSD);
        addCostData(data);
        notifyCostListeners(data);
    }
    
    // ==================== 数据存储方法 ====================
    
    private void addResourceData(ResourceData data) {
        resourceHistory.add(data);
        if (resourceHistory.size() > MAX_DATA_POINTS) {
            resourceHistory.remove(0);
        }
    }
    
    private void addThroughputData(ThroughputData data) {
        throughputHistory.add(data);
        if (throughputHistory.size() > MAX_DATA_POINTS) {
            throughputHistory.remove(0);
        }
    }
    
    private void addLatencyData(LatencyData data) {
        latencyHistory.add(data);
        if (latencyHistory.size() > MAX_DATA_POINTS) {
            latencyHistory.remove(0);
        }
    }
    
    private void addCostData(CostData data) {
        costHistory.add(data);
        if (costHistory.size() > MAX_DATA_POINTS) {
            costHistory.remove(0);
        }
    }
    
    // ==================== 通知监听器方法 ====================
    
    private void notifyResourceListeners(ResourceData data) {
        for (PerformanceDataListener listener : listeners) {
            listener.onResourceDataUpdate(data);
        }
    }
    
    private void notifyThroughputListeners(ThroughputData data) {
        for (PerformanceDataListener listener : listeners) {
            listener.onThroughputDataUpdate(data);
        }
    }
    
    private void notifyLatencyListeners(LatencyData data) {
        for (PerformanceDataListener listener : listeners) {
            listener.onLatencyDataUpdate(data);
        }
    }
    
    private void notifyCostListeners(CostData data) {
        for (PerformanceDataListener listener : listeners) {
            listener.onCostDataUpdate(data);
        }
    }
    
    private void notifyErrorListeners(String error) {
        for (PerformanceDataListener listener : listeners) {
            listener.onError(error);
        }
    }
    
    // ==================== 数据获取方法 ====================
    
    public List<ResourceData> getResourceHistory() {
        return new ArrayList<>(resourceHistory);
    }
    
    public List<ThroughputData> getThroughputHistory() {
        return new ArrayList<>(throughputHistory);
    }
    
    public List<LatencyData> getLatencyHistory() {
        return new ArrayList<>(latencyHistory);
    }
    
    public List<CostData> getCostHistory() {
        return new ArrayList<>(costHistory);
    }
    
    public long getTotalBleMessages() {
        return totalBleMessages;
    }
    
    public long getTotalMqttMessages() {
        return totalMqttMessages;
    }
    
    public double getTotalCostUSD() {
        return totalCostUSD;
    }
    
    public void cleanup() {
        stopMonitoring();
        if (resourceMonitor != null) {
            resourceMonitor.cleanup();
        }
        listeners.clear();
        resourceHistory.clear();
        throughputHistory.clear();
        latencyHistory.clear();
        costHistory.clear();
    }
}