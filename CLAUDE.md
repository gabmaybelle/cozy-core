# Cozy Core

A data pack for Minecraft 26.3, and on the `1.21.1` branch for 1.21.1 (namespace `cozy_core`) with small building helpers: wood in the
stonecutter at cheap rates (1 plank -> 1 fence, door, sign...; 1 log -> 4), two wooden slabs ->
one plank, re-dyeing terracotta, glass, panes and concrete powder, a few hard-to-get block
recipes, and builder trades (wandering trader, mason). The mod version (Fabric and NeoForge jars
from one codebase) adds the little code a data pack can't: five `cozy_core:` game rules (mob
explosions, endermen and fire spare blocks; the Hearth Lantern's reach; keep your hotbar on
death), phantoms off by default for new worlds, and the Hearth Lantern block, near which no
monster spawns naturally. Nothing runs on a timer: the rules are hooks, the lantern is a block
entity that only reports where it is, and keep-on-death is two hooks around dying and respawning.

`PROGRESS.md` says what's done, what's broken and what's next, with a row per session: read
it first, and add this session's row before ending.

## Branches

- **`public`** is the live branch and the one to push: its history starts fresh with one commit
  (2026-10-13). Work here. It is pushed to the remote's `main` (Minecraft 26.x).
- **`1.21.1`** is Minecraft 1.21.1 (Fabric and NeoForge, Java 21), cut from `main` at 0.3.0 and
  pushed as `1.21.1`; versions there are `<version>+1.21.1`. See "On the 1.21.1 branch" below.
- **`main`** keeps the earlier local history, which has a personal detail in it. Never push `main`.

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
  `ServerExplosionMixin` (creeper/ghast blasts clear their block list; the `cozy_core:explosions_spare_blocks`
  tag says which mobs), `EndermanTakeBlockGoalMixin`, `FireBlockMixin` (ignite and burn odds read
  as 0 while the rule is off, so fire ages out like on stone), `NaturalSpawnerMixin` (the lantern),
  and for keep-on-death two: `DeathLootMixin` (the head of `dropAllDeathLoot` sets the hotbar,
  armour and off-hand aside, before graves mods see the inventory) and `ServerPlayerMixin` (the
  set-aside list lives on the dead player, is saved with them, and is handed to the new player at
  the end of `restoreFrom`); the logic is `KeepOnDeath.java`. Vanilla's `keep_inventory` wins when on.
  `GameRuleAccessor` flips vanilla `spawn_phantoms`' default to false; saved worlds keep their value.
- **The Hearth Lantern** (`HearthLantern*.java`): a `LanternBlock` with a block entity that does
  nothing but tell `HearthLanterns` (an in-memory set per `ServerLevel`) when it's in a loaded chunk
  (`clearRemoved`) and when it isn't (`setRemoved`). The chunk is the only record of where lanterns
  are: no saved data (26.3's `SavedDataType` needs a non-null `DataFixTypes`, and none fits a mod).
  `protects(level, pos)` is a horizontal-distance check against that set, any height.
- `art/icon.ase`: Gabby's own 32x32 sprite; `art/icon.png` is it at 512x512 (nearest-neighbour).
  The build puts it in the zip as `pack.png` and Gradle puts it in the jars as `icon.png` (Fabric
  `icon`, NeoForge `logoFile`). Never replace it with generated art; re-export from the .ase.
  `tools/generate-art.js` draws the Hearth Lantern's block and item textures (original pixel art,
  laid out like vanilla's lantern texture so the vanilla lantern models can be the parents).
- **The advancements tab** (`cozy_core:cozy/...`, Gabby's picks and wording, 2026-10-17) is made by
  `tools/generate.js` (section 6b): a root that opens on picking up planks and six steps. Each
  title and description has its English as the `fallback`, so the data pack reads properly with no
  language file; the generator stops if `en_us.json` doesn't say the same. On 1.21.1 a
  `recipe_crafted` criterion names one `recipe_id`, so "Plank to porch" and "A fresh coat" have a
  criterion per recipe and need any one; the background is a texture path. The Hearth Lantern step
  and the two trade steps are mod-only (`MOD_ONLY` in `tools/build.js`), and the zip's root shows a
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
  per wood-mod group), about three minutes. `RulesTest` checks each rule both ways (kind, then
  switched to vanilla with its game rule), the Hearth Lantern (placed, listed, guarding a dark
  room 10 blocks away, not from 40, off at radius 0, hanging above the room), keep-on-death (a
  real death and respawn in survival, both ways) and takes the
  `hearth_lantern` screenshot; `StonecutterTest` clicks every stonecutter recipe in the real screen.
  A proof run with the mixins emptied out of `cozy_core.mixins.json` fails the kind-side checks.
- The NeoForge game test: `./gradlew :neoforge:runGameTestServer` (about a minute, no window).
  `neoforge/src/gametest` is a mod of its own (`cozy_core_gametest`, loaded only by that run) that
  registers one vanilla test instance per check through `RegisterGameTestsEvent`, with the test
  functions in the `TEST_FUNCTION` registry (`RegisterEvent`). Two environments: `kind` (a new
  world's defaults) and `vanilla` (the rules set to vanilla's behaviour), so each rule is checked
  both ways; the lantern, explosion, fire and keep-on-death checks are the Fabric test's, rewritten on
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

## On the 1.21.1 branch

What differs from 26.3, and why (2026-10-17):

- **Toolchain.** Loom `fabric-loom` with `loom.officialMojangMappings()` and `mod*` configurations
  (1.21.1 is obfuscated), Java 21, MDG 2.0.148 with NeoForge 21.1.262. `common/` has a compile-only
  access widener (`BlockEntityType$BlockEntitySupplier`): Fabric API and NeoForge's access
  transformer open it at run time, so neither jar ships it. No refmap file appears in the Fabric
  jar (Loom remaps the mixin annotations in place). Jars: `cozy-core-<version>-fabric.jar` and
  `-neoforge.jar`, as on 26.3.
- **Rules** are 1.21.1 `GameRules.Key`s, registered through `Platform`: Fabric API's
  `GameRuleRegistry` under a `CustomGameRuleCategory` (the "Cozy Core" heading, bounded int rule);
  NeoForge's plain `GameRules.register` under vanilla categories (MOBS, UPDATES, SPAWNING, PLAYER),
  since NeoForge can't add a heading, and with no bounds (`Rules.get` clamps the radius to 0..128).
  Ids stay `cozy_core:<name>`, so commands and docs match 26.3; lang keys are `gamerule.cozy_core:<name>`.
  Phantoms are vanilla's `doInsomnia`. Until 0.4.0 `GameRulesAccessor`/`BooleanValueInvoker`
  made it start false; since 0.5.0 it's vanilla's default and phantoms only skip players at home.
  `GameRules.createTag` writes every rule, so worlds made before keep their saved values.
- **Explosions:** `ExplosionMixin` trims `Explosion.getToBlow()` at the head of `finalizeExplosion`
  (server side only: the blocks at home, or all of them with the world-wide switch off); the server
  sends players that list, and the explosion's fire uses it too. Lithium replaces `explode()`, not this.
- **The Hearth (0.5.0)** is 26.3's, with: classic and soul styles only (no copper lanterns before
  1.21.9); `CompoundTag` saving and 1.21.1's component hooks in the block entity; loot `functions`
  with `copy_components`; the fire hooks' two shapes (vanilla and NeoForge's `+Direction`) as on
  26.3. **Phantoms are a `@ModifyVariable` here, not 26.3's `@Redirect`:** Loom remaps mixin
  annotations in place on 1.21.1 and writes a redirect's single `at` as a list, which MixinExtras'
  redirect handling in Fabric Loader can't read, so the Fabric server crashed loading
  `PhantomSpawner` (2026-10-19; NeoForge, unremapped, was fine). The hook moves the spot vanilla
  checks for a player at home to the bottom of the world, so its sky check says no. Keep to
  `@Inject` and `@ModifyVariable` on this branch.
- **Home comforts (0.6.0)** are 26.3's, with: `LivingEntity.hurt(source, amount)` (no level
  argument) for sparing animals; `causeFallDamage(float, ...)`; item extra time saved in the
  `CompoundTag`; 1.21.1's class locations (`animal.Pig`, `animal.horse.Llama`, `npc.Villager`...);
  `getType().is(...)` for the blast-mob tag. All `@Inject`. `hearth_item_minutes` is clamped to
  5..1440 in `Rules.get`, as NeoForge's number rules have no bounds.
- **Home comes alive (0.7.0)** is 26.3's (`HomeLife.java`: candles that light at dusk, pets that
  settle by the hearth, a garden; the reach 64), with: `getDayTime()` for the time of day (26.3
  keeps time in clocks); the garden's flower from the biome's first flower feature, as 1.21.1's
  bone meal picks it (26.3 has bone-meal features); `SpreadingSnowyDirtBlockMixin` (26.3's
  `SpreadingSnowyBlock`); `animal.Cat` and `animal.Wolf`; the three new rules under UPDATES and
  MOBS on NeoForge. All `@Inject`. The NeoForge "home_..." batches set the reach to 32, which
  their checks were laid out for; the pet check uses the "lanterns" template so the cat's mind
  ticks.
- **Keep-on-death** saves with `CompoundTag` and the codec through `RegistryOps` (no `ValueOutput` in 1.21.1).
- **Trades are code.** 1.21.1 has no `villager_trade` data, so `Trades.java` lists them and
  `Platform` adds them (Fabric `TradeOfferHelper`, NeoForge `VillagerTradesEvent`/`WandererTradesEvent`);
  the extra slot is `WanderingTraderMixin` (5 -> 6 common offers) and `VillagerMixin` (mason levels
  1 and 2: 2 -> 3). The generator writes no trade files on this branch. With the experimental trade
  rebalance (`trade_rebalance` feature) the wandering trader uses another code path and gets no
  extra slot; the NeoForge test server switches every experiment on, so it skips that count, and
  the Fabric test (an ordinary world) checks it.
- **Data:** the generator reads 1.21.1's long ingredient form (`{item}`/`{tag}`) into the short one
  it works in and writes the long one back (`readRecipe`/`writeRecipe`, every result with a
  `count`); recipe unlocks use `conditions.recipe`; `pack_format` 48. Missing on 1.21.1, so not
  generated: pale oak, poplar and shelves. Compat recipes also carry an item-exists condition for
  their result (`fabric:registry_contains`, `neoforge:item_exists`): Beautify's NeoForge 2.0.2 has
  no cherry blinds or trellises, which its Fabric 3.0.0 has. A log source whose logs another source
  for the same planks already takes in is dropped: Wilder Wild 4.2.1 puts its hollowed logs in the
  log tags too (2 planks by their own recipe, 4 by the tag), which made clashing stonecutter recipes.
- **Wood mods on 1.21.1** (Modrinth, 2026-10-16): Fabric has all of them; NeoForge has BOP,
  Ecologics, Promenade, Beautify and Macaw's, but no Wilder Wild. NeoForge groups: A BOP; B
  Ecologics and Promenade; C Beautify (its own loot tables log errors, ignored in the load
  test); D Macaw's.
- **Tests.** The Fabric game test runs on its own harness (`fabric/src/gametest/.../harness`, the
  same as Spawn Gazebo's and Pocket List's 1.21.1 branches: Fabric has no client game test API on
  1.21.1), started by a `client` entrypoint when `cozy_core.gametest` is set; the tests are the
  `cozy_core:gametest` entrypoint. `RulesTest` also counts the trades. The NeoForge game test uses
  1.21.1's annotation tests (`@GameTestHolder`, `@GameTest`, batches with `@BeforeBatch`/`@AfterBatch`
  instead of 26.3's test environments: `kind`, `everywhere`, `vanilla`, `hearths_off` and one
  `home_...` batch per check that places lanterns, as 26.3 has environments); vanilla's test server
  switches `doFireTick` off, so every batch switches it on (the 1.21.1 side of 26.3's fire-radius gotcha).
  Templates are size-only `.nbt` files with palette key `Name` and data version 3955 (26.x's `id`
  loads as air), plus an `empty` one. 1.21.1 logs "No data fixer registered for" a mod's mobs and
  boats; `server-test.js` ignores it.

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

**Minecraft.** Fabric on Minecraft 26.3 (Mojang names; Loom 1.18.4, Java 25); the `1.21.1` branch is 1.21.1 (Java 21, see above). Vanilla names
changed a lot in 26.x: check the merged jar rather than older docs (for example `iron_chain`,
`pink_petals[flower_amount=N]`, timelines instead of `time query daytime`).
