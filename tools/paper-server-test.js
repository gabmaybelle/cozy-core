#!/usr/bin/env node
// Starts a Paper server with the plugin on a brand-new world and checks the Hearth works: a Hearth
// Lantern placed by /cozycore place is a lantern block; /cozycore selftest passes (a natural
// monster spawn at home is refused, one in the wild goes through); a creeper's blast at home breaks
// nothing while one in the wild leaves a hole and TNT at home still does (the rule is creepers and
// ghasts only); fire on planks at home burns none while fire on planks in the wild burns some; and
// the plugin logs no errors or warnings. Then the server is stopped and started again on the same
// world: the lantern must still be known (the chunk's own data remembers it). No window opens.
// The Paper server jar is downloaded once into the folder.
//
//   node tools/paper-server-test.js "<server folder>" --plugin paper/build/libs/cozy-core-<v>-paper.jar --eula
//       [--paper 26.3] [--seed <n>] [--port <n>] [--no-protection] [--no-build-protection] [--timeout <seconds>]
//
// --eula says you accept Minecraft's server EULA (https://aka.ms/MinecraftEULA); without it the
// script stops before starting the server. The world is deleted first. --no-protection sets the
// plugin's hearthLanternRadius to 0, so every home check is expected to fail (the proof that they
// test something); --no-build-protection sets hearthsProtectBuilds off, so the selftest still
// passes but the creeper and fire checks fail.
'use strict';
const fs = require('fs'), path = require('path'), cp = require('child_process'), crypto = require('crypto');

const args = process.argv.slice(2);
const folder = args[0] && !args[0].startsWith('--') ? path.resolve(args[0]) : null;
const opt = (name, fallback) => { const i = args.indexOf(name); return i >= 0 && i + 1 < args.length ? args[i + 1] : fallback; };
const flag = (name) => args.includes(name);
const plugin = opt('--plugin', null);
if (!folder || !plugin) {
	console.error('usage: node tools/paper-server-test.js "<server folder>" --plugin <jar> --eula [--paper 26.3] [--seed <n>] [--port <n>] [--no-protection] [--no-build-protection] [--timeout <s>]');
	process.exit(2);
}
if (!flag('--eula')) {
	console.error('The server only runs once the Minecraft server EULA (https://aka.ms/MinecraftEULA) is accepted: pass --eula to say you do.');
	process.exit(2);
}
const paperVersion = opt('--paper', '26.3');
const port = +opt('--port', '25598');
const seed = opt('--seed', '');
const timeoutMs = +opt('--timeout', '600') * 1000;
const javaBin = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const UA = { 'User-Agent': 'cozy-core-server-test (https://github.com/gabmaybelle/cozy-core)' };

async function download(url, to, sha256) {
	const res = await fetch(url, { headers: UA });
	if (!res.ok) throw new Error(`${url}: ${res.status}`);
	const data = Buffer.from(await res.arrayBuffer());
	if (sha256 && crypto.createHash('sha256').update(data).digest('hex') !== sha256) throw new Error(`${url}: checksum mismatch`);
	fs.writeFileSync(to, data);
}

/** The newest Paper build of this version, downloaded once as paper-<version>-<build>.jar. */
async function serverJar() {
	const existing = fs.readdirSync(folder).filter((f) => f.startsWith(`paper-${paperVersion}-`) && f.endsWith('.jar')).sort();
	if (existing.length) return path.join(folder, existing[existing.length - 1]);
	const res = await fetch(`https://fill.papermc.io/v3/projects/paper/versions/${paperVersion}/builds/latest`, { headers: UA });
	if (!res.ok) throw new Error(`Paper build list: ${res.status}`);
	const build = await res.json();
	const dl = build.downloads['server:default'];
	console.log(`downloading Paper ${paperVersion} build ${build.id} (${dl.name})`);
	const to = path.join(folder, dl.name);
	await download(dl.url, to, dl.checksums && dl.checksums.sha256);
	return to;
}

function prepare() {
	fs.mkdirSync(path.join(folder, 'plugins', 'CozyCore'), { recursive: true });
	fs.writeFileSync(path.join(folder, 'eula.txt'), 'eula=true\n');
	fs.writeFileSync(path.join(folder, 'server.properties'), [
		'online-mode=false', `server-port=${port}`, 'query.port=' + port, 'enable-rcon=false', 'white-list=false',
		'spawn-protection=0', 'view-distance=6', 'simulation-distance=4', 'max-players=2', 'motd=Cozy Core test', 'difficulty=easy',
		`level-seed=${seed}`, 'level-name=world', 'sync-chunk-writes=false', ''].join('\n'));
	// Only this plugin, and only the jar under test.
	for (const f of fs.readdirSync(path.join(folder, 'plugins'))) {
		if (f.endsWith('.jar')) fs.rmSync(path.join(folder, 'plugins', f));
	}
	fs.copyFileSync(plugin, path.join(folder, 'plugins', path.basename(plugin)));
	// The plugin's settings for this run: its defaults, with the switches the flags turn off.
	const config = path.join(folder, 'plugins', 'CozyCore', 'config.yml');
	if (flag('--no-protection')) {
		fs.writeFileSync(config, 'hearthLanternRadius: 0\n');
	} else if (flag('--no-build-protection')) {
		fs.writeFileSync(config, 'hearthsProtectBuilds: false\n');
	} else {
		fs.rmSync(config, { force: true });
	}
	for (const w of ['world', 'world_nether', 'world_the_end']) fs.rmSync(path.join(folder, w), { recursive: true, force: true });
}

/** Runs the server, feeding it console commands and reading their replies. */
class Server {
	constructor(jar) {
		this.lines = [];
		this.waiters = [];
		this.proc = cp.spawn(javaBin, ['-Xmx2G', '-Dcom.mojang.eula.agree=true', '-jar', jar, 'nogui'],
			{ cwd: folder, stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
		let buffer = '';
		const onData = (chunk) => {
			buffer += chunk.toString();
			let i;
			while ((i = buffer.indexOf('\n')) >= 0) {
				const line = buffer.slice(0, i).replace(/\r$/, '');
				buffer = buffer.slice(i + 1);
				this.lines.push(line);
				for (const w of [...this.waiters]) if (w.test(line)) { this.waiters.splice(this.waiters.indexOf(w), 1); w.resolve(line); }
			}
		};
		this.proc.stdout.on('data', onData);
		this.proc.stderr.on('data', onData);
		this.exited = new Promise((resolve) => this.proc.on('exit', (code) => { this.exitCode = code; resolve(code); }));
	}

	/** The next line matching the pattern (or one already seen since `from`), within the time given. */
	waitFor(pattern, ms, from = 0) {
		const found = this.lines.slice(from).find((l) => pattern.test(l));
		if (found) return Promise.resolve(found);
		return new Promise((resolve, reject) => {
			const w = { test: (l) => pattern.test(l), resolve: (l) => { clearTimeout(timer); resolve(l); } };
			const timer = setTimeout(() => { this.waiters.splice(this.waiters.indexOf(w), 1); reject(new Error(`timed out waiting for ${pattern}`)); }, ms);
			this.waiters.push(w);
		});
	}

	/**
	 * Sends a console command and returns what the server said to it. The console prints a vanilla
	 * command's reply as "System chat: ..." (Test passed, Test failed, an error); the plugin's own
	 * messages are plain lines, so a pattern for them can be given.
	 */
	async command(text, reply = /System chat: |Unknown or incomplete command|Incorrect argument/) {
		const from = this.lines.length;
		this.proc.stdin.write(text + '\n');
		const line = await this.waitFor(reply, 15000, from);
		return line.replace(/^.*?\]: /, '').replace(/\x1b\[[0-9;]*m/g, '');
	}

	ready() {
		return Promise.race([this.waitFor(/Done \(.*\)! For help/, timeoutMs), this.exited.then(() => { throw new Error('the server stopped before it was ready'); })]);
	}

	async stop() {
		if (this.exitCode !== undefined) return;
		try { this.proc.stdin.write('stop\n'); } catch (e) { /* already gone */ }
		const t = setTimeout(() => this.proc.kill(), 60000);
		await this.exited;
		clearTimeout(t);
	}
}

// The test's scene, high above any terrain so nothing natural is in the way: the lantern on a stone
// block at the origin, home being the 64 blocks around it; the wild starts 65 blocks out.
const L = { x: 0, y: 301, z: 0 }, Y = 300, WILD = 100;

async function main() {
	fs.mkdirSync(folder, { recursive: true });
	const jar = await serverJar();
	prepare();
	console.log(`starting Paper (${path.basename(jar)}) in ${folder} with ${path.basename(plugin)}`);
	let server = new Server(jar);
	const failures = [];
	const allLines = [];
	const check = (name, ok, detail) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${name}${detail ? ': ' + detail : ''}`); if (!ok) failures.push(name); };
	const passed = async (name, cmd) => { const reply = await server.command(cmd); check(name, /Test passed/.test(reply), reply); };
	const plain = /Cozy Core|Unknown or incomplete command|Incorrect argument|System chat: /;
	/** How many of the nine planks of a 3x3 at (x..x+2, Y, z..z+2) are gone. */
	const burnt = async (x, z) => {
		let n = 0;
		for (let dx = 0; dx < 3; dx++) for (let dz = 0; dz < 3; dz++) {
			if (/Test passed/.test(await server.command(`execute unless block ${x + dx} ${Y} ${z + dz} minecraft:oak_planks`))) n++;
		}
		return n;
	};
	/** A 3x3x3 dirt cube with its top at Y+2, and a lit creeper (or TNT) on top of it. */
	const blast = async (x, z, what) => {
		await server.command(`fill ${x - 1} ${Y} ${z - 1} ${x + 1} ${Y + 2} ${z + 1} minecraft:dirt`);
		await server.command(`summon ${what} ${x} ${Y + 3} ${z}${what === 'minecraft:creeper' ? ' {ignited:1b,Fuse:1s}' : ' {fuse:1s}'}`, /Summoned|System chat: /);
	};
	try {
		await server.ready();
		const readyLine = server.lines.find((l) => /Cozy Core: ready/.test(l));
		check('the plugin started', !!readyLine, readyLine ? readyLine.replace(/^.*?\]: /, '') : 'no "Cozy Core: ready" line in the log');
		// Nothing may change on its own meanwhile, and the scene must stay loaded with no player near.
		await server.command(`forceload add ${L.x - 20} ${L.z - 20} ${WILD + 20} ${L.z + 60}`);
		const stillness = await server.command('gamerule random_tick_speed 0');
		check('random ticks off', !/Unknown|Incorrect|Expected/.test(stillness), stillness);
		// Since 26.x fire only ticks near a player unless this rule says everywhere.
		const spread = await server.command('gamerule fire_spread_radius_around_player -1');
		check('fire ticks without a player nearby', !/Unknown|Incorrect|Expected/.test(spread), spread);
		await server.command('weather clear 100000');

		// The lantern, named, on a stone block.
		await server.command(`setblock ${L.x} ${Y} ${L.z} minecraft:stone`);
		const placed = await server.command(`cozycore place ${L.x} ${L.y} ${L.z} soul Willow Cottage`, plain);
		check('/cozycore place puts a lantern down', /placed a Soul Hearth Lantern at 0 301 0, the home called "Willow Cottage"/.test(placed), placed);
		await passed('the block is a soul lantern', `execute if block ${L.x} ${L.y} ${L.z} minecraft:soul_lantern`);
		const refused = await server.command(`cozycore place ${L.x} ${Y} ${L.z}`, plain);
		check('/cozycore place refuses a taken spot', /the lantern needs an empty spot/.test(refused), refused);

		// The spawn rule, through the real spawn event, both sides of the edge.
		const selftest = await server.command(`execute positioned ${L.x} ${L.y} ${L.z} run cozycore selftest`, /Cozy Core selftest:|Unknown or incomplete command|System chat: /);
		check('/cozycore selftest passes', /Cozy Core selftest: passed/.test(selftest), selftest);

		// Blasts: a creeper at home, a creeper in the wild, TNT at home.
		await blast(10, 10, 'minecraft:creeper');
		await blast(WILD, 10, 'minecraft:creeper');
		await blast(10, 30, 'minecraft:tnt');
		await sleep(5000);
		await passed('a creeper blast at home breaks nothing', `execute if block 10 ${Y + 2} 10 minecraft:dirt`);
		await passed('a creeper blast in the wild leaves a hole', `execute unless block ${WILD} ${Y + 2} 10 minecraft:dirt`);
		await passed('TNT at home still breaks blocks', `execute unless block 10 ${Y + 2} 30 minecraft:dirt`);
		await passed('the lantern stands after the blasts', `execute if block ${L.x} ${L.y} ${L.z} minecraft:soul_lantern`);

		// Fire: nine planks with a fire on each, at home and in the wild, left to burn for a while.
		for (const [x, z] of [[20, 20], [WILD, 20]]) {
			await server.command(`fill ${x} ${Y} ${z} ${x + 2} ${Y} ${z + 2} minecraft:oak_planks`);
			await server.command(`fill ${x} ${Y + 1} ${z} ${x + 2} ${Y + 1} ${z + 2} minecraft:fire`);
		}
		await sleep(15000);
		await server.command(`fill 15 ${Y} 15 ${WILD + 10} ${Y + 6} 40 minecraft:air replace minecraft:fire`);
		const home = await burnt(20, 20), wild = await burnt(WILD, 20);
		check('fire burns no planks at home', home === 0, `${home} of 9 planks burnt`);
		check('fire still burns planks in the wild', wild > 0, `${wild} of 9 planks burnt`);

		const errors = () => server.lines.filter((l) => /^\[[\d:]+ (ERROR|WARN)\]/.test(l) && /CozyCore|Cozy Core|cozycore/i.test(l));
		check('no errors or warnings from the plugin', errors().length === 0, errors().join(' | '));

		// The world remembers the lantern: stop, start again on the same world, ask again.
		await server.stop();
		allLines.push(...server.lines);
		console.log('restarting the server on the same world');
		server = new Server(jar);
		await server.ready();
		const again = await server.command(`execute positioned ${L.x} ${L.y} ${L.z} run cozycore selftest`, /Cozy Core selftest:|Unknown or incomplete command|System chat: /);
		check('after a restart the world still knows the lantern', /Cozy Core selftest: passed/.test(again), again);
		await server.command('forceload remove all');
		check('no errors or warnings from the plugin after the restart', errors().length === 0, errors().join(' | '));
	} catch (e) {
		check('the test ran to the end', false, e.message);
	} finally {
		await server.stop();
		allLines.push(...server.lines);
		fs.writeFileSync(path.join(folder, 'test-console.log'), allLines.join('\n'));
		console.log(failures.length ? `FAILED: ${failures.join(', ')}` : 'passed: every check');
		process.exit(failures.length ? 1 : 0);
	}
}

main().catch((e) => { console.error(e); process.exit(1); });
