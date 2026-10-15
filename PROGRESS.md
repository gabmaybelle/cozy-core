# Progress

What's done, what's broken, what's next. Newest entry first. Each session adds a row before it
ends, saying what changed, what the game test counted, what the proof run was (the feature off,
which checks fail, code put back), what was learned, and what was deliberately not done.

## Where things stand

- **Done:** Cozy Core 0.2.0: the recipes and trades that take the grind out of decorating, four kind rules so builds stay built, and the Hearth Lantern (no natural monster spawns within 32 blocks), as a Fabric and a NeoForge mod plus the plain data pack; CI builds all of it and runs load tests on Fabric, NeoForge and vanilla, the stonecutter screen check, and the NeoForge game test. The live branch is `public`, pushed to the remote `public` as `main`; `main` is old local history and is never pushed.
- **Broken:** Nothing known as of this writing (not re-verified).
- **Next:** Whatever the Espresso Flavoured pack asks of it next; it took over from Espresso Flavoured Core there on 2026-10-14.

## Log

| Date | Session | What happened |
| --- | --- | --- |
| 2026-10-15 | Notes | `PROGRESS.md` added, with the rows below reconstructed from the commit history (one row per day, the commits' own words); the two shared rules (commits one at a time by hand, proof runs for data changes) added to `CLAUDE.md`. No code change. |
| 2026-10-14 | (from history) | Take the game-watch hook back out of the server test; The server test says why a server didn't start; The NeoForge version is now tested in a real game too; The Hearth Lantern and four kind rules, as a Fabric and a NeoForge mod; Say what the in-game test scripts need; Beautify's blinds and trellises at the same rate as vanilla's wood. |
| 2026-10-13 | (from history) | Beautify's blinds and trellises in the stonecutter, at the wood they cost; CI load tests: match FrozenLib's config messages on Fabric too; CI load tests: use beta wood mods when there's no release yet, and skip FrozenLib's harmless config errors on Fabric; Fix the fresh build in CI; NeoForge tests without a copied tool; CI: build, load tests on Fabric, NeoForge and vanilla, and the stonecutter screen; An icon: Gabby's own pixel art; Cozy Core 0.1.0: put the kettle on and build. |
