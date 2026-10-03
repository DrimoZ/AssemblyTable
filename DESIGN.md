# Laser Assembly — Design (phase 1)

> Mod NeoForge **1.21.1**. Les ports (26.1, 26.2) se décideront une fois la 1.21.1 jouée.
> Objectif : **petit mod**, une seule idée, exécutée proprement et data-driven — la direction
> d'Ore Vein Tweaker, de Factor'IO et de Portable Beacons.
>
> Décisions tranchées une à une avec le mainteneur (séance de questions du 03/10/2026). Ce qui est
> écarté est dit, pour ne pas rouvrir le débat par accident.

## 1. Concept

Un **atelier laser**. Une table d'assemblage où l'on dépose des ingrédients, qui montre les recettes
réalisables, où l'on coche celles à produire. Des **lasers** posés autour la visent et lui
apportent l'énergie. Une **lentille** dans un laser donne un type à son faisceau (découpe, soudure,
gravure…) ; certaines recettes passent par des **étapes** qui demandent chacune un type de faisceau
et une **intensité**. Trop d'intensité fait **chauffer** la table, qu'on refroidit en construisant
autour.

La table de BuildCraft en est le cas le plus simple : une recette à une étape, n'importe quel
laser. Tout le reste est une progression, jamais une obligation.

## 2. Positionnement

| Mod | Version | Résumé |
|---|---|---|
| [Assembler Table](https://www.curseforge.com/minecraft/mc-mods/assembler-table) (Sixik, LGPL) | NeoForge 1.21.1 | Copie fidèle de BuildCraft : table, table alimentée, lasers devant le bloc avec montée en régime, chipsets, KubeJS/CraftTweaker |
| BuildCraft Community Edition, BuildCraft UNOFFICIAL | NeoForge 1.21.1 | Ports complets de BuildCraft |

Ce qui nous distingue : lentilles et étapes, intensité et chaleur, l'assemblage visible sur la
table, les lasers pilotés depuis la table, la charte d'écran de Factor'IO, tout en datapack.

**Mod id `laserassembly`.** `assemblytable` est celui du concurrent (deux mods au même id ne se
chargent pas ensemble) ; `assembly`, celui du prototype, est trop générique. Aucun mod « Laser
Assembly » trouvé au 03/10/2026 — revérifier sur CurseForge et Modrinth avant publication.

## 3. Le prototype de ce dépôt

Forge 1.20.1, 9 commits. Les idées sont reprises — recette JSON avec un coût en énergie,
recettes réalisables à cocher, mode sans laser, faces configurables — **pas le code**, réécrit :
il envoyait 3 paquets à tous les joueurs à chaque tick, plantait un serveur dédié
(`MenuScreens.register` en `commonSetup`), lisait la config avant son chargement, perdait le
namespace des recettes et ignorait le `count` des ingrédients.

## 4. La table

- **Entrées et sorties séparées**, toujours : 12 entrées (3×4), 4 sorties. Les entrées
  n'acceptent **que** les ingrédients des recettes cochées, au plus N crafts d'avance (règle du
  crafter de Factor'IO) : un tuyau ne peut plus la boucher.
- **Recettes réalisables** recalculées au changement d'inventaire, pas à chaque tick. Clic =
  cocher / décocher. Une recette cochée sans ses ingrédients reste cochée, voilée.
- **Production** : les recettes cochées tournent à tour de rôle (BuildCraft), en sautant celles
  qui ne sont pas réalisables. **Ingrédients consommés à la fin** du craft : casser la table ou
  décocher ne demande aucune comptabilité.
- **L'assemblage se voit** : au-dessus du plateau, les ingrédients de la recette en cours flottent
  et convergent à mesure que le craft avance, les faisceaux s'allument étape par étape, le
  résultat apparaît avec des particules. `BlockEntityRenderer`, pas de modèle animé.
- **Contrôle** : interrupteur + réaction native à la redstone. **Comparateur** : avancement du
  craft. **Jade** : recette en cours, étape, progression.
- **Pas d'énergie dans la table** : elle ne prend aucun FE, il faut toujours un laser. Sa colonne
  0, celle de l'énergie sur les autres machines, montre sa chaleur. Au repos, la chaleur retombe
  d'elle-même.

### 4.1 Faces et automatisation

Un **tiroir de configuration** (onglet à droite), comme Thermal : une vue de la table, et pour
chaque face — sauf le dessus, réservé à l'assemblage visible — *rien*, *entrée*, *sortie* ou
*entrée + sortie*. Deux interrupteurs : **import auto** (tire des voisins, uniquement les
ingrédients des recettes cochées, dans la limite de §4) et **export auto** (pousse les sorties).
Tout est désactivé par défaut ; les faces sans automatique restent accessibles aux entonnoirs,
tuyaux et inserters selon leur réglage. Transferts par lots toutes les 10 ticks.

Écarté : la table qui pousse d'elle-même dans l'inventaire du dessus (BuildCraft) — un coffre
posé dessus cacherait l'assemblage.

## 5. Recettes

```json
{
  "type": "laserassembly:assembling",
  "ingredients": [
    { "ingredient": { "item": "minecraft:iron_ingot" }, "count": 3 },
    { "ingredient": { "tag": "c:gems/prismarine" }, "count": 2 }
  ],
  "result": { "id": "minecraft:trident", "count": 1 },
  "energy": 20000,
  "intensity": 2,
  "steps": [
    { "beam": "laserassembly:cut", "energy": 8000 },
    { "beam": "laserassembly:weld", "energy": 12000, "intensity": 4 }
  ]
}
```

- **Sans `steps`** : une seule étape brute (`energy`, `intensity`). C'est le format BuildCraft et
  la majorité des recettes.
- **Au plus 3 étapes**, faites **dans l'ordre**. Une étape typée avance aussi au **faisceau brut, à
  ×0,25** (config) : les lentilles sont une optimisation, pas une barrière. `"strict": true` sur
  une étape refuse le faisceau brut — pour la fin de jeu.
- **Seuils d'intensité, aucun obligatoire.** Une étape ne déclare qu'une intensité minimale ;
  ce qu'elle ne précise pas vient de la recette, puis de la config serveur. La chauffe et le
  maximum en découlent par deux multiples (config : chauffe au-delà de ×3, surcharge au-delà
  de ×10 : un laser poussé à fond chauffe, il en faut plusieurs pour surcharger), qu'une recette ou une étape peut aussi fixer (`heat_above`, `max_intensity`).
- `"keep_components": true` : le résultat garde les composants de l'ingrédient principal
  (enchantements, durabilité) — améliorer ou graver un objet existant.
- `MapCodec` borné qui **refuse et journalise** : 1 à 12 ingrédients, `count` 1 à 64, 1 à 3
  étapes, `energy` > 0. Répartition des ingrédients par `StackedContents`. Tags `c:`.
- À l'écran comme dans JEI, une recette se lit en **une ligne d'icônes** : 🔴 2 → 🔵 4.
- Une recette disparue après `/reload` : la table garde l'identifiant, s'arrête, n'expulse rien.

## 6. Intensité, chaleur, surcharge

- Un laser a un **cran d'intensité de 1 à 5** ; plus haut, plus de FE par tick.
- L'intensité **reçue** par la table est la somme, par type de faisceau, des lasers qui la visent.
  En dessous du minimum de l'étape, rien n'avance ; au-dessus, plus vite.
- **Au-delà du seuil de chauffe**, la table chauffe. La chaleur redescend seule sous le seuil.
  **Refroidissement** : les blocs adjacents listés dans le registre datapack `coolant` (bloc ou
  tag + valeur — eau, glace, glace compacte, glace bleue livrées). Jauge à l'écran, lueur rouge et
  fumée sur la table.
- **La chaleur ralentit avant d'arrêter** : pleine vitesse jusqu'à la moitié de la chaleur
  maximale, puis de moins en moins vite jusqu'à 25 % juste avant le maximum.
- **Au-delà du maximum d'intensité** : rien n'avance (« surcharge »). **À chaleur maximale** : la
  table s'arrête jusqu'à être refroidie de moitié, et repart à pleine vitesse.
- Un point d'intensité vaut un nombre de FE de progression par tick : réglage de la config par
  défaut, que chaque type de faisceau du registre `beam` pourra fixer. Option serveur (1.1) : elle **explose** à la place — table et
  contenu détruits, lasers épargnés.

## 7. Les lasers

- **Ciblage** : la table la plus proche dans le **demi-espace devant le laser**, portée 8 blocs
  (config), **ligne de vue obligatoire**. Cible collante : il reste sur sa table tant qu'elle
  demande de l'énergie. Plusieurs lasers sur une table s'additionnent.
- **Écran du laser** : lentille, 2 emplacements d'augment, jauge d'énergie, cible actuelle, cran.
- **Piloté depuis la table** : un onglet « Lasers » liste les lasers liés — lentille, cran −/+,
  interrupteur, distance, augments — avec une ligne « tous ». On n'y change pas lentilles ni
  augments, qui sont des items à poser dans le laser. Chaque action validée serveur (6 validations
  de Factor'IO). Implémentation exacte à revoir en développement.
- **Lentilles** : un seul item, type porté par un composant ; les types de faisceau sont un
  **registre datapack** `beam` (id, couleur, nom). Livrés : découpe, soudure, gravure. Sans
  lentille, faisceau **brut**.
- **Augments** : un seul item, type en registre datapack (le modèle de Portable Beacons), un de
  chaque type par laser.

| Augment | Effet |
|---|---|
| Régulateur | règle seul l'intensité au maximum possible sans faire chauffer |
| Barillet | 3 lentilles de plus ; pose seul la bonne pour l'étape en cours |
| Portée | +4 / +8 blocs |
| Efficacité | −20 / −35 % de FE à intensité égale |

Écarté : des paliers de laser (le levier est le nombre de lasers et les augments) ; un augment
« surcharge » (la chaleur est une conséquence, pas un mode).

## 8. Contenu livré

- **Items du mod**, qui forment sa progression : table et laser à l'établi ; **lentilles sur la
  table** en faisceau brut (le premier usage, un tutoriel) ; **augments sur la table** avec étapes ;
  baguette à l'établi (1.1). Pas de chipsets ni de composants génériques.
- **Recettes vanilla d'objets introuvables**, dans un datapack intégré désactivable : cotte de
  mailles, selle, name tag, armures de cheval en brut ; trident (découpe → soudure) et éperon
  (découpe → soudure → gravure) à étapes.

## 9. L'écran

Charte de Factor'IO (`docs/09-CONVENTIONS.md` §9) : `GuiMetrics`, `GuiTheme`, `GuiSprites` repris
de Portable Beacons. 176 de large, énergie en colonne 0, voyant d'état, onglets — à gauche ce qui
renseigne (informations : état, étape, intensité reçue par type, chaleur), à droite ce qui se règle
(Lasers, Faces). Recettes en grille à ascenseur : tient dans 176 quel que soit le modpack.

## 10. Architecture

- `dev.drimoz.laserassembly`, version `x.y.z+1.21.1`, MIT (code) / ARR (assets) depuis
  `licensing-kit`, `LICENSE` et `LICENSE-ASSETS` dans le jar.
- ModDevGradle 2, Java 21, Gradle 9, `neoforge.mods.toml` généré depuis `src/main/templates`.
- `core/` : logique pure — étapes, intensité reçue, chaleur, tour de rôle des recettes, ciblage.
  Testée en JUnit ; c'est ce qui traversera les ports.
- `client/` jamais importé depuis le reste. Synchronisation par `ContainerData` et
  `getUpdateTag`, **aucun paquet S→C périodique**. Actions de menu par `clickMenuButton`.
- Capabilities NeoForge : items (par face), énergie.
- JEI (et EMI) en `compileOnly` + `devMods`.
- GameTests : conservation des items, persistance, recette disparue, plafond d'entrée respecté,
  surcharge qui arrête sans rien détruire.
- Ce dépôt et son historique sont gardés ; le prototype est retiré en un commit.

## 11. Découpage

| 1.0 | 1.1 et après |
|---|---|
| table, lasers, lentilles, étapes, intensité, chaleur, refroidissement | baguette (lier, voir les liens, copier une table, délier) |
| augments Régulateur, Barillet, Portée, Efficacité | commandes « en faire N » / « maintenir N » |
| onglet Lasers, tiroir Faces avec import/export auto | miroirs |
| assemblage visible, filtre des entrées, `keep_components` | faisceau qu'on peut couper |
| comparateur, Jade, JEI | option d'explosion en surcharge |
