# 碎银记

一款纯本地运行的记账应用：零账号、零云端、零框架依赖，账目数据永不离开你的设备。
内置端侧离线 AI——拍小票自动记账、自然语言对话记账、AI 理财报告，推理全程不发出任何网络请求。

> **作者**：计算机科学与技术专业大一在读。
> **开发方式**：本项目由 Trae（AI IDE）与 DeepSeek 大模型协同打磨完成——从需求梳理、方案设计到逐轮代码评审与真机回归，全部以 AI 结对方式迭代。开发过程的 Prompt 快照与「提问 → AI 建议 → 引导修复」完整链记录见 [PROMPT_HISTORY.md](PROMPT_HISTORY.md)。

## 它解决什么问题

记账应用的两个老大难：

1. **云端记账不放心**。账目是最私人的数据之一，主流 App 却普遍要求登录、上传、分析。碎银记把所有数据锁在浏览器 localStorage，删应用即删数据，无任何遥测与上报。
2. **手动记账坚持不下去**。碎银记把 OCR 和大模型直接装进手机（WASM 本地推理）：拍一张小票、或说一句「昨天打车花了 23」，AI 在本地完成识别、解析与理财报告生成——断网也能用。

## 功能一览

- **记账核心**：支出 / 收入、分类（可不选＝真未分类）、备注、日期补记、预算、储蓄目标
- **端侧离线 AI**：PP-OCRv6 tiny 小票识别 + Qwen2.5-0.5B-Instruct（Q4_K_M，469MB）对话记账与理财报告，wllama（llama.cpp WASM）推理
- **统计可视化**：饼图、趋势、分类排行、时光轴、星图、万花筒、月度 AI 报告、记账人格测试
- **液态玻璃 UI**：SDF 折射 / 色散 / 菲涅尔边缘高光，启动时帧耗时基准实测，低端机自动降级
- **手感细节**：果冻 Dock 滑块、支出/收入滑块 1:1 跟手拖动、横滑方向锁定（弧度手势容错）、过中线触觉反馈
- **其他**：深浅色自动秒切、NTP 校时碎银时钟、CSV 导入导出、头像裁剪

## 10 分钟复现指南

### 方式一：浏览器直接运行（约 1 分钟）

```bash
git clone <本仓库地址>
cd expense-tracker
python3 -m http.server 8080
# 浏览器打开 http://localhost:8080
```

无需安装任何依赖。端侧 AI 因 COOP/COEP 隔离要求需经 http 访问，其余功能直接双击 index.html 也可运行。

### 方式二：构建 Android APK（约 10 分钟）

环境要求：JDK 17+、Android SDK（build-tools 34.0.0 与 platforms android-34）。无需 Gradle——构建链（aapt2 → javac → d8 → apksigner）全部由一个 bash 脚本驱动：

```bash
# 1. 生成自己的签名 keystore（不入库；已有可跳过）
keytool -genkeypair -v -keystore android/release.keystore \
  -alias suyinji -keyalg RSA -keysize 2048 -validity 10000

# 2. 设置环境变量
export ANDROID_HOME=$HOME/Android/Sdk     # 指向 Android SDK
export JAVA_HOME=/usr/lib/jvm/jdk-17      # 指向 JDK 17+
export KS_PASS=你的keystore密码

# 3. 一键构建（自动同步页面 → 编译资源 → javac → d8 → 签名）
./build_apk.sh

# 4. 安装
adb install xiaozhangben.apk
```

### （可选）重新组装端侧 AI 运行时

`android/app/src/main/assets/offline-ai/` 已包含组装好的运行时（单 ESM bundle）。如需自行重新打包：

```bash
node tools/build-offline-ai.mjs   # 依赖 esbuild
```

OCR / LLM 模型文件体积较大，不入 git 仓库（.gitignore 已排除），放置说明见 `android/app/src/main/assets/offline-ai/README.md`。

## 技术架构

```
┌─ Android WebView 壳（com.xiaozhangben.app）
│    手写构建链 aapt2 → javac → d8 → apksigner，不用 Gradle，构建只跑一个 bash 脚本
│    壳层只做三件事：相册/相机本地文件桥、生命周期省电（后台暂停定时器与渲染）、返回键接管
├─ 单文件前端 index.html（约 300KB，5500+ 行）
│    零框架、零构建、零外部请求；localStorage 持久化 + 数据版本号渲染缓存
└─ 端侧 AI 运行时（assets/offline-ai/，单 ESM bundle）
     PP-OCR（OCR）+ ONNX Runtime Web + wllama（llama.cpp WASM）
     本地 http 源 + COOP/COEP 隔离，推理全程零网络请求，模型分块 Range 读取不整包驻留内存
```

## 与评分表对照

| 评分维度 | 本作品的对位 |
| --- | --- |
| 过程可复现（25%） | 本页 10 分钟复现指南：浏览器 1 条命令起跑，APK 一键脚本无需 Gradle；[CHANGELOG.md](CHANGELOG.md) 逐版本记录每轮修改的根因分析与浏览器实测结论；[PROMPT_HISTORY.md](PROMPT_HISTORY.md) 记录 4 条「提问 → AI 建议 → 引导修复」完整链 |
| 解题有效性（25%） | 直指记账 App 两大真实痛点（隐私不放心、懒得记账）；每个版本更新均附浏览器实测 PASS 记录（见 CHANGELOG） |
| 创新意图（20%） | 端侧 WASM 大模型 + OCR 全离线记账（469MB 模型不上云）；液态玻璃 UI 带能力探测与帧耗时基准自动降级；离线优先的意图解析（关键词 → 本地 LLM 兜底） |
| 工程质量（15%） | 单文件但强结构化（模块化 App 对象 + 分区注释）；兼容性硬约束逐一落实（minSdk 21、老 WebView 无 PointerEvent / 不支持可选链均有兜底）；构建参数化、签名材料不入库 |
| 用户体验（15%） | 1:1 跟手滑块、触觉反馈、弧度手势容错、深浅色秒切、低端机帧率保护（毛玻璃降级 / 合成层隔离 / rAF 合帧） |

## 合规声明

- 所有数据仅存于设备本地（localStorage），应用不请求任何云端接口、不采集任何用户数据
- 应用内无广告、无内购、无打赏 / 收款等任何商业内容
- 签名 keystore 与密码不进入仓库，由构建者自行提供

## 版本历史

见 [CHANGELOG.md](CHANGELOG.md)——从 v1.0 基础记账到 v2.9 的每一轮迭代：每处 bug 的现象、根因、修复方案与浏览器实测结论。
