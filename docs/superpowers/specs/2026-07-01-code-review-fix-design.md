# Code Review 修复设计

## 概述

基于代码审查结果，分三批修复 serial-debug 项目中发现的 10 个问题，涵盖性能、线程安全、代码质量和可维护性。

## 分批策略

采用模块分批策略（方案 A），按依赖关系和风险等级分 3 批独立实施、独立测试。

## 批 1：Core 模块修复

### 范围

`HexParser.java`、`FileLogService.java`

### 1.1 HexParser 性能优化

**问题**: `HexParser.decode()` 逐字节使用 `String.format("%02X", data[i])` 进行 hex 编码，性能约比 `HexFormat` 慢 5-10x。

**修复**: 替换为 Java 17 原生 `HexFormat` API：

```java
// 当前
StringBuilder sb = new StringBuilder(length * 3);
for (int i = offset; i < offset + length; i++) {
    if (i > offset) sb.append(' ');
    sb.append(String.format("%02X", data[i]));
}
return sb.toString();

// 修复后
HexFormat hex = HexFormat.of().withUpperCase().withDelimiter(" ");
return hex.formatHex(data, offset, length);
```

`HexFormat.withDelimiter(" ")` 直接输出带空格分隔的大写 hex 字符串，消除手写循环和 `StringBuilder`。

### 1.2 FileLogService 修复

**问题 a — 文件分割每次写日志触发 Files.size()**: `checkAndSplit()` 对每次 `log()` 调用都执行 `Files.size()` 文件系统查询，产生不必要的 syscall。

**修复**: 利用已有的 `bytesLogged` 字段做阈值判断：

```java
// 修复前：每次调用 Files.size()
if (currentFile != null && Files.exists(currentFile)
        && Files.size(currentFile) >= splitThreshold)

// 修复后：用累计字节计数，打开新文件后重建索引
if (bytesLogged >= splitThreshold) {
    // split...
    bytesLogged = 0; // 新文件重置计数
}
```

注意：由于日志行长度在写入前已知道（`line.length() + separator.length()`），`bytesLogged` 可以精确追踪文件大小。分割后新文件的 `bytesLogged` 从 0 开始计数，不再需要 `Files.size()`。

**问题 b — encodeHex 与 HexParser.decode 代码重复**: `FileLogService` 和 `HexParser` 各自有一份 hex 编码实现。

**修复**: `FileLogService` 改为使用 `HexFormat` 工具方法（与 HexParser 复用同一套）。由于 `HexFormat` 已经是 Java 17 标准库，不需要抽取自定义工具类，两端统一使用 `HexFormat.of().withUpperCase().withDelimiter(" ")`。

**问题 c — IOException 后 currentFile 脏数据**: 当 `log()` 捕获 IOException 时，writer 被关闭并置 null，但 `currentFile` 仍保留旧路径。

**修复**: 在异常处理块中增加 `currentFile = null`：

```java
} catch (IOException e) {
    try { writer.close(); } catch (IOException ignored) {}
    writer = null;
    currentFile = null;  // 新增：清除文件引用
}
```

### 1.3 测试加固

为了拦截 `bytesLogged` 分割逻辑的回归：

- `shouldSplitFileWhenExceedsThreshold`：用 `bytesLogged >= splitThreshold` 与 `Files.exists(splitPath)` 双重断言
- 添加 `shouldNotLogWhenWriterClosed`：start → IOException 注入 → 验证 isLogging() 返回 false

## 批 2：UI 线程安全

### 范围

`MainController.java`

### 2.1 logService.log() 移出 Platform.runLater

**问题**: `onSend()` 的 `Platform.runLater` 块内调用 `logService.log()`（文件 I/O），阻塞 UI 线程。`onDataReceived()` 也类似——log 写入与 UI 更新耦合在同一个 `Platform.runLater` 中。

**修复**: 在 jSerialComm 监听线程（或发送线程）直接执行 `logService.log()`，只把 UI 更新部分塞入 `Platform.runLater`。

`onDataReceived()` 调整后流程：

```
jSerialComm 线程:
  logService.log(data, 0, data.length, Direction.RX)  // 直接写文件
  构造 hexStr / asciiStr
  入队到批量缓冲区
  Platform.runLater → flush 缓冲区到 TextArea + updateStats
```

`onSend()` 调整后流程：

```
发送线程:
  serialService.sendData(data)  // 发送
  logService.log(data, 0, data.length, Direction.TX)  // 直接写文件
  Platform.runLater → appendText + updateStats
```

### 2.2 引入批量提交（batch flush）

**问题**: 中频场景下 jSerialComm 监听线程每秒触发几十到几百次 `onDataReceived`，每次独立 `Platform.runLater` 会导致事件队列增长、UI 更新碎片化。

**修复**: 引入缓冲区 + 原子标记机制：

```java
private static record BatchEntry(String timestamp, String hex, String ascii, Direction dir) {}

private final List<BatchEntry> batchBuffer = new ArrayList<>();
private final AtomicBoolean batchPending = new AtomicBoolean(false);

void onDataReceived(byte[] data) {
    // 1. 后台线程写日志
    if (logService.isLogging()) {
        logService.log(data, 0, data.length, Direction.RX);
    }
    // 2. 构造条目入队
    String timestamp = LocalTime.now().format(TIME_FORMATTER);
    String hex = hexParser.decode(data, 0, data.length);
    String ascii = asciiParser.decode(data, 0, data.length);
    synchronized (batchBuffer) {
        batchBuffer.add(new BatchEntry(timestamp, hex, ascii, Direction.RX));
    }
    // 3. 仅当无等待批次时提交
    if (batchPending.compareAndSet(false, true)) {
        Platform.runLater(this::flushBatch);
    }
}

void flushBatch() {
    List<BatchEntry> entries;
    synchronized (batchBuffer) {
        entries = new ArrayList<>(batchBuffer);
        batchBuffer.clear();
    }
    for (BatchEntry e : entries) {
        hexViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.hex + "\n");
        asciiViewArea.appendText("[" + e.timestamp + " " + e.dir + "] " + e.ascii + "\n");
    }
    hexViewArea.setScrollTop(Double.MAX_VALUE);
    asciiViewArea.setScrollTop(Double.MAX_VALUE);
    updateStats();
    batchPending.set(false);
    // 在 flush 期间可能有新数据到达，若 buffer 非空则再次提交
    synchronized (batchBuffer) {
        if (!batchBuffer.isEmpty() && batchPending.compareAndSet(false, true)) {
            Platform.runLater(this::flushBatch);
        }
    }
}
```

关键设计点：
- `AtomicBoolean` 确保任何时刻最多一个 `Platform.runLater` 在队列中等待
- `synchronized` 保护 `ArrayList` 的并发读写（jSerialComm 线程 vs JavaFX 线程）
- flush 末尾检查 buffer 非空可防止竞态——在 flush 执行期间新到达的数据不会丢失

### 2.3 NPE 防御

`openPort()` 中 ComboBox 取值添加 null 检查：

```java
Integer baudRate = baudRateCombo.getValue();
if (baudRate == null) { showWarning("Please select a baud rate"); return; }
Integer dataBits = dataBitsCombo.getValue();
if (dataBits == null) { showWarning("Please select data bits"); return; }
Integer stopBits = stopBitsCombo.getValue();
if (stopBits == null) { showWarning("Please select stop bits"); return; }
SerialConfig.Parity parity = parityCombo.getValue();
if (parity == null) { showWarning("Please select parity"); return; }
```

虽然当前 `initialize()` 已设默认选中项，防御性 null 检查防止未来重构导致 NPE。

## 批 3：质量补全

### 3.1 CSS 类替代内联样式

在 `style.css` 中新增样式类：

```css
.btn-danger {
    -fx-background-color: #e74c3c;
    -fx-text-fill: white;
}
```

`MainController.updatePortState()` 修改：

```java
private void updatePortState(boolean connected) {
    if (connected) {
        openCloseButton.setText("Close");
        openCloseButton.getStyleClass().add("btn-danger");
        connectionStatusLabel.setText("Connected: " + serialService.getCurrentConfig());
    } else {
        openCloseButton.setText("Open");
        openCloseButton.getStyleClass().remove("btn-danger");
        connectionStatusLabel.setText("Disconnected");
    }
}
```

### 3.2 .gitignore 清理

在当前 `.gitignore` 中确认 `.idea/` 条目存在后：

```bash
git rm --cached -r serial-debug/.idea/
```

停止跟踪 IDE 文件，保留本地副本。

### 3.3 补充测试

**JSerialCommServiceTest**：

- Mock `SerialPort.getCommPorts()` 返回空列表/单端口
- Mock `SerialPort.getCommPort()` + `openPort()` 返回 true/false
- 测试：listPorts、open 成功、open 失败（端口不可用）、close、sendData（正常/空/未打开时抛异常）

**FileLogServiceTest 加固**：

- `shouldSplitFileWhenExceedsThreshold`：在验证 split 文件存在前，增加 `assertTrue(service.getBytesLogged() >= 30)` 断言
- `shouldTolerateWriteError`：start → 设文件不可写 → log → 验证 isLogging 返回 false

## 实施顺序

```
批 1 (Core) ──→ 批 2 (UI 线程) ──→ 批 3 (质量补全)
     │                │                  │
     ▼                ▼                  ▼
  mvn test        mvn compile         mvn test
  验证通过         验证编译通过          验证全部通过
```

每批均有独立验证步骤，前一批未通过不进入下一批。

## 不纳入本次修复

- MainController SRP 分解（490 行 → 多个控制器）：改动量大且当前边界不够清晰，留待 v0.2 功能扩展自然重构
- JSerialCommService 补充测试：需要 mock 静态方法，但当前的 PowerMock/Mockito inline mock 策略需要额外配置，优先级低于其他修复。在批 3 作为可选 task
