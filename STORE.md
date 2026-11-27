# Store copy

Paste-ready text for the CurseForge and Modrinth project pages. Not documentation: this file sells
the mod, the README explains it. Every claim here is something the mod does today; check it against
the data files and `CHANGELOG.md` before changing a number.

No game version is named on purpose: the page stays true across ports, and each uploaded file
carries its own versions.

The images are `docs/store-art/`, linked from GitHub on the `1.21.1` branch, as for the other mods:
`LASER_GUI_PREVIEW=1 ./gradlew runClient` takes the captures by itself, then
`java tools/Banners.java run/screenshots/preview docs/store-art` makes the images (850 px wide at
most). Commit and push them; the page follows without being edited.


## Summary

> One line, 256 characters at most, shown under the name in every search result.

An assembly table fed by lasers. Fit them with lenses to cut, weld and engrave, run recipes in steps, tune each beam's intensity and keep the table cool. BuildCraft's idea, redesigned, all in datapacks.

## Categories

Main: **Technology**. Additional: Utility & QoL.

## Licence field

Custom. Point it at `LICENSE` in the repository: MIT for the code, all rights reserved for the
artwork with redistribution granted.

## Description

<!-- Everything below this line is pasted into the site's Markdown editor as-is. -->

![Laser Assembly](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/banner.png)

![The table at work](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/world.png)

### A workshop of lasers.

Build an Assembly Table, aim lasers at it, and it makes what no crafting grid can: saddles,
tridents, chainmail, horse armour, and the lenses and augments that make the lasers better. The
table takes no power of its own; every bit of work is a beam arriving at it. The lasers run on Forge
Energy: play it with a mod that makes some.

It is BuildCraft's Assembly Table, redesigned rather than copied: lasers carry lenses, recipes come
in steps, beams have an intensity, and pushing them too hard heats the table.

![Lenses and steps](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/header_lenses.png)

![Lenses](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/items_lenses.png)

![The laser](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/screen_laser.png)

- **Three lenses: cutting, welding, engraving.** A lens gives its laser's beam a type.
- **Most recipes take any beam.** Some go through up to three steps in order, a trident is cut,
  then welded, and only the lasers with the right lens fire on each step.
- **No lens?** The raw beam still works on any step, at a tenth of the speed. Servers can make
  lenses mandatory.
- **A Barrel augment** lets one laser carry three more lenses and fire whichever the step needs.

![Intensity and heat](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/header_heat.png)

- **Each laser has an intensity**, judged on its own against the step: too weak does nothing, above
  the threshold heats the table, above the maximum overloads.
- **More lasers, faster**, each counting for 70 % of the one before. Nine lasers are quicker than
  one, never nine times.
- **Heat slows the table past half and stops it at the top.** It cools on its own; water and ice
  around it cool it faster.
- **A Regulator augment** sets its laser's intensity by itself: as high as the step allows without
  heating.

![The information tab](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/screen_table_info.png)

![One screen](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/header_screen.png)

![The table and its lasers](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/screen_table_lasers.png)

- **Tick the recipes the table may run**; it takes them in turn.
- **See the step, its progress and the heat** at a glance, with a status light.
- **Side tabs**: every linked laser and its intensity, set one by one or all at once; each face's
  input and output with auto import and export; a switch and redstone control.
- **The item being made turns over the table** while it works, and each laser turns its head to it.

![Augments](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/items_augments.png)

![Automation](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/header_automation.png)

![Faces](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/screen_table_faces.png)

- **Per-face input and output**, auto import and export.
- **Only what the ticked recipes use goes in**: a pipe fills at most one stack of each ingredient,
  so it never clogs the table.
- **A comparator** reads the craft's progress.

![Redstone control](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/screen_table_control.png)

![For pack makers](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/header_data.png)

Recipes, beam types, augments and coolants are datapack files. Each lens and augment is its own
item, so tags, KubeJS, CraftTweaker and JEI see plain item ids. The vanilla recipes sit in a
built-in datapack you can turn off. The server config sets thresholds, the raw beam, stacking, heat
and range.

### Compatibility

| | |
|---|---|
| Loader | NeoForge |
| An energy mod *(required)* | anything that makes Forge Energy: the lasers run on it |
| [JEI](https://www.curseforge.com/minecraft/mc-mods/jei) *(optional)* | every recipe, with the lens each step needs |
| [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) *(optional)* | what a table makes, its step and its heat; whether a laser fires, and why not |

![JEI](https://raw.githubusercontent.com/DrimoZ/AssemblyTable/1.21.1/docs/store-art/screen_jei.png)

Needed on both the client and the server.

### FAQ

**Does the table need power?** No. Power the lasers with Forge Energy from any mod; they bring the
work.

**Can I add recipes, beams or augments?** Yes, with datapack files. A new beam or augment needs an
item to stand for it, from KubeJS or another mod.

**Fabric?** No.

**Can I put it in my modpack?** Yes. No need to ask.

### Permissions

**Modpacks: yes.** No permission needed, no message required, public or private, monetised or not,
on any platform or launcher.

**Credit** is appreciated and never required.

**Forks and addons: yes**, under the MIT terms. Please do not publish a fork under the name
*Laser Assembly*: the name is not covered by the licence.

**Assets** (textures, GUI artwork, logo) are the one exception: redistribute them with the mod
freely, but do not lift them into another project.

### Links

[CurseForge](https://www.curseforge.com/minecraft/mc-mods/laserassembly) ·
[Source](https://github.com/DrimoZ/AssemblyTable) ·
[Report a bug](https://github.com/DrimoZ/AssemblyTable/issues)

<!-- End of the pasted description. -->


## Release checklist

For each release, with `X` the version in `gradle.properties`:

- [ ] `## X` in `CHANGELOG.md`, the art regenerated and pushed (`docs/store-art/`).
- [ ] Tag `vX+<minecraft>` on the version's branch and push it.
- [ ] Upload the jar from `gradlew build`, release type **Release**, loader NeoForge, the branch's
      game version and Java (1.21.1: Java 21; 26.1.2: Java 25), with the `## X` section of
      `CHANGELOG.md` (`## X+26.1.2` on the 26.1 branch).
- [ ] Optional dependencies: JEI and Jade.
- [ ] Paste the description when it changed; set the project avatar to `src/main/resources/logo.png`
      when the logo did.
