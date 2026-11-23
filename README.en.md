# Future Tinker

[English](README.en.md) | [中文](README.md)

A **Tinkers' Construct add-on for Minecraft 1.20.1** that rebuilds newer vanilla equipment
and mechanics with Tinkers' materials, parts, and modifiers.

> Status: **0.1.0**. Back up your worlds before testing. The full engineering notes
> (in Chinese) are in [README.md](README.md) and [PLAN.md](PLAN.md).

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.3.0 |
| Tinkers' Construct | 3.12.1.236 (runtime `[3.9.0,)`) |
| Mantle | 1.11.122 (runtime `[1.11.0,)`) |
| Java | 17 |

The `libs/` folder contains the TConstruct/Mantle jars pinned for reproducible builds.
They are third-party works and keep their original licenses.

## Installation

1. Install Minecraft 1.20.1, Forge 47.3.0, Tinkers' Construct and Mantle.
2. Drop the release jar into the `mods/` folder.
3. Start the game.

## Modules

### Wolf Armor

A Tinkers' `ModifiableArmorItem` worn by **wolves**, not players.

- Four parts: chestplate plating, maille, and two large plates; assembled at the Tinkers'
  anvil.
- Armor value scales with the material; the default total multiplier is 2.0 (iron = 10 armor).
- Material-colored icon and in-world rendering; the armor layer inflates the wolf model
  per box so the back, belly, legs, tail, sitting pose, and baby wolves all fit.
- Absorbs non-magic damage with its own durability (1:1 by default), drops on death, and has
  an encyclopedia page.
- The base armor multiplier, toughness, flat bonuses, and absorption ratio are configurable.
- Part swapping: the armor is in `tconstruct:modifiable/multipart` (Tinker Station), and
  `recipes/tools/armor/wolf_armor_swapping.json` adds the plate-armor-style casting-table
  recipe for its chestplate plating.

### Spear

A Tinkers' tool with a new `spear_head` part, a tough handle, and a tough binding.

- **Left-click jab**: 4.5 block reach, full-charge requirement, no critical hits, no block
  mining, and a multi-target sweep with the vanilla 0.125 hitbox margin.
- **Right-click kinetic charge**, aligned with Minecraft 1.21.11:
  - ray from eye + 2.0 to 4.5 blocks (6.5 in creative) plus one tick of movement;
  - exact hitbox first, then the 0.125-inflated hitbox with a line-of-sight check to the
    hitbox center;
  - block clipping from the eye; walls closer than 2.0 cancel the attack;
  - relative-speed damage `attack damage + floor(relative speed × multiplier)`;
  - separate dismount, knockback, and damage windows;
  - a 10-tick per-target contact cooldown.
- Gold/sand/red-sand casts are registered through Tinkers' `PartCastItem`. Creating each
  cast and pouring parts from it both use the standard casting/molding recipes:
  `recipes/tools/parts/casting/` has the reusable gold cast, the consumed sand/red-sand
  casts, and composite casting, while `data/tconstruct/tags/items/casts/` puts the tools
  into Tinkers' `multi_use`, `single_use`, `gold`, `sand`, and `red_sand` tags.
  Each texture starts from the matching Tinkers' blank cast (`cast/blank`,
  `sand_cast/blank`, `red_sand_cast/blank`) and punches out the part icon's alpha at
  native size — centred, not scaled, not dilated. The casts are **not** part-builder
  pattern items; the builder still uses blank patterns.
- The part icon itself is centred in its 16×16 canvas, so the casting table draws the
  cast and the poured part in the same frame and the part lands inside the cavity.
- Part swapping: the spear is in `tconstruct:modifiable/multipart`, so its head, handle,
  and binding can be replaced at the Tinker Station.
- First- and third-person charge/jab animations, with the vanilla swing duration.
- Per-material charge stats and fatigue windows; the tuning audit is in
  [docs/spear_charge_audit.md](docs/spear_charge_audit.md).

### Mace (Wolf-Tooth Club)

A heavy Chinese wolf-tooth club built from a custom mace head and a tough handle.

- The model is a heavy oval body, rows of iron teeth, a diagonal socket collar, and a
  pointed tip; the tool icon is 32×32 while the part icon and casts stay 16×16.
- Hits apply a bleed effect; while bleeding, the target takes magic damage every 10 ticks
  **only while moving**.
- The head has two custom stats, `futuretinker:hardness` and `futuretinker:toughness`:
  - **HARD**: armor-piercing bonus damage, narrow sweep, slower attacks;
  - **TOUGH**: movement slow, medium sweep;
  - **LIGHT**: faster attacks, wide sweep with lower per-target damage;
  - **NEUTRAL**: baseline.
- A full-charge hit sweeps targets in front; the handle's weight applies a movement-speed
  penalty.
- The handle and the socket collar share one head axis; the shaft is rasterised one
  row at a time at a fixed width, so its centreline no longer steps halfway down.
- Gold/sand/red-sand casts follow the same recipes, tags, and blank-cast cavity rule as
  the spear; the mace is also in `tconstruct:modifiable/multipart` for part swapping.

## Creative Tab

The shared "Future Tinker" tab lists the spear head and mace head once per usable
material via Tinkers' `IMaterialItem.addVariants`, matching how Tinkers' own parts tab
works. Tools and casts are listed once.

## Configuration

The common config is `config/futuretinker-common.toml`.

- Module switches: `[modules.wolfarmor]`, `[modules.spear]`, `[modules.mace]`.
  Disabled modules register **nothing** and require a game restart.
- Numeric sections: `[wolfarmor]`, `[spear]`, `[mace]` expose base armor multipliers,
  absorption, base attack damage/speed, reach, charge scaling, fatigue windows, bleed, and
  handle weight.
- `[mace.material_stats].overrides` lets pack authors set the mace head's hardness and
  toughness for **any** material, including materials this mod does not hardcode:

```toml
[mace.material_stats]
overrides = [
  "tconstruct:nahuatl=4.0,2.5",
  "modid:my_material=6.5,1.5"
]
```

Priority: **config override > material data > built-in classic table > stat-derived
fallback**. Invalid entries are skipped with a warning.

See [docs/configuration.md](docs/configuration.md) for every key, default, and reload note.

## Building and Testing

```bash
# Build the release jar -> build/libs/futuretinker-0.1.0.jar
./gradlew build                 # Windows: gradlew.bat build

# Run the required GameTests and validate the log evidence
python tools/verify_gametest.py
```

The verifier exits 0 only when all 38 required tests pass, the log contains the batch
evidence, and the jar is newer than the sources.

## Project Layout

```text
src/main/java/com/lisheng/futuretinker/
  config/      module switches, base values, mace material overrides
  module/      module lifecycle and the single registration gate
  wolfarmor/   wolf armor module
  spear/       spear module and 1.21.11 kinetic charge
  mace/        wolf-tooth mace, bleed, forms and sweep
  gametest/    GameTest suites
src/main/resources/  assets, data, tool definitions and recipes
art/                 deterministic texture/cast generation scripts
docs/                configuration and charge audit documents
tools/               GameTest verification script
```

## Verification and Known Limitations

- `gradlew build` passes; `tools/verify_gametest.py` reports **41/41 required tests passed**.
- `CastCastingTests` checks all gold/sand/red-sand pouring recipes, their fluid amounts,
  consumed flags, and item tags; `PartSwappingTests` matches the Tinker Station swap recipe
  against the spear, mace, and wolf armor, the wolf armor casting-table plating swap
  against molten copper, and the per-material creative variants.
- The mod has not been tested inside a large modpack alongside Tinkers' Tools/Dreamtinker.
- Wolf armor client visuals, part-pattern buttons, and some material color combinations
  still need in-game confirmation.
- Tinkers' anvil/part-builder UI placement has not been visually checked in every case.

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0-only)**.
See [LICENSE](LICENSE).

Minecraft, Tinkers' Construct, Mantle, and their textures/reference assets remain the
property of their respective owners. The jars in `libs/` are pinned build dependencies and
keep their original licenses.
