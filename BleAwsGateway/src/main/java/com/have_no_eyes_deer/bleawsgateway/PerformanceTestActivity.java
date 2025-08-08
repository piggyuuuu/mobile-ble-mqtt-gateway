package com.have_no_eyes_deer.bleawsgateway;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.have_no_eyes_deer.bleawsgateway.monitor.PerformanceTestManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Performance Test Activity - Provides UI interface to run and view performance test results
 */
public class PerformanceTestActivity extends AppCompatActivity {
    
    private static final String TAG = "PerformanceTestActivity";
    
    private PerformanceTestManager testManager;
    private Handler mainHandler;
    
    // UI components
    private Button btnStartTest;
    private Button btnStopTest;
    private Button btnSaveResults;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvResults;
    private ScrollView scrollView;
    
    // Test status
    private boolean isTestRunning = false;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private SimpleDateFormat fileDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_performance_test);
        
        initializeViews();
        initializeTestManager();
    }
    
    private void initializeViews() {
        btnStartTest = findViewById(R.id.btn_start_test);
        btnStopTest = findViewById(R.id.btn_stop_test);
        btnSaveResults = findViewById(R.id.btn_save_results);
        progressBar = findViewById(R.id.progress_bar);
        tvStatus = findViewById(R.id.tv_status);
        tvResults = findViewById(R.id.tv_results);
        scrollView = findViewById(R.id.scroll_view);
        
        btnStartTest.setOnClickListener(v -> startPerformanceTest());
        btnStopTest.setOnClickListener(v -> stopPerformanceTest());
        btnSaveResults.setOnClickListener(v -> saveResultsToTable());
        
        // Initial state
        btnStopTest.setEnabled(false);
        btnSaveResults.setEnabled(false);
        progressBar.setProgress(0);
        updateStatus(getString(R.string.ready_click_start));
    }
    
    private void initializeTestManager() {
        testManager = new PerformanceTestManager(this);
        mainHandler = new Handler(Looper.getMainLooper());
        
        // Set test listener
        testManager.addTestListener(new PerformanceTestManager.TestListener() {
            @Override
            public void onTestStarted(String testName) {
                mainHandler.post(() -> {
                    Log.i(TAG, "Test started: " + testName);
                    updateStatus(getString(R.string.test_started, testName));
                    appendResult("=== " + testName + " Started ===\n");
                });
            }
            
            @Override
            public void onTestProgress(String testName, int progress, String status) {
                mainHandler.post(() -> {
                    progressBar.setProgress(progress);
                    updateStatus(status);
                    Log.d(TAG, String.format("Test progress: %s - %d%% - %s", testName, progress, status));
                });
            }
            
            @Override
            public void onTestCompleted(String testName, PerformanceTestManager.TestResult result) {
                mainHandler.post(() -> {
                    Log.i(TAG, "Test completed: " + testName);
                    updateStatus(getString(R.string.test_completed, testName));
                    displayTestResults(result);
                    // Enable save button
                    btnSaveResults.setEnabled(true);
                });
            }
            
            @Override
            public void onTestError(String testName, String error) {
                mainHandler.post(() -> {
                    Log.e(TAG, "Test error: " + testName + " - " + error);
                    updateStatus(getString(R.string.test_error, error));
                    appendResult("❌ Error: " + error + "\n");
                    Toast.makeText(PerformanceTestActivity.this, 
                        getString(R.string.test_error, error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void startPerformanceTest() {
        if (isTestRunning) {
            Toast.makeText(this, getString(R.string.test_already_running), Toast.LENGTH_SHORT).show();
            return;
        }
        
        isTestRunning = true;
        btnStartTest.setEnabled(false);
        btnStopTest.setEnabled(true);
        btnSaveResults.setEnabled(false);
        progressBar.setProgress(0);
        
        // Clear previous results
        clearResults();
        appendResult("=== Performance Test Started ===\n");
        appendResult("Time: " + timeFormat.format(new Date()) + "\n\n");
        
        // Configure test parameters
        testManager.setTestDuration(30000); // 30 seconds
        testManager.setSampleInterval(100); // 100ms sampling
        testManager.setMessageCount(50); // 50 messages
        
        // Start test
        testManager.startComprehensiveTest();
    }
    
    private void stopPerformanceTest() {
        if (!isTestRunning) return;
        
        testManager.stopTest();
        isTestRunning = false;
        btnStartTest.setEnabled(true);
        btnStopTest.setEnabled(false);
        btnSaveResults.setEnabled(true);
        
        updateStatus(getString(R.string.test_stopped));
        appendResult("\n=== Test manually stopped ===\n");
    }
    
    private void displayTestResults(PerformanceTestManager.TestResult result) {
        appendResult("\n=== Test Results ===\n");
        appendResult("Test Name: " + result.testName + "\n");
        appendResult("Test Duration: " + result.duration + "ms\n");
        appendResult("Test Status: " + (result.success ? "Success" : "Failed") + "\n");
        appendResult("Test Summary: " + result.summary + "\n\n");
        
        // Display detailed information
        appendResult("Detailed Information:\n");
        for (String detail : result.details) {
            appendResult("• " + detail + "\n");
        }
        
        // Display detailed data
        displayDetailedResults();
        
        appendResult("\n=== Test Ended ===\n");
        appendResult("End Time: " + timeFormat.format(new Date()) + "\n");
        
        // Scroll to bottom
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
    
    private void displayDetailedResults() {
        // Memory test results
        List<PerformanceTestManager.MemoryTestResult> memoryResults = testManager.getMemoryResults();
        if (!memoryResults.isEmpty()) {
            appendResult("\nMemory Test Detailed Data:\n");
            appendResult("Sample Count: " + memoryResults.size() + "\n");
            
            long avgMemory = memoryResults.stream()
                .mapToLong(r -> r.memoryUsageMB)
                .sum() / memoryResults.size();
            long maxMemory = memoryResults.stream()
                .mapToLong(r -> r.memoryUsageMB)
                .max()
                .orElse(0);
            long minMemory = memoryResults.stream()
                .mapToLong(r -> r.memoryUsageMB)
                .min()
                .orElse(0);
            
            appendResult("Average Memory Usage: " + avgMemory + "MB\n");
            appendResult("Maximum Memory Usage: " + maxMemory + "MB\n");
            appendResult("Minimum Memory Usage: " + minMemory + "MB\n");
            appendResult("Memory Baseline: " + testManager.getBaselineMemoryUsage() + "MB\n");
            appendResult("Peak Memory: " + testManager.getPeakMemoryUsage() + "MB\n");
        }
        
        // Latency test results
        List<PerformanceTestManager.LatencyTestResult> latencyResults = testManager.getLatencyResults();
        if (!latencyResults.isEmpty()) {
            appendResult("\nLatency Test Detailed Data:\n");
            appendResult("Message Count: " + latencyResults.size() + "\n");
            
            double avgLatency = latencyResults.stream()
                .mapToLong(r -> r.latencyMs)
                .average()
                .orElse(0);
            long maxLatency = latencyResults.stream()
                .mapToLong(r -> r.latencyMs)
                .max()
                .orElse(0);
            long minLatency = latencyResults.stream()
                .mapToLong(r -> r.latencyMs)
                .min()
                .orElse(0);
            
            appendResult("Average Latency: " + String.format("%.1f", avgLatency) + "ms\n");
            appendResult("Maximum Latency: " + maxLatency + "ms\n");
            appendResult("Minimum Latency: " + minLatency + "ms\n");
        }
        
        // CPU test results
        List<PerformanceTestManager.CpuTestResult> cpuResults = testManager.getCpuResults();
        if (!cpuResults.isEmpty()) {
            appendResult("\nCPU Test Detailed Data:\n");
            appendResult("Sample Count: " + cpuResults.size() + "\n");
            
            double avgCpu = cpuResults.stream()
                .mapToDouble(r -> r.cpuUsagePercent)
                .average()
                .orElse(0);
            double maxCpu = cpuResults.stream()
                .mapToDouble(r -> r.cpuUsagePercent)
                .max()
                .orElse(0);
            int maxThreads = cpuResults.stream()
                .mapToInt(r -> r.threadCount)
                .max()
                .orElse(0);
            
            appendResult("Average CPU Usage: " + String.format("%.1f", avgCpu) + "%\n");
            appendResult("Maximum CPU Usage: " + String.format("%.1f", maxCpu) + "%\n");
            appendResult("Maximum Threads: " + maxThreads + "\n");
        }
    }
    
    private void updateStatus(String status) {
        tvStatus.setText(status);
    }
    
    private void appendResult(String text) {
        tvResults.append(text);
    }
    
    private void clearResults() {
        tvResults.setText("");
    }

    private void saveResultsToTable() {
        String fileName = "test_data_" + fileDateFormat.format(new Date()) + ".txt";
        File file = new File(getExternalFilesDir(null), fileName);

        try {
            FileWriter writer = new FileWriter(file);
            
            // Write basic information
            writer.append("Test Time: " + timeFormat.format(new Date()) + "\n");
            writer.append("Test Data Record\n");
            writer.append("================\n\n");
            
            // Write memory data
            List<PerformanceTestManager.MemoryTestResult> memoryResults = testManager.getMemoryResults();
            if (!memoryResults.isEmpty()) {
                writer.append("Memory Data:\n");
                for (PerformanceTestManager.MemoryTestResult result : memoryResults) {
                    writer.append(String.format("Time:%d Memory:%dMB Usage:%.1f%% Heap:%dMB\n",
                        result.timestamp,
                        result.memoryUsageMB,
                        result.memoryUsagePercent,
                        result.heapSizeMB
                    ));
                }
                writer.append("\n");
            }
            
            // Write latency data
            List<PerformanceTestManager.LatencyTestResult> latencyResults = testManager.getLatencyResults();
            if (!latencyResults.isEmpty()) {
                writer.append("Latency Data:\n");
                for (PerformanceTestManager.LatencyTestResult result : latencyResults) {
                    writer.append(String.format("Message:%s Latency:%dms Status:%s\n",
                        result.messageId,
                        result.latencyMs,
                        result.status
                    ));
                }
                writer.append("\n");
            }
            
            // Write CPU data
            List<PerformanceTestManager.CpuTestResult> cpuResults = testManager.getCpuResults();
            if (!cpuResults.isEmpty()) {
                writer.append("CPU Data:\n");
                for (PerformanceTestManager.CpuTestResult result : cpuResults) {
                    writer.append(String.format("Time:%d CPU:%.1f%% Threads:%d\n",
                        result.timestamp,
                        result.cpuUsagePercent,
                        result.threadCount
                    ));
                }
            }
            
            writer.close();
            Toast.makeText(this, "Test data saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            shareFile(file);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save test data: " + e.getMessage());
            Toast.makeText(this, "Failed to save test data: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain"); // Changed to text/plain for a simple text file
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Performance Test Data");
            intent.putExtra(Intent.EXTRA_TEXT, "Attached is detailed performance test data, including memory, latency, and CPU usage.");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share Test Data"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to share file: " + e.getMessage());
            Toast.makeText(this, "Failed to share file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (testManager != null) {
            testManager.cleanup();
        }
    }
} 