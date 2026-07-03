# SerialDebug — AT 指令伴侣 + 数据仪表盘设计文档

- **功能**: AT 指令伴侣（模板库 + 回复高亮）、数据仪表盘（正则提取 + 数值卡片）
- **版本**: v0.4 剩余功能
- **状态**: 设计完成，待实现
- **日期**: 2026-07-03

---

## 1. 功能概述

为 SerialDebug 增加两个高级工具标签页，完善 v0.4 路线图：

1. **AT 指令伴侣** — 提供常用 AT 指令模板库，一键填入发送区；对模块回复进行关键词着色（OK/ERROR 高亮）
2. **数据仪表盘** — 复用波形图正则规则，从数据流提取数值并以卡片形式展示最新值 + Min/Max/Avg 统计

### 用户故事

- 用户在 AT 标签页浏览指令模板，点击后指令自动填入发送区
- 用户发送 AT 指令后，模块回复在显示区以彩色高亮呈现（OK 绿、ERROR 红、+响应 蓝）
- 用户添加/自定义 AT 指令，重启后仍然存在
- 用户在仪表盘标签页看到实时数值卡片，自动统计 Min/Max/Avg
- 波形图规则修改后，仪表盘自动同步生效

---

## 2. 需求清单

| # | 需求 | 优先级 |
|---|---|---|
| R1 | AT 指令模板库：内置 10+ 常用 AT 指令，JSON 持久化 | P0 |
| R2 | AT 指令一键填入发送区 | P0 |
| R3 | AT 回复关键词着色（OK 绿 / ERROR 红 / +响应 蓝） | P0 |
| R4 | AT 指令增删管理 | P1 |
| R5 | AT 模板搜索过滤 | P1 |
| R6 | 仪表盘数值卡片：展示最新值 + Min/Max/Avg | P0 |
| R7 | 仪表盘复用波形图 DataExtractor 规则 | P0 |
| R8 | 仪表盘清空统计 | P1 |
| R9 | 两个功能各自独立标签页 | P0 |

---

## 3. 架构设计

### 3.1 模块结构

```
serial-debug-ui/src/main/java/io/github/serialdebug/ui/
├── at/                             # 新增：AT 指令伴侣
│   ├── AtCompanionPanel.java       # AT 伴侣面板（左模板库 + 右接收显示）
│   ├── AtCommand.java              # AT 指令模板模型（record）
│   └── AtCommandService.java       # JSON 持久化服务
├── dashboard/                      # 新增：数据仪表盘
│   ├── DashboardPanel.java         # 仪表盘面板（TilePane 卡片布局）
│   ├── DashboardConsumer.java      # 管道消费者，接入 DataExtractor
│   └── MetricCard.java             # 数值卡片组件
├── session/
│   └── SessionTabContent.java      # 修改：替换占位标签页
└── subtab/
    ├── AtConsumer.java             # 删除：原占位符
    └── DashboardConsumer.java      # 删除：原占位符
```

### 3.2 数据流

**AT 指令伴侣:**

```
AtCommandService (JSON) → ListView<AtCommand> → 点击 → Consumer<String> 回调
                                                      ↓
                                              SendController.send()
                                                      ↓
                                                  串口发送
                                                      ↓
                                                  模块回复
                                                      ↓
                                              DisplayController 回调
                                                      ↓
                                              AtCompanionPanel.appendResponse()
                                                      ↓
                                              TextFlow 着色展示
```

**数据仪表盘:**

```
DataExtractor (与波形图共享实例)
      ↓
DashboardConsumer.onPacket()
      ↓ (过滤 TX，只处理 RX)
extractor.extract(text)
      ↓
Consumer<List<ExtractedValue>> 回调
      ↓
DashboardPanel → MetricCard.update(value)
      ↓
刷新显示：latest / min / max / avg / count
```

### 3.3 与现有组件的集成点

| 现有组件 | 集成方式 |
|---|---|
| `SendController` | AT 伴侣通过 `Consumer<String>` 回调将指令填入发送区 |
| `DisplayController` | AT 伴侣通过回调接收回复文本 |
| `DataExtractor` | 仪表盘与波形图共享同一实例 |
| `SessionDataPipeline` | DashboardConsumer 注册为消费者 |
| `SubTabPane` | 两个新功能各自作为 lazy tab 添加 |

---

## 4. 组件规格

### 4.1 AtCommand 模型

```java
public record AtCommand(
    String name,        // 显示名，如 "查询信号强度"
    String command,     // 实际指令，如 "AT+CSQ"
    String description  // 可选说明
) {}
```

### 4.2 AtCommandService

遵循 `JsonPresetService` 模式：

| 属性 | 值 |
|---|---|
| 存储路径 | `~/.serialdebug/at-commands.json` |
| 写入策略 | 原子写入（tmp + rename） |
| 损坏处理 | 返回内置默认值，不崩溃 |
| 首次启动 | 文件不存在时写入内置默认值 |

**API:**

```java
public class AtCommandService {
    public List<AtCommand> load();
    public void save(List<AtCommand> commands);
    public List<AtCommand> getDefaults();  // 内置 10+ 常用指令
}
```

**内置指令:**

| 名称 | 指令 |
|---|---|
| 测试通信 | AT |
| 查询厂商 | AT+CGMI |
| 查询型号 | AT+CGMM |
| 查询 IMEI | AT+CGSN |
| 查询信号强度 | AT+CSQ |
| 查询网络注册 | AT+CREG? |
| 查询 APN | AT+CGDCONT? |
| 查询电池 | AT+CBC |
| 查询时间 | AT+CCLK? |
| 重启模块 | AT+CFUN=1,1 |

### 4.3 AtCompanionPanel

**布局：** `SplitPane` 水平分割

左半部分（模板库）：
- `TextField` 搜索框
- `ListView<AtCommand>` 指令列表
- `Button` 添加 / 删除按钮

右半部分（接收显示）：
- `TextFlow` 在 `ScrollView` 中，支持关键词着色
- `Button` 清空按钮

**关键词着色规则：**

| 关键词 | 颜色 | CSS |
|---|---|---|
| `OK` | 绿色 | `#2ecc71` |
| `ERROR` / `+CME ERROR` / `+CMS ERROR` | 红色 | `#e74c3c` |
| `+` 开头的响应行 | 蓝色 | `#3498db` |
| 普通文本 | 默认 | `#333333` |

**公共 API:**

```java
public class AtCompanionPanel extends BorderPane {
    public AtCompanionPanel(Consumer<String> onCommandSelected);
    public void appendResponse(String text);  // 添加一行回复并着色
    public void clearResponses();             // 清空显示区
}
```

### 4.4 DashboardConsumer

```java
public class DashboardConsumer implements PayloadConsumer {
    private final DataExtractor extractor;
    private final Consumer<List<ExtractedValue>> onExtracted;

    public DashboardConsumer(DataExtractor extractor,
                             Consumer<List<ExtractedValue>> onExtracted);

    @Override
    public void onPacket(RawPacket pkt) {
        if (pkt.dir() == Direction.TX) return;
        String text = new String(pkt.data(), pkt.offset(), pkt.length());
        var values = extractor.extract(text);
        if (!values.isEmpty()) {
            onExtracted.accept(values);
        }
    }
}
```

与 `ChartConsumer` 类似，但将提取结果传递给回调而非 ChartDataBuffer。

### 4.5 MetricCard

```java
public class MetricCard extends VBox {
    public MetricCard(String seriesName);
    public synchronized void update(double value);
    public void reset();
    public double getLatest();
    public double getMin();
    public double getMax();
    public double getAverage();
    public int getCount();
}
```

**UI 结构：**
```
┌─────────────────────┐
│ 🌡 Temperature      │  ← 系列名
│                     │
│     24.5 °C         │  ← 最新值（大字体 24px）
│                     │
│ Min: 18.0  Max: 32.0│  ← 统计行
│ Avg: 23.8  N: 142   │  ← 平均值 + 样本数
└─────────────────────┘
```

**数值格式化：**
- 整数显示为整数（如 `24`）
- 小数保留 1 位（如 `24.5`）
- 极大/极小值使用科学计数法
- `Infinity` / `NaN` 显示为 `---`

### 4.6 DashboardPanel

**布局：** `BorderPane`
- 中心：`TilePane` 自动排列 MetricCard（每张卡片 180x120px，间距 8px）
- 底部：`Button` 清空统计

**公共 API:**

```java
public class DashboardPanel extends BorderPane {
    public DashboardPanel(DataExtractor extractor);
    public void resetAll();  // 清空所有卡片统计
}
```

**卡片管理：**
- 收到新系列时自动创建卡片
- 已有系列更新对应卡片
- TilePane 自动换行排列

### 4.7 SessionTabContent 改动

将原有两个占位标签页替换：

```java
// 删除：
Tab atTab = new Tab("AT伴侣", createPlaceholder("AT 指令伴侣 — M3"));
subTabs.addLazyTab("at", atTab, new AtConsumer(), null, null);

Tab dashTab = new Tab("仪表盘", createPlaceholder("数据仪表盘 — M3"));
subTabs.addLazyTab("dash", dashTab, new DashboardConsumer(), null, null);

// 新增：
AtCompanionPanel atPanel = new AtCompanionPanel(
    cmd -> sendController.setSendText(cmd));
Tab atTab = new Tab("AT 伴侣", atPanel);
subTabs.addLazyTab("at", atTab, new PayloadConsumer() {
    @Override public void onPacket(RawPacket pkt) { /* AT 伴侣通过 DisplayController 回调接收 */ }
}, null, null);

DashboardPanel dashboardPanel = new DashboardPanel(waveExtractor);
Tab dashTab = new Tab("仪表盘", dashboardPanel);
subTabs.addLazyTab("dash", dashTab,
    new DashboardConsumer(waveExtractor, dashboardPanel::onExtracted),
    null, null);
```

**`SendController` 新增方法：**

```java
/** Set the send text field value (used by AT companion to fill commands). */
public void setSendText(String text) {
    if (text != null && sendField != null) {
        sendField.setText(text);
    }
}
```

**`DisplayController` 新增回调：**

```java
private Consumer<String> onResponseReceived;

/** Register a callback for received response text (one line at a time). */
public void setOnResponseReceived(Consumer<String> callback) {
    this.onResponseReceived = callback;
}
```

在 `onDataReceived()` 中，每构造完一行 hex/ascii 文本后，调用 `onResponseReceived.accept(line)` 通知 AT 伴侣面板。

---

## 5. 错误处理

| 场景 | 处理方式 |
|---|---|
| AT 指令 JSON 文件损坏 | `AtCommandService.load()` 返回内置默认值，不崩溃 |
| AT 指令 JSON 写入失败 | 打印 stderr，内存中继续运行 |
| 仪表盘无规则时收到数据 | `DataExtractor.extract()` 返回空列表，无操作 |
| 仪表盘卡片数值溢出 | `Infinity` / `NaN` 格式化为 `---` |
| AT 显示区数据过多 | 超过 1000 行时自动清理前 500 行 |

---

## 6. 测试策略

本功能全部是 UI 代码，core 模块无新增逻辑。遵循项目惯例（UI 模块无自动化测试），采用手动验证：

| 验证项 | 方法 |
|---|---|
| AT 模板库加载 | 启动后 AT 标签页显示内置指令列表 |
| AT 指令发送 | 点击模板 → 发送区填入指令 → 发送成功 |
| AT 回复着色 | 串口回复 OK/ERROR 正确着色 |
| AT 指令增删 | 添加自定义指令 → 重启后仍在 |
| AT 搜索过滤 | 输入关键词 → 列表过滤 |
| 仪表盘数值更新 | 波形图规则生效时，仪表盘卡片实时更新 |
| 仪表盘统计 | Min/Max/Avg 计算正确 |
| 规则共享 | 修改波形图规则 → 仪表盘同步生效 |
| 清空统计 | 点击按钮后所有卡片重置 |

---

## 7. 文件清单

### 新增文件（6 个）

| 文件 | 模块 |
|---|---|
| `ui/at/AtCompanionPanel.java` | ui |
| `ui/at/AtCommand.java` | ui |
| `ui/at/AtCommandService.java` | ui |
| `ui/dashboard/DashboardPanel.java` | ui |
| `ui/dashboard/DashboardConsumer.java` | ui |
| `ui/dashboard/MetricCard.java` | ui |

### 修改文件（3 个）

| 文件 | 改动 |
|---|---|
| `ui/session/SessionTabContent.java` | 替换两个占位标签页为 AT 伴侣和仪表盘正式实现 |
| `ui/controller/SendController.java` | 新增 `setSendText(String text)` 方法，供 AT 伴侣填入发送区 |
| `ui/controller/DisplayController.java` | 新增 `setOnResponseReceived(Consumer<String>)` 回调，供 AT 伴侣接收回复文本 |

### 删除文件（2 个）

| 文件 | 原因 |
|---|---|
| `ui/subtab/AtConsumer.java` | 替换为 ui/at/ 下的正式实现 |
| `ui/subtab/DashboardConsumer.java` | 替换为 ui/dashboard/ 下的正式实现 |

---

## 8. 后续规划（不在本次范围）

- AT 指令执行序列（批量发送 + 期望回复比对 + PASS/FAIL）
- 仪表盘卡片拖拽排序
- 仪表盘迷你趋势图
- 仪表盘数据导出 CSV
- AT 指令分类管理（网络/GPS/短信等）
