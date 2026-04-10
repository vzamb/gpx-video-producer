# Creating Lottie Template Overlays

This guide explains how to create new overlay templates for the GPX Video Producer app.

## Overview

Templates are [Lottie](https://airbnb.io/lottie/) JSON files placed in `app/src/main/assets/templates/`. The app **automatically discovers** all templates in this directory at runtime — no code changes needed. Each template needs **at least 1 JSON file** — the app auto-scales it to all supported aspect ratios (9:16, 16:9, 1:1, 4:5). You can optionally provide ratio-specific files for pixel-perfect control.

## File Naming Convention

```
templates/{template_id}_{ratio}.json
```

- `{template_id}` — lowercase, underscore-separated (e.g. `cinematic`, `pro_dashboard`, `night_run`)
- `{ratio}` — one of: `9x16`, `16x9`, `1x1`, `4x5`

**Example for a new "trail" template:**
```
templates/trail_9x16.json     (1080×1920)
```

> **Single-file approach:** You only need to create **one ratio file** (typically 9:16). The app automatically scales and adapts it to all other aspect ratios at runtime. If you want pixel-perfect control for a specific ratio, you can provide additional ratio files — the app will prefer an exact match over auto-scaling.

## Canvas Dimensions

Each ratio uses a fixed canvas size:

| Ratio | Width | Height | Use Case |
|-------|-------|--------|----------|
| 9:16  | 1080  | 1920   | Instagram/TikTok Stories |
| 16:9  | 1920  | 1080   | YouTube landscape |
| 1:1   | 1080  | 1080   | Instagram feed square |
| 4:5   | 1080  | 1350   | Instagram feed portrait |

## Template Metadata

Add a `templateMeta` object at the root of each JSON:

```json
{
  "v": "5.7.4",
  "fr": 30,
  "ip": 0,
  "op": 30,
  "w": 1080,
  "h": 1920,
  "templateMeta": {
    "displayName": "Trail Runner",
    "description": "Scenic trail overlay with large elevation chart"
  },
  "layers": [ ... ],
  "fonts": { ... }
}
```

- `displayName` — shown below the template preview in the UI
- `description` — currently unused, reserved for future tooltip/info display

## Layer Types

Templates use standard Lottie layer types:

| Type (`ty`) | Name | Purpose |
|-------------|------|---------|
| 4 (shape)   | Cards, backgrounds, text, charts/maps | All visual elements — rectangles, text (vectorized as paths), chart/map regions |
| 5 (text)    | Stats, labels, title | Dynamic text bound at runtime (hand-written JSON or After Effects export) |
| 1 (solid)   | Placeholders (legacy) | Invisible regions for chart/map rendering |

> **Lottie Studio note:** When exporting from Lottie Studio, all layers are type 4 (shape) — including text and chart/map layers. The app handles this correctly: text layers named `stat_*`, `label_*`, or `title_*` are hidden from Lottie rendering and replaced with native text; chart/map layers are hidden and replaced with native data-driven renderers.

## Dynamic Text Layers

The renderer replaces text content in layers based on their `nm` (name) field. Use these exact names:

### Stat values (dynamic — updated every frame)

| Layer Name | Content | Example |
|------------|---------|---------|
| `stat_distance` | Distance in km | `12.50` |
| `stat_distance_unit` | Distance unit | `km` |
| `stat_elevation` | Cumulative elevation gain | `↑ 342` |
| `stat_elevation_unit` | Elevation unit | `m` |
| `stat_pace` | Current pace | `5:23` |
| `stat_pace_unit` | Pace unit | `/km` |
| `stat_hr` | Heart rate | `152` |
| `stat_hr_unit` | HR unit | `bpm` |
| `stat_time` | Elapsed time | `1:23:45` |
| `stat_grade` | Current grade | `+4.2%` |
| `stat_speed` | Speed in km/h | `11.2` |
| `stat_speed_unit` | Speed unit | `km/h` |

### Static labels

| Layer Name | Default Text |
|------------|-------------|
| `label_distance` | `DISTANCE` |
| `label_elevation` | `ELEVATION` |
| `label_pace` | `PACE` |
| `label_hr` | `HEART RATE` |
| `label_time` | `TIME` |
| `label_grade` | `GRADE` |
| `label_speed` | `SPEED` |

### Title

| Layer Name | Content |
|------------|---------|
| `title_text` | User-defined activity title |

> **Note:** You don't need to include all layers. Only include the stats you want to display. Layers not present in the JSON are simply not shown.

## Text Layer Structure

Each text layer follows this Lottie JSON structure:

```json
{
  "ddd": 0,
  "ind": 5,
  "ty": 5,
  "nm": "stat_distance",
  "sr": 1,
  "ks": {
    "o": { "a": 0, "k": 100 },
    "p": { "a": 0, "k": [84, 1284, 0] },
    "a": { "a": 0, "k": [0, 0, 0] },
    "s": { "a": 0, "k": [100, 100, 100] }
  },
  "ip": 0,
  "op": 30,
  "st": 0,
  "t": {
    "d": {
      "k": [{
        "s": {
          "s": 84,
          "f": "SansBold",
          "t": "0.0",
          "j": 0,
          "tr": 0,
          "lh": 101,
          "ls": 0,
          "fc": [1.0, 1.0, 1.0]
        },
        "t": 0
      }]
    },
    "a": [],
    "m": { "g": 1, "a": { "a": 0, "k": [0, 0] } }
  }
}
```

Key fields in `t.d.k[0].s`:
- `s` — font size (px)
- `f` — font name (must be declared in `fonts.list`)
- `t` — placeholder text (replaced at runtime)
- `j` — justification (0=left, 1=right, 2=center)
- `fc` — font color as [R, G, B] (0.0–1.0)

## Chart & Map Layers (Design-Driven)

The app renders data-driven charts and maps (elevation profile, route map) using styles extracted directly from your Lottie design. Instead of invisible placeholder boxes, you create **fully styled visual mockups** using named sub-groups inside the layer. The app reads the colors, sizes, and opacities from your design elements and uses them to render the live data visualization.

### Layer Names

| Layer Name | Also accepts (legacy) | Rendering |
|------------|----------------------|-----------|
| `elevation_chart` | `placeholder_elevation_chart` | Elevation profile with area gradient + progress dot |
| `route_map` | `placeholder_route_map` | Route map with visited/unvisited path + position dot |

### Architecture: Named Sub-Groups

Each chart/map layer is a **shape layer** (`ty: 4`) containing named groups (`ty: "gr"`). Each group represents a specific visual element of the chart. The app extracts the style (colors, sizes, opacities) from each group and draws the live data using those exact styles.

**Elevation chart groups:**

| Group Name | Purpose | Key Properties Read |
|------------|---------|-------------------|
| `background` | Chart background card | Rectangle → bounds & corner radius; Fill → bg color & opacity |
| `line` | Visited elevation trace | Stroke → line color & width |
| `area` | Gradient fill under the line | Fill → area color & opacity |
| `full_path` | Unvisited (future) portion | Stroke → line color, width & opacity |
| `dot` | Current position indicator | Ellipse → dot radius; Fill → dot color |
| `glow` | Outer glow around the dot | Ellipse → glow radius; Fill → glow color & opacity |

**Route map groups:**

| Group Name | Purpose | Key Properties Read |
|------------|---------|-------------------|
| `background` | Map background card | Rectangle → bounds & corner radius; Fill → bg color & opacity |
| `route` | Visited route path | Stroke → line color & width |
| `full_route` | Full unvisited route | Stroke → line color, width & opacity |
| `dot` | Current position marker | Ellipse → dot radius; Fill → dot color |
| `glow` | Outer glow around marker | Ellipse → glow radius; Fill → glow color & opacity |

### Creating a Chart Layer in Lottie Studio

1. **Create a shape layer** and name it `elevation_chart` (or `route_map`)
2. **Inside the layer, create named groups** (each group is a visual mockup element):

```
elevation_chart (Shape Layer)
  ├── background (Group)
  │   ├── Rectangle — defines the chart bounds (size = chart area)
  │   ├── Fill — background color and opacity
  │   └── Transform
  ├── line (Group)
  │   ├── Rectangle — mockup path (any shape, just carries the stroke)
  │   ├── Stroke — the line color and width
  │   └── Transform
  ├── area (Group)
  │   ├── Rectangle — mockup area shape
  │   ├── Fill — gradient area color and opacity
  │   └── Transform
  ├── full_path (Group)
  │   ├── Rectangle — mockup for unvisited portion
  │   ├── Stroke — color, width, and opacity for unvisited line
  │   └── Transform
  ├── dot (Group)
  │   ├── Ellipse — size defines the dot radius
  │   ├── Fill — dot color
  │   └── Transform
  └── glow (Group)
      ├── Ellipse — size defines the glow radius
      ├── Fill — glow color and opacity
      └── Transform
```

3. **Position the layer** at the correct location on your canvas (using layer transform `ks.p`)

### JSON Example — Elevation Chart

```json
{
  "ty": 4,
  "nm": "elevation_chart",
  "ks": {
    "o": { "a": 0, "k": 100 },
    "p": { "a": 0, "k": [540, 1820, 0] },
    "s": { "a": 0, "k": [100, 100] }
  },
  "shapes": [
    {
      "ty": "gr", "nm": "background", "bm": 0, "hd": false,
      "it": [
        { "ty": "rc", "nm": "Bounds", "d": 1,
          "p": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [1000, 200] },
          "r": { "a": 0, "k": 8 } },
        { "ty": "fl", "nm": "BgFill", "bm": 0, "r": 1,
          "c": { "a": 0, "k": [0.06, 0.06, 0.08] },
          "o": { "a": 0, "k": 20 } },
        { "ty": "tr", "p": { "a": 0, "k": [0, 0] },
          "a": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [100, 100] },
          "o": { "a": 0, "k": 100 },
          "r": { "a": 0, "k": 0 } }
      ]
    },
    {
      "ty": "gr", "nm": "line", "bm": 0, "hd": false,
      "it": [
        { "ty": "rc", "nm": "LineShape", "d": 1,
          "p": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [900, 3] },
          "r": { "a": 0, "k": 0 } },
        { "ty": "st", "nm": "LineStroke", "bm": 0, "lc": 2, "lj": 2, "ml": 0,
          "c": { "a": 0, "k": [0.267, 0.867, 0.467] },
          "o": { "a": 0, "k": 100 },
          "w": { "a": 0, "k": 3 } },
        { "ty": "tr", "p": { "a": 0, "k": [0, 0] },
          "a": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [100, 100] },
          "o": { "a": 0, "k": 100 },
          "r": { "a": 0, "k": 0 } }
      ]
    },
    {
      "ty": "gr", "nm": "area", "bm": 0, "hd": false,
      "it": [
        { "ty": "rc", "nm": "AreaShape", "d": 1,
          "p": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [500, 120] },
          "r": { "a": 0, "k": 0 } },
        { "ty": "fl", "nm": "AreaFill", "bm": 0, "r": 1,
          "c": { "a": 0, "k": [0.267, 0.867, 0.467] },
          "o": { "a": 0, "k": 30 } },
        { "ty": "tr", "p": { "a": 0, "k": [0, 0] },
          "a": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [100, 100] },
          "o": { "a": 0, "k": 100 },
          "r": { "a": 0, "k": 0 } }
      ]
    },
    {
      "ty": "gr", "nm": "dot", "bm": 0, "hd": false,
      "it": [
        { "ty": "el", "nm": "DotCircle", "d": 1,
          "p": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [10, 10] } },
        { "ty": "fl", "nm": "DotFill", "bm": 0, "r": 1,
          "c": { "a": 0, "k": [1, 1, 1] },
          "o": { "a": 0, "k": 100 } },
        { "ty": "tr", "p": { "a": 0, "k": [0, 0] },
          "a": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [100, 100] },
          "o": { "a": 0, "k": 100 },
          "r": { "a": 0, "k": 0 } }
      ]
    },
    {
      "ty": "gr", "nm": "glow", "bm": 0, "hd": false,
      "it": [
        { "ty": "el", "nm": "GlowCircle", "d": 1,
          "p": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [18, 18] } },
        { "ty": "fl", "nm": "GlowFill", "bm": 0, "r": 1,
          "c": { "a": 0, "k": [0.267, 0.867, 0.467] },
          "o": { "a": 0, "k": 40 } },
        { "ty": "tr", "p": { "a": 0, "k": [0, 0] },
          "a": { "a": 0, "k": [0, 0] },
          "s": { "a": 0, "k": [100, 100] },
          "o": { "a": 0, "k": 100 },
          "r": { "a": 0, "k": 0 } }
      ]
    }
  ]
}
```

### Design Tips

- **All groups are optional.** If you omit `dot`/`glow`, the app uses sensible defaults (white dot, semi-transparent glow).
- **The `background` group's rectangle defines the chart bounds.** Its size determines where the chart is drawn.
- **Colors are in [0–1] range** — e.g., `[0.267, 0.867, 0.467]` = green (#44DD77).
- **Opacity is 0–100** — e.g., `"o": { "a": 0, "k": 30 }` = 30% opacity.
- **Dot/glow radii come from the ellipse size.** An ellipse `"s": [10, 10]` gives a 5dp radius dot.
- **The layer is hidden** from Lottie's own rendering — the app draws charts natively in its place.

### Backward Compatibility

The app also supports two legacy approaches (auto-detected):

1. **Flat shapes** (no named groups): A layer containing only `rc`, `fl`, `st` at the top level. The fill becomes the area color and the stroke becomes the line color.
2. **Solid layers** (`ty: 1`): Legacy After Effects approach using `sw`/`sh` for dimensions. Uses default chart colors.

## Shape Layers (Cards & Backgrounds)

Cards are shape layers (`ty: 4`) containing rounded rectangles with fills and optional strokes:

```json
{
  "ty": 4,
  "nm": "card_distance",
  "shapes": [{
    "ty": "gr",
    "it": [
      {
        "ty": "rc",
        "nm": "Rect",
        "p": { "a": 0, "k": [0, 0] },
        "s": { "a": 0, "k": [200, 120] },
        "r": { "a": 0, "k": 12 }
      },
      {
        "ty": "fl",
        "nm": "Fill",
        "c": { "a": 0, "k": [0, 0, 0, 1] },
        "o": { "a": 0, "k": 50 }
      },
      {
        "ty": "tr",
        "p": { "a": 0, "k": [0, 0] },
        "a": { "a": 0, "k": [0, 0] },
        "s": { "a": 0, "k": [100, 100] }
      }
    ]
  }]
}
```

A gradient scrim for readability:

```json
{
  "ty": 4,
  "nm": "scrim",
  "shapes": [{
    "ty": "gr",
    "it": [
      { "ty": "rc", "s": { "a": 0, "k": [1080, 600] } },
      {
        "ty": "gf",
        "s": { "a": 0, "k": [0, 0] },
        "e": { "a": 0, "k": [0, -600] },
        "g": {
          "p": 2,
          "k": { "a": 0, "k": [0, 0, 0, 0, 1, 0, 0, 0] }
        },
        "o": { "a": 0, "k": 80 }
      },
      { "ty": "tr", "p": { "a": 0, "k": [0, 0] } }
    ]
  }]
}
```

## Fonts

Declare fonts in the `fonts` object:

```json
{
  "fonts": {
    "list": [
      {
        "fName": "SansBold",
        "fFamily": "sans-serif",
        "fStyle": "Bold",
        "ascent": 75
      },
      {
        "fName": "SansRegular",
        "fFamily": "sans-serif",
        "fStyle": "Regular",
        "ascent": 75
      },
      {
        "fName": "CondensedBold",
        "fFamily": "sans-serif-condensed",
        "fStyle": "Bold",
        "ascent": 75
      }
    ]
  }
}
```

The app uses system fonts. Available font families on Android:
- `sans-serif` (Roboto)
- `sans-serif-condensed` (Roboto Condensed)
- `monospace`
- `serif`

## Required Lottie Properties

Every template JSON must include:

```json
{
  "v": "5.7.4",
  "fr": 30,
  "ip": 0,
  "op": 30,
  "w": <canvas_width>,
  "h": <canvas_height>,
  "nm": "TemplateName",
  "ddd": 0,
  "assets": [],
  "fonts": { "list": [...] },
  "layers": [...],
  "templateMeta": {
    "displayName": "...",
    "description": "..."
  }
}
```

## Layer Ordering

Layers render **back to front** in the order listed. Typical order:
1. Background scrim/gradient (bottom)
2. Card shapes
3. Label text layers
4. Stat text layers (on top of cards)
5. Chart/map layers (elevation chart, route map)
6. Title text (topmost)

## Design Rules

1. **Unique `ind` values** — every layer must have a unique integer `ind` (index). Start from 0 and increment.

2. **All elements within canvas bounds** — ensure all layers fit within the canvas (w × h). For text, account for font size; for shapes, account for size + position.

3. **Consistent stats across ratios** — all 4 ratio files for a template should display the same stats, just laid out differently.

4. **Accent color** — the user-selected accent color is applied to label text. Design with this in mind (white stat values + colored labels is the standard pattern).

## Step-by-Step: Adding a New Template

1. **Create 1 JSON file** in `app/src/main/assets/templates/`:
   ```
   my_template_9x16.json
   ```

2. **Design the layout** for 9:16 (1080×1920) — the app auto-scales to other ratios. Optionally create additional ratio files for pixel-perfect control.

3. **Add `templateMeta`** to the file with `displayName` and `description`.

4. **Use recognized layer names** for text (`stat_*`, `label_*`, `title_text`) and chart/map layers (`elevation_chart`, `route_map`).

5. **Build and run** — the app will automatically discover your new template and show it in the template pager. No code changes required.

## Existing Templates (Reference)

| Template ID | Display Name | Description |
|------------|-------------|-------------|
| `cinematic` | Cinematic | Minimalist data cards nestled in the corner |
| `hero` | Hero | Massive distance tracking, centered and bold |
| `pro_dashboard` | Pro Dashboard | Full metrics panel with route map |
| `lottie_custom` | Lottie template | Custom design created in Lottie Studio |

You can study these existing JSON files as reference when creating new templates.

## Creating Templates with Lottie Studio (Recommended)

[Lottie Studio](https://www.lottielab.com/) is a web-based motion design tool that exports native Lottie JSON. It gives you full visual control over every element — colors, gradients, typography, shapes — and the exported file works directly in the app with no conversion needed.

### Why Lottie Studio?

- **Direct export** — no conversion scripts or post-processing
- **Full style control** — design exactly what you see
- **Chart styling** — define chart/map colors through named sub-groups inside chart layers
- **Single-file workflow** — create one ratio, the app scales the rest

### Prerequisites

- A [Lottie Studio](https://www.lottielab.com/) account (free tier available)
- Basic understanding of the layer naming conventions (above)

### Step 1: Create a New Composition

1. Open Lottie Studio and create a new project
2. Set the canvas size to **1080 × 1920** (9:16 vertical)
3. Set duration to 1 second at 30fps (the app uses a single frame)

### Step 2: Design the Background and Cards

1. **Background scrim:** Draw a full-width rectangle at the bottom of the canvas. Apply a gradient fill (transparent → semi-transparent black) for readability over video.

2. **Stat cards:** For each stat you want to display, draw a rectangle:
   - Style freely: custom fills, gradients, rounded corners, opacity, borders
   - Name each card with a `card_` prefix (e.g., `card_distance`, `card_hr`)

3. **Decorative elements:** Add any lines, shapes, badges, or visual flourishes you want. Name them descriptively (e.g., `separator_line`, `badge_bg`).

> **Important:** All non-text, non-chart layers render as pure Lottie visuals. The app preserves every fill, gradient, stroke, and opacity exactly as designed.

### Step 3: Add Text Layers

For each stat, add two text elements:

1. **Label** (small, uppercase): e.g., "DISTANCE"
   - Name exactly: `label_distance`
   - Style: smaller font, any color (labels receive the user's accent color at runtime)

2. **Value** (large, bold): e.g., "0.0"
   - Name exactly: `stat_distance`
   - Style: larger font, white recommended (replaced at runtime with live data)

3. **Title** (optional): e.g., "MY RIDE"
   - Name exactly: `title_text`
   - Style: any size/color (user can customize title text and color)

> **Layer names are critical.** The app identifies text layers by name to bind live data. Use the exact names from the [Dynamic Text Layers](#dynamic-text-layers) table. Layers with unrecognized names are rendered as static Lottie visuals.

### Step 4: Add Chart and Map Layers

Create chart/map layers with named sub-groups to define every visual detail:

1. **Elevation chart:**
   - Create a shape layer named `elevation_chart`
   - Inside it, create named groups: `background`, `line`, `area`, `full_path`, `dot`, `glow`
   - **`background`:** Rectangle (defines chart bounds) + Fill (bg color/opacity)
   - **`line`:** Stroke (chart line color & width)
   - **`area`:** Fill (gradient area color & opacity, e.g., 30%)
   - **`dot`:** Ellipse (dot size) + Fill (dot color, e.g., white)
   - **`glow`:** Ellipse (glow size) + Fill (glow color & opacity)

2. **Route map:**
   - Create a shape layer named `route_map`
   - Inside it, create named groups: `background`, `route`, `full_route`, `dot`, `glow`
   - **`background`:** Rectangle (map bounds) + Fill (bg color/opacity)
   - **`route`:** Stroke (visited route line color & width)
   - **`full_route`:** Stroke (unvisited route color & opacity)
   - **`dot`/`glow`:** Same as elevation chart

> The app hides these layers from Lottie rendering and draws live data-driven charts/maps using the exact styles you designed. See the [Chart & Map Layers](#chart--map-layers-design-driven) section for the full JSON structure.

### Step 5: Layer Ordering

In the Lottie Studio layers panel, arrange from **top to bottom** (top = frontmost):

```
title_text                       <-- text (frontmost)
stat_hr                          <-- text
label_hr                         <-- text
stat_pace                        <-- text
label_pace                       <-- text
stat_distance                    <-- text
label_distance                   <-- text
route_map                        <-- chart/map (rendered natively)
elevation_chart                  <-- chart/map (rendered natively)
card_hr                          <-- shape (visual card)
card_pace                        <-- shape
card_distance                    <-- shape
scrim_bottom                     <-- gradient scrim (bottommost)
```

### Step 6: Export

1. **Export as Lottie JSON** from Lottie Studio (File → Export → Lottie JSON)
2. Save the file in the project:
   ```
   app/src/main/assets/templates/my_template_9x16.json
   ```

3. **Add `templateMeta`** to the exported JSON at the root level:
   ```json
   {
     "v": "5.7.4",
     "fr": 30,
     ...
     "templateMeta": {
       "displayName": "My Template",
       "description": "Custom overlay design"
     }
   }
   ```

### Step 7: Build and Test

Build the app — your template automatically appears in the template pager on the Overlays screen:

```bash
./gradlew assembleDebug
```

Verify:
- All text layers show live data
- Chart colors match your design
- Map renders in the correct position with your colors
- Layout works across all aspect ratios (try switching in the ratio selector)

### Tips for Lottie Studio Templates

- **One file is enough** — create the 9:16 version and the app auto-scales to 16:9, 1:1, and 4:5
- **Test chart styling** — the named sub-groups control every visual aspect: line colors, area gradients, dot/glow colors
- **Keep it single-frame** — the app doesn't use Lottie animations; all motion comes from live data updates
- **Use system fonts** — the app replaces text with native rendering using Android system fonts (`sans-serif`, `sans-serif-condensed`, `monospace`, `serif`)
- **Transparent background** — don't add a solid background fill to the root canvas; the overlay renders on top of video

## Creating Templates with After Effects

You can design templates in After Effects and export them as Lottie JSON using the [Bodymovin plugin](https://aescripts.com/bodymovin/):

1. Create a comp at the target canvas size (e.g. 1080×1920 for 9:16)
2. Design your layout using shape layers and text layers
3. **Name your layers** using the conventions above (`stat_distance`, `label_pace`, etc.)
4. For chart/map regions, use shape layers named `elevation_chart` / `route_map` with named sub-groups (see [Chart & Map Layers](#chart--map-layers-design-driven))
5. Export via Bodymovin as JSON
6. Add `templateMeta` to the exported JSON
7. Repeat for each aspect ratio (or use just one and let auto-scaling handle the rest)

> **Important:** Keep the design static (single frame). Animations in the Lottie JSON are not used — the app controls all dynamic behavior through text binding and chart rendering.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Template doesn't appear in the app | Check file name follows `{id}_{ratio}.json` pattern. At least one ratio file must exist. |
| Text layers don't show live data | Verify layer names match exactly (`stat_distance`, not `distance` or `stat_Distance`) |
| Chart colors are defaults | Add named sub-groups (`line`, `area`, `dot`, `glow`) inside the chart layer with your colors |
| Charts/map don't appear | Check layer name is `elevation_chart` or `route_map` (also accepts `placeholder_` prefix) |
| Colors look different in app | The app applies accent color to `label_*` text and to chart lines (when no stroke color is defined in the placeholder) |
| Layout is clipped at certain ratios | Auto-scaling works best when elements are positioned relative to the center. Elements at extreme edges may clip. |
