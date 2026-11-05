"""Compose a preview of the spear sprites (not a delivery, just a look-check aid)."""

import pathlib

from PIL import Image

ART = pathlib.Path("art/out")


def load(name):
    return Image.open(ART / name / "sprite.png").convert("RGBA")


def tint(image, colour):
    out = image.copy()
    pixels = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = pixels[x, y]
            if a:
                pixels[x, y] = (r * colour[0] // 255, g * colour[1] // 255, b * colour[2] // 255, a)
    return out


IRON = (213, 213, 213)
WOOD = (150, 108, 74)
BINDING = (176, 130, 96)

spear = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
spear.alpha_composite(tint(load("gen_spear_tool_head"), IRON))
spear.alpha_composite(tint(load("gen_spear_tool_shaft"), WOOD))
spear.alpha_composite(tint(load("gen_spear_tool_binding"), BINDING))

part = tint(load("gen_spear_part"), IRON)

tiles = [("spear", spear), ("part", part),
         ("cast gold", load("gen_spear_cast_gold")),
         ("cast sand", load("gen_spear_cast_sand")),
         ("cast red sand", load("gen_spear_cast_red_sand")),
         ("pattern", load("gen_spear_part_pattern"))]

scale = 12
canvas = Image.new("RGBA", ((16 * scale + 12) * len(tiles), 16 * scale + 8), (24, 24, 28, 255))
for index, (_, image) in enumerate(tiles):
    canvas.alpha_composite(image.resize((16 * scale, 16 * scale), Image.NEAREST), (index * (16 * scale + 12), 4))
canvas.save("art/out/spear_preview.png")
print("art/out/spear_preview.png", [name for name, _ in tiles])
