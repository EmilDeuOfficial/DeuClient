# DeuClient wing models - shared spec

Goal: replace the old flat galaxy wings (two textured quads per side) with real 3D wing models made in
Blockbench (.bbmodel), loaded at runtime by the DeuCore mod (Minecraft 26.3, `mod/mc26`).

There are six styles, ids must not change: `engel`, `daemon`, `eis`, `phoenix`, `schatten`, `smaragd`.
Old palettes (for the colour theme, keep the spirit): see `WINGS` in `tools/generate-ingame-ui.cjs`.

## Files (one set per style `<id>`)

| What | Path |
|------|------|
| Model | `mod/mc26/src/main/resources/assets/deucore/models/wings/<id>.bbmodel` |
| Texture atlas | `mod/mc26/src/main/resources/assets/deucore/textures/cosmetics/wings/<id>.png` |
| Menu preview | `mod/mc26/src/main/resources/assets/deucore/textures/cosmetics/ui/wings_<id>.png` |
| Generator script (optional, recommended) | `tools/wings/<id>.cjs` |

Delete nothing else. The old `<id>_lower.png` files are removed later by the renderer agent.

## Model rules (the renderer relies on these)

* Blockbench format: **Generic Model ("free")**. Only **cubes** (zero thickness in one axis = plane is allowed).
  **No meshes**. Cubes may be rotated on any axis.
* **Per-face UV only** (`box_uv: false`). One texture, resolution 128x128 or 256x256 (set the project
  texture size, the renderer normalises UVs by it).
* Author **one wing only: the RIGHT wing**. The renderer mirrors it for the left side.
* Coordinate convention (Blockbench units, 16 units = 1 block, the player is 24 units tall, 8 wide, 4 deep):
  * Origin (0,0,0) is the shoulder blade attachment point on the player's back.
  * **+X = outward, away from the spine** (wing extends along +X).
  * **+Y = up**.
  * **+Z = backwards, away from the player's back** (the wing hangs 0 to +8 behind the back).
    The wing's rest pose is roughly in the XY plane (Z near 0), slightly swept back is fine.
  * Size: wingspan 26 to 36 units along X, height 24 to 38 units along Y (the old wings were 28).
    Make them clearly larger and richer than the old ones, but keep the silhouette elegant.
* Budget: **at most 150 cubes** per wing. Fewer, well-shaped cubes beat many noisy ones.
* Layering: real depth. Overlapping feather layers offset 0.4 to 1.0 units in Z, bone/arm structure
  thicker than the membrane/feathers. No z-fighting: offset coplanar faces by at least 0.2 units.
* Group hierarchy (names are case-sensitive, the renderer animates by name):
  * One top-level group `wing`, pivot (origin) at (0,0,0). Everything lives inside it.
  * Arm chain: nested groups `seg1` > `seg2` > `seg3` (optionally `seg4`), each with its pivot at its joint.
    Child groups sit inside their parent group, so they follow it. This gives a flexing wing.
  * Feather groups: any group whose name starts with `f_` (for example `f_01`). Pivot at the feather root.
    They get a small extra sway. Feathers belong inside the `seg*` group they are attached to.
  * Rest pose rotations of groups are allowed and encouraged (fan out feathers), the renderer adds its
    animation on top of them.
* Faces that must not be rendered: delete the face from the cube (or set no texture) instead of using a
  transparent pixel region.

## Texture rules

* RGBA PNG, transparent where unused. Draw every face UV island deliberately: gradient, feather veins,
  highlights, subtle galaxy sparkle (small bright stars), a brighter rim. Smooth gradients and clean edges
  are preferred over noisy pixel art. No text.
* The wings render with the translucent entity pipeline plus a minimum light level, so bright glow areas
  look emissive. Use alpha below 255 for soft, ghostly edges where it fits the style.
* The texture must match the model's UVs exactly: generate both from the same script, or paint the
  texture to the UV layout. Verify with the bbmodel render tools.

## Menu preview (`ui/wings_<id>.png`)

* 256x256 RGBA PNG, transparent background.
* One wing (the right wing) seen from behind, in the rest pose. The shoulder root sits at about
  x = 12 %, y = 55 % of the image, the wing extends to the right and up and fills the frame
  (about 5 % margin). The UI draws a mirrored copy for the left wing.
* Use the bbmodel render tools (`bbmodel_render`, `bbmodel_contact_sheet`) to get the image, then crop,
  resize and place it with node (`pngjs` and `@resvg/resvg-js` exist in `node_modules`).

## Procedural animation (done by the renderer, for your information)

The renderer yaws `wing` outward, rolls it up/down for the flap (idle: small, elytra flight: large,
crouch: folded), adds phase-lagged roll to deeper `seg*` groups and a small sway to `f_*` groups.
Design wings so that this looks good: joints at the elbow and wrist, feathers fanning from the arm.

## Style briefs

* `engel`: classic angel wings, long layered white and pale-gold feathers, strong arm bone, soft glow.
* `daemon`: bat or dragon wings: bone fingers with a dark red membrane between them, barbs and claws, ember glow.
* `eis`: crystalline ice wings: angular shard feathers, frosty cyan and white, translucent edges, sparkle.
* `phoenix`: flame wings: long feathers that taper into flame tips, orange to gold gradient, ember glow.
* `schatten`: ragged shadow wings: torn smoky feathers, deep violet and black, purple glowing veins.
* `smaragd`: DeuClient signature: emerald crystal blade feathers, green gradient, bright facet highlights.

Rules for the repo: no emojis in code or docs, use short hyphens only (never long dashes).

Nach jedem Lauf eines Generators: `node tools/wings/postprocess.cjs` (Federn unter die Arme, engel-Glow, Eisdeckel).
Danach `node tools/wings/postprocess2.cjs` (Rueckenanschluss) und `node tools/wings/pairs.cjs` (Paar-Ansicht).
Danach `node tools/wings/postprocess3.cjs` (kein engel-Glow, kein Flackern an Gelenken), dann `node tools/wings/pairs.cjs`.

## Animations (elytra replacement)

`node tools/wings/animations.cjs` writes the Blockbench animations `stehen`, `schleichen`, `gleiten`, `schlagen`, `sturz` into every model (rotation keyframes, groups by uuid; `stehen` and `schleichen` match the normal wings). The mod plays them when the player wears an elytra and chose wings in the cosmetics menu. Run it after the postprocess scripts, then `pairs.cjs`. It overwrites manual animation edits.
