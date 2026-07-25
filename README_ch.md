# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

Android 质粒图谱查看器，集成 AI 分析、酶切模拟和序列比对功能。基于 Kotlin & Jetpack Compose 构建。

## 功能特性

### 🧬 质粒图谱（v2.2）
- 环状质粒渲染，支持双指缩放和平移
- **重叠特征自动分层** — 重叠的特征自动分配到同心环上，互不遮挡
- **单路径特征弧线** — 按类型着色，正向/反向箭头集成在弧线端部，一条路径画到底
- 箭头尖精确落在特征边界，弧线自动后退避免与箭头重叠
- 碱基对刻度和自动适配位置的标签，永不溢出屏幕
- 点击任意特征弹出统一详情对话框（四屏通用，支持 **Ask AI**）
- **复位按钮** 🏠 一键恢复默认缩放和位置

### 📄 序列浏览器（v2.2）
- 完整质粒序列展示，**等宽字体**保证字符严格对齐
- **基于片段（Seg）的特征轨道** — 跨起点特征自动拆为两段，正确分配轨道层
- **箭头只出现在特征真正结束的那一行** — 跨多行特征仅在终结行显示箭头
- **层感知点击** — 重叠特征点各自轨道层弹出不同详情；点序列行时若多位点重合弹出选择列表
- **缓存搜索** — 搜索结果跨行高亮，在绘制循环外预计算
- 滑块调节字体大小

### 🔍 序列比对
- 将任意 DNA 序列（FASTA / `.seq` / `.fa`）比对到当前质粒
- **Smith-Waterman** 局部比对引擎，自动寻找最佳匹配片段
- 自动检测正向链和反向互补链，选分数高的结果
- 四行 Canvas 渲染显示：
  - **Track** — 特征色块
  - **Ref** — 参考序列，碱基分色
  - **Match** — ClustalW 质量符号：`*` 完全保守、`:` 强相似、`.` 弱相似
  - **Query** — 查询序列，错配位红色加粗
- Overhang 检测：匹配区外多余的碱基单独显示
- 突变汇总：错配、插入、缺失，标注影响的 feature
- **Ask AI** — 将比对结果发送给 AI 分析，流式输出

### 🏷️ 特征列表
- 支持搜索的特征列表，显示类型标签和位置信息
- 每个特征的 **Ask AI** — 查看特征详情后，一键获取 AI 对其功能的描述
- **About this plasmid** — AI 描述整个质粒的结构和功能
- 所有 AI 回复均支持流式 SSE 输出和 Markdown 渲染
- 可在设置中关闭 Deep Thinking（默认关闭，提升响应速度）

### ✂️ 酶切分析（v2.2）
- **1,088 种限制性内切酶**（来自 REBASE 数据库）
- 多酶组合模拟环状质粒酶切
- **AutoPick** — 根据以下条件自动推荐最佳酶组合：
  - 单酶切或双酶切
  - 目标片段数量
  - 最小/最大片段长度
- 环状图使用与 Map 屏幕相同的分层渲染和箭头风格
- 固定 2:1 布局，地图始终占上三分之二
- 点击特征可 **Ask AI**

### ⚙️ 设置
- 主题：自动（跟随系统）、浅色、深色 — Material You 动态配色
- 碱基分色开关
- AI 配置：API 地址、API 密钥、模型选择
- AI 回复语言：English / 中文
- Deep Thinking 开关（默认关闭）
- 测试连接按钮

## 支持文件格式

| 格式       | 扩展名                | 解析器              |
| ---------- | ------------------- | ----------------- |
| SnapGene   | `.dna`              | Chaquopy + sgffp  |
| GenBank    | `.gb`, `.gbk`       | 内置解析器           |
| FASTA      | `.fasta`, `.fa`     | 内置解析器           |
| 纯文本      | `.seq`              | 内置（FASTA 格式）    |
| JSON       | `.json`             | 内置解析器           |

## 打开文件

1. 在主界面点击 **Import file** 或 **Import folder**
2. 选择 `.dna`、`.gb`、`.fasta`、`.fa`、`.seq` 或 `.json` 文件
3. 质粒在 Map 视图中打开，所有特征自动解析

## 序列比对

1. 打开质粒后切换到 **Sequence** 标签页
2. 点击标题栏的 **Compare** 按钮
3. 选择设备上的 FASTA/seq 文件
4. 比对结果在独立页面中以四行格式显示
5. 点击标题栏的 **Ask AI** 获取比对结果的 AI 分析

## AI 设置

1. 进入 **设置 → AI 配置**
2. 输入 API 地址（默认：`https://api.xiaomimimo.com/v1`）
3. 输入 API 密钥
4. 选择模型（默认：`mimo-v2.5`）
5. 选择回复语言
6. 点击 **测试连接** 验证

App 兼容任意 OpenAI 协议的 API。AI 功能为可选增强，不配置 API Key 也可正常使用所有其他功能。

## 技术栈

| 组件       | 技术                        |
| ---------- | --------------------------- |
| 语言       | Kotlin                      |
| UI         | Jetpack Compose + Material 3 |
| Python 桥  | Chaquopy（用于解析 .dna）     |
| AI API     | 兼容 OpenAI `/v1/chat/completions` |
| 比对引擎   | 纯 Kotlin Smith-Waterman（自定义实现） |
| 酶切引擎   | 纯 Kotlin，IUPAC 简并碱基支持，REBASE 数据经 Biopython 预处理 |
| 导航       | Navigation Compose            |
| 持久化     | DataStore Preferences         |
| 构建       | Gradle (Groovy DSL)，AGP 8.9，Kotlin 2.1 |

## 构建方法

```bash
git clone https://github.com/zihanlinsam/PlasmidView.git
cd PlasmidView
./gradlew assembleDebug
```

需要 Python 3.12 环境和 Android SDK。Chaquopy 插件会自动处理 Python 桥接。

## 系统要求

- Android 8.0（API 26）或更高
- ARM64 或 x86_64 设备

## 下载

APK 请从 [Releases](https://github.com/zihanlinsam/PlasmidView/releases) 页面下载。

## 许可证

AGPL-3.0
