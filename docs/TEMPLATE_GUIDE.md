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
| 4 (shape)   | Cards, backgrounds, text, placeholders | All visual elements — rectangles, text (vectorized as paths), chart/map regions |
| 5 (text)    | Stats, labels, title | Dynamic text bound at runtime (hand-written JSON or After Effects export) |
| 1 (solid)   | Placeholders (legacy) | Invisible regions for chart/map rendering |

> **Lottie Studio note:** When exporting from Lottie Studio, all layers are type 4 (shape) — including text and placeholders. The app handles this correctly: text layers named `stat_*`, `label_*`, or `title_*` are hidden from Lottie rendering and replaced with native text; placeholder layers are hidden and replaced with chart/map renderers.

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

## Placeholder Layers (Charts & Maps)

The renderer draws data-driven visualizations in special placeholder regions. These can be **solid layers** (`ty: 1`) or **shape layers** (`ty: 4`) with specific names:

| Layer Name | Rendering |
|------------|-----------|
| `placeholder_elevation_chart` | Elevation profile with progress indicator |
| `placeholder_route_map` | Route map with current position dot |

### Shape-layer placeholders (recommended — Lottie Studio)

When using Lottie Studio, placeholders are shape layers containing a rectangle that defines the rendering region. You can add **fill** and **stroke** shapes to control the visual style of the chart/map:

```json
{
  "ty": 4,
  "nm": "placeholder_elevation_chart",
  "ind": 16,
  "ks": {
    "o": { "a": 0, "k": 100 },
    "p": { "a": 0, "k": [540, 1820, 0] }
  },
  "shapes": [{
    "ty": "gr",
    "it": [
      {
        "ty": "rc",
        "p": { "a": 0, "k": [0, 0] },
        "s": { "a": 0, "k": [1000, 200] },
        "r": { "a": 0, "k": 8 }
      },
      {
        "ty": "fl",
        "c": { "a": 0, "k": [0.267, 0.867, 0.467, 1] },
        "o": { "a": 0, "k": 30 }
      },
      {
        "ty": "st",
        "c": { "a": 0, "k": [0.267, 0.867, 0.467, 1] },
        "o": { "a": 0, "k": 100 },
        "w": { "a": 0, "k": 3 }
      },
      { "ty": "tr", "p": { "a": 0, "k": [0, 0] } }
    ]
  }]
}
```

**Chart/map style properties extracted from the shape:**

| Shape | Property | Effect |
|-------|----------|--------|
| `fl` (fill) | `c.k` | Area fill color (elevation chart gradient, map background) |
| `fl` (fill) | `o.k` | Fill opacity (0–100) |
| `st` (stroke) | `c.k` | Line color for the visited path / elevation trace |
| `st` (stroke) | `w.k` | Line width in dp |
| `rc` (rect) | `r.k` | Corner radius for the background |
| `rc` (rect) | `s.k` | Size `[width, height]` — the rendering region |

> **Design tip:** Set the stroke color to your desired accent for the chart line and route path. Set the fill to a complementary color at low opacity for the background/area gradient. The app uses these as the primary visual style rather than hardcoded defaults.

### Solid-layer placeholders (legacy — After Effects / hand-written)

```json
{
  "ty": 1,
  "nm": "placeholder_elevation_chart",
  "ind": 16,
  "ks": {
    "o": { "a": 0, "k": 0 },
    "p": { "a": 0, "k": [540, 1700, 0] },
    "a": { "a": 0, "k": [480, 120, 0] }
  },
  "sw": 960,
  "sh": 240,
  "sc": "#000000"
}
```

Key fields:
- `p` — center position `[x, y, 0]`
- `a` — anchor point `[width/2, height/2, 0]`
- `sw`, `sh` — solid width and height (the rendering region size)
- `o.k: 0` — opacity 0 (the solid itself is invisible; the renderer draws on top)

**Bounds calculation:**
```
left  = p.x - a.x
top   = p.y - a.y
right = left + sw
bottom = top + sh
```

> Legacy solid-layer placeholders use default chart colors (white lines, derived gradients). To control chart colors, use shape-layer placeholders with fill/stroke definitions.

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
5. Placeholder layers (chart, map)
6. Title text (topmost)

## Design Rules

1. **Unique `ind` values** — every layer must have a unique integer `ind` (index). Start from 0 and increment.

2. **All elements within canvas bounds** — ensure all layers fit within the canvas (w × h). For text, account for font size; for shapes, account for size + position.

3. **Consistent stats across ratios** — all 4 ratio files for a template should display the same stats, just laid out differently.

4. **Use opacity 0 for placeholders** — the chart/map renderer draws into the placeholder bounds. The solid itself should be invisible.

5. **Accent color** — the user-selected accent color is applied to label text. Design with this in mind (white stat values + colored labels is the standard pattern).

## Step-by-Step: Adding a New Template

1. **Create 1 JSON file** in `app/src/main/assets/templates/`:
   ```
   my_template_9x16.json
   ```

2. **Design the layout** for 9:16 (1080×1920) — the app auto-scales to other ratios. Optionally create additional ratio files for pixel-perfect control.

3. **Add `templateMeta`** to the file with `displayName` and `description`.

4. **Use recognized layer names** for text (`stat_*`, `label_*`, `title_text`) and placeholders (`placeholder_elevation_chart`, `placeholder_route_map`).

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
- **Chart styling** — define chart/map colors through fill and stroke on placeholder shapes
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

> **Important:** All non-text, non-placeholder layers render as pure Lottie visuals. The app preserves every fill, gradient, stroke, and opacity exactly as designed.

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

### Step 4: Add Chart and Map Placeholders

Draw rectangles where you want the elevation chart and route map to appear:

1. **Elevation chart:**
   - Draw a wide rectangle (e.g., 1000×200) at the desired position
   - Name exactly: `placeholder_elevation_chart`
   - **Style the chart colors:**
     - Set the **stroke** color to your desired chart line color (e.g., green `#44DD77`)
     - Set the **stroke width** to control line thickness (e.g., 3px)
     - Set the **fill** color to the area gradient color (same as stroke, or complementary)
     - Set the **fill opacity** low (e.g., 30%) for the translucent gradient below the elevation line

2. **Route map:**
   - Draw a rectangle (e.g., 400×300) where you want the map
   - Name exactly: `placeholder_route_map`
   - **Style the map colors:**
     - Set the **stroke** color to the route line color
     - Set the **stroke width** for the route line thickness
     - Set the **fill** color to the map background (e.g., dark `#0D0D14`)
     - Set the **fill opacity** for the background (e.g., 80%)

> The app hides these placeholder shapes from Lottie rendering and draws native charts/maps in their exact bounds, using the colors and line widths you defined.

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
placeholder_route_map            <-- placeholder (rendered natively)
placeholder_elevation_chart      <-- placeholder (rendered natively)
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
- **Test chart styling** — the stroke and fill on placeholder shapes directly control chart appearance
- **Keep it single-frame** — the app doesn't use Lottie animations; all motion comes from live data updates
- **Use system fonts** — the app replaces text with native rendering using Android system fonts (`sans-serif`, `sans-serif-condensed`, `monospace`, `serif`)
- **Transparent background** — don't add a solid background fill to the root canvas; the overlay renders on top of video

## Creating Templates with After Effects

You can design templates in After Effects and export them as Lottie JSON using the [Bodymovin plugin](https://aescripts.com/bodymovin/):

1. Create a comp at the target canvas size (e.g. 1080×1920 for 9:16)
2. Design your layout using shape layers and text layers
3. **Name your layers** using the conventions above (`stat_distance`, `label_pace`, etc.)
4. For chart/map regions, use solid layers named `placeholder_elevation_chart` / `placeholder_route_map`
5. Export via Bodymovin as JSON
6. Add `templateMeta` to the exported JSON
7. Repeat for each aspect ratio (or use just one and let auto-scaling handle the rest)

> **Important:** Keep the design static (single frame). Animations in the Lottie JSON are not used — the app controls all dynamic behavior through text binding and chart rendering.

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Template doesn't appear in the app | Check file name follows `{id}_{ratio}.json` pattern. At least one ratio file must exist. |
| Text layers don't show live data | Verify layer names match exactly (`stat_distance`, not `distance` or `stat_Distance`) |
| Chart colors are default white | Add `fl` (fill) and `st` (stroke) shapes inside the placeholder layer's shape group |
| Charts/map don't appear | Check placeholder layer name is exactly `placeholder_elevation_chart` or `placeholder_route_map` |
| Colors look different in app | The app applies accent color to `label_*` text and to chart lines (when no stroke color is defined in the placeholder) |
| Layout is clipped at certain ratios | Auto-scaling works best when elements are positioned relative to the center. Elements at extreme edges may clip. |
