# Cozy Core

*Put the kettle on and build.*

Cozy Core takes the grind out of building in Minecraft. Your builds stay built, a lantern keeps the night quiet, and decorating needs fewer trips back to the crafting table. There's nothing new to learn and nothing running in the background.

This is the version for **Minecraft 1.21.1**, on Fabric and NeoForge. It's the same Cozy Core as on 26.3; the few small differences are under [On 1.21.1](#on-1211).

## Two ways to get it

- **The mod** (Fabric with [Fabric API](https://modrinth.com/mod/fabric-api), or NeoForge): everything below.
- **The data pack** (`.zip`): only the recipes, for a world on plain vanilla. Drop it in the world's `datapacks` folder and type `/reload`.

## What it adds

**Builds stay built.** Near a Hearth Lantern (below), creeper and ghast explosions still hurt, but break no blocks. Endermen leave your blocks alone. Fire still burns, but never spreads to a block or destroys one. Phantoms don't come for you there. Out in the wild, Minecraft is as usual, or a game rule spares blocks everywhere.

**Keep your hotbar when you die.** Your hotbar, armour and off-hand stay with you. Everything else drops where you fell (into a grave, with a graves mod), so there's still a trip back.

**The Hearth Lantern.** Make it from a lantern (or a soul lantern) and four amethyst shards. Within 32 blocks of it is home: your build is kept safe (above) and no monster spawns naturally, so a build site stays quiet at night without torches everywhere. Spawners and raids work as usual.

**Wood in the stonecutter.** One plank makes one set of stairs, a fence, a gate, a door, a trapdoor, a button, a pressure plate or a sign, or two slabs. A log makes four times as much. Logs also cut into wood and stripped wood, and bamboo works too.

**Slabs back into planks.** Two wooden slabs make one plank again.

**Re-dyeing.** Eight blocks of terracotta, stained glass, glass panes or concrete powder around a dye, in any colours, make eight of the dye's colour.

**A few hard-to-find blocks to craft:**

| Recipe | Makes |
|---|---|
| Six sticks in two columns with a string between them at the top | 3 scaffolding |
| Eight sand around a red dye | 8 red sand |
| A vine and a glow ink sac | 4 glow lichen |
| Any leaves and bone meal | A moss block |

**Traders with building blocks.** The wandering trader can sell azaleas, fungus and calcite. Masons sell tuff, mud bricks, calcite and packed mud.

The rules, the lantern and the trades are in the mod only, since they need a little code. New recipes show up in your recipe book as you pick up what they need.

## Changing the rules

Each rule is an ordinary game rule: it shows in the Game Rules screen when you make a world, and `/gamerule` changes it any time.

| Game rule | New worlds |
|---|---|
| `cozy_core:hearths_protect_builds` | true (blasts, fire and endermen spare blocks near a Hearth Lantern) |
| `cozy_core:mob_explosions_break_blocks` | true (false: explosions break nothing anywhere) |
| `cozy_core:endermen_take_blocks` | true (false: endermen take nothing anywhere) |
| `cozy_core:fire_burns_blocks` | true (false: fire burns nothing anywhere) |
| `cozy_core:keep_hotbar_on_death` | true |
| `cozy_core:hearth_lantern_radius` | 32 (how far home reaches; 0 switches lanterns off) |
| `doInsomnia` (vanilla's own phantoms rule) | true (phantoms don't come for you near a Hearth Lantern) |

Worlds you already have keep the settings they were made with.

## Other mods

With the mod version, wood from Biomes O' Plenty, Ecologics, Promenade and Wilder Wild works in the stonecutter like vanilla wood. Pieces from [Beautify](https://modrinth.com/mod/beautify-refabricated) and Macaw's [Roofs](https://modrinth.com/mod/macaws-roofs), [Fences and Walls](https://modrinth.com/mod/macaws-fences-and-walls), [Stairs](https://modrinth.com/mod/macaws-stairs), [Windows](https://modrinth.com/mod/macaws-windows), [Furniture](https://modrinth.com/mod/macaws-furniture), [Doors](https://modrinth.com/mod/macaws-doors) and [Trapdoors](https://modrinth.com/mod/macaws-trapdoors) go in too, when they're made of a single wood. Small pieces cost one plank; big ones, like desks and big gates, cost a log. Anything with glass, iron or paper stays at the crafting table. These recipes only switch on when that mod is installed.

## On 1.21.1

- Wood in the stonecutter covers the woods 1.21.1 has. Pale oak and its pieces came later, and so did shelves.
- On NeoForge the rules show under vanilla's own headings in the Game Rules screen (Mobs, Updates, Spawning and Player) instead of a "Cozy Core" heading. Fabric has the heading. They're the same rules either way.
- Phantoms are vanilla's `doInsomnia` rule, which 1.21.1 calls it.
- There are no copper Hearth Lanterns: copper lanterns came in a later Minecraft version. The classic and soul ones are here.
- Wilder Wild's hollowed logs cut into four planks, like its other logs: on 1.21.1 it counts them as logs.
- Beautify's NeoForge version has no cherry blinds or trellises, so those are only in the stonecutter on Fabric. Wilder Wild has no NeoForge version for 1.21.1.
- With the experimental trade rebalance switched on, the wandering trader picks its trades another way and doesn't get the extra slot. Masons still do.

## Requirements

This version: Minecraft 1.21.1 and Java 21, with either Fabric (Fabric Loader 0.16 or newer and
Fabric API) or NeoForge 21.1 (nothing else needed). Pick the `fabric` or `neoforge` file for your
loader. The data pack is for plain Minecraft 1.21.1. For Minecraft 26.3, see the main page.

## Licence

[MIT](LICENSE).

Cozy Core is made by Gabby with help from Claude, an AI assistant. The icon is Gabby's own pixel art: `art/icon.ase` is the source, and `art/icon.png` is it at 512 pixels, each pixel kept sharp. The Hearth Lantern's textures are drawn by `tools/generate-art.js`.
