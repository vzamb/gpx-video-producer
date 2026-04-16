#!/usr/bin/env python3
"""
Generate 5 premium templates × 4 aspect ratios each.

Writes SVGs into app/src/main/assets/templates/<name>/<name>_<ratio>.svg
and copies the appropriate font into that folder. Afterwards run
finalize_template.py on each folder to produce meta.json.

The SVG element IDs follow project conventions:
  - title_text
  - metric_N_value, metric_N_label
  - elevation_chart (group: area, line, full_path, dot, glow)
  - route_map (group: full_route, route, dot, glow)
"""

import os
import shutil
import subprocess
import sys

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TEMPLATES_DIR = os.path.join(REPO, "app/src/main/assets/templates")
FONTS_SRC = "/tmp/template_fonts"

RATIOS = {
    "9x16": (1080, 1920),
    "16x9": (1920, 1080),
    "4x5":  (1080, 1350),
    "1x1":  (1080, 1080),
}


def svg_header(w, h, bg="#0B0B0E"):
    return (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<rect width="{w}" height="{h}" fill="{bg}"/>\n'
    )


def svg_footer():
    return "</svg>\n"


def text(id_, x, y, size, family, fill, content, anchor="start", weight="bold",
         stroke=None, stroke_width=0, letter_spacing="0em"):
    stroke_attr = ""
    if stroke and stroke_width:
        stroke_attr = f' stroke="{stroke}" stroke-width="{stroke_width}" paint-order="stroke fill"'
    return (
        f'<text id="{id_}" x="{x}" y="{y}" fill="{fill}"{stroke_attr} '
        f'font-family="{family}" font-size="{size}" font-weight="{weight}" '
        f'text-anchor="{anchor}" letter-spacing="{letter_spacing}">{content}</text>\n'
    )


def line(x1, y1, x2, y2, stroke, stroke_width=2, opacity=1):
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" '
        f'stroke-width="{stroke_width}" opacity="{opacity}"/>\n'
    )


def rect(x, y, w, h, fill=None, stroke=None, stroke_width=0, opacity=1, rx=0):
    attrs = []
    if fill:
        attrs.append(f'fill="{fill}"')
    else:
        attrs.append('fill="none"')
    if stroke:
        attrs.append(f'stroke="{stroke}"')
        attrs.append(f'stroke-width="{stroke_width}"')
    attrs.append(f'opacity="{opacity}"')
    if rx:
        attrs.append(f'rx="{rx}"')
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" {" ".join(attrs)}/>\n'


def elevation_chart(x, y, w, h, line_color, area_color, track_color="#FFFFFF", track_opacity=0.15):
    """Chart box group (area, line, full_path, dot, glow). The app renderer
    reads the bounding rect + colors. Shapes are placeholders."""
    dot_cx = x + w * 0.75
    dot_cy = y + h * 0.45
    return (
        f'<g id="elevation_chart">\n'
        f'  <g id="area"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'fill="{area_color}" opacity="0.25"/></g>\n'
        f'  <g id="full_path"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{track_color}" stroke-width="2" opacity="{track_opacity}" fill="none"/></g>\n'
        f'  <g id="line"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{line_color}" stroke-width="3" fill="none"/></g>\n'
        f'  <g id="glow"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="10" ry="10" '
        f'fill="{line_color}" opacity="0.35"/></g>\n'
        f'  <g id="dot"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="5" ry="5" '
        f'fill="{line_color}"/></g>\n'
        f'</g>\n'
    )


def route_map(x, y, w, h, line_color, track_color="#FFFFFF", track_opacity=0.25):
    dot_cx = x + w * 0.5
    dot_cy = y + h * 0.5
    return (
        f'<g id="route_map">\n'
        f'  <g id="full_route"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{track_color}" stroke-width="2" opacity="{track_opacity}" fill="none"/></g>\n'
        f'  <g id="route"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{line_color}" stroke-width="3" fill="none"/></g>\n'
        f'  <g id="glow"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="10" ry="10" '
        f'fill="{line_color}" opacity="0.35"/></g>\n'
        f'  <g id="dot"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="5" ry="5" '
        f'fill="{line_color}"/></g>\n'
        f'</g>\n'
    )


# ---------- Template generators ----------
# Each returns a full SVG string for (ratio, w, h).

# ===== A. HORIZON (Garmin-style) =====
# Title + 4 horizontal metrics footer with vertical dividers + thin elevation
def gen_horizon(ratio, w, h):
    font = "Montserrat"
    accent = "#FFFFFF"
    sub = "#B8B8BE"
    divider = "#FFFFFF"
    # Gradient scrim at bottom
    scrim_h = int(h * 0.42)
    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n'
        f'  <linearGradient id="scrim" x1="0" y1="0" x2="0" y2="1">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0.75"/>\n'
        f'  </linearGradient>\n'
        f'</defs>\n'
        f'<rect x="0" y="{h-scrim_h}" width="{w}" height="{scrim_h}" fill="url(#scrim)"/>\n'
    )
    # Positions
    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.048)
        title_y = int(h - 420)
        metric_row_y = int(h - 120)
        value_size = int(h * 0.03)
        label_size = int(h * 0.013)
        chart_y = int(h - 360)
        chart_h = int(h * 0.045)
    elif ratio == "1x1":
        title_size = 60
        title_y = h - 380
        metric_row_y = h - 110
        value_size = 54
        label_size = 18
        chart_y = h - 310
        chart_h = 45
    else:  # 16x9
        title_size = 54
        title_y = h - 310
        metric_row_y = h - 95
        value_size = 46
        label_size = 16
        chart_y = h - 250
        chart_h = 40

    margin = int(w * 0.05)
    out += text("title_text", margin, title_y, title_size, font, accent,
                "Morning Run", weight="800", letter_spacing="-0.02em")
    # thin elevation chart
    out += elevation_chart(margin, chart_y, w - 2 * margin, chart_h,
                           line_color=accent, area_color=accent, track_opacity=0.08)
    # 4 metrics horizontal
    cols = 4
    col_w = (w - 2 * margin) / cols
    labels = ["DISTANCE", "TIME", "PACE", "HEART"]
    values = ["10.0", "1:00:00", "05:00", "145"]
    for i in range(cols):
        cx = margin + col_w * i + col_w / 2
        out += text(f"metric_{i+1}_value", cx, metric_row_y, value_size, font,
                    accent, values[i], anchor="middle", weight="800",
                    letter_spacing="-0.02em")
        out += text(f"metric_{i+1}_label", cx, metric_row_y + label_size + 14,
                    label_size, font, sub, labels[i], anchor="middle",
                    weight="600", letter_spacing="0.15em")
        if i < cols - 1:
            div_x = margin + col_w * (i + 1)
            out += line(div_x, metric_row_y - value_size, div_x,
                        metric_row_y + label_size + 20, divider, 1, 0.35)
    out += svg_footer()
    return out


# ===== B. EDITORIAL (Adidas serif) =====
def gen_editorial(ratio, w, h):
    font = "Playfair Display"
    mono = "Inter"
    accent = "#F4EFE6"
    sub = "#A09A8E"
    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.062)
        title_y = h - 300
        top_y = 120
        metric_row_y = h - 130
        value_size = int(h * 0.028)
        label_size = 16
    elif ratio == "1x1":
        title_size = 78
        title_y = h - 260
        top_y = 100
        metric_row_y = h - 110
        value_size = 44
        label_size = 16
    else:
        title_size = 72
        title_y = h - 230
        top_y = 90
        metric_row_y = h - 100
        value_size = 40
        label_size = 15

    margin = int(w * 0.055)
    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n'
        f'  <linearGradient id="ed_scrim" x1="0" y1="0" x2="0" y2="1">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0.55"/>\n'
        f'  </linearGradient>\n'
        f'</defs>\n'
        f'<rect x="0" y="{int(h*0.4)}" width="{w}" height="{int(h*0.6)}" '
        f'fill="url(#ed_scrim)"/>\n'
    )
    # Top-right brand line
    out += text("brand", w - margin, top_y, 22, mono, accent, "RUNNING",
                anchor="end", weight="700", letter_spacing="0.35em")
    out += line(w - margin - 140, top_y + 12, w - margin, top_y + 12, accent, 1.5, 0.6)
    # Serif title
    out += text("title_text", margin, title_y, title_size, font, accent,
                "Morning Run", weight="700", letter_spacing="-0.01em")
    # Italic date accent
    # Horizontal divider line above metrics
    out += line(margin, metric_row_y - 60, w - margin, metric_row_y - 60,
                accent, 1, 0.35)
    # 3 metrics horizontal
    cols = 3
    col_w = (w - 2 * margin) / cols
    labels = ["DISTANCE", "TIME", "PACE"]
    values = ["10.0", "1:00:00", "05:00"]
    for i in range(cols):
        cx = margin + col_w * i
        out += text(f"metric_{i+1}_value", cx, metric_row_y, value_size, font,
                    accent, values[i], weight="700", letter_spacing="-0.01em")
        out += text(f"metric_{i+1}_label", cx, metric_row_y + label_size + 12,
                    label_size, mono, sub, labels[i], weight="600",
                    letter_spacing="0.3em")
    out += svg_footer()
    return out


# ===== C. EXPEDITION (left-stack + route) =====
def gen_expedition(ratio, w, h):
    font = "Bebas Neue"
    mono = "Inter"
    accent = "#FFFFFF"
    highlight = "#FF5A1F"
    sub = "#BDBDBD"
    margin = int(w * 0.055)
    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n'
        f'  <linearGradient id="ex_scrim" x1="0" y1="1" x2="0" y2="0">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0.75"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0"/>\n'
        f'  </linearGradient>\n'
        f'</defs>\n'
        f'<rect x="0" y="0" width="{w}" height="{int(h*0.35)}" '
        f'transform="translate(0,{int(h*0.65)})" fill="url(#ex_scrim)"/>\n'
    )

    if ratio == "9x16" or ratio == "4x5":
        title_size = int(h * 0.06)
        title_y = 180
        metric_y_start = int(h * 0.55)
        metric_gap = int(h * 0.07)
        value_size = int(h * 0.04)
        label_size = 20
        route_w = int(w * 0.85)
        route_h = int(h * 0.25)
        route_x = (w - route_w) // 2
        route_y = int(h * 0.22)
    elif ratio == "1x1":
        title_size = 72
        title_y = 130
        metric_y_start = 500
        metric_gap = 95
        value_size = 56
        label_size = 20
        route_w = 500
        route_h = 360
        route_x = (w - route_w) // 2
        route_y = 180
    else:  # 16x9
        title_size = 64
        title_y = 110
        metric_y_start = 400
        metric_gap = 85
        value_size = 50
        label_size = 18
        route_w = 560
        route_h = 560
        route_x = (w - route_w) // 2
        route_y = 200

    # Title
    out += text("title_text", margin, title_y, title_size, font, accent,
                "MILAN RUNNING", weight="400", letter_spacing="0.02em")
    # subtitle / accent line
    out += line(margin, title_y + 25, margin + 240, title_y + 25, highlight, 4)

    # Route map centered
    out += route_map(route_x, route_y, route_w, route_h, highlight,
                     track_color=accent, track_opacity=0.3)

    # 3 left-stacked metrics
    labels = ["DISTANCE", "TIME", "PACE"]
    values = ["10.0", "1:00:00", "05:00"]
    for i in range(3):
        y = metric_y_start + i * metric_gap
        # small diagonal accent bar
        out += line(margin, y - value_size + 6, margin + 22, y - value_size - 18,
                    highlight, 3)
        out += text(f"metric_{i+1}_label", margin + 40, y - value_size + 8,
                    label_size, mono, sub, labels[i], weight="600",
                    letter_spacing="0.3em")
        out += text(f"metric_{i+1}_value", margin + 40, y, value_size, font,
                    accent, values[i], weight="400", letter_spacing="0.02em")
    out += svg_footer()
    return out


# ===== D. MINIMAL (huge typography) =====
def gen_minimal(ratio, w, h):
    font = "Oswald"
    mono = "Inter"
    accent = "#0A0A0A"  # dark text on transparent
    bg = "#F6F5F0"
    sub = "#7A7A7A"
    margin = int(w * 0.06)
    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
    )
    # Bottom band
    band_h = int(h * 0.28)
    out += rect(0, h - band_h, w, band_h, fill=bg)

    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.022)
        value_size = int(h * 0.055)
        label_size = 16
        band_top = h - band_h
        title_y = band_top + 55
        row_y = band_top + 55 + title_size + 110
    elif ratio == "1x1":
        title_size = 28
        value_size = 64
        label_size = 16
        band_top = h - band_h
        title_y = band_top + 50
        row_y = band_top + 180
    else:
        title_size = 26
        value_size = 56
        label_size = 15
        band_top = h - band_h
        title_y = band_top + 45
        row_y = band_top + 145

    out += text("title_text", margin, title_y, title_size, mono, accent,
                "MORNING RUN", weight="700", letter_spacing="0.3em")
    # Divider
    out += line(margin, title_y + 18, w - margin, title_y + 18,
                accent, 1, 0.2)

    cols = 3
    col_w = (w - 2 * margin) / cols
    labels = ["DIST", "TIME", "PACE"]
    values = ["10.0", "1:00:00", "05:00"]
    for i in range(cols):
        cx = margin + col_w * i
        out += text(f"metric_{i+1}_value", cx, row_y, value_size, font,
                    accent, values[i], weight="500", letter_spacing="0em")
        out += text(f"metric_{i+1}_label", cx, row_y + 28, label_size,
                    mono, sub, labels[i], weight="600",
                    letter_spacing="0.25em")
    out += svg_footer()
    return out


# ===== E. DASHBOARD (4-metric grid + chart) =====
def gen_dashboard(ratio, w, h):
    font = "Inter"
    display = "Inter"
    accent = "#FFFFFF"
    sub = "#9AA0A6"
    tint = "#1FD1A7"
    card = "#14161A"
    card_stroke = "#262931"
    margin = int(w * 0.045)

    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.03)
        value_size = int(h * 0.045)
        label_size = 20
        card_h = int(h * 0.12)
        grid_top = int(h * 0.55)
        chart_h = int(h * 0.1)
        chart_top = int(h * 0.82)
    elif ratio == "1x1":
        title_size = 34
        value_size = 54
        label_size = 18
        card_h = 120
        grid_top = 520
        chart_h = 110
        chart_top = 860
    else:
        title_size = 30
        value_size = 50
        label_size = 18
        card_h = 110
        grid_top = 480
        chart_h = 100
        chart_top = 820

    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n'
        f'  <linearGradient id="db_scrim" x1="0" y1="1" x2="0" y2="0">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0.85"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0"/>\n'
        f'  </linearGradient>\n'
        f'</defs>\n'
        f'<rect x="0" y="{int(h*0.45)}" width="{w}" height="{int(h*0.55)}" fill="url(#db_scrim)"/>\n'
    )

    # Title
    out += text("title_text", margin, grid_top - 40, title_size, display,
                accent, "Morning Run", weight="700", letter_spacing="-0.01em")
    out += rect(margin, grid_top - 30, 48, 4, fill=tint)

    # 2x2 grid of metric cards
    gap = int(w * 0.02)
    col_w = (w - 2 * margin - gap) / 2
    labels = ["DISTANCE", "TIME", "PACE", "HEART"]
    values = ["10.0", "1:00:00", "05:00", "145"]
    for i in range(4):
        r = i // 2
        c = i % 2
        cx = margin + c * (col_w + gap)
        cy = grid_top + r * (card_h + gap)
        out += rect(cx, cy, col_w, card_h, fill=card, stroke=card_stroke,
                    stroke_width=1, rx=14)
        # Accent bar
        out += rect(cx + 20, cy + 20, 3, card_h - 40, fill=tint)
        out += text(f"metric_{i+1}_label", cx + 40, cy + 38, label_size,
                    font, sub, labels[i], weight="600",
                    letter_spacing="0.2em")
        out += text(f"metric_{i+1}_value", cx + 40,
                    cy + card_h - 28, value_size, display, accent,
                    values[i], weight="700", letter_spacing="-0.02em")

    # Elevation chart
    out += elevation_chart(margin, chart_top, w - 2 * margin, chart_h,
                           line_color=tint, area_color=tint,
                           track_color=accent, track_opacity=0.12)
    out += svg_footer()
    return out


TEMPLATES = {
    "horizon":    (gen_horizon,    "Montserrat",       "Montserrat-Bold.ttf"),
    "editorial":  (gen_editorial,  "Playfair Display", "PlayfairDisplay-Bold.ttf"),
    "expedition": (gen_expedition, "Bebas Neue",       "BebasNeue-Regular.ttf"),
    "minimal":    (gen_minimal,    "Oswald",           "Oswald-Bold.ttf"),
    "dashboard":  (gen_dashboard,  "Inter",            "Inter-Bold.ttf"),
}

# Additional fonts shared across templates
SHARED_FONTS = {
    "Inter": "Inter-Bold.ttf",
    "Montserrat": "Montserrat-Bold.ttf",
}


def main():
    for name, (gen, family, font_file) in TEMPLATES.items():
        tpl_dir = os.path.join(TEMPLATES_DIR, name)
        if os.path.isdir(tpl_dir):
            shutil.rmtree(tpl_dir)
        os.makedirs(tpl_dir)

        for ratio, (w, h) in RATIOS.items():
            svg = gen(ratio, w, h)
            out_path = os.path.join(tpl_dir, f"{name}_{ratio}.svg")
            with open(out_path, "w", encoding="utf-8") as fh:
                fh.write(svg)
            print(f"  wrote {out_path}")

        # Copy primary font
        src = os.path.join(FONTS_SRC, font_file)
        if not os.path.isfile(src):
            print(f"  !! missing font: {src}", file=sys.stderr)
            sys.exit(1)
        shutil.copy(src, os.path.join(tpl_dir, font_file))

        # Copy Inter (used as secondary/label font in most templates)
        if font_file != "Inter-Bold.ttf":
            inter = os.path.join(FONTS_SRC, "Inter-Bold.ttf")
            shutil.copy(inter, os.path.join(tpl_dir, "Inter-Bold.ttf"))

        # Run finalize
        print(f"\n>>> Finalizing {name}")
        subprocess.check_call(
            ["python3", os.path.join(REPO, "tools/finalize_template.py"), tpl_dir]
        )
        print()


if __name__ == "__main__":
    main()
