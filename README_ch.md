# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

Android 质粒图谱查看器，集成 AI 功能分析。支持 SnapGene、GenBank、FASTA 等格式的质粒文件浏览与酶切分析。

## 功能特性

### 🧬 质粒图谱

- 环状质粒渲染，支持双指缩放和平移
- 按类型着色（基因、CDS、启动子、复制起点等）显示特征弧线
- 正链/反链指示（实线/虚线边框）
- 碱基对刻度和位置标签

### 📄 序列浏览器

- 完整质粒序列展示，支持 A/T/G/C 碱基分色
- 拖拽滑块调节字体大小
- 序列搜索和高亮标记
- 特征轨迹行，显示方向指示
- 点击任意特征查看详情

### 🏷️ 特征列表

- 支持搜索的特征列表（类型、位置、长度）
- 单个特征的 **AI 问答** — 一键获取任意特征的 AI 分析
- **质粒 AI 分析** — 让 AI 描述整个质粒的结构和潜在功能
- AI 回复使用 MiMo API（兼容 OpenAI 协议），支持 Markdown 渲染

### ✂️ 酶切分析

- **选择模式** — 从 REBASE 数据库的 1088 种限制性内切酶中选取，模拟酶切
- **自动推荐** — 根据需求（单/双酶切、目标片段数、长度范围）推荐合适酶组合
- 环状质粒图上同时显示特征弧线和酶切位点标记（不同酶不同颜色）
- 酶切片段列表，显示大小和位置

### ⚙️ 设置

- 主题：自动 / 浅色 / 深色（Material You）
- 碱基分色开关
- AI 配置（API 地址、API 密钥、模型）
- AI 回复语言：English / 中文
- 连接测试按钮

## 支持文件格式

| 格式       | 扩展名             | 解析器              |
| -------- | --------------- | ---------------- |
| SnapGene | `.dna`          | Chaquopy + sgffp |
| GenBank  | `.gb`, `.gbk`   | 内置解析器            |
| FASTA    | `.fasta`, `.fa` | 内置解析器            |
| JSON     | `.json`         | 内置解析器            |

## 技术栈

- **语言：** Kotlin
- **UI：** Jetpack Compose + Material You（Material 3）
- **Python 桥接：** Chaquopy（用于 .dna 解析）
- **AI：** MiMo API（兼容 OpenAI 的 `/v1/chat/completions`）
- **酶切引擎：** 纯 Kotlin 实现（含 IUPAC 简并碱基支持），数据来自 REBASE（经 Biopython 预处理）
- **持久化：** DataStore Preferences
- **构建：** Gradle（Groovy DSL）

## 构建方法

```bash
git clone https://github.com/yourusername/PlasmidView.git
cd PlasmidView
./gradlew assembleDebug
```

APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

## 系统要求

- Android 8.0（API 26）或更高
- ARM64 或 x86_64 设备

## AI 设置

1. 进入 **设置 → AI 配置**
2. 输入 API 地址（默认：`https://api.xiaomimimo.com/v1`）
3. 输入 API 密钥
4. 选择模型（默认：`mimo-v2.5`）
5. 选择回复语言
6. 点击 **测试连接** 验证

## 致谢

- [Biopython](https://biopython.org/) — REBASE 限制性内切酶数据
- [sgffp](https://github.com/merv1n34k/sgffp) — SnapGene .dna 文件解析
- [REBASE](http://rebase.neb.com/) — 限制性内切酶数据库

# 
