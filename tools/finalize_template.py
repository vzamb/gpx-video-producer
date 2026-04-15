#!/usr/bin/env python3
"""
Finalize a template directory for GPX Video Producer.

Takes a folder containing SVG files (and optionally font files) and:
1. Moves font files (.ttf/.otf) into a fonts/ subdirectory
2. Detects aspect ratios from SVG filenames or dimensions
3. Maps font-family names in the SVGs to the bundled font files
4. Generates meta.json

Usage:
    python3 tools/finalize_template.py app/src/main/assets/templates/pulp
"""

import argparse
import json
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

SVG_NS = "http://www.w3.org/2000/svg"

KNOWN_RATIOS = {
    "9x16": (1080, 1920),
    "16x9": (1920, 1080),
    "4x5":  (1080, 1350),
    "1x1":  (1080, 1080),
}


def detect_ratio_from_filename(filename: str) -> str | None:
    """Extract ratio key from filename like pulp_9x16.svg."""
    for key in KNOWN_RATIOS:
        if f"_{key}" in filename:
            return key
    return None


def detect_ratio_from_svg(path: str) -> str | None:
    """Detect ratio from SVG width/height attributes."""
    try:
        tree = ET.parse(path)
        root = tree.getroot()
        w = float(root.get("width", "0"))
        h = float(root.get("height", "0"))
        if w == 0 or h == 0:
            return None
        r = w / h
        if r > 1.4:
            return "16x9"
        elif r < 0.65:
            return "9x16"
        elif 0.75 <= r <= 0.85:
            return "4x5"
        else:
            return "1x1"
    except Exception:
        return None


def extract_font_families(svg_path: str) -> set[str]:
    """Extract unique font-family values from an SVG."""
    fonts = set()
    tree = ET.parse(svg_path)
    for elem in tree.iter():
        ff = elem.get("font-family")
        if ff:
            fonts.add(ff.strip().strip("'\""))
    return fonts


def fuzzy_match_font(family: str, filename: str) -> bool:
    """Check if a font filename fuzzy-matches a CSS font-family name."""
    normalized = family.replace(" ", "").lower()
    fname_norm = os.path.splitext(filename)[0].lower().replace("-", "").replace("_", "")
    return normalized in fname_norm or fname_norm.startswith(normalized[:8])


def finalize(template_dir: str):
    template_dir = os.path.abspath(template_dir)
    template_name = os.path.basename(template_dir)

    if not os.path.isdir(template_dir):
        print(f"Error: not a directory: {template_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"Template: {template_name}")
    print(f"Directory: {template_dir}\n")

    # Collect files
    svg_files = []
    font_files = []
    for f in os.listdir(template_dir):
        fp = os.path.join(template_dir, f)
        if not os.path.isfile(fp):
            continue
        if f.lower().endswith(".svg"):
            svg_files.append(f)
        elif f.lower().endswith((".ttf", ".otf")):
            font_files.append(f)

    if not svg_files:
        print("Error: no SVG files found in directory", file=sys.stderr)
        sys.exit(1)

    # Move fonts into fonts/ subdirectory
    fonts_dir = os.path.join(template_dir, "fonts")
    if font_files:
        os.makedirs(fonts_dir, exist_ok=True)
        for f in font_files:
            src = os.path.join(template_dir, f)
            dst = os.path.join(fonts_dir, f)
            if src != dst:
                shutil.move(src, dst)
                print(f"  Moved: {f} → fonts/{f}")

    # Detect aspect ratios
    aspect_ratios = {}
    for f in sorted(svg_files):
        ratio = detect_ratio_from_filename(f)
        if ratio is None:
            ratio = detect_ratio_from_svg(os.path.join(template_dir, f))
        if ratio:
            aspect_ratios[ratio] = f
            print(f"  {f} → {ratio}")
        else:
            print(f"  ⚠ Could not detect ratio for {f} — skipping")

    if not aspect_ratios:
        print("Error: no valid aspect ratios detected", file=sys.stderr)
        sys.exit(1)

    # Extract all font families from SVGs
    all_families = set()
    for f in svg_files:
        all_families |= extract_font_families(os.path.join(template_dir, f))

    # Match font families to font files
    font_map = {}
    available_fonts = os.listdir(fonts_dir) if os.path.isdir(fonts_dir) else []
    for family in sorted(all_families):
        matched = False
        for ff in available_fonts:
            if ff.lower().endswith((".ttf", ".otf")) and fuzzy_match_font(family, ff):
                font_map[family] = f"fonts/{ff}"
                print(f"  Font: '{family}' → fonts/{ff}")
                matched = True
                break
        if not matched and family not in ("sans-serif", "serif", "monospace"):
            print(f"  ⚠ Font '{family}' not found in fonts/ — add it manually")

    # Generate meta.json
    display_name = template_name.replace("_", " ").replace("-", " ").title()
    meta = {
        "name": template_name,
        "displayName": display_name,
        "description": "",
        "aspectRatios": aspect_ratios,
        "fonts": font_map,
    }

    meta_path = os.path.join(template_dir, "meta.json")
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
    print(f"  Generated: meta.json")

    print(f"\n✅ Template '{template_name}' finalized")
    print(f"   Ratios: {', '.join(sorted(aspect_ratios.keys()))}")
    if font_map:
        print(f"   Fonts:  {', '.join(font_map.keys())}")


def main():
    parser = argparse.ArgumentParser(
        description="Finalize a template directory: organize fonts and generate meta.json."
    )
    parser.add_argument("directory", help="Path to the template directory")
    args = parser.parse_args()
    finalize(args.directory)


if __name__ == "__main__":
    main()
