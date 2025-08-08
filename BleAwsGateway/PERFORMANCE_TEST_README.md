# 性能测试功能说明

## 概述

本项目实现了完整的性能测试功能，用于测试BLE网关应用的延迟和内存占用。测试功能包括：

- **内存监控测试**：实时监控应用内存使用情况
- **延迟测试**：模拟消息处理延迟
- **CPU压力测试**：测试CPU使用率
- **综合性能测试**：同时运行多种测试

## 主要组件

### 1. PerformanceTestManager
核心测试管理器，负责：
- 配置测试参数
- 执行各种测试
- 收集测试数据
- 生成测试报告

### 2. PerformanceTestActivity
UI界面，提供：
- 测试控制按钮
- 实时进度显示
- 测试结果展示
- 详细数据报告

### 3. PerformanceTestExample
示例类，展示如何使用测试功能

## 使用方法

### 1. 通过UI界面测试

1. 启动应用
2. 在主界面点击"性能测试"按钮
3. 在性能测试界面点击"开始测试"
4. 等待测试完成，查看结果

### 2. 通过代码测试

```java
// 创建测试管理器
PerformanceTestManager testManager = new PerformanceTestManager(context);

// 配置测试参数
testManager.setTestDuration(30000); // 30秒
testManager.setSampleInterval(100);  // 100ms采样
testManager.setMessageCount(50);     // 50条消息

// 设置监听器
testManager.addTestListener(new PerformanceTestManager.TestListener() {
    @Override
    public void onTestStarted(String testName) {
        Log.i("Test", "测试开始: " + testName);
    }
    
    @Override
    public void onTestProgress(String testName, int progress, String status) {
        Log.d("Test", "进度: " + progress + "% - " + status);
    }
    
    @Override
    public void onTestCompleted(String testName, PerformanceTestManager.TestResult result) {
        Log.i("Test", "测试完成: " + result.summary);
    }
    
    @Override
    public void onTestError(String testName, String error) {
        Log.e("Test", "测试错误: " + error);
    }
});

// 开始测试
testManager.startComprehensiveTest();
```

### 3. 使用示例类

```java
PerformanceTestExample example = new PerformanceTestExample(context);

// 运行快速测试（10秒）
example.runQuickTest();

// 运行标准测试（30秒）
example.runStandardTest();

// 运行长时间测试（60秒）
example.runLongTest();

// 运行自定义测试
example.runCustomTest(45000, 150, 75);
```

## 测试参数说明

### 测试持续时间 (testDuration)
- 单位：毫秒
- 推荐值：10000-60000 (10-60秒)
- 影响：测试时间越长，数据越准确，但耗时也越长

### 采样间隔 (sampleInterval)
- 单位：毫秒
- 推荐值：100-500
- 影响：间隔越小，数据越详细，但CPU占用越高

### 消息数量 (messageCount)
- 单位：条
- 推荐值：20-100
- 影响：消息越多，延迟测试越全面

## 测试结果解读

### 内存测试结果
- **平均内存使用**：测试期间的平均内存占用
- **最大内存使用**：测试期间的最高内存占用
- **内存基线**：测试开始前的内存使用
- **峰值内存**：整个测试过程中的最高内存使用

### 延迟测试结果
- **平均延迟**：所有消息处理的平均延迟时间
- **最大延迟**：单条消息的最大延迟时间
- **最小延迟**：单条消息的最小延迟时间
- **消息数量**：成功处理的消息总数

### CPU测试结果
- **平均CPU使用率**：测试期间的平均CPU使用率
- **最大CPU使用率**：测试期间的最高CPU使用率
- **线程数**：测试期间的最大线程数

## 性能指标参考

### 良好性能指标
- 平均内存使用 < 100MB
- 内存增长 < 20MB
- 平均延迟 < 50ms
- 最大延迟 < 200ms
- 平均CPU使用率 < 30%

### 需要优化的指标
- 平均内存使用 > 200MB
- 内存增长 > 50MB
- 平均延迟 > 100ms
- 最大延迟 > 500ms
- 平均CPU使用率 > 50%

## 注意事项

1. **测试环境**：建议在真实设备上测试，模拟器可能无法提供准确的性能数据
2. **后台应用**：测试前关闭不必要的后台应用，避免干扰
3. **设备状态**：确保设备电量充足，避免性能降频
4. **网络环境**：如果涉及网络测试，确保网络稳定
5. **测试频率**：避免频繁测试，给设备足够的冷却时间

## 故障排除

### 测试无法启动
- 检查权限是否已授予
- 确认设备支持相关功能
- 查看日志中的错误信息

### 测试数据异常
- 检查设备是否处于低电量模式
- 确认没有其他应用占用大量资源
- 重新启动应用后再次测试

### 内存泄漏检测
- 观察内存基线是否持续增长
- 检查是否有未释放的资源
- 使用Android Studio的Memory Profiler进行详细分析

## 扩展功能

### 自定义测试类型
可以通过继承`PerformanceTestManager`来添加新的测试类型：

```java
public class CustomTestManager extends PerformanceTestManager {
    public CustomTestManager(Context context) {
        super(context);
    }
    
    // 添加自定义测试方法
    public void runCustomTest() {
        // 实现自定义测试逻辑
    }
}
```

### 数据导出
测试结果可以导出为JSON或CSV格式，便于进一步分析：

```java
// 获取测试数据
List<MemoryTestResult> memoryResults = testManager.getMemoryResults();
List<LatencyTestResult> latencyResults = testManager.getLatencyResults();
List<CpuTestResult> cpuResults = testManager.getCpuResults();

// 转换为JSON格式
String jsonData = convertToJson(memoryResults, latencyResults, cpuResults);
```

## 更新日志

### v1.0.0
- 实现基础性能测试功能
- 支持内存、延迟、CPU测试
- 提供UI界面和代码接口
- 添加测试结果报告功能 