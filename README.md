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
  <a href="#路线图--roadmap">路线图 / Roadmap</a> ·
  <a href="#贡献--contributing">贡献 / Contributing</a>
</p>

---

## 为什么选择 SerialDebug？ / Why SerialDebug?

<p align="center">
<table>
<tr><th>解决什么 / Problem</th><th>如何解决 / Solution</th></tr>
<tr><td>嵌入式开发需要频繁切换电脑，环境搭建费时</td><td>jlink 打包自包含 JRE，<b>解压即用</b>，零系统依赖 / Self-contained JRE via jlink, zero system dependencies</td></tr>
<tr><td>Linux 服务器无桌面，主流调试工具装不上</td><td>VNC 下流畅运行的纯 Java 桌面客户端 / Pure Java desktop client that runs smoothly under VNC</td></tr>
<tr><td>高速串口数据刷屏，工具卡死丢数据</td><td>HEX/ASCII 双视图 + 1MB 自动清理，守护内存上限 / Dual-view with auto-clear at 1MB to protect memory</td></tr>
<tr><td>常用指令每次手动敲，容易出错</td><td><b>指令预设</b>一键填入，JSON 持久化 / Command presets with one-click fill, persisted as JSON</td></tr>
<tr><td>收发数据难以事后追溯</td><td><b>日志落盘</b>，支持 HEX/ASCII 格式和文件分割 / Log to disk in HEX/ASCII with file rotation</td></tr>
</table>
</p>

---

## 功能特性 / Features

### 当前版本 v0.1 / Current Release

| 图标 | 功能 / Feature | 说明 / Description |
|:---:|---|---|
| 🔌 | **串口配置 / Port Config** | 波特率 300–921600、数据位 5–8、停止位 1–2、校验位全支持 |
| 🔄 | **自动枚举 / Auto Detect** | 一键刷新系统可用串口列表 |
| 📡 | **双模收发 / Dual Mode** | HEX / ASCII 双模式发送和接收 |
| 👁️ | **双视图 / Dual View** | Tab 切换 HEX 或 ASCII 格式，带时间戳和 TX/RX 标识 |
| ⌨️ | **发送辅助 / Send Assist** | HEX/ASCII 切换、自动追加 `\r\n`、指令预设一键填入 |
| 📊 **统计 / Statistics** | 连接状态、收发字节数实时统计 |
| 💾 | **日志落盘 / Logging** | 收发数据保存为文件，支持 HEX/ASCII 格式 |
| 🛡️ | **内存保护 / Memory Guard** | 显示区超 1MB 自动清理，防止内存溢出 |

---

## 快速开始 / Quick Start

### 环境要求 / Prerequisites

- **JDK 17+** (推荐 [Eclipse Temurin](https://adoptium.net/) 或 [Microsoft Build of OpenJDK](https://microsoft.com/openjdk))
- **Maven 3.8+**

### 构建与运行 / Build & Run

```bash
# 克隆 / Clone
git clone https://github.com/your-org/serial-debug.git
cd serial-debug

# 编译 / Compile
mvn clean compile

# 开发模式运行 / Run in development mode
mvn javafx:run -pl serial-debug-app

# 运行测试 / Run tests
mvn test
```

<details>
<summary><b>🖥️ Windows 用户 / Windows Users</b></summary>

```powershell
$env:JAVA_HOME = "D:\soft\java\jdk17"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn clean compile
mvn javafx:run -pl serial-debug-app
```

</details>

---

## 项目结构 / Architecture

```
serial-debug/
├── serial-debug-core/               # 核心模块（零 UI 依赖 / Zero UI dependencies）
│   ├── serial/                      # 串口操作（Strategy 模式）
│   │   ├── SerialConfig.java            # 参数配置值对象
│   │   ├── SerialService.java           # 串口操作接口
│   │   └── JSerialCommService.java      # jSerialComm 实现
│   ├── parser/                      # 数据编解码（Strategy 模式）
│   │   ├── DataParser.java              # 编解码接口
│   │   ├── HexParser.java               # HEX 编解码（Java 17 HexFormat）
│   │   └── AsciiParser.java             # ASCII 编解码
│   └── log/                         # 日志服务
│       ├── LogService.java              # 日志接口
│       └── FileLogService.java          # 文件日志实现（含分割）
├── serial-debug-ui/                 # JavaFX 界面模块
│   ├── controller/MainController.java   # 主控制器（单控制器模式）
│   ├── preset/                      # 指令预设管理
│   │   ├── Preset.java                  # 预设模型
│   │   ├── PresetService.java           # 持久化接口
│   │   └── JsonPresetService.java       # JSON 文件实现
│   ├── main-view.fxml                   # FXML 布局
│   └── style.css                        # 样式表
├── serial-debug-protocol/           # 协议扩展 SPI（预留 / Placeholder）
├── serial-debug-app/                # 启动入口 + jlink 打包
│   └── Launcher.java
└── docs/superpowers/                # 设计文档 & 实现计划
```

### 模块依赖 / Module Dependencies

```
serial-debug-app → serial-debug-ui → serial-debug-core
                                    → serial-debug-protocol (可选 / optional)
```

### 设计模式 / Design Patterns

| 模式 / Pattern | 应用 / Application |
|---|---|
| **Strategy** | `DataParser` (Hex/Ascii)、`SerialService` (jSerialComm)、`PresetService` (JSON) |
| **Interface Segregation** | UI 层仅依赖 `SerialService` 接口，不耦合实现 |
| **Observer** | 数据接收通过 `Consumer<byte[]>` 回调，UI 用 `Platform.runLater` 同步 |

---

## 技术栈 / Tech Stack

| 组件 / Component | 选型 / Choice | 说明 / Note |
|---|---|---|
| Language | Java 17 (LTS) | jlink 支持最佳 |
| GUI | JavaFX 21 | 跨平台桌面框架 |
| Serial | jSerialComm 2.11 | 纯 Java 串口库 |
| Icons | Ikonli Material Design 2 | 原生矢量图标 |
| Build | Maven 多模块 | 依赖版本集中管理 |
| Test | JUnit 5 + Mockito | 单元测试 + Mock |
| Serialization | Jackson 2.17 | JSON 持久化 |
| Package | jlink + jpackage | 自包含 JRE |

---

## 路线图 / Roadmap

- [x] **v0.1** — 基础串口收发 + HEX/ASCII 双视图 + 指令预设 + 日志落盘
- [ ] **v0.2** — 波形图显示 + 高级过滤
- [ ] **v0.3** — 配置记忆 + 文件发送
- [ ] **v0.4** — 数据回放 + SPI 协议扩展
- [ ] **v1.0** — 国际化 + 主题切换 + jlink 打包发布

---

## 部署 / Deployment

### Linux 离线部署 / Offline Linux Deployment

```bash
# 开发机打包 / Build on dev machine
mvn clean package -DskipTests
jlink --module-path $JAVA_HOME/jmods --add-modules java.base,java.desktop,javafx.controls,javafx.fxml \
      --output serial-debug-runtime --launcher serial-debug=serial-debug.app/io.github.serialdebug.app.Launcher

# 服务器运行（无需 JDK）/ Run on server without JDK
./serial-debug-runtime/bin/serial-debug
```

### Linux 串口权限 / Serial Port Permissions

```bash
sudo usermod -a -G dialout $USER  # 重新登录生效 / re-login to apply
```

---

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
