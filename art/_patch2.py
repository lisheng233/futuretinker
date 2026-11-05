import pathlib

p = pathlib.Path("art/spear_masks.py")
s = p.read_text(encoding="utf-8")

old_mask = '''def mask(predicate):
    rows = []
    for y in range(H):
        rows.append("".join("X" if predicate(x + 0.5, y + 0.5) else "." for x in range(W)))
    return rows'''
new_mask = '''def mask(predicate, size=W):
    """Rasterise a 16x16-space predicate at `size` resolution (size=64 keeps the same shape, finer edges)."""
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            px = (x + 0.5) * W / size
            py = (y + 0.5) * H / size
            row.append("X" if predicate(px, py) else ".")
        rows.append("".join(row))
    return rows'''
assert old_mask in s
s = s.replace(old_mask, new_mask)

old_sig = '''def sprite_plan(name, query, semantic, rows, palette, colors, outline_width=0,
                outline_color="#1E1E1E", shape_edit_mode="new_silhouette", waiver=None):'''
new_sig = '''def sprite_plan(name, query, semantic, rows, palette, colors, outline_width=0,
                outline_color="#1E1E1E", shape_edit_mode="new_silhouette", waiver=None, size=W):'''
assert old_sig in s
s = s.replace(old_sig, new_sig)

s = s.replace('''            "width": W,
            "height": H,''', '''            "width": size,
            "height": size,''')
s = s.replace('''            "width": W,
            "height": H,
            "parts": [''', '''            "width": size,
            "height": size,
            "parts": [''')

# 部件图标改成实心锥体；pattern 按钮仍然只画轮廓
old_part = '''    plans["gen_spear_part"] = sprite_plan(
        "spear_part", "spear head part: cone outline, hollow middle",
        "Outline of a spear head (a 2D cone projection) with a hollow middle, tinted by the part material.",
        ring(upright_cone), STEEL, list(STEEL.keys()))'''
new_part = '''    plans["gen_spear_part"] = sprite_plan(
        "spear_part", "spear head part: solid cone",
        "Solid spear head (a 2D cone projection), tinted by the part material.",
        mask(upright_cone), STEEL, list(STEEL.keys()))'''
assert old_part in s
s = s.replace(old_part, new_part)

s = s.replace('''        ring(upright_cone), {"dark": "#323232"}, ["dark"], outline_width=0,
        outline_color="#323232", shape_edit_mode="appearance_only")''',
              '''        ring(upright_cone), {"dark": "#323232"}, ["dark"], outline_width=0,
        outline_color="#323232", shape_edit_mode="appearance_only")''')

# 矛的三层贴图改成 64x64
for layer in ("head", "shaft", "binding"):
    s = s.replace(f'''        mask({layer}_layer), tool_palette, tool_colors)''',
                  f'''        mask({layer}_layer, 64), tool_palette, tool_colors, size=64)''')

p.write_text(s, encoding="utf-8")
print("patched")
