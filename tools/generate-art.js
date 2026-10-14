// Draws the Hearth Lantern's two textures (the block's, laid out the way vanilla's lantern model
// reads it, and the flat item icon) into common/src/main/resources. Original pixel art: a copper
// frame, a warm amber glow and an amethyst shard at the top, so it reads as its own thing next
// to a plain lantern. Re-run after changing the palettes or the maps below.
//   node tools/generate-art.js
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const OUT = path.join(__dirname, '..', 'common', 'src', 'main', 'resources', 'assets', 'cozy_core', 'textures');

// One letter per colour; '.' is transparent.
const PALETTE = {
	A: '5a3320', // frame, dark copper
	B: '9a5a34', // frame, copper
	K: '7b4628', // chain
	C: '6b3f8f', // amethyst trim, dark
	D: '55307a', // amethyst trim, darker
	E: '2b1a12', // chain gap
	F: 'd9741f', // glow, deep amber
	G: 'f4a640', // glow, amber
	H: 'ffd57a', // glow, pale
	I: 'fff6d0', // glow, white heart
	J: 'ffe596', // glow, bright
	P: 'b388e6', // amethyst shard, light
	Q: '8a5fc4', // amethyst shard
};

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

function png(rows) {
	const h = rows.length, w = rows[0].length;
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0; // filter: none
		for (let x = 0; x < w; x++) {
			const c = rows[y][x];
			const o = y * (w * 4 + 1) + 1 + x * 4;
			if (c === '.') continue;
			const hex = PALETTE[c];
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

for (const [rel, rows] of [['block/hearth_lantern.png', BLOCK], ['item/hearth_lantern.png', ITEM]]) {
	for (const row of rows) if (row.length !== 16) throw new Error(`${rel}: a row isn't 16 wide: ${row}`);
	const file = path.join(OUT, ...rel.split('/'));
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, png(rows));
	console.log(`wrote ${path.relative(path.join(__dirname, '..'), file)}`);
}
