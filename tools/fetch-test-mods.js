// Downloads the wood and decoration mods Cozy Core supports (and their libraries) for MC 26.3
// Fabric from Modrinth into a test folder's mods/, checking each file's SHA-512 against Modrinth's.
//   node tools/fetch-test-mods.js "<test folder>"
const fs = require('fs'), path = require('path'), crypto = require('crypto');

const MC = '26.3';
// TerraBlender is a BOP requirement Modrinth's dependency list doesn't show (found 2026-10-13).
// Beautify (Refabricated, the Fabric port) needs Cloth Config. Macaw's mods need only Fabric API.
const PROJECTS = ['biomes-o-plenty', 'glitchcore', 'terrablender', 'ecologics', 'wilder-wild', 'frozenlib', 'promenade',
	'beautify-refabricated', 'cloth-config', 'macaws-roofs', 'macaws-fences-and-walls', 'macaws-stairs', 'macaws-windows',
	'macaws-furniture', 'macaws-doors', 'macaws-trapdoors'];
const UA = { 'User-Agent': 'gabmaybelle/cozy-core test setup' };

(async () => {
	const folder = process.argv[2];
	if (!folder) throw new Error('usage: node tools/fetch-test-mods.js "<test folder>"');
	const mods = path.join(folder, 'mods');
	fs.mkdirSync(mods, { recursive: true });
	for (const slug of PROJECTS) {
		const q = `loaders=${encodeURIComponent('["fabric"]')}&game_versions=${encodeURIComponent(`["${MC}"]`)}`;
		const versions = await (await fetch(`https://api.modrinth.com/v2/project/${slug}/version?${q}`, { headers: UA })).json();
		if (!versions.length) throw new Error(`${slug} has no ${MC} Fabric version`);
		const file = versions[0].files.find(f => f.primary) || versions[0].files[0];
		const out = path.join(mods, file.filename);
		if (fs.existsSync(out)) { console.log(`have ${file.filename}`); continue; }
		const buf = Buffer.from(await (await fetch(file.url, { headers: UA })).arrayBuffer());
		const sha = crypto.createHash('sha512').update(buf).digest('hex');
		if (sha !== file.hashes.sha512) throw new Error(`${file.filename}: SHA-512 mismatch, not saved`);
		fs.writeFileSync(out, buf);
		console.log(`got ${file.filename} (${(buf.length / 1048576).toFixed(1)} MB, SHA-512 ok)`);
	}
})().catch(e => { console.error(e.message); process.exit(1); });
