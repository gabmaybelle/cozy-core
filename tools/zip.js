// Minimal zip writer (deflate, forward-slash paths). Windows PowerShell's Compress-Archive
// writes backslash paths, which Iris and launchers can't read.
//   zipDir(sourceDir, outFile)
const fs = require('fs'), path = require('path'), zlib = require('zlib');

const CRC = new Uint32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c >>> 0; });
const DOS_1980 = 0x00210000; // 1980-01-01 00:00, the earliest valid DOS date (0 means month 0)
const crc32 = buf => { let c = 0xffffffff; for (const b of buf) c = CRC[(c ^ b) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };

function walk(dir, base = '') {
	const out = [];
	for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
		const rel = base ? `${base}/${e.name}` : e.name;
		if (e.isDirectory()) out.push(...walk(path.join(dir, e.name), rel));
		else out.push(rel);
	}
	return out.sort();
}

function zipDir(src, outFile) {
	const parts = [], central = [];
	let offset = 0;
	for (const rel of walk(src)) {
		const data = fs.readFileSync(path.join(src, ...rel.split('/')));
		const comp = zlib.deflateRawSync(data, { level: 9 });
		const name = Buffer.from(rel, 'utf8');
		const crc = crc32(data);
		const local = Buffer.alloc(30);
		local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x0800, 6); local.writeUInt16LE(8, 8);
		local.writeUInt32LE(DOS_1980, 10); local.writeUInt32LE(crc, 14); local.writeUInt32LE(comp.length, 18); local.writeUInt32LE(data.length, 22);
		local.writeUInt16LE(name.length, 26); local.writeUInt16LE(0, 28);
		parts.push(local, name, comp);
		const cen = Buffer.alloc(46);
		cen.writeUInt32LE(0x02014b50, 0); cen.writeUInt16LE(20, 4); cen.writeUInt16LE(20, 6); cen.writeUInt16LE(0x0800, 8); cen.writeUInt16LE(8, 10);
		cen.writeUInt32LE(DOS_1980, 12); cen.writeUInt32LE(crc, 16); cen.writeUInt32LE(comp.length, 20); cen.writeUInt32LE(data.length, 24);
		cen.writeUInt16LE(name.length, 28); cen.writeUInt32LE(offset, 42);
		central.push(cen, name);
		offset += local.length + name.length + comp.length;
	}
	const cenBuf = Buffer.concat(central);
	const end = Buffer.alloc(22);
	end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(central.length / 2, 8); end.writeUInt16LE(central.length / 2, 10);
	end.writeUInt32LE(cenBuf.length, 12); end.writeUInt32LE(offset, 16);
	fs.writeFileSync(outFile, Buffer.concat([...parts, cenBuf, end]));
}

module.exports = zipDir;
if (require.main === module) zipDir(process.argv[2], process.argv[3]);
