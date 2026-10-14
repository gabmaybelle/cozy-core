// Writes the empty structure templates the NeoForge game tests are laid out in:
//   neoforge/src/gametest/resources/data/cozy_core_gametest/structure/<name>.nbt
// Vanilla's test framework clears a box the size of a test's template and runs the test inside
// it, so each template is just a size (in the shape of vanilla's own data/minecraft/structure/
// empty.nbt: one air block and the size); the tests in neoforge/src/gametest build what they need
// in that box themselves. Run this again after changing a size in SIZES, and commit the files.
//   node tools/generate-test-structures.js
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const OUT = path.join(__dirname, '..', 'neoforge', 'src', 'gametest', 'resources', 'data', 'cozy_core_gametest', 'structure');
// [width (x), height (y), depth (z)]. RuleChecks.java says where things go inside each.
const SIZES = {
	explosions: [9, 7, 9],
	fire: [9, 5, 9],
	lanterns: [48, 10, 13],
};
const DATA_VERSION = 5023; // Minecraft 26.3, as in vanilla's empty.nbt

// A small NBT writer: just the tags a structure template uses.
const END = 0, INT = 3, STRING = 8, LIST = 9, COMPOUND = 10;
const u16 = n => { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; };
const i32 = n => { const b = Buffer.alloc(4); b.writeInt32BE(n); return b; };
const str = s => { const b = Buffer.from(s, 'utf8'); return Buffer.concat([u16(b.length), b]); };
const tag = (type, name, payload) => Buffer.concat([Buffer.from([type]), str(name), payload]);
const compound = (...entries) => Buffer.concat([...entries, Buffer.from([END])]);
const list = (type, items) => Buffer.concat([Buffer.from([type]), i32(items.length), ...items]);
const ints = (name, values) => tag(LIST, name, list(INT, values.map(i32)));

function template([x, y, z]) {
	return tag(COMPOUND, '', compound(
		ints('size', [x, y, z]),
		tag(LIST, 'entities', list(END, [])),
		tag(LIST, 'blocks', list(COMPOUND, [compound(ints('pos', [0, 0, 0]), tag(INT, 'state', i32(0)))])),
		tag(LIST, 'palette', list(COMPOUND, [compound(tag(STRING, 'id', str('minecraft:air')))])),
		tag(INT, 'DataVersion', i32(DATA_VERSION)),
	));
}

module.exports = { template };

if (require.main === module) {
	fs.mkdirSync(OUT, { recursive: true });
	for (const [name, size] of Object.entries(SIZES)) {
		fs.writeFileSync(path.join(OUT, `${name}.nbt`), zlib.gzipSync(template(size)));
		console.log(`${name}.nbt: ${size.join('x')}`);
	}
}
