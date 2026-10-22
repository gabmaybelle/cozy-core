# Cozy Core

A data pack for Minecraft 26.3 (namespace `cozy_core`) with small building helpers: wood in the
stonecutter at cheap rates (1 plank -> 1 fence, door, sign...; 1 log -> 4), two wooden slabs ->
one plank, re-dyeing terracotta, glass, panes and concrete powder, a few hard-to-get block
recipes, and builder trades (wandering trader, mason). The mod version (Fabric and NeoForge jars
from one codebase) adds the little code a data pack can't: the Hearth Lantern (since 0.5.0 its
centrepiece, "Light a hearth. Home is safe."): inside its reach no monster spawns naturally and
creeper/ghast blasts, fire and endermen leave blocks alone, while the wild stays vanilla; the
`cozy_core:` game rules (hearths protect builds; world-wide switches for mob explosions, endermen
and fire; the lantern's reach, 64 since 0.7.0; how long drops last at home; keep your hotbar on
death; and 0.7.0's lights, garden and pets), and no phantoms over a player at home
(until 0.4.0 phantoms were off by default in new worlds; Gabby's call 2026-10-19). Nothing runs on a timer on the server: the rules are hooks, the lantern is a block entity
that only reports where it is, keep-on-death is two hooks around dying and respawning, and 0.7.0's
home life rides on vanilla's random ticks and a pet goal. The
client has one tick hook (`client/HearthClient`) for the "Home" sign and the edge flames.

**The feel (Gabby, 2026-10-21): slow-paced, peaceful, cute.** Since 0.7.0 every new feature is
tested against those three words before it's built: home should feel like a refuge that comes
alive, not only a place where bad things don't happen.

`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

## Branches

- **`public`** is the live branch and the one to push: its history starts fresh with one commit
  (2026-10-13). Work here.
- **`main`** keeps the earlier local history, which has a personal detail in it. Never push `main`.
- **`1.21.1`** (Fabric + NeoForge) gets ports of new releases.
- **1.20.1 was dropped** (Gabby, 2026-10-19): its releases and branch are gone. Don't port to it
  or bring it back unless she asks.

## Layout

- `tools/generate.js` writes everything in `pack/` from the vanilla 26.3 data (and the wood
  mods' jars), then checks for recipe clashes and block-making loops. **Don't edit `pack/` by
  hand**: change the generator and re-run it.
- `tools/build.js` regenerates `pack/` and builds the data pack, `dist/cozy-core-<version>.zip`
  (recipes only, vanilla wood). The build stops if a mod-only file would reach the zip.
- **The mods are Gradle's.** `./gradlew build` makes `fabric/build/libs/cozy-core-<version>-fabric.jar`
  and `neoforge/build/libs/cozy-core-<version>-neoforge.jar`; `node tools/build.js --jars` copies
  them into `dist/`. Layout: `common/` holds the code, mixins,
  assets and the mod-only data (the lantern recipe and its unlock, the `explosions_spare_blocks`
  entity tag); `fabric/` and `neoforge/` compile `common/`'s sources and `pack/` straight into their
  jars, with their own entrypoint, `Platform` implementation and metadata. Common code reaches the
  loader through `platform/Platform` (`Services.PLATFORM`): registering, creative tabs. The jars
  require Fabric API on Fabric (resource conditions) and NeoForge on NeoForge.
- **Rules are game rules** (`Rules.java`): registered into vanilla's game-rule registry under the
  `cozy_core` category, so they show in the create-world screen, work with `/gamerule`, save with
  the world and need no settings file or sync. Each rule's code is one mixin in `common/.../mixin/`:
  `ServerExplosionMixin` (creeper/ghast blasts drop the blocks at home from their list, or clear it
  with the world-wide switch off; the `cozy_core:explosions_spare_blocks` tag says which mobs; a
  blast centred at home awards "Safe and sound", `SafeAndSound.java`), `EndermanTakeBlockGoalMixin`
  (home reaches 3 blocks further for endermen, which take blocks up to 2 away), `FireBlockMixin`
  (ignite and burn odds read as 0 for a fire at home or with the switch off, so it ages out like on
  stone; plus spot checks so a fire outside can't burn or spread into home; NeoForge adds a
  `Direction` to `checkBurnOut`, so that hook exists in both shapes with `require = 0`, and the
  edge-fire game tests prove each applies), `NaturalSpawnerMixin` (the lantern),
  and for keep-on-death two: `DeathLootMixin` (the head of `dropAllDeathLoot` sets the hotbar,
  armour and off-hand aside, before graves mods see the inventory) and `ServerPlayerMixin` (the
  set-aside list lives on the dead player, is saved with them, and is handed to the new player at
  the end of `restoreFrom`); the logic is `KeepOnDeath.java`. Vanilla's `keep_inventory` wins when on.
  `PhantomSpawnerMixin` passes over a player at home the way vanilla passes over spectators
  (NeoForge keeps that `isSpectator()` call around its phantom event, so one redirect works on
  both). 0.4.0's `GameRuleAccessor` (vanilla `spawn_phantoms` defaulting to false) is gone; worlds
  made before keep their saved value.
- **The Hearth Lantern** (`HearthLantern*.java`): a `LanternBlock` with a block entity that tells
  `HearthLanterns` (an in-memory map per `Level`, on the server and on each client) when it's in a
  loaded chunk (`clearRemoved`) and when it isn't (`setRemoved`). The chunk is the only record of
  where lanterns are: no saved data (26.3's `SavedDataType` needs a non-null `DataFixTypes`, and
  none fits a mod). `protects(level, pos)` (spawning) and `guards(level, pos)` (builds, while
  `hearths_protect_builds` is on) are horizontal-distance checks against that map, any height.
  The block entity keeps the anvil name (the home's name, `CustomName`, copied into the drop by
  the loot table) and sends clients the name plus the server's reach (`cozy_core_reach`, only in
  the update tag), since game rules don't reach clients; a reach change reaches clients when the
  chunk is sent again. Six styles (`HearthLantern.STYLES`: classic, soul, and copper in its four
  colours, never weathering; Gabby's pick 2026-10-19), all one block entity type. Since 0.5.0
  each has a loot table and the `minecraft:mineable/pickaxe` tag (0.4.0's lantern had neither and
  dropped nothing).
- **Home comforts (0.6.0, `HomeComforts.java`):** at home, dropped items last
  `hearth_item_minutes` (`ItemEntityMixin`: vanilla saves an item's age as a short, so an item about
  to go is set back a minute at a time and the extra is kept, and saved, beside it); animals and
  villagers aren't hurt by creeper/ghast blasts, fire or lightning (`LivingEntityMixin`, following
  `hearths_protect_builds`) and lightning doesn't convert them (`ThunderHitMixin`, one mixin on
  `Entity`, `Villager`, `Pig`, `MushroomCow` and `Turtle`, which each have their own `thunderHit`);
  players and tamed pets take no fall damage (`FallDamageMixin` on `LivingEntity`, plus
  `AbstractHorse` and `Llama`, which work out their own); `cozy_core:keeps_at_home` blocks (glass,
  panes, ice, bookshelves) drop themselves (`BlockBehaviourMixin` on `getDrops`; `IceBlockMixin`
  skips the melt). Candles re-dye in the generator's `REDYE` list. Gabby's calls, 2026-10-19:
  every passive animal and pet plus villagers; players and their tamed pets; every glass block and
  pane, ice, packed and blue ice, and bookshelves.
- **Home comes alive (0.7.0, `HomeLife.java`; Gabby's picks, 2026-10-21):** three things home
  does on its own, each with a game rule (on).
  - *Lights at dusk* (`hearth_lights`): `BlockBehaviourMixin` makes every candle and candle cake
    randomly tick (`isRandomlyTicking`, asked once per block state at start-up) and catches their
    tick in `BlockBehaviour.randomTick`'s empty default. At home, an unlit candle lights from
    sunset (12000) to dawn (23000), and a lit one goes out only from dawn to 2000, so a candle lit
    by hand at noon stays lit. The time of day is `getDefaultClockTime() % 24000` (26.3 keeps time
    in clocks).
  - *A garden* (`hearth_garden`): `SpreadingSnowyBlockMixin` adds to grass's own random tick. One
    tick in 40,000 (`GARDEN_CHANCE`), open grass at home with air and sky above grows a flower
    from the biome's bone-meal features, unless its 5x5 patch already has `GARDEN_CAP` (2).
  - *Pets come home* (`pets_come_home`): `PetGoalsMixin` adds `SettleByHearthGoal` to cats and
    wolves at priority 5, above their follow-owner goal (6). From 13000 to 23000, with the owner
    at home and the pet within 48 blocks of the nearest lantern, not sitting, leashed or riding, the
    pet walks to the ground under the lantern (or beside one standing on something) and settles:
    a cat lies down, a dog sits. `HearthLanterns.nearest` finds the lantern.
- `art/icon.png`: the mod's icon, three candles on a wooden board (512x512; Gabby's pick, 2026-10-21,
  the same as the listings'; it replaced the first lantern sprite). The build puts it in the zip as `pack.png` and Gradle puts it in the jars as `icon.png` (Fabric
  `icon`, NeoForge `logoFile`). Change it only when Gabby gives a new one.
  `tools/generate-art.js` draws every Hearth Lantern style's block and item textures (original
  pixel art, laid out like vanilla's lantern texture so the vanilla lantern models can be the
  parents; the classic palette is unchanged since 0.2.0, the others take their frame and flame
  colours from their vanilla lantern) and writes each style's models, recipe, recipe unlock, loot
  table and the `cozy_core:hearth_lanterns` tags into `common/`. Its `STYLES` list must match
  `HearthLantern.STYLES`.
- **The advancements tab** (`cozy_core:cozy/...`, Gabby's picks and wording, 2026-10-17) is made by
  `tools/generate.js` (section 6b): a root that opens on picking up planks and seven steps ("Safe
  and sound", 0.5.0, sits under the Hearth Lantern's step and has an impossible criterion that
  the explosion hook awards). Each
  title and description has its English as the `fallback`, so the data pack reads properly with no
  language file; the generator stops if `en_us.json` doesn't say the same. On 26.x `recipe_crafted`
  takes a list of recipe ids, checked against the generated recipes (and the six Hearth Lantern
  recipes in `common/`). The two Hearth Lantern steps and the two trade steps are mod-only
  (`MOD_ONLY` in `tools/build.js`), and the zip's root shows a
  vanilla lantern. The NeoForge game test's `advancements` check loads every one and every recipe
  it names.
- `tools/pack-info.js`: the Minecraft version and descriptions the generator and the build share;
  the version comes from `gradle.properties`. `tools/group-jars.sh`: the wood-mod test groups
  (A, B wood mods, C Beautify, D Macaw's), used by `ci-load-test.sh` and the workflow, so they're
  defined once. `tools/neoforge-group.sh` does the same for NeoForge (A BOP; B Ecologics, Wilder Wild,
  Promenade; D Macaw's; no C): it downloads a group's jars and, with `--ids`, names its mod ids. It
  needs curl and jq, so it's for CI or a machine that has jq.
- `tools/fetch-test-mods.js "<test folder>"` downloads the supported wood and decoration mods
  (SHA-512 checked).
- `tools/server-test.js` starts a dedicated server with the pack and prints errors.
- `tools/crafter-test.js "<test folder>" <player> [mod ids]` crafts every crafting recipe with
  crafter blocks in a running test game (bridge with commands on) and checks the output.
- `tools/trade-test.js "<test folder>" <n> wandering_trader | villager <profession> <level>`
  opens merchants through the bridge and counts their offers.
  Both need the MCPFabric bridge's command-line client (set `MCF` to its path), so they're for a
  machine with a test game open, not CI.
- `pack/data/cozy_core/recipe/compat/<modid>/`: wood-mod recipes, each with Fabric and NeoForge
  load conditions. Decoration mods (`DECOR_MODS`: Beautify, Gabby's pick 2026-10-13; seven Macaw's
  mods, 2026-10-15, Bridges left out as it has no all-wood piece) get the same cheap rate as
  vanilla's pieces (`decorCutting`): a piece made of one vanilla wood (planks, slabs, logs, sticks)
  is one per plank and four per log, or its own rate per plank if that's cheaper. A piece worth more
  than a log (`BIG_PIECE`, 4 planks: Macaw's desks, big gates, the bigger roofs) is one per log
  only, so nothing is ten times cheaper than crafting it (Gabby's call: the line is a log, not a
  door's 2.5, so Beautify's 3-plank blinds keep their rate). Pieces needing non-wood items or
  mixing woods are skipped. Macaw's Windows saves its JSON with a byte-order mark; `readJson`
  strips it. They only use vanilla woods, because
  a compat recipe's load condition names one mod. Farmer's Delight was tried and dropped (2026-10-13):
  its cabinets are its only all-wood pieces and the rule was decided per mod, not per piece; add it
  back if Gabby wants its cabinets cheap too. Mods Gabby wants later, once they reach 26.3: Let's Do
  Vinery, Supplementaries, Decorative Blocks, Nature's Spirit (none had a 26.3 build on 2026-10-15).
- Beautify ReFoxed 2.0.0 (the NeoForge port) crashes by itself on NeoForge 26.3 release builds
  (`ModConfig$Type.SERVER`, 2026-10-13), so CI has no NeoForge decoration group until it's fixed.

## Build and test

- `node tools/build.js` must print "No recipe clashes or block-making loops found."
- Server load test: the zip on a plain vanilla server, the jar on a Fabric server with and
  without the wood mods; each must reach "Done" with no `Errors in element` lines.
- In a test game with the bridge's commands on: crafter-test and trade-test pass, every
  recipe id unlocks with `recipe give`, and opening the world shows no experimental warning.
  Note `recipe give *` undercounts: unlocking one recipe of a group completes its recipe-book
  advancement, which hands out the rest of the group first.
- NeoForge (and Fabric, and the zip on plain vanilla): `bash tools/ci-load-test.sh neoforge none|A|B|D`
  installs the newest NeoForge server, adds the jar (and a wood-mod group from Modrinth) and runs
  `tools/server-test.js`, which must end with 0 problem lines. Needs jq and curl (CI has both).
- The game test: `./gradlew :fabric:runClientGameTest -Pexpect=pack` (plus `-PwoodMods=<folder>`
  per wood-mod group), about three minutes. `RulesTest` checks each world-wide rule both ways
  (switched off, then at its vanilla default), home (a blast with a lantern 10 blocks away breaks
  nothing, and does with hearths switched off; fire on the edge of home burns only the wild half,
  which proves the vanilla-shaped `checkBurnOut` hook applies), the Hearth Lantern (placed, listed, guarding a dark
  room 10 blocks away, not from 40, off at radius 0, hanging above the room), keep-on-death (a
  real death and respawn in survival, both ways) and takes the
  `hearth_lantern` screenshot; `StonecutterTest` clicks every stonecutter recipe in the real screen.
  A proof run with the mixins emptied out of `cozy_core.mixins.json` fails the kind-side checks.
- The NeoForge game test: `./gradlew :neoforge:runGameTestServer` (about a minute, no window).
  `neoforge/src/gametest` is a mod of its own (`cozy_core_gametest`, loaded only by that run) that
  registers one vanilla test instance per check through `RegisterGameTestsEvent`, with the test
  functions in the `TEST_FUNCTION` registry (`RegisterEvent`). Environments: `kind` (a new world's
  defaults: safe at home, vanilla in the wild), `everywhere` (the world-wide switches off, like
  pre-0.5.0 worlds), `vanilla` (everything at vanilla's behaviour), `hearths_off`, and one `home_...`
  environment per check that places lanterns (`HomeChecks.java`: blasts and fire at home and on the
  edge, endermen, every style's drop, name and sync, phantoms (vanilla's own `PhantomSpawner` run
  100 times at midnight over a sleepless mock player, with and without a lantern), the spawning
  room), 0.6.0's comforts (`ComfortsChecks.java`: items, animals, lightning, falls, glass) and
  0.7.0's home life (`LifeChecks.java`: the 64-block reach in a new world; candles through their
  real random tick at dusk, in the morning, lit by hand at noon, in the wild and with the rule off;
  the garden, at chance 1 and once at play's odds through grass's own random tick, plus the wild,
  the cap and the rule off; a tamed cat at night leaving its owner to lie by the lantern, then
  getting up at noon). The "home_..." environments pin the reach to 32: their checks were laid out
  for it (a spot 45 or 60 blocks out stands in the wild). The item, lightning and pet checks keep
  every spot inside the test's own area and use the "lanterns" template, not the 1-block "empty":
  items only age, a mob only turns into another, and a mob's mind only runs where entities tick,
  which the test server only promises near a test, sized by its template (a forced chunk wasn't
  enough; a cat 10 blocks from an "empty" test never moved, 2026-10-21). **Vanilla runs each
  environment as its own batch, one after another, in the same part of the world**, so a lantern
  or stand-in player left by one check would guard or quiet a later batch's test: every check
  removes them even when it fails (`RuleChecks.orTidy`; learned 2026-10-19, when a failed proof
  run's leftover lantern made three wild checks fail too). The explosion, fire and keep-on-death checks are the Fabric test's, rewritten on
  `GameTestHelper` (keep-on-death on mock server players: die, save, load, `restoreFrom`); `recipes` checks every recipe in `pack/` (`-Pexpect=<dir>`, default `pack/`)
  is in the game, or isn't when its mod isn't loaded. `-PwoodMods=<folder>` adds a group's jars to the
  run and `-PexpectMods=<ids>` (from `neoforge-group.sh <group> --ids`) makes `recipes` fail if one of
  them didn't load, so a group whose jars were skipped can't pass with its recipes "waiting". The templates in
  `neoforge/src/gametest/resources/data/cozy_core_gametest/structure/` are only sizes, made by
  `tools/generate-test-structures.js`; each test builds its own scene. Vanilla's test server
  world has `spawn_mobs` off and no players, and natural spawning wants a player (and the spawn
  point) more than 24 blocks away, so the lantern test adds a mock player high above its room
  and moves the spawn point for its duration. Since 26.3 vanilla fire only ticks within
  `fire_spread_radius_around_player` blocks of a player (128 by default), so both test environments
  set that rule to -1 (everywhere); without it the fire checks only see fire tick while another
  test's mock player exists, and pass or fail by luck (2026-10-15). A failing check exits the run
  non-zero.
- **CI** (`.github/workflows/build.yml`) runs all of it on every push: the build (vanilla jar from
  Mojang, wood mods from Modrinth, both jars from Gradle) and a check that the committed `pack/`
  is what the generator makes; `tools/ci-load-test.sh` on Fabric (alone and with groups A, B, C
  and D), NeoForge (alone and with A, B and D) and plain vanilla with the zip; the game test alone
  and with each group; and the NeoForge game test alone and with groups A, B and D. `node tools/build.js <vanilla server jar> <wood mods folder>` builds from
  given inputs.
- **Each game test removes what it builds** (blocks, lanterns, entities, stand-in players) before it
  succeeds. Tests in a batch share one world, and a run can reuse the last run's world, so anything
  left behind can change another test's result: a Hearth Lantern left standing by the radius-0 check
  once guarded the other lantern check's room (found on 1.20.1, 2026-10-17).

## Gotchas

- **Plain vanilla can't load the compat recipes.** In 26.x recipes are a registry and a recipe
  naming an unknown item stops the world from loading ("Failed to load registries"). Vanilla
  ignores Fabric/NeoForge load conditions. So `compat/` must never ship in the vanilla zip.
- Button recipes are shapeless and some mod fences use `#c:rods/wooden` instead of sticks; the
  generator handles both (both were bugs once).
- Two stacked bamboo slabs are vanilla's bamboo mosaic, so bamboo slabs -> planks is side by side only.
- Biolith (bundled in Wilder Wild and Promenade) crashes with TerraBlender 26.3.0.0.9
  (`SurfaceRuleManager$RuleCategory`), so BOP can't be tested in the same game as those two.
- Git Bash's `unzip` doesn't match `/` with `*`; sub-folders need their own `*/*` pattern.
- **Trades are mod-only.** A world data pack with `villager_trade`/`trade_set` files makes vanilla
  show "Worlds using Experimental Settings are not supported" on every world load (no Realms),
  as the wiki says. The same files inside a mod don't (tested 2026-10-13 with only Fabric API;
  bigger mods like BOP can hide the screen, so test the warning without them). Trades also only
  load when a world opens; the trade tag entries are `required: false` so a missing trade never
  takes vanilla's whole pool down with it. The recipe-only zip reloads live with `/reload`.
- A cartographer badlands map was tried and dropped: maps target structures only, and badlands
  are usually beyond the 100-chunk search.
- Vanilla 26.3 data: trades are data-driven (`villager_trade`, `trade_set`, tags under
  `tags/villager_trade`); the pack format is `min_format`/`max_format` 121.

## This mod's job

**Cozy Core**: the Hearth Lantern, which makes the ground around it home: no monsters or phantoms
spawn there, and creeper and ghast blasts, fire and endermen leave its blocks alone (each an
ordinary game rule, with world-wide switches too); keep your hotbar on death, cheap wood in the
stonecutter, re-dyeing and builder trades. Fabric and NeoForge jars, plus a plain data pack of the
recipes.

Gabby has other mods, each with its own job. Add only features that fit this mod's job. If an
idea doesn't fit, don't add it here or start it in another repo: ask Gabby where it goes.

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

**Commits are made one at a time, by hand.** Never commit or push from a chained script. Run
the test, read its result (and the screenshot, for anything visual), then commit that one
branch, then push it. A cherry-pick that conflicts stops the work until it is resolved by hand.
A worldgen or balance change is tested on every terrain in the branch's matrix before it goes
to any other branch. (Learned on another mod, 2026-10-14, when scripted chains committed
a conflicted cherry-pick, put one branch's files on another, and sent an untested ore change to
all three branches.) For a Gradle game test use a game watcher that reports a verdict (in the world, error screen, crashed or hung),
not a wait loop.

**Commits.**
- Author and committer are always `gabmaybelle <211247938+gabmaybelle@users.noreply.github.com>`:
  `git -c user.name=gabmaybelle -c user.email=211247938+gabmaybelle@users.noreply.github.com commit ...`.
  Never a personal email, and never `Claude <noreply@anthropic.com>` as the author. Claude goes in
  a `Co-Authored-By:` line at the end of the message.
- Ask Gabby before committing or pushing when she's around. Finish the build and the tests first.
- Commit messages say what changed for players, in plain sentences.

**Privacy.** These repos are public. Don't add personal email addresses, real names other than
"Gabby", or anything about her other private projects and modpacks: not in code, docs, commits
or test names. Check the diff for these before every push.

**Tests.**
- `node tools/build.js`, `./gradlew build` and the server load test must pass before a commit.
  The game test opens a real game window for a few minutes.
- A new or changed check should fail with the feature switched off: run it that way once, then
  put the code back. A check that compares against the setting it tests proves nothing.
- A data change (worldgen, loot, a template, a pack's mod list) gets the same proof: run the part
  of the test that sees it, and quote the numbers in the progress row.
- On Linux without a screen, run game tests under `xvfb-run -a`. 26.3 also needs
  `SDL_VIDEO_FORCE_EGL=1` with `libegl1 libegl-mesa0` installed, or the game hangs at startup.

**Writing.**
- README and CHANGELOG are for players: plain, friendly, no jargon. New player-facing changes
  go in the CHANGELOG's newest section.
- Keep the credit line: "made by Gabby with help from Claude, an AI assistant".
- Gabby publishes on Modrinth herself. Don't upload anything there.

**Minecraft.** Fabric on Minecraft 26.3 (Mojang names; Loom 1.18.4, Java 25). Vanilla names
changed a lot in 26.x: check the merged jar rather than older docs (for example `iron_chain`,
`pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
