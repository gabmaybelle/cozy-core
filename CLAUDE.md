# Cozy Core

A data pack for Minecraft 26.3 (namespace `cozy_core`) with small building helpers: wood in the
stonecutter at cheap rates (1 plank -> 1 fence, door, sign...; 1 log -> 4), two wooden slabs ->
one plank, re-dyeing terracotta, glass, panes and concrete powder, a few hard-to-get block
recipes, and builder trades (wandering trader, mason). The mod version (Fabric and NeoForge jars
from one codebase) adds the little code a data pack can't: four `cozy_core:` game rules (mob
explosions, endermen and fire spare blocks; the Hearth Lantern's reach), phantoms off by default
for new worlds, and the Hearth Lantern block, near which no monster spawns naturally. Nothing
runs on a timer: the rules are hooks, and the lantern is a block entity that only reports where
it is.

## Branches

- **`public`** is the live branch and the one to push: its history starts fresh with one commit
  (2026-10-13). Work here.
- **`main`** keeps the earlier local history, which has a personal detail in it. Never push `main`.

## Layout

- `tools/generate.js` writes everything in `pack/` from the vanilla 26.3 data (and the wood
  mods' jars), then checks for recipe clashes and block-making loops. **Don't edit `pack/` by
  hand**: change the generator and re-run it.
- `tools/build.js` regenerates `pack/` and builds the data pack, `dist/cozy-core-<version>.zip`
  (recipes only, vanilla wood). The build stops if a mod-only file would reach the zip.
- **The mods are Gradle's.** `./gradlew build` makes `fabric/build/libs/cozy-core-<version>-fabric.jar`
  and `neoforge/build/libs/cozy-core-<version>-neoforge.jar`; `node tools/build.js --jars` copies
  them into `dist/`. Layout (the same as Beyond the Dragon's): `common/` holds the code, mixins,
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
  as 0 while the rule is off, so fire ages out like on stone), `NaturalSpawnerMixin` (the lantern).
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
- `tools/pack-info.js`: the Minecraft version and descriptions the generator and the build share;
  the version comes from `gradle.properties`. `tools/group-jars.sh`: the wood-mod test groups
  (A, B, C), used by `ci-load-test.sh` and the workflow, so they're defined once.
- `tools/fetch-test-mods.js "<test folder>"` downloads the supported wood and decoration mods
  (SHA-512 checked).
- `tools/server-test.js` starts a dedicated server with the pack and prints errors.
- `tools/crafter-test.js "<test folder>" <player> [mod ids]` crafts every crafting recipe with
  crafter blocks in a running test game (bridge with commands on) and checks the output.
- `tools/trade-test.js "<test folder>" <n> wandering_trader | villager <profession> <level>`
  opens merchants through the bridge and counts their offers.
  Both talk to the bridge through `mcf.js` from Gabby's local toolkit (`~/mods-dev/tools`, not in
  this repo; `MCF` points them elsewhere), so they're for her machine, not CI.
- `pack/data/cozy_core/recipe/compat/<modid>/`: wood-mod recipes, each with Fabric and NeoForge
  load conditions. Decoration mods (`DECOR_MODS`: Beautify; Gabby's pick 2026-10-13) get the same
  cheap rate as vanilla's pieces (`decorCutting`): a piece made of one vanilla wood (planks, slabs,
  logs, sticks) is one per plank and four per log, or its own rate per plank if that's cheaper.
  Pieces needing non-wood items or mixing woods are skipped. They only use vanilla woods, because
  a compat recipe's load condition names one mod. Farmer's Delight was tried and dropped (2026-10-13):
  its cabinets are its only all-wood pieces and the rule was decided per mod, not per piece; add it
  back if Gabby wants its cabinets cheap too. Mods Gabby wants later, once they reach 26.3: Let's Do
  Vinery, Supplementaries, Decorative Blocks, Nature's Spirit, Macaw's (declined for Espresso, so
  ask first).
- Beautify ReFoxed 2.0.0 (the NeoForge port) crashes by itself on NeoForge 26.3 release builds
  (`ModConfig$Type.SERVER`, 2026-10-13), so CI has no NeoForge decoration group until it's fixed.

## Build and test

- `node tools/build.js` must print "No recipe clashes or block-making loops found."
- Server load test: the zip on a plain vanilla server, the jar on a Fabric server with and
  without the wood mods; each must reach "Done" with no `Errors in element` lines.
- In a test game (`new-test-world.js --mcp --commands`): crafter-test and trade-test pass, every
  recipe id unlocks with `recipe give`, and opening the world shows no experimental warning.
  Note `recipe give *` undercounts: unlocking one recipe of a group completes its recipe-book
  advancement, which hands out the rest of the group first.
- NeoForge (and Fabric, and the zip on plain vanilla): `bash tools/ci-load-test.sh neoforge none|A|B`
  installs the newest NeoForge server, adds the jar (and a wood-mod group from Modrinth) and runs
  `tools/server-test.js`, which must end with 0 problem lines. Needs jq and curl (CI has both).
- The game test: `./gradlew :fabric:runClientGameTest -Pexpect=pack` (plus `-PwoodMods=<folder>`
  per wood-mod group), about three minutes. `RulesTest` checks each rule both ways (kind, then
  switched to vanilla with its game rule), the Hearth Lantern (placed, listed, guarding a dark
  room 10 blocks away, not from 40, off at radius 0, hanging above the room) and takes the
  `hearth_lantern` screenshot; `StonecutterTest` clicks every stonecutter recipe in the real screen.
  A proof run with the mixins emptied out of `cozy_core.mixins.json` fails the kind-side checks.
- **CI** (`.github/workflows/build.yml`) runs all of it on every push: the build (vanilla jar from
  Mojang, wood mods from Modrinth, both jars from Gradle) and a check that the committed `pack/`
  is what the generator makes; `tools/ci-load-test.sh` on Fabric (alone and with groups A, B and
  C), NeoForge (alone and with A and B) and plain vanilla with the zip; and the game test alone
  and with each group. `node tools/build.js <vanilla server jar> <wood mods folder>` builds from
  given inputs.

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

## Working on Gabby's mods (the same in every repo)

Gabby makes these mods with help from Claude. Several Claude sessions work on them: one on her
computer and others in the cloud. These rules keep their work fitting together.

**One session per repo at a time.**
- A cloud session works on a `claude/...` branch and never pushes to `main`. Gabby's local
  session reviews the branch, runs its tests and merges it as one commit.
- Start from the latest `main` (`git pull`), and say in the branch's last commit or a short note
  what is finished and what isn't.

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
