# Vocab App Graduation Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a verified 15-slide Vietnamese PowerPoint deck for a 15-minute Vocab App graduation-project presentation.

**Architecture:** Build the deck as one plain JavaScript ES module using `@oai/artifact-tool`. Keep source and QA artifacts in an external temporary workspace, export only the final PPTX to the repository `outputs` directory, render every slide to PNG, inspect all slides, and iterate until overflow and layout checks pass.

**Tech Stack:** Node.js, `@oai/artifact-tool`, PowerPoint `.pptx`, LibreOffice rendering through the presentation container tools.

## Global Constraints

- Source specification: `docs/superpowers/specs/2026-08-17-vocab-app-presentation-design.md`.
- Exactly 15 audience-facing Vietnamese slides in 16:9 format.
- Academic technical visual direction: white canvas, black hierarchy, restrained blue accents, thin gray rules.
- No application screenshots, stock photography, or decorative generated images.
- Use native PowerPoint shapes only for simple diagrams and flows.
- Use at least 50 pt for the cover title, 35 pt for slide titles, 24 pt for subheads/callout headers, and 16 pt for body copy.
- Review Vocab must receive three dedicated slides.
- Show verified MySQL counts: 1,334,872 distinct words; 1,473,332 `words` rows; 1,762,690 senses; 1,017,860 examples.
- Show current Redis identities accurately: `review_progress:v2:<userId>:<wordId>` and `current_review_wrong:<userVocabId>`.
- Final output: `outputs/vocab-app-graduation-presentation.pptx`.
- Do not modify user-owned untracked report or PlantUML files.

---

### Task 1: Initialize the artifact-tool workspace and presentation source

**Files:**
- Create: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/vocab-app-deck.mjs`
- Create: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/source-notes.txt`
- Create: `outputs/vocab-app-graduation-presentation.pptx`

**Interfaces:**
- Consumes: the approved presentation specification and artifact-tool documentation.
- Produces: an initialized workspace where `node vocab-app-deck.mjs` can export the final PPTX.

- [ ] **Step 1: Resolve the external temporary root**

Run:

```powershell
node -p "require('node:os').tmpdir()"
```

Expected: an absolute writable Windows temporary directory.

- [ ] **Step 2: Initialize artifact-tool package resolution**

Run:

```powershell
node "$env:PRESENTATION_SKILL_DIR\container_tools\setup_artifact_tool_workspace.mjs" --workspace "$env:PRESENTATION_TMP_DIR"
```

Expected: package setup completes without an error and the temporary workspace can import `@oai/artifact-tool`.

- [ ] **Step 3: Create source notes**

Record the exact source files and verified figures in `source-notes.txt`:

```text
Approved design: docs/superpowers/specs/2026-08-17-vocab-app-presentation-design.md
Architecture and flows: current Java source under src/main/java
Redis identities and TTLs: src/main/resources/redis_keys.properties
Data counts supplied by project owner:
- COUNT(DISTINCT word) = 1,334,872
- COUNT(*) FROM words = 1,473,332
- COUNT(*) FROM word_senses = 1,762,690
- COUNT(*) FROM word_examples = 1,017,860
```

- [ ] **Step 4: Scaffold a plain ES module**

The module must use this top-level structure:

```javascript
import { Presentation, PresentationFile } from "@oai/artifact-tool";

const presentation = new Presentation({ slideSize: { width: 13.333, height: 7.5 } });
const buildDeck = (deck) => deck;
buildDeck(presentation);
await PresentationFile.exportPptx(presentation, process.env.FINAL_PPTX);
```

Expected: the source is directly executable by Node.js with no transpiler.

### Task 2: Implement the visual system and slides 1–7

**Files:**
- Modify: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/vocab-app-deck.mjs`

**Interfaces:**
- Consumes: artifact-tool Compose primitives and the approved slide 1–7 specification.
- Produces: reusable helpers `addBaseSlide`, `addTitle`, `addFooter`, `addFlowNode`, `addConnector`, `addMetric`, and the first seven slides.

- [ ] **Step 1: Define presentation tokens and reusable helpers**

Use fixed tokens:

```javascript
const C = {
  canvas: "#FFFFFF",
  ink: "#0F172A",
  muted: "#475569",
  rule: "#CBD5E1",
  panel: "#F1F5F9",
  blue: "#2563EB",
  blueSoft: "#DBEAFE",
  cyan: "#0891B2",
  amber: "#D97706"
};

const F = {
  headline: "Arial",
  body: "Arial"
};
```

Every slide helper must reserve consistent 0.55-inch left/right margins and a footer source area.

- [ ] **Step 2: Build slides 1–3**

Implement:

```text
1. Minimal cover with student information and the shared-data/personal-pathway thesis.
2. Three-problem causal diagram ending in the need for a connected learning process.
3. Four-stage learning loop: lookup, save sense, review, skill application.
```

Expected: one dominant claim per slide; no title wrapping.

- [ ] **Step 3: Build slides 4–5**

Implement:

```text
4. Left-to-right data pipeline with sources, collection/validation, normalization/deduplication, relational output.
5. Large-number evidence layout using exact verified counts and precise labels.
```

Expected: 1,334,872 is labelled as distinct words and 1,473,332 as rows in `words`.

- [ ] **Step 4: Build slides 6–7**

Implement:

```text
6. Four-layer architecture: React, Spring Boot/API/services, MySQL/Redis, external translation and AI.
7. Three-way data model: shared content, personal state, reconstructable temporary state.
```

Expected: slide 7 visually bridges architecture to feature flows.

- [ ] **Step 5: Export and run an early overflow check**

Run:

```powershell
node "$env:PRESENTATION_TMP_DIR\vocab-app-deck.mjs"
python "$env:PRESENTATION_SKILL_DIR\container_tools\slides_test.py" "$env:FINAL_PPTX"
```

Expected: PPTX exports and no element exceeds the slide canvas.

### Task 3: Implement feature, deployment, and result slides 8–15

**Files:**
- Modify: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/vocab-app-deck.mjs`

**Interfaces:**
- Consumes: the visual helpers from Task 2 and approved slide 8–15 content.
- Produces: the complete 15-slide presentation.

- [ ] **Step 1: Build the word lookup flow on slide 8**

Render a cache-aside branch with Redis hit and MySQL/Azure miss paths. Show the five-hour TTL and a small amber limitation callout for the missing `transLangCode` in the translated cache key.

- [ ] **Step 2: Build the three-slide Review Vocab sequence**

Implement:

```text
9. Due selection → example preflight → shared revisioned snapshot → balanced in-memory quiz creation.
10. Shared snapshot versus personal retry state, with exact Redis key patterns and 2h/3h TTLs.
11. Level 1–6 schedule table with correct and wrong next-review intervals.
```

Expected: slide 10 explicitly states that exercise-type reservations are keyed by user and word, while repeated wrong penalties are guarded by `userVocabId`.

- [ ] **Step 3: Build slides 12–13**

Implement:

```text
12. Listen-and-Type category → lesson → challenge → attempt → progress flow.
13. Writing task → strict examiner prompt → Groq → JSON review → saved history flow.
```

Expected: the slides include concise current-behavior notes without dominating the main flow.

- [ ] **Step 4: Build slides 14–15**

Implement:

```text
14. Runtime and CI/CD flows in two horizontal bands.
15. Achieved outcomes, three concise future improvements, and the final shared-data/personal-progress conclusion.
```

Expected: the final slide resolves the opening claim and is not a generic thank-you slide.

- [ ] **Step 5: Export the complete deck**

Run:

```powershell
node "$env:PRESENTATION_TMP_DIR\vocab-app-deck.mjs"
```

Expected: `outputs/vocab-app-graduation-presentation.pptx` is created or replaced successfully.

### Task 4: Render, inspect, revise, and deliver

**Files:**
- Modify: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/vocab-app-deck.mjs`
- Verify: `outputs/vocab-app-graduation-presentation.pptx`
- Create: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/preview/slide-*.png`
- Create: `C:/Users/Admin/AppData/Local/Temp/codex-presentations/manual-vocab-app-20260817/vocab-app-graduation/tmp/qa/montage.png`

**Interfaces:**
- Consumes: the complete PPTX and rendered slide images.
- Produces: a visually verified final deck with no overflow, clipping, unintended overlap, or connector crossing.

- [ ] **Step 1: Run structural slide QA**

Run:

```powershell
python "$env:PRESENTATION_SKILL_DIR\container_tools\slides_test.py" "$env:FINAL_PPTX"
```

Expected: zero overflow errors.

- [ ] **Step 2: Render all slides**

Run:

```powershell
python "$env:PRESENTATION_SKILL_DIR\container_tools\render_slides.py" "$env:FINAL_PPTX" --output_dir "$env:PRESENTATION_PREVIEW_DIR"
```

Expected: exactly 15 PNG files.

- [ ] **Step 3: Create and inspect the montage**

Run:

```powershell
python "$env:PRESENTATION_SKILL_DIR\container_tools\create_montage.py" --input_dir "$env:PRESENTATION_PREVIEW_DIR" --output_file "$env:PRESENTATION_QA_DIR\montage.png"
```

Inspect the montage for deck-level consistency, then inspect every slide PNG individually at full size.

- [ ] **Step 4: Fix all QA findings and rerun checks**

For each finding, update the ES module, re-export, rerender, and repeat `slides_test.py`. Do not deliver with unresolved title wrapping, clipped labels, unintended overlaps, unreadable table text, or connectors crossing node labels.

- [ ] **Step 5: Verify the final artifact**

Run:

```powershell
Get-Item "outputs\vocab-app-graduation-presentation.pptx" | Select-Object FullName,Length,LastWriteTime
python "$env:PRESENTATION_SKILL_DIR\container_tools\slides_test.py" "outputs\vocab-app-graduation-presentation.pptx"
```

Expected: the PPTX exists, has nonzero size, contains 15 rendered slides, and the overflow checker reports no errors.
