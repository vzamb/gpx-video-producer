#!/usr/bin/env python3
"""
Generate 5 premium templates × 4 aspect ratios each.

Design principles for real-video readability:
- Every text element has a dark stroke outline (paint-order: stroke fill)
  so overlays stay legible on any background.
- Strong gradient scrims under text.
- Values and labels are sized for impact, not subtlety.

Each template also declares which chart/route slots it supports. Layouts
that don't include an elevation_chart or route_map group will have those
toggles disabled in the app's overlay settings.
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


# ---------- Low-level primitives ----------

def stroked_text(id_, x, y, size, family, fill, content,
                 anchor="start", weight="bold", stroke="#000000",
                 stroke_width=None, letter_spacing="0em"):
    """Text with a proportional dark outline for readability on any
    background. paint-order ensures the stroke is behind the fill."""
    if stroke_width is None:
        stroke_width = max(2, round(size * 0.07))
    return (
        f'<text id="{id_}" x="{x}" y="{y}" fill="{fill}" '
        f'stroke="{stroke}" stroke-width="{stroke_width}" '
        f'stroke-linejoin="round" paint-order="stroke fill" '
        f'font-family="{family}" font-size="{size}" font-weight="{weight}" '
        f'text-anchor="{anchor}" letter-spacing="{letter_spacing}">{content}</text>\n'
    )


def line(x1, y1, x2, y2, stroke, stroke_width=2, opacity=1):
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{stroke}" '
        f'stroke-width="{stroke_width}" opacity="{opacity}" '
        f'stroke-linecap="round"/>\n'
    )


def rect(x, y, w, h, fill=None, stroke=None, stroke_width=0, opacity=1, rx=0):
    attrs = [f'fill="{fill}"' if fill else 'fill="none"']
    if stroke:
        attrs.append(f'stroke="{stroke}"')
        attrs.append(f'stroke-width="{stroke_width}"')
    attrs.append(f'opacity="{opacity}"')
    if rx:
        attrs.append(f'rx="{rx}"')
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" {" ".join(attrs)}/>\n'


def elevation_chart(x, y, w, h, line_color, area_color,
                    track_color="#FFFFFF", track_opacity=0.18):
    dot_cx = x + w * 0.75
    dot_cy = y + h * 0.45
    return (
        f'<g id="elevation_chart">\n'
        f'  <g id="area"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'fill="{area_color}" opacity="0.28"/></g>\n'
        f'  <g id="full_path"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{track_color}" stroke-width="2" opacity="{track_opacity}" fill="none"/></g>\n'
        f'  <g id="line"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{line_color}" stroke-width="4" fill="none"/></g>\n'
        f'  <g id="glow"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="14" ry="14" '
        f'fill="{line_color}" opacity="0.35"/></g>\n'
        f'  <g id="dot"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="7" ry="7" '
        f'fill="{line_color}" stroke="#000000" stroke-width="2"/></g>\n'
        f'</g>\n'
    )


def route_map(x, y, w, h, line_color, track_color="#FFFFFF", track_opacity=0.35):
    dot_cx = x + w * 0.5
    dot_cy = y + h * 0.5
    return (
        f'<g id="route_map">\n'
        f'  <g id="full_route"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{track_color}" stroke-width="3" opacity="{track_opacity}" fill="none"/></g>\n'
        f'  <g id="route"><rect x="{x}" y="{y}" width="{w}" height="{h}" '
        f'stroke="{line_color}" stroke-width="5" fill="none"/></g>\n'
        f'  <g id="glow"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="16" ry="16" '
        f'fill="{line_color}" opacity="0.35"/></g>\n'
        f'  <g id="dot"><ellipse cx="{dot_cx}" cy="{dot_cy}" rx="8" ry="8" '
        f'fill="{line_color}" stroke="#000000" stroke-width="2"/></g>\n'
        f'</g>\n'
    )


def scrim_defs(id_, top_opacity=0.0, bottom_opacity=0.75):
    return (
        f'  <linearGradient id="{id_}" x1="0" y1="0" x2="0" y2="1">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="{top_opacity}"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="{bottom_opacity}"/>\n'
        f'  </linearGradient>\n'
    )


# ============================================================
# A. HORIZON — Garmin-style horizontal footer. chart only.
# ============================================================
def gen_horizon(ratio, w, h):
    font = "Montserrat"
    accent = "#FFFFFF"
    sub = "#D6D6DE"
    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.052)
        value_size = int(h * 0.045)
        label_size = int(h * 0.019)
        chart_h = int(h * 0.055)
    elif ratio == "1x1":
        title_size = 68
        value_size = 72
        label_size = 26
        chart_h = 54
    else:
        title_size = 62
        value_size = 66
        label_size = 24
        chart_h = 50

    margin = int(w * 0.05)
    # Vertical layout in the footer: title, chart, metrics row
    title_y = h - int(value_size * 1.0) - label_size - chart_h - int(h * 0.09)
    chart_y = title_y + int(title_size * 0.5)
    metric_row_y = h - int(h * 0.055)
    scrim_h = int(h * 0.45)

    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n{scrim_defs("scrim", 0, 0.8)}</defs>\n'
        f'<rect x="0" y="{h - scrim_h}" width="{w}" height="{scrim_h}" fill="url(#scrim)"/>\n'
    )
    out += stroked_text("title_text", margin, title_y, title_size, font,
                        accent, "Morning Run", weight="800",
                        letter_spacing="-0.02em")
    out += elevation_chart(margin, chart_y, w - 2 * margin, chart_h,
                           line_color=accent, area_color=accent,
                           track_opacity=0.25)
    cols = 4
    col_w = (w - 2 * margin) / cols
    labels = ["DISTANCE", "TIME", "PACE", "HEART"]
    values = ["10.0", "1:00:00", "05:00", "145"]
    for i in range(cols):
        cx = margin + col_w * i + col_w / 2
        out += stroked_text(f"metric_{i+1}_value", cx, metric_row_y,
                            value_size, font, accent, values[i],
                            anchor="middle", weight="800",
                            letter_spacing="-0.02em")
        out += stroked_text(f"metric_{i+1}_label", cx,
                            metric_row_y + label_size + 18,
                            label_size, font, sub, labels[i],
                            anchor="middle", weight="700",
                            stroke_width=3, letter_spacing="0.2em")
        if i < cols - 1:
            div_x = margin + col_w * (i + 1)
            out += line(div_x, metric_row_y - value_size + 10, div_x,
                        metric_row_y + label_size + 22, accent, 2, 0.45)
    out += "</svg>\n"
    return out


# ============================================================
# B. EDITORIAL — Adidas-like serif title. Pure typography, no chart/route.
# ============================================================
def gen_editorial(ratio, w, h):
    serif = "Playfair Display"
    sans = "Inter"
    accent = "#F6F1E7"
    sub = "#D1C9B6"
    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.075)
        value_size = int(h * 0.048)
        label_size = int(h * 0.017)
        top_size = 28
    elif ratio == "1x1":
        title_size = 96
        value_size = 76
        label_size = 22
        top_size = 26
    else:
        title_size = 88
        value_size = 70
        label_size = 22
        top_size = 24

    margin = int(w * 0.06)
    title_y = h - int(h * 0.17)
    metric_y = h - int(h * 0.055)
    divider_y = metric_y - value_size - int(h * 0.04)

    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n{scrim_defs("edscr", 0, 0.65)}</defs>\n'
        f'<rect x="0" y="{int(h*0.35)}" width="{w}" height="{int(h*0.65)}" fill="url(#edscr)"/>\n'
    )
    out += stroked_text("brand", w - margin, int(h * 0.06) + top_size,
                        top_size, sans, accent, "RUNNING",
                        anchor="end", weight="800",
                        letter_spacing="0.35em", stroke_width=3)
    out += line(w - margin - 200, int(h * 0.06) + top_size + 12,
                w - margin, int(h * 0.06) + top_size + 12,
                accent, 2, 0.8)

    out += stroked_text("title_text", margin, title_y, title_size, serif,
                        accent, "Morning Run", weight="900",
                        letter_spacing="-0.01em")

    out += line(margin, divider_y, w - margin, divider_y, accent, 2, 0.45)

    cols = 3
    col_w = (w - 2 * margin) / cols
    labels = ["DISTANCE", "TIME", "PACE"]
    values = ["10.0", "1:00:00", "05:00"]
    for i in range(cols):
        cx = margin + col_w * i
        out += stroked_text(f"metric_{i+1}_value", cx, metric_y, value_size,
                            serif, accent, values[i], weight="900",
                            letter_spacing="-0.01em")
        out += stroked_text(f"metric_{i+1}_label", cx,
                            metric_y + label_size + 14,
                            label_size, sans, sub, labels[i],
                            weight="700", letter_spacing="0.35em",
                            stroke_width=3)
    out += "</svg>\n"
    return out


# ============================================================
# C. EXPEDITION — left stack + centered route. route only.
# ============================================================
def gen_expedition(ratio, w, h):
    display = "Bebas Neue"
    sans = "Inter"
    accent = "#FFFFFF"
    highlight = "#FF5A1F"
    sub = "#E0E0E0"
    if ratio == "9x16" or ratio == "4x5":
        title_size = int(h * 0.07)
        value_size = int(h * 0.06)
        label_size = int(h * 0.022)
        metric_gap = int(h * 0.09)
        route_w = int(w * 0.88)
        route_h = int(h * 0.28)
        route_x = (w - route_w) // 2
        route_y = int(h * 0.24)
        title_y = int(h * 0.12)
        metric_y_start = int(h * 0.62)
    elif ratio == "1x1":
        title_size = 90
        value_size = 92
        label_size = 30
        metric_gap = 140
        route_w = 560
        route_h = 420
        route_x = (w - route_w) // 2
        route_y = 150
        title_y = 120
        metric_y_start = 660
    else:  # 16x9
        title_size = 80
        value_size = 78
        label_size = 26
        metric_gap = 110
        route_w = 620
        route_h = 620
        route_x = (w - route_w) // 2
        route_y = 230
        title_y = 110
        metric_y_start = 360

    margin = int(w * 0.06)
    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n'
        f'  <linearGradient id="exsc" x1="0" y1="1" x2="0" y2="0">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0.7"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0"/>\n'
        f'  </linearGradient>\n'
        f'  <linearGradient id="exsc_top" x1="0" y1="0" x2="0" y2="1">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0.5"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0"/>\n'
        f'  </linearGradient>\n'
        f'</defs>\n'
        f'<rect x="0" y="0" width="{w}" height="{int(h*0.22)}" fill="url(#exsc_top)"/>\n'
        f'<rect x="0" y="{int(h*0.5)}" width="{w}" height="{int(h*0.5)}" fill="url(#exsc)"/>\n'
    )
    out += stroked_text("title_text", margin, title_y, title_size, display,
                        accent, "MILAN RUNNING", weight="400",
                        letter_spacing="0.03em")
    out += line(margin, title_y + 22, margin + 300, title_y + 22,
                highlight, 6)

    out += route_map(route_x, route_y, route_w, route_h, highlight,
                     track_color=accent, track_opacity=0.4)

    labels = ["DISTANCE", "TIME", "PACE"]
    values = ["10.0", "1:00:00", "05:00"]
    for i in range(3):
        y = metric_y_start + i * metric_gap
        out += line(margin, y - value_size + 8, margin + 30,
                    y - value_size - 24, highlight, 5)
        out += stroked_text(f"metric_{i+1}_label", margin + 52,
                            y - value_size + 6, label_size, sans, sub,
                            labels[i], weight="700",
                            letter_spacing="0.3em", stroke_width=3)
        out += stroked_text(f"metric_{i+1}_value", margin + 52, y,
                            value_size, display, accent, values[i],
                            weight="400", letter_spacing="0.03em")
    out += "</svg>\n"
    return out


# ============================================================
# D. MINIMAL — compact cream footer band. No chart, no route.
# ============================================================
def gen_minimal(ratio, w, h):
    display = "Oswald"
    sans = "Inter"
    ink = "#0A0A0A"
    muted = "#5A5A5A"
    band_col = "#F6F5F0"
    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.018)
        value_size = int(h * 0.05)
        label_size = int(h * 0.017)
        band_h = int(h * 0.18)
    elif ratio == "1x1":
        title_size = 24
        value_size = 72
        label_size = 22
        band_h = 210
    else:
        title_size = 22
        value_size = 64
        label_size = 20
        band_h = 190

    margin = int(w * 0.06)
    band_top = h - band_h
    title_y = band_top + int(band_h * 0.27)
    row_y = band_top + int(band_h * 0.82)

    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
    )
    # Accent ink strip above band for premium feel
    out += rect(0, band_top - 4, w, 4, fill="#0A0A0A", opacity=0.9)
    out += rect(0, band_top, w, band_h, fill=band_col)
    # Title (on band, so no stroke needed)
    out += (
        f'<text id="title_text" x="{margin}" y="{title_y}" fill="{ink}" '
        f'font-family="{sans}" font-size="{title_size}" font-weight="800" '
        f'letter-spacing="0.35em">MORNING RUN</text>\n'
    )
    out += line(margin, title_y + 16, w - margin, title_y + 16,
                ink, 1, 0.18)

    cols = 3
    col_w = (w - 2 * margin) / cols
    labels = ["DIST", "TIME", "PACE"]
    values = ["10.0", "1:00:00", "05:00"]
    for i in range(cols):
        cx = margin + col_w * i
        out += (
            f'<text id="metric_{i+1}_value" x="{cx}" y="{row_y}" '
            f'fill="{ink}" font-family="{display}" font-size="{value_size}" '
            f'font-weight="600">{values[i]}</text>\n'
        )
        out += (
            f'<text id="metric_{i+1}_label" x="{cx}" y="{row_y - value_size - 10}" '
            f'fill="{muted}" font-family="{sans}" font-size="{label_size}" '
            f'font-weight="700" letter-spacing="0.3em">{labels[i]}</text>\n'
        )
    out += "</svg>\n"
    return out


# ============================================================
# E. DASHBOARD — compact 2x2 grid + chart. chart only.
# ============================================================
def gen_dashboard(ratio, w, h):
    font = "Inter"
    accent = "#FFFFFF"
    sub = "#B5BCC4"
    tint = "#1FD1A7"
    card = "#12141A"
    card_stroke = "#2A2F3A"

    if ratio in ("9x16", "4x5"):
        title_size = int(h * 0.028)
        value_size = int(h * 0.04)
        label_size = int(h * 0.016)
        card_h = int(h * 0.08)
        chart_h = int(h * 0.07)
        bottom_margin = int(h * 0.04)
    elif ratio == "1x1":
        title_size = 32
        value_size = 58
        label_size = 20
        card_h = 120
        chart_h = 100
        bottom_margin = 50
    else:
        title_size = 28
        value_size = 52
        label_size = 18
        card_h = 105
        chart_h = 90
        bottom_margin = 45

    margin = int(w * 0.045)
    gap = int(w * 0.02)
    col_w = (w - 2 * margin - gap) / 2
    chart_y = h - bottom_margin - chart_h
    grid_bottom = chart_y - int(h * 0.025)
    grid_top = grid_bottom - card_h * 2 - gap
    title_y = grid_top - 24

    out = (
        f'<svg width="{w}" height="{h}" viewBox="0 0 {w} {h}" fill="none" '
        f'xmlns="http://www.w3.org/2000/svg">\n'
        f'<defs>\n'
        f'  <linearGradient id="dbsc" x1="0" y1="1" x2="0" y2="0">\n'
        f'    <stop offset="0" stop-color="#000000" stop-opacity="0.85"/>\n'
        f'    <stop offset="1" stop-color="#000000" stop-opacity="0"/>\n'
        f'  </linearGradient>\n'
        f'</defs>\n'
        f'<rect x="0" y="{int(h*0.5)}" width="{w}" height="{int(h*0.5)}" fill="url(#dbsc)"/>\n'
    )
    out += stroked_text("title_text", margin, title_y, title_size, font,
                        accent, "Morning Run", weight="800",
                        letter_spacing="-0.01em")
    out += rect(margin, title_y + 12, 48, 4, fill=tint)

    labels = ["DISTANCE", "TIME", "PACE", "HEART"]
    values = ["10.0", "1:00:00", "05:00", "145"]
    for i in range(4):
        r = i // 2
        c = i % 2
        cx = margin + c * (col_w + gap)
        cy = grid_top + r * (card_h + gap)
        out += rect(cx, cy, col_w, card_h, fill=card, stroke=card_stroke,
                    stroke_width=1, rx=16, opacity=0.92)
        out += rect(cx + 18, cy + 18, 4, card_h - 36, fill=tint, rx=2)
        out += (
            f'<text id="metric_{i+1}_label" x="{cx + 40}" y="{cy + 36}" '
            f'fill="{sub}" font-family="{font}" font-size="{label_size}" '
            f'font-weight="700" letter-spacing="0.25em">{labels[i]}</text>\n'
        )
        out += (
            f'<text id="metric_{i+1}_value" x="{cx + 40}" y="{cy + card_h - 22}" '
            f'fill="{accent}" font-family="{font}" font-size="{value_size}" '
            f'font-weight="800" letter-spacing="-0.02em">{values[i]}</text>\n'
        )
    out += elevation_chart(margin, chart_y, w - 2 * margin, chart_h,
                           line_color=tint, area_color=tint,
                           track_color=accent, track_opacity=0.18)
    out += "</svg>\n"
    return out


TEMPLATES = {
    "horizon":    (gen_horizon,    "Montserrat-Bold.ttf"),
    "editorial":  (gen_editorial,  "PlayfairDisplay-Bold.ttf"),
    "expedition": (gen_expedition, "BebasNeue-Regular.ttf"),
    "minimal":    (gen_minimal,    "Oswald-Bold.ttf"),
    "dashboard":  (gen_dashboard,  "Inter-Bold.ttf"),
}


def main():
    for name, (gen, font_file) in TEMPLATES.items():
        tpl_dir = os.path.join(TEMPLATES_DIR, name)
        if os.path.isdir(tpl_dir):
            shutil.rmtree(tpl_dir)
        os.makedirs(tpl_dir)

        for ratio, (w, h) in RATIOS.items():
            svg = gen(ratio, w, h)
            path = os.path.join(tpl_dir, f"{name}_{ratio}.svg")
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(svg)

        # Copy primary font + Inter as secondary
        for ff in [font_file, "Inter-Bold.ttf"]:
            src = os.path.join(FONTS_SRC, ff)
            if os.path.isfile(src):
                shutil.copy(src, os.path.join(tpl_dir, ff))

        subprocess.check_call(
            ["python3", os.path.join(REPO, "tools/finalize_template.py"), tpl_dir]
        )
        print()


if __name__ == "__main__":
    main()
