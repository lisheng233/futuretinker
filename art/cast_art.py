"""Cut the spear/mace cavities out of TConstruct's three blank casts.

TConstruct's blank casts (gold / sand / red sand) are 16x16 fully opaque
textured blocks.  A part cast is exactly that block with the part silhouette
punched out, so we reuse the real blank textures instead of drawing a new
palette.  The cavity uses the part icon's alpha at its native pixel size
(centred, not scaled, not dilated), which keeps it identical to the part.

The blank textures are read straight from the TConstruct jar in libs/.
Deterministic: rerun and you get the same files.
"""

import io
import pathlib
import zipfile

from PIL import Image

ART = pathlib.Path(__file__).resolve().parent
RES = ART.parent / "src/main/resources/assets/futuretinker/textures"
TCON_JAR = sorted((ART.parent / "libs").glob("TConstruct-*.jar"))[-1]

BLANKS = (
    ("item/cast", "cast"),
    ("item/sand_cast", "sand_cast"),
    ("item/red_sand_cast", "red_sand_cast"),
)


def cavity(part):
    """Native-size part silhouette, centred on a 16x16 canvas."""
    image = Image.open(RES / "item" / f"{part}.png").convert("RGBA")
    alpha = image.getchannel("A").point(lambda a: 255 if a > 0 else 0)
    bbox = alpha.getbbox()
    if bbox is None:
        raise ValueError(f"{part} has no pixels")
    shape = alpha.crop(bbox)
    mask = Image.new("L", (16, 16), 0)
    mask.paste(shape, ((16 - shape.width) // 2, (16 - shape.height) // 2))
    return mask


def blank_texture(folder):
    with zipfile.ZipFile(TCON_JAR) as jar:
        path = f"assets/tconstruct/textures/{folder}/blank.png"
        with jar.open(path) as handle:
            image = Image.open(io.BytesIO(handle.read())).convert("RGBA")
    if image.size != (16, 16):
        raise ValueError(f"{folder}/blank.png is {image.size}, expected 16x16")
    return image


def cut(blank, mask):
    out = blank.copy()
    for y in range(16):
        for x in range(16):
            if mask.getpixel((x, y)):
                out.putpixel((x, y), (0, 0, 0, 0))
    return out


def main():
    for part in ("spear_head", "mace_head"):
        mask = cavity(part)
        for folder, _ in BLANKS:
            path = RES / folder / f"{part}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            cut(blank_texture(folder), mask).save(path)
    print("wrote 2 parts x 3 casts from TConstruct blank casts")


if __name__ == "__main__":
    main()