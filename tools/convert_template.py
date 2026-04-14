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

The script detects element roles from their SVG ids (stat_*, label_*, title_text,
elevation_chart, route_map) and repositions them intelligently for each ratio.
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
    """A stat/label pair occupying a row in the stats column."""
    stat_id: str
    label_id: Optional[str]
    stat_elem: ET.Element
    label_elem: Optional[ET.Element]
    y_center: float  # average y of the pair in source coords


def extract_stat_rows(overlay_group: ET.Element):
    """Find stat+label pairs, sorted by vertical position."""
    texts = {}
    for elem in overlay_group:
        tag = elem.tag.replace(f"{{{SVG_NS}}}", "")
        if tag != "text":
            continue
        eid = elem.get("id", "")
        if eid.startswith("stat_") or eid.startswith("label_"):
            tspan = elem.find(ns("tspan"))
            if tspan is not None:
                y = float(tspan.get("y", "0"))
                texts[eid] = (elem, y)

    # Group by suffix (e.g., stat_hr + label_hr)
    stat_ids = [k for k in texts if k.startswith("stat_")]
    rows = []
    for sid in stat_ids:
        suffix = sid[len("stat_"):]
        lid = f"label_{suffix}"
        s_elem, s_y = texts[sid]
        l_elem, l_y = texts.get(lid, (None, s_y))
        y_center = (s_y + l_y) / 2
        rows.append(StatRow(sid, lid if lid in texts else None, s_elem, l_elem, y_center))

    rows.sort(key=lambda r: r.y_center)
    return rows


# ── Coordinate transformation ──────────────────────────────────────────

def reposition_text(elem: ET.Element, new_x: float, new_y: float, new_font_size: float):
    """Update a <text> element's tspan position and font-size."""
    tspan = elem.find(ns("tspan"))
    if tspan is not None:
        tspan.set("x", f"{new_x:.3f}")
        tspan.set("y", f"{new_y:.3f}")
    elem.set("font-size", f"{new_font_size:.0f}")


def reposition_group_rect(group: ET.Element, zone: LayoutZone, tgt_w: int, tgt_h: int):
    """Reposition all rects/circles/ellipses inside a chart/map group."""
    x = zone.x * tgt_w
    y = zone.y * tgt_h
    w = zone.w * tgt_w
    h = zone.h * tgt_h

    for child in group.iter():
        tag = child.tag.replace(f"{{{SVG_NS}}}", "")
        if tag == "rect":
            child.set("x", f"{x:.0f}")
            child.set("y", f"{y:.0f}")
            child.set("width", f"{w:.0f}")
            child.set("height", f"{h:.0f}")
        elif tag == "circle":
            child.set("cx", f"{x + w * 0.2:.1f}")
            child.set("cy", f"{y + h * 0.5:.1f}")
            r = child.get("r")
            if r:
                child.set("r", f"{max(2, min(float(r), w * 0.01)):.1f}")
        elif tag == "ellipse":
            child.set("cx", f"{x + w * 0.2:.1f}")
            child.set("cy", f"{y + h * 0.5:.1f}")
            rx = child.get("rx")
            ry = child.get("ry")
            if rx:
                child.set("rx", f"{max(2, min(float(rx), w * 0.012)):.1f}")
            if ry:
                child.set("ry", f"{max(2, min(float(ry), h * 0.02)):.1f}")


def update_filter(defs: ET.Element, filter_id: str, zone: LayoutZone, tgt_w: int, tgt_h: int):
    """Update a filter element's position to match the new zone."""
    for filt in defs.iter(ns("filter")):
        if filt.get("id") == filter_id:
            x = zone.x * tgt_w - 5
            y = zone.y * tgt_h - 2
            w = zone.w * tgt_w + 10
            h = zone.h * tgt_h + 10
            filt.set("x", f"{x:.0f}")
            filt.set("y", f"{y:.0f}")
            filt.set("width", f"{w:.0f}")
            filt.set("height", f"{h:.0f}")


# ── Main conversion ────────────────────────────────────────────────────

def convert_svg(source_path: str, target_ratio: str) -> str:
    """Convert an SVG template to the given aspect ratio. Returns SVG string."""

    tree = ET.parse(source_path)
    root = tree.getroot()

    src_w = int(float(root.get("width", "1080")))
    src_h = int(float(root.get("height", "1920")))
    src_ratio = detect_source_ratio(src_w, src_h)

    tgt_w, tgt_h = RATIOS[target_ratio]

    # If source matches target, just update the overlay group id and return
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

    # Find the overlay group and rename it
    overlay_group = None
    for g in root.iter(ns("g")):
        gid = g.get("id", "")
        if gid.startswith("overlay_"):
            g.set("id", f"overlay_{target_ratio}")
            overlay_group = g
            break

    if overlay_group is None:
        print(f"  Warning: no overlay_* group found, returning scaled SVG")
        return ET.tostring(root, encoding="unicode", xml_declaration=False)

    zones = ZONE_LAYOUTS[target_ratio]

    # Uniform scale factor for font sizes (based on shorter dimension ratio)
    font_scale = min(tgt_w / src_w, tgt_h / src_h)

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

            # Stat value: larger font, positioned above center
            orig_stat_fs = float(row.stat_elem.get("font-size", "75"))
            new_stat_fs = max(24, orig_stat_fs * font_scale)
            stat_y = row_center_y - new_stat_fs * 0.15
            reposition_text(row.stat_elem, right_edge, stat_y, new_stat_fs)
            sw = row.stat_elem.get("stroke-width")
            if sw:
                row.stat_elem.set("stroke-width", f"{float(sw) * font_scale:.1f}")

            # Label: smaller font, positioned below stat
            if row.label_elem is not None:
                orig_label_fs = float(row.label_elem.get("font-size", "50"))
                new_label_fs = max(18, orig_label_fs * font_scale)
                label_y = stat_y + new_label_fs * 1.2
                reposition_text(row.label_elem, right_edge, label_y, new_label_fs)
                sw = row.label_elem.get("stroke-width")
                if sw:
                    row.label_elem.set("stroke-width", f"{float(sw) * font_scale:.1f}")

    # ── Process chart and map groups ────────────────────────────────
    for elem in list(overlay_group):
        eid = elem.get("id", "")
        if eid == "elevation_chart":
            reposition_group_rect(elem, zones["chart"], tgt_w, tgt_h)
        elif eid == "route_map":
            reposition_group_rect(elem, zones["map"], tgt_w, tgt_h)

    # ── Update filter positions in <defs> ───────────────────────────
    defs = root.find(ns("defs"))
    if defs is not None:
        update_filter(defs, "filter0_d_0_1", zones["chart"], tgt_w, tgt_h)
        update_filter(defs, "filter1_d_0_1", zones["map"], tgt_w, tgt_h)

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


def build_template_dir(source_svg: str, template_name: str, output_dir: str):
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

    # Extract fonts used
    font_families = extract_fonts_from_svg(source_svg)
    print(f"Fonts detected: {font_families}")

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
        for candidate_dir in candidate_dirs:
            if not os.path.isdir(candidate_dir):
                continue
            for f in os.listdir(candidate_dir):
                if f.endswith((".ttf", ".otf")):
                    # Fuzzy match: "Climate Crisis" → "ClimeCrisis" or "ClimateCrisis"
                    normalized = family.replace(" ", "").lower()
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
            print(f"  ⚠ Font '{family}' not found in assets — add it manually to {fonts_dir}/")

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

    build_template_dir(args.source, name, out_dir)


if __name__ == "__main__":
    main()
