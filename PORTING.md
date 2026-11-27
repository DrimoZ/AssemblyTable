# Porting: 1.21.1 → 26.1

Written before any port code was touched, to size the port before it starts. Measured on
27 November 2026 against NeoForge 26.1.2.112. Everything marked **[verified]** was compiled or read
in a jar. Everything else comes from what Portable Beacons, Ore Vein Tweaker and Immaterial Drawers
already found on their own 26.1 ports (`BeaconPack/PORTING.md` §8–9 is the reference), and is an
estimate until the compiler disagrees.

## 1. Toolchain [verified]

| | 1.21.1 | 26.1 |
|---|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.252 | 26.1.2 / 26.1.2.112 |
| Java | 21 | 25 |
| Gradle / ModDevGradle | 9.2.1 / 2.0.148 | same |
| Parchment | 2024.11.17 | **none**: the game ships unobfuscated |
| `modLoader` / `loaderVersion` in `neoforge.mods.toml` | required | removed |
| JEI | 19.44.0.401 | 29.29.0.76 (`jei-26.1.2-neoforge`) |
| Jade | 15.10.6 | 26.1.12+neoforge |
| Mekanism (dev FE only) | 10.7.19 | **none on 26.x**: dropped from `devMods` |
| Pack format | data 48 | resource 84.0, data 101.1 |

Datagen is not used by this mod (every JSON is written by hand), so the `data` run is gone rather
than split into `clientData` / `serverData`.

## 2. What the compiler says [verified]

`gradlew compileJava` (with `-Xmaxerrs` raised): **429 errors in 36 of 47 files.** First pass: javac
reports no attribution errors behind a missing type, so the real number moves once the renames are in.

| file | errors |
|---|---|
| `content/laser/LaserBlockEntity` | 52 |
| `content/table/AssemblyTableBlockEntity` | 45 |
| `gametest/TableGameTests` | 41 |
| `client/gui/GuiSprites` | 40 |
| `client/table/AssemblyTableScreen` | 39 |
| `client/table/AssemblyTableRenderer`, `client/laser/LaserRenderer` | 20 each |
| `compat/jade/JadePlugin` | 17 |
| `client/laser/LaserScreen` | 14 |
| `recipe/AssemblyRecipe`, `client/GuiPreview` | 12 each |
| the other 25 | 1–10 each |

`core/` has 8 errors, in `Step` and `Beams`, all of them the `ResourceLocation` rename.

The API names in §3 were checked with `javap` against `minecraft-patched-26.1.2.112-merged.jar`:
`loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)`, `affectNeighborsAfterRemoval`,
`BlockEntityRenderer<T, S>` with `extractRenderState` + `submit`, `Level#isClientSide()`,
`Level#recipeAccess()`, `RecipeSerializer` as a record, `Screen#extractRenderState`.

## 3. What changes, by layer

**Renames (most of the 429).** `ResourceLocation` → `Identifier` (131 hits on its own),
`ResourceKey#location()` → `#identifier()`, `registryOrThrow` → `lookupOrThrow`, `Level#isClientSide`
is a method now (`isClientSide()`, 14 hits), `InteractionResult.sidedSuccess` gone,
`Block#onRemove` → `affectNeighborsAfterRemoval` / `preRemoveSideEffects`, `DirectionProperty` →
`EnumProperty<Direction>`, `Util` moved to `net.minecraft.util`.

**Screens: renames, not a rewrite.** `GuiGraphics` → `GuiGraphicsExtractor` (60 hits), `render` →
`extractRenderState`, input as event objects (`mouseClicked(MouseButtonEvent, boolean)`,
`keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`), `imageWidth` / `imageHeight` final and set
through the constructor. Portable Beacons migrated 97 draw calls this way. Trap they hit: **text
colours are strict ARGB**, so any `0x404040` without `0xFF` alpha draws invisibly. `GuiTheme` is
where ours live.

**Block entity save/load.** `saveAdditional` / `loadAdditional(CompoundTag, Provider)` →
`(ValueOutput)` / `(ValueInput)`. `ItemStackHandler.serializeNBT` / `deserializeNBT` follow, and
`CompoundTag` getters return `Optional` (the `Optional<Boolean>` / `Optional<Integer>` errors).
**Keep the keys identical**, so a world opened on 26.1 keeps its tables and lasers.

**Item and energy capabilities: the one real decision.** `Capabilities.ItemHandler.BLOCK`
(`IItemHandler`) → `Capabilities.Item.BLOCK` (`ResourceHandler<ItemResource>`, transactional), and
energy → `EnergyHandler`. `IItemHandler`, `ItemStackHandler` and `EnergyStorage` still exist
[verified], deprecated for removal, with `IItemHandler.of(...)` / `IEnergyStorage.of(...)` bridges.

- *Bridge* at the capability boundary: cheap now, due again at 26.2 or 26.3.
- *Migrate* to `ItemStacksResourceHandler` / `SimpleEnergyHandler` and transactions: more work now,
  and the house rule "simulate, compute what moves, then extract" becomes what a transaction
  does. `OneWay`, `CombinedInvWrapper`, `move()` and the neighbour caches are what get rewritten.
  Portable Beacons and Immaterial Drawers both migrated.

**Recommendation: migrate.** The transfer code is the one place an item can be duplicated or lost,
and the GameTests for DESIGN §10 are what prove it.

**Recipes.** `RecipeSerializer` is a record of `MapCodec` + `StreamCodec` (no subclass),
`Recipe#recipeBookCategory()` is abstract, `RecipeManager` is server-only
(`level.recipeAccess()` client-side, which changes how JEI and the recipe picker list recipes),
`Recipe#assemble` lost its `HolderLookup.Provider`.

**Rendering.** `BlockEntityRenderer` is two-phase since 1.21.9: `extractRenderState` into a
render state, then `submit` to a `SubmitNodeCollector`. `RenderType`, `ItemRenderer`,
`ModelResourceLocation` and `ModelData` are gone from where we use them. The beam and the floating
item in both renderers are real rewrites, the only ones in the port.

**GameTests: a rearchitecture.** `@GameTest`, `@GameTestHolder` and `@PrefixGameTestTemplate` no
longer exist. A test is a function registered in `Registries.TEST_FUNCTION` (through
`RegisterEvent`, *not* `TestFunctionLoader`, which runs before any mod is constructed) plus a
`data/laserassembly/test_instance/<name>.json` naming it, its structure and its timeout. The bodies
stay; `GameTestHelper#getBlockEntity` now takes the expected class.

**Jade, JEI.** Ten major JEI versions and Jade 15 → 26: the plugins are small (67 + 138 + 124
lines), expect renames plus the recipe-access change above.

## 4. What the compiler cannot see

- **Item model definitions.** Since 1.21.4 every item needs `assets/laserassembly/items/<id>.json`
  pointing at its model, or it renders as the missing texture. 11 files to add.
- **Recipe JSON.** Since 1.21.2 an ingredient is a string (`"minecraft:iron_ingot"`, `"#c:ingots"`,
  or a list), not `{ "item": ... }`. All 21 recipes (11 in `data/`, 10 in the built-in pack) change,
  and so do `docs/DATAPACKS.md` and the store page wherever they show a recipe: **the
  `laserassembly:assembling` format datapack authors write changes on this branch**, through
  `Ingredient.CODEC`.
- **Pack metadata.** `datapacks/vanilla_recipes/pack.mcmeta` says format 48; 26.1 reads
  `min_format` / `max_format` (data 101.1).
- **Item properties.** `Item.Properties#setId` is mandatory (1.21.2); `registerSimpleItem` and
  block items need the id.
- **Advancements, loot tables, tags, blockstates, block models:** expected to carry over. The test
  structure `platform.nbt` is upgraded by the data fixer on load.
- Portable Beacons' other trap: vanilla now shows container contents in item tooltips on its own.
  Not expected to apply (no item here carries an inventory), worth one look in game.

## 5. Order

1. Renames across the tree, `core/` first, then `gradlew test` green.
2. Block entities: `ValueInput` / `ValueOutput`, then the transfer API (§3, once decided).
3. Recipes and the 21 JSON files, `docs/DATAPACKS.md` with them.
4. GameTests on the new framework, then `runGameTestServer` green: this proves 2 and 3.
5. Screens, renderers, item model definitions, then JEI and Jade.
6. In game: textures, beam, floating item, text colours, tabs.

## 6. Branches

`26.1` branches from `1.21.1` at 1.1.0. Fixes still go on `1.21.1` and are merged forward. `26.2`
branches from `26.1` once it builds and runs, not before.

## 7. What actually happened

The port is done: 429 errors to zero, 7 unit tests and 12 GameTests green (one loads a save in the 1.21.1 format), every screen, both
renderers, JEI and Jade checked in a client through `GuiPreview`. Items and energy were migrated to
the transfer API, as recommended in §3.

**Where the estimate was right.** `core/` came through on the `Identifier` rename alone. The screens
were renames plus input events: no colour lacked its alpha. The renderers were the one rewrite.

**What only running it showed** - none of it fails a build:

- **`minecraft:chain` is `minecraft:iron_chain`** since 1.21.9. The four chainmail recipes failed to
  load, with an error that only names the list that came out empty.
- **Recipes reach a client only when asked for** since 1.21.2. The picker, Jade and JEI read them from
  `ModRecipes`, which the server fills with `OnDatapackSyncEvent#sendRecipes` and the client with
  `RecipesReceivedEvent`, at highest priority so it is there before JEI starts.
- **Jade refuses a provider that is both server data and client component** (since 1.21.6): the
  table's is two classes now.
- **An ingredient is only a list without registry access.** A unit test that parses a recipe needs
  `RegistryOps`; one that makes an `ItemStack` needs a world, since items get their components at
  resource load - that test is a GameTest now.
- **A `GameTestHelper` test is a registered function plus a `test_instance` file.** One
  `TableGameTests#register` lists them; a test missing from either side does not run.

**Kept compatible.** Block entities save under the 1.21.1 keys, items in the 1.21.1 layout
(`ItemSlots`), energy under `energy`: a table or laser carried over from a 1.21.1 world keeps what it
held. Datapacks are the exception: recipe ingredients changed format (see `docs/DATAPACKS.md`).
