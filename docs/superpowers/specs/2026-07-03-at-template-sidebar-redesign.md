# AT 指令模板 — 嵌入 IO 标签页设计文档

- **功能**: 将 AT 指令模板从独立子标签页移至 IO 标签页（收发视图）的右侧边栏
- **版本**: v0.4 改进
- **日期**: 2026-07-03

---

## 1. 问题

当前 AT 伴侣是独立子标签页，与收发视图交互不友好：用户需要切换标签页来浏览模板、填入发送区、查看回复，操作流程割裂。AT 伴侣自身的回复显示区也与 IO 标签页的 TextArea 内容重复。

## 2. 解决方案

将 AT 指令模板以侧栏形式嵌入 IO 标签页发送区右侧，取消独立的 AT 伴侣标签页。

### 布局变更

```
VBox:
  TabPane (HEX / ASCII 显示区)
  ToolBar (Clear / Pause / [AT] / Search...)   ← 新增 AT 切换按钮
  Separator
  BorderPane:
    Center: sendArea (原有发送区)
    Right: AT 模板侧栏 (可折叠，ToggleButton 控制)
```

### AT 模板侧栏

- 宽 ~220px，默认隐藏
- 工具栏 `ToggleButton "AT"` 控制显示/隐藏
- 搜索框 + `ListView<AtCommand>` + 添加按钮
- 点击模板 → 指令填入发送区

### 工作流

```
用户展开侧栏 → 浏览/搜索模板 → 点击模板
  → 指令填入发送文本框 → 用户点击 Send 发送
  → 回复显示在 HEX/ASCII 区（原有流程，无需独立显示）
```

## 3. 数据流

去除独立的回复显示区后，AT 功能简化为「模板选择器」，数据流大幅简化：

```
JsonAtCommandService → ListView<AtCommand>
  → 用户点击 → SendController.setSendText() → 发送文本框填充
  → 用户发送 → serialService.sendData() → 回复 → DisplayController → TextArea
```

不再需要 `DisplayController.onResponseReceived` 回调。

## 4. 文件变更

### 删除

| 文件 | 原因 |
|---|---|
| `at/AtCompanionPanel.java` | AT 伴侣面板不再需要 |
| `at/AtConsumer.java` | 已删除（确认） |

### 修改

| 文件 | 改动 |
|---|---|
| `session/SessionTabContent.java` | createIOView() 内联 AT 侧栏；移除 AT 独立标签页；布局改为 BorderPane |
| `controller/DisplayController.java` | 回滚 onResponseReceived 字段、setter、flushBatch 调用 |

### 保留

`AtCommand.java`, `AtCommandService.java`, `JsonAtCommandService.java`, `SendController.setSendText()`

## 5. 交互细节

| 操作 | 结果 |
|---|---|
| 点击工具栏 `AT` 按钮 | 切换侧栏显示/隐藏 |
| 点击模板行 | 指令填入发送文本框 |
| 搜索输入 | FilteredList 过滤（按名称/指令） |
| 点击 `+ 添加` | 弹出对话框，添加后持久化 |
| 侧栏隐藏时 | 不影响发送区操作和显示 |