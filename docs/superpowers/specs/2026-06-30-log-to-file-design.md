# SerialDebug — 日志落盘功能设计文档

- **功能**: 将串口接收/发送的数据保存到文本文件
- **版本**: v0.2 功能子集
- **状态**: 设计完成，待实现
- **日期**: 2026-06-30

---

## 1. 功能概述

为 SerialDebug 增加日志落盘能力，允许用户将串口收发数据手动记录到本地文本文件，支持 HEX / ASCII 两种格式输出、文件自动分卷、记录状态实时显示。

### 用户故事

- 用户点击「开始记录」，选择保存路径，数据开始写入文件
- 用户点击「停止记录」，文件关闭，写入停止
- 记录过程中可切换 HEX / ASCII 格式
- 文件超过 100MB 时自动创建新文件继续记录
- 状态栏实时显示记录状态、已写入字节数、当前文件路径

---

## 2. 需求清单

| # | 需求 | 优先级 |
|---|---|---|
| R1 | 手动开始/停止记录（文件选择对话框选路径） | P0 |
| R2 | 文本格式保存：HEX 视图样式（`01 02 FF`） | P0 |
| R3 | 文本格式保存：ASCII 视图样式（非打印字符 `\xHH` 转义） | P0 |
| R4 | 每行带时间戳 + RX/TX 方向标识，与屏幕显示一致 | P0 |
| R5 | 记录过程中可切换 HEX/ASCII 格式 | P1 |
| R6 | 文件分卷：超过 100MB 自动创建 `_N` 后缀新文件 | P1 |
| R7 | 状态栏显示记录状态、已写入字节数、当前文件路径 | P1 |
| R8 | 写入异常时自动停止并弹窗提示，不崩溃 | P0 |

---

## 3. 架构设计

### 3.1 分层定位

日志功能遵循项目已有的 Service 接口模式，新增 `log` 包于 `serial-debug-core` 模块：

```
serial-debug-core/
└── src/main/java/io/github/serialdebug/core/
    ├── serial/          (已有)
    ├── parser/          (已有)
    └── log/             (新增)
        ├── LogService.java
        ├── LogFormat.java
        ├── Direction.java
        └── FileLogService.java
```

**设计原则：**
- `LogService` 为接口，与 `SerialService` 同级，保持 core 模块零 UI 依赖
- `FileLogService` 是唯一实现，封装所有文件 I/O 逻辑
- Controller 通过接口调用，可 mock 测试

### 3.2 数据流

```
SerialPort
    ↓ (jSerialComm 监听线程)
JSerialCommService
    ↓ (Consumer<byte[]> 回调)
Controller.onDataReceived()
    ├── Platform.runLater → TextArea (不变)
    └── logService.log(data, offset, length, Direction.RX)  [新增]

Controller.onSend()
    ├── serialService.sendData() (不变)
    ├── Platform.runLater → TextArea (不变)
    └── logService.log(data, offset, length, Direction.TX)  [新增]
        ↓
FileLogService
    ↓ (序列化 + 分卷检查 + 缓冲写入)
BufferedWriter → 文件
```

**线程模型：**
- RX 数据：jSerialComm 监听线程 → `logService.log()` → 内部锁保护写文件
- TX 数据：JavaFX 事件线程 → `logService.log()` → 同一把锁
- 文件 I/O 始终在调用者线程内同步完成（缓冲写入，单次写入延迟可忽略）

---

## 4. 组件规格

### 4.1 LogService 接口

```java
package io.github.serialdebug.core.log;

import java.io.IOException;
import java.nio.file.Path;

public interface LogService {

    /**
     * 开始记录到指定文件。
     * @param file   目标文件路径
     * @param format 日志格式 (HEX / ASCII)
     * @throws IOException 文件创建/打开失败
     */
    void start(Path file, LogFormat format) throws IOException;

    /** 停止记录，关闭文件流。 */
    void stop();

    /** 是否正在记录。 */
    boolean isLogging();

    /**
     * 写入一条日志。
     * @param data      原始字节数据
     * @param offset    起始偏移
     * @param length    有效长度
     * @param direction RX 或 TX
     */
    void log(byte[] data, int offset, int length, Direction direction);

    /** 已写入文件的总字节数（不含缓冲）。 */
    long getBytesLogged();

    /** 当前正在写入的文件路径。 */
    Path getCurrentFile();
}
```

### 4.2 枚举类型

```java
public enum LogFormat { HEX, ASCII }

public enum Direction { RX, TX }
```

### 4.3 FileLogService

| 属性 | 值 |
|---|---|
| 默认分卷阈值 | 100 MB |
| 写入缓冲 | `BufferedWriter` (8KB) |
| 字符编码 | UTF-8 |
| 时间戳格式 | `HH:mm:ss.SSS` |
| 线程安全 | `ReentrantLock` 保护写操作和分卷 |

**分卷命名规则：**

| 原始文件 | 分卷 1 | 分卷 2 | ... |
|---|---|---|---|
| `log.txt` | `log_1.txt` | `log_2.txt` | ... |
| `my_data.log` | `my_data_1.log` | `my_data_2.log` | ... |

**序列化格式：**

HEX 模式：
```
[14:30:22.123 RX] 01 02 FF 0A
[14:30:22.456 TX] 41 54 2B 0D 0A
```

ASCII 模式：
```
[14:30:22.123 RX] Hello\x00World
[14:30:22.456 TX] AT+\x0D\x0A
```

ASCII 模式下所有非打印字节（值 < 0x20 或 > 0x7E，包括 `\r` `\n` `\0` 等）统一以 `\xHH` 两位十六进制转义，保留原始信息。可打印字节（0x20-0x7E）直接输出为对应 ASCII 字符。

**分卷触发时机：** 每次 `log()` 写入并 `flush()` 后，检查 `currentFile.length() >= splitThreshold`，若超过则关闭当前 writer、生成新文件名、创建新 writer。整个检查-滚动操作在锁内完成。

### 4.4 UI 改动

**工具栏新增元素：**

```
[开始记录] [停止记录] | 记录格式: (● HEX ○ ASCII) | ...
```

- 「开始记录」按钮 — 绑定 `onStartLogging()`
- 「停止记录」按钮 — 绑定 `onStopLogging()`，初始 `disable=true`
- 记录格式切换 — `ToggleButton` 组或 `ComboBox<LogFormat>`

**状态栏新增：**

```
[● Recording: D:\logs\port_COM3.log (1.2 MB)]    [RX: 1234] [TX: 567]
```

未记录时显示：`[Not recording]`

**MainController 改动要点：**
- 新增 `@FXML` 字段：`startLoggingButton`, `stopLoggingButton`, `logFormatToggle`, `loggingStatusLabel`
- `onStartLogging()` — 弹出 `FileChooser`，调用 `logService.start()`，更新按钮状态和状态栏
- `onStopLogging()` — 调用 `logService.stop()`，更新按钮状态和状态栏
- `onDataReceived()` — 在 `Platform.runLater` 内追加 `logService.log(..., Direction.RX)`
- `onSend()` — 在发送成功后追加 `logService.log(..., Direction.TX)`
- `updateStats()` — 扩展为同时刷新记录状态标签

---

## 5. 错误处理

| 场景 | 处理方式 |
|---|---|
| 文件选择对话框取消 | 不调用 `start()`，状态不变 |
| 文件创建失败（权限/无效路径） | `start()` 抛 `IOException`，Controller 弹窗 `Alert.ERROR` |
| 写入时磁盘已满/IO 异常 | 捕获异常 → 自动 `stop()` → Controller 弹窗提示 → 重置 UI 状态 |
| 未点击「开始记录」时收到数据 | `log()` 在 `isLogging()==false` 时直接 return，无操作 |
| 连续快速开始/停止 | 按钮状态互斥保护，`stop()` 幂等（已停止时调用无效果） |

---

## 6. 测试策略

### 6.1 单元测试（serial-debug-core）

`FileLogServiceTest` 覆盖：

| 测试用例 | 验证点 |
|---|---|
| `shouldWriteHexString` | HEX 格式序列化正确，含时间戳和方向 |
| `shouldWriteAsciiString` | ASCII 格式序列化正确，非打印字符 `\xHH` 转义 |
| `shouldCreateFileOnStart` | `start()` 后文件存在 |
| `shouldCloseFileOnStop` | `stop()` 后文件流关闭，资源释放 |
| `shouldSplitFileWhenExceedsThreshold` | 写入超过 100MB 时自动创建 `_1` 后缀新文件 |
| `shouldNotLogWhenStopped` | `isLogging()==false` 时 `log()` 不写入 |
| `shouldHandleConcurrentLogging` | 多线程并发 `log()` 不丢数据、不损坏文件 |
| `shouldThrowOnInvalidPath` | 无效路径时 `start()` 抛 `IOException` |
| `shouldIncrementBytesLogged` | `getBytesLogged()` 与实际写入量一致 |

### 6.2 UI 验证

手动验证（JavaFX UI 暂无自动化测试框架）：
- 开始/停止记录流程
- 文件格式切换实时生效
- 状态栏信息正确更新
- 异常场景弹窗提示

---

## 7. 文件清单

### 新增文件

| 文件 | 模块 |
|---|---|
| `core/.../log/LogService.java` | core |
| `core/.../log/LogFormat.java` | core |
| `core/.../log/Direction.java` | core |
| `core/.../log/FileLogService.java` | core |
| `core/.../log/FileLogServiceTest.java` | core (test) |

### 修改文件

| 文件 | 改动 |
|---|---|
| `ui/.../main-view.fxml` | 新增开始/停止按钮、格式切换、状态标签 |
| `ui/.../controller/MainController.java` | 注入 LogService，新增事件处理 |
| `ui/pom.xml` | 无改动（已有 junit 依赖） |

---

## 8. 后续规划（不在本次范围）

- 日志文件按日期自动命名
- 配置记忆：记住上次保存路径
- 日志回放功能
- 自动日志：连接后自动开始记录
- 分卷阈值可配置
