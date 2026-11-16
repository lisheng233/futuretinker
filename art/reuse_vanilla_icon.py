"""Reuse the vanilla 1.21.11 spear icon (art/refs_vanilla_spear.png) for our sprites.

The user asked for exactly this: take that icon, edit it and reuse it. It is a 16x16
sprite on a black background: grey metal cone at the tip, wooden shaft, copper butt cap.

Splitting goes by **position along the spear axis** (not by hue), because the three
pieces are contiguous segments of one long object:

    butt cap  t in [0.00, 0.16)
    shaft     t in [0.16, 0.48)
    cone      t in [0.48, 1.00]

Each piece is desaturated to grey but keeps its luminance, because the game multiplies
the sprite by the three Tinker's material colours (head / handle / binding). The part
icon and the part-builder button (hollow, as the GUI wants) are derived from the same
pixels. The casts are drawn separately by `cast_art.py` (the cavity is much larger than
the part icon, like TConstruct's own broad-blade cast).

Deterministic: rerun and you get the same files.
"""

import pathlib

from PIL import Image

ART = pathlib.Path(__file__).resolve().parent
SRC = ART / "refs_vanilla_spear.png"
RES = ART.parent / "src/main/resources/assets/futuretinker/textures"

BUTT_END = 0.16
SHAFT_END = 0.48


def luma(rgb):
    r, g, b = rgb
    return int(round(0.299 * r + 0.587 * g + 0.114 * b))


def load_pixels():
    source = Image.open(SRC).convert("RGBA").resize((16, 16), Image.NEAREST)
    pixels = {}
    for y in range(16):
        for x in range(16):
            rgba = source.getpixel((x, y))
            # the icon is on a black background; anything else (including the dark
            # outline) belongs to the spear
            if rgba[:3] == (0, 0, 0):
                continue
            pixels[(x, y)] = rgba
    return pixels


def axis(pixels):
    xs = [x for x, _ in pixels]
    ys = [y for _, y in pixels]
    # the spear runs from the lower-left (butt) to the upper-right (tip)
    butt = (min(xs), max(ys))
    tip = (max(xs), min(ys))
    direction = (tip[0] - butt[0], tip[1] - butt[1])
    return butt, direction, direction[0] ** 2 + direction[1] ** 2


def t_of(pos, butt, direction, length_sq):
    return ((pos[0] - butt[0]) * direction[0] + (pos[1] - butt[1]) * direction[1]) / length_sq


def make_layer(pixels, group):
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for (x, y), rgba in pixels.items():
        if group(x, y):
            grey = luma(rgba[:3])
            image.putpixel((x, y), (grey, grey, grey, 255))
    return image


def outline_of(image):
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    solid = [[image.getpixel((x, y))[3] > 0 for x in range(16)] for y in range(16)]

    def inside(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and solid[y][x]

    for y in range(16):
        for x in range(16):
            if inside(x, y) and not (inside(x - 1, y) and inside(x + 1, y)
                                     and inside(x, y - 1) and inside(x, y + 1)):
                out.putpixel((x, y), (60, 60, 60, 255))
    return out


def main():
    pixels = load_pixels()
    butt, direction, length_sq = axis(pixels)
    t = {pos: t_of(pos, butt, direction, length_sq) for pos in pixels}

    head = make_layer(pixels, lambda x, y: t[(x, y)] >= SHAFT_END)
    shaft = make_layer(pixels, lambda x, y: BUTT_END <= t[(x, y)] < SHAFT_END)
    cap = make_layer(pixels, lambda x, y: t[(x, y)] < BUTT_END)

    (RES / "item").mkdir(parents=True, exist_ok=True)
    head.save(RES / "item/spear_head_layer.png")
    shaft.save(RES / "item/spear_shaft_layer.png")
    cap.save(RES / "item/spear_binding_layer.png")

    head.save(RES / "item/spear_head.png")
    outline_of(head).save(RES / "gui/tinker_pattern/spear_head.png")

    print("wrote 3 layers + part icon + pattern")


if __name__ == "__main__":
    main()
