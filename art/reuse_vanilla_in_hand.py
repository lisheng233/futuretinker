"""Reuse the vanilla 1.21.11 spear **in-hand** texture for our hand sprite.

The inventory icon (16x16, refs_vanilla_spear.png) and the in-hand texture (32x32,
refs_vanilla_spear_in_hand.png) are different drawings: the in-hand one is longer and
vanilla's first/third person display transforms are tuned for it. So the hand model
gets its own three grayscale layers, split along the spear axis:

    head      t in [0.00, 0.20)   (top-left end in the vanilla texture)
    shaft     t in [0.20, 0.84)
    butt cap  t in [0.84, 1.00)   (bottom-right end)

The game multiplies each layer by the material colour (head / handle / binding),
exactly like the icon layers. Keep the vanilla orientation: with the vanilla
`spear_in_hand` display transforms, the top-left end is the one near the crosshair
and the bottom-right end is off-screen; putting the head at the top-left is the only
way the spearhead is visible in first person.

Deterministic: rerun and you get the same files.
"""

import pathlib

from PIL import Image

ART = pathlib.Path(__file__).resolve().parent
SRC = ART / "refs_vanilla_spear_in_hand.png"
RES = ART.parent / "src/main/resources/assets/futuretinker/textures/item"

HEAD_END = 0.20
CAP_START = 0.84


def luma(rgb):
    r, g, b = rgb
    return int(round(0.299 * r + 0.587 * g + 0.114 * b))


def main():
    source = Image.open(SRC).convert("RGBA")
    width, height = source.size
    pixels = {}
    for y in range(height):
        for x in range(width):
            rgba = source.getpixel((x, y))
            if rgba[3] > 16:
                pixels[(x, y)] = rgba

    xs = [x for x, _ in pixels]
    ys = [y for _, y in pixels]
    # vanilla in-hand texture: spearhead at the top-left, butt cap at the bottom-right
    head_end = (min(xs), min(ys))
    cap_end = (max(xs), max(ys))
    dx = cap_end[0] - head_end[0]
    dy = cap_end[1] - head_end[1]
    length_sq = dx * dx + dy * dy

    def t_of(pos):
        return ((pos[0] - head_end[0]) * dx + (pos[1] - head_end[1]) * dy) / length_sq

    def make_layer(keep):
        image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        for pos, rgba in pixels.items():
            if keep(t_of(pos)):
                grey = luma(rgba[:3])
                image.putpixel(pos, (grey, grey, grey, 255))
        return image

    head = make_layer(lambda t: t <= HEAD_END)
    shaft = make_layer(lambda t: HEAD_END < t < CAP_START)
    cap = make_layer(lambda t: t >= CAP_START)

    head.save(RES / "spear_head_hand_layer.png")
    shaft.save(RES / "spear_shaft_hand_layer.png")
    cap.save(RES / "spear_binding_hand_layer.png")
    print(f"wrote hand layers ({width}x{height}: "
          f"head {head.getbbox()}, shaft {shaft.getbbox()}, cap {cap.getbbox()})")


if __name__ == "__main__":
    main()
