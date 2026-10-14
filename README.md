# Cozy Core

*Put the kettle on and build.*

Small, thoughtful recipes and trades that take the grind out of decorating, for Minecraft 26.3,
and a few kind rules so the things you build stay built. There's nothing new to learn and
nothing running in the background: just fewer trips back to the crafting table, a few blocks that
were a pain to find, and a lantern that keeps the night quiet.

## What it adds

**Wood in the stonecutter.** Put planks in a stonecutter and pick what you want:

- one plank makes one set of stairs, a fence, a fence gate, a door, a trapdoor, a pressure plate,
  a button or a sign, or two slabs;
- one log makes four times as much (a log is four planks), or the planks themselves;
- logs and stems turn into their wood and hyphae, stripped or not, and stripped logs into shelves.

Bamboo works too, including bamboo mosaic. Hanging signs are left out, because their recipe needs
chains.

**Slabs back into planks.** Two wooden slabs make one plank again, so a slab you didn't need
isn't wasted. (Bamboo slabs go side by side, since stacked ones already make bamboo mosaic.)

**Re-dyeing.** Surround a dye with eight blocks of terracotta, stained glass, stained glass panes
or concrete powder, of any colours, and get eight of the dye's colour. Like wool, so a build can
change colour without starting over.

**A few hard-to-find blocks to craft:**

| Recipe | Makes |
|---|---|
| Six sticks in two columns with a string between them at the top | 3 scaffolding |
| Eight sand around a red dye | 8 red sand |
| A vine and a glow ink sac | 4 glow lichen |
| Any leaves and bone meal | A moss block |

**Traders who bring building blocks** (in the Fabric and NeoForge version only):

- the wandering trader can offer azaleas, flowering azaleas and crimson and warped fungus (5
  emeralds each) and 8 calcite for an emerald, with one more offer than usual so it never leaves
  its own trades out;
- masons sell tuff and mud bricks at their first level, and calcite and packed mud at their
  second, again with one more offer at each of those levels.

**Decorations in the stonecutter** (in the Fabric and NeoForge version, when the mod is
installed). [Beautify](https://modrinth.com/mod/beautify-refabricated)'s blinds and trellises
cut like vanilla's trapdoors and doors: one plank makes one set of blinds or one trellis, and a
log makes four. Picture frames still need wool, so they stay at the crafting table.

New recipes show up in your recipe book as you pick up what they need, like vanilla's.

**The Hearth Lantern** (in the Fabric and NeoForge version only). A lantern with four amethyst
shards around it makes one. No monster spawns naturally within 32 blocks of it, at any height,
so a build site or a village stays quiet at night without a torch on every block. It hangs,
stands and shines like a lantern. Spawners, raids and patrols carry on as usual, and you can
change how far it reaches with `/gamerule cozy_core:hearth_lantern_radius` (0 switches the
lanterns off).

**Kind rules** (in the Fabric and NeoForge version only). Every one is an ordinary game rule:
it's on in new worlds, shows in the Game Rules screen when you make a world, and can be set back
to vanilla with `/gamerule` whenever you like.

| Rule | What it does |
|---|---|
| `cozy_core:mob_explosions_break_blocks` (off) | Creeper and ghast explosions still hurt, but break nothing. TNT and the wither are vanilla. |
| `cozy_core:endermen_take_blocks` (off) | Endermen leave your blocks where they are. |
| `cozy_core:fire_burns_blocks` (off) | Fire still appears and goes out, but never catches a block alight or burns one away. A lightning strike on a wooden roof is harmless. |
| `spawn_phantoms` (vanilla's, off for new worlds) | No phantoms. Worlds you already have keep their setting. |

## Two ways to install

| | The data pack (`.zip`) | The mod (`.jar`, Fabric or NeoForge) |
|---|---|---|
| Recipes | Yes | Yes |
| Trades | No | Yes |
| Wood from other mods | No | Biomes O' Plenty, Ecologics, Promenade and Wilder Wild |
| Decorations from other mods | No | Beautify |
| The Hearth Lantern and the kind rules | No | Yes |
| Install | Into a world's `datapacks` folder; `/reload` and it works straight away | Into your `mods` folder (Fabric needs [Fabric API](https://modrinth.com/mod/fabric-api)) |

The data pack is recipes only so it can go into a world you're already playing, with no restart
and no "Experimental Settings" warning. Trades in a data pack would bring that warning up on
every load, and the lantern and the rules need a little code, so they're in the mod version instead.

The wood-mod recipes only switch on when that mod is installed, so the mod version is safe with
or without them.

## Requirements

Minecraft 26.3. The mod version runs on Fabric (with Fabric API) or NeoForge; download the file
for your loader.

## Licence

[MIT](LICENSE).

Cozy Core is made by Gabby with help from Claude, an AI assistant. The icon is Gabby's own pixel art: `art/icon.ase` is the source, and `art/icon.png` is it at 512 pixels, each pixel kept sharp. The Hearth Lantern's textures are drawn by `tools/generate-art.js`.
