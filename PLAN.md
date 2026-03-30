# GPX Video Producer — Android Native App

## 1. Vision & Problem Statement

Sport enthusiasts (cyclists, runners, hikers, skiers, etc.) capture hours of video and hundreds of photos during events, but lack a mobile-first tool to combine that media with their GPS/sensor data into polished recap videos. Existing solutions either require desktop software, offer limited GPX integration, or lack a proper timeline-based editing experience.

**GPX Video Producer** is an Android-native video editor purpose-built for this use case. It provides:
- A familiar timeline-based editor (like PowerDirector / YouCut)
- Deep GPX integration with both static and dynamic overlays
- A template system for reusable video styles
- On-device processing for privacy and offline use

---

## 2. Technology Stack

| Layer | Technology | Rationale |
|---|---|---|
| Language | Kotlin 2.0+ | Modern, concise, first-class Android support |
| UI Framework | Jetpack Compose + Material 3 | Declarative, performant, modern design system |
| Architecture | MVVM + Clean Architecture | Separation of concerns, testability |
| DI | Hilt (Dagger) | Standard Android DI, compile-time safe |
| Database | Room (SQLite) | Offline-first, reactive queries via Flow |
| Media Storage | Scoped Storage + app-internal dirs | Android best practices for media |
| Video Engine | FFmpeg via ffmpeg-kit (LGPL) | Mature, wide format support, overlay filters |
| Video Preview | ExoPlayer (Media3) | Low-latency preview, seek, frame extraction |
| GPX Parsing | Custom Kotlin parser + XmlPullParser | Lightweight, no external deps for core format |
| Canvas/Overlay | Jetpack Compose Canvas + Android Canvas | GPU-accelerated drawing for overlays |
| Image Loading | Coil 3 | Compose-native, performant |
| Async | Kotlin Coroutines + Flow | Structured concurrency |
| Navigation | Compose Navigation (type-safe) | Single-activity architecture |
| Testing | JUnit 5 + Turbine + Compose Testing | Comprehensive test coverage |
| Build | Gradle (Kotlin DSL) + Version Catalogs | Modern dependency management |
| Min SDK | API 26 (Android 8.0) | ~95% device coverage |
| Target SDK | API 35 (latest) | Required for Play Store |

---

## 3. Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer                          │
│  Jetpack Compose Screens + ViewModels               │
│  (Home, Project Editor, Timeline, Preview, Export)   │
├─────────────────────────────────────────────────────┤
│                  Domain Layer                        │
│  Use Cases, Repository Interfaces, Domain Models     │
│  (ProjectManager, GpxProcessor, OverlayEngine,       │
│   ExportPipeline, TemplateEngine)                    │
├─────────────────────────────────────────────────────┤
│                   Data Layer                         │
│  Room DB, File System, FFmpeg Bridge, GPX Parser     │
│  (ProjectRepo, MediaRepo, GpxRepo, TemplateRepo)    │
└─────────────────────────────────────────────────────┘
```

### Module Structure (Gradle multi-module)

```
:app                    → Main application module, DI wiring, navigation
:core:model             → Domain models (Project, Track, Overlay, Template, etc.)
:core:database          → Room database, DAOs, entities, migrations
:core:common            → Shared utilities, extensions, constants
:core:ui                → Shared Compose components, theme, design system
:feature:home           → Home screen, project list, template gallery
:feature:project        → Project creation/editing, media import
:feature:timeline       → Timeline editor (tracks, overlays, trimming)
:feature:preview        → Real-time video preview with overlays
:feature:export         → Export pipeline, format selection, progress
:feature:gpx            → GPX import, parsing, visualization
:feature:overlays       → Overlay configuration (static + dynamic)
:feature:templates      → Template management (create, edit, apply)
:lib:ffmpeg             → FFmpeg wrapper, command builder, execution
:lib:gpx-parser         → GPX/TCX/FIT file parsing library
:lib:media-utils        → Thumbnail generation, media probing, frame extraction
```

---

## 4. Data Models

### 4.1 Core Entities

```kotlin
// === Project ===
data class Project(
    val id: UUID,
    val name: String,
    val description: String?,
    val sportType: SportType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val thumbnailPath: String?,
    val outputSettings: OutputSettings,
    val templateId: UUID?          // optional template reference
)

enum class SportType {
    CYCLING, RUNNING, HIKING, TRAIL_RUNNING,
    SKIING, SNOWBOARDING, SWIMMING,
    KAYAKING, CLIMBING, MULTI_SPORT, OTHER
}

data class OutputSettings(
    val resolution: Resolution,    // 1080p, 4K, custom
    val frameRate: Int,            // 24, 30, 60
    val format: ExportFormat,      // MP4_H264, MP4_H265, WEBM
    val bitrate: Long,             // in bps
    val audioCodec: AudioCodec     // AAC, OPUS
)

// === Media Items ===
data class MediaItem(
    val id: UUID,
    val projectId: UUID,
    val type: MediaType,           // VIDEO, IMAGE
    val sourcePath: String,        // original file URI
    val localCopyPath: String,     // app-internal copy
    val durationMs: Long?,         // null for images
    val width: Int,
    val height: Int,
    val rotation: Int,
    val createdAt: Instant,        // file creation timestamp
    val metadata: MediaMetadata    // EXIF, video codec info
)

// === GPX Data ===
data class GpxFile(
    val id: UUID,
    val projectId: UUID,
    val name: String,
    val filePath: String,
    val parsedData: GpxData?       // lazy-loaded parsed result
)

data class GpxData(
    val tracks: List<GpxTrack>,
    val waypoints: List<GpxWaypoint>,
    val bounds: GeoBounds,
    val totalDistance: Double,      // meters
    val totalElevationGain: Double, // meters
    val totalElevationLoss: Double,
    val totalDuration: Duration,
    val startTime: Instant?,
    val endTime: Instant?
)

data class GpxTrack(
    val name: String?,
    val segments: List<GpxSegment>
)

data class GpxSegment(
    val points: List<GpxPoint>
)

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,        // meters
    val time: Instant?,
    val heartRate: Int?,           // bpm (from extensions)
    val cadence: Int?,             // rpm
    val power: Int?,               // watts
    val temperature: Double?,      // celsius
    val speed: Double?             // m/s (computed or from extensions)
)

// === Timeline ===
data class Timeline(
    val id: UUID,
    val projectId: UUID,
    val totalDurationMs: Long,
    val tracks: List<TimelineTrack>
)

data class TimelineTrack(
    val id: UUID,
    val timelineId: UUID,
    val type: TrackType,           // VIDEO, AUDIO, OVERLAY, TEXT
    val order: Int,                // z-order (0 = bottom)
    val isLocked: Boolean,
    val isVisible: Boolean,
    val clips: List<TimelineClip>
)

data class TimelineClip(
    val id: UUID,
    val trackId: UUID,
    val mediaItemId: UUID?,        // null for generated content
    val startTimeMs: Long,         // position on timeline
    val endTimeMs: Long,
    val trimStartMs: Long,         // trim from source start
    val trimEndMs: Long,           // trim from source end
    val transitions: ClipTransitions,
    val adjustments: ClipAdjustments
)

data class ClipTransitions(
    val entryTransition: Transition?,
    val exitTransition: Transition?
)

data class Transition(
    val type: TransitionType,      // FADE, DISSOLVE, SLIDE, WIPE, CUT
    val durationMs: Long
)

data class ClipAdjustments(
    val volume: Float,             // 0.0 - 2.0
    val speed: Float,              // 0.25 - 4.0
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val rotation: Float,
    val scale: Float,
    val positionX: Float,
    val positionY: Float,
    val opacity: Float             // 0.0 - 1.0
)

// === Overlays ===
sealed class Overlay {
    abstract val id: UUID
    abstract val projectId: UUID
    abstract val name: String
    abstract val timelineClipId: UUID
    abstract val position: OverlayPosition
    abstract val size: OverlaySize
    abstract val style: OverlayStyle
}

data class OverlayPosition(
    val x: Float,                  // 0.0-1.0 relative to frame
    val y: Float,
    val anchor: Anchor             // TOP_LEFT, CENTER, etc.
)

data class OverlaySize(
    val width: Float,              // 0.0-1.0 relative to frame
    val height: Float
)

data class OverlayStyle(
    val backgroundColor: Int?,     // ARGB
    val borderColor: Int?,
    val borderWidth: Float,
    val cornerRadius: Float,
    val opacity: Float,
    val fontFamily: String?,
    val fontSize: Float?,
    val fontColor: Int?,
    val shadow: ShadowConfig?
)

// Static overlays — computed once, rendered as image
data class StaticAltitudeProfileOverlay(/* ... */) : Overlay()
data class StaticMapOverlay(/* ... */) : Overlay()
data class StaticStatsOverlay(
    val stats: List<StatField>     // TOTAL_DISTANCE, TOTAL_ELEVATION, AVG_PACE, etc.
) : Overlay()

// Dynamic overlays — re-rendered per frame based on GPX time
data class DynamicAltitudeProfileOverlay(
    val showCurrentPosition: Boolean,
    val highlightColor: Int,
    val trailColor: Int,
    val syncMode: SyncMode
) : Overlay()

data class DynamicMapOverlay(
    val mapStyle: MapStyle,        // MINIMAL, SATELLITE, TERRAIN, DARK
    val showCurrentPosition: Boolean,
    val showTrail: Boolean,
    val trailColor: Int,
    val zoomLevel: Float,
    val syncMode: SyncMode
) : Overlay()

data class DynamicStatOverlay(
    val field: DynamicField,       // CURRENT_PACE, CURRENT_SPEED, CURRENT_HR, etc.
    val syncMode: SyncMode,
    val format: String             // display format pattern
) : Overlay()

enum class SyncMode {
    GPX_TIMESTAMP,                 // auto-sync via GPX timestamps
    MANUAL_KEYFRAMES              // user defines keyframe mapping
}

enum class DynamicField {
    CURRENT_SPEED, CURRENT_PACE, CURRENT_ELEVATION,
    CURRENT_HEART_RATE, CURRENT_CADENCE, CURRENT_POWER,
    CURRENT_TEMPERATURE, CURRENT_GRADE,
    ELAPSED_TIME, ELAPSED_DISTANCE,
    REMAINING_TIME, REMAINING_DISTANCE
}

// === Templates ===
data class Template(
    val id: UUID,
    val name: String,
    val description: String?,
    val sportType: SportType?,     // null = universal
    val thumbnailPath: String?,
    val isBuiltIn: Boolean,
    val createdAt: Instant,
    val trackLayout: List<TemplateTrack>,
    val overlayPresets: List<OverlayPreset>,
    val outputSettings: OutputSettings,
    val stylePreset: StylePreset
)

data class OverlayPreset(
    val overlayType: String,       // class name of Overlay subtype
    val defaultPosition: OverlayPosition,
    val defaultSize: OverlaySize,
    val defaultStyle: OverlayStyle,
    val config: Map<String, Any>   // type-specific defaults
)

data class StylePreset(
    val primaryColor: Int,
    val secondaryColor: Int,
    val accentColor: Int,
    val fontFamily: String,
    val backgroundOverlayColor: Int,
    val transitionType: TransitionType,
    val transitionDurationMs: Long
)
```

### 4.2 Room Database Schema

```
projects
├── id (TEXT PK)
├── name (TEXT)
├── description (TEXT?)
├── sport_type (TEXT)
├── created_at (INTEGER)
├── updated_at (INTEGER)
├── thumbnail_path (TEXT?)
├── output_settings_json (TEXT)
└── template_id (TEXT? FK → templates.id)

media_items
├── id (TEXT PK)
├── project_id (TEXT FK → projects.id)
├── type (TEXT)
├── source_path (TEXT)
├── local_copy_path (TEXT)
├── duration_ms (INTEGER?)
├── width (INTEGER)
├── height (INTEGER)
├── rotation (INTEGER)
├── created_at (INTEGER)
└── metadata_json (TEXT)

gpx_files
├── id (TEXT PK)
├── project_id (TEXT FK → projects.id)
├── name (TEXT)
├── file_path (TEXT)
└── parsed_cache_path (TEXT?)

timeline_tracks
├── id (TEXT PK)
├── project_id (TEXT FK → projects.id)
├── type (TEXT)
├── order_index (INTEGER)
├── is_locked (INTEGER)
└── is_visible (INTEGER)

timeline_clips
├── id (TEXT PK)
├── track_id (TEXT FK → timeline_tracks.id)
├── media_item_id (TEXT? FK → media_items.id)
├── start_time_ms (INTEGER)
├── end_time_ms (INTEGER)
├── trim_start_ms (INTEGER)
├── trim_end_ms (INTEGER)
├── transitions_json (TEXT)
└── adjustments_json (TEXT)

overlays
├── id (TEXT PK)
├── project_id (TEXT FK → projects.id)
├── clip_id (TEXT FK → timeline_clips.id)
├── type (TEXT)
├── name (TEXT)
├── position_json (TEXT)
├── size_json (TEXT)
├── style_json (TEXT)
└── config_json (TEXT)

templates
├── id (TEXT PK)
├── name (TEXT)
├── description (TEXT?)
├── sport_type (TEXT?)
├── thumbnail_path (TEXT?)
├── is_built_in (INTEGER)
├── created_at (INTEGER)
├── track_layout_json (TEXT)
├── overlay_presets_json (TEXT)
├── output_settings_json (TEXT)
└── style_preset_json (TEXT)

sync_mappings
├── id (TEXT PK)
├── overlay_id (TEXT FK → overlays.id)
├── mode (TEXT)  -- GPX_TIMESTAMP or MANUAL_KEYFRAMES
├── gpx_file_id (TEXT FK → gpx_files.id)
├── keyframes_json (TEXT?)  -- for manual mode
└── time_offset_ms (INTEGER)  -- offset between video and GPX time
```

---

## 5. Feature Breakdown

### 5.1 Home Screen & Project Management

**Home Screen**
- Grid/list view of all projects with thumbnails, name, sport type, last edited date
- Sort by: name, date created, date modified, sport type
- Search/filter projects
- FAB button to create new project
- Long-press for context menu: duplicate, delete, export, share
- Empty state with onboarding illustration

**Project Creation Flow**
1. Enter project name
2. Select sport type (with icons)
3. Optionally select a template to start from
4. Choose output settings (resolution, frame rate, format) — defaults from template
5. Project created → navigate to editor

**Project Dashboard (optional intermediate screen)**
- Project info card (name, sport, created date)
- Quick stats: media count, total duration, GPX loaded
- Quick actions: Add Media, Add GPX, Open Editor, Export
- Last-edited preview thumbnail

### 5.2 Media Import & Management

**Import Sources**
- Device gallery (photo picker / SAF)
- Camera capture (direct from app)
- File manager (for GPX, TCX, FIT files)
- Drag-and-drop from split screen (Android 12+)

**Media Browser (within project)**
- Grid view of all imported media with thumbnails
- Filter by: videos only, images only, date range
- Sort by: date taken, name, duration, size
- Multi-select for batch operations (delete, add to timeline)
- Media detail panel: resolution, duration, size, date, GPS data if available
- Auto-detect media timestamps for GPX alignment

**Supported Input Formats**
- Video: MP4, MKV, MOV, AVI, WebM (anything FFmpeg supports)
- Image: JPEG, PNG, WebP, HEIF
- GPS: GPX, TCX, FIT (with parsing adapters)

### 5.3 GPX Processing Engine

**GPX Parser**
- Full GPX 1.1 spec support
- Extension parsing (Garmin, Polar, Wahoo, Strava extensions)
- Heart rate, cadence, power, temperature extraction
- Speed computation (from point-to-point or from extensions)
- Pace computation (min/km or min/mi, configurable)
- Elevation smoothing (configurable algorithm: none, rolling average, Kalman)
- Distance calculation (Haversine or Vincenty formula)
- Grade/slope calculation
- Segment detection (auto-pause detection)
- Lap detection (if present in data)

**TCX Parser Adapter**
- Convert TCX structure to internal GpxData model
- Handle Garmin-specific extensions

**FIT Parser Adapter**
- Binary FIT protocol parsing
- Map FIT records to GpxPoint model

**Computed Statistics**
- Total distance
- Total elapsed time / moving time
- Total elevation gain / loss
- Average / max / min speed
- Average / max / min pace
- Average / max / min heart rate
- Average / max / min cadence
- Average / max / min power
- Average / max / min temperature
- Average grade (uphill / downhill)
- Estimated calories (if HR + weight available)
- Normalized power (cycling)
- Training effect zones (HR zones)

**GPX-to-Video Time Synchronization**
- Auto-sync: Match GPX start time to video file creation time
- Manual offset: User adjusts time offset with preview
- Keyframe mapping: User marks specific points in video and maps to GPX positions
- Stretch/compress: Handle GPX and video duration mismatches

### 5.4 Timeline Editor

This is the core UI — a horizontal, scrollable, zoomable multi-track timeline.

**Timeline Layout**
```
┌──────────────────────────────────────────────┐
│              VIDEO PREVIEW                    │  ← Top: live preview
│              (16:9 frame)                     │
│    [overlays rendered on top of frame]        │
├──────────────────────────────────────────────┤
│  ◀ 00:00:32 / 00:05:17  ▶  ▶▶  ⏸           │  ← Playback controls
├──────────────────────────────────────────────┤
│  🔍 [--------|----------] zoom slider        │  ← Timeline zoom
├──────────────────────────────────────────────┤
│  ▼ Video 1   [████████████████              ]│  ← Video track
│  ▼ Video 2   [     █████████                ]│  ← Second video track
│  ▼ Images    [  ■     ■    ■       ■        ]│  ← Image track
│  ▼ Audio     [████████████████████████████  ]│  ← Audio track
│  ▼ Overlay 1 [  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓           ]│  ← Alt. profile overlay
│  ▼ Overlay 2 [  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓           ]│  ← Map overlay
│  ▼ Overlay 3 [  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓           ]│  ← Stats overlay
│  ▼ Text      [    TTTTTT     TTTTT          ]│  ← Text overlay
├──────────────────────────────────────────────┤
│  [+ Add Track]  [🎬 Media] [📊 Overlay]     │  ← Add actions
│  [📝 Text]  [🎵 Audio]  [⚙️ Settings]       │
└──────────────────────────────────────────────┘
```

**Timeline Features**
- Horizontal scroll with inertia
- Pinch-to-zoom (time scale)
- Playhead (vertical line) with frame-accurate scrubbing
- Drag clips to reposition
- Drag clip edges to trim
- Split clip at playhead
- Copy/paste clips
- Snap-to-grid (configurable grid size)
- Snap-to-clip-edges
- Track reordering (drag handle)
- Track visibility toggle (eye icon)
- Track lock toggle (lock icon)
- Undo/redo stack (minimum 50 steps)
- Waveform display for audio/video tracks
- Thumbnail strip for video tracks

**Clip Operations**
- Trim (start/end)
- Split
- Delete
- Duplicate
- Speed change (0.25x - 4x)
- Volume adjustment
- Basic color adjustments (brightness, contrast, saturation)
- Position/scale/rotation (for picture-in-picture)
- Opacity
- Transitions between clips (fade, dissolve, slide, wipe)

**Image Clips**
- Default duration (configurable, default 5s)
- Ken Burns effect (pan + zoom animation)
- Custom pan/zoom keyframes
- Fit/fill/stretch scaling modes

### 5.5 Overlay System

#### 5.5.1 Static Overlays (rendered once as images)

**Static Altitude Profile**
- Full ride/activity elevation profile chart
- Customizable colors (line, fill, background)
- Optional grid lines
- Axis labels (distance or time on X, elevation on Y)
- Configurable units (m/ft, km/mi)
- Gradient fill below line
- Optional markers for waypoints

**Static GPS Map**
- Route rendered on a minimal basemap (offline vector tiles or simple lat/lng plot)
- Customizable line color and width
- Optional start/end markers
- Optional distance markers
- Configurable map style (minimal line-only, with terrain, dark mode)
- Bounding box auto-fit

**Static Statistics Panel**
- Configurable grid of stat fields:
  - Total Distance
  - Total Time / Moving Time
  - Total Elevation Gain / Loss
  - Average Speed / Max Speed
  - Average Pace / Best Pace
  - Average HR / Max HR
  - Average Cadence
  - Average Power / Normalized Power
  - Calories
  - Average Temperature
- Customizable layout (1×1, 2×2, 3×2, 4×2 grids, or vertical list)
- Unit system toggle (metric / imperial)
- Sport-specific defaults (e.g., cycling shows power, running shows pace)

#### 5.5.2 Dynamic Overlays (re-rendered per frame, synced to GPX)

**Dynamic Altitude Profile**
- Same as static BUT with a moving marker showing current position
- Trail behind marker in different color
- Current elevation value displayed
- Smooth animation between GPX points
- Sync mode: GPX timestamp or manual keyframes

**Dynamic GPS Map**
- Route with animated current-position dot
- Trail drawn progressively as "video plays"
- Map can optionally pan/zoom to follow position
- Direction indicator (arrow at current position)
- Mini-map variant (small, corner-positioned)

**Dynamic Speed/Pace Gauge**
- Analog gauge or digital readout
- Customizable ranges and colors (zone-based coloring)
- Smooth interpolation between data points
- Unit display (km/h, mph, min/km, min/mi)

**Dynamic Heart Rate**
- Current BPM display
- Optional HR zone color indicator
- Optional mini heart rate graph (last N seconds)
- Heart animation at current rate (visual flair)

**Dynamic Cadence / Power / Temperature**
- Same pattern: current value + optional mini graph
- Zone-based coloring for power (FTP zones)
- Unit labels

**Dynamic Elapsed Stats**
- Elapsed time
- Elapsed distance
- Remaining time / distance (if known total)
- Running average speed/pace

**Dynamic Grade/Slope Indicator**
- Current grade percentage
- Visual slope indicator (tilting line or gauge)
- Color coding (green = flat, yellow = moderate, red = steep)

#### 5.5.3 Text Overlays
- Free-text entry
- Font selection (bundled fonts + system fonts)
- Size, color, shadow, outline
- Background box with configurable opacity
- Position (drag-to-place on preview)
- Animation: fade in/out, slide, typewriter
- Duration on timeline

#### 5.5.4 Overlay Editor Panel
- When an overlay is selected on timeline, a config panel slides up
- Real-time preview updates as settings change
- Presets for common configurations
- Copy style between overlays
- Position/size via drag on preview or numeric input

### 5.6 Preview Engine

**Real-time Preview**
- ExoPlayer-based video playback
- Compose Canvas overlay rendering on top of video frame
- Frame-accurate seeking
- Play/pause/seek controls
- Playback speed control (0.5x, 1x, 2x)
- Full-screen preview mode
- Preview at reduced resolution for performance (configurable)

**Preview Rendering Pipeline**
1. ExoPlayer decodes current video frame
2. For each visible overlay at current timestamp:
   a. Compute GPX data point (interpolated) for current time
   b. Render overlay to Canvas/Bitmap
   c. Composite onto preview surface
3. Display composited frame

**Performance Optimizations**
- Cache static overlay renders
- Pre-compute GPX interpolation LUT (lookup table)
- Render overlays at preview resolution (not export resolution)
- Background thread for overlay computation
- Frame skip if rendering can't keep up with playback

### 5.7 Template System

**Built-in Templates**
- "Cycling Classic" — map + altitude + speed + HR, clean white style
- "Trail Runner" — altitude + pace + elevation gain, earthy tones
- "Hiker's Journal" — map + photos slideshow + stats, nature green
- "Ski Day" — speed gauge + altitude + temperature, cool blue
- "Minimalist" — just a small map + elapsed time, dark overlay
- "Full Dashboard" — all dynamic stats in a dashboard layout
- "Photo Slideshow" — images with Ken Burns, stats at end
- "Race Recap" — splits, pace chart, finish time highlight

**Template Features**
- Templates define:
  - Default track layout (how many tracks, what types)
  - Overlay presets (which overlays, where positioned, what style)
  - Style preset (colors, fonts, transitions)
  - Output settings (resolution, framerate)
- Create template from existing project ("Save as Template")
- Edit template directly (standalone template editor)
- Apply template to existing project (merges/replaces overlays)
- Template gallery with preview thumbnails
- Template categories by sport type
- Import/export templates as JSON files (for sharing)

### 5.8 Export Pipeline

**Export Flow**
1. User taps "Export" button
2. Export settings dialog:
   - Format: MP4 (H.264), MP4 (H.265/HEVC), WebM (VP9)
   - Resolution: 720p, 1080p, 1440p, 4K, custom
   - Frame rate: 24, 30, 60
   - Quality/bitrate: Low, Medium, High, Custom
   - Audio: AAC 128k, AAC 256k, Opus
3. User confirms → export begins
4. Progress screen with:
   - Progress bar (percentage)
   - Estimated time remaining
   - Current phase (preparing, rendering, encoding, finalizing)
   - Cancel button
   - Preview of frames being rendered
5. Export complete → share/save dialog

**Export Technical Pipeline**
1. **Prepare**: Validate timeline, resolve all media paths
2. **Render Overlays**: For each frame:
   a. Compute timestamp
   b. Look up GPX data point
   c. Render all dynamic overlays to PNG sequence (or pipe directly)
3. **Build FFmpeg Command**:
   a. Input: video files with trim/speed adjustments
   b. Overlay filter chain: position each overlay
   c. Transition filters between clips
   d. Audio mixing and volume adjustments
   e. Output codec and container settings
4. **Execute FFmpeg**: Run via ffmpeg-kit with progress callback
5. **Finalize**: Write metadata, generate thumbnail, update project

**FFmpeg Filter Graph (conceptual)**
```
[video1] trim=10:60, setpts → [v1]
[video2] trim=0:45, setpts → [v2]
[v1][v2] concat=n=2:v=1:a=1 → [base]
[overlay_altitude] overlay=x=50:y=600 → [with_alt]
[overlay_map] overlay=x=800:y=50 → [with_map]
[overlay_speed] overlay=x=800:y=600 → [final]
[final] output -c:v libx264 -crf 18 output.mp4
```

For dynamic overlays, we pre-render each overlay as a frame sequence, then use FFmpeg's overlay filter with the sequence as input.

### 5.9 Settings & Preferences

**App Settings**
- Default units (metric/imperial)
- Default sport type
- Default output settings
- Theme (light/dark/system)
- Storage location preference
- Auto-save interval
- Max undo history size
- Preview quality (low/medium/high)
- Cache size limit

**About / Legal**
- App version
- FFmpeg license (LGPL compliance)
- Open source licenses
- Privacy policy link

---

## 6. UI/UX Design Specifications

### 6.1 Design Language
- Material 3 (Material You) with dynamic color
- Custom color scheme overrides for the "sporty" feel
- Dark mode as default (video editors are traditionally dark)
- Accent color: energetic orange/yellow (configurable)
- Typography: Inter or Roboto for UI, monospace for stats

### 6.2 Key Screen Flows

```
App Launch
  └→ Home Screen (Project List)
       ├→ Create New Project
       │    ├→ Name & Sport Type
       │    ├→ Choose Template (optional)
       │    └→ → Project Editor
       ├→ Open Existing Project → Project Editor
       └→ Template Gallery
            ├→ Browse Templates
            ├→ Create New Template
            └→ Edit Template

Project Editor (main workspace)
  ├→ Media Browser (import/manage media)
  ├→ GPX Manager (import/view GPX files)
  ├→ Timeline Editor (core editing)
  │    ├→ Add Clip (from media browser)
  │    ├→ Add Overlay (from overlay catalog)
  │    ├→ Clip Properties (trim, speed, color)
  │    └→ Overlay Properties (config panel)
  ├→ Preview (fullscreen preview)
  ├→ Project Settings
  └→ Export
```

### 6.3 Responsive Layout
- Portrait: stacked layout (preview on top, timeline below)
- Landscape: expanded timeline, wider preview
- Tablet: side-by-side panels, more timeline tracks visible
- Foldable support: preview on one screen, timeline on other

### 6.4 Gestures
- Pinch: zoom timeline
- Drag: move clips, scroll timeline
- Long press: context menu on clips
- Double tap: toggle preview fullscreen
- Two-finger drag: scroll timeline without selecting
- Edge swipe: navigate between editor panels

---

## 7. Development Phases

### Phase 1: Foundation & Project Management
- Android project setup (multi-module Gradle)
- Design system & theme (Material 3)
- Room database with all entities
- Home screen with project CRUD
- Navigation infrastructure
- Basic settings screen

### Phase 2: Media Import & Management
- Media picker integration (photo picker API)
- Media import with local copy
- Thumbnail generation
- Media browser within project
- Media metadata extraction (duration, resolution, codec)
- File size management

### Phase 3: GPX Processing Engine
- GPX parser (full 1.1 spec)
- TCX parser adapter
- FIT parser adapter (basic)
- Statistics computation engine
- Elevation smoothing algorithms
- Speed/pace/grade calculations
- GPX data caching (parsed → protobuf or JSON cache)
- GPX preview screen (basic map + stats view)

### Phase 4: Timeline Editor — Core
- Timeline UI component (custom Compose layout)
- Horizontal scroll + zoom
- Playhead with scrubbing
- Video track with thumbnail strips
- Image track
- Clip drag-to-reposition
- Clip trim (drag edges)
- Split/delete/duplicate operations
- Undo/redo system
- Snap-to-grid and snap-to-edges

### Phase 5: Preview Engine
- ExoPlayer integration for video playback
- Frame-accurate seeking
- Play/pause/speed controls
- Canvas overlay rendering on preview
- Basic compositing pipeline
- Preview performance optimization

### Phase 6: Static Overlays
- Overlay rendering engine (Canvas-based)
- Static altitude profile chart
- Static GPS map/route
- Static statistics panel
- Overlay placement UI (drag on preview)
- Overlay configuration panel
- Overlay styling (colors, fonts, sizes)

### Phase 7: Dynamic Overlays
- GPX-to-video time synchronization engine
- Auto-sync (timestamp matching)
- Manual offset adjustment
- Keyframe-based mapping
- Dynamic altitude profile with marker
- Dynamic map with animated position
- Dynamic speed/pace display
- Dynamic heart rate display
- Dynamic cadence/power/temperature
- Dynamic elapsed stats
- Dynamic grade indicator
- Interpolation and smoothing for animations

### Phase 8: Audio & Transitions
- Audio track support
- Audio waveform display
- Volume adjustment per clip
- Audio mixing (multiple tracks)
- Clip transitions (fade, dissolve, wipe, slide)
- Image Ken Burns effect
- Transition configuration UI

### Phase 9: Template System
- Template data model and storage
- Built-in template definitions
- Template gallery UI
- Apply template to project
- Save project as template
- Template editor
- Import/export templates (JSON)

### Phase 10: Export Pipeline
- FFmpeg command builder
- Overlay pre-rendering pipeline (frame sequences)
- Video concatenation with transitions
- Overlay compositing via FFmpeg filters
- Audio mixing in export
- Export progress UI
- Export to MP4 (H.264)
- Export to MP4 (H.265/HEVC)
- Export to WebM (VP9)
- Resolution/bitrate/quality options
- Foreground service for export

### Phase 11: Polish & Optimization
- Performance profiling and optimization
- Memory management for large projects
- Cache management
- Error handling and recovery
- Crash reporting integration
- Analytics (optional)
- App icon and branding
- Splash screen
- Onboarding tutorial
- Accessibility (TalkBack, font scaling)
- ProGuard/R8 optimization
- Play Store listing preparation

---

## 8. Key Technical Challenges & Solutions

### 8.1 Dynamic Overlay Rendering Performance
**Challenge**: Rendering multiple dynamic overlays per frame at 30fps during preview.
**Solution**:
- Pre-compute GPX interpolation lookup tables at project load
- Use off-screen Canvas rendering on background coroutine
- Cache overlay bitmaps when GPX data hasn't changed between frames
- Reduce preview resolution (render at 540p, display upscaled)
- Frame-skip mechanism when rendering falls behind

### 8.2 GPX-Video Time Synchronization
**Challenge**: GPX timestamps rarely match video file timestamps exactly.
**Solution**:
- Primary: Use video file creation time + GPX start time to compute offset
- Secondary: Manual offset slider with real-time preview (±hours, ±minutes, ±seconds)
- Tertiary: Keyframe mapping — user marks "I'm at this trail marker in the video" and links to GPX point
- Visual feedback: show GPS speed vs video motion to help user align

### 8.3 Large File Handling
**Challenge**: Sports videos can be many GB; GPX files with 1-second recording can have 100K+ points.
**Solution**:
- Stream-based GPX parsing (SAX/pull parser, not DOM)
- GPX point decimation for rendering (Douglas-Peucker for map, LTTB for charts)
- Video thumbnails generated lazily and cached
- Media imported as references with on-demand copying
- FFmpeg operations use file-based I/O (no full video in memory)

### 8.4 FFmpeg Filter Graph Complexity
**Challenge**: Building correct FFmpeg filter graphs for complex timelines.
**Solution**:
- Dedicated `FilterGraphBuilder` class with type-safe API
- Unit tests for every filter graph pattern
- Intermediate validation step before execution
- Fallback: simpler two-pass approach (render overlays first, composite second)

### 8.5 Offline Map Rendering
**Challenge**: Rendering GPS routes on maps without internet.
**Solution**:
- Phase 1: Simple route-only rendering (plot lat/lng on blank canvas with grid)
- Phase 2: Optional offline vector tile support (Mapbox SDK offline or OSM tiles)
- Phase 3: Pre-rendered map tile caching from online sources
- User always has route-only fallback

---

## 9. Testing Strategy

### Unit Tests
- GPX parser: parse sample files, validate all fields
- Statistics engine: verify calculations against known data
- Timeline model: clip operations (trim, split, move, overlap resolution)
- FFmpeg command builder: verify generated commands
- Overlay rendering: snapshot tests for static overlays
- Template serialization: round-trip tests

### Integration Tests
- Room database: DAO operations, migrations
- Media import pipeline: file copy, metadata extraction
- GPX → overlay pipeline: parse, compute, render
- Export pipeline: simple project → valid output file

### UI Tests (Compose)
- Home screen: project CRUD operations
- Timeline: clip manipulation gestures
- Overlay configuration: settings changes reflect in preview
- Export flow: settings → progress → completion

### Manual / QA Testing
- Real GPX files from various devices (Garmin, Wahoo, Apple Watch, Strava)
- Large projects (30+ clips, 5+ overlays)
- Various video formats and resolutions
- Different Android devices (phone, tablet, foldable)
- Memory pressure testing
- Export quality verification

---

## 10. File Structure (Initial Setup)

```
gpx-video-producer/
├── app/
│   ├── src/main/
│   │   ├── java/com/gpxvideo/app/
│   │   │   ├── GpxVideoApp.kt           (Application class)
│   │   │   ├── MainActivity.kt
│   │   │   ├── navigation/
│   │   │   │   └── AppNavigation.kt
│   │   │   └── di/
│   │   │       └── AppModule.kt
│   │   └── res/
│   └── build.gradle.kts
├── core/
│   ├── model/
│   │   └── src/main/java/com/gpxvideo/core/model/
│   │       ├── Project.kt
│   │       ├── MediaItem.kt
│   │       ├── GpxData.kt
│   │       ├── Timeline.kt
│   │       ├── Overlay.kt
│   │       └── Template.kt
│   ├── database/
│   │   └── src/main/java/com/gpxvideo/core/database/
│   │       ├── AppDatabase.kt
│   │       ├── dao/
│   │       ├── entity/
│   │       └── converter/
│   ├── common/
│   │   └── src/main/java/com/gpxvideo/core/common/
│   │       ├── extensions/
│   │       ├── utils/
│   │       └── result/
│   └── ui/
│       └── src/main/java/com/gpxvideo/core/ui/
│           ├── theme/
│           ├── components/
│           └── icons/
├── feature/
│   ├── home/
│   ├── project/
│   ├── timeline/
│   ├── preview/
│   ├── export/
│   ├── gpx/
│   ├── overlays/
│   └── templates/
├── lib/
│   ├── ffmpeg/
│   ├── gpx-parser/
│   └── media-utils/
├── gradle/
│   └── libs.versions.toml          (version catalog)
├── build.gradle.kts                 (root)
├── settings.gradle.kts
└── gradle.properties
```

---

## 11. Dependencies (Version Catalog Preview)

```toml
[versions]
kotlin = "2.1.0"
compose-bom = "2025.02.00"
hilt = "2.53"
room = "2.7.0"
media3 = "1.5.0"
ffmpeg-kit = "6.0-2"
coil = "3.0.4"
coroutines = "1.9.0"

[libraries]
# Compose
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-navigation = { group = "androidx.navigation", name = "navigation-compose" }

# DI
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

# Database
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# Media
media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }

# FFmpeg
ffmpeg-kit = { group = "com.arthenica", name = "ffmpeg-kit-full", version.ref = "ffmpeg-kit" }

# Image
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

# Coroutines
coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
```

---

## 12. Non-Functional Requirements

| Requirement | Target |
|---|---|
| App cold start | < 2 seconds |
| Preview latency (seek → frame) | < 200ms |
| Export speed (1080p, 5min video) | < 10 minutes on mid-range device |
| Max project media | 100+ clips |
| Max overlays per project | 20+ |
| APK size | < 50MB (FFmpeg adds ~30MB) |
| Min RAM usage during preview | < 512MB |
| Crash-free rate target | > 99.5% |
| Database migration | Always non-destructive |
| Undo history | ≥ 50 operations |

---

## 13. Future Enhancements (Post-V1)

- **Cloud sync**: Backup projects to Google Drive / cloud
- **Social sharing**: Direct export to YouTube, Instagram, Strava
- **Strava integration**: Import activities directly from Strava API
- **Garmin Connect integration**: Import from Garmin
- **AI-powered highlights**: Auto-detect exciting moments (speed changes, elevation peaks)
- **Music library**: Built-in royalty-free music tracks
- **Voice-over recording**: Record narration directly in app
- **Collaborative editing**: Share projects between users
- **iOS version**: Kotlin Multiplatform for shared logic
- **Wear OS companion**: Quick-start recording from watch
- **Live telemetry**: Real-time overlay during recording (future camera mode)
- **3D elevation visualization**: 3D terrain flyover from GPX data
- **Segment comparison**: Overlay two GPX tracks (e.g., race vs training)
- **Custom widget/overlay SDK**: Let users create custom overlay types
