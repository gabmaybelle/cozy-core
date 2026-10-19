// Draws the Hearth Lanterns' textures (each block's, laid out the way vanilla's lantern model
// reads it, and the flat item icon) and writes each style's model and data files into
// common/src/main/resources. Original pixel art: a frame, a glow and an amethyst shard at the
// top, so each reads as its own thing next to the plain lantern it's made from. The classic
// Hearth Lantern has a copper-coloured frame and an amber glow; the others take their frame and
// flame colours from the vanilla lantern they're made from (Gabby's pick, 2026-10-19: soul, and
// copper in its four colours, which never weather). Re-run after changing the palettes, the
// maps or the styles below.
//   node tools/generate-art.js
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const RES = path.join(__dirname, '..', 'common', 'src', 'main', 'resources');
const ASSETS = path.join(RES, 'assets', 'cozy_core');
const DATA = path.join(RES, 'data', 'cozy_core');

// One letter per colour; '.' is transparent. These are the classic Hearth Lantern's.
const PALETTE = {
	A: '5a3320', // frame, dark
	B: '9a5a34', // frame
	K: '7b4628', // chain
	C: '6b3f8f', // amethyst trim, dark
	D: '55307a', // amethyst trim, darker
	E: '2b1a12', // chain gap
	F: 'd9741f', // glow, deep
	G: 'f4a640', // glow
	H: 'ffd57a', // glow, pale
	I: 'fff6d0', // glow, white heart
	J: 'ffe596', // glow, bright
	P: 'b388e6', // amethyst shard, light
	Q: '8a5fc4', // amethyst shard
};

// The copper lanterns' green flame (vanilla's copper torch colours).
const COPPER_GLOW = { F: '46824d', G: '69b26e', H: '99eb99', I: 'ffffff', J: 'dcf8dc' };

// Each style: its id, the vanilla lantern(s) it's made from, its light level (the same as that
// lantern's) and the colours that differ from the classic palette. The block code reads the ids
// and light levels from HearthLantern.java's STYLES, which must say the same.
const STYLES = [
	{ id: 'hearth_lantern', from: ['minecraft:lantern'], unlock: 'minecraft:amethyst_shard', colours: {} },
	{ id: 'soul_hearth_lantern', from: ['minecraft:soul_lantern'],
		colours: { A: '252c3d', B: '495065', K: '3e4453', E: '151a26', F: '03969a', G: '23c0c6', H: '6dedf1', I: 'f4faff', J: 'c4fdff' } },
	{ id: 'copper_hearth_lantern', from: ['minecraft:copper_lantern', 'minecraft:waxed_copper_lantern'],
		colours: { A: '84412b', B: 'c26b4c', K: '9a5038', E: '3a1d12', ...COPPER_GLOW } },
	{ id: 'exposed_copper_hearth_lantern', from: ['minecraft:exposed_copper_lantern', 'minecraft:waxed_exposed_copper_lantern'],
		colours: { A: '665849', B: 'a87762', K: '8f705b', E: '2e271f', ...COPPER_GLOW } },
	{ id: 'weathered_copper_hearth_lantern', from: ['minecraft:weathered_copper_lantern', 'minecraft:waxed_weathered_copper_lantern'],
		colours: { A: '3e5e54', B: '6c975c', K: '497164', E: '1c2a25', ...COPPER_GLOW } },
	{ id: 'oxidized_copper_hearth_lantern', from: ['minecraft:oxidized_copper_lantern', 'minecraft:waxed_oxidized_copper_lantern'],
		colours: { A: '39725b', B: '4c9484', K: '3e816b', E: '1a3329', ...COPPER_GLOW } },
];

// The block texture (16x16). Rows 0..8 are the lantern's sides (6 wide at the left), rows 9..14
// its top and bottom, and the column at x 11..13 the chain, like vanilla's layout.
const BLOCK = [
	'.ABBA...........',
	'.CQQC.......BAA.',
	'ABBBBA......A.E.',
	'DFGGFD......A.E.',
	'CGHHGC......EAE.',
	'CHIJHC..........',
	'CHJIHC......A.E.',
	'DGHHGD......EAE.',
	'ABBBBA..........',
	'AAKKAA..........',
	'AKBBKA......BAA.',
	'KBBBBK......A.E.',
	'KBBBBK..........',
	'AKBBKA..........',
	'AAKKAA..........',
	'................',
];

// The item icon (16x16): the lantern hanging from a short chain, with the shard on top.
const ITEM = [
	'................',
	'........A.......',
	'.......AB.......',
	'.......BA.......',
	'.......A........',
	'......ABBA......',
	'......CPQC......',
	'.....ABBBBA.....',
	'.....DFGGFD.....',
	'.....CGHHGC.....',
	'.....CHIJHC.....',
	'.....CHJIHC.....',
	'.....DGHHGD.....',
	'.....ABBBBA.....',
	'................',
	'................',
];

function png(rows, palette) {
	const h = rows.length, w = rows[0].length;
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0; // filter: none
		for (let x = 0; x < w; x++) {
			const c = rows[y][x];
			const o = y * (w * 4 + 1) + 1 + x * 4;
			if (c === '.') continue;
			const hex = palette[c];
			if (!hex) throw new Error(`no colour for '${c}'`);
			raw[o] = parseInt(hex.slice(0, 2), 16); raw[o + 1] = parseInt(hex.slice(2, 4), 16); raw[o + 2] = parseInt(hex.slice(4, 6), 16); raw[o + 3] = 255;
		}
	}
	const chunk = (type, data) => {
		const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
		const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
		const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body));
		return Buffer.concat([len, body, crc]);
	};
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const crc32 = buf => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

function write(base, rel, content) {
	const file = path.join(base, ...rel.split('/'));
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, typeof content === 'string' || Buffer.isBuffer(content) ? content : JSON.stringify(content, null, 2) + '\n');
	console.log(`wrote ${path.relative(path.join(__dirname, '..'), file)}`);
}

for (const rows of [BLOCK, ITEM]) for (const row of rows) if (row.length !== 16) throw new Error(`a row isn't 16 wide: ${row}`);

for (const style of STYLES) {
	const id = style.id, palette = { ...PALETTE, ...style.colours };
	write(ASSETS, `textures/block/${id}.png`, png(BLOCK, palette));
	write(ASSETS, `textures/item/${id}.png`, png(ITEM, palette));
	write(ASSETS, `models/block/${id}.json`, { parent: 'minecraft:block/template_lantern', textures: { lantern: `cozy_core:block/${id}` } });
	write(ASSETS, `models/block/${id}_hanging.json`, { parent: 'minecraft:block/template_hanging_lantern', textures: { lantern: `cozy_core:block/${id}` } });
	write(ASSETS, `models/item/${id}.json`, { parent: 'minecraft:item/generated', textures: { layer0: `cozy_core:item/${id}` } });
	write(ASSETS, `items/${id}.json`, { model: { type: 'minecraft:model', model: `cozy_core:item/${id}` } });
	write(ASSETS, `blockstates/${id}.json`, { variants: {
		'hanging=false': { model: `cozy_core:block/${id}` },
		'hanging=true': { model: `cozy_core:block/${id}_hanging` },
	} });

	// Made like the classic one: its lantern in the middle, four amethyst shards around it.
	write(DATA, `recipe/${id}.json`, {
		type: 'minecraft:crafting_shaped', category: 'building',
		key: { '#': style.from.length === 1 ? style.from[0] : style.from, X: 'minecraft:amethyst_shard' },
		pattern: [' X ', 'X#X', ' X '],
		result: { id: `cozy_core:${id}` },
	});
	// The classic one unlocks with amethyst (as since 0.1.0); the others with their own lantern,
	// so picking up shards doesn't fill the recipe book with six lanterns at once.
	write(DATA, `advancement/recipes/${id}.json`, {
		parent: 'minecraft:recipes/root',
		criteria: {
			has_items: { conditions: { items: [{ items: style.unlock ?? (style.from.length === 1 ? style.from[0] : style.from) }] }, trigger: 'minecraft:inventory_changed' },
			has_the_recipe: { conditions: { recipes: `cozy_core:${id}` }, trigger: 'minecraft:recipe_unlocked' },
		},
		requirements: [['has_the_recipe', 'has_items']],
		rewards: { recipes: [`cozy_core:${id}`] },
	});
	// Drops itself, keeping the name it was given on an anvil (that name is the home's name).
	write(DATA, `loot_table/blocks/${id}.json`, {
		type: 'minecraft:block',
		pools: [{
			condition: { type: 'minecraft:survives_explosion' },
			entries: [{
				type: 'minecraft:item',
				modifier: { type: 'minecraft:copy_components', include: ['minecraft:custom_name'], source: 'block_entity' },
				name: `cozy_core:${id}`,
			}],
			rolls: 1,
		}],
		random_sequence: `cozy_core:blocks/${id}`,
	});
}

// Every style in one tag: mined fast with a pickaxe like vanilla's lanterns, and the list the
// advancements and the tests read.
const ids = STYLES.map(s => `cozy_core:${s.id}`);
write(DATA, 'tags/block/hearth_lanterns.json', { values: ids });
write(DATA, 'tags/item/hearth_lanterns.json', { values: ids });
write(path.join(RES, 'data', 'minecraft'), 'tags/block/mineable/pickaxe.json', { values: ['#cozy_core:hearth_lanterns'] });
