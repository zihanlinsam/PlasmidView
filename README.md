# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

An Android plasmid map viewer with AI-powered analysis and sequence alignment.

## Screenshots

*(coming soon)*

## Features

### 🧬 Plasmid Map
- Circular plasmid visualization with pinch-to-zoom and pan
- Feature arcs with color-coded types (genes, CDS, promoters, origins, LTRs, etc.)
- Forward/reverse strand indicators (solid/dashed borders)
- Base pair ticks and position labels
- Tap any feature arc to view detailed information

### 📄 Sequence Browser
- Full plasmid sequence display with per-base coloring (A=green, T=red, G=orange, C=blue)
- Adjustable font size slider
- Search within the sequence with highlighted matches
- Feature tracks below each line with directional indicators
- Tap any feature track to view position, length, strand, and sequence

### 🔍 Sequence Comparison *(New in v2.0)*
- Align any DNA sequence (FASTA/`.seq`/`.fa`) against the opened plasmid
- **Smith-Waterman** local alignment engine finds the best matching segment
- Automatic forward/reverse complement detection — picks whichever scores higher
- ClustalW-style 4-line display:
  - **Track** — feature bars color-coded by type (dashed border for reverse strand)
  - **Ref** — reference sequence with per-base coloring
  - **Match** — quality symbols (\* = exact, : = strong, . = weak)
  - **Query** — query sequence with mismatches highlighted in red bold
- Overhang detection — extra bases extending beyond the match shown separately
- Mutation summary with mismatches, insertions, deletions, and affected features

### 🏷️ Features List
- Searchable feature list with type badges, positions, and lengths
- **Ask AI** per feature — get instant AI analysis of any genetic element
- **About this plasmid** — AI describes the entire plasmid's structure and function
- Streaming SSE responses rendered as formatted markdown

### ✂️ Restriction Digest
- Browse 1,088 restriction enzymes from the REBASE database
- Multi-enzyme selection for simulated circular digestion
- **AutoPick** — recommends optimal enzyme combinations (single/double digest, fragment count, size range)
- Circular map overlay with feature arcs + cut site markers (color-coded per enzyme)
- Fragment list with sizes and positions

### ⚙️ Settings
- Theme: Auto / Light / Dark (Material You dynamic colors)
- Colored bases toggle
- AI configuration: API URL, API Key, Model selection
- AI response language: English / 中文
- Test Connection button to validate AI setup

## Supported File Formats

| Format     | Extension        | Parser             |
| ---------- | ---------------- | ------------------ |
| SnapGene   | `.dna`           | Chaquopy + sgffp   |
| GenBank    | `.gb`, `.gbk`    | Built-in           |
| FASTA      | `.fasta`, `.fa`  | Built-in           |
| Plain text | `.seq`           | Built-in (FASTA)   |
| JSON       | `.json`          | Built-in           |

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material You (Material 3)
- **Python Bridge:** Chaquopy (for .dna parsing)
- **AI:** OpenAI-compatible API (`/v1/chat/completions`)
- **Alignment Engine:** Pure Kotlin Smith-Waterman (custom implementation)
- **Restriction Engine:** Pure Kotlin IUPAC-aware pattern matching, data from REBASE via Biopython
- **Navigation:** Navigation Compose
- **Persistence:** DataStore Preferences
- **Build:** Gradle (Groovy DSL), AGP 8.9

## Building

```bash
git clone https://github.com/zihanlinsam/PlasmidView.git
cd PlasmidView
./gradlew assembleDebug
```

The build requires a Python 3.12 interpreter with Chaquopy support. See `app/build.gradle` for the exact path.

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

The app supports any OpenAI-compatible API. No AI features are required — everything works without an API key, AI analysis is optional.

## Download

APKs are available from the [Releases](https://github.com/zihanlinsam/PlasmidView/releases) page.

## License

AGPL-3.0

This project is licensed under the GNU Affero General Public License v3.0. See the [LICENSE](LICENSE) file for details.
