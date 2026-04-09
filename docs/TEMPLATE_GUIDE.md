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

## Creating Templates with Figma (Beginner-Friendly)

Figma is a free, browser-based design tool that makes it easy to visually design your overlay layout and export it as Lottie JSON. This section walks you through the entire process step by step.

### Prerequisites

- A free [Figma](https://www.figma.com/) account (the free plan is sufficient)
- The **LottieFiles** plugin for Figma (free)

### Step 1: Install the LottieFiles Plugin

1. Open Figma in your browser or desktop app
2. Click the **Figma logo** (top-left) → **Plugins** → **Browse plugins in Community**
3. Search for **"LottieFiles"**
4. Click **Install** on the plugin by LottieFiles
5. You'll see a confirmation — the plugin is now available in your Figma workspace

### Step 2: Create a New Frame

Each aspect ratio needs its own Figma file (or page). Start with 9:16 (vertical/Stories format).

1. Open Figma and create a **New Design File**
2. Press **F** (or click the Frame tool in the toolbar)
3. On the right panel under **Frame**, manually set the size:
   - **Width:** `1080`
   - **Height:** `1920`
4. Name this frame `overlay_9x16` (double-click the name in the left panel)

> 💡 **Tip:** Don't use Figma's preset phone sizes. The template canvas must match the exact dimensions listed in the [Canvas Dimensions](#canvas-dimensions) table above.

### Step 3: Set Up the Background

The overlay is rendered **on top of video**, so the background should be transparent. However, during design it helps to simulate a dark video frame:

1. Select the frame
2. In the right panel, under **Fill**, click the color swatch
3. Set it to a dark gray (`#1A1A1A`) with opacity at 100% — this is only for reference
4. **Before exporting**, you'll remove this fill (or set opacity to 0%)

### Step 4: Design Stat Cards

Stat cards are rounded rectangles that hold your data values. Here's how to create one:

1. Press **R** to draw a rectangle inside your frame
2. In the right panel, set:
   - **Width:** `200`, **Height:** `110`
   - **Corner radius:** `12`
   - **Fill color:** `#000000` (black) with **opacity** `50%`
3. Position it where you want (e.g., bottom-left corner: X=`48`, Y=`1650`)
4. **Name the layer** `card_distance` (double-click in the left layers panel)

Repeat for each stat you want (e.g., `card_elevation`, `card_pace`, `card_hr`, `card_time`).

> ⚠️ **Layer naming is critical!** The app matches layers by name. You can name card layers anything — they're purely visual. The **text layers** are the ones that must follow the naming convention.

### Step 5: Add Text Labels and Values

For each stat card, you need two text layers — a **label** (e.g., "DISTANCE") and a **value** (e.g., "12.50").

**Add a label:**
1. Press **T** to create a text element
2. Type `DISTANCE` as placeholder text
3. In the right panel, set:
   - **Font:** Roboto (or any sans-serif; the app uses system fonts)
   - **Weight:** Bold
   - **Size:** `24px`
   - **Color:** white (`#FFFFFF`)
4. Position it above/inside the card
5. **Name this layer exactly:** `label_distance`

**Add a value:**
1. Press **T** again
2. Type `0.0` as placeholder text
3. Set font size to `84px`, Bold, white
4. Position it below the label
5. **Name this layer exactly:** `stat_distance`

Repeat for each stat. The recognized layer names are:

| What you're adding | Label layer name | Value layer name |
|---|---|---|
| Distance | `label_distance` | `stat_distance` |
| Elevation | `label_elevation` | `stat_elevation` |
| Pace | `label_pace` | `stat_pace` |
| Heart Rate | `label_hr` | `stat_hr` |
| Time | `label_time` | `stat_time` |
| Speed | `label_speed` | `stat_speed` |
| Grade | `label_grade` | `stat_grade` |

You can also add **unit layers** (e.g., `stat_distance_unit` with text `km`).

### Step 6: Add a Title Layer

1. Press **T**, type a placeholder title like `My Run`
2. Position it at the top of the overlay
3. Set a large font size (e.g., `48px`), Bold, white
4. **Name the layer:** `title_text`

### Step 7: Add Placeholder Rectangles for Charts and Map

The app draws the elevation chart and route map into invisible rectangular regions. You need to define where those go.

**Elevation Chart:**
1. Press **R** to draw a rectangle
2. Size it to your desired chart area (e.g., **Width:** `960`, **Height:** `240`)
3. Position it (e.g., X=`60`, Y=`1580` for the bottom area)
4. Set **Fill opacity to 0%** (the rectangle must be invisible — the app draws the chart here at runtime)
5. **Name the layer exactly:** `placeholder_elevation_chart`

**Route Map:**
1. Draw another rectangle
2. Size it (e.g., **Width:** `400`, **Height:** `400`)
3. Position it where you want the map
4. Set **Fill opacity to 0%**
5. **Name the layer exactly:** `placeholder_route_map`

> 💡 During design, you can keep a faint fill (e.g., 5% opacity) so you can see the placeholder areas. Just set them to 0% before exporting.

### Step 8: Add a Gradient Scrim (Optional but Recommended)

A dark gradient at the bottom ensures text is readable over bright video:

1. Draw a rectangle the full width of the frame: **Width:** `1080`, **Height:** `600`
2. Position it at the bottom: X=`0`, Y=`1320`
3. Set the fill to a **Linear Gradient**:
   - Top color: `#000000` at **0% opacity**
   - Bottom color: `#000000` at **80% opacity**
4. Name it `scrim_bottom`

### Step 9: Organize Your Layer Order

In the left panel, arrange layers from **top to bottom** in this order (remember: in Figma, the top layer in the list renders on top visually):

```
title_text              (topmost — always visible)
stat_distance
stat_elevation
stat_pace
label_distance
label_elevation
label_pace
placeholder_route_map
placeholder_elevation_chart
card_distance
card_elevation
card_pace
scrim_bottom
```

> The exact order depends on your design, but keep text on top of cards, and cards on top of scrims.

### Step 10: Export to Lottie JSON

1. Select your frame (`overlay_9x16`)
2. Go to **Plugins** → **LottieFiles** → **Export to Lottie**
3. In the plugin window:
   - Make sure your frame is selected
   - Set **Frame rate:** `30`
   - Click **Export** (or **Save to workspace** and then download as JSON)
4. Save the downloaded file as `my_template_9x16.json`

> ⚠️ If the plugin shows errors for certain layer types, simplify those layers. The LottieFiles plugin works best with basic shapes (rectangles, ellipses), text, and groups. Avoid masks, blur effects, or complex boolean operations.

### Step 11: Add Template Metadata

Open the exported JSON in a text editor (VS Code, Notepad++, or even a basic text editor). You need to add the `templateMeta` block.

1. Open `my_template_9x16.json`
2. Find the very first `{` at the beginning of the file
3. Locate the last property before `"layers"` (usually `"ddd": 0,`)
4. Add the following **before** the `"layers"` key:

```json
  "templateMeta": {
    "displayName": "My Template",
    "description": "A custom overlay template"
  },
```

The beginning of your file should look roughly like:
```json
{
  "v": "5.7.4",
  "fr": 30,
  "ip": 0,
  "op": 30,
  "w": 1080,
  "h": 1920,
  "nm": "overlay_9x16",
  "ddd": 0,
  "assets": [],
  "fonts": { "list": [...] },
  "templateMeta": {
    "displayName": "My Template",
    "description": "A custom overlay template"
  },
  "layers": [...]
}
```

### Step 12: Verify and Fix Layer Names

The LottieFiles plugin preserves Figma layer names, but it's good to double-check. Open the JSON and search for `"nm":` to see all layer names. Make sure:

- Stat values use: `stat_distance`, `stat_pace`, `stat_hr`, etc.
- Labels use: `label_distance`, `label_pace`, `label_hr`, etc.
- Placeholders use: `placeholder_elevation_chart`, `placeholder_route_map`
- Title uses: `title_text`

If a name got mangled during export (e.g., `stat_distance Copy`), fix it by editing the JSON directly.

### Step 13: Create All 4 Aspect Ratios

Go back to Figma and create 3 more pages (or frames) for the remaining ratios:

| Ratio | Frame Size | File Name |
|-------|-----------|-----------|
| 16:9 | 1920 × 1080 | `my_template_16x9.json` |
| 1:1 | 1080 × 1080 | `my_template_1x1.json` |
| 4:5 | 1080 × 1350 | `my_template_4x5.json` |

For each ratio:
1. Create a new frame at the correct dimensions
2. Rearrange your elements to fit (re-position cards, resize chart areas, etc.)
3. Keep the **same stats and layer names** in all variants
4. Export each via the LottieFiles plugin
5. Add `templateMeta` to each JSON

> 💡 **Tip:** In Figma, duplicate your 9:16 frame and resize it as a starting point for each ratio. Then reposition elements to fit.

### Step 14: Add Files to the Project

Copy all 4 JSON files into the app's template directory:

```
app/src/main/assets/templates/my_template_9x16.json
app/src/main/assets/templates/my_template_16x9.json
app/src/main/assets/templates/my_template_1x1.json
app/src/main/assets/templates/my_template_4x5.json
```

Build and run the app — your new template will appear automatically in the template selector on the Overlays screen. No code changes needed.

### Troubleshooting

| Problem | Solution |
|---------|----------|
| Template doesn't appear in the app | Check file names follow the `{id}_{ratio}.json` pattern and all 4 ratios exist |
| Text shows placeholder values (`0.0`) instead of live data | Verify layer names match exactly (case-sensitive): `stat_distance`, not `Stat_Distance` |
| Chart or map doesn't appear | Ensure placeholder layers are type `1` (solid) with opacity `0`. Check the `nm` field is exactly `placeholder_elevation_chart` or `placeholder_route_map` |
| Export plugin fails | Flatten complex layers. Avoid masks, effects, and boolean operations. Keep it simple: rectangles, text, and groups |
| Colors look different in the app | The app applies the user's accent color to labels and charts at runtime. Design labels in white — they will be recolored |
| Text is cut off or misaligned | Adjust font size and position. Remember the app replaces text content dynamically — leave room for longer strings like `12:34:56` or `1234.5` |
