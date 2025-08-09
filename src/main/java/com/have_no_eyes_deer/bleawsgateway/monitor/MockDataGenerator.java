package com.have_no_eyes_deer.bleawsgateway.monitor;

import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/**
 * Mock BLE/MQTT Data Generator
 * Used for testing and demonstrating performance monitoring functionality
 */
public class MockDataGenerator {
    
    // Control switch - Set to true to use mock data, false to use real data
    public static final boolean USE_MOCK_DATA = false;
    
    private static final int MIN_BLE_INTERVAL = 500;   // Minimum BLE message interval (ms)
    private static final int MAX_BLE_INTERVAL = 2000;  // Maximum BLE message interval (ms)
    private static final int MIN_MQTT_LATENCY = 50;    // Minimum MQTT latency (ms)
    private static final int MAX_MQTT_LATENCY = 500;   // Maximum MQTT latency (ms)
    
    private PerformanceDataManager performanceManager;
    private Handler handler;
    private Random random;
    private boolean isRunning = false;
    
    // Mock data statistics
    private int mockDeviceCount = 0;
    private long lastMockTime = 0;
    
    public MockDataGenerator(PerformanceDataManager performanceManager) {
        this.performanceManager = performanceManager;
        this.handler = new Handler(Looper.getMainLooper());
        this.random = new Random();
    }
    
    /**
     * Start generating mock data
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
     * Stop generating mock data
     */
    public void stopMockDataGeneration() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }
    
    /**
     * Schedule next BLE message
     */
    private void scheduleNextBleMessage() {
        if (!isRunning) return;
        
        // Generate BLE messages at random intervals
        int interval = MIN_BLE_INTERVAL + random.nextInt(MAX_BLE_INTERVAL - MIN_BLE_INTERVAL);
        handler.postDelayed(this::generateMockBleMessage, interval);
    }
    
    /**
     * Generate mock BLE message
     */
    private void generateMockBleMessage() {
        if (!isRunning) {
            android.util.Log.d("MockDataGenerator", "Not running, stopping generation");
            return;
        }
        
        mockDeviceCount++;
        
        // Mock device address
        String mockDeviceAddress = "MOCK:" + String.format("%02X:%02X:%02X", 
            random.nextInt(256), random.nextInt(256), random.nextInt(256));
        
        // Generate mock sensor data
        String mockData = generateMockSensorData();
        
        // Record BLE message
        performanceManager.recordBleMessage(mockDeviceAddress, mockData);
        
        // Print status every 10 messages
        if (mockDeviceCount % 10 == 0) {
            android.util.Log.d("MockDataGenerator", "Generated " + mockDeviceCount + " BLE messages");
        }
        
        // 80% probability also send MQTT message
        if (random.nextFloat() < 0.8f) {
            scheduleMockMqttMessage();
        }
        
        // Schedule next message
        scheduleNextBleMessage();
    }
    
    /**
     * Generate mock sensor data
     */
    private String generateMockSensorData() {
        // Simulate temperature and humidity sensor data
        float temperature = 20.0f + random.nextFloat() * 15.0f; // 20-35°C
        float humidity = 40.0f + random.nextFloat() * 40.0f;    // 40-80%
        int battery = 20 + random.nextInt(80);                  // 20-100%
        
        return String.format("T:%.1f,H:%.1f,B:%d", temperature, humidity, battery);
    }
    
    /**
     * Schedule mock MQTT message sending
     */
    private void scheduleMockMqttMessage() {
        // Simulate MQTT send latency
        int mqttLatency = MIN_MQTT_LATENCY + random.nextInt(MAX_MQTT_LATENCY - MIN_MQTT_LATENCY);
        
        handler.postDelayed(() -> {
            // Simulate MQTT send process
            performanceManager.recordMqttSendStart();
            
            // Simulate network send time
            handler.postDelayed(() -> {
                // 95% success rate
                boolean success = random.nextFloat() < 0.95f;
                performanceManager.recordMqttSendComplete(success);
            }, mqttLatency);
            
        }, 10 + random.nextInt(50)); // BLE processing latency 10-60ms
    }
    
    /**
     * Generate periodic load test
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
     * Simulate burst traffic
     */
    public void generateBurstTraffic(int burstCount) {
        if (!USE_MOCK_DATA) return;
        
        for (int i = 0; i < burstCount; i++) {
            handler.postDelayed(() -> {
                generateMockBleMessage();
            }, i * 50); // Burst messages with 50ms interval
        }
    }
    
    /**
     * Simulate increased network latency
     */
    public void simulateNetworkLatency(boolean highLatency) {
        if (!USE_MOCK_DATA) return;
        
        if (highLatency) {
            // Increase latency range to 200-2000ms
            scheduleHighLatencyPeriod();
        }
        // Otherwise use default latency range
    }
    
    private void scheduleHighLatencyPeriod() {
        // Simulate high latency period (30 seconds)
        handler.postDelayed(() -> {
            // High latency period ends, return to normal
        }, 30000);
    }
    
    /**
     * Get mock data statistics
     */
    public String getMockDataStats() {
        if (!USE_MOCK_DATA) {
            return "Using real data (mock disabled)";
        }
        
        return String.format("Mock data enabled - Devices: %d, Running: %b", 
            mockDeviceCount, isRunning);
    }
    
    /**
     * Check if mock data is being used
     */
    public static boolean isUsingMockData() {
        return USE_MOCK_DATA;
    }
}