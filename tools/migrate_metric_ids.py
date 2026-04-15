#!/usr/bin/env python3
"""
Migrate SVG templates from specific metric IDs (stat_hr, label_hr, stat_hr_unit)
to generic positional slots (metric_1_value, metric_1_label, metric_1_unit).

Scans all .svg files under the templates directory, sorts stat/label pairs by
vertical position, and assigns metric_1, metric_2, ... in top-to-bottom order.

Usage:
    python3 tools/migrate_metric_ids.py [--templates-dir DIR] [--dry-run]
"""

import argparse
import os
import re
import sys


def extract_stat_entries(svg_content: str):
    """
    Extract stat_*/label_* text elements with their Y positions.
    Returns dict: { suffix: { 'stat': (id, y), 'label': (id, y), 'unit': (id, y) } }
    Handles both <text ... y="Y"> and <text ...><tspan ... y="Y"> formats.
    """
    entries = {}

    # Match <text id="stat_X" ...> — extract y from tspan or text element itself
    for m in re.finditer(
        r'<text\s[^>]*id\s*=\s*"(stat_([^"]+?)(?:_unit)?)"[^>]*(?:y="([^"]+)")?[^>]*>(?:.*?<tspan[^>]*y="([^"]+)")?',
        svg_content
    ):
        full_id = m.group(1)
        y_text = m.group(3)
        y_tspan = m.group(4)
        y = float(y_tspan or y_text or "0")

        if full_id.endswith("_unit"):
            suffix = full_id[len("stat_"):-len("_unit")]
            entries.setdefault(suffix, {})["unit"] = (full_id, y)
        else:
            suffix = m.group(2)
            entries.setdefault(suffix, {})["stat"] = (full_id, y)

    # Match <text id="label_X" ...>
    for m in re.finditer(
        r'<text\s[^>]*id\s*=\s*"(label_([^"]+))"[^>]*(?:y="([^"]+)")?[^>]*>(?:.*?<tspan[^>]*y="([^"]+)")?',
        svg_content
    ):
        full_id = m.group(1)
        suffix = m.group(2)
        y_text = m.group(3)
        y_tspan = m.group(4)
        y = float(y_tspan or y_text or "0")
        entries.setdefault(suffix, {})["label"] = (full_id, y)

    return entries


def compute_slot_order(entries: dict) -> list:
    """
    Sort metric suffixes by their vertical position (stat Y value, fallback to label Y).
    Returns ordered list of suffixes.
    """
    def sort_key(suffix):
        e = entries[suffix]
        if "stat" in e:
            return e["stat"][1]
        if "label" in e:
            return e["label"][1]
        if "unit" in e:
            return e["unit"][1]
        return 0

    return sorted(entries.keys(), key=sort_key)


def migrate_svg(svg_content: str) -> tuple:
    """
    Rename stat_*/label_*/stat_*_unit to metric_N_value/label/unit.
    Returns (new_content, rename_map) where rename_map is {old_id: new_id}.
    """
    entries = extract_stat_entries(svg_content)
    if not entries:
        return svg_content, {}

    ordered = compute_slot_order(entries)
    rename_map = {}

    for slot_idx, suffix in enumerate(ordered, start=1):
        e = entries[suffix]
        if "stat" in e:
            rename_map[e["stat"][0]] = f"metric_{slot_idx}_value"
        if "label" in e:
            rename_map[e["label"][0]] = f"metric_{slot_idx}_label"
        if "unit" in e:
            rename_map[e["unit"][0]] = f"metric_{slot_idx}_unit"

    result = svg_content
    # Sort by length descending to avoid partial replacements (e.g., stat_hr before stat_hr_unit)
    for old_id in sorted(rename_map.keys(), key=len, reverse=True):
        new_id = rename_map[old_id]
        result = result.replace(f'id="{old_id}"', f'id="{new_id}"')

    return result, rename_map


def process_file(svg_path: str, dry_run: bool) -> dict:
    """Process a single SVG file. Returns the rename map."""
    with open(svg_path, "r", encoding="utf-8") as f:
        content = f.read()

    new_content, rename_map = migrate_svg(content)

    if not rename_map:
        return {}

    if dry_run:
        print(f"  [DRY RUN] {os.path.basename(svg_path)}:")
    else:
        with open(svg_path, "w", encoding="utf-8") as f:
            f.write(new_content)
        print(f"  ✅ {os.path.basename(svg_path)}:")

    for old_id, new_id in sorted(rename_map.items()):
        print(f"      {old_id:25s} → {new_id}")

    return rename_map


def main():
    parser = argparse.ArgumentParser(description="Migrate SVG template IDs to generic metric slots.")
    parser.add_argument(
        "--templates-dir", default=None,
        help="Templates directory (default: app/src/main/assets/templates)"
    )
    parser.add_argument("--dry-run", action="store_true", help="Preview changes without writing files")
    args = parser.parse_args()

    if args.templates_dir:
        templates_dir = args.templates_dir
    else:
        repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        templates_dir = os.path.join(repo_root, "app", "src", "main", "assets", "templates")

    if not os.path.isdir(templates_dir):
        print(f"Error: directory not found: {templates_dir}", file=sys.stderr)
        sys.exit(1)

    total_files = 0
    total_renames = 0

    for template_name in sorted(os.listdir(templates_dir)):
        tpl_dir = os.path.join(templates_dir, template_name)
        if not os.path.isdir(tpl_dir):
            continue

        print(f"\n📦 {template_name}/")

        for fname in sorted(os.listdir(tpl_dir)):
            if not fname.endswith(".svg"):
                continue
            svg_path = os.path.join(tpl_dir, fname)
            rename_map = process_file(svg_path, args.dry_run)
            if rename_map:
                total_files += 1
                total_renames += len(rename_map)

    print(f"\n{'[DRY RUN] ' if args.dry_run else ''}Done: {total_renames} renames across {total_files} files.")


if __name__ == "__main__":
    main()
