# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

An Android plasmid map viewer with AI-powered feature analysis.

## Features

### 🧬 Plasmid Map

- Circular plasmid visualization with pinch-to-zoom and pan
- Feature arcs with color-coded types (genes, CDS, promoters, origins, etc.)
- Forward/reverse strand indicators (solid/dashed borders)
- Base pair ticks and position labels

### 📄 Sequence Browser

- Full plasmid sequence display with per-base coloring (A/T/G/C)
- Adjustable font size slider
- Search within the sequence with highlighted matches
- Feature tracks below each line with directional indicators
- Tap any feature to view details

### 🏷️ Features List

- Searchable feature list with type, position, and length
- Per-feature **Ask AI** button — get instant AI analysis of any feature
- **Plasmid AI Analysis** — ask the AI to describe the entire plasmid's structure and likely function
- AI responses use MiMo API (OpenAI-compatible), with markdown rendering

### ✂️ Restriction Digest

- **Select** — browse 1,088 restriction enzymes from the REBASE database; pick one or more to simulate digestion
- **AutoPick** — let the app recommend enzymes based on your needs (single/double digest, desired fragment count, size range)
- Circular plasmid map shows both features and cut site markers (color-coded per enzyme)
- Fragment list with sizes and positions

### ⚙️ Settings

- Theme: Auto / Light / Dark (Material You)
- Colored bases toggle
- AI configuration (API URL, API Key, Model)
- AI response language: English / 中文
- Test Connection button for AI setup

## Supported File Formats

| Format   | Extension       | Parser           |
| -------- | --------------- | ---------------- |
| SnapGene | `.dna`          | Chaquopy + sgffp |
| GenBank  | `.gb`, `.gbk`   | Built-in         |
| FASTA    | `.fasta`, `.fa` | Built-in         |
| JSON     | `.json`         | Built-in         |

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material You (Material 3)
- **Python Bridge:** Chaquopy (for .dna parsing)
- **AI:** MiMo API (OpenAI-compatible `/v1/chat/completions`)
- **Restriction Engine:** Pure Kotlin (IUPAC-aware), data from REBASE via Biopython
- **Persistence:** DataStore Preferences
- **Build:** Gradle (Groovy DSL)

## Building

```bash
git clone https://github.com/yourusername/PlasmidView.git
cd PlasmidView
./gradlew assembleDebug
```

## Requirements

- Android 8.0 (API 26) or higher
- ARM64 or x86_64 device/emulator

## AI Setup

1. Go to **Settings → AI Configuration**
2. Enter your API endpoint (default: `https://api.xiaomimimo.com/v1`)
3. Enter your API key
4. Select the model (default: `mimo-v2.5`)
5. Choose response language
6. Tap **Test Connection** to verify

## License

AGPL-3.0

This project is licensed under the GNU Affero General Public License v3.0. See the [LICENSE](LICENSE) file for details.
