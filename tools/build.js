// Regenerates pack/ and builds the data pack download:
//   dist/cozy-core-<version>.zip   the data pack: the recipes (vanilla wood only)
// The mods, dist/cozy-core-<version>-fabric.jar and -neoforge.jar, are built by Gradle from the
// same pack/ plus the code in common/ (`./gradlew build` after this, then `node tools/build.js
// --jars` or CI copies them here). Three things are mod-only:
// - compat/ recipes: plain vanilla can't skip them (it doesn't know load conditions) and refuses
//   to load the world.
// - trades (villager_trade, trade_set and their tags): a world data pack with these makes vanilla
//   show "Worlds using Experimental Settings are not supported" every time the world opens (no
//   Realms either), and they need a restart. A mod's data doesn't get that warning (both tested
//   2026-10-13). Without them the zip reloads live with /reload.
// - the rules and the Hearth Lantern: code, so they live in common/ and never touch pack/.
//   node tools/build.js [vanilla 26.3 server jar] [folder of wood-mod jars]
//   node tools/build.js --jars      only copy Gradle's two jars into dist/
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');
const zipDir = require('./zip');

const { VERSION, DATAPACK_DESCRIPTION } = require('./pack-info');
const ROOT = path.join(__dirname, '..');
const PACK = path.join(ROOT, 'pack');
const STAGE = path.join(ROOT, 'build');
const DIST = path.join(ROOT, 'dist');
// The mod's icon (512x512, Gabby's pick). It's the data pack's pack.png and the mods' icon.png.
const ICON = path.join(ROOT, 'art', 'icon.png');

function copyJars() {
	fs.mkdirSync(DIST, { recursive: true });
	let found = 0;
	for (const loader of ['fabric', 'neoforge']) {
		const jar = path.join(ROOT, loader, 'build', 'libs', `cozy-core-${VERSION}-${loader}.jar`);
		if (!fs.existsSync(jar)) continue;
		fs.copyFileSync(jar, path.join(DIST, path.basename(jar)));
		console.log(`Copied dist/${path.basename(jar)} (${(fs.statSync(jar).size / 1024).toFixed(1)} KB)`);
		found++;
	}
	if (!found) console.log('No jars yet: run ./gradlew build first.');
}

if (process.argv[2] === '--jars') {
	copyJars();
	process.exit(0);
}

// Anything after build.js is passed on to generate.js: [vanilla jar] [wood mods folder] (CI uses both).
execFileSync(process.execPath, [path.join(__dirname, 'generate.js'), ...process.argv.slice(2)], { stdio: 'inherit' });
fs.rmSync(STAGE, { recursive: true, force: true });
fs.mkdirSync(DIST, { recursive: true });
// The advancements tab's Hearth Lantern and trade steps are mod-only too (tools/generate.js, section 6b).
const MOD_ONLY = /^data\/(cozy_core\/(recipe|advancement\/recipes)\/compat|cozy_core\/villager_trade|minecraft\/trade_set|minecraft\/tags\/villager_trade|cozy_core\/advancement\/cozy\/(the_kettles_on|safe_and_sound|far_flung_flowers|built_to_last)\.json$)(\/|$)/;
const isModOnly = rel => MOD_ONLY.test(rel.split(path.sep).join('/'));

// The data pack, with its own description (it has no trades).
const datapack = path.join(STAGE, 'datapack');
fs.cpSync(PACK, datapack, { recursive: true, filter: src => !isModOnly(path.relative(PACK, src)) });
const mcmeta = JSON.parse(fs.readFileSync(path.join(datapack, 'pack.mcmeta'), 'utf8'));
mcmeta.pack.description.text = DATAPACK_DESCRIPTION;
fs.writeFileSync(path.join(datapack, 'pack.mcmeta'), JSON.stringify(mcmeta, null, 2) + '\n');
// The data pack has no Hearth Lantern, so its advancements tab shows a vanilla lantern instead.
const tabRoot = path.join(datapack, 'data', 'cozy_core', 'advancement', 'cozy', 'root.json');
const root = JSON.parse(fs.readFileSync(tabRoot, 'utf8'));
root.display.icon.id = 'minecraft:lantern';
fs.writeFileSync(tabRoot, JSON.stringify(root, null, 2) + '\n');
fs.copyFileSync(path.join(ROOT, 'LICENSE'), path.join(datapack, 'LICENSE'));
fs.copyFileSync(ICON, path.join(datapack, 'pack.png'));

// Guard: nothing mod-only may reach the zip.
const leaked = [];
(function walk(d) {
	for (const e of fs.readdirSync(d, { withFileTypes: true })) {
		const f = path.join(d, e.name);
		if (e.isDirectory()) walk(f); else if (isModOnly(path.relative(datapack, f))) leaked.push(f);
	}
})(datapack);
if (leaked.length) throw new Error(`mod-only files in the data pack: ${leaked.slice(0, 3).join(', ')}`);

const out = path.join(DIST, `cozy-core-${VERSION}.zip`);
zipDir(datapack, out);
console.log(`Built dist/cozy-core-${VERSION}.zip (${(fs.statSync(out).size / 1024).toFixed(1)} KB)`);
copyJars();
