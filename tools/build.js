// Regenerates pack/ and builds the two downloads:
//   dist/cozy-core-<version>.zip                 the data pack: the recipes (vanilla wood only)
//   dist/cozy-core-<version>-fabric-neoforge.jar the whole pack as a Fabric/NeoForge mod: the
//                                                recipes, the trades and the wood-mod recipes
// Two things are mod-only:
// - compat/ recipes: plain vanilla can't skip them (it doesn't know load conditions) and refuses
//   to load the world.
// - trades (villager_trade, trade_set and their tags): a world data pack with these makes vanilla
//   show "Worlds using Experimental Settings are not supported" every time the world opens (no
//   Realms either), and they need a restart. A mod's data doesn't get that warning (both tested
//   2026-10-13). Without them the zip reloads live with /reload.
//   node tools/build.js [vanilla 26.3 server jar] [folder of wood-mod jars]
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const zipDir = require('./zip');

const { VERSION, MC, DESCRIPTION, DATAPACK_DESCRIPTION } = require('./pack-info');
const ROOT = path.join(__dirname, '..');
const PACK = path.join(ROOT, 'pack');
const STAGE = path.join(ROOT, 'build');
const DIST = path.join(ROOT, 'dist');
// Gabby's own sprite (art/icon.ase, 32x32), exported at 512x512 with each pixel kept sharp.
// It's the data pack's pack.png and the mod's icon.png. Never swap in generated art.
const ICON = path.join(ROOT, 'art', 'icon.png');

// Anything after build.js is passed on to generate.js: [vanilla jar] [wood mods folder] (CI uses both).
execFileSync(process.execPath, [path.join(__dirname, 'generate.js'), ...process.argv.slice(2)], { stdio: 'inherit' });
fs.rmSync(STAGE, { recursive: true, force: true });
fs.mkdirSync(DIST, { recursive: true });
const MOD_ONLY = /^data\/(cozy_core\/(recipe|advancement\/recipes)\/compat|cozy_core\/villager_trade|minecraft\/trade_set|minecraft\/tags\/villager_trade)(\/|$)/;
const isModOnly = rel => MOD_ONLY.test(rel.split(path.sep).join('/'));

// The data pack, with its own description (it has no trades).
const datapack = path.join(STAGE, 'datapack');
fs.cpSync(PACK, datapack, { recursive: true, filter: src => !isModOnly(path.relative(PACK, src)) });
const mcmeta = JSON.parse(fs.readFileSync(path.join(datapack, 'pack.mcmeta'), 'utf8'));
mcmeta.pack.description.text = DATAPACK_DESCRIPTION;
fs.writeFileSync(path.join(datapack, 'pack.mcmeta'), JSON.stringify(mcmeta, null, 2) + '\n');
fs.copyFileSync(path.join(ROOT, 'LICENSE'), path.join(datapack, 'LICENSE'));
fs.copyFileSync(ICON, path.join(datapack, 'pack.png'));

// The mod: the whole pack, plus Fabric and NeoForge metadata. Fabric only skips compat/ recipes
// with Fabric API's resource conditions installed, so the mod requires Fabric API (by that name,
// so a missing-mod message says "Fabric API", not an internal module id).
const mod = path.join(STAGE, 'mod');
fs.cpSync(PACK, mod, { recursive: true });
fs.copyFileSync(path.join(ROOT, 'LICENSE'), path.join(mod, 'LICENSE'));
fs.copyFileSync(ICON, path.join(mod, 'icon.png'));
fs.writeFileSync(path.join(mod, 'fabric.mod.json'), JSON.stringify({
	schemaVersion: 1, id: 'cozy_core', version: VERSION, name: 'Cozy Core', description: DESCRIPTION,
	authors: ['Gabby'], license: 'MIT', icon: 'icon.png', environment: '*',
	depends: { minecraft: `~${MC}`, fabricloader: '>=0.16.0', 'fabric-api': '*' },
}, null, 2) + '\n');
fs.mkdirSync(path.join(mod, 'META-INF'), { recursive: true });
// A data-only mod: no modLoader or loaderVersion. NeoForge 26.3 warns that the old "lowcodefml"
// loader is deprecated and says to leave both lines out (tested: it loads with no warning).
fs.writeFileSync(path.join(mod, 'META-INF', 'neoforge.mods.toml'), `license = "MIT"

[[mods]]
modId = "cozy_core"
version = "${VERSION}"
displayName = "Cozy Core"
logoFile = "icon.png"
authors = "Gabby"
description = "${DESCRIPTION}"

[[dependencies.cozy_core]]
modId = "minecraft"
type = "required"
versionRange = "[${MC},${MC}.99]"
ordering = "NONE"
side = "BOTH"
`);

// Guard: nothing mod-only may reach the zip.
const leaked = [];
(function walk(d) {
	for (const e of fs.readdirSync(d, { withFileTypes: true })) {
		const f = path.join(d, e.name);
		if (e.isDirectory()) walk(f); else if (isModOnly(path.relative(datapack, f))) leaked.push(f);
	}
})(datapack);
if (leaked.length) throw new Error(`mod-only files in the data pack: ${leaked.slice(0, 3).join(', ')}`);

for (const [dir, file] of [[datapack, `cozy-core-${VERSION}.zip`], [mod, `cozy-core-${VERSION}-fabric-neoforge.jar`]]) {
	const out = path.join(DIST, file);
	zipDir(dir, out);
	console.log(`Built dist/${file} (${(fs.statSync(out).size / 1024).toFixed(1)} KB)`);
}
