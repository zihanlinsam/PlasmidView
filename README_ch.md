# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

Android 质粒图谱查看器，集成 AI 功能分析和序列比对。

## 功能特性

### 🧬 质粒图谱
- 环状质粒渲染，支持双指缩放和平移
- 按类型着色（基因、CDS、启动子、复制起点等）显示特征弧线
- 正链/反链指示（实线/虚线边框）
- 碱基对刻度和位置标签
- 点击任意特征弧查看详情

### 📄 序列浏览器
- 完整质粒序列展示，A/T/G/C 碱基分色（A绿 T红 G橙 C蓝）
- 滑块调节字体大小
- 序列搜索和高亮标记
- 特征轨迹行，显示方向指示
- 点击任意特征查看位置、长度、链向和序列

### 🔍 序列比对 *(v2.0 新增)*
- 将任意 DNA 序列（FASTA/`.seq`/`.fa`）比对到当前质粒
- **Smith-Waterman** 局部比对引擎，自动寻找最佳匹配片段
- 自动检测正向/反向互补链，选分数高的结果
- 四行对比显示：
  - **Track** — 特征色块（反链用虚线边框）
  - **Ref** — 参考序列，碱基分色
  - **Match** — 比对质量符号（\* 完全保守 / : 强相似 / . 弱相似）
  - **Query** — 查询序列，错配位红色加粗
- Overhang 检测——匹配区外多余的碱基单独显示
- 突变汇总：错配、插入、缺失，标注影响的 feature

### 🏷️ 特征列表
- 支持搜索的特征列表（类型、位置、长度）
- 单个特征的 **Ask AI** — 一键获取任意特征的 AI 分析
- **About this plasmid** — AI 描述整个质粒的结构和功能
- 流式 SSE 响应，Markdown 渲染

### ✂️ 酶切分析
- 从 REBASE 数据库的 1088 种限制性内切酶中选取
- 多酶组合模拟环状质粒酶切
- **AutoPick** — 根据需求（单/双酶切、目标片段数、长度范围）自动推荐
- 环状图叠加特征弧线和酶切位点标记（不同酶不同颜色）
- 酶切片段列表，显示大小和位置

### ⚙️ 设置
- 主题：自动 / 浅色 / 深色（Material You 动态配色）
- 碱基分色开关
- AI 配置：API 地址、API 密钥、模型选择
- AI 回复语言：English / 中文
- 测试连接按钮

## 支持文件格式

| 格式       | 扩展名                | 解析器              |
| ---------- | ------------------- | ----------------- |
| SnapGene   | `.dna`              | Chaquopy + sgffp  |
| GenBank    | `.gb`, `.gbk`       | 内置解析器           |
| FASTA      | `.fasta`, `.fa`     | 内置解析器           |
| 纯文本      | `.seq`              | 内置（FASTA 格式）    |
| JSON       | `.json`             | 内置解析器           |

## 技术栈

- **语言：** Kotlin
- **UI：** Jetpack Compose + Material You（Material 3）
- **Python 桥接：** Chaquopy（用于 .dna 解析）
- **AI：** 兼容 OpenAI 的 `/v1/chat/completions` 接口
- **比对引擎：** 纯 Kotlin Smith-Waterman（自定义实现）
- **酶切引擎：** 纯 Kotlin（含 IUPAC 简并碱基支持），数据来自 REBASE（经 Biopython 预处理）
- **导航：** Navigation Compose
- **持久化：** DataStore Preferences
- **构建：** Gradle（Groovy DSL），AGP 8.9

## 构建方法

```bash
git clone https://github.com/zihanlinsam/PlasmidView.git
cd PlasmidView
./gradlew assembleDebug
```

需要 Python 3.12 环境（用于 Chaquopy）。具体路径见 `app/build.gradle`。

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

所有功能均可在不配置 AI 的情况下正常使用，AI 分析为可选增强功能。

## 下载

APK 请从 [Releases](https://github.com/zihanlinsam/PlasmidView/releases) 页面下载。

## 致谢

- [Biopython](https://biopython.org/) — REBASE 限制性内切酶数据
- [sgffp](https://github.com/merv1n34k/sgffp) — SnapGene .dna 文件解析
- [REBASE](http://rebase.neb.com/) — 限制性内切酶数据库

## 许可证

AGPL-3.0
