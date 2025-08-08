package com.have_no_eyes_deer.bleawsgateway.monitor;

import android.app.ActivityManager;
import android.content.Context;
import android.net.TrafficStats;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * System Resource Monitor - Collects CPU, memory, and network usage information
 */
public class ResourceMonitor {
    
    private static final int DEFAULT_UPDATE_INTERVAL = 1000; // Update every 1 second
    private static final int MAX_DATA_POINTS = 60; // Keep 60 data points (1 minute history)
    
    private Context context;
    private Handler handler;
    private boolean isMonitoring = false;
    private int updateInterval = DEFAULT_UPDATE_INTERVAL;
    
    // Data storage
    private List<ResourceData> resourceDataHistory = new CopyOnWriteArrayList<>();
    private List<ResourceMonitorListener> listeners = new ArrayList<>();
    
    // Network traffic calculation
    private long lastTxBytes = 0;
    private long lastRxBytes = 0;
    private long lastTimestamp = 0;
    
    // CPU calculation related
    private long lastCpuTotal = 0;
    private long lastCpuIdle = 0;
    
    public interface ResourceMonitorListener {
        void onResourceDataUpdate(ResourceData data);
        void onError(String error);
    }
    
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
        
        @Override
        public String toString() {
            return String.format("CPU: %.1f%%, Memory: %dMB, Network: %.1fKB/s", 
                cpuUsagePercent, memoryUsageMB, networkSpeedKBps);
        }
    }
    
    public ResourceMonitor(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        initializeNetworkBaseline();
    }
    
    private void initializeNetworkBaseline() {
        lastTxBytes = TrafficStats.getTotalTxBytes();
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTimestamp = System.currentTimeMillis();
    }
    
    public void addListener(ResourceMonitorListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(ResourceMonitorListener listener) {
        listeners.remove(listener);
    }
    
    public void startMonitoring() {
        if (isMonitoring) return;
        
        isMonitoring = true;
        initializeNetworkBaseline();
        scheduleNextUpdate();
    }
    
    public void stopMonitoring() {
        isMonitoring = false;
        handler.removeCallbacksAndMessages(null);
    }
    
    public void setUpdateInterval(int intervalMs) {
        this.updateInterval = intervalMs;
    }
    
    public List<ResourceData> getHistoryData() {
        return new ArrayList<>(resourceDataHistory);
    }
    
    public ResourceData getCurrentData() {
        if (resourceDataHistory.isEmpty()) {
            return null;
        }
        return resourceDataHistory.get(resourceDataHistory.size() - 1);
    }
    
    private void scheduleNextUpdate() {
        if (!isMonitoring) return;
        
        handler.postDelayed(this::collectResourceData, updateInterval);
    }
    
    private void collectResourceData() {
        try {
            long timestamp = System.currentTimeMillis();
            float cpuUsage = getCpuUsage();
            long memoryUsage = getMemoryUsage();
            long totalMemory = getTotalMemory();
            float networkSpeed = getNetworkSpeed();
            
            ResourceData data = new ResourceData(timestamp, cpuUsage, memoryUsage, networkSpeed, totalMemory);
            
            // Add to history data
            resourceDataHistory.add(data);
            
            // Limit history data size
            if (resourceDataHistory.size() > MAX_DATA_POINTS) {
                resourceDataHistory.remove(0);
            }
            
            // Notify listeners
            for (ResourceMonitorListener listener : listeners) {
                listener.onResourceDataUpdate(data);
            }
            
        } catch (Exception e) {
            for (ResourceMonitorListener listener : listeners) {
                listener.onError("Resource monitoring error: " + e.getMessage());
            }
        }
        
        scheduleNextUpdate();
    }
    
    private float getCpuUsage() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String line = reader.readLine();
            reader.close();
            
            if (line != null && line.startsWith("cpu ")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    long user = Long.parseLong(parts[1]);
                    long nice = Long.parseLong(parts[2]);
                    long system = Long.parseLong(parts[3]);
                    long idle = Long.parseLong(parts[4]);
                    
                    long currentTotal = user + nice + system + idle;
                    long currentIdle = idle;
                    
                    if (lastCpuTotal != 0) {
                        long totalDiff = currentTotal - lastCpuTotal;
                        long idleDiff = currentIdle - lastCpuIdle;
                        
                        if (totalDiff > 0) {
                            float usage = 100.0f * (totalDiff - idleDiff) / totalDiff;
                            lastCpuTotal = currentTotal;
                            lastCpuIdle = currentIdle;
                            return Math.max(0, Math.min(100, usage));
                        }
                    }
                    
                    lastCpuTotal = currentTotal;
                    lastCpuIdle = currentIdle;
                }
            }
        } catch (IOException e) {
            // If /proc/stat cannot be read, use a simplified method
            return getSimplifiedCpuUsage();
        }
        
        return 0.0f;
    }
    
    private float getSimplifiedCpuUsage() {
        // Use Debug API to get a rough CPU usage rate
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        
        // This is a simplified estimate, actual CPU usage rate requires more complex calculation
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        
        if (processes != null && !processes.isEmpty()) {
            // Simple estimate based on number of running processes
            return Math.min(processes.size() * 2.0f, 30.0f);
        }
        
        return 0.0f;
    }
    
    private long getMemoryUsage() {
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        
        // Return current application memory usage (MB)
        return memInfo.getTotalPss() / 1024; // PSS in KB -> MB
    }
    
    private long getTotalMemory() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);
        
        return memInfo.totalMem / 1024 / 1024; // bytes -> MB
    }
    
    private float getNetworkSpeed() {
        long currentTxBytes = TrafficStats.getTotalTxBytes();
        long currentRxBytes = TrafficStats.getTotalRxBytes();
        long currentTimestamp = System.currentTimeMillis();
        
        if (lastTxBytes == -1 || lastRxBytes == -1) {
            // Network statistics unavailable
            initializeNetworkBaseline();
            return 0.0f;
        }
        
        long timeDiff = currentTimestamp - lastTimestamp;
        if (timeDiff <= 0) {
            return 0.0f;
        }
        
        long txDiff = currentTxBytes - lastTxBytes;
        long rxDiff = currentRxBytes - lastRxBytes;
        long totalBytesDiff = txDiff + rxDiff;
        
        // Calculate bytes per second, then convert to KB/s
        float speedBytesPerSec = (float) totalBytesDiff * 1000 / timeDiff;
        float speedKBps = speedBytesPerSec / 1024;
        
        // Update baseline values
        lastTxBytes = currentTxBytes;
        lastRxBytes = currentRxBytes;
        lastTimestamp = currentTimestamp;
        
        return Math.max(0, speedKBps);
    }
    
    public void cleanup() {
        stopMonitoring();
        listeners.clear();
        resourceDataHistory.clear();
    }
}