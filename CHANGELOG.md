# Changelog

## 0.4.0

- **A Cozy Core advancements tab.** It opens when you pick up planks, with six steps: craft a
  Hearth Lantern, cut wood in a stonecutter, re-dye blocks, craft moss or glow lichen, and buy
  from the wandering trader and a mason. No rewards. The data pack has the three steps that
  don't need the mod.

## 0.3.0

- **Keep your hotbar on death** (`cozy_core:keep_hotbar_on_death`, on in new worlds). Your hotbar,
  armour and off-hand stay with you when you die, so a death isn't a trip back for your tools;
  everything else drops where you fell (into a grave, with a graves mod). Curse of Vanishing
  still vanishes, vanilla's `keepInventory` still keeps everything when it's on, and logging out
  while dead loses nothing.

## 0.2.0

The mod version grows a little code, for the things a data pack can't do. The data pack is the
same recipes as before.

- **The Hearth Lantern.** A lantern with four amethyst shards around it makes one. No monster
  spawns naturally within 32 blocks of it, at any height, so a build site or a village stays
  quiet at night without a single torch line. It hangs, stands and shines like a lantern. The
  reach is the game rule `cozy_core:hearth_lantern_radius` (0 switches the lanterns off).
  Spawners, raids and patrols aren't affected.
- **Kind rules, on by default in new worlds.** Each is an ordinary game rule, so it shows in the
  Game Rules screen when you make a world and can be set with `/gamerule` any time:
  - creeper and ghast explosions still hurt, but break nothing (`cozy_core:mob_explosions_break_blocks`);
  - endermen leave your blocks where they are (`cozy_core:endermen_take_blocks`);
  - fire still appears and goes out, but never catches a block alight or burns one away, so a
    lightning strike on a wooden roof is harmless (`cozy_core:fire_burns_blocks`);
  - phantoms are off for new worlds (vanilla's own `spawn_phantoms`; worlds you already have keep
    their setting).
- In the mod version: Beautify's blinds and trellises go in the stonecutter at the same rate as
  vanilla's wooden pieces: one per plank, four per log.
- In the mod version: Macaw's Roofs, Fences and Walls, Stairs, Windows, Furniture, Doors and
  Trapdoors too, for every piece made only of one wood: one per plank and four per log for pieces
  worth up to a log, and one per log for bigger ones like desks and big gates.
- The mod is now two files, one for Fabric and one for NeoForge, instead of one for both.

## 0.1.0

The first release.

- Wood in the stonecutter: one plank makes one of each wooden building block (or two slabs), one
  log makes four times as much, logs turn into wood and stripped logs into shelves. Bamboo and bamboo mosaic too.
- Two wooden slabs make one plank again.
- Re-dye terracotta, stained glass, stained glass panes and concrete powder, eight at a time.
- Craft scaffolding from sticks and string, red sand, glow lichen and moss blocks.
- In the mod version: the wandering trader can bring azaleas, crimson and warped fungus and
  calcite, and masons sell tuff, mud bricks, calcite and packed mud, each with an extra offer
  so their own trades are never pushed out.
- The mod version also covers the wood from Biomes O' Plenty, Ecologics, Promenade and Wilder
  Wild, whenever they're installed.
- Two downloads: a recipes-only data pack that can be added to a world mid-game with no warning,
  and a Fabric/NeoForge mod with everything.
