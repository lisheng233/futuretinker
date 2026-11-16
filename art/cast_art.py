"""Draw the gold / sand / red-sand casts for the spear head and wolf-tooth head.

TConstruct's part casts are 16x16: an opaque noisy metal/sand background with the
part silhouette cut out (fully transparent).  The cavity is derived from the actual
part icon, scaled up to fill the cast and dilated 1px so thin teeth/spikes stay
connected: the spear cavity follows `item/spear_head.png`, the mace cavity follows
`mace_wolf_tooth.py`.  No hand-drawn second silhouette that can drift from the part.

Deterministic: rerun and you get the same files.
"""

import pathlib
import random
import zlib

from PIL import Image

from mace_wolf_tooth import cast_mask

ART = pathlib.Path(__file__).resolve().parent
RES = ART.parent / "src/main/resources/assets/futuretinker/textures"

# Exact palettes sampled from TConstruct's cast textures.
GOLD = ["#EDAD17", "#F4C730", "#FCE24E", "#FDF55F", "#FEFFBD"]
SAND = ["#D1BA8A", "#D5C496", "#DACFA3", "#E3DBB0", "#E7E4BB", "#EDEBCB"]
RED_SAND = ["#AC5712", "#B2601F", "#BF6721", "#CB6E24", "#D2752B", "#D97B30"]

GOLD = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in GOLD]
SAND = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in SAND]
RED_SAND = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in RED_SAND]

def dilate(mask, size=16):
    out = [[False] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            if not mask[y][x]:
                continue
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < size and 0 <= ny < size:
                        out[ny][nx] = True
    return out


def part_silhouette(part, fill=14, expand=1):
    """Read a 16x16 part icon and scale its alpha to fill most of the cast."""
    image = Image.open(RES / "item" / f"{part}.png").convert("RGBA")
    bbox = image.getbbox()
    if bbox is None:
        raise ValueError(f"{part} has no pixels")
    image = image.crop(bbox)
    w, h = image.size
    scale = min(fill / w, fill / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    image = image.resize((nw, nh), Image.Resampling.NEAREST)
    canvas = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((16 - nw) // 2, (16 - nh) // 2))
    solid = [[canvas.getpixel((x, y))[3] > 0 for x in range(16)] for y in range(16)]
    return dilate(solid) if expand else solid


def cast_texture(mask, palette, seed):
    rng = random.Random(seed)
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            if mask[y][x]:
                continue
            colour = palette[rng.randrange(len(palette))]
            image.putpixel((x, y), (*colour, 255))
    return image


def main():
    variants = (
        (part_silhouette("spear_head", fill=12), "spear_head"),
        (cast_mask(), "mace_head"),
    )
    palettes = (
        (GOLD, "item/cast"),
        (SAND, "item/sand_cast"),
        (RED_SAND, "item/red_sand_cast"),
    )
    for mask, part in variants:
        for palette, folder in palettes:
            path = RES / folder / f"{part}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            seed = zlib.crc32(f"{part}/{folder}".encode())
            cast_texture(mask, palette, seed=seed).save(path)
    print("wrote 2 parts x 3 casts")


if __name__ == "__main__":
    main()
