# Cozy Core

*Light a hearth. Home is safe.*

Light a Hearth Lantern and the ground around it becomes home. A creeper blows up beside your house and not a block breaks. No monsters spawn in the yard, fire can't creep into the walls, and endermen leave your roof alone. Out in the wild, Minecraft is as it always was.

Cozy Core also takes the grind out of decorating, with wood in the stonecutter, re-dyeing and a few hard-to-find blocks. There's nothing new to learn and nothing running in the background.

This is the version for **Minecraft 1.21.1**, on Fabric and NeoForge. It's the same Cozy Core as on 26.3; the few small differences are under [On 1.21.1](#on-1211).

## Two ways to get it

- **The mod** (Fabric with [Fabric API](https://modrinth.com/mod/fabric-api), or NeoForge): everything below.
- **The data pack** (`.zip`): only the recipes, for a world on plain vanilla. Drop it in the world's `datapacks` folder and type `/reload`.

## Light a hearth

**The Hearth Lantern.** Make it from a lantern and four amethyst shards. Everything within 64 blocks of it, at any height, is home. There's the classic one and a soul one, made from a soul lantern, which works the same.

**Home is safe.** Inside a Hearth Lantern's reach:

- no monster spawns naturally and phantoms don't come for you (spawners and raids work as usual);
- creeper and ghast explosions still hurt, but break no blocks, so a blast at the edge leaves a crater that stops where home begins;
- fire still burns and goes out, but doesn't spread or burn blocks away;
- endermen leave your blocks alone;
- blasts, fire and lightning don't hurt your animals, pets or villagers, and lightning doesn't turn a villager into a witch, a pig into a zombified piglin or a mooshroom into the other colour;
- you and your tamed pets take no fall damage, so a slip off the roof doesn't cost a trip back;
- things you drop last an hour instead of five minutes;
- glass and panes of every colour, ice, packed ice, blue ice and bookshelves drop themselves when you break them, as with Silk Touch (and ice leaves no water).

**Welcome home.** Walking in shows "Home" above your hotbar, with a soft fireplace crackle. Rename a lantern on an anvil ("Willow Cottage") and you'll see that name instead; where two named homes overlap, the nearest one's name shows. While you hold a Hearth Lantern, small flames on the ground show where home ends.

## Build with less grind

**Wood in the stonecutter.** One plank makes one set of stairs, a fence, a gate, a door, a trapdoor, a button, a pressure plate or a sign, or two slabs. A log makes four times as much. Logs also cut into wood and stripped wood, and bamboo works too.

**Slabs back into planks.** Two wooden slabs make one plank again.

**Re-dyeing.** Eight blocks of terracotta, stained glass, glass panes, concrete powder or candles around a dye, in any colours, make eight of the dye's colour.

**A few hard-to-find blocks to craft:**

| Recipe | Makes |
|---|---|
| Six sticks in two columns with a string between them at the top | 3 scaffolding |
| Eight sand around a red dye | 8 red sand |
| A vine and a glow ink sac | 4 glow lichen |
| Any leaves and bone meal | A moss block |

**Traders with building blocks.** The wandering trader can sell azaleas, fungus and calcite. Masons sell tuff, mud bricks, calcite and packed mud.

## Also in Cozy Core

**Keep your hotbar when you die.** Your hotbar, armour and off-hand stay with you. Everything else drops where you fell (into a grave, with a graves mod), so there's still a trip back.

**A Cozy Core advancements tab** that opens when you pick up planks, with seven steps along the way. One of them is "Safe and sound": a creeper explodes at home, and nothing breaks. There are no rewards. The data pack has the steps that don't need the mod.

The hearth, the trades and keeping your hotbar are in the mod only, since they need a little code. New recipes show up in your recipe book as you pick up what they need.

## Changing the rules

Each rule is an ordinary game rule: it shows in the Game Rules screen when you make a world, and `/gamerule` changes it any time.

| Game rule | New worlds |
|---|---|
| `cozy_core:hearths_protect_builds` | true (blasts, fire and endermen spare blocks at home, and animals are safe there) |
| `cozy_core:hearth_lantern_radius` | 64 (how far home reaches; 0 switches lanterns off) |
| `cozy_core:hearth_item_minutes` | 60 (how long dropped items last at home; 5 is the usual five minutes) |
| `cozy_core:hearth_lights` | true (candles at home light after sunset and go out in the early morning) |
| `cozy_core:hearth_garden` | true (now and then a flower grows on open grass at home) |
| `cozy_core:pets_come_home` | true (at night, your cats and dogs settle by the hearth while you're home) |
| `cozy_core:keep_hotbar_on_death` | true |
| `cozy_core:mob_explosions_break_blocks` | true (false: explosions break nothing anywhere) |
| `cozy_core:endermen_take_blocks` | true (false: endermen take nothing anywhere) |
| `cozy_core:fire_burns_blocks` | true (false: fire burns nothing anywhere) |
| `doInsomnia` (vanilla's own phantoms rule) | true (phantoms don't come for you at home either way; false turns them off everywhere) |

Want the whole world kept safe? Turn off the three "anywhere" rules. Worlds you already have keep the settings they were made with.

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
