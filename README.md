# AstrBot Android Native
一个面向 Android 的 AstrBot 原生项目，用于在移动端承载 QQ Bot 管理、模型配置、Persona 管理、对话上下文、运行时控制和 NapCat 本地桥接能力。

这个项目不是从零独立设计出来的移动端新产品，而是基于两个桌面端项目做“移动端减法”后的原生化整合：
- `https://github.com/AstrBotDevs/AstrBot`
- `https://github.com/NapNeko/NapCatQQ`

目标不是把桌面端的 Python 后端、Node WebUI、插件生态和协议栈原封不动搬进 Android，而是提炼其中适合手机场景的核心管理能力，并以 Kotlin + Compose + Android 前台服务的方式重建。

## 为什么要做 AstrBot-Android
刷到AstrBot的宣传视频，我寻思闲置设备可以拿来部署一个玩玩，但看了一圈下来发现我只有闲置的安卓手机。
通过AstrBot官方文档我找到了[AstrBot-Android-App](https://github.com/zz6zz666/AstrBot-Android-App),但奈何我的老手机比较掘，死活部署不上，所以打算写一个能兼容我老手机的安卓工程
### 通过AstrBot-Android,你可以：
- [✔] 完全免费开源
- [✔] 实现单台安卓设备一键部署AstrBot
- [✔] 将QQ作为一个AI节点，实现基础的对话
- [✔] 拥有专门为移动端设计的UI/UX体验
- [✔] 部分Android版独特的功能（可能在桌面端插件市场中已经集成）
- [×] QQ以外的平台支持
- [×] 完整的AstrBot桌面端体验
- [×] 定期更新
ps:有空我会一直更新的

## 首次使用
1. 在右侧Realese（移动端在最下方）处下载最新的apk
2. 点击运行状态悬浮窗的启动，等待资产下载完成
3. 打开我的-QQ账号，点击去登录，首次使用请使用扫码登录
4. 配置模型api，配置bot配置，bot绑定模型和配置后即可进行QQ对话

具体教程：https://b23.tv/AdJrQ77 更新在分p里

## 目前已经支持的功能：
- QQ登录和快捷登录
- 大模型对话：人格定义，持久化数据，语音转文字，文字转语音，图片转述，流式输出，现实世界时间感知
- 模型提供商支持：

| chat模型 | STT模型 | TTS模型 |
|---|---|---|
| OpenAi Compatible | Whisper API | OpenAi TTS |
| Deepseek | Xinference STT | Alibaba Bailian TTS（包括 Qwen） |
| Gemini | Alibaba Bailian STT（包括 Qwen） | MiniMax TTS |
| Ollama | Sherpa ONNX STT（内置支持） | Sherpa ONNX TTS（内置支持） |
| Qwen | — | — |
| Zhipu | — | — |
| xAI | — | — |
- 中英语言切换
- 深色模式切换

## 准备更新的功能：
- [ ] 云端自定义声音克隆
- [ ] 知识库支持
- [ ] 上下文策略
- [ ] 网页搜索能力
- [ ] 白名单以及回复速度，长度自定义

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

## 已知问题
1. 
## 差异说明

`AstrBot-Android` 基于 `AstrBot-master` 与 `NapCatQQ-main` 的核心能力思路构建，但并不是两者的 Android 直接移植版。

它保留了 AstrBot 在 Bot、Provider、Persona、聊天、日志和设置上的核心管理体验，同时吸收了 NapCat 在 QQ 登录、运行时控制与 OneBot 桥接上的关键能力，并通过 Kotlin + Jetpack Compose + Android 前台服务的方式，重构为一个面向手机端的原生控制台与本地桥接运行层。

因此，这个项目的目标不是复刻完整桌面端，而是在 Android 场景下提供一个更聚焦、更本地化、更适合移动设备使用的 AstrBot + NapCat 整合方案。


## 致谢

感谢以下开源项目为本 App 提供基础支持：
- [NapCatQQ](https://github.com/NapNeko/NapCatQQ)：高效稳定的 QQ 协议适配器
- [Astrbot](https://github.com/AstrBotDevs/AstrBot)：强大的一站式 Agentic 个人和群聊助手
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)：轻量化的安卓端侧TTS框架

## 许可证说明
本项目采用AGPL3.0
