#!/usr/bin/env python3
"""
Template Generator for GPX Video Producer

Design your overlay layout visually in Figma, then describe the positions
and sizes here. This script generates a valid Lottie JSON with proper
text layers (type 5) that the app can bind dynamic data to.

Usage:
  1. Design your layout in Figma at the correct canvas size
  2. Note down the X, Y, width, height of each element
  3. Edit the TEMPLATE_CONFIG below with your values
  4. Run: python3 tools/generate_template.py
  5. The files are generated directly into app/src/main/assets/templates/

How to read positions from Figma:
  - Select an element in Figma
  - Look at the right panel → "Design" tab
  - X and Y are the top-left corner position
  - W and H are width and height
"""

import json
import os
import copy

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# TEMPLATE CONFIGURATION — Edit this section with your design values
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

TEMPLATE_CONFIG = {
    "id": "my_template",           # Template ID (used in file names)
    "displayName": "My Template",  # Shown in the app UI
    "description": "Custom overlay template designed in Figma",

    # Define your layout for each aspect ratio.
    # Positions (x, y) are the TOP-LEFT corner of each element.
    # All coordinates are in pixels at the canvas resolution.
    #
    # Available element types:
    #   "stat"         → A stat card with value + label (e.g., distance, pace)
    #   "title"        → The activity title text
    #   "chart"        → Elevation chart placeholder
    #   "map"          → Route map placeholder
    #   "scrim"        → Dark gradient for text readability
    #
    # Available stat names:
    #   distance, elevation, pace, hr, time, speed, grade
    #
    # For stats, you specify the position/size of the CARD, and the
    # value + label text are automatically positioned inside it.

    "ratios": {
        "9x16": {
            "width": 1080,
            "height": 1920,
            "elements": [
                # Bottom scrim for readability
                {
                    "type": "scrim",
                    "x": 0, "y": 1320,
                    "w": 1080, "h": 600,
                    "opacity": 80,           # 0-100
                    "direction": "up",        # gradient fades upward
                },

                # Stat cards — position the card, text is auto-placed inside
                {
                    "type": "stat",
                    "name": "distance",
                    "x": 48, "y": 1700,       # Card top-left corner
                    "w": 220, "h": 140,       # Card size
                    "value_size": 72,          # Font size for the value (e.g. "12.5")
                    "label_size": 22,          # Font size for the label (e.g. "DISTANCE")
                    "card_opacity": 50,        # Card background opacity (0-100)
                    "card_radius": 12,         # Corner radius
                },
                {
                    "type": "stat",
                    "name": "elevation",
                    "x": 290, "y": 1700,
                    "w": 220, "h": 140,
                    "value_size": 72,
                    "label_size": 22,
                    "card_opacity": 50,
                    "card_radius": 12,
                },
                {
                    "type": "stat",
                    "name": "pace",
                    "x": 532, "y": 1700,
                    "w": 220, "h": 140,
                    "value_size": 72,
                    "label_size": 22,
                    "card_opacity": 50,
                    "card_radius": 12,
                },
                {
                    "type": "stat",
                    "name": "hr",
                    "x": 810, "y": 1700,
                    "w": 220, "h": 140,
                    "value_size": 72,
                    "label_size": 22,
                    "card_opacity": 50,
                    "card_radius": 12,
                },
                {
                    "type": "stat",
                    "name": "time",
                    "x": 48, "y": 1540,
                    "w": 220, "h": 140,
                    "value_size": 72,
                    "label_size": 22,
                    "card_opacity": 50,
                    "card_radius": 12,
                },

                # Elevation chart
                {
                    "type": "chart",
                    "x": 48, "y": 1380,
                    "w": 984, "h": 140,
                },

                # Route map
                {
                    "type": "map",
                    "x": 640, "y": 80,
                    "w": 400, "h": 400,
                },

                # Activity title
                {
                    "type": "title",
                    "x": 48, "y": 80,
                    "size": 56,               # Font size
                    "align": "left",           # left, center, right
                },
            ],
        },

        # ── Add other ratios below ──
        # Copy the 9x16 block and adjust positions for each ratio.
        # You can remove elements or reposition them to fit.

        # "16x9": { "width": 1920, "height": 1080, "elements": [...] },
        # "1x1":  { "width": 1080, "height": 1080, "elements": [...] },
        # "4x5":  { "width": 1080, "height": 1350, "elements": [...] },
    },
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# GENERATOR — No need to edit below this line
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

STAT_DEFAULTS = {
    "distance":  {"label": "DISTANCE",  "placeholder": "0.0"},
    "elevation": {"label": "ELEVATION", "placeholder": "0"},
    "pace":      {"label": "PACE",      "placeholder": "0:00"},
    "hr":        {"label": "HR",        "placeholder": "—"},
    "time":      {"label": "TIME",      "placeholder": "0:00"},
    "speed":     {"label": "SPEED",     "placeholder": "0.0"},
    "grade":     {"label": "GRADE",     "placeholder": "0.0%"},
}

FONTS = {
    "list": [
        {"fName": "SansBold", "fFamily": "sans-serif", "fStyle": "Bold", "ascent": 75},
        {"fName": "SansRegular", "fFamily": "sans-serif", "fStyle": "Regular", "ascent": 75},
        {"fName": "CondensedBold", "fFamily": "sans-serif-condensed", "fStyle": "Bold", "ascent": 75},
    ]
}


def make_text_layer(ind, name, text, x, y, font_size, font="SansBold",
                    color=(1.0, 1.0, 1.0), justify=0, op=30):
    """Create a Lottie text layer (type 5)."""
    return {
        "ddd": 0,
        "ind": ind,
        "ty": 5,
        "nm": name,
        "sr": 1,
        "ks": {
            "o": {"a": 0, "k": 100},
            "p": {"a": 0, "k": [x, y, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "ip": 0,
        "op": op,
        "st": 0,
        "t": {
            "d": {
                "k": [{
                    "s": {
                        "s": font_size,
                        "f": font,
                        "t": text,
                        "j": justify,
                        "tr": 0,
                        "lh": int(font_size * 1.2),
                        "ls": 0,
                        "fc": list(color),
                    },
                    "t": 0,
                }]
            },
            "a": [],
            "m": {"g": 1, "a": {"a": 0, "k": [0, 0]}},
        },
    }


def make_shape_rect(ind, name, cx, cy, w, h, radius, fill_color, fill_opacity, op=30):
    """Create a Lottie shape layer with a rounded rectangle."""
    return {
        "ddd": 0,
        "ind": ind,
        "ty": 4,
        "nm": name,
        "sr": 1,
        "ks": {
            "o": {"a": 0, "k": 100},
            "p": {"a": 0, "k": [cx, cy, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "ip": 0,
        "op": op,
        "st": 0,
        "shapes": [{
            "ty": "gr",
            "it": [
                {
                    "ty": "rc",
                    "nm": "Rect",
                    "d": 1,
                    "p": {"a": 0, "k": [0, 0]},
                    "s": {"a": 0, "k": [w, h]},
                    "r": {"a": 0, "k": radius},
                },
                {
                    "ty": "fl",
                    "nm": "Fill",
                    "c": {"a": 0, "k": fill_color},
                    "o": {"a": 0, "k": fill_opacity},
                },
                {
                    "ty": "tr",
                    "p": {"a": 0, "k": [0, 0]},
                    "a": {"a": 0, "k": [0, 0]},
                    "s": {"a": 0, "k": [100, 100]},
                    "r": {"a": 0, "k": 0},
                    "o": {"a": 0, "k": 100},
                },
            ],
        }],
    }


def make_gradient_scrim(ind, name, cx, cy, w, h, opacity, direction, op=30):
    """Create a gradient scrim shape layer."""
    if direction == "up":
        start = [0, h / 2]
        end = [0, -h / 2]
    else:
        start = [0, -h / 2]
        end = [0, h / 2]

    return {
        "ddd": 0,
        "ind": ind,
        "ty": 4,
        "nm": name,
        "sr": 1,
        "ks": {
            "o": {"a": 0, "k": 100},
            "p": {"a": 0, "k": [cx, cy, 0]},
            "a": {"a": 0, "k": [0, 0, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "ip": 0,
        "op": op,
        "st": 0,
        "shapes": [{
            "ty": "gr",
            "it": [
                {
                    "ty": "rc",
                    "nm": "Rect",
                    "d": 1,
                    "p": {"a": 0, "k": [0, 0]},
                    "s": {"a": 0, "k": [w, h]},
                    "r": {"a": 0, "k": 0},
                },
                {
                    "ty": "gf",
                    "s": {"a": 0, "k": start},
                    "e": {"a": 0, "k": end},
                    "g": {
                        "p": 2,
                        "k": {"a": 0, "k": [0, 0, 0, 0, 1, 0, 0, 0]},
                    },
                    "o": {"a": 0, "k": opacity},
                    "r": 1,
                },
                {
                    "ty": "tr",
                    "p": {"a": 0, "k": [0, 0]},
                    "a": {"a": 0, "k": [0, 0]},
                    "s": {"a": 0, "k": [100, 100]},
                    "r": {"a": 0, "k": 0},
                    "o": {"a": 0, "k": 100},
                },
            ],
        }],
    }


def make_placeholder(ind, name, cx, cy, w, h, op=30):
    """Create an invisible solid layer for chart/map rendering."""
    return {
        "ddd": 0,
        "ind": ind,
        "ty": 1,
        "nm": name,
        "sr": 1,
        "ks": {
            "o": {"a": 0, "k": 0},
            "p": {"a": 0, "k": [cx, cy, 0]},
            "a": {"a": 0, "k": [w / 2, h / 2, 0]},
            "s": {"a": 0, "k": [100, 100, 100]},
        },
        "sw": w,
        "sh": h,
        "sc": "#000000",
        "ip": 0,
        "op": op,
        "st": 0,
    }


def generate_ratio(config, ratio_config):
    """Generate a complete Lottie JSON for one aspect ratio."""
    w = ratio_config["width"]
    h = ratio_config["height"]
    op = 30
    layers = []
    ind = 0

    # Process elements in reverse order (first in config = rendered on top)
    elements = list(reversed(ratio_config["elements"]))

    for elem in elements:
        etype = elem["type"]

        if etype == "scrim":
            cx = elem["x"] + elem["w"] / 2
            cy = elem["y"] + elem["h"] / 2
            layers.append(make_gradient_scrim(
                ind, "scrim_bottom", cx, cy,
                elem["w"], elem["h"],
                elem.get("opacity", 80),
                elem.get("direction", "up"), op
            ))
            ind += 1

        elif etype == "stat":
            name = elem["name"]
            defaults = STAT_DEFAULTS.get(name, {"label": name.upper(), "placeholder": "0"})
            card_cx = elem["x"] + elem["w"] / 2
            card_cy = elem["y"] + elem["h"] / 2
            card_opacity = elem.get("card_opacity", 50)
            card_radius = elem.get("card_radius", 12)
            value_size = elem.get("value_size", 72)
            label_size = elem.get("label_size", 22)

            # Card background
            layers.append(make_shape_rect(
                ind, f"card_{name}", card_cx, card_cy,
                elem["w"], elem["h"], card_radius,
                [0, 0, 0, 1], card_opacity, op
            ))
            ind += 1

            # Label text — positioned inside the card, near the bottom
            label_x = card_cx
            label_y = elem["y"] + elem["h"] - label_size * 0.4
            layers.append(make_text_layer(
                ind, f"label_{name}", defaults["label"],
                label_x, label_y, label_size,
                font="SansBold", color=[1.0, 1.0, 1.0], justify=2, op=op
            ))
            ind += 1

            # Unit text (small, next to value)
            unit_map = {
                "distance": ("stat_distance_unit", "km"),
                "elevation": ("stat_elevation_unit", "m"),
                "pace": ("stat_pace_unit", "/km"),
                "hr": ("stat_hr_unit", "bpm"),
                "speed": ("stat_speed_unit", "km/h"),
            }
            if name in unit_map:
                unit_name, unit_text = unit_map[name]
                layers.append(make_text_layer(
                    ind, unit_name, unit_text,
                    card_cx + elem["w"] / 2 - 8, label_y,
                    label_size - 4, font="SansRegular",
                    color=[0.7, 0.7, 0.7], justify=1, op=op
                ))
                ind += 1

            # Value text — positioned inside the card, vertically centered
            value_y = elem["y"] + value_size + (elem["h"] - value_size - label_size) * 0.35
            layers.append(make_text_layer(
                ind, f"stat_{name}", defaults["placeholder"],
                card_cx, value_y, value_size,
                font="SansBold", color=[1.0, 1.0, 1.0], justify=2, op=op
            ))
            ind += 1

        elif etype == "chart":
            cx = elem["x"] + elem["w"] / 2
            cy = elem["y"] + elem["h"] / 2
            layers.append(make_placeholder(
                ind, "placeholder_elevation_chart",
                cx, cy, elem["w"], elem["h"], op
            ))
            ind += 1

        elif etype == "map":
            cx = elem["x"] + elem["w"] / 2
            cy = elem["y"] + elem["h"] / 2
            layers.append(make_placeholder(
                ind, "placeholder_route_map",
                cx, cy, elem["w"], elem["h"], op
            ))
            ind += 1

        elif etype == "title":
            justify_map = {"left": 0, "center": 2, "right": 1}
            justify = justify_map.get(elem.get("align", "left"), 0)
            title_y = elem["y"] + elem.get("size", 48)
            title_x = elem["x"]
            if justify == 2:
                title_x = w / 2
            elif justify == 1:
                title_x = w - elem["x"]
            layers.append(make_text_layer(
                ind, "title_text", "Activity Title",
                title_x, title_y, elem.get("size", 48),
                font="SansBold", color=[1.0, 1.0, 1.0], justify=justify, op=op
            ))
            ind += 1

    # Reverse so top-of-list renders on top (Lottie renders back-to-front)
    layers.reverse()

    return {
        "v": "5.7.4",
        "fr": 30,
        "ip": 0,
        "op": op,
        "w": w,
        "h": h,
        "nm": f"{config['id']}_{ratio_config['width']}x{ratio_config['height']}",
        "ddd": 0,
        "assets": [],
        "fonts": FONTS,
        "templateMeta": {
            "displayName": config["displayName"],
            "description": config["description"],
        },
        "layers": layers,
    }


def main():
    config = TEMPLATE_CONFIG
    template_id = config["id"]
    output_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "templates")
    os.makedirs(output_dir, exist_ok=True)

    generated = []
    for ratio_name, ratio_config in config["ratios"].items():
        lottie = generate_ratio(config, ratio_config)
        filename = f"{template_id}_{ratio_name}.json"
        filepath = os.path.join(output_dir, filename)
        with open(filepath, "w") as f:
            json.dump(lottie, f, indent=2)
        generated.append(filename)
        print(f"  ✓ {filename}")

    # Check which ratios are missing
    all_ratios = {"9x16", "16x9", "1x1", "4x5"}
    defined_ratios = set(config["ratios"].keys())
    missing = all_ratios - defined_ratios
    if missing:
        print(f"\n  ⚠ Missing ratios: {', '.join(sorted(missing))}")
        print("    The app requires all 4 ratios. Add them to TEMPLATE_CONFIG['ratios'].")
    else:
        print(f"\n  ✓ All 4 ratios generated for template '{template_id}'")

    print(f"\n  Output: {os.path.abspath(output_dir)}/")


if __name__ == "__main__":
    main()
