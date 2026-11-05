"""Generate the mc-art plans for the spear icons (part icon, spear layers, casts).

Every plan is written as JSON; the actual pixels come from `mc-art render`, so the
engine does the painting and validation. This script only decides *shape*: masks are
computed from the geometry below instead of by hand, so the part icon, the tool icon
and the three casts all describe the same cone.

Run with the project venv python; render each plan afterwards:

    python art/spear_masks.py          # writes art/plans/gen_*.json + prints previews
"""

import json
import math
import pathlib

W = H = 16
OUT = pathlib.Path(__file__).resolve().parent / "plans"

CONE_TIP = (7.5, 0.8)
CONE_A = (2.4, 13.6)
CONE_B = (12.6, 13.6)

BUTT = (1.8, 14.2)
TIP = (14.2, 1.4)
AXIS = (TIP[0] - BUTT[0], TIP[1] - BUTT[1])
LEN = math.hypot(*AXIS)
U = (AXIS[0] / LEN, AXIS[1] / LEN)
N = (-U[1], U[0])
JUNCTION = (BUTT[0] + U[0] * LEN * 0.55, BUTT[1] + U[1] * LEN * 0.55)


def along(base, direction, distance):
    return (base[0] + direction[0] * distance, base[1] + direction[1] * distance)


def in_triangle(px, py, tri):
    def side(a, b):
        return (a[0] - b[0]) * (py - b[1]) - (a[1] - b[1]) * (px - b[0])

    d1, d2, d3 = side(tri[0], tri[1]), side(tri[1], tri[2]), side(tri[2], tri[0])
    neg = d1 < 0 or d2 < 0 or d3 < 0
    pos = d1 > 0 or d2 > 0 or d3 > 0
    return not (neg and pos)


def in_rect(px, py, rect):
    x0, y0, x1, y1 = rect
    return x0 <= px <= x1 and y0 <= py <= y1


def dist_to_segment(px, py, a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    denom = dx * dx + dy * dy
    t = 0.0 if denom == 0 else max(0.0, min(1.0, ((px - a[0]) * dx + (py - a[1]) * dy) / denom))
    return math.hypot(px - (a[0] + t * dx), py - (a[1] + t * dy))


def mask(predicate, size=W):
    """Rasterise a 16x16-space predicate at `size` resolution (size=64 keeps the same shape, finer edges)."""
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            px = (x + 0.5) * W / size
            py = (y + 0.5) * H / size
            row.append("X" if predicate(px, py) else ".")
        rows.append("".join(row))
    return rows


def upright_cone(px, py):
    return in_triangle(px, py, (CONE_TIP, CONE_A, CONE_B))


def ring(predicate):
    def edge(px, py):
        if not predicate(px, py):
            return False
        return not (predicate(px + 1, py) and predicate(px - 1, py)
                    and predicate(px, py + 1) and predicate(px, py - 1))

    return mask(edge)


CONE_BASE = along(JUNCTION, U, 0.3)
HEAD_TRI = (TIP, along(CONE_BASE, N, 3.2), along(CONE_BASE, N, -3.2))


def head_layer(px, py):
    return in_triangle(px, py, HEAD_TRI)


def shaft_layer(px, py):
    return dist_to_segment(px, py, BUTT, CONE_BASE) <= 1.15


def binding_layer(px, py):
    a = along(JUNCTION, U, -1.1)
    b = along(JUNCTION, U, 1.1)
    return dist_to_segment(px, py, a, b) <= 1.6


def cast_mask(hole):
    return mask(lambda px, py: not hole(px, py))


STEEL = {
    "steel_1": "#8C8C8C",
    "steel_2": "#9E9E9E",
    "steel_3": "#B2B2B2",
    "steel_4": "#C6C6C6",
    "steel_5": "#DADADA",
}
GOLD = ["#FCE24E", "#FDF55F"]
SAND = ["#D5C496", "#DACFA3", "#E3DBB0", "#E7E4BB", "#EDEBCB"]
RED_SAND = ["#B2601F", "#BF6721", "#CB6E24", "#D2752B", "#D97B30"]


def sprite_plan(name, query, semantic, rows, palette, colors, outline_width=0,
                outline_color="#1E1E1E", shape_edit_mode="new_silhouette", waiver=None, size=W):
    return {
        "request": {
            "query": query,
            "form": "item",
            "name": name,
            "namespace": "futuretinker",
            "width": size,
            "height": size,
            "novelty": 0.3,
            "target_path": None,
            "shape_policy": "planned",
            "seed": 71,
            "pack_format": 15,
        },
        "descriptor": {
            "target": semantic,
            "semantic": semantic,
            "visual_identity": [
                "16x16 transparent-background item sprite",
                "neutral grey ramp so the game can tint it with the material colour",
            ],
            "parts": [{
                "id": "shape",
                "meaning": semantic,
                "required": True,
                "paint_only": False,
                "layer": 0,
                "style_role": "silhouette support",
                "recognition_terms": ["spear", "cone"],
                "contour_intent": "elongated",
            }],
            "negative_identities": ["sword blade", "chestplate"],
            "orientation": "as drawn",
            "reference_strategy": "no reference",
            "shape_edit_mode": shape_edit_mode,
            "reference_waiver": waiver or (
                "1.20.1 has no spear; the cone silhouette is authored from the vanilla 1.21.11 "
                "spear as a 2D projection, and only the Tinkers material grammar is borrowed."),
            "reference_part_map": None,
            "target_part_map": None,
            "class": "unknown",
            "layer": "unknown",
        },
        "geometry": {
            "width": size,
            "height": size,
            "parts": [{
                "id": "shape",
                "meaning": semantic,
                "required": True,
                "paint_only": False,
                "layer": 0,
                "style_role": "silhouette support",
                "recognition_terms": ["spear", "cone"],
                "contour_intent": "elongated",
            }],
            "primitives": [{
                "id": "mask",
                "part_id": "shape",
                "primitive": "custom_mask",
                "params": {"offset": [0, 0], "marker": "X", "rows": rows},
                "layer": 0,
            }],
            "connections": [],
            "constraints": [],
            "uv_regions": [],
            "background_transparent": True,
        },
        "appearance": {
            "palette": palette,
            "parts": {
                "shape": {
                    "colors": colors,
                    "material": "metal",
                    "shade_axis": "top_left_to_bottom_right",
                    "noise": 0.0,
                    "highlight_ratio": 0.0,
                    "marks": [],
                    "shade_mode": "gradient",
                }
            },
            "outline_color": outline_color,
            "outline_width": outline_width,
            "transparent_background": True,
            "reference_sampling": "none",
            "region_rules": [],
            "part_reference_sampling": {},
            "part_reference_sources": {},
            "part_reference_composite": {},
            "motif_policy": "model_authored",
            "accent_colors": [],
            "accent_budget": 0,
            "accent_min_cluster": 1,
            "accent_cleanup": False,
            "accent_edge_max": 90,
            "band_maximum_isolated": 0.1,
            "band_maximum_step": 30,
        },
        "references": [],
    }


def main():
    plans = {}

    plans["gen_spear_part"] = sprite_plan(
        "spear_part", "spear head part: solid cone",
        "Solid spear head (a 2D cone projection), tinted by the part material.",
        mask(upright_cone), STEEL, list(STEEL.keys()))

    plans["gen_spear_part_pattern"] = sprite_plan(
        "spear_part_pattern", "spear head pattern button: flat dark cone outline",
        "Part builder pattern button: the same cone outline, flat dark, no shading.",
        ring(upright_cone), {"dark": "#323232"}, ["dark"], outline_width=0,
        outline_color="#323232", shape_edit_mode="appearance_only")

    tool_palette = {"t1": "#B4B4B4", "t2": "#C6C6C6", "t3": "#D8D8D8"}
    tool_colors = ["t1", "t2", "t3"]

    plans["gen_spear_tool_head"] = sprite_plan(
        "spear_tool_head", "spear tool icon: cone head at the top right",
        "The cone head of the spear tool icon, tinted by the head material.",
        mask(head_layer, 64), tool_palette, tool_colors, size=64)

    plans["gen_spear_tool_shaft"] = sprite_plan(
        "spear_tool_shaft", "spear tool icon: diagonal shaft",
        "The diagonal shaft of the spear tool icon, tinted by the handle material.",
        mask(shaft_layer, 64), tool_palette, tool_colors, size=64)

    plans["gen_spear_tool_binding"] = sprite_plan(
        "spear_tool_binding", "spear tool icon: binding band at the head joint",
        "The binding band of the spear tool icon, tinted by the binding material.",
        mask(binding_layer, 64), tool_palette, tool_colors, size=64)

    for suffix, palette in (("gold", GOLD), ("sand", SAND), ("red_sand", RED_SAND)):
        named = {f"c{i}": value for i, value in enumerate(palette)}
        cast = sprite_plan(
            f"spear_head_cast_{suffix}", f"spear head {suffix} cast: a block with a cone shaped cavity",
            f"The {suffix} cast for the spear head: a full block with the cone shaped cavity punched out.",
            cast_mask(upright_cone), named, list(named.keys()), shape_edit_mode="appearance_only")
        plans[f"gen_spear_cast_{suffix}"] = cast

    OUT.mkdir(parents=True, exist_ok=True)
    for name, plan in plans.items():
        (OUT / f"{name}.plan.json").write_text(
            json.dumps(plan, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    for name in ("gen_spear_part", "gen_spear_tool_head", "gen_spear_tool_shaft", "gen_spear_tool_binding"):
        rows = plans[name]["geometry"]["primitives"][0]["params"]["rows"]
        print(f"--- {name}")
        print("\n".join(rows))
    print("wrote", len(plans), "plans to", OUT)


if __name__ == "__main__":
    main()
