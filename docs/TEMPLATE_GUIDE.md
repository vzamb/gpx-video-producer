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
