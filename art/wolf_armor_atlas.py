"""Paint the wolf-armour atlas (64x32) for the vanilla 1.20.1 WolfModel UVs.

The wolf is rendered with its own model; this texture is drawn *over* it, so the
UV rectangles are the vanilla wolf's, not ours. Only body/upper_body/legs/tail
are painted: the head rectangles stay transparent so the wolf's own face shows,
which is what makes the armour read as barding instead of a recoloured wolf.

Colours are a grey ramp on purpose — the game tints the layer with the armour's
material colour (TConstruct material render info), so the ramp must stay
neutral or it would double-tint.

Run:  python art/wolf_armor_atlas.py
Writes src/main/resources/assets/futuretinker/textures/entity/wolf_armor.png
"""

from PIL import Image, ImageDraw

WIDTH, HEIGHT = 64, 32

# (u, v, w, h, d) for every box that should be covered, straight from the
# vanilla WolfModel's CubeListBuilder calls (see .art-work/wolfarmor/wolf_model.json).
CUBES = [
    (18, 14, 6, 9, 6),   # body
    (21, 0, 8, 6, 7),    # upper_body
    (0, 18, 2, 8, 2),    # all four legs share this rect
    (9, 18, 2, 8, 2),    # tail
]

# Grey ramp. Multiplied by the material colour at render time.
MID = (172, 172, 172, 255)
LIGHT = (206, 206, 206, 255)
SHADE = (120, 120, 120, 255)
DARK = (54, 54, 54, 255)


def faces(u, v, w, h, d):
    """Vanilla ModelPart.Cube face rectangles."""
    return [
        (u + d, v, w, d),             # down
        (u + d + w, v, w, d),         # up
        (u, v + d, d, h),             # west
        (u + d, v + d, w, h),         # north
        (u + d + w, v + d, d, h),     # east
        (u + d + w + d, v + d, w, h)  # south
    ]


def plate(draw, x, y, w, h):
    """A bolted plate: dark rim, lit top-left, shaded bottom-right, corner rivets."""
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=MID)
    draw.rectangle([x, y, x + w - 1, y + h - 1], outline=DARK)
    if w >= 4 and h >= 4:
        draw.line([x + 1, y + 1, x + w - 2, y + 1], fill=LIGHT)
        draw.line([x + 1, y + 1, x + 1, y + h - 2], fill=LIGHT)
        draw.line([x + 1, y + h - 2, x + w - 2, y + h - 2], fill=SHADE)
        draw.line([x + w - 2, y + 1, x + w - 2, y + h - 2], fill=SHADE)
    if w >= 6 and h >= 6:
        # one seam through the middle turns a slab into two bolted plates
        seam = y + h // 2
        draw.line([x + 1, seam, x + w - 2, seam], fill=DARK)
        for rx, ry in ((x + 2, y + 2), (x + w - 3, y + 2),
                       (x + 2, seam + 2), (x + w - 3, seam + 2)):
            draw.point((rx, ry), fill=LIGHT)
    elif w >= 4 and h >= 4:
        for rx, ry in ((x + 2, y + 2), (x + w - 3, y + h - 3)):
            draw.point((rx, ry), fill=LIGHT)


def banded(draw, x, y, w, h):
    """Narrow strips (legs, tail): plain plate with a cuff at the joint.

    Two texels wide is too little for a rim plus shading — any extra line reads
    as a stripe, so this stays almost flat and lets the game's own face lighting
    do the work.
    """
    draw.rectangle([x, y, x + w - 1, y + h - 1], fill=MID)
    draw.line([x, y, x + w - 1, y], fill=DARK)
    if h >= 4:
        draw.line([x, y + h - 1, x + w - 1, y + h - 1], fill=SHADE)


def main():
    img = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for u, v, w, h, d in CUBES:
        for fx, fy, fw, fh in faces(u, v, w, h, d):
            if min(fw, fh) >= 4:
                plate(draw, fx, fy, fw, fh)
            else:
                banded(draw, fx, fy, fw, fh)
    out = "src/main/resources/assets/futuretinker/textures/entity/wolf_armor.png"
    img.save(out)
    print("wrote", out, img.size)


if __name__ == "__main__":
    main()
