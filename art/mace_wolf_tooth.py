"""Draw the Chinese wolf-tooth club (狼牙棒) head.

The silhouette was traced from a real 狼牙棒 reference: a heavy diagonal oval/barrel
head with rows of conical iron teeth, a collar/socket at the base and a pointed tip.
The tool icon is 32x32 (the head needs the extra resolution); the part icon, pattern
and casts stay 16x16, as Tinkers expects.

Two grayscale layers are written for the tool (head / handle) so Tinkers can tint
them with two materials. The part icon and pattern are derived from the 16x16 head
silhouette; the cast cavity is that silhouette at native size (see cast_art.py).

Deterministic: rerun and you get the same files.
"""

import pathlib
import math

from PIL import Image

ART = pathlib.Path(__file__).resolve().parent
RES = ART.parent / "src/main/resources/assets/futuretinker/textures"

# The head axis; the collar and the handle are both built from it.
HEAD_BASE = (11.0, 19.0)
HEAD_TIP = (29.0, 2.0)

# 32x32 head silhouette, collar at the lower-left, pointed tip at the upper-right.
TOOL_MASK = """
................................
................................
................................
................................
................................
................................
......................#.........
..................##.##.........
................#.#######.......
................###########.....
................##########......
.............############.......
..............###########.......
...........################.....
............############........
.........################.......
..........############..........
.......################.........
........###########.............
......##############............
.....############...............
......###########...............
......#########.................
......#########.................
.....#######....................
......#...##....................
................................
................................
................................
................................
................................
................................
""".strip().splitlines()

# 16x16 head silhouette for the part icon / pattern / cast.
PART_MASK = """
................
................
.........#..#...
........######..
........#######.
......########..
.....##########.
....##########..
..##########....
..########......
.########.......
.########.......
.######.........
.#####..........
................
................
""".strip().splitlines()


def shade(mask, size, base, tip, half, bands, tip_highlights=(), spike_highlights=True):
    """Shade a boolean silhouette by distance from its long axis."""
    ax, ay = base
    bx, by = tip
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    px, py = -uy, ux
    filled = {(x, y) for y in range(size) for x in range(size) if mask[y][x] == '#'}
    pix = {}
    for (x, y) in filled:
        vx, vy = x - ax, y - ay
        r = (vx * px + vy * py) / half
        if r < -0.65:
            v = bands[0]
        elif r < -0.15:
            v = bands[1]
        elif r < 0.25:
            v = bands[2]
        elif r < 0.70:
            v = bands[3]
        else:
            v = bands[4]
        pix[(x, y)] = v

    # 1px dark outline around the union
    for (x, y) in list(pix):
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if not (0 <= nx < size and 0 <= ny < size) or (nx, ny) not in filled:
                pix[(x, y)] = 40
                break

    if spike_highlights:
        # the narrow tips of the silhouette are the teeth; keep them bright
        for (x, y) in list(pix):
            n = sum(1 for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                    if 0 <= nx < size and 0 <= ny < size and (nx, ny) in filled)
            if n <= 2 and pix.get((x, y)) == 40:
                pix[(x, y)] = 205

    for (x, y), v in tip_highlights:
        if (x, y) in pix:
            pix[(x, y)] = v
    return pix


def shift(mask, size, dx, dy):
    rows = [['.'] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            if mask[y][x] == '#':
                nx, ny = x + dx, y + dy
                if 0 <= nx < size and 0 <= ny < size:
                    rows[ny][nx] = '#'
    return [''.join(row) for row in rows]


def to_image(pix, size):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    for (x, y), v in pix.items():
        v = max(0, min(255, int(v)))
        im.putpixel((x, y), (v, v, v, 255))
    return im


def axis():
    ax, ay = HEAD_BASE
    bx, by = HEAD_TIP
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    return ax, ay, dx / length, dy / length


def draw_tool_head():
    mask = shift(TOOL_MASK, 32, 4, -5)
    pix = shade(mask, 32, base=HEAD_BASE, tip=HEAD_TIP, half=7.0,
                bands=(195, 165, 135, 98, 62),
                tip_highlights=(((30, 2), 235), ((29, 1), 220), ((28, 3), 220)))
    # socket collar: a band perpendicular to the head axis at its base, so the
    # handle (also on that axis) has a real mount instead of a horizontal shelf
    ax, ay, ux, uy = axis()
    for (x, y) in list(pix):
        along = (x - ax) * ux + (y - ay) * uy
        if -0.6 <= along <= 0.6:
            pix[(x, y)] = 85
        elif 0.6 < along <= 2.0:
            pix[(x, y)] = 175
    return to_image(pix, 32)


def draw_handle():
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    ax, ay, ux, uy = axis()
    # One row at a time on the head axis: a fixed 5px-wide strip.  A distance
    # field let the width wobble by a pixel and stepped twice on some rows,
    # which read as a kink halfway down; this keeps every row moving one pixel.
    bands = (45, 92, 150, 118, 45)
    for y in range(16, 28):
        left = round(ax + (y - ay) * (ux / uy)) - 2
        for i, v in enumerate(bands):
            x = left + i
            if 0 <= x < 32:
                im.putpixel((x, y), (v, v, v, 255))
    # rounded pommel, kept clear of the canvas edge
    for y in range(24, 32):
        for x in range(0, 6):
            distance = math.hypot(x - 2.1, y - 28.2)
            if distance <= 2.1:
                v = 45 if distance > 1.55 else 98
                im.putpixel((x, y), (v, v, v, 255))
    return im


def draw_part():
    pix = shade(PART_MASK, 16, base=(3, 12), tip=(13, 3), half=3.6,
                bands=(185, 155, 125, 92, 60),
                tip_highlights=(((14, 3), 230), ((13, 2), 215)),
                spike_highlights=False)
    return to_image(pix, 16)


def pattern_from(image):
    pattern = Image.new('RGBA', image.size, (0, 0, 0, 0))
    size = image.width
    solid = [[image.getpixel((x, y))[3] > 0 for x in range(size)] for y in range(size)]

    def inside(x, y):
        return 0 <= x < size and 0 <= y < size and solid[y][x]

    for y in range(size):
        for x in range(size):
            if inside(x, y) and not (inside(x - 1, y) and inside(x + 1, y)
                                     and inside(x, y - 1) and inside(x, y + 1)):
                pattern.putpixel((x, y), (60, 60, 60, 255))
    return pattern


def main():
    tool_head = draw_tool_head()
    handle = draw_handle()
    part = draw_part()

    (RES / "item").mkdir(parents=True, exist_ok=True)
    tool_head.save(RES / "item/mace_head_layer.png")
    handle.save(RES / "item/mace_handle_layer.png")
    part.save(RES / "item/mace_head.png")

    (RES / "gui/tinker_pattern").mkdir(parents=True, exist_ok=True)
    pattern_from(part).save(RES / "gui/tinker_pattern/mace_head.png")

    # preview for humans (not part of the resource pack)
    preview = Image.new('RGBA', (32 + 16 + 8, 32), (40, 44, 52, 255))
    tool = Image.alpha_composite(handle, tool_head)
    preview.alpha_composite(tool, (0, 0))
    preview.alpha_composite(part, (40, 8))
    (ART / "out").mkdir(parents=True, exist_ok=True)
    preview.resize((preview.width * 8, preview.height * 8), Image.NEAREST).save(
        ART / "out/mace_preview.png")
    print("wrote tool/part/pattern")


if __name__ == "__main__":
    main()