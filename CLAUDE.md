# CLAUDE.md

Laser Assembly — a NeoForge mod: an assembly table fed by lasers fitted with lenses, recipes in
steps, intensity and heat. A rewrite of BuildCraft's Assembly Table, not a copy of it. Content
(recipes, beam types, augments, coolants) is defined in datapacks rather than in code.

`DESIGN.md` is the source of truth for what the mod does and why (**written in French**). Every
decision in it was settled one by one with the maintainer; do not reopen one in passing.

## Branches

One branch per Minecraft version: `1.21.1` (NeoForge, released) and `26.1` (NeoForge 26.1.2, the
port in progress, sized in `PORTING.md`); `master` still holds the abandoned Forge 1.20.1 prototype.
Changes go on the oldest branch they apply to and are merged forward, as in Ore Vein Tweaker.

## Build and run

| Task | What it does |
|---|---|
| `gradlew build` | jar + JUnit, `build/libs/laserassembly-<version>+26.1.2.jar` |
| `gradlew test` | JUnit over the pure-logic classes in `core/` |
| `gradlew runGameTestServer` | every GameTest headlessly, non-zero exit on failure |
| `gradlew runClient` / `runServer` | dev client / dedicated server, game directory `run/` |

No datagen: every JSON under `src/main/resources` is written by hand.

`org.gradle.daemon=false`: every invocation pays a full JVM start, so keep Gradle out of anything
that runs per-edit. Each run owns its game directory on purpose (see `build.gradle`).

## House rules — shared with Factor'IO and Portable Beacons

- **`core/` is pure logic**: no components, packets, rendering or `Level`. It is what survives the
  ports, and it is tested in JUnit. Anything needing a world is a GameTest, never a mocked `Level`.
- **`client/` is never imported from common code.** That is the dedicated-server crash guard.
- **No periodic S→C packet.** Screens sync through `ContainerData`, the world through
  `getUpdateTag`; menu actions go through `clickMenuButton`, validated server-side.
- **C→S validations**, in order: sender, chunk loaded, reach, menu open, right block, right type.
- **Hot paths** (`tick()`): no `stream()`, no allocation, no uncached `getBlockEntity`.
- **Item transfers**: simulate, compute what moves, then extract. Every insert/extract result is
  consumed. No item is ever created or destroyed by a transfer, a reload or a broken block.
- **Data validation**: bounded codecs that refuse and log, never silent coercion.
- **No visible string in code.** New keys go into `en_us.json` **and** `fr_fr.json` together.
- **Screens follow the shared charter** (Factor'IO `docs/09-CONVENTIONS.md` §9): `GuiMetrics`,
  `GuiTheme`, `GuiSprites`, 176 wide, energy in column 0, status light, side tabs.

## Licensing

Code MIT, assets all rights reserved with redistribution granted, from `licensing-kit`.
`mod_license` states both halves; both files are packed into the jar.

## Conventions

Comments explain *why*, usually the failure that motivated the code. UTF-8. Package
`dev.drimoz.laserassembly`, author DrimoZ, version `x.y.z+<minecraft>`.
