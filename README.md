# 碎银记

一款纯本地运行的记账应用：零账号、零云端、零框架依赖，账目数据永不离开你的设备。
内置端侧离线 AI——拍小票自动记账、自然语言对话记账、AI 理财报告，推理全程不发出任何网络请求。

> **作者**：计算机科学与技术专业在读。一个人写、一个人维护，没有广告与会员，数据只存在你的设备里。

## 它解决什么问题

记账应用的两个老大难：

1. **云端记账不放心**。账目是最私人的数据之一，主流 App 却普遍要求登录、上传、分析。碎银记把所有数据锁在浏览器 localStorage，删应用即删数据，无任何遥测与上报。
2. **手动记账坚持不下去**。碎银记把 OCR 和大模型直接装进手机（WASM 本地推理）：拍一张小票、或说一句「昨天打车花了 23」，AI 在本地完成识别、解析与理财报告生成——断网也能用。

## 为什么不是支付工具的附属功能

一个常见的疑问是「支付后没人会再手动记账，这类功能应该由支付工具或银行来做」。前半句完全正确，所以碎银记**不做手动输入**；后半句的前提值得商榷：

- **每一家支付机构只看得见自己平台的流水**：微信看不到支付宝、支付宝看不到微信、银行只看得见卡（看不到零钱/余额宝/花呗/食堂卡）。
- 用户真正的问题是「我这个月到底一共花了多少」——答案分散在 3~5 个互不通信的 App 里，**没有任何一家有动机和权限去汇总竞争对手的数据**。
- 所以能回答这个问题的，只有用户自己，或一个**中立、本地、不依赖平台授权的第三方工具**——这正是碎银记。

碎银记的做法是走平台官方出口（账单导出），在本机解析、汇总、决策，数据不出手机。产品上体现为**跨平台账本**与**年度账单**：一屏看到微信、支付宝、手动三类支出的合计与占比——这是任何单一支付 App 都给不出的数字。完整说明见 [跨平台记账的价值](跨平台记账的价值说明.md)。

## 功能一览

- **记账核心**：支出 / 收入、分类（可不选＝真未分类）、备注、日期补记、预算、储蓄目标
- **端侧离线 AI**：PP-OCRv6 tiny 小票识别 + Qwen2.5-0.5B-Instruct（Q4_K_M，469MB）对话记账与理财报告，wllama（llama.cpp WASM）推理
- **AI 三师会诊（多 Agent 财务看板）**：记账师 / 分析师 / 毒舌教练三位端侧顾问依次会诊同一账本，泳道看板呈现流水线状态与逐 token 流式输出，主持人自动合并为 3 条行动清单
- **统计可视化**：饼图、趋势、分类排行、时光轴、星图、万花筒、月度 AI 报告、记账人格测试
- **预算管家**：月末日均外推预测、今日安全可花额度、三色超支预警，与 AI 报告上下文联动
- **液态玻璃 UI**：SDF 折射 / 色散 / 菲涅尔边缘高光，启动时帧耗时基准实测，低端机自动降级
- **iOS 式视觉（构建 #32）**：靛紫主色 `#6259D6` + `#F2F2F7` 分组背景 + 实心白卡 + 淡紫描边；深色用冷黑底 `#0C0C12` / 紫调卡片 `#1A1A21` / 提亮主色 `#8B82F0`
- **手感细节**：果冻 Dock 滑块、支出/收入滑块 1:1 跟手拖动、横滑方向锁定（弧度手势容错）、过中线触觉反馈
- **其他**：深浅色自动秒切、NTP 校时碎银时钟、CSV 导入导出、头像裁剪
- **零手动输入四条通路（构建 #26）**：微信 / 支付宝官方账单 CSV 一键导入（自动适配新旧列名、UTF-8/GBK 编码、按交易单号去重）· 截图 OCR · 剪贴板支付通知识别 · 固定开销一键记账
- **认人记账（构建 #27）**：商户分类记忆——某商户改过一次分类，之后导入/识别自动套用（上限 300 个）
- **快捷记账支持收支双向（构建 #28）**：固定开销可设为收入或支出，用颜色与正负号区分；截图识别自动判断收支方向并提取交易时间
- **跨平台账本（构建 #30）**：微信 / 支付宝 / 手动三类支出各自的金额、笔数与占比，一屏合成一本总账
- **年度账单（构建 #30）**：本年支出/收入/结余、日均、Top 分类与商户、最大一笔、最费钱月份、平台构成，可复制分享

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

### 依赖清单

| 复现层级 | 依赖 | 版本要求 | 说明 |
| --- | --- | --- | --- |
| 浏览器运行 | 现代浏览器 | Chrome 90+ / Edge 90+ / Safari 15+ 等 | **零第三方依赖**：无 npm、无 node_modules、无构建步骤 |
| APK 构建 | JDK | **17**（勿用 JDK 25） | JDK 25 与 build-tools 34 的 d8 不兼容（实测踩坑，见 CHANGELOG v2.x） |
| APK 构建 | Android SDK build-tools | 34.0.0 | 需要 aapt2 / apksigner |
| APK 构建 | Android SDK platforms | android-34 | javac 编译 android.jar |
| APK 构建 | adb | platform-tools | 仅安装调试用 |
| 可选：AI 运行时重组 | Node.js + esbuild | Node 18+ | `node tools/build-offline-ai.mjs` |
| 可选：端侧模型 | model.gguf（469MB）、PP-OCR 模型 | — | 体积大不入库，放置路径见 `android/app/src/main/assets/offline-ai/README.md` |

### 环境变量模板

浏览器方式（方式一）**无需任何环境变量**；APK 构建需要以下三项。复制为 `.env` 或直接 export：

```bash
# ===== APK 构建环境变量模板 =====
export ANDROID_HOME=$HOME/Android/Sdk     # Android SDK 根目录（含 build-tools/34.0.0）
export JAVA_HOME=/usr/lib/jvm/jdk-17      # JDK 17 路径（勿用 JDK 25：与 build-tools 34 d8 不兼容）
export KS_PASS=你的keystore密码            # 签名 keystore 密码（自行 keytool 生成，不入库）

# 可选：adb 不在 PATH 时
# export PATH=$ANDROID_HOME/platform-tools:$PATH

# 无默认值，缺失时 build_apk.sh 会给出明确报错提示
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

## 技术选择说明

本项目**有意选择零框架单文件 + Android WebView 壳**，这是主动的设计决策：

| 维度 | 常见做法 | 本项目的选择与理由 |
| --- | --- | --- |
| 10 分钟复现 | 需 npm/pnpm 安装依赖、构建打包 | 单文件零依赖：`git clone` + 一条 `python3 -m http.server` 即跑，双击 index.html 也能用 |
| 移动端交付 | Flutter / React Native 或 Web 套壳 | 直接复用系统 WebView：壳层仅 3 个 Java 文件，无框架运行时开销，minSdk 21 老设备可用 |
| 性能与兼容 | 依赖框架版本与 polyfill 策略 | 代码里逐一兜底老 WebView 缺陷（无 PointerEvent 的 touch 适配、清除可选链 SyntaxError） |
| 端侧 AI | 通常接云端大模型 API | 0.5B 模型 + OCR 直接打包进 APK，WASM 本地推理，零网络请求 |

一句话：**框架解决的是规模化协作的工程问题，本项目要解决的是「数据不出手机的可信记账」；在后一个目标下，零依赖单文件反而是最优解。**

## 合规声明

- 所有数据仅存于设备本地（localStorage），应用不请求任何云端接口、不采集任何用户数据
- 应用内无广告、无内购、无打赏 / 收款等任何商业内容
- 签名 keystore 与密码不进入仓库，由构建者自行提供

## 版本历史

见 [CHANGELOG.md](CHANGELOG.md)——从 v1.0 基础记账到 v3.0（v3.0 新增预算管家组件、完成 28 项全面体检修复、滑块拖拽三轮纵深防御与运行时自诊断、统计组件瘦身与碎银诗歌默认启用、建议列表可点击跳转，构建 #22 再把滑块内核重写回单状态机并让端侧大模型改为纯按需加载，构建 #23 针对真机反馈重做端侧 AI——高频问题本地秒答、截图识别加规则预解析命中即跳过 491MB 模型，构建 #24 让三师会诊改为纯按需——点开页面不自动跑、点「开始会诊」才加载模型、模型常驻内存后续秒开，构建 #25 按朋友反馈加删除二次确认与「快捷记账」小框——固定开销一按记一笔，构建 #26 新增微信/支付宝官方账单 CSV 导入（自适应新旧列名与 GBK 编码、按交易单号去重）与剪贴板支付通知识别，构建 #27 加商户分类记忆与空账本导入入口，构建 #28 让快捷记账支持收入并让截图识别认收支方向/时间、会诊结果账本未变时 0 秒复用，构建 #29 审计修掉退款收入漏记、收入挂支出分类、弹窗状态残留三处问题，构建 #30 新增跨平台账本与年度账单，并修正真机复制兼容，构建 #31 视觉重做「雾霭紫」——主色降饱和、背景收敛为 iOS 分组灰、卡片改实心白、深色对齐 iOS 深色体系，构建 #32 加强为「紫曜」——主色加深、淡紫描边统一全局、深色模式重做为冷黑底与紫调卡片）的每一轮迭代：每处 bug 的现象、根因、修复方案与实测结论。
