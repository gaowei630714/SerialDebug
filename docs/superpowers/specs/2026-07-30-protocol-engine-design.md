# 协议引擎模块 — 设计文档

> 日期：2026-07-30
> 状态：已审批（brainstorming 阶段确认）

## 1. 问题背景

当前 SerialDebug 的"提取数值"功能服务于**波形图**和**仪表盘**两个场景，
核心由 `DataExtractor` 实现，提供两条路径：

- **正则提取** — `addRegexRule(seriesName, regex, groupIndex)`，适用于文本协议（如 `T:25.3`）
- **固定偏移** — `addOffsetRule(seriesName, byteOffset, length)`，适用于固定位置的二进制字段

数据流：

```
SerialPort (jSerialComm) → SessionDataPipeline.publish(byte[])
    → ChartConsumer / DashboardConsumer
        → DataExtractor.extract(text | byte[])
            → ChartDataBuffer / DashboardPanel
```

**核心矛盾**：正则天然只适合文本；固定偏移只能处理"每条消息就是一帧"的情形，
无法应对真实串口二进制协议——帧头定界、跨 packet 拆帧、字节流缓冲。

> 用户的原话："正则规则提取数值。对于 hex 作用不大，都有自己的协议。"

## 2. 设计目标

1. 支持用 **JSON 声明式描述** 定义任意二进制协议
2. 支持 **帧头定界** 与 **固定帧长** 两种帧对齐方式
3. 协议与波形/仪表盘**一键关联**，所有字段自动成为序列/卡片
4. 与现有正则/偏移路径**并行共存**，不修改现有代码

## 3. 非目标（不在本版本）

- 长度字段动态切帧（C 模式）
- 嵌套结构 / 字段间依赖
- CRC 校验
- 字符串字段提取
- 可视化帧结构编辑器

## 4. 架构

### 4.1 模块划分

新增类均位于 `serial-debug-protocol` 模块（目前为 SPI 占位，作为承载层），
以及 `serial-debug-ui` 模块的 UI 组件。

```
serial-debug-protocol
├── protocol/
│   ├── Protocol.java             # 协议描述 POJO（name, framing, fields）
│   ├── ProtocolField.java        # 字段描述（name, offset, size, type, endian, scale, bias, bits）
│   ├── ProtocolValue.java        # 解析结果（name, double value, long timestamp）
│   ├── ProtocolParser.java       # 核心引擎：帧头搜索 + 字段解包
│   ├── ProtocolStore.java        # 协议文件 CRUD 接口
│   └── JsonProtocolStore.java    # JSON 序列化/反序列化实现
└── protocol/
    └── (SPI 扩展点，保留)

serial-debug-ui
└── protocol/
    ├── ProtocolPanel.java        # 协议编辑器表单 UI
    ├── ProtocolConsumer.java     # PayloadConsumer，注册到 SessionDataPipeline
    └── ProtocolManager.java      # 协议列表加载/切换/校验
```

**依赖关系**：`serial-debug-protocol` 仅依赖 `serial-debug-core` 的
`SessionDataPipeline.RawPacket`，不依赖 UI，保持核心层纯净。

### 4.2 与现有系统的连接

`ProtocolConsumer` 实现已有的 `PayloadConsumer` 接口，注册到 `SessionDataPipeline`。
解析出的 `ProtocolValue` 推入**同一个** `ChartDataBuffer` 和 `DashboardPanel`，
与现有正则/偏移路径共用下游，**无需修改 ChartConsumer、DashboardConsumer、
DashboardPanel、WaveChartCanvas**。

## 5. JSON 协议描述 Schema

用户编写 / UI 表单生成维护的 JSON 文件，存放于 `.serialdebug/protocols/`。

```json
{
  "name": "温湿度传感器",
  "version": "1.0",
  "framing": {
    "mode": "header",
    "header": "AA55",
    "frameLength": 10
  },
  "fields": [
    {
      "name": "temp",
      "label": "温度 °C",
      "offset": 2,
      "size": 2,
      "type": "int16_le",
      "scale": 0.1,
      "bias": 0.0
    },
    {
      "name": "humi",
      "label": "湿度 %",
      "offset": 4,
      "size": 2,
      "type": "uint16_be",
      "scale": 0.1,
      "bias": 0.0
    },
    {
      "name": "status",
      "label": "状态",
      "offset": 6,
      "size": 1,
      "type": "uint8"
    },
    {
      "name": "alarm",
      "label": "告警",
      "offset": 6,
      "size": 1,
      "type": "uint8",
      "bits": [0],
      "scale": 1,
      "bias": 0
    }
  ]
}
```

### 5.1 字段定义

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `name` | string | 是 | 序列/卡片名称，全局唯一 |
| `label` | string | 否 | UI 显示名，默认使用 `name` |
| `offset` | int | 是 | 字段在帧内的起始字节偏移 |
| `size` | int | 是 | 字段字节数（1/2/4/8） |
| `type` | string | 是 | 数值类型（见 5.2） |
| `scale` | double | 否 | 线性变换乘数，默认 1.0 |
| `bias` | double | 否 | 线性变换偏移，默认 0.0 |
| `bits` | int[] | 否 | bit 切片，取该字段内的特定位索引 |

### 5.2 支持的数值类型

| 类型 | 字节数 | 字节序 | 说明 |
|---|---|---|---|
| `uint8` | 1 | — | 无符号 8 位 |
| `uint16_le` / `uint16_be` | 2 | LE / BE | 无符号 16 位 |
| `uint32_le` / `uint32_be` | 4 | LE / BE | 无符号 32 位 |
| `int8` | 1 | — | 有符号 8 位 |
| `int16_le` / `int16_be` | 2 | LE / BE | 有符号 16 位 |
| `int32_le` / `int32_be` | 4 | LE / BE | 有符号 32 位 |
| `float32_le` / `float32_be` | 4 | LE / BE | IEEE 754 单精度 |
| `float64_le` / `float64_be` | 8 | LE / BE | IEEE 754 双精度 |

### 5.3 帧配置

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `mode` | string | 是 | `"header"`（帧头定界）或 `"fixed"`（固定帧长） |
| `header` | string | 仅 header 模式 | 帧头 hex 字符串，如 `"AA55"` |
| `frameLength` | int | 是 | 每帧固定字节数 |

### 5.4 校验规则（加载时静态校验）

1. `name` 非空且在 `fields[]` 内唯一
2. 每个字段的 `offset + size ≤ frameLength`
3. `bits` 中的每个索引 `< size * 8` 且 ≥ 0
4. `type` 必须属于 5.2 列表
5. `header` 的 hex 字符数为偶数且在 2-8 字节范围内
6. `frameLength > 0`

校验失败给出明确错误信息，拒绝加载，不进入解析流程。

## 6. 核心引擎 — ProtocolParser

### 6.1 帧对齐与字节缓冲池

串口数据按 `jSerialComm` 回调分片投递，一条帧可能跨多个 packet，
也可能多个帧粘在一个 packet 里。`ProtocolParser` 维护一个滑动缓冲池：

```
ProtocolParser
├── private final ByteBuffer frameBuffer       // 积压字节池
├── private final int headerLen                // 帧头字节数
├── private final byte[] headerBytes           // 帧头模式
├── private final int frameLength              // 固定帧长
├── private final Protocol protocol            // 协议描述
├── private final int maxBufferCapacity        // 最大累积 = frameLength * 4
└── private Consumer<ProtocolValue> onValue    // 解析结果回调
```

### 6.2 feed() 流程

```
feed(byte[] data, int offset, int length):
  1. 将新数据追加到 frameBuffer
  2. loop:
     a. 在 frameBuffer 中搜索 headerBytes（"fixed" 模式跳过）
     b. 未找到 → break，等待下一次 feed
     c. 找到 → 计算从 header 起剩余字节数
     d. 字节不足 frameLength → break，等待
     e. 字节足够 → 取一完整帧 → 解包字段 → 消费
     f. frameBuffer 前移 frameLength 字节
  3. 若 frameBuffer 超出 maxBufferCapacity → 丢弃头部字节重扫（WARN）
```

**帧头定界模式**：搜索 `headerBytes`，支持同一 packet 内的粘连多帧（
一帧解完后循环继续搜索下一帧）。

**固定帧长模式**：不搜索帧头，`frameBuffer` 累积到 `frameLength` 即切一帧解包。

### 6.3 字段解包

每帧到齐后，遍历 `protocol.fields[]`：

```
for field in protocol.fields:
  if field.isEnabled == false → skip
  raw = 从帧 data[field.offset, field.size] 按 field.type 读字节
  if field.bits 存在 → raw = 按 bits 切片
  value = raw * field.scale + field.bias
  emit ProtocolValue(field.name, value, System.nanoTime())
```

`readBytes(data, type)` 实现：

- `uint8/int8`：直接 `(data[offset] & 0xFF)` / `(byte) data[offset]`
- `uint16_le`：`(data[offset] & 0xFF) | ((data[offset+1] & 0xFF) << 8)`
- `uint16_be`：`((data[offset] & 0xFF) << 8) | (data[offset+1] & 0xFF)`
- `uint32/float32`：4 字节组合后 `Float.intBitsToFloat` / 直接 int
- `int16/int32`：同理，符号扩展
- `float64`：8 字节 → `Double.longBitsToDouble`

**bit 切片**：将 `raw` 视为 `size*8` 位的无符号整数（LSB = 第 0 位），
按 `bits[]` 指定的位索引取出对应位，重组为整数值（`bits[0]` 映射到结果 LSB）。

### 6.4 线程安全

`ProtocolParser` **不保证线程安全**。它只在 `PayloadConsumer.onPacket()` 中调用，
由 jSerialComm 监听器线程单线程驱动，无需加锁。

## 7. 端到端数据流

```
jSerialComm 监听器线程
  │
  ├─ SessionDataPipeline.publish(byte[])      // 已有，不变
  │       └─ RingBuffer<RawPacket>
  │
  ├─ ChartConsumer.onPacket()                 // 已有，正则/偏移路径，不变
  │
  ├─ DashboardConsumer.onPacket()             // 已有，正则路径，不变
  │
  └─ ProtocolConsumer.onPacket(pkt)           // 【新增】
          │
          ├─ ProtocolParser.feed(pkt.data)
          │       ├─ 帧头定界：搜索 AA55 → 取 frameLength 字节
          │       ├─ 固定帧长：累积到 frameLength → 切帧
          │       ├─ 解包 fields[] → 线性变换
          │       └─ emit ProtocolValue[]
          │
          └─ Platform.runLater()
                  ├─ dataBuffer.addPoint(seriesName, value)    // 波形
                  └─ dashboardPanel.onExtracted(values)         // 仪表盘
```

## 8. 错误处理

| 情况 | 行为 |
|---|---|
| 协议 JSON 格式错误 | 拒绝加载，`showWarning` 提示错误信息 |
| 字段 offset+size 超帧长 | 静态校验拦截，拒绝加载 |
| 字节流中非预期字节 | WARN 日志 + 丢弃头部字节重扫，不中断 |
| 缓冲池溢出 | WARN 日志 + 丢弃头部，说明可能丢帧 |
| 帧解包字段越界 | 该字段跳过，WARN，其他字段正常 |
| 用户关闭协议 | 清空 `frameBuffer`、停止消费，已有数据保持 |
| bit 切片索引越界 | 该字段跳过，WARN |
| type 不在支持列表 | 静态校验拦截，拒绝 |

## 9. UI — 协议编辑器

`ProtocolPanel` 在会话 Tab 内新增一个 **"协议" subtab**，与 CRC、波形并列。

### 9.1 上部 — 协议选择

- 下拉列表：加载 `.serialdebug/protocols/` 下所有 JSON 文件
- 按钮：**加载** / **新建** / **保存** / **删除**

### 9.2 中部 — 帧配置

- 模式选择：`帧头定界` / `固定帧长`（切换 header 输入框可见性）
- 帧头 hex 输入（仅帧头模式）
- 帧长数字输入

### 9.3 下部 — 字段表格

| 启用 | 名称 | 标签 | 偏移 | 大小 | 类型 | Scale | Bias | Bit | 操作 |
|---|---|---|---|---|---|---|---|---|---|
| ☑ | temp | 温度 °C | 2 | 2 | int16_le | 0.1 | 0 | — | ✕ |
| ☑ | humi | 湿度 % | 4 | 2 | uint16_be | 0.1 | 0 | — | ✕ |

- 每行可编辑、可新增、可删除
- "启用" 复选框控制该字段是否参与解析
- 实时生成 JSON 预览
- "保存" 写入 `.serialdebug/protocols/`

### 9.4 加载协议的行为

加载协议后，所有已启用字段**自动**：
- 作为序列加入波形图（与现有正则序列共存）
- 作为卡片加入仪表盘（与现有 metric card 共存）

每个字段在 UI 上有独立开关可单独关闭。

## 10. 与现有代码的修改点

| 文件 | 改动 |
|---|---|
| `serial-debug-protocol/pom.xml` | 新增模块 pom |
| `serial-debug-core` | 无改动 |
| `serial-debug-app/pom.xml` | 新增 `serial-debug-protocol` 模块依赖 |
| `SessionTabContent.java` | 新增 "协议" subtab + `ProtocolConsumer` 注册 |
| `Messages.java` / `messages_*.properties` | 新增 i18n key |

**无需修改**：`DataExtractor`、`ChartConsumer`、`DashboardConsumer`、
`DashboardPanel`、`ChartDataBuffer`、`WaveChartCanvas`。

## 11. 测试计划

| 测试 | 内容 |
|---|---|
| 帧头定界 - 单帧 | 一个 packet 内含完整帧 |
| 帧头定界 - 粘连 | 一个 packet 内含两帧 |
| 帧头定界 - 跨包 | 帧跨 2-3 个 packet |
| 帧头定界 - 丢首字节 | 模拟丢失，重扫下一个帧头 |
| 固定帧长 - 切片 | 按 frameLength 对齐 |
| 固定帧长 - 不足 | 字节不够不触发 |
| 字段解包 - 每种 type | uint/int 8/16/32, float32/64, 大小端 |
| 字段解包 - scale/bias | 线性变换正确 |
| 字段解包 - bit 切片 | 单 bit + 多 bit |
| 协议加载 - 合法 | 完整 JSON 通过 |
| 协议加载 - 超帧长 | offset+size > frameLength 拒绝 |
| 协议加载 - 非法 header | hex 奇数长度拒绝 |
| ProtocolConsumer 端到端 | mock RawPacket → 入 dataBuffer |

## 12. 文件与目录

- 协议存储：`.serialdebug/protocols/`，每个协议一个 `.json` 文件
- 设计文档：`docs/superpowers/specs/2026-07-30-protocol-engine-design.md`（本文）
