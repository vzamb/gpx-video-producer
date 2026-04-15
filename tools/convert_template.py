#!/usr/bin/env python3
"""
SVG template converter for GPX Video Producer.

Takes a single SVG template (designed for one aspect ratio) and generates
all four aspect-ratio variants plus the directory structure needed by the app:

    assets/templates/{name}/
        meta.json
        fonts/              (copies font files referenced in source)
        {name}_9x16.svg
        {name}_16x9.svg
        {name}_4x5.svg
        {name}_1x1.svg

Usage:
    python3 tools/convert_template.py <source.svg> [--name NAME] [--out DIR]

The script detects element roles from their SVG ids (metric_N_value, metric_N_label,
metric_N_unit, title_text, elevation_chart, route_map) and repositions them
intelligently for each ratio.
"""

import argparse
import json
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Optional

# ── Target formats ──────────────────────────────────────────────────────

RATIOS = {
    "9x16":  (1080, 1920),
    "16x9":  (1920, 1080),
    "4x5":   (1080, 1350),
    "1x1":   (1080, 1080),
}

# ── Helpers ─────────────────────────────────────────────────────────────

SVG_NS = "http://www.w3.org/2000/svg"
XLINK_NS = "http://www.w3.org/1999/xlink"

ET.register_namespace("", SVG_NS)
ET.register_namespace("xlink", XLINK_NS)


def detect_source_ratio(width: int, height: int) -> str:
    r = width / height
    if r > 1.4:
        return "16x9"
    elif r < 0.65:
        return "9x16"
    elif 0.75 <= r <= 0.85:
        return "4x5"
    else:
        return "1x1"


def ns(tag: str) -> str:
    """Prepend SVG namespace to a tag name."""
    return f"{{{SVG_NS}}}{tag}"


@dataclass
class LayoutZone:
    """Defines a rectangular zone in normalized [0,1] coordinates."""
    x: float
    y: float
    w: float
    h: float


# For each target ratio, define where the major zones should be placed.
# Coordinates are in fractions of canvas size.
ZONE_LAYOUTS = {
    "9x16": {
        "title":     LayoutZone(0.046, 0.050, 0.90, 0.08),
        "stats":     LayoutZone(0.55,  0.08,  0.42, 0.40),
        "map":       LayoutZone(0.04,  0.12,  0.50, 0.33),
        "chart":     LayoutZone(0.015, 0.83,  0.97, 0.16),
    },
    "16x9": {
        "title":     LayoutZone(0.026, 0.065, 0.90, 0.12),
        "stats":     LayoutZone(0.55,  0.12,  0.42, 0.75),
        "map":       LayoutZone(0.03,  0.18,  0.45, 0.60),
        "chart":     LayoutZone(0.015, 0.78,  0.97, 0.20),
    },
    "1x1": {
        "title":     LayoutZone(0.046, 0.055, 0.90, 0.10),
        "stats":     LayoutZone(0.55,  0.11,  0.42, 0.55),
        "map":       LayoutZone(0.04,  0.15,  0.48, 0.45),
        "chart":     LayoutZone(0.015, 0.78,  0.97, 0.20),
    },
    "4x5": {
        "title":     LayoutZone(0.046, 0.055, 0.90, 0.09),
        "stats":     LayoutZone(0.55,  0.10,  0.42, 0.50),
        "map":       LayoutZone(0.04,  0.14,  0.48, 0.40),
        "chart":     LayoutZone(0.015, 0.80,  0.97, 0.18),
    },
}


# ── Source layout extraction ────────────────────────────────────────────

@dataclass
class StatRow:
    """A metric slot row (value + label + optional unit + optional card) occupying a row in the stats column."""
    value_id: str
    label_id: Optional[str]
    unit_id: Optional[str]
    value_elem: ET.Element
    label_elem: Optional[ET.Element]
    unit_elem: Optional[ET.Element]
    card_elem: Optional[ET.Element]
    y_center: float  # average y of the pair in source coords


def extract_stat_rows(overlay_group: ET.Element):
    """Find metric_N_value/label/unit triplets and card_N backgrounds, sorted by slot number."""
    import re
    metric_re = re.compile(r'^metric_(\d+)_(value|label|unit)$')
    card_re = re.compile(r'^card_(\d+)$')
    slots = {}  # slot_num -> {part: (elem, y)}
    cards = {}  # slot_num -> rect element

    for elem in overlay_group:
        tag = elem.tag.replace(f"{{{SVG_NS}}}", "")
        eid = elem.get("id", "")

        if tag == "text":
            m = metric_re.match(eid)
            if m:
                slot_num = int(m.group(1))
                part = m.group(2)
                tspan = elem.find(ns("tspan"))
                y_str = (tspan.get("y") if tspan is not None else None) or elem.get("y", "0")
                y = float(y_str)
                if slot_num not in slots:
                    slots[slot_num] = {}
                slots[slot_num][part] = (elem, y)
        elif tag == "rect":
            m = card_re.match(eid)
            if m:
                cards[int(m.group(1))] = elem

    rows = []
    for slot_num in sorted(slots.keys()):
        parts = slots[slot_num]
        value_data = parts.get("value")
        label_data = parts.get("label")
        unit_data = parts.get("unit")
        if value_data is None:
            continue
        v_elem, v_y = value_data
        l_elem = label_data[0] if label_data else None
        l_y = label_data[1] if label_data else v_y
        u_elem = unit_data[0] if unit_data else None
        y_center = (v_y + l_y) / 2
        vid = f"metric_{slot_num}_value"
        lid = f"metric_{slot_num}_label" if l_elem is not None else None
        uid = f"metric_{slot_num}_unit" if u_elem is not None else None
        card = cards.get(slot_num)
        rows.append(StatRow(vid, lid, uid, v_elem, l_elem, u_elem, card, y_center))

    return rows


# ── Coordinate transformation ──────────────────────────────────────────

def reposition_text(elem: ET.Element, new_x: float, new_y: float, new_font_size: float):
    """Update a <text> element's tspan position and font-size."""
    tspan = elem.find(ns("tspan"))
    if tspan is not None:
        tspan.set("x", f"{new_x:.3f}")
        tspan.set("y", f"{new_y:.3f}")
    elem.set("font-size", f"{new_font_size:.0f}")


def reposition_chart_map_group(group: ET.Element, zone: LayoutZone, tgt_w: int, tgt_h: int):
    """Reposition a chart/map group by updating its transform and scaling children."""
    new_x = zone.x * tgt_w
    new_y = zone.y * tgt_h
    new_w = zone.w * tgt_w
    new_h = zone.h * tgt_h

    # Find source dimensions from the largest rect in the group
    src_w = src_h = 0
    for child in group.iter():
        if child is group:
            continue
        tag = child.tag.replace(f"{{{SVG_NS}}}", "")
        if tag == "rect":
            src_w = max(src_w, float(child.get("width", "0")))
            src_h = max(src_h, float(child.get("height", "0")))

    if src_w == 0 or src_h == 0:
        group.set("transform", f"translate({new_x:.0f},{new_y:.0f})")
        return

    sx, sy = new_w / src_w, new_h / src_h
    has_transform = "translate" in group.get("transform", "")

    if not has_transform:
        # Children are at absolute coords — make them relative to (0,0) first
        min_x = min_y = float('inf')
        for child in group.iter():
            if child is group:
                continue
            tag = child.tag.replace(f"{{{SVG_NS}}}", "")
            if tag == "rect":
                min_x = min(min_x, float(child.get("x", "0")))
                min_y = min(min_y, float(child.get("y", "0")))
            elif tag == "line":
                min_x = min(min_x, float(child.get("x1", "0")))
                min_y = min(min_y, float(child.get("y1", "0")))
            elif tag in ("circle", "ellipse"):
                min_x = min(min_x, float(child.get("cx", "0")))
                min_y = min(min_y, float(child.get("cy", "0")))

        if min_x != float('inf'):
            for child in group.iter():
                if child is group:
                    continue
                _offset_shape(child, -min_x, -min_y)

    # Set transform to target position
    group.set("transform", f"translate({new_x:.0f},{new_y:.0f})")

    # Scale all descendants proportionally
    for child in group.iter():
        if child is group:
            continue
        _scale_shape(child, sx, sy)


def _offset_shape(elem: ET.Element, dx: float, dy: float):
    """Shift a shape element's coordinates by (dx, dy)."""
    tag = elem.tag.replace(f"{{{SVG_NS}}}", "")
    if tag == "rect":
        elem.set("x", f"{float(elem.get('x', '0')) + dx:.0f}")
        elem.set("y", f"{float(elem.get('y', '0')) + dy:.0f}")
    elif tag == "line":
        for a in ("x1", "x2"):
            elem.set(a, f"{float(elem.get(a, '0')) + dx:.1f}")
        for a in ("y1", "y2"):
            elem.set(a, f"{float(elem.get(a, '0')) + dy:.1f}")
    elif tag in ("circle", "ellipse"):
        elem.set("cx", f"{float(elem.get('cx', '0')) + dx:.1f}")
        elem.set("cy", f"{float(elem.get('cy', '0')) + dy:.1f}")


def _scale_shape(elem: ET.Element, sx: float, sy: float):
    """Scale a shape element's geometry in-place."""
    tag = elem.tag.replace(f"{{{SVG_NS}}}", "")
    if tag == "rect":
        elem.set("x", f"{float(elem.get('x', '0')) * sx:.0f}")
        elem.set("y", f"{float(elem.get('y', '0')) * sy:.0f}")
        elem.set("width", f"{float(elem.get('width', '0')) * sx:.0f}")
        elem.set("height", f"{float(elem.get('height', '0')) * sy:.0f}")
    elif tag == "line":
        for a in ("x1", "x2"):
            elem.set(a, f"{float(elem.get(a, '0')) * sx:.1f}")
        for a in ("y1", "y2"):
            elem.set(a, f"{float(elem.get(a, '0')) * sy:.1f}")
    elif tag == "circle":
        elem.set("cx", f"{float(elem.get('cx', '0')) * sx:.1f}")
        elem.set("cy", f"{float(elem.get('cy', '0')) * sy:.1f}")
    elif tag == "ellipse":
        elem.set("cx", f"{float(elem.get('cx', '0')) * sx:.1f}")
        elem.set("cy", f"{float(elem.get('cy', '0')) * sy:.1f}")


# ── Main conversion ────────────────────────────────────────────────────

def convert_svg(source_path: str, target_ratio: str) -> str:
    """Convert an SVG template to the given aspect ratio. Returns SVG string."""

    tree = ET.parse(source_path)
    root = tree.getroot()

    src_w = int(float(root.get("width", "1080")))
    src_h = int(float(root.get("height", "1920")))
    src_ratio = detect_source_ratio(src_w, src_h)

    tgt_w, tgt_h = RATIOS[target_ratio]

    # If source matches target, just update the overlay group id (if present) and return
    if src_ratio == target_ratio:
        for g in root.iter(ns("g")):
            gid = g.get("id", "")
            if gid.startswith("overlay_"):
                g.set("id", f"overlay_{target_ratio}")
                break
        return ET.tostring(root, encoding="unicode", xml_declaration=False)

    # Update root dimensions
    root.set("width", str(tgt_w))
    root.set("height", str(tgt_h))
    root.set("viewBox", f"0 0 {tgt_w} {tgt_h}")

    # Find the overlay group (if it exists) or use root as container
    overlay_group = None
    for g in root.iter(ns("g")):
        gid = g.get("id", "")
        if gid.startswith("overlay_"):
            g.set("id", f"overlay_{target_ratio}")
            overlay_group = g
            break

    # Fall back to root SVG element as the container
    if overlay_group is None:
        overlay_group = root

    zones = ZONE_LAYOUTS[target_ratio]

    # Uniform scale factor for font sizes (based on shorter dimension ratio)
    font_scale = min(tgt_w / src_w, tgt_h / src_h)

    # ── Process scrim (background gradient) ──────────────────────────
    for elem in overlay_group:
        if elem.get("id") == "scrim":
            scrim_ratio = float(elem.get("height", "0")) / src_h if src_h else 0.44
            scrim_y = tgt_h * (1 - scrim_ratio)
            elem.set("x", "0")
            elem.set("y", f"{scrim_y:.0f}")
            elem.set("width", str(tgt_w))
            elem.set("height", f"{tgt_h * scrim_ratio:.0f}")
            break

    # ── Process title ───────────────────────────────────────────────
    title_zone = zones["title"]
    for elem in overlay_group:
        if elem.get("id") == "title_text":
            orig_fs = float(elem.get("font-size", "100"))
            new_fs = max(30, orig_fs * font_scale)
            tspan = elem.find(ns("tspan"))
            if tspan is not None:
                new_x = title_zone.x * tgt_w
                new_y = title_zone.y * tgt_h + new_fs * 0.8
                reposition_text(elem, new_x, new_y, new_fs)
            sw = elem.get("stroke-width")
            if sw:
                elem.set("stroke-width", f"{float(sw) * font_scale:.1f}")
            break

    # ── Process stats (right-aligned column) ────────────────────────
    stat_rows = extract_stat_rows(overlay_group)
    stats_zone = zones["stats"]

    if stat_rows:
        n_rows = len(stat_rows)
        zone_top = stats_zone.y * tgt_h
        zone_h = stats_zone.h * tgt_h
        row_spacing = zone_h / n_rows
        right_edge = (stats_zone.x + stats_zone.w) * tgt_w

        for i, row in enumerate(stat_rows):
            row_center_y = zone_top + row_spacing * (i + 0.5)

            # Metric value: larger font, positioned above center
            orig_stat_fs = float(row.value_elem.get("font-size", "75"))
            new_stat_fs = max(24, orig_stat_fs * font_scale)
            stat_y = row_center_y - new_stat_fs * 0.15
            reposition_text(row.value_elem, right_edge, stat_y, new_stat_fs)
            sw = row.value_elem.get("stroke-width")
            if sw:
                row.value_elem.set("stroke-width", f"{float(sw) * font_scale:.1f}")

            # Label: smaller font, positioned below value
            if row.label_elem is not None:
                orig_label_fs = float(row.label_elem.get("font-size", "50"))
                new_label_fs = max(18, orig_label_fs * font_scale)
                label_y = stat_y + new_label_fs * 1.2
                reposition_text(row.label_elem, right_edge, label_y, new_label_fs)
                sw = row.label_elem.get("stroke-width")
                if sw:
                    row.label_elem.set("stroke-width", f"{float(sw) * font_scale:.1f}")

            # Unit: small font, positioned near value
            if row.unit_elem is not None:
                orig_unit_fs = float(row.unit_elem.get("font-size", "30"))
                new_unit_fs = max(14, orig_unit_fs * font_scale)
                unit_y = stat_y + new_unit_fs * 0.3
                reposition_text(row.unit_elem, right_edge, unit_y, new_unit_fs)
                sw = row.unit_elem.get("stroke-width")
                if sw:
                    row.unit_elem.set("stroke-width", f"{float(sw) * font_scale:.1f}")

            # Card background: spans the stats zone width at this row
            if row.card_elem is not None:
                gap = row_spacing * 0.06
                pad_x = 16
                card_x = stats_zone.x * tgt_w - pad_x
                card_y = zone_top + row_spacing * i + gap
                card_w = stats_zone.w * tgt_w + 2 * pad_x
                card_h = row_spacing - 2 * gap
                src_rx = float(row.card_elem.get("rx", "12"))
                new_rx = max(6, src_rx * font_scale)
                row.card_elem.set("x", f"{card_x:.0f}")
                row.card_elem.set("y", f"{card_y:.0f}")
                row.card_elem.set("width", f"{card_w:.0f}")
                row.card_elem.set("height", f"{card_h:.0f}")
                row.card_elem.set("rx", f"{new_rx:.0f}")
                row.card_elem.set("ry", f"{new_rx:.0f}")

    # ── Process chart and map groups ────────────────────────────────
    for elem in list(overlay_group):
        eid = elem.get("id", "")
        if eid == "elevation_chart":
            reposition_chart_map_group(elem, zones["chart"], tgt_w, tgt_h)
        elif eid == "route_map":
            reposition_chart_map_group(elem, zones["map"], tgt_w, tgt_h)

    return ET.tostring(root, encoding="unicode", xml_declaration=False)


def extract_fonts_from_svg(svg_path: str) -> set:
    """Extract unique font-family values from an SVG."""
    fonts = set()
    tree = ET.parse(svg_path)
    for elem in tree.iter():
        ff = elem.get("font-family")
        if ff:
            fonts.add(ff.strip().strip("'\""))
    return fonts


def build_template_dir(source_svg: str, template_name: str, output_dir: str,
                       font_files: Optional[list] = None):
    """Create the full template directory with all ratio variants."""

    tree = ET.parse(source_svg)
    root = tree.getroot()
    src_w = int(float(root.get("width", "1080")))
    src_h = int(float(root.get("height", "1920")))
    src_ratio = detect_source_ratio(src_w, src_h)

    print(f"Source: {source_svg}")
    print(f"Detected ratio: {src_ratio} ({src_w}x{src_h})")
    print(f"Template name: {template_name}")
    print(f"Output: {output_dir}")
    print()

    # Create directory structure
    tpl_dir = os.path.join(output_dir, template_name)
    fonts_dir = os.path.join(tpl_dir, "fonts")
    os.makedirs(fonts_dir, exist_ok=True)

    # Extract fonts used in the SVG
    font_families = extract_fonts_from_svg(source_svg)
    print(f"Fonts detected: {font_families}")

    # Build lookup from explicitly provided --font files
    explicit_fonts = {}  # normalized_name -> (src_path, filename)
    for fp in (font_files or []):
        fname = os.path.basename(fp)
        norm = os.path.splitext(fname)[0].lower().replace("-", "").replace("_", "")
        explicit_fonts[norm] = (fp, fname)

    # Try to find font files in known locations
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    asset_fonts_dir = os.path.join(repo_root, "app", "src", "main", "assets", "fonts")
    templates_dir = os.path.join(repo_root, "app", "src", "main", "assets", "templates")
    font_map = {}

    # Build list of candidate dirs: global fonts, plus all existing template font dirs
    candidate_dirs = [asset_fonts_dir]
    if os.path.isdir(templates_dir):
        for entry in os.listdir(templates_dir):
            fdir = os.path.join(templates_dir, entry, "fonts")
            if os.path.isdir(fdir):
                candidate_dirs.append(fdir)

    for family in font_families:
        found = False
        normalized = family.replace(" ", "").lower()

        # 1) Check explicitly provided font files first
        for norm_key, (src_path, fname) in explicit_fonts.items():
            if normalized in norm_key or norm_key.startswith(normalized[:8]):
                dst_font = os.path.join(fonts_dir, fname)
                if not os.path.exists(dst_font):
                    shutil.copy2(src_path, dst_font)
                font_map[family] = f"fonts/{fname}"
                print(f"  Linked font: '{family}' → {fname} (from --font)")
                found = True
                break

        # 2) Search candidate directories
        if not found:
            for candidate_dir in candidate_dirs:
                if not os.path.isdir(candidate_dir):
                    continue
                for f in os.listdir(candidate_dir):
                    if f.endswith((".ttf", ".otf")):
                        fname_norm = os.path.splitext(f)[0].lower().replace("-", "").replace("_", "")
                        if normalized in fname_norm or fname_norm.startswith(normalized[:8]):
                            src_font = os.path.join(candidate_dir, f)
                            dst_font = os.path.join(fonts_dir, f)
                            if not os.path.exists(dst_font):
                                shutil.copy2(src_font, dst_font)
                            font_map[family] = f"fonts/{f}"
                            print(f"  Linked font: '{family}' → {f}")
                            found = True
                            break
                if found:
                    break

        if not found:
            print(f"  ⚠ Font '{family}' not found — provide it with --font or add to {fonts_dir}/")

    # Generate all four ratio variants
    aspect_files = {}
    for ratio_key, (tw, th) in RATIOS.items():
        out_filename = f"{template_name}_{ratio_key}.svg"
        out_path = os.path.join(tpl_dir, out_filename)

        svg_str = convert_svg(source_svg, ratio_key)

        with open(out_path, "w", encoding="utf-8") as f:
            f.write(svg_str)

        aspect_files[ratio_key] = out_filename
        print(f"  Generated: {out_filename} ({tw}x{th})")

    # Generate meta.json
    display_name = template_name.replace("_", " ").replace("-", " ").title()
    meta = {
        "name": template_name,
        "displayName": display_name,
        "description": f"Auto-generated from {os.path.basename(source_svg)}",
        "aspectRatios": aspect_files,
        "fonts": font_map,
    }

    meta_path = os.path.join(tpl_dir, "meta.json")
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
    print(f"  Generated: meta.json")

    print(f"\n✅ Template '{template_name}' created at {tpl_dir}")


# ── CLI ─────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Convert a single SVG template to all aspect ratios for GPX Video Producer."
    )
    parser.add_argument("source", help="Path to the source SVG file")
    parser.add_argument(
        "--name", default=None,
        help="Template name (default: derived from filename, e.g., pulp2_9x16.svg → pulp2)"
    )
    parser.add_argument(
        "--out", default=None,
        help="Output directory (default: app/src/main/assets/templates)"
    )
    parser.add_argument(
        "--font", action="append", default=None, metavar="FILE",
        help="Font file (.ttf/.otf) to bundle. Repeatable for multiple fonts. "
             "Auto-matched to font-family names in the SVG."
    )

    args = parser.parse_args()

    if not os.path.isfile(args.source):
        print(f"Error: file not found: {args.source}", file=sys.stderr)
        sys.exit(1)

    # Derive template name from filename if not specified
    if args.name:
        name = args.name
    else:
        basename = os.path.splitext(os.path.basename(args.source))[0]
        # Strip ratio suffix (e.g., pulp2_9x16 → pulp2)
        for ratio in RATIOS:
            suffix = f"_{ratio}"
            if basename.endswith(suffix):
                basename = basename[:-len(suffix)]
                break
        name = basename

    # Default output directory
    if args.out:
        out_dir = args.out
    else:
        repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        out_dir = os.path.join(repo_root, "app", "src", "main", "assets", "templates")

    # Validate font files
    font_files = []
    for fp in (args.font or []):
        if not os.path.isfile(fp):
            print(f"Error: font file not found: {fp}", file=sys.stderr)
            sys.exit(1)
        if not fp.lower().endswith((".ttf", ".otf")):
            print(f"Error: font must be .ttf or .otf: {fp}", file=sys.stderr)
            sys.exit(1)
        font_files.append(fp)

    build_template_dir(args.source, name, out_dir, font_files=font_files or None)


if __name__ == "__main__":
    main()
