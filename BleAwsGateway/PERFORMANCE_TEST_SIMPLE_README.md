# 性能测试数据记录与导出

## 功能概述

这是一个简化的性能测试功能，专注于**记录测试数据并导出**，不包含复杂的表格格式和统计信息。

## 主要功能

### 1. 数据记录
- **内存数据**: 记录内存使用量、使用率、堆大小
- **延迟数据**: 记录消息处理延迟和状态
- **CPU数据**: 记录CPU使用率和线程数

### 2. 数据导出
- 将测试数据保存为简单的文本文件
- 文件名格式: `test_data_yyyyMMdd_HHmmss.txt`
- 支持通过系统分享功能发送文件

## 使用方法

### 界面操作
1. 点击"开始测试"按钮开始性能测试
2. 测试过程中会实时显示进度和状态
3. 测试完成后点击"导出数据"按钮
4. 选择分享方式发送数据文件

### 编程使用
```java
// 创建测试管理器
PerformanceTestManager testManager = new PerformanceTestManager(context);

// 设置测试参数
testManager.setTestDuration(30000); // 30秒
testManager.setSampleInterval(100);  // 100ms采样

// 开始测试
testManager.startComprehensiveTest();

// 获取测试数据
List<MemoryTestResult> memoryData = testManager.getMemoryResults();
List<LatencyTestResult> latencyData = testManager.getLatencyResults();
List<CpuTestResult> cpuData = testManager.getCpuResults();
```

## 数据格式

### 导出文件内容
```
测试时间: 14:30:25
测试数据记录
================

内存数据:
时间:1703123425000 内存:156MB 使用率:45.2% 堆:128MB
时间:1703123425100 内存:158MB 使用率:45.8% 堆:128MB
...

延迟数据:
消息:msg_001 延迟:125ms 状态:success
消息:msg_002 延迟:98ms 状态:success
...

CPU数据:
时间:1703123425000 CPU:23.5% 线程:45
时间:1703123425100 CPU:24.1% 线程:46
...
```

## 文件位置

测试数据文件保存在应用的外部存储目录:
```
/storage/emulated/0/Android/data/com.have_no_eyes_deer.bleawsgateway/files/
```

## 技术特点

- **简单记录**: 只记录原始数据，不进行复杂统计
- **轻量导出**: 文本格式，文件小，易读
- **即时分享**: 测试完成后立即可以分享数据
- **时间戳**: 每个数据点都有精确时间戳

## 权限要求

- `WRITE_EXTERNAL_STORAGE`: 保存数据文件
- `READ_EXTERNAL_STORAGE`: 读取数据文件

## 注意事项

1. 测试过程中请勿关闭应用
2. 确保设备有足够存储空间
3. 导出功能需要测试完成后才能使用
4. 文件会自动添加时间戳避免覆盖

## 故障排除

- **无法保存文件**: 检查存储权限
- **分享失败**: 确保文件路径正确
- **数据为空**: 确认测试已完成 