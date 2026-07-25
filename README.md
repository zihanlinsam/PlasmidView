# PlasmidView

[English](README.md) | [中文](README_ch.md)

---

An Android plasmid map viewer with AI-powered analysis, restriction digest simulation, sequence alignment, and sequence browser. Built with Kotlin & Jetpack Compose.

## Features

### 🧬 Plasmid Map (v2.2)
- Circular vector visualization with pinch-to-zoom and pan gestures
- **Lane-based concentric rings** for overlapping features — each feature ring auto-arranged to avoid collision
- **Single-path feature arcs** — color-coded arcs with integrated directional arrows (forward/reverse) drawn as one continuous path
- Arrow tips precisely at feature boundaries; arcs recede to avoid overlap with arrows
- Base pair ticks and auto-positioned labels that never overflow the screen
- Tap any feature to view details (shared dialog across all screens with **Ask AI**)
- **Home reset button** to restore default zoom and position

### 📄 Sequence Browser (v2.2)
- Full plasmid sequence with A/T/G/C per-base coloring using **monospace font** for perfect alignment
- **Segment-aware feature tracks** — cross-origin features split into segments, each occupying the correct track layer
- **Arrow only at the feature's real end** — multi-line features show arrow only on the terminating row
- **Layer-aware tap detection** — tap individual track layers for overlapping features; tap the sequence row to see a feature picker if multiple features cover that position
- **Cached multi-line search** — search results span line boundaries with per-character highlighting, computed once outside the draw loop
- Adjustable font size for comfortable reading

### 🔍 Sequence Comparison
- Align any DNA sequence (FASTA / `.seq` / `.fa`) against the opened plasmid
- **Smith-Waterman** local alignment — automatically finds the best matching segment
- Automatic forward/reverse complement detection, picks the higher-scoring alignment
- Four-line display rendered on a canvas:
  - **Track** — feature bars
  - **Ref** — reference sequence with per-base coloring
  - **Match** — ClustalW quality symbols: `*` exact, `:` strong, `.` weak
  - **Query** — query sequence, mismatches highlighted in red bold
- Overhang detection: extra query bases beyond the matched region shown separately
- Mutation summary: lists mismatches, insertions, and deletions with affected feature annotations
- **Ask AI** — send the alignment result to AI for analysis, streaming response

### 🏷️ Features List
- Searchable, scrollable list of all features with type badges and position tags
- **Ask AI** per feature — view details, then ask AI for a description and functional analysis
- **About this plasmid** — AI describes the entire plasmid's structure and likely biological function
- All AI responses are streaming SSE with markdown rendering
- Deep Thinking toggle in Settings (default off for faster responses)

### ✂️ Restriction Digest (v2.2)
- Browse **1,088 restriction enzymes** from the REBASE database
- Select multiple enzymes to simulate a circular digestion
- **AutoPick** — the app recommends optimal enzyme combinations based on:
  - Single or double digest
  - Desired fragment count
  - Minimum and maximum fragment size
- Circular map overlay shows feature arcs and enzyme cut markers with the same lane-based rendering and arrows as the Map screen
- Fragment list with sizes and genomic coordinates
- Fixed 2:1 layout — map always occupies the upper two-thirds
- **Ask AI** on any feature within the digest view

### ⚙️ Settings
- Theme: Auto (follow system), Light, Dark — Material You dynamic colors
- Colored bases toggle
- AI configuration: API endpoint, API key, model selection
- AI response language: English / 中文
- Deep Thinking toggle (off by default)
- Test Connection button to validate the AI setup

## Supported File Formats

| Format     | Extension        | Parser             |
| ---------- | ---------------- | ------------------ |
| SnapGene   | `.dna`           | Chaquopy + sgffp   |
| GenBank    | `.gb`, `.gbk`    | Built-in           |
| FASTA      | `.fasta`, `.fa`  | Built-in           |
| Plain text | `.seq`           | Built-in (FASTA)   |
| JSON       | `.json`          | Built-in           |

## Opening Files

1. Tap **Import file** or **Import folder** on the home screen
2. Select a `.dna`, `.gb`, `.fasta`, `.fa`, `.seq`, or `.json` file
3. The plasmid opens in the Map view with all features parsed

## Sequence Comparison

1. Open a plasmid, switch to the **Sequence** tab
2. Tap the **Compare** button in the title bar
3. Select a FASTA/seq file from your device
4. The alignment result appears in a dedicated screen with the four-line display
5. Tap **Ask AI** in the title bar for AI-powered analysis of the alignment

## AI Setup

1. Go to **Settings → AI Configuration**
2. Enter your API endpoint (default: `https://api.xiaomimimo.com/v1`)
3. Enter your API key
4. Select the model (default: `mimo-v2.5`)
5. Choose response language
6. Tap **Test Connection** to verify

The app supports any OpenAI-compatible API. AI features are optional — everything works without an API key.

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language  | Kotlin |
| UI        | Jetpack Compose + Material You (Material 3) |
| Python Bridge | Chaquopy (for SnapGene .dna parsing) |
| AI API    | OpenAI-compatible `/v1/chat/completions` |
| Alignment Engine | Pure Kotlin Smith-Waterman (custom implementation) |
| Restriction Engine | Pure Kotlin, IUPAC-aware, REBASE data via Biopython |
| Navigation | Navigation Compose |
| Persistence | DataStore Preferences |
| Build     | Gradle (Groovy DSL), AGP 8.9, Kotlin 2.1 |

## Building

```bash
git clone https://github.com/zihanlinsam/PlasmidView.git
cd PlasmidView
./gradlew assembleDebug
```

Requires Python 3.12 and an Android SDK. The Chaquopy plugin handles the Python bridge automatically.

## Requirements

- Android 8.0 (API 26) or higher
- ARM64 or x86_64 device

## Download

Pre-built APKs are available from the [Releases](https://github.com/zihanlinsam/PlasmidView/releases) page.

## License

AGPL-3.0

This project is licensed under the GNU Affero General Public License v3.0.
