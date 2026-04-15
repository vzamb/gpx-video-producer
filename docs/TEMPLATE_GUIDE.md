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

The app identifies elements by their `id` attribute.

### Metric Slots (generic positional)

Templates use **generic positional slots** instead of specific metric names. The renderer dynamically maps each slot to a metric based on the sport type and user preferences.

| Element ID | Purpose |
|---|---|
| `metric_1_value` | First metric value (e.g. `10.5`) |
| `metric_1_label` | First metric label (e.g. `DIST`) |
| `metric_1_unit`  | First metric unit (e.g. `km`) |
| `metric_2_value` | Second metric value |
| `metric_2_label` | Second metric label |
| `metric_2_unit`  | Second metric unit |
| `metric_3_value` | Third metric value |
| `metric_3_label` | Third metric label |
| `metric_3_unit`  | Third metric unit |
| `metric_4_value` | Fourth metric value |
| `metric_4_label` | Fourth metric label |
| `metric_4_unit`  | Fourth metric unit |
| `title_text` | Activity title (e.g. `Morning Run`) |

Add more slots (`metric_5_*`, `metric_6_*`, etc.) as needed. The app auto-detects the slot count from the SVG.

#### Available Metrics

| Metric | Label | Unit | Key |
|---|---|---|---|
| Distance | DIST | km | `distance` |
| Elevation | ELEV | m | `elevation` |
| Pace | PACE | min/km | `pace` |
| Heart Rate | HR | bpm | `hr` |
| Time | TIME | — | `time` |
| Speed | SPEED | km/h | `speed` |
| Grade | GRADE | % | `grade` |

#### Sport Defaults

| Sport | Slot 1 | Slot 2 | Slot 3 | Slot 4 |
|---|---|---|---|---|
| Running | Distance | Pace | HR | Time |
| Cycling | Distance | Speed | Elevation | HR |
| Walking | Distance | Time | Elevation | HR |

Users can customize which metrics appear in the Overlay Settings menu.

### Card Backgrounds

Each metric slot can have an associated card (background rectangle). Name them `card_N` where N matches the slot number:

```xml
<rect id="card_1" x="..." y="..." width="..." height="..." rx="12" fill="#000000" fill-opacity="0.4"/>
<rect id="card_2" ... />
```

When a metric slot is empty (user disabled that slot), the corresponding `card_N` rect is automatically hidden along with the metric text.

Not all templates need cards — templates like `pulp` and `pulp2` render text directly without background cards.

### Dynamic Text Rendering

All `metric_N_*` and `title_text` elements are `<text>` elements that get **hidden** in the SVG render and **redrawn natively** with real data using Android Canvas (fill + stroke outlined text).

**Supported attributes on `<text>` elements:**

| Attribute | Effect |
|---|---|
| `font-family` | Resolved from meta.json fonts or system |
| `font-size` | Text size in SVG units |
| `font-weight` | `bold`, `normal`, or numeric (e.g. `700`) |
| `fill` | Text fill color |
| `stroke` | Outline color (drawn behind fill) |
| `stroke-width` | Outline thickness |
| `text-anchor` | `start` (left), `middle` (center), `end` (right) |

> **Figma tip:** Figma doesn't export `text-anchor="end"` for right-aligned text. The app detects right-alignment heuristically from placeholder position, but adding `text-anchor="end"` in the SVG source is more reliable.

### Static Labels

`<text>` elements without a `metric_*` or `title_text` ID are rendered directly by the SVG engine using the template's custom font.

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
2. **Use semantic IDs** — name layers exactly as documented above (`metric_1_value`, `metric_2_label`, etc.)
3. **Figma deduplicates IDs** — if two groups have a `dot` child, Figma renames one to `dot_2`. The app strips `_N` suffixes automatically.
4. **Invisible elements are dropped** — Figma removes elements with no visible fill/stroke. If `background` rects disappear, the app infers bounds from sibling elements.
5. **tspan positioning** — Figma puts `x`/`y` on `<tspan>` children instead of `<text>`. The app handles this.

## Custom Fonts

1. Place `.ttf`/`.otf` in the template's `fonts/` subdirectory
2. Reference in `meta.json` `fonts` map: key = CSS `font-family` name, value = relative path
3. Use the same `font-family` name in your SVG `<text>` elements
4. The font is used for both native Canvas text rendering (metric values) and SVG label rendering

## How Rendering Works

1. SVG static visuals (cards, scrims, labels) → rendered by AndroidSVG to Canvas
2. Dynamic text (`metric_N_*`, `title_text`) → hidden in SVG, drawn natively with fill + stroke
3. Charts/maps → bounds and colors read from SVG groups, real GPS data rendered by ChartRenderer/RouteMapRenderer
4. All three layers composite onto a single Bitmap
5. The **same Bitmap render** is used for both preview and export → pixel-perfect parity

## User Controls

These settings are configured per-project in the app — no template changes needed:

| Setting | Effect |
|---|---|
| **Elevation chart toggle** | Show/hide the elevation chart and its background card |
| **Route map toggle** | Show/hide the route map and its background card |
| **Metric selection** | Choose which metrics to display (limited by template slot count) |
| **Metric reordering** | Drag to reorder active metrics — changes which slot each metric fills |
| **Activity title** | Text shown in the `title_text` element |
| **Sync mode** | Static (final totals), Fast Forward (animated), Live Sync (real-time) |

## Tools

### Convert Template (`tools/convert_template.py`)

Generate all aspect-ratio variants from a single SVG:

```bash
python3 tools/convert_template.py my_template_9x16.svg --name my_template --out app/src/main/assets/templates/
```

### Migrate Metric IDs (`tools/migrate_metric_ids.py`)

One-time migration from old `stat_*/label_*` naming to `metric_N_*` slots (already applied to all built-in templates).
