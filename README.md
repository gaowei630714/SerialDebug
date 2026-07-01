# SerialDebug

<p align="center">
  <b>跨平台串口调试工具 / Cross-Platform Serial Port Debugger</b><br/>
  <sub>面向嵌入式开发 · IoT 调试 · 传感器数据采集</sub><br/>
  <sub>Built for Embedded Development · IoT Debugging · Sensor Data Acquisition</sub>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white" alt="Java 17+" />
  <img src="https://img.shields.io/badge/JavaFX-21-007396?logo=java&logoColor=white" alt="JavaFX 21" />
  <img src="https://img.shields.io/badge/jSerialComm-2.11.0-2088FF?logo=data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7" alt="jSerialComm" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-green?logo=apache" alt="Apache 2.0" />
  <img src="https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey" alt="Platform" />
</p>

<p align="center">
  <a href="#快速开始--quick-start">快速开始 / Quick Start</a> ·
  <a href="#功能特性--features">功能 / Features</a> ·
  <a href="#项目结构--architecture">架构 / Architecture</a> ·
  <a href="#路线图--roadmap">路线图 / Roadmap</a>
</p>

---

## 为什么选择 SerialDebug？

| 解决什么 | 如何解决 |
|----------|----------|
| 嵌入式开发需要频繁切换电脑，环境搭建费时 | jlink 打包自包含 JRE，**解压即用**，零系统依赖 |
| 高速串口数据刷屏，工具卡死丢数据 | HEX/ASCII 双视图 + 批量刷新 + 1MB 自动清理 |
| 常用指令每次手动敲，容易出错 | **指令预设**一键填入，JSON 持久化 |
| 收发数据难以事后追溯 | **日志落盘**，支持 HEX/ASCII 格式和文件分割 |
| 需要同时监控多个串口设备 | **多标签会话**，每个标签独立收发和配置 |
| 传感器数值只看文本不够直观 | **波形图**实时绘制数据曲线 |

---

## 功能特性

| 功能 | 说明 |
|:----:|------|
| **串口配置** | 波特率 300–921600、数据位 5–8、停止位 1–2、校验位全支持 |
| **自动枚举** | 一键刷新系统可用串口列表 |
| **双模收发** | HEX / ASCII 双模式发送和接收 |
| **双视图** | Tab 切换 HEX 或 ASCII 格式，带时间戳和 TX/RX 标识 |
| **多标签会话** | 同时管理多个串口连接，每个标签独立配置、显示和发送 |
| **波形图** | 实时折线图，正则规则提取数值，多序列叠加，自动缩放 |
| **定时发送** | 可设间隔和次数，超时自动停止 |
| **行尾模式** | 支持 None / CR / LF / CRLF |
| **搜索过滤** | 实时搜索 + 过滤模式 + 大小写开关 |
| **发送辅助** | HEX/ASCII 切换、行尾模式、指令预设、文件发送 |
| **文件发送** | 文本逐行发送 / 二进制分块发送，可取消 |
| **统计** | RX/TX 字节数、帧数、实时速率 |
| **日志落盘** | 收发数据保存为文件，支持 HEX/ASCII 格式和自动分割 |
| **内存保护** | 显示区超 1MB 自动清理，防止内存溢出 |

---

## 快速开始 / Quick Start

### 环境要求

- **JDK 17+** (推荐 [Eclipse Temurin](https://adoptium.net/))
- **Maven 3.8+**

### 构建与运行

```bash
# 克隆
git clone https://github.com/your-org/serial-debug.git
cd serial-debug

# 编译
mvn clean compile

# 开发模式运行
mvn javafx:run -pl serial-debug-app

# 运行测试
mvn test
```

<details>
<summary><b>Windows 用户</b></summary>

```powershell
$env:JAVA_HOME = "D:\soft\java\jdk17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean compile
mvn javafx:run -pl serial-debug-app
```
</details>

---

## 项目结构

```
serial-debug/
├── serial-debug-core/                 # 核心模块（零 UI 依赖）
│   ├── serial/                        # 串口操作（Strategy 模式）
│   ├── parser/                        # 数据编解码（Strategy 模式）
│   ├── log/                           # 日志服务
│   ├── chart/                         # 波形图数据模型（DataExtractor, ChartDataBuffer）
│   └── util/                          # 工具类（RateCalculator）
├── serial-debug-ui/                   # JavaFX 界面模块
│   ├── controller/                    # 子控制器（Toolbar, Send, Display, Log 等）
│   ├── session/                       # 多标签会话管理
│   ├── chart/                         # 波形图 Canvas 渲染
│   └── preset/                        # 指令预设管理
├── serial-debug-protocol/             # 协议扩展 SPI（预留）
├── serial-debug-app/                  # 启动入口 + jlink 打包
└── docs/superpowers/                  # 设计文档 & 实现计划
```

### 模块依赖

```
serial-debug-app → serial-debug-ui → serial-debug-core
                                    → serial-debug-protocol (可选)
```

---

## 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| Language | Java 17 (LTS) | jlink 支持最佳 |
| GUI | JavaFX 21 | 跨平台桌面框架 |
| Serial | jSerialComm 2.11 | 纯 Java 串口库 |
| Icons | Ikonli Material Design 2 | 原生矢量图标 |
| Build | Maven 多模块 | 依赖版本集中管理 |
| Test | JUnit 5 + Mockito | 单元测试 |
| Serialization | Jackson 2.17 | JSON 持久化 |
| Package | jlink + jpackage | 自包含 JRE |

---

## 路线图

- [x] **v0.1** — 基础串口收发 + HEX/ASCII 双视图 + 指令预设 + 日志落盘
- [x] **v0.3 (M1)** — 定时发送、行尾模式、搜索过滤、文件发送、收发统计
- [x] **v0.3 (M2)** — SRP 分解、多标签会话、波形图、DataExtractor
- [ ] **v0.4** — CRC 助手、端口配置记忆、AT 指令伴侣、数据仪表盘
- [ ] **v1.0** — 脚本引擎、协议插件 SPI、固件升级、数据回放

---

## 部署

### Linux 离线部署

```bash
# 开发机打包
mvn clean package -DskipTests
jlink --module-path $JAVA_HOME/jmods --add-modules java.base,java.desktop,javafx.controls,javafx.fxml \
      --output serial-debug-runtime --launcher serial-debug=serial-debug.app/io.github.serialdebug.app.Launcher

# 服务器运行（无需 JDK）
./serial-debug-runtime/bin/serial-debug
```

### Linux 串口权限

```bash
sudo usermod -a -G dialout $USER  # 重新登录生效
```



## 贡献 / Contributing

欢迎贡献！请遵循以下步骤 / Contributions are welcome! Please follow these steps:

1. Fork 本仓库 / Fork this repository
2. 创建特性分支 / Create a feature branch (`git checkout -b feature/amazing-feature`)
3. 提交变更 / Commit changes (`git commit -m "feat: add amazing feature"`)
4. 推送分支 / Push to the branch (`git push origin feature/amazing-feature`)
5. 创建 Pull Request / Open a Pull Request

> 提交信息风格遵循 [Conventional Commits](https://www.conventionalcommits.org/) /
> Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/)

---

## 许可证 / License

[Apache License 2.0](LICENSE) — 自由使用、修改、分发，包括商业用途 /
Free to use, modify, and distribute, including commercial purposes.

---

## 致谢 / Acknowledgments

- [jSerialComm](https://fazecast.github.io/jSerialComm/) — 跨平台串口通信库
- [JavaFX](https://openjfx.io/) — 桌面 GUI 框架
- [Ikonli](https://github.com/kordamp/ikonli) — 图标库
- [ControlsFX](https://github.com/controlsfx/controlsfx) — JavaFX 增强控件

---

<p align="center">
  Made with ❤️ by the SerialDebug Community
</p>
