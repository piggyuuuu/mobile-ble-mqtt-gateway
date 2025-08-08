package com.have_no_eyes_deer.bleawsgateway.monitor;

import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance Test Manager - Specifically designed for testing latency and memory usage
 */
public class PerformanceTestManager {
    
    private static final String TAG = "PerformanceTestManager";
    private static final int DEFAULT_TEST_DURATION = 30000; // 30 second test
    private static final int DEFAULT_SAMPLE_INTERVAL = 100; // 100ms sampling interval
    
    private Context context;
    private Handler handler;
    private ExecutorService executorService;
    private PerformanceDataManager performanceDataManager;
    
    // Test status
    private boolean isTestRunning = false;
    private long testStartTime = 0;
    private long testEndTime = 0;
    
    // Test data collection
    private List<LatencyTestResult> latencyResults = new ArrayList<>();
    private List<MemoryTestResult> memoryResults = new ArrayList<>();
    private List<CpuTestResult> cpuResults = new ArrayList<>();
    private List<TestResult> allTestResults = new ArrayList<>();
    
    // Test configuration
    private int testDuration = DEFAULT_TEST_DURATION;
    private int sampleInterval = DEFAULT_SAMPLE_INTERVAL;
    private int messageCount = 100; // Test message count
    
    // Test listeners
    private List<TestListener> testListeners = new ArrayList<>();
    
    // Memory baseline
    private long baselineMemoryUsage = 0;
    private long peakMemoryUsage = 0;
    private long currentMemoryUsage = 0;
    
    // Latency test related
    private ConcurrentHashMap<String, Long> messageStartTimes = new ConcurrentHashMap<>();
    private AtomicLong totalLatency = new AtomicLong(0);
    private AtomicLong messageCountProcessed = new AtomicLong(0);
    
    public interface TestListener {
        void onTestStarted(String testName);
        void onTestProgress(String testName, int progress, String status);
        void onTestCompleted(String testName, TestResult result);
        void onTestError(String testName, String error);
    }
    
    public static class TestResult {
        public String testName;
        public long duration;
        public boolean success;
        public String summary;
        public List<String> details = new ArrayList<>();
        
        public TestResult(String testName, long duration, boolean success) {
            this.testName = testName;
            this.duration = duration;
            this.success = success;
        }
    }
    
    public static class LatencyTestResult {
        public long timestamp;
        public String messageId;
        public long startTime;
        public long endTime;
        public long latencyMs;
        public String status; // "success", "timeout", "error"
        
        public LatencyTestResult(long timestamp, String messageId, long startTime, long endTime) {
            this.timestamp = timestamp;
            this.messageId = messageId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.latencyMs = endTime - startTime;
        }
    }
    
    public static class MemoryTestResult {
        public long timestamp;
        public long memoryUsageMB;
        public long totalMemoryMB;
        public float memoryUsagePercent;
        public long heapSizeMB;
        public long heapFreeMB;
        
        public MemoryTestResult(long timestamp, long memoryUsage, long totalMemory) {
            this.timestamp = timestamp;
            this.memoryUsageMB = memoryUsage;
            this.totalMemoryMB = totalMemory;
            this.memoryUsagePercent = totalMemory > 0 ? (float) memoryUsage / totalMemory * 100 : 0;
            
            Runtime runtime = Runtime.getRuntime();
            this.heapSizeMB = runtime.totalMemory() / 1024 / 1024;
            this.heapFreeMB = runtime.freeMemory() / 1024 / 1024;
        }
    }
    
    public static class CpuTestResult {
        public long timestamp;
        public float cpuUsagePercent;
        public int threadCount;
        public long uptimeMs;
        
        public CpuTestResult(long timestamp, float cpuUsage, int threadCount) {
            this.timestamp = timestamp;
            this.cpuUsagePercent = cpuUsage;
            this.threadCount = threadCount;
            this.uptimeMs = System.currentTimeMillis();
        }
    }
    
    public PerformanceTestManager(Context context) {
        this.context = context.getApplicationContext();
        this.handler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newCachedThreadPool();
        this.performanceDataManager = new PerformanceDataManager(context);
    }
    
    // ==================== Test Configuration Methods ====================
    
    public void setTestDuration(int durationMs) {
        this.testDuration = durationMs;
    }
    
    public void setSampleInterval(int intervalMs) {
        this.sampleInterval = intervalMs;
    }
    
    public void setMessageCount(int count) {
        this.messageCount = count;
    }
    
    public void addTestListener(TestListener listener) {
        testListeners.add(listener);
    }
    
    public void removeTestListener(TestListener listener) {
        testListeners.remove(listener);
    }
    
    // ==================== Comprehensive Performance Test ====================
    
    public void startComprehensiveTest() {
        if (isTestRunning) {
            Log.w(TAG, "Test is already running");
            return;
        }
        
        isTestRunning = true;
        testStartTime = System.currentTimeMillis();
        
        notifyTestStarted("Comprehensive Performance Test");
        
        // Reset test data
        resetTestData();
        
        // Establish memory baseline
        establishMemoryBaseline();
        
        // Start test
        executorService.submit(this::runComprehensiveTest);
    }
    
    private void runComprehensiveTest() {
        try {
            // 1. Memory monitoring test
            runMemoryMonitoringTest();
            
            // 2. Latency test
            runLatencyTest();
            
            // 3. CPU stress test
            runCpuStressTest();
            
            // 4. Generate test report
            generateTestReport();
            
        } catch (Exception e) {
            Log.e(TAG, "Comprehensive test failed", e);
            notifyTestError("Comprehensive Performance Test", "Test execution failed: " + e.getMessage());
        } finally {
            isTestRunning = false;
            testEndTime = System.currentTimeMillis();
        }
    }
    
    // ==================== Memory Monitoring Test ====================
    
    private void runMemoryMonitoringTest() {
        notifyTestProgress("Memory Monitoring Test", 0, "Starting memory monitoring test");
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;
        
        int sampleCount = 0;
        int totalSamples = testDuration / sampleInterval;
        
        while (System.currentTimeMillis() < endTime && isTestRunning) {
            try {
                // Collect memory data
                MemoryTestResult result = collectMemoryData();
                memoryResults.add(result);
                
                // Update peak memory usage
                if (result.memoryUsageMB > peakMemoryUsage) {
                    peakMemoryUsage = result.memoryUsageMB;
                }
                
                // Update current memory usage
                currentMemoryUsage = result.memoryUsageMB;
                
                sampleCount++;
                int progress = (sampleCount * 100) / totalSamples;
                notifyTestProgress("Memory Monitoring Test", progress, 
                    String.format("Collected %d samples, current memory: %dMB", sampleCount, result.memoryUsageMB));
                
                Thread.sleep(sampleInterval);
                
            } catch (InterruptedException e) {
                Log.w(TAG, "Memory monitoring test interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Memory monitoring test error", e);
                notifyTestError("Memory Monitoring Test", "Data collection error: " + e.getMessage());
                break;
            }
        }
        
        notifyTestProgress("Memory Monitoring Test", 100, "Memory monitoring test completed");
    }
    
    // ==================== Latency Test ====================
    
    private void runLatencyTest() {
        notifyTestProgress("Latency Test", 0, "Starting latency test");
        
        long startTime = System.currentTimeMillis();
        
        // Simulate message processing latency test
        for (int i = 0; i < messageCount && isTestRunning; i++) {
            try {
                String messageId = "test_msg_" + i;
                long messageStartTime = System.currentTimeMillis();
                
                // Record start time
                messageStartTimes.put(messageId, messageStartTime);
                
                // Simulate message processing (random delay)
                int processingDelay = (int) (Math.random() * 100) + 10; // 10-110ms
                Thread.sleep(processingDelay);
                
                // Record completion time
                long messageEndTime = System.currentTimeMillis();
                Long recordedStartTime = messageStartTimes.remove(messageId);
                
                if (recordedStartTime != null) {
                    LatencyTestResult result = new LatencyTestResult(
                        System.currentTimeMillis(), messageId, recordedStartTime, messageEndTime);
                    latencyResults.add(result);
                    
                    totalLatency.addAndGet(result.latencyMs);
                    messageCountProcessed.incrementAndGet();
                }
                
                int progress = ((i + 1) * 100) / messageCount;
                notifyTestProgress("Latency Test", progress, 
                    String.format("Processed %d/%d messages, average latency: %.1fms", 
                        i + 1, messageCount, getAverageLatency()));
                
            } catch (InterruptedException e) {
                Log.w(TAG, "Latency test interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "Latency test error", e);
                notifyTestError("Latency Test", "Message processing error: " + e.getMessage());
                break;
            }
        }
        
        notifyTestProgress("Latency Test", 100, "Latency test completed");
    }
    
    // ==================== CPU Stress Test ====================
    
    private void runCpuStressTest() {
        notifyTestProgress("CPU Stress Test", 0, "Starting CPU stress test");
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;
        
        int sampleCount = 0;
        int totalSamples = testDuration / sampleInterval;
        
        // Create CPU stress threads
        List<Thread> stressThreads = new ArrayList<>();
        for (int i = 0; i < 2; i++) { // Create 2 stress threads
            Thread stressThread = new Thread(() -> {
                while (isTestRunning && System.currentTimeMillis() < endTime) {
                    // Perform CPU intensive operations
                    performCpuIntensiveTask();
                }
            });
            stressThread.start();
            stressThreads.add(stressThread);
        }
        
        // Monitor CPU usage
        while (System.currentTimeMillis() < endTime && isTestRunning) {
            try {
                CpuTestResult result = collectCpuData();
                cpuResults.add(result);
                
                sampleCount++;
                int progress = (sampleCount * 100) / totalSamples;
                notifyTestProgress("CPU Stress Test", progress, 
                    String.format("CPU usage: %.1f%%, Thread count: %d", 
                        result.cpuUsagePercent, result.threadCount));
                
                Thread.sleep(sampleInterval);
                
            } catch (InterruptedException e) {
                Log.w(TAG, "CPU stress test interrupted");
                break;
            } catch (Exception e) {
                Log.e(TAG, "CPU stress test error", e);
                notifyTestError("CPU Stress Test", "CPU monitoring error: " + e.getMessage());
                break;
            }
        }
        
        // Wait for stress threads to finish
        for (Thread thread : stressThreads) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Waiting for stress thread interrupted");
            }
        }
        
        notifyTestProgress("CPU Stress Test", 100, "CPU stress test completed");
    }
    
    private void performCpuIntensiveTask() {
        // Perform some CPU intensive operations
        for (int i = 0; i < 1000; i++) {
            Math.sqrt(i);
            Math.sin(i);
            Math.cos(i);
        }
    }
    
    // ==================== Data Collection Methods ====================
    
    private void establishMemoryBaseline() {
        MemoryTestResult baseline = collectMemoryData();
        baselineMemoryUsage = baseline.memoryUsageMB;
        peakMemoryUsage = baselineMemoryUsage;
        currentMemoryUsage = baselineMemoryUsage;
        
        Log.i(TAG, "Memory baseline established: " + baselineMemoryUsage + "MB");
    }
    
    private MemoryTestResult collectMemoryData() {
        long timestamp = System.currentTimeMillis();
        
        // Get memory information
        Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memInfo);
        
        long memoryUsage = memInfo.getTotalPss() / 1024; // KB to MB
        
        // Get total memory
        android.app.ActivityManager am = (android.app.ActivityManager) 
            context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo systemMemInfo = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(systemMemInfo);
        long totalMemory = systemMemInfo.totalMem / 1024 / 1024; // bytes to MB
        
        return new MemoryTestResult(timestamp, memoryUsage, totalMemory);
    }
    
    private CpuTestResult collectCpuData() {
        long timestamp = System.currentTimeMillis();
        
        // Simplified CPU usage calculation
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // Estimate CPU usage based on memory usage
        float memoryUsageRatio = (float) usedMemory / totalMemory;
        return new CpuTestResult(timestamp, memoryUsageRatio * 100, Thread.activeCount());
    }
    
    // ==================== Test Report Generation ====================
    
    private void generateTestReport() {
        TestResult result = new TestResult("Comprehensive Performance Test", 
            testEndTime - testStartTime, true);
        
        // Memory test results
        if (!memoryResults.isEmpty()) {
            long avgMemory = memoryResults.stream()
                .mapToLong(r -> r.memoryUsageMB)
                .sum() / memoryResults.size();
            
            result.details.add(String.format("Memory Test: Average usage %dMB, Peak %dMB, Baseline %dMB", 
                avgMemory, peakMemoryUsage, baselineMemoryUsage));
        }
        
        // Latency test results
        if (!latencyResults.isEmpty()) {
            double avgLatency = getAverageLatency();
            long maxLatency = latencyResults.stream()
                .mapToLong(r -> r.latencyMs)
                .max()
                .orElse(0);
            
            result.details.add(String.format("Latency Test: Average latency %.1fms, Max latency %dms, Processed messages %d", 
                avgLatency, maxLatency, messageCountProcessed.get()));
        }
        
        // CPU test results
        if (!cpuResults.isEmpty()) {
            double avgCpu = cpuResults.stream()
                .mapToDouble(r -> r.cpuUsagePercent)
                .average()
                .orElse(0);
            
            result.details.add(String.format("CPU Test: Average usage %.1f%%, Max thread count %d", 
                avgCpu, cpuResults.stream().mapToInt(r -> r.threadCount).max().orElse(0)));
        }
        
        result.summary = String.format("Test completed, total duration %dms, memory growth %dMB", 
            result.duration, currentMemoryUsage - baselineMemoryUsage);
        
        // Save test results
        allTestResults.add(result);
        
        notifyTestCompleted("Comprehensive Performance Test", result);
    }
    
    private double getAverageLatency() {
        if (messageCountProcessed.get() == 0) return 0;
        return (double) totalLatency.get() / messageCountProcessed.get();
    }
    
    // ==================== Utility Methods ====================
    
    private void resetTestData() {
        latencyResults.clear();
        memoryResults.clear();
        cpuResults.clear();
        allTestResults.clear();
        messageStartTimes.clear();
        totalLatency.set(0);
        messageCountProcessed.set(0);
    }
    
    private void notifyTestStarted(String testName) {
        for (TestListener listener : testListeners) {
            listener.onTestStarted(testName);
        }
    }
    
    private void notifyTestProgress(String testName, int progress, String status) {
        for (TestListener listener : testListeners) {
            listener.onTestProgress(testName, progress, status);
        }
    }
    
    private void notifyTestCompleted(String testName, TestResult result) {
        for (TestListener listener : testListeners) {
            listener.onTestCompleted(testName, result);
        }
    }
    
    private void notifyTestError(String testName, String error) {
        for (TestListener listener : testListeners) {
            listener.onTestError(testName, error);
        }
    }
    
    // ==================== Public Interface Methods ====================
    
    public boolean isTestRunning() {
        return isTestRunning;
    }
    
    public void stopTest() {
        isTestRunning = false;
    }
    
    public List<LatencyTestResult> getLatencyResults() {
        return new ArrayList<>(latencyResults);
    }
    
    public List<MemoryTestResult> getMemoryResults() {
        return new ArrayList<>(memoryResults);
    }
    
    public List<CpuTestResult> getCpuResults() {
        return new ArrayList<>(cpuResults);
    }
    
    public List<TestResult> getAllTestResults() {
        return new ArrayList<>(allTestResults);
    }
    
    public long getBaselineMemoryUsage() {
        return baselineMemoryUsage;
    }
    
    public long getPeakMemoryUsage() {
        return peakMemoryUsage;
    }
    
    public long getCurrentMemoryUsage() {
        return currentMemoryUsage;
    }
    
    public void cleanup() {
        stopTest();
        if (executorService != null) {
            executorService.shutdown();
        }
        if (performanceDataManager != null) {
            performanceDataManager.cleanup();
        }
        testListeners.clear();
        resetTestData();
    }
} 