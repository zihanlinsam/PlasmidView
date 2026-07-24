# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

Android 质粒图谱查看器，集成 AI 分析、酶切模拟和序列比对功能。

## 功能特性

### 🧬 质粒图谱
- 环状质粒渲染，支持双指缩放和平移
- 按类型着色显示特征弧线（CDS、启动子、复制起点、终止子、LTR、基因等）
- 正链/反链指示 — 实线边框为正链，虚线为反链
- 碱基对刻度和位置标签
- 点击任意特征弧查看名称、类型、位置、长度、链向和序列

### 📄 序列浏览器
- 完整质粒序列展示，A/T/G/C 碱基分色
- 滑块调节字体大小
- 序列搜索和高亮定位
- 特征轨迹行：彩色条块 + 方向箭头指示
- 点击任意特征查看详情

### 🔍 序列比对
- 将任意 DNA 序列（FASTA / `.seq` / `.fa`）比对到当前质粒
- **Smith-Waterman** 局部比对引擎，自动寻找最佳匹配片段
- 自动检测正向链和反向互补链，选分数高的结果
- 四行 Canvas 渲染显示：
  - **Track** — 特征色块，反链用虚线边框
  - **Ref** — 参考序列，碱基分色
  - **Match** — ClustalW 质量符号：`*` 完全保守、`:` 强相似、`.` 弱相似
  - **Query** — 查询序列，错配位红色加粗
- Overhang 检测：匹配区外多余的碱基单独显示
- 突变汇总：错配、插入、缺失，标注影响的 feature
- **Ask AI** — 将比对结果（坐标、突变、feature 列表）发送给 AI 分析，流式输出

### 🏷️ 特征列表
- 支持搜索的特征列表，显示类型标签和位置信息
- 每个特征的 **Ask AI** — 查看特征详情后，一键获取 AI 对其功能的 description
- **About this plasmid** — AI 描述整个质粒的结构和功能
- 所有 AI 回复均支持流式 SSE 输出和 Markdown 渲染
- 可在设置中关闭 Deep Thinking（默认关闭，提升响应速度）

### ✂️ 酶切分析
- **1,088 种限制性内切酶**（来自 REBASE 数据库）
- 多酶组合模拟环状质粒酶切
- **AutoPick** — 根据以下条件自动推荐最佳酶组合：
  - 单酶切或双酶切
  - 目标片段数量
  - 最小/最大片段长度
- 环状图同时显示特征弧线和酶切位点标记，不同酶以不同颜色区分
- 酶切片段列表，显示大小和基因组坐标

### ⚙️ 设置
- 主题：自动（跟随系统）、浅色、深色 — Material You 动态配色
- 碱基分色开关
- AI 配置：API 地址、API 密钥、模型选择
- AI 回复语言：English / 中文
- Deep Thinking 开关（默认关闭，开启后启用深度推理链）
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
| 构建       | Gradle (Groovy DSL)，AGP 8.9  |

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
