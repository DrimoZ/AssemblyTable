# Datapack reference

Everything Laser Assembly makes, fires or cools with is a datapack file. This page lists every
field. A file that breaks a rule is refused and logged at load, never quietly corrected: check the
log after changing one.

Recipes reload with `/reload`. Beam types, augments and coolants are registries: they load with the
world, so restart it after changing them.

## Recipes

`data/<namespace>/recipe/<name>.json`, type `laserassembly:assembling`.

```json
{
  "type": "laserassembly:assembling",
  "ingredients": [
    { "ingredient": "minecraft:iron_ingot", "count": 3 },
    { "ingredient": "#c:gems/prismarine", "count": 2 }
  ],
  "result": { "id": "minecraft:trident" },
  "intensity": 2,
  "steps": [
    { "beam": "laserassembly:cut", "energy": 8000 },
    { "beam": "laserassembly:weld", "energy": 12000, "intensity": 4, "strict": true }
  ]
}
```

| Field | | |
|---|---|---|
| `ingredients` | required | 1 to 12 entries, each an ingredient and a `count` from 1 to 64. An ingredient is an item id, a `#tag`, or a list of ids, as in vanilla recipes since 1.21.2 - the 1.21.1 `{ "item": ... }` form is refused on this branch |
| `result` | required | the item stack made |
| `energy` | either this | FE of work for a recipe in one step, which any beam advances |
| `steps` | or this | 1 to 3 steps, done in order |
| `intensity`, `heat_above`, `max_intensity` | optional | thresholds for every step that does not set its own |
| `keep_components` | optional, `false` | the result keeps the first ingredient's components: enchantments, damage, name |

A step:

| Field | | |
|---|---|---|
| `beam` | optional | the beam type it wants; without it, any beam |
| `energy` | required | FE of work, above 0 |
| `intensity` | optional | the lowest intensity a beam needs to work on it |
| `heat_above` | optional | beams above this heat the table |
| `max_intensity` | optional | beams above this are too strong and hold fire |
| `strict` | optional, `false` | a laser without a lens does nothing on it; needs a `beam` |

**Thresholds.** Each comes from the step, then the recipe, then the server config: `intensity`
defaults to `default_intensity` (1), `heat_above` to 3 × the intensity and `max_intensity` to
10 × (`heat_multiplier`, `max_multiplier`). They must read intensity ≤ heat_above ≤ max_intensity.
A laser's intensity goes from 1 to 5.

**Raw beams.** A laser without a lens works on a step that wants a beam at a tenth of its power
(`raw_beam_factor`), unless the step is `strict` or the server sets the factor to 0.

## Beam types

`data/<namespace>/laserassembly/beam/<name>.json`. The file's id is the beam's id, which recipes
name in `beam`.

```json
{ "item": "laserassembly:cut_lens", "color": "#FF8C1A" }
```

| Field | | |
|---|---|---|
| `item` | required | the lens item that fires this beam; one item per beam type |
| `color` | required | the beam's colour, `#RRGGBB` |

The lens item must exist: register it with KubeJS or another mod. Name the beam with a translation
key `beam.<namespace>.<name>`.

## Augments

`data/<namespace>/laserassembly/augment/<name>.json`. Every effect is optional and they combine.

```json
{ "item": "laserassembly:range_augment_1", "group": "laserassembly:range", "range": 4 }
```

| Field | | |
|---|---|---|
| `item` | required | the augment item; one item per augment |
| `group` | optional | a laser takes one augment per group; without it, the entry is its own group |
| `range` | optional, 0 to 32 | blocks added to the laser's range |
| `efficiency` | optional, 0.05 to 1 | multiplies the FE a shot costs: 0.8 is 20 % cheaper |
| `regulator` | optional | the laser sets its own intensity, the highest that fits without heating |
| `barrel` | optional, 0 to 3 | extra lens slots; the laser fires the lens the step wants |

## Coolants

`data/<namespace>/laserassembly/coolant/<name>.json`. A table counts the blocks against its sides
and bottom; each block counts its best coolant.

```json
{ "blocks": "minecraft:packed_ice", "cooling": 4 }
```

| Field | | |
|---|---|---|
| `blocks` | required | a block, a list of blocks, or a `#tag` |
| `cooling` | required | heat removed per tick, on top of the table's own dissipation |

Shipped: water 1, ice 2, packed ice 4, blue ice 8. A table holds 1000 heat; a beam heats it by
its intensity above `heat_above`, per tick.

## Server config

`serverconfig/laserassembly-server.toml` in the world folder. Defaults in brackets.

| Key | |
|---|---|
| `intensity.default_intensity` [1] | minimum intensity of a step that sets none |
| `intensity.heat_multiplier` [3] | `heat_above` default, × the minimum |
| `intensity.max_multiplier` [10] | `max_intensity` default, × the minimum; not below `heat_multiplier` |
| `intensity.raw_beam_factor` [0.1] | share of a raw beam's power on a step that wants a lens; 0 makes lenses required |
| `intensity.fe_per_intensity` [20] | FE of work one point of intensity is worth per tick, and what the laser pays |
| `intensity.stacking_factor` [0.7] | each extra laser on a table counts this much of the one before |
| `heat.heat_per_intensity` [1] | heat per tick per point above `heat_above` |
| `heat.dissipation` [1] | heat a table loses per tick on its own |
| `heat.max_heat` [1000] | past half the table slows; here it stops until cooled to half |
| `laser.range` [8] | how far in front a laser finds a table, in blocks |

The vanilla recipes (saddle, trident, chainmail, name tag, horse armour) are a built-in datapack,
on by default. Turn it off in the world's datapack screen, or with `/datapack disable` and the name
`/datapack list` shows for it.
