# Changelog

## 0.7.0: Home comes alive

Home does a few gentle things on its own now, inside a Hearth Lantern's reach.

- **Lights at dusk.** Candles at home light themselves one by one after sunset and go out in the
  early morning. A candle you light during the day stays lit. Game rule `cozy_core:hearth_lights`.
- **Pets come home.** At night, while you're home, your tamed cats and dogs settle by the nearest
  Hearth Lantern instead of following you around; cats curl up. In the morning they follow you
  again. Pets told to sit stay where they are. Game rule `cozy_core:pets_come_home`.
- **A garden that grows.** Now and then a flower that suits the biome grows on open grass at
  home, never more than a few in one patch, so over the days the garden fills in a little. Game
  rule `cozy_core:hearth_garden`.
- **Home reaches further.** A Hearth Lantern now reaches 64 blocks (it was 32), room for a garden,
  a barn and a pond. Worlds you already have keep 32; `/gamerule cozy_core:hearth_lantern_radius 64`
  changes it.

## 0.6.0: Home comforts

Small kindnesses of being at home, inside a Hearth Lantern's reach.

- **Nothing lost at home.** Things you drop at home last an hour instead of five minutes, so a
  build site full of dropped blocks keeps them. The game rule `cozy_core:hearth_item_minutes`
  sets how long (60 in new worlds; 5 is the usual five minutes).
- **Animals are safe at home.** Creeper and ghast blasts, fire and lightning don't hurt your
  animals, pets and villagers at home, and lightning doesn't turn a villager into a witch, a pig
  into a zombified piglin or a mooshroom into the other colour. Players and monsters are hurt as
  usual. It follows `cozy_core:hearths_protect_builds`, like the rest of home's protection.
- **Safe footing at home.** You and your tamed pets take no fall damage at home, so a slip off a
  roof or scaffolding doesn't cost a trip back.
- **Glass comes back at home.** Glass and glass panes of every colour, ice, packed ice, blue ice
  and bookshelves broken at home drop themselves, as with Silk Touch (and ice leaves no water).
- **Re-dye candles.** Eight candles in any colours around a dye make eight of that colour, like
  terracotta and glass. In the data pack too, and "A fresh coat" counts them.

## 0.5.0: Light a hearth. Home is safe.

The Hearth Lantern now makes the ground around it home, and home keeps your build safe.

- **Home is safe.** Within a Hearth Lantern's reach (32 blocks across the ground, at any height,
  as before):
  - no monster spawns naturally (as before), and no phantoms come for you while you're there;
  - creeper and ghast explosions still hurt, but break no blocks. A blast at the edge leaves a
    crater that stops where home begins;
  - fire still burns and goes out, but doesn't spread or burn blocks away;
  - endermen leave blocks alone.

  It's the game rule `cozy_core:hearths_protect_builds` (on in new worlds).
- **The wild stays wild.** In new worlds, creepers, fire and endermen behave as in vanilla away
  from home. The three world-wide switches (`cozy_core:mob_explosions_break_blocks`,
  `cozy_core:endermen_take_blocks`, `cozy_core:fire_burns_blocks`) are still there: turn one off
  to spare blocks everywhere. **Worlds you already have keep their settings**, so a world made
  with an earlier version still spares blocks everywhere.
- **Phantoms leave you alone at home,** instead of being off everywhere. New worlds have vanilla's
  phantoms out in the wild, but none spawn over you inside a Hearth Lantern's reach. Worlds you
  already have keep their setting, and vanilla's `spawn_phantoms` game rule still turns them off
  everywhere.
- **"Home."** Walking into a Hearth Lantern's reach shows "Home" above your hotbar for a moment,
  with a soft fireplace crackle.
- **Name your home.** Rename a Hearth Lantern on an anvil ("Willow Cottage") and walking in
  shows that name instead. Where two named homes overlap, the nearest one's name shows. The
  lantern keeps its name when you pick it up.
- **See where home ends.** While you hold a Hearth Lantern, small flames on the ground mark the
  edge of the homes nearby.
- **Six Hearth Lanterns.** Besides the classic one, there's a Soul Hearth Lantern and a Copper
  Hearth Lantern in each of copper's four colours (they don't weather). Each is made like the
  classic one, from its own lantern and four amethyst shards, and works the same.
- **A new advancement, "Safe and sound":** a creeper explodes at home, and nothing breaks.
- **Fixed:** a broken Hearth Lantern now drops itself (it used to drop nothing), and a pickaxe
  mines it quickly, like a lantern.

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
