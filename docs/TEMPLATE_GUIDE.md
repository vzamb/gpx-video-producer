# SVG Template Guide

Design overlay templates in **Figma** (free), export as SVG, and bundle into the app.

## Directory Structure

```
app/src/main/assets/templates/
└── my_template/
    ├── meta.json
    ├── my_template_9x16.svg
    ├── my_template_16x9.svg      (optional — fallback auto-scales)
    ├── my_template_4x5.svg       (optional)
    ├── my_template_1x1.svg       (optional)
    └── fonts/
        └── MyFont.ttf            (optional — custom font)
```

## meta.json

```json
{
  "name": "My Template",
  "displayName": "My Template",
  "description": "Short description",
  "aspectRatios": {
    "9x16": "my_template_9x16.svg",
    "16x9": "my_template_16x9.svg",
    "4x5": "my_template_4x5.svg",
    "1x1": "my_template_1x1.svg"
  },
  "fonts": {
    "My Custom Font": "fonts/MyFont.ttf"
  }
}
```

- **`aspectRatios`** — map of ratio key → SVG filename. Only `9x16` is required; missing ratios auto-scale from the nearest available.
- **`fonts`** — map of CSS font-family name → `.ttf`/`.otf` path relative to the template directory.

## Canvas Dimensions

| Ratio | Width | Height | Use Case |
|-------|-------|--------|----------|
| 9:16  | 1080  | 1920   | Instagram/TikTok Stories |
| 16:9  | 1920  | 1080   | YouTube landscape |
| 4:5   | 1080  | 1350   | Instagram feed portrait |
| 1:1   | 1080  | 1080   | Instagram feed square |

## SVG Element Naming

The app identifies elements by their `id` attribute. Wrap everything in a root `<g id="overlay_{ratio}">`.

### Dynamic Text (stat values)

These `<text>` elements are **hidden** in the SVG render and **redrawn natively** with real GPS data using Canvas (fill + stroke outlined text).

| Element ID | Data |
|---|---|
| `stat_distance` | Distance in km (e.g. `10.5`) |
| `stat_distance_unit` | Unit label (`km`) |
| `stat_elevation` | Elevation in meters |
| `stat_pace` | Pace in min/km (e.g. `5:30`) |
| `stat_hr` | Heart rate in bpm |
| `stat_time` | Elapsed time (e.g. `1:23:45`) |
| `stat_speed` | Speed in km/h |
| `stat_grade` | Grade percentage |
| `title_text` | Activity title |

**Supported attributes on `<text>` elements:**

| Attribute | Effect |
|---|---|
| `font-family` | Resolved from meta.json fonts or system |
| `font-size` | Text size in SVG units |
| `fill` | Text fill color |
| `stroke` | Outline color (drawn behind fill) |
| `stroke-width` | Outline thickness |
| `text-anchor` | `start` (left), `middle` (center), `end` (right) |

> **Figma tip:** Figma doesn't export `text-anchor="end"` for right-aligned text. The app detects right-alignment heuristically from placeholder position, but adding `text-anchor="end"` in the SVG source is more reliable.

### Static Labels

`<text id="label_*">` elements (e.g. `label_hr`, `label_distance`) are rendered directly by the SVG engine using the template's custom font.

### Elevation Chart

```xml
<g id="elevation_chart">
  <g id="area"><rect ... fill="#FFBE00" fill-opacity="0.3"/></g>
  <g id="line"><rect ... stroke="#FFBE00" stroke-width="5"/></g>
  <g id="full_path"><rect ... stroke="#FFEFBF" stroke-width="5"/></g>
  <g id="dot"><circle ... fill="#FFBE00"/></g>
  <g id="glow"><circle ... fill="#FFBE00" fill-opacity="0.3"/></g>
</g>
```

The `<rect>` and `<circle>` shapes define **bounds and style** — the app replaces them with real elevation data.

| Sub-element | Purpose | Key attributes |
|---|---|---|
| `area` | Gradient fill under visited elevation | `fill`, `fill-opacity` |
| `line` | Visited elevation path | `stroke`, `stroke-width` |
| `full_path` | Full (unvisited) elevation path | `stroke`, `stroke-width` |
| `dot` | Current position dot | `fill`, `r` (radius) |
| `glow` | Glow around dot | `fill`, `fill-opacity`, `r` |

### Route Map

```xml
<g id="route_map">
  <g id="route"><rect ... stroke="#FFBE00" stroke-width="5"/></g>
  <g id="full_route"><rect ... stroke="#FFEFBF" stroke-width="5"/></g>
  <g id="dot_2"><circle ... fill="#FFBE00"/></g>
  <g id="glow_2"><circle ... fill="#FFBE00" fill-opacity="0.3"/></g>
</g>
```

Same structure as elevation chart. The rect defines the map rendering area.

## Figma Export Tips

1. **Outline Text: OFF** — text must stay as `<text>` elements, not outlined paths
2. **Use semantic IDs** — name layers exactly as documented above
3. **Figma deduplicates IDs** — if two groups have a `dot` child, Figma renames one to `dot_2`. The app strips `_N` suffixes automatically.
4. **Invisible elements are dropped** — Figma removes elements with no visible fill/stroke. If `background` rects disappear, the app infers bounds from sibling elements.
5. **tspan positioning** — Figma puts `x`/`y` on `<tspan>` children instead of `<text>`. The app handles this.

## Custom Fonts

1. Place `.ttf`/`.otf` in the template's `fonts/` subdirectory
2. Reference in `meta.json` `fonts` map: key = CSS `font-family` name, value = relative path
3. Use the same `font-family` name in your SVG `<text>` elements
4. The font is used for both native Canvas text rendering (stat values) and SVG label rendering

## How Rendering Works

1. SVG static visuals (cards, scrims, labels) → rendered by AndroidSVG to Canvas
2. Dynamic text (`stat_*`, `title_text`) → hidden in SVG, drawn natively with fill + stroke
3. Charts/maps → bounds and colors read from SVG groups, real GPS data rendered by ChartRenderer/RouteMapRenderer
4. All three layers composite onto a single Bitmap
5. The **same Bitmap render** is used for both preview and export → pixel-perfect parity
