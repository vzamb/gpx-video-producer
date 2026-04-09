#!/usr/bin/env python3
"""
Figma → GPX Video Producer Template Converter

Converts a Figma LottieFiles plugin export into a working app template by:
  - Preserving all visual shape layers (cards, scrims, gradients)
  - Replacing vectorized text outlines with proper Lottie text layers (type 5)
  - Adding placeholder layers for charts and route maps
  - Restoring layer names stripped by the plugin
  - Removing entrance/exit animations (static overlay)

Usage:
  python3 tools/figma_to_template.py <figma_export.json> <config.json> [output.json]

The config.json maps Figma layer names (top-to-bottom panel order) and defines
placeholder regions. See tools/example_config.json or docs/TEMPLATE_GUIDE.md.
"""

import json
import sys
import os
import math
from pathlib import Path


# ─── Text layer defaults ───────────────────────────────────────────────────────

# Names recognized as text layers that need dynamic binding
TEXT_PREFIXES = ("stat_", "label_", "title_")

# Default text content for each recognized name
DEFAULT_TEXT = {
    "stat_distance":  "0.0 km",
    "stat_elevation": "↑ 0 m",
    "stat_pace":      "—",
    "stat_hr":        "—",
    "stat_time":      "0:00",
    "stat_grade":     "0.0%",
    "stat_speed":     "0.0 km/h",
    "label_distance":  "DISTANCE",
    "label_elevation": "ELEVATION",
    "label_pace":      "PACE",
    "label_hr":        "HEART RATE",
    "label_time":      "TIME",
    "label_grade":     "GRADE",
    "label_speed":     "SPEED",
    "title_text":      "ACTIVITY TITLE",
}

# Font size estimation: cap height ≈ 72% of font size
CAP_HEIGHT_RATIO = 0.72


# ─── Helpers ───────────────────────────────────────────────────────────────────

def compute_bbox(layer):
    """Compute absolute bounding box from path vertices in a shape layer.
    
    After the LottieFiles entrance animation settles, the group transform
    position is [0,0], so path vertices are in absolute canvas coordinates.
    """
    all_x, all_y = [], []
    for grp in layer.get("shapes", [{}]):
        for item in grp.get("it", []):
            if item.get("ty") == "sh":
                ks = item.get("ks", {}).get("k", {})
                if isinstance(ks, dict):
                    for v in ks.get("v", []):
                        all_x.append(v[0])
                        all_y.append(v[1])
    if not all_x:
        return None
    return {
        "min_x": min(all_x), "max_x": max(all_x),
        "min_y": min(all_y), "max_y": max(all_y),
        "w": max(all_x) - min(all_x),
        "h": max(all_y) - min(all_y),
        "cx": (min(all_x) + max(all_x)) / 2,
        "cy": (min(all_y) + max(all_y)) / 2,
    }


def get_fill_color(layer):
    """Extract fill color [r,g,b] (0-1 range) from a shape layer."""
    for grp in layer.get("shapes", [{}]):
        for item in grp.get("it", []):
            if item.get("ty") == "fl":
                return item.get("c", {}).get("k", [1, 1, 1])
    return [1, 1, 1]


def count_paths(layer):
    """Count the number of path shapes in a layer."""
    count = 0
    for grp in layer.get("shapes", [{}]):
        for item in grp.get("it", []):
            if item.get("ty") == "sh":
                count += 1
    return count


def count_vertices(layer):
    """Count total vertices across all paths in a layer."""
    total = 0
    for grp in layer.get("shapes", [{}]):
        for item in grp.get("it", []):
            if item.get("ty") == "sh":
                ks = item.get("ks", {}).get("k", {})
                if isinstance(ks, dict):
                    total += len(ks.get("v", []))
    return total


def classify_layer(layer):
    """Classify a layer as 'text' (vectorized text) or 'shape' (visual element).
    
    Heuristic: vectorized text characters have many vertices (even a single '0'
    has ~20+ vertices). Simple shapes like rounded rectangles have ≤12 vertices.
    """
    verts = count_vertices(layer)
    # A rounded rectangle has ~8-9 vertices; text glyphs typically 20+
    if verts > 15:
        return "text"
    return "shape"


def make_static_transform(x=0, y=0, anchor_x=0, anchor_y=0, scale=100):
    """Create a non-animated Lottie transform."""
    return {
        "p": {"a": 0, "k": [x, y, 0]},
        "a": {"a": 0, "k": [anchor_x, anchor_y, 0]},
        "s": {"a": 0, "k": [scale, scale, 100]},
        "r": {"a": 0, "k": 0},
        "o": {"a": 0, "k": 100},
    }


def make_text_layer(name, bbox, fill_color, config_overrides, ind):
    """Create a proper Lottie text layer (type 5) from a bounding box."""
    override = config_overrides.get(name, {})

    # Estimate font size from bounding box height
    font_size = override.get("size", round(bbox["h"] / CAP_HEIGHT_RATIO, 1))

    # Font selection
    is_bold = override.get("bold", name.startswith("stat_") or name == "title_text")
    font_name = "SansBold" if is_bold else "SansRegular"

    # Justification: 0=left, 1=center, 2=right
    justify = override.get("align", None)
    if justify is None:
        if name == "title_text":
            justify = 0  # left-aligned title
        elif name.startswith("label_"):
            justify = 1  # center labels
        else:
            justify = 1  # center stats

    # Map string alignment to Lottie justify int
    if isinstance(justify, str):
        justify = {"left": 0, "center": 1, "right": 2}.get(justify, 1)

    # Position: baseline anchor point
    if justify == 0:    # left
        x = bbox["min_x"]
    elif justify == 2:  # right
        x = bbox["max_x"]
    else:               # center
        x = bbox["cx"]
    y = bbox["max_y"]  # baseline ≈ bottom of cap height

    # Fill color (RGB 0-1)
    color = fill_color[:3] if len(fill_color) >= 3 else [1, 1, 1]

    # Default text
    default_text = override.get("text", DEFAULT_TEXT.get(name, name))

    return {
        "ty": 5,
        "nm": name,
        "sr": 1,
        "st": 0,
        "op": 300,
        "ip": 0,
        "hd": False,
        "ddd": 0,
        "bm": 0,
        "ao": 0,
        "ind": ind,
        "ks": make_static_transform(x, y),
        "t": {
            "d": {
                "k": [{
                    "s": {
                        "s": font_size,
                        "f": font_name,
                        "t": default_text,
                        "j": justify,
                        "tr": 0,
                        "lh": font_size * 1.2,
                        "ls": 0,
                        "fc": color,
                    },
                    "t": 0
                }]
            },
            "p": {},
            "m": {"g": 1, "a": {"a": 0, "k": [0, 0]}},
            "a": []
        }
    }


def make_placeholder_layer(name, x, y, w, h, ind):
    """Create a Lottie solid layer (type 1) for chart/map placeholders."""
    return {
        "ty": 1,
        "nm": f"placeholder_{name}",
        "sr": 1,
        "st": 0,
        "op": 300,
        "ip": 0,
        "hd": False,
        "ddd": 0,
        "bm": 0,
        "ao": 0,
        "ind": ind,
        "sw": w,
        "sh": h,
        "sc": "#000000",
        "ks": make_static_transform(x + w / 2, y + h / 2, w / 2, h / 2),
    }


def strip_animation(layer):
    """Remove entrance/exit animations from a shape layer, making it static.
    
    The LottieFiles plugin adds animated group transforms (entrance from canvas
    center). This converts them to static transforms at the resting position
    (which is [0,0] since vertices are in absolute coords).
    """
    result = json.loads(json.dumps(layer))  # deep copy
    
    for grp in result.get("shapes", []):
        for item in grp.get("it", []):
            if item.get("ty") == "tr":
                # Replace animated position with static [0, 0]
                item["p"] = {"a": 0, "k": [0, 0]}
                # Ensure other properties are static too
                if "a" not in item:
                    item["a"] = {"a": 0, "k": [0, 0]}
                elif isinstance(item["a"].get("k"), list) and len(item["a"]["k"]) > 0 and isinstance(item["a"]["k"][0], dict):
                    item["a"] = {"a": 0, "k": [0, 0]}
                if "s" not in item:
                    item["s"] = {"a": 0, "k": [100, 100]}
                elif isinstance(item["s"].get("k"), list) and len(item["s"]["k"]) > 0 and isinstance(item["s"]["k"][0], dict):
                    item["s"] = {"a": 0, "k": [100, 100]}
                if "r" not in item:
                    item["r"] = {"a": 0, "k": 0}
                elif isinstance(item["r"].get("k"), list) and len(item["r"]["k"]) > 0 and isinstance(item["r"]["k"][0], dict):
                    item["r"] = {"a": 0, "k": 0}
                if "o" not in item:
                    item["o"] = {"a": 0, "k": 100}
                elif isinstance(item["o"].get("k"), list) and len(item["o"]["k"]) > 0 and isinstance(item["o"]["k"][0], dict):
                    item["o"] = {"a": 0, "k": 100}
    
    # Also make the layer's own transform static
    result["ks"] = make_static_transform()
    
    return result


# ─── Main converter ────────────────────────────────────────────────────────────

def convert(export_path, config_path, output_path):
    with open(export_path) as f:
        export = json.load(f)
    with open(config_path) as f:
        config = json.load(f)

    canvas_w = export.get("w", 1080)
    canvas_h = export.get("h", 1920)
    export_layers = export.get("layers", [])
    
    layer_names = config.get("layers", [])
    text_overrides = config.get("text_overrides", {})
    placeholders = config.get("placeholders", {})
    template_name = config.get("name", "Custom Template")
    template_desc = config.get("description", "Custom overlay template")

    if len(layer_names) != len(export_layers):
        print(f"⚠️  Warning: config has {len(layer_names)} layer names but export has {len(export_layers)} layers")
        print(f"   Will process min({len(layer_names)}, {len(export_layers)}) layers")

    output_layers = []
    ind = 0
    stats = {"text_replaced": 0, "shapes_kept": 0, "placeholders_added": 0}

    for i in range(min(len(layer_names), len(export_layers))):
        name = layer_names[i]
        layer = export_layers[i]
        bbox = compute_bbox(layer)
        detected_type = classify_layer(layer)
        fill_color = get_fill_color(layer)

        is_text_name = any(name.startswith(p) for p in TEXT_PREFIXES)

        if is_text_name and bbox:
            # Replace vectorized text with proper text layer
            text_layer = make_text_layer(name, bbox, fill_color, text_overrides, ind)
            output_layers.append(text_layer)
            stats["text_replaced"] += 1
            print(f"  ✏️  {name}: text layer at ({bbox['cx']:.0f}, {bbox['max_y']:.0f}), "
                  f"size≈{bbox['h'] / CAP_HEIGHT_RATIO:.0f}px")
        else:
            # Keep visual shape layer, just rename and remove animation
            kept = strip_animation(layer)
            kept["nm"] = name
            kept["ind"] = ind
            output_layers.append(kept)
            stats["shapes_kept"] += 1
            if bbox:
                print(f"  🎨 {name}: shape kept, bbox ({bbox['min_x']:.0f},{bbox['min_y']:.0f})-"
                      f"({bbox['max_x']:.0f},{bbox['max_y']:.0f})")
            else:
                print(f"  🎨 {name}: shape kept (no bbox)")
        
        ind += 1

    # Add placeholder layers
    for ph_name, ph_rect in placeholders.items():
        x = ph_rect.get("x", 0)
        y = ph_rect.get("y", 0)
        w = ph_rect.get("w", 200)
        h = ph_rect.get("h", 200)
        ph_layer = make_placeholder_layer(ph_name, x, y, w, h, ind)
        output_layers.append(ph_layer)
        stats["placeholders_added"] += 1
        print(f"  📍 placeholder_{ph_name}: solid at ({x},{y}) size {w}x{h}")
        ind += 1

    # Build output template
    template = {
        "v": "5.7.1",
        "fr": 30,
        "ip": 0,
        "op": 300,
        "w": canvas_w,
        "h": canvas_h,
        "nm": template_name,
        "ddd": 0,
        "assets": [],
        "layers": output_layers,
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
                }
            ]
        },
        "meta": {"g": "figma_to_template.py v1.0"},
        "templateMeta": {
            "displayName": template_name,
            "description": template_desc
        }
    }

    with open(output_path, 'w') as f:
        json.dump(template, f, indent=2)
    
    size_kb = os.path.getsize(output_path) / 1024
    print(f"\n✅ Template written to {output_path} ({size_kb:.1f} KB)")
    print(f"   Canvas: {canvas_w}×{canvas_h}")
    print(f"   Text layers replaced: {stats['text_replaced']}")
    print(f"   Shape layers kept: {stats['shapes_kept']}")
    print(f"   Placeholders added: {stats['placeholders_added']}")
    print(f"   Total layers: {len(output_layers)}")


# ─── CLI ───────────────────────────────────────────────────────────────────────

def print_usage():
    print("Usage: python3 tools/figma_to_template.py <figma_export.json> <config.json> [output.json]")
    print()
    print("Arguments:")
    print("  figma_export.json  The Lottie JSON exported from Figma via LottieFiles plugin")
    print("  config.json        Layer mapping config (see tools/example_config.json)")
    print("  output.json        Output path (default: alongside figma_export with _template suffix)")
    print()
    print("To analyze a Figma export without converting:")
    print("  python3 tools/figma_to_template.py --analyze <figma_export.json>")


def analyze(export_path):
    """Analyze a Figma export to help the user create a config file."""
    with open(export_path) as f:
        export = json.load(f)

    canvas_w = export.get("w", 1080)
    canvas_h = export.get("h", 1920)
    layers = export.get("layers", [])

    print(f"📐 Canvas: {canvas_w}×{canvas_h}")
    print(f"📦 Layers: {len(layers)}")
    print(f"🎬 Frames: {export.get('ip', 0)}-{export.get('op', 0)} @ {export.get('fr', 0)}fps")
    print()
    print("Layer analysis (compare with your Figma layers panel, top to bottom):")
    print("─" * 80)

    config_layers = []
    for i, layer in enumerate(layers):
        bbox = compute_bbox(layer)
        detected = classify_layer(layer)
        fill = get_fill_color(layer)
        paths = count_paths(layer)
        
        if bbox:
            fill_str = f"rgb({fill[0]:.0%},{fill[1]:.0%},{fill[2]:.0%})" if fill else "none"
            if detected == "text":
                est_size = round(bbox["h"] / CAP_HEIGHT_RATIO, 1)
                print(f"  Layer {i:2d}: TEXT     {paths:2d} paths  "
                      f"bbox=({bbox['min_x']:.0f},{bbox['min_y']:.0f})-({bbox['max_x']:.0f},{bbox['max_y']:.0f})  "
                      f"~{est_size:.0f}px  fill={fill_str}")
                config_layers.append(f"    \"layer_{i}_text\"")
            else:
                print(f"  Layer {i:2d}: SHAPE    {paths:2d} path   "
                      f"bbox=({bbox['min_x']:.0f},{bbox['min_y']:.0f})-({bbox['max_x']:.0f},{bbox['max_y']:.0f})  "
                      f"size={bbox['w']:.0f}×{bbox['h']:.0f}  fill={fill_str}")
                config_layers.append(f"    \"layer_{i}_shape\"")
        else:
            print(f"  Layer {i:2d}: EMPTY")
            config_layers.append(f"    \"layer_{i}\"")

    print()
    print("Suggested config template (replace names with your Figma layer names):")
    print("─" * 80)
    print("{")
    print(f'  "name": "My Template",')
    print(f'  "description": "Custom overlay",')
    print(f'  "layers": [')
    print(",\n".join(config_layers))
    print(f'  ],')
    print(f'  "placeholders": {{')
    print(f'    "route_map": {{"x": 40, "y": 60, "w": 400, "h": 400}},')
    print(f'    "elevation_chart": {{"x": 24, "y": {canvas_h - 200}, "w": {canvas_w - 48}, "h": 120}}')
    print(f'  }}')
    print("}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print_usage()
        sys.exit(1)
    
    if sys.argv[1] == "--analyze":
        if len(sys.argv) < 3:
            print("Error: --analyze requires a Figma export path")
            sys.exit(1)
        analyze(sys.argv[2])
    elif len(sys.argv) >= 3:
        export_path = sys.argv[1]
        config_path = sys.argv[2]
        output_path = sys.argv[3] if len(sys.argv) > 3 else export_path.replace(".json", "_template.json")
        
        print(f"🔄 Converting Figma export: {export_path}")
        print(f"   Config: {config_path}")
        print(f"   Output: {output_path}")
        print()
        
        convert(export_path, config_path, output_path)
    else:
        print_usage()
        sys.exit(1)
