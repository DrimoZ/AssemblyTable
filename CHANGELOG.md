# Changelog

All notable changes to this project are documented here, newest first.
Versions follow `{mod version}+{game version}`.

## Unreleased

### Fixed
- A table could crash the server when a datapack change brought its recipe back with fewer steps
  than it had reached. It now starts the recipe over.
- Lasers near an idle table no longer relink to it every second, which sent needless updates and
  reordered the lasers tab under the cursor.
- A server config with `max_multiplier` below `heat_multiplier` is reported once when it loads,
  and each recipe it disables is named once, instead of a warning on every inventory change.

### Changed
- The table no longer has a `powered` block state: it read its own signal already.

### Added
- A config screen under Mods, with every server setting named and explained.
- Lenses say which beam they fire, in its colour.
- A [datapack reference](docs/DATAPACKS.md): every field of recipes, beam types, augments and
  coolants, and every server setting.

## 1.0.0

The first release.

### Added
- **The assembly table.** Twelve inputs that take only what the ticked recipes use, four outputs,
  and recipes picked from a list; ticked recipes run in turn. It takes no power: lasers bring it.
- **Lasers.** Each finds the nearest table in front of it, within 8 blocks and in line of sight,
  turns its head to it and fires on Forge Energy. Intensity is set per laser, in the laser or from
  the table.
- **Lenses: cutting, welding, engraving.** A lens gives the beam its type. Without one a laser fires
  a raw beam, at 10 % on steps that want a lens (`raw_beam_factor`; 0 makes lenses mandatory).
- **Recipes in steps.** Up to three, in order, each with its beam, energy and intensity window.
  Recipes without steps take any beam.
- **Intensity judged per beam.** Too weak does nothing, above the heating threshold heats the
  table, above the maximum overloads. Several lasers go faster, each counting for 70 % of the one
  before (`stacking_factor`).
- **Heat.** Full speed up to half, slower past it, stopped at the top until it cools back to half.
  It leaves on its own; water, ice, packed ice and blue ice around the table take more away.
- **Augments.** Regulator, Barrel, Range I and II, Efficiency I and II, made on the table in steps.
- **Table side tabs.** Info (status, heat, cooling, beams, speed against one laser), Lasers (every
  linked laser, its lenses and intensity, all at once or one by one), Faces (input, output or both
  per face, auto import and export), Control (a switch and redstone with a threshold).
- **Automation.** Pipes, hoppers and the auto import fill up to one stack of each ingredient;
  players can fill more by hand. A comparator reads the craft's progress.
- **The assembly on display.** The item being made turns over the table's lit focus while it works.
- **Vanilla recipes** for saddle, trident, chainmail, name tag and horse armour, in a built-in
  datapack that is on by default and can be turned off.
- **JEI** shows every recipe with the lens each step needs; **Jade** shows what a table makes, its
  step, its progress and its heat.
- **Datapack content.** Beam types, augments, coolants and recipes are all datapack files; each beam
  type and augment names its own item.
- English and French.
