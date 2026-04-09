# Creating Lottie Template Overlays

This guide explains how to create new overlay templates for the GPX Video Producer app.

## Overview

Templates are [Lottie](https://airbnb.io/lottie/) JSON files placed in `app/src/main/assets/templates/`. The app **automatically discovers** all templates in this directory at runtime — no code changes needed. Each template needs **4 JSON files**, one per supported aspect ratio.

## File Naming Convention

```
templates/{template_id}_{ratio}.json
```

- `{template_id}` — lowercase, underscore-separated (e.g. `cinematic`, `pro_dashboard`, `night_run`)
- `{ratio}` — one of: `9x16`, `16x9`, `1x1`, `4x5`

**Example for a new "trail" template:**
```
templates/trail_9x16.json     (1080×1920)
templates/trail_16x9.json     (1920×1080)
templates/trail_1x1.json      (1080×1080)
templates/trail_4x5.json      (1080×1350)
```

> All 4 files are required. The app groups files by template id and reads metadata from any variant.

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
| 4 (shape)   | Cards, backgrounds | Rounded rectangles, gradients, scrims |
| 5 (text)    | Stats, labels, title | Dynamic text bound at runtime |
| 1 (solid)   | Placeholders | Invisible regions for chart/map rendering |

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

The renderer draws data-driven visualizations in special placeholder regions. These are **solid layers** (`ty: 1`) with specific names:

| Layer Name | Rendering |
|------------|-----------|
| `placeholder_elevation_chart` | Elevation profile with progress indicator |
| `placeholder_route_map` | Route map with current position dot |

### Placeholder layer structure:

```json
{
  "ddd": 0,
  "ind": 16,
  "ty": 1,
  "nm": "placeholder_elevation_chart",
  "ks": {
    "o": { "a": 0, "k": 0 },
    "p": { "a": 0, "k": [540, 1700, 0] },
    "a": { "a": 0, "k": [480, 120, 0] }
  },
  "sw": 960,
  "sh": 240,
  "sc": "#000000",
  "ip": 0,
  "op": 30,
  "st": 0
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

1. **Create 4 JSON files** in `app/src/main/assets/templates/`:
   ```
   my_template_9x16.json
   my_template_16x9.json
   my_template_1x1.json
   my_template_4x5.json
   ```

2. **Design the layout** for each ratio using the canvas dimensions above. Tip: start with 9:16 as the reference, then adapt.

3. **Add `templateMeta`** to each file with `displayName` and `description`.

4. **Use recognized layer names** for text (`stat_*`, `label_*`, `title_text`) and placeholders (`placeholder_elevation_chart`, `placeholder_route_map`).

5. **Build and run** — the app will automatically discover your new template and show it in the template pager. No code changes required.

## Existing Templates (Reference)

| Template ID | Display Name | Description |
|------------|-------------|-------------|
| `cinematic` | Cinematic | Minimalist data cards nestled in the corner |
| `hero` | Hero | Massive distance tracking, centered and bold |
| `pro_dashboard` | Pro Dashboard | Full metrics panel with route map |

You can study these existing JSON files as reference when creating new templates.

## Creating Templates with After Effects

You can design templates in After Effects and export them as Lottie JSON using the [Bodymovin plugin](https://aescripts.com/bodymovin/):

1. Create a comp at the target canvas size (e.g. 1080×1920 for 9:16)
2. Design your layout using shape layers and text layers
3. **Name your layers** using the conventions above (`stat_distance`, `label_pace`, etc.)
4. For chart/map regions, use solid layers named `placeholder_elevation_chart` / `placeholder_route_map`
5. Export via Bodymovin as JSON
6. Add `templateMeta` to the exported JSON
7. Repeat for each aspect ratio

> **Important:** Keep the design static (single frame). Animations in the Lottie JSON are not used — the app controls all dynamic behavior through text binding and chart rendering.


## Creating Templates with Figma

Figma is a free, browser-based design tool. Design your overlay visually with full creative control — custom card styles, gradients, scrims, colors — then use the converter script to produce a working template.

> **How it works:** The LottieFiles Figma plugin exports your visual design (cards, gradients, shapes) perfectly, but converts text into vector outlines and strips layer names. The converter script fixes this by keeping all your visual elements intact while replacing vectorized text with proper dynamic text layers.

### The Workflow

```
┌──────────────┐     ┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│  Design in   │     │  Export via      │     │  Write a small   │     │  Run converter   │
│  Figma       │ ──► │  LottieFiles     │ ──► │  config.json     │ ──► │  script          │
│  (full style)│     │  plugin          │     │  (layer names)   │     │  (working JSON)  │
└──────────────┘     └──────────────────┘     └──────────────────┘     └──────────────────┘
```

**What the converter preserves from your Figma design:**
- Cards with custom fills, gradients, opacity, rounded corners
- Background scrims and gradient overlays
- Decorative shape elements (lines, circles, badges)

**What the converter generates (replacing broken exports):**
- Proper text layers (type 5) for dynamic data binding
- Placeholder layers for charts and route map

### Prerequisites

- A free [Figma](https://www.figma.com/) account
- The [LottieFiles](https://www.figma.com/community/plugin/809860933081065308) Figma plugin installed
- Python 3 installed on your machine
- The converter script at `tools/figma_to_template.py`

### Step 1: Create a New Frame in Figma

Start with 9:16 (vertical/Stories format):

1. Open Figma, then create a **New Design File**
2. Press **F** (Frame tool)
3. Set the frame size to **1080 x 1920**
4. Name the frame `overlay_9x16`

> Use exact pixel dimensions from the [Canvas Dimensions](#canvas-dimensions) table. Don't use Figma's preset phone sizes.

### Step 2: Set Up the Background

The overlay renders on top of video, so the background should be transparent. During design, simulate a dark video frame:

1. Select the frame
2. Set fill to dark gray (`#1A1A1A`) at 100% opacity — for reference only
3. **Before exporting:** set the frame fill opacity to **0%** (transparent)

### Step 3: Design Your Visual Elements

This is where you have full creative freedom. Design cards, scrims, decorative elements — everything that makes your template unique.

**Stat Cards — create one for each stat you want:**
1. Press **R** to draw a rectangle
2. Style it however you like: fill, gradient, opacity, corner radius, shadows, borders
3. **Name the layer** with a `card_` prefix: `card_distance`, `card_hr`, `card_pace`, etc.

**Gradient Scrim (optional):**
1. Draw a full-width rectangle at the bottom
2. Apply a linear gradient: transparent at top, semi-transparent black at bottom
3. Name it `scrim_bottom` or similar

> Go wild with the visual design — gradients, glass-morphism, custom colors, rounded corners, multiple scrims. The converter preserves ALL shape styling from your Figma export.

### Step 4: Add Text Layers

For each stat card, add two text layers — a **label** and a **value**. The text content doesn't matter (it's replaced at runtime), but the position and approximate size do.

**Label:**
1. Press **T**, type `DISTANCE`
2. Set font to any sans-serif, ~24px, white
3. Position it inside/above the card
4. **Name exactly:** `label_distance`

**Value:**
1. Press **T**, type `0.0`
2. Set font to bold, ~72px, white
3. Position it inside the card
4. **Name exactly:** `stat_distance`

**Recognized text layer names:**

| What | Label name | Value name |
|------|-----------|-----------|
| Distance | `label_distance` | `stat_distance` |
| Elevation | `label_elevation` | `stat_elevation` |
| Pace | `label_pace` | `stat_pace` |
| Heart Rate | `label_hr` | `stat_hr` |
| Time | `label_time` | `stat_time` |
| Speed | `label_speed` | `stat_speed` |
| Grade | `label_grade` | `stat_grade` |
| Title | — | `title_text` |

### Step 5: Organize Layer Order

In the Figma layers panel, arrange from **top to bottom** (top = frontmost):

```
title_text              <-- text (frontmost)
stat_hr                 <-- text
stat_time               <-- text
stat_pace               <-- text
label_hr                <-- text
label_time              <-- text
label_pace              <-- text
card_hr                 <-- shape (visual card)
card_time               <-- shape
card_pace               <-- shape
scrim_bottom            <-- shape (bottommost)
```

> **The layer order in Figma's panel is critical** — it determines the order in the exported JSON. The converter maps names by position in this list.

### Step 6: Export with LottieFiles Plugin

1. Set the frame's background fill to **0% opacity** (transparent)
2. Select the frame
3. Run the LottieFiles plugin: **Plugins > LottieFiles > Export to Lottie**
4. Download the exported `.json` file
5. Save it in the project root (e.g., `my_overlay_9x16.json`)

### Step 7: Analyze the Export (Optional)

Run the analyzer to verify the export and get a config template:

```bash
python3 tools/figma_to_template.py --analyze my_overlay_9x16.json
```

Output:
```
Canvas: 1080x1920
Layers: 16

Layer analysis (compare with your Figma layers panel, top to bottom):
  Layer  0: TEXT      8 paths  bbox=(55,61)-(358,122)  ~84px
  Layer  1: TEXT      5 paths  bbox=(886,47)-(1020,109)  ~85px
  ...
  Layer 11: SHAPE     1 path   bbox=(853,40)-(1053,150)  size=200x110
  ...
```

The analyzer shows detected types (TEXT vs SHAPE) and bounding boxes. Compare with your Figma layers panel to verify the order matches.

### Step 8: Create the Config File

Create a JSON config file that maps layer names in Figma panel order:

```json
{
  "name": "My Template",
  "description": "My custom overlay design",
  "layers": [
    "title_text",
    "stat_hr",
    "stat_time",
    "stat_pace",
    "stat_elevation",
    "stat_distance",
    "label_hr",
    "label_time",
    "label_pace",
    "label_elevation",
    "label_distance",
    "card_hr",
    "card_time",
    "card_pace",
    "card_elevation",
    "card_distance"
  ],
  "placeholders": {
    "route_map": {"x": 40, "y": 60, "w": 800, "h": 800},
    "elevation_chart": {"x": 24, "y": 1600, "w": 1032, "h": 120}
  },
  "text_overrides": {
    "title_text": {"size": 56, "bold": true, "align": "left"}
  }
}
```

**Config fields:**

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Display name shown in the app |
| `description` | No | Short description |
| `layers` | Yes | Layer names in Figma panel order (top to bottom). Text layers (`stat_*`, `label_*`, `title_*`) are replaced; shape layers (`card_*`, `scrim*`) are kept with styling intact |
| `placeholders` | No | Chart and map regions (x, y, w, h) — these are added as new layers since they're not in the Figma design |
| `text_overrides` | No | Override auto-detected font size, boldness, or alignment for specific text layers |

**Text override options:**

| Key | Default | Description |
|-----|---------|-------------|
| `size` | Auto-detected from vectorized text height | Font size in pixels |
| `bold` | `true` for `stat_*` and `title_*`, `false` for `label_*` | Whether to use bold font |
| `align` | `"left"` for title, `"center"` for stats/labels | `"left"`, `"center"`, or `"right"` |
| `text` | Standard placeholder text | Default text shown when no data |

> See `tools/example_config.json` for a complete example.

### Step 9: Run the Converter

```bash
python3 tools/figma_to_template.py my_overlay_9x16.json my_config.json output.json
```

Output:
```
Converting Figma export: my_overlay_9x16.json
   Config: my_config.json
   Output: output.json

  title_text: text layer at (207, 122), size=84px
  stat_hr: text layer at (953, 109), size=85px
  ...
  card_hr: shape kept, bbox (853,40)-(1053,150)
  ...
  placeholder_route_map: solid at (40,60) size 800x800
  placeholder_elevation_chart: solid at (24,1600) size 1032x120

Template written to output.json (42.2 KB)
   Text layers replaced: 11
   Shape layers kept: 5
   Placeholders added: 2
```

### Step 10: Install and Test

Copy the output to the templates directory with the correct naming convention:

```bash
cp output.json app/src/main/assets/templates/my_template_9x16.json
```

Build and run — your template appears in the template selector on the Overlays screen.

### Step 11: Create All 4 Aspect Ratios

Go back to Figma and create frames for the remaining ratios:

| Ratio | Frame Size | File Name |
|-------|-----------|-----------|
| 16:9 | 1920 x 1080 | `my_template_16x9.json` |
| 1:1 | 1080 x 1080 | `my_template_1x1.json` |
| 4:5 | 1080 x 1350 | `my_template_4x5.json` |

For each: duplicate the 9:16 frame, resize, rearrange elements, export, and convert with a matching config.

> You don't need every stat in every ratio. Landscape (16:9) might use fewer cards. All 4 ratio files must exist for the template to appear in the app.

### Troubleshooting

| Problem | Solution |
|---------|----------|
| Template doesn't appear in the app | Check file names follow `{id}_{ratio}.json` pattern and all 4 ratios exist |
| Converter warns about layer count mismatch | Your Figma layer count doesn't match the config. Check for hidden layers or grouped elements |
| Text is too large/small | Add a `text_overrides` entry for the layer with an explicit `size` value |
| Cards not visible | Ensure cards are at the bottom of the Figma layer list (behind text) and have non-zero opacity fills |
| Charts/map don't appear | Check `placeholders` coordinates are within the canvas bounds |
| Colors look different in app | The app applies accent color to `title_text` and charts at runtime. Design other text in white |
