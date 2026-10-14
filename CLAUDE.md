# Cozy Core

A data pack for Minecraft 26.3 (namespace `cozy_core`) with small building helpers: wood in the
stonecutter at cheap rates (1 plank -> 1 fence, door, sign...; 1 log -> 4), two wooden slabs ->
one plank, re-dyeing terracotta, glass, panes and concrete powder, a few hard-to-get block
recipes, and builder trades (wandering trader, mason). Nothing runs on a timer:
there are no functions, only recipes, tags, trades and recipe-book advancements.

## Branches

- **`public`** is the live branch and the one to push: its history starts fresh with one commit
  (2026-10-13). Work here.
- **`main`** keeps the earlier local history, which has a personal detail in it. Never push `main`.

## Layout

- `tools/generate.js` writes everything in `pack/` from the vanilla 26.3 data (and the wood
  mods' jars), then checks for recipe clashes and block-making loops. **Don't edit `pack/` by
  hand**: change the generator and re-run it.
- `tools/build.js` regenerates `pack/` and builds two downloads: `dist/cozy-core-<version>.zip`
  (the data pack: recipes only, vanilla wood) and `dist/cozy-core-<version>-fabric-neoforge.jar`
  (everything as a code-free Fabric/NeoForge mod: recipes, trades and `compat/`). The jar requires
  Fabric API on Fabric; its neoforge.mods.toml has no modLoader/loaderVersion (NeoForge's advice
  for data-only mods; "lowcodefml" is deprecated). The build stops if a mod-only file would reach the zip.
- `art/icon.ase`: Gabby's own 32x32 sprite; `art/icon.png` is it at 512x512 (nearest-neighbour).
  The build puts it in the zip as `pack.png` and in the jar as `icon.png` (Fabric `icon`, NeoForge
  `logoFile`). Never replace it with generated art; re-export from the .ase.
- `tools/fetch-test-mods.js "<test folder>"` downloads the supported wood mods (SHA-512 checked).
- `tools/server-test.js` starts a dedicated server with the pack and prints errors.
- `tools/crafter-test.js "<test folder>" <player> [mod ids]` crafts every crafting recipe with
  crafter blocks in a running test game (bridge with commands on) and checks the output.
- `tools/trade-test.js "<test folder>" <n> wandering_trader | villager <profession> <level>`
  opens merchants through the bridge and counts their offers.
- `pack/data/cozy_core/recipe/compat/<modid>/`: wood-mod recipes, each with Fabric and NeoForge
  load conditions.

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
- The stonecutter screen: `gametest/` (`./gradlew runClientGameTest -Pexpect=../pack`, plus
  `-PwoodMods=<folder>` per wood-mod group) clicks every stonecutter recipe in the real screen.
- **CI** (`.github/workflows/build.yml`) runs all of it on every push: the build (vanilla jar from
  Mojang, wood mods from Modrinth) and a check that the committed `pack/` is what the generator
  makes; `tools/ci-load-test.sh` on Fabric, NeoForge (each alone and with wood-mod groups A and B)
  and plain vanilla with the zip; and the stonecutter test alone and with each group.
  `node tools/build.js <vanilla server jar> <wood mods folder>` builds from given inputs.

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
- `node tools/build.js` and the server load test must pass before
  a commit. The game test opens a real game window for a minute or two.
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
