package com.have_no_eyes_deer.bleawsgateway.monitor;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Performance Test Example Class - Demonstrates how to use PerformanceTestManager for various tests
 */
public class PerformanceTestExample {
    
    private static final String TAG = "PerformanceTestExample";
    private PerformanceTestManager testManager;
    private Context context;
    
    public PerformanceTestExample(Context context) {
        this.context = context;
        this.testManager = new PerformanceTestManager(context);
    }
    
    /**
     * Run quick performance test (10 seconds)
     */
    public void runQuickTest() {
        Log.i(TAG, "Starting quick performance test");
        
        // Configure test parameters
        testManager.setTestDuration(10000); // 10 seconds
        testManager.setSampleInterval(200); // 200ms sampling
        testManager.setMessageCount(20); // 20 messages
        
        // Set test listener
        testManager.addTestListener(new PerformanceTestManager.TestListener() {
            @Override
            public void onTestStarted(String testName) {
                Log.i(TAG, "Test started: " + testName);
            }
            
            @Override
            public void onTestProgress(String testName, int progress, String status) {
                Log.d(TAG, String.format("Test progress: %s - %d%% - %s", testName, progress, status));
            }
            
            @Override
            public void onTestCompleted(String testName, PerformanceTestManager.TestResult result) {
                Log.i(TAG, "Test completed: " + testName);
                Log.i(TAG, "Test result: " + result.summary);
                for (String detail : result.details) {
                    Log.i(TAG, "Detail: " + detail);
                }
            }
            
            @Override
            public void onTestError(String testName, String error) {
                Log.e(TAG, "Test error: " + testName + " - " + error);
            }
        });
        
        // Start test
        testManager.startComprehensiveTest();
    }
    
    /**
     * Run standard performance test (30 seconds)
     */
    public void runStandardTest() {
        Log.i(TAG, "Starting standard performance test");
        
        // Configure test parameters
        testManager.setTestDuration(30000); // 30 seconds
        testManager.setSampleInterval(100); // 100ms sampling
        testManager.setMessageCount(50); // 50 messages
        
        // Start test
        testManager.startComprehensiveTest();
    }
    
    /**
     * Run long performance test (60 seconds)
     */
    public void runLongTest() {
        Log.i(TAG, "Starting long performance test");
        
        // Configure test parameters
        testManager.setTestDuration(60000); // 60 seconds
        testManager.setSampleInterval(100); // 100ms sampling
        testManager.setMessageCount(100); // 100 messages
        
        // Start test
        testManager.startComprehensiveTest();
    }
    
    /**
     * Get test result summary
     */
    public String getTestSummary() {
        StringBuilder summary = new StringBuilder();
        
        // Memory test results
        List<PerformanceTestManager.MemoryTestResult> memoryResults = testManager.getMemoryResults();
        if (!memoryResults.isEmpty()) {
            long avgMemory = memoryResults.stream()
                .mapToLong(r -> r.memoryUsageMB)
                .sum() / memoryResults.size();
            long maxMemory = memoryResults.stream()
                .mapToLong(r -> r.memoryUsageMB)
                .max()
                .orElse(0);
            
            summary.append("Memory test results:\n");
            summary.append("  Average memory usage: ").append(avgMemory).append("MB\n");
            summary.append("  Maximum memory usage: ").append(maxMemory).append("MB\n");
            summary.append("  Memory baseline: ").append(testManager.getBaselineMemoryUsage()).append("MB\n");
            summary.append("  Peak memory: ").append(testManager.getPeakMemoryUsage()).append("MB\n\n");
        }
        
        // Latency test results
        List<PerformanceTestManager.LatencyTestResult> latencyResults = testManager.getLatencyResults();
        if (!latencyResults.isEmpty()) {
            double avgLatency = latencyResults.stream()
                .mapToLong(r -> r.latencyMs)
                .average()
                .orElse(0);
            long maxLatency = latencyResults.stream()
                .mapToLong(r -> r.latencyMs)
                .max()
                .orElse(0);
            
            summary.append("Latency test results:\n");
            summary.append("  Average latency: ").append(String.format("%.1f", avgLatency)).append("ms\n");
            summary.append("  Maximum latency: ").append(maxLatency).append("ms\n");
            summary.append("  Message count: ").append(latencyResults.size()).append("\n\n");
        }
        
        // CPU test results
        List<PerformanceTestManager.CpuTestResult> cpuResults = testManager.getCpuResults();
        if (!cpuResults.isEmpty()) {
            double avgCpu = cpuResults.stream()
                .mapToDouble(r -> r.cpuUsagePercent)
                .average()
                .orElse(0);
            double maxCpu = cpuResults.stream()
                .mapToDouble(r -> r.cpuUsagePercent)
                .max()
                .orElse(0);
            
            summary.append("CPU test results:\n");
            summary.append("  Average CPU usage: ").append(String.format("%.1f", avgCpu)).append("%\n");
            summary.append("  Maximum CPU usage: ").append(String.format("%.1f", maxCpu)).append("%\n");
            summary.append("  Sample count: ").append(cpuResults.size()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Check if test is running
     */
    public boolean isTestRunning() {
        return testManager.isTestRunning();
    }
    
    /**
     * Stop test
     */
    public void stopTest() {
        testManager.stopTest();
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        testManager.cleanup();
    }
    
    /**
     * Run custom test
     * @param durationMs Test duration (milliseconds)
     * @param sampleIntervalMs Sampling interval (milliseconds)
     * @param messageCount Message count
     */
    public void runCustomTest(int durationMs, int sampleIntervalMs, int messageCount) {
        Log.i(TAG, String.format("Starting custom test: Duration=%dms, Sampling Interval=%dms, Message Count=%d", 
            durationMs, sampleIntervalMs, messageCount));
        
        // Configure test parameters
        testManager.setTestDuration(durationMs);
        testManager.setSampleInterval(sampleIntervalMs);
        testManager.setMessageCount(messageCount);
        
        // Start test
        testManager.startComprehensiveTest();
    }
} 