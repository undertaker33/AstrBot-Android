# AstrBot Android Native

一个面向 Android 的 AstrBot 原生控制台项目，用于在移动端承载 QQ Bot 管理、模型配置、Persona 管理、对话上下文、运行时控制和 NapCat 本地桥接能力。

这个项目不是从零独立设计出来的移动端新产品，而是基于两个桌面端项目做“移动端减法”后的原生化整合：

- `C:\Users\93445\Desktop\Astrbot\AstrBot-master`
- `C:\Users\93445\Desktop\Astrbot\NapCatQQ-main`

目标不是把桌面端的 Python 后端、Node WebUI、插件生态和协议栈原封不动搬进 Android，而是提炼其中适合手机场景的核心管理能力，并以 Kotlin + Compose + Android 前台服务的方式重建。

## 项目来源

### 1. AstrBot-master 提供的能力来源

本项目吸收了 AstrBot 桌面端/服务端中的以下产品语义：

- Bot 管理
- Provider 管理
- Persona 管理
- 对话会话与上下文管理
- 日志中心
- 运行时控制台的产品边界

但没有直接迁移以下桌面端实现：

- Python 主程序
- Dashboard WebUI 后端
- 多平台 Adapter 体系
- 插件热安装与完整插件生态
- MCP / Agent Sandbox / 技能执行运行时
- 完整知识库和向量检索链路

### 2. NapCatQQ-main 提供的能力来源

本项目吸收了 NapCat 桌面端/WebUI 中的以下产品语义：

- QQ 登录流程
- NapCat 配置与 per-account 配置思路
- OneBot 反向 WebSocket 桥接思路
- Runtime 状态展示
- 健康检查、启动、停止、日志查看的管理方式

但没有直接迁移以下桌面端实现：

- Node/Express WebUI 后端
- React WebUI 前端
- 完整终端模拟器与桌面系统管理页面
- 全量 NapCat WebUI 配置页
- 桌面端高级设备指纹管理页面

## 这个 Android 项目当前已经实现了什么

以下内容已经在当前 Android 原生工程中落地，不是规划项。

### 原生工程基础

- 单 `app` 模块 Android 工程
- Kotlin + Jetpack Compose
- Navigation Compose
- ViewModel 状态管理
- DataStore 本地轻量设置
- Room 持久化 Bot 数据
- Android 前台服务管理运行时
- Java 17 / SDK 34 工程配置

### 应用主界面与导航

已经存在完整的原生 App Shell，包含底部主导航和若干二级页面：

- Bots
- Personas
- Chat
- Logs
- Me
- QQ Account Center
- QQ Login
- Settings Hub
- Runtime Settings
- Provider / Models 页面

### Bot 管理

已经实现：

- QQ Bot 基础资料模型
- 多 Bot 列表与选择
- 默认 Bot 初始化
- Bot 的本地持久化存储
- 默认 Provider / Persona 绑定字段
- Trigger Words
- Auto Reply 开关
- Bridge endpoint 等配置字段

当前状态：

- Bot 数据由 Room 持久化
- `BotRepository` 已完成数据库初始化、选中态维护和增删改逻辑

### Provider 管理

已经实现：

- ProviderProfile 数据模型
- ProviderType 枚举
- ProviderCapability 枚举
- Provider 列表、创建、修改、删除、启停
- 预置 OpenAI-compatible 和 DeepSeek 示例 Provider
- 聊天模型拉取接口 `/models`

当前支持的真实调用能力：

- OpenAI-compatible
- DeepSeek

已预留但未真正打通的类型：

- Gemini
- Anthropic
- Custom
- TTS / ASR 相关 Provider 能力

### Persona 管理

已经实现基础版 Persona 管理：

- PersonaProfile 数据模型
- system prompt
- enabled tools 字段
- 默认 provider 绑定字段
- max context messages
- 启用禁用

当前仍属于移动端精简版，尚未完整覆盖桌面端的：

- 文件夹树
- 排序管理
- begin dialogs
- custom error message
- skills 的完整管理语义

### Chat 与对话上下文

已经实现：

- 本地聊天主界面
- 会话列表与选中态
- 创建/切换/删除会话
- 用户消息写入本地上下文
- 调用 OpenAI-compatible / DeepSeek 接口完成回复
- assistant 消息回写本地上下文
- 根据首条消息自动重命名会话
- 当前 Bot / Provider / Persona 的绑定逻辑

当前状态：

- `ConversationRepository` 已具备会话、消息、上下文预览等基础能力
- `ChatViewModel` 已形成真实闭环，不是纯 UI mock

### QQ 登录

已经实现 NapCat 登录状态管理的原生化骨架，并且覆盖了桌面端登录流程中的关键分支：

- 检查桥接是否可用
- 刷新登录状态
- 刷新二维码
- 快速登录账号保存与读取
- 快速登录
- 密码登录
- 验证码登录
- 新设备登录
- 登录挑战状态保留与合并
- SharedPreferences 保存最近快速登录账号

当前状态：

- `NapCatLoginRepository` 已具备较完整的状态机
- `QQLoginViewModel` 和登录页面已具备原生交互基础

### NapCat Runtime 控制

已经实现：

- 前台服务 `ContainerBridgeService`
- 运行时控制器 `ContainerBridgeController`
- 启动 / 停止 / 检查 三种控制动作
- 运行进度读取
- 健康检查轮询
- 通知栏前台服务状态更新
- 运行日志采集
- 启动超时与启动停滞判断

当前状态：

- 运行时并非“页面按钮假动作”，而是已经有命令执行、状态同步、进度监视和日志 tail 的真实逻辑

### Runtime 安装与资产准备

已经存在运行时资源打包和安装骨架：

- `ContainerRuntimeInstaller`
- `RootfsExtractor`
- `RootfsOverlayExtractor`
- `DebPayloadExtractor`
- `app/src/main/assets/runtime/` 下的脚本和运行时资产
- `jniLibs/arm64-v8a` 下的桥接相关 native 库
- Gradle 侧的 filtered-assets 预处理逻辑

这部分说明项目已经不是简单 UI 壳，而是在为本地 NapCat 运行时落地做准备。

### OneBot 反向 WebSocket

已经实现：

- 本地 `ws://127.0.0.1:6199/ws` 反向 WebSocket 服务
- 基础 token 校验
- QQ 私聊/群聊消息事件解析
- at 自己与 trigger word 触发逻辑
- message_id 去重
- 收到 QQ 消息后自动选择 Provider / Persona / Bot
- 调用模型生成回复
- 再通过 `send_msg` action 回发

当前状态：

- `OneBotBridgeServer` 已经是项目最关键的闭环能力之一
- 这意味着 Android 端已具备一个“精简版 AstrBot 编排层”

### 日志中心

已经实现：

- Runtime 日志集中写入
- 页面查看日志
- 部分上下文预览能力
- 运行时状态悬浮层展示

### 设置与运行时配置

已经实现基础设置仓库：

- QQ 开关
- NapCat runtime 开关
- 首选聊天 Provider

并已具备：

- Runtime 设置页
- Settings Hub 页面
- 运行时状态悬浮面板

## 当前版本可视为“已迁移”的桌面端能力

如果把桌面端两个项目做能力切片，那么当前 Android 项目已经完成了以下迁移：

### 来自 AstrBot 的已迁移能力

- QQ-only Bot 管理
- Provider 管理基础版
- Persona 管理基础版
- 本地聊天与上下文管理
- 对话请求到模型接口的闭环
- 日志中心基础版

### 来自 NapCat 的已迁移能力

- QQ 登录主流程基础版
- 运行时启动/停止/检查
- 健康检查与日志读取
- 本地 OneBot reverse WebSocket
- Android 本地 runtime 资产安装骨架

## 当前还没有完整迁移的能力

以下能力在桌面端存在，但 Android 端目前还没有完整落地，或只做了预留：

### AstrBot 侧未完整迁移

- 多平台适配器体系
- 完整 Persona 文件夹树与排序体系
- Conversation 的高级筛选、分页、导出能力
- 插件市场、插件安装、插件更新、插件热重载
- Skills/MCP/Agent Sandbox
- 完整知识库、向量库与检索链路
- Web ChatUI
- Dashboard 后端接口体系

### NapCat 侧未完整迁移

- 全量 NapCat Config 页面
- per-UIN 配置的完整编辑器
- 设备 GUID / Linux machine-id / MAC 等高级管理界面
- 完整 WebUI backend 路由语义
- 终端管理器与桌面运维页面
- 更多 OneBot 配置项和高级协议管理能力

## 当前项目的产品边界

### Android 原生层负责

- 移动端 UI
- Bot / Provider / Persona / Conversation 管理
- QQ 登录交互
- Runtime 控制面板
- OneBot 本地桥接
- 模型调用与自动回复最小闭环

### Runtime / 容器层负责

- NapCat runtime
- QQ 协议与协议侧行为
- 运行时脚本与安装
- 容器/本地命令执行
- 运行健康状态输出

这条边界是项目的核心设计前提。不要把协议细节和桌面端 WebUI 逻辑直接塞回业务层。

## 当前工程结构概览

```text
app/
  src/main/java/com/astrbot/android/
    data/                 本地仓库与服务
    data/db/              Room 数据库
    model/                数据模型
    runtime/              运行时桥接、安装、日志、OneBot
    ui/                   Compose App Shell
    ui/screen/            页面
    ui/viewmodel/         页面状态
    ui/theme/             主题
  src/main/assets/runtime/
    assets/               rootfs / deb / 安装资产
    scripts/              runtime 启停与状态脚本
  src/main/jniLibs/
    arm64-v8a/            proot/busybox/loader 等 native 资产
```

## 构建要求

- Android Studio Ladybug 或更新版本
- JDK 17+
- Android SDK Platform 34
- Android Build Tools 与 AGP 8.5+ 兼容

## 已知现状与限制

1. 当前项目仍处于“移动端原生化迁移”阶段，不是桌面端全量功能对等版本。
2. 目前最成熟的是：
- 原生 UI 骨架
- Bot / Provider / Chat / Runtime 主链路
- QQ 登录状态管理骨架
- OneBot 自动回复闭环

3. 目前仍需继续补强的是：
- Persona 高级管理
- Conversation 高级管理
- NapCat 配置管理
- 插件 / 知识库入口级功能
- 更完整的本地持久化

## 后续建议路线

### Phase A

- 将 Persona 补齐 begin dialogs、skills、custom error message、folder/tree
- 将 Conversation 改造为 Room 持久化
- 为日志页补齐更清晰的分类视图

### Phase B

- 完善 NapCatConfig / per-UIN 配置模型
- 将 QQ 登录页与 runtime 健康状态做更强联动
- 强化 Runtime 页面中的安装、恢复、错误诊断信息

### Phase C

- 以“精简版能力”方式补充 Plugin / Knowledge Base 入口
- 做可控的导入导出能力
- 明确哪些功能长期保持移动端裁剪，不追求桌面端对等

## 总结

这个项目可以理解为：

“从 AstrBot-master 中提取管理层，从 NapCatQQ-main 中提取 QQ/NapCat 运行时管理语义，再用 Android 原生方式重建的一套移动端 AstrBot 控制台。”

它当前已经不是一个空壳，而是已经完成了以下最关键的事情：

- 原生 UI 与导航建立完成
- 本地 Bot / Provider / Persona / Chat 主链路建立完成
- NapCat runtime 控制链路建立完成
- QQ 登录状态链路建立完成
- OneBot 自动回复闭环建立完成

剩余工作主要是继续把桌面端成熟能力做“有判断的迁移”，而不是继续堆叠桌面端实现本身。
