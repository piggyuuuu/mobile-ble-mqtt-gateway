package com.have_no_eyes_deer.bleawsgateway.monitor;

import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/**
 * 模拟BLE/MQTT数据生成器
 * 用于测试和演示性能监控功能
 */
public class MockDataGenerator {
    
    // 控制开关 - 设置为true使用模拟数据，false使用真实数据
    public static final boolean USE_MOCK_DATA = false;
    
    private static final int MIN_BLE_INTERVAL = 500;   // 最小BLE消息间隔(ms)
    private static final int MAX_BLE_INTERVAL = 2000;  // 最大BLE消息间隔(ms)
    private static final int MIN_MQTT_LATENCY = 50;    // 最小MQTT延迟(ms)
    private static final int MAX_MQTT_LATENCY = 500;   // 最大MQTT延迟(ms)
    
    private PerformanceDataManager performanceManager;
    private Handler handler;
    private Random random;
    private boolean isRunning = false;
    
    // 模拟数据统计
    private int mockDeviceCount = 0;
    private long lastMockTime = 0;
    
    public MockDataGenerator(PerformanceDataManager performanceManager) {
        this.performanceManager = performanceManager;
        this.handler = new Handler(Looper.getMainLooper());
        this.random = new Random();
    }
    
    /**
     * 开始生成模拟数据
     */
    public void startMockDataGeneration() {
        if (!USE_MOCK_DATA) {
            android.util.Log.d("MockDataGenerator", "Mock data disabled");
            return;
        }
        
        if (isRunning) {
            android.util.Log.d("MockDataGenerator", "Already running");
            return;
        }
        
        isRunning = true;
        android.util.Log.d("MockDataGenerator", "Starting mock data generation");
        scheduleNextBleMessage();
    }
    
    /**
     * 停止生成模拟数据
     */
    public void stopMockDataGeneration() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }
    
    /**
     * 调度下一条BLE消息
     */
    private void scheduleNextBleMessage() {
        if (!isRunning) return;
        
        // 随机间隔生成BLE消息
        int interval = MIN_BLE_INTERVAL + random.nextInt(MAX_BLE_INTERVAL - MIN_BLE_INTERVAL);
        handler.postDelayed(this::generateMockBleMessage, interval);
    }
    
    /**
     * 生成模拟BLE消息
     */
    private void generateMockBleMessage() {
        if (!isRunning) {
            android.util.Log.d("MockDataGenerator", "Not running, stopping generation");
            return;
        }
        
        mockDeviceCount++;
        
        // 模拟设备地址
        String mockDeviceAddress = "MOCK:" + String.format("%02X:%02X:%02X", 
            random.nextInt(256), random.nextInt(256), random.nextInt(256));
        
        // 生成模拟传感器数据
        String mockData = generateMockSensorData();
        
        // 记录BLE消息
        performanceManager.recordBleMessage(mockDeviceAddress, mockData);
        
        // 每10条消息打印一次状态
        if (mockDeviceCount % 10 == 0) {
            android.util.Log.d("MockDataGenerator", "Generated " + mockDeviceCount + " BLE messages");
        }
        
        // 80%的概率也发送MQTT消息
        if (random.nextFloat() < 0.8f) {
            scheduleMockMqttMessage();
        }
        
        // 调度下一条消息
        scheduleNextBleMessage();
    }
    
    /**
     * 生成模拟传感器数据
     */
    private String generateMockSensorData() {
        // 模拟温湿度传感器数据
        float temperature = 20.0f + random.nextFloat() * 15.0f; // 20-35°C
        float humidity = 40.0f + random.nextFloat() * 40.0f;    // 40-80%
        int battery = 20 + random.nextInt(80);                  // 20-100%
        
        return String.format("T:%.1f,H:%.1f,B:%d", temperature, humidity, battery);
    }
    
    /**
     * 调度模拟MQTT消息发送
     */
    private void scheduleMockMqttMessage() {
        // 模拟MQTT发送延迟
        int mqttLatency = MIN_MQTT_LATENCY + random.nextInt(MAX_MQTT_LATENCY - MIN_MQTT_LATENCY);
        
        handler.postDelayed(() -> {
            // 模拟MQTT发送过程
            performanceManager.recordMqttSendStart();
            
            // 模拟网络发送时间
            handler.postDelayed(() -> {
                // 95%成功率
                boolean success = random.nextFloat() < 0.95f;
                performanceManager.recordMqttSendComplete(success);
            }, mqttLatency);
            
        }, 10 + random.nextInt(50)); // BLE处理延迟10-60ms
    }
    
    /**
     * 生成周期性负载测试
     */
    public void generateLoadTest(int durationSeconds, int messagesPerSecond) {
        if (!USE_MOCK_DATA) return;
        
        int totalMessages = durationSeconds * messagesPerSecond;
        int interval = 1000 / messagesPerSecond;
        
        for (int i = 0; i < totalMessages; i++) {
            handler.postDelayed(() -> {
                generateMockBleMessage();
            }, i * interval);
        }
    }
    
    /**
     * 模拟突发流量
     */
    public void generateBurstTraffic(int burstCount) {
        if (!USE_MOCK_DATA) return;
        
        for (int i = 0; i < burstCount; i++) {
            handler.postDelayed(() -> {
                generateMockBleMessage();
            }, i * 50); // 50ms间隔的突发消息
        }
    }
    
    /**
     * 模拟网络延迟增加
     */
    public void simulateNetworkLatency(boolean highLatency) {
        if (!USE_MOCK_DATA) return;
        
        if (highLatency) {
            // 增加延迟范围到200-2000ms
            scheduleHighLatencyPeriod();
        }
        // 否则使用默认延迟范围
    }
    
    private void scheduleHighLatencyPeriod() {
        // 模拟高延迟期间（30秒）
        handler.postDelayed(() -> {
            // 高延迟期间结束，恢复正常
        }, 30000);
    }
    
    /**
     * 获取模拟数据统计
     */
    public String getMockDataStats() {
        if (!USE_MOCK_DATA) {
            return "Using real data (mock disabled)";
        }
        
        return String.format("Mock data enabled - Devices: %d, Running: %b", 
            mockDeviceCount, isRunning);
    }
    
    /**
     * 检查是否使用模拟数据
     */
    public static boolean isUsingMockData() {
        return USE_MOCK_DATA;
    }
}