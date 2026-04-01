# GPX Video Producer

Android-native video editor focused on sports creators who want to combine ride/run/hike footage, photos, and GPX data into social-ready recap videos.

The app is designed around a **single-screen editor** with:

- a live preview canvas
- a timeline with clip rows and overlay rows
- GPX-driven static and dynamic visuals
- export settings for common social/video formats

This repository contains the current working implementation and the history of the main technical approaches already tackled during development.

## Project vision

Typical target users are cyclists, runners, hikers, skiers, and other sport enthusiasts who record:

- videos during an activity
- photos during the same event
- a GPX/TCX track from a watch, bike computer, or phone

The goal of the app is to let them build a polished video story directly on Android, including:

- ordered clips and images on a timeline
- text and visual overlays
- route maps, altitude profiles, and summary stats
- dynamic GPX-synced data during playback
- export to vertical, square, or landscape outputs

## Current repository status

This is **not a blank prototype anymore**. The codebase already includes a multi-module Android app with:

- project creation and home flows
- a PowerDirector-style editor layout
- live preview playback through Media3 / ExoPlayer
- timeline state, seek, trimming, rearranging, and clip actions
- overlay rendering and overlay interaction on canvas
- GPX import workspace and GPX visualization components
- export configuration and FFmpeg-based export pipeline
- template gallery/editor scaffolding

Recent emulator verification confirmed that the editor is operational for core interactions, including:

- opening a project
- displaying the preview
- seeking via the playback slider
- playing the timeline
- switching output canvas aspect ratio
- opening overlay, text, GPX, effects, and export surfaces

## What has been implemented

### Editor experience

- Single-screen editor with top bar, preview, playback bar, timeline, and bottom tool actions
- Larger live preview area
- Timeline rows for video/media and generated elements
- Filmstrip-style clip rendering in the timeline instead of plain blocks
- Playhead-driven preview synchronization
- Clip selection, split, duplicate, delete, undo, redo
- Long-press drag behavior for safer clip rearranging

### Playback and preview

- Media3 / ExoPlayer-backed preview engine
- Multi-clip preview composition
- Timeline-position seeking into the correct clip segment
- Playback state synchronization between preview and timeline
- Clip display transform support
- Fix for the seek slider responsiveness issue by using local drag state in the playback bar

### Canvas and aspect ratio handling

- Configurable output canvas ratio from the editor top bar
- Presets for popular publishing formats such as:
  - `16:9`
  - `9:16`
  - `1:1`
  - `4:5`
- Preview surface adapts to project output resolution
- Letterbox/pillarbox behavior is part of the preview composition flow
- Per-clip content mode model exists (`FIT`, `FILL`, `CROP`)

### Overlays and GPX visuals

- Overlay catalog with static and dynamic items
- Static overlays such as:
  - altitude profile
  - GPS map
  - statistics card/grid
- Dynamic overlays such as:
  - live altitude
  - live map
  - live metric/stat
- Overlay drag on preview canvas
- Overlay resize handles
- Overlay settings and sync-related UI wiring
- GPX time sync engine

### GPX handling

- GPX import entry points from the editor
- GPX workspace bottom sheet
- GPX route canvas and altitude profile canvas
- GPX stats grid / visualization surface
- Custom GPX parser library module

### Export

- Export settings screen
- Export pipeline backed by FFmpeg abstractions
- Overlay frame rendering for export
- Encoding pipeline structure with phases and progress reporting
- Resolution / frame-rate / format based export configuration

### Templates

- Template repository/applicator structure
- Template gallery and editor screens
- Built-in template support scaffolding

## Main technical approaches already tackled

### 1. Modular Android architecture

The app is split into feature and library modules so editor concerns stay isolated:

- `:feature:project`
- `:feature:timeline`
- `:feature:preview`
- `:feature:export`
- `:feature:gpx`
- `:feature:overlays`
- `:feature:templates`
- `:core:*`
- `:lib:*`

This was important because the product spans media handling, rendering, timeline logic, GPX processing, and export.

### 2. Compose-first editor UI

The editor is implemented with Jetpack Compose rather than legacy views. That made it easier to iterate on:

- a single-tab editing screen
- bottom sheets for tools
- reactive state across preview and timeline
- draggable / resizable overlays

### 3. Separate preview and timeline state coordination

One major challenge was making the preview feel like a real editor instead of a static media player. The chosen approach separates:

- timeline editing state
- preview playback engine
- overlay state / GPX sync state

Then these are coordinated from the editor screen so:

- seeking updates both timeline and preview
- playback updates the timeline playhead
- overlay rendering uses the active playback position

### 4. ExoPlayer for interactive preview, FFmpeg for export

The implementation uses two different engines for two different jobs:

- **Media3 / ExoPlayer** for responsive in-editor playback and seek
- **FFmpeg** for deterministic final export and compositing

This avoids trying to force the export engine to power the live editor.

### 5. GPX-driven overlay model

Instead of treating GPX as a simple file attachment, the codebase models GPX as a source for:

- route visualization
- summary stats
- timeline-synced dynamic overlays
- export-time overlay rendering

This is the key product differentiator.

### 6. Touch and interaction debugging on emulator

Several iterations were spent debugging real editor interaction issues on the emulator, including:

- seekbar not reacting to touch
- preview/timeline desynchronization
- clip order not refreshing immediately
- aspect ratio transform issues
- overlay drag/resize inconsistencies

The current project state reflects those debugging passes, not only static implementation.

## Tech stack

- Kotlin
- Jetpack Compose
- Material 3
- Hilt
- Room
- Kotlin Coroutines / Flow
- Media3 / ExoPlayer
- FFmpeg wrapper modules
- custom GPX parser
- Gradle Kotlin DSL

## Module overview

- `app/` – application shell, dependency wiring, navigation entry points
- `core/model/` – shared domain models
- `core/database/` – Room entities, DAOs, migrations
- `core/common/` – common utilities
- `core/ui/` – shared design system/UI pieces
- `feature/home/` – home, onboarding, settings
- `feature/project/` – editor shell, media import, project editing UI
- `feature/timeline/` – timeline models, actions, row rendering, undo, controls
- `feature/preview/` – preview engine and video surface
- `feature/export/` – export config and pipeline
- `feature/gpx/` – GPX workspace and visualization
- `feature/overlays/` – overlay catalog, sync, rendering support
- `feature/templates/` – templates gallery/editor/application
- `lib/ffmpeg/` – FFmpeg command and execution layer
- `lib/gpx-parser/` – GPX parsing
- `lib/media-utils/` – thumbnails and media helpers

## Verified behaviors from recent work

Recent emulator checks verified:

- project entry into the editor
- visible multi-clip timeline
- seekbar-driven jump across the timeline
- play button reaching end of timeline
- aspect ratio preset switching
- opening the overlay catalog
- adding at least one overlay entry to timeline state
- opening GPX workspace
- opening text overlay flow
- opening clip effects controls
- opening export settings

## Open points / known gaps

The app is much further along than the initial prototype, but it still needs more polishing before it can be considered production-ready.

### 1. Final aspect-ratio and framing polish

The biggest product requirement is:

- one global output canvas ratio
- each input video keeping its own source aspect ratio
- black bands belonging to the editable canvas when needed
- per-clip framing controls for fit/fill/crop/reposition

The codebase contains the foundations for this, but this area still needs more UX and behavior verification to ensure every scenario behaves correctly with mixed portrait/landscape clips.

### 2. Canvas manipulation UX

Overlay drag and resize exist, but interaction polish is still an open area:

- repeated drag operations should remain perfectly stable
- manipulation over black bands / empty canvas must always feel natural
- per-video transform controls need to be as visual and reliable as overlay controls

### 3. Timeline editing polish

The timeline is functional, but still needs refinement in:

- resize handles for clips/elements
- precision trimming UX
- preventing invalid empty gaps in all editing paths
- clearer affordances for selected clips and rows

### 4. GPX workspace depth

The GPX area exists and can import files, but it should continue evolving into a richer editing workspace where users can:

- inspect route details comfortably
- review parsed metrics
- tune sync values
- manage a single project-level GPX source cleanly

### 5. Export robustness

The export stack is implemented, but still needs broader end-to-end validation for:

- long projects
- mixed media sources
- multiple overlays together
- failure reporting and recovery
- performance and temp-file cleanup under stress

### 6. Templates maturity

Template infrastructure exists, but the product still needs stronger template authoring/application UX to deliver the original “project + template” vision cleanly.

### 7. General product polish

Still to be improved over time:

- empty states
- onboarding
- visual consistency
- performance on larger projects
- automated test depth for editor-specific behaviors

## Suggested next engineering priorities

1. Finish the mixed-aspect-ratio canvas/framing behavior end to end.
2. Make per-clip visual framing controls first-class in the editor.
3. Further polish timeline trim/move/resize interactions.
4. Strengthen GPX workspace editing and sync tooling.
5. Run export verification on real projects with multiple overlays.
6. Expand automated coverage around timeline, preview sync, and export.

## Build

Build the debug APK with:

```bash
./gradlew assembleDebug --no-daemon -q
```

Install it on an emulator/device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If the app is already running and you want a clean relaunch:

```bash
adb shell am force-stop com.gpxvideo.app
adb shell am start -n com.gpxvideo.app/.MainActivity
```

## Notes

- Session planning/history also exists in `PLAN.md` and the Copilot checkpoint files.
- This `README.md` is intended to describe the **current project reality**: what is already in place, what technical directions were taken, and what still needs work.
