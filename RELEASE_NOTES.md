# PlasmidView v2.2 Release Notes

## v2.2 — Maps, Lanes, and Precision (July 2026)

### 🧬 Map Screen Rewrite
- **Single-path feature arcs** — each feature is drawn as one continuous filled path (outer arc → arrow tip → inner arc → close) instead of a stroke arc with a separate triangle pasted on. No alignment gaps, no mismatched borders.
- **Directional arrows** — forward-strand arrows at the clockwise end, reverse-strand arrows at the counter-clockwise start. Arrow tip sits precisely at the feature boundary; the arc recedes slightly to avoid visual overlap.
- **Lane-based concentric rings** — overlapping features are automatically assigned to concentric rings (greedy interval colouring). Lane 0 = main backbone, odd lanes squeeze inward, even lanes expand outward. No more hidden features.
- **Auto-fit base radius** — the map's radius is calculated bottom-up from the space needed for all outer lanes, tick marks, and tick labels. Ticks and labels never overflow the screen at zoom 1.
- **Home reset button** — tap to restore default zoom and position.

### 📄 Sequence Screen Rewrite
- **Mono-spaced font** — switched from the old ambiguous `CHAR_W` constant to actual `Paint.measureText("A")` with `Typeface.MONOSPACE`. Characters are perfectly aligned.
- **Segment model (Seg)** — features that cross the origin (start > end) are split into two logical segments for track layering, hit testing, and clipping. No more invisible features.
- **Arrow-once-per-feature** — multi-line features show an arrow only on the line where the feature actually ends (forward → right arrow on the last line; reverse → left arrow on the first line).
- **Layer-aware tap detection** — tap individual track layers to select overlapping features independently; tap the sequence row to see a picker if multiple features cover that position.
- **Cached search** — search results are computed once outside the draw loop and cached by line. Cross-line match highlighting works correctly.

### ✂️ Digest Screen
- **Map parity** — the digest map now uses the same lane-based rendering, single-path feature arcs, arrows, and auto-fit radius as the Map screen. Cut-site markers remain on the backbone.
- **Fixed 2:1 layout** — the circular map always occupies the upper two-thirds regardless of digest results.
- **Ask AI** on any digest-map feature.

### 🤖 Unified Feature Detail Dialog
- A single `FeatureDetailDialog` composable is used across all four screens (Map, Sequence, Features, Digest). Every screen gets the same detail layout (color bar, type, position, length, strand, sequence) **and** the Ask AI button with streaming analysis.

### 🧠 AI Icon
- All Ask AI buttons use the Material `AutoAwesome` (sparkle) icon, consistent with NoteAndRecall.

### 🔧 Bug Fixes & Quality
- `Color.hashCode()` → `Color.toArgb()` for all native `Paint` colour assignments. No more wrong text colours.
- Map tap collision tightened to nearest-lane-with-2dp tolerance; adjacent lane rings no longer interfere.
- Shared constants and helpers (`assignLanes`, `laneRadius`, `fitBaseRadius`, `drawFeatureArc`) keep rendering consistent across views.

---

## v2.1 — Notes, Editors, and AI Refinements (May 2026)

### ✏️ Feature Editor
- Long-press any feature in the Features list to open the **Feature Editor** sheet.
- Edit feature name, type (dropdown), start/end positions, strand direction, colour, and description.
- Delete features from the editor sheet.

### 📝 Plasmid Notes Engine
- **Export** any plasmid to a Markdown note with full file-system integration (Android SAF).
- Sequence alignment results can be directly exported as structured notes.
- Notes are linked back to the source plasmid file for traceability.

### 🤖 AI Improvements
- **Streaming text accumulation** — the AI response now buffers tokens and flushes in batches of 50+ characters, making streaming noticeable and responsive.
- **Copy AI result** — once a streaming response completes, the Ask AI button turns into a Copy button.
- **Plasmid-level AI query** — the Features screen gained an "About this plasmid" button that asks AI to describe the whole vector's structure and function.

### 🔧 Misc
- SnapGene `.dna` parser improvements (better feature-type mapping for terminators and protein-binding sites).
- AutoPick now uses the correct REBASE data pre-loaded from assets (no network dependency).
- Performance: heavy Canvas operations profiled and optimised for large plasmids.

---

## v2.0 — Stable Release (March 2026)

### Core
- Initial public release.
- **Circular plasmid map** with Compose Canvas rendering, pinch-to-zoom, pan gestures, and tap-to-inspect.
- **Sequence browser** with per-base A/T/G/C colouring, adjustable font size, search with highlighting, and feature track layers.
- **Restriction digest**: 1,088 enzymes from REBASE, multi-select, AutoPick, circular map overlay with cut markers and fragment list.
- **Sequence comparison**: Smith-Waterman local alignment, forward/reverse-complement auto-detect, four-line display, mutation summary, and overhang detection.
- **AI integration**: OpenAI-compatible API, streaming responses, markdown rendering, per-feature Ask AI, per-alignment Ask AI.
- **Settings**: theme (Auto/Light/Dark), coloured bases toggle, full AI configuration, language selection, deep-thinking toggle, test-connection.
- **File format support**: SnapGene `.dna`, GenBank `.gb`/`.gbk`, FASTA, plain text, JSON.
- **Home screen**: file picker (single & folder), persistent file history.
- Material You dynamic colours throughout.
