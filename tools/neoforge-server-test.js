// A NeoForge dedicated server load test: installs NeoForge (cached), adds the mods and data packs
// you name, starts the server in its own folder, waits for "Done", runs any console commands,
// stops it, and reports every error. Exits 0 only if the server finished loading with no errors
// from the game's data loading (recipes, tags, registries, trades) or from the mods.
//
//   node tools/neoforge-server-test.js "<test folder>" [options]
//
//   --neoforge <version>   NeoForge version (default: the newest release for --minecraft)
//   --minecraft <version>  Minecraft version (default 26.3)
//   --mod <jar>            a mod jar to add (repeat for more)
//   --modrinth a,b,c       Modrinth projects to add, newest NeoForge build for the Minecraft
//                          version, with their required dependencies (SHA-512 checked)
//   --datapack <zip|dir>   a data pack to put in the world's datapacks folder (repeat for more)
//   --command "<cmd>"      a console command to run once loaded (no slash; repeat for more)
//   --fresh-world          delete the test folder's world first (default: keep it)
//   --ignore <regex>       log lines matching this aren't counted as problems (repeat for more),
//                          for a mod's known harmless noise, e.g. "FrozenLib.*Unable to read config"
//
// A Modrinth project that forgets to list a dependency (Biomes O' Plenty doesn't list TerraBlender)
// stops the server with "requires X"; add X to --modrinth.
//
// The test folder gets its own server (libraries hard-linked from tools/.cache, so it's quick),
// a free port and offline mode. Its mods/ folder is emptied and refilled on every run. The
// Minecraft EULA must already be accepted for these local test servers (new-test-world.js
// --accept-eula records that in tools/.cache/eula-accepted; CI writes that file itself).
const fs = require('fs'), path = require('path'), crypto = require('crypto'), net = require('net');
const { spawn, execFileSync } = require('child_process');

const TOOLS = __dirname, CACHE = path.join(TOOLS, '.cache');
const args = process.argv.slice(2);
const opt = n => { const i = args.indexOf('--' + n); return i >= 0 ? args[i + 1] : undefined; };
const all = n => args.flatMap((a, i) => (a === '--' + n ? [args[i + 1]] : []));
const flag = n => args.includes('--' + n);
const folderArg = args.find((a, i) => !a.startsWith('--') && !(i > 0 && args[i - 1].startsWith('--') && !['--fresh-world'].includes(args[i - 1])));
const fail = msg => { console.error('STOPPED: ' + msg); process.exit(2); };
if (!folderArg) fail('usage: node neoforge-server-test.js "<test folder>" [--mod x.jar] [--modrinth a,b] [--datapack p.zip] [--command "..."]');
const folder = path.resolve(folderArg);
const MC = opt('minecraft') || '26.3';
const UA = { 'User-Agent': 'gabmaybelle/mods-dev (neoforge-server-test)' };

if (!fs.existsSync(path.join(CACHE, 'eula-accepted'))) {
	fail("Minecraft's EULA hasn't been accepted for local test servers. Ask Gabby, then run new-test-world.js once with --accept-eula.");
}
if (/ModrinthApp[\\/]+profiles/i.test(folder)) fail('this is for test folders, never a Modrinth app profile');

/** Java 25 from the Modrinth app (as new-test-world.js finds it). */
function java() {
	const meta = path.join(process.env.APPDATA || '', 'ModrinthApp', 'meta', 'java_versions');
	const pick = fs.existsSync(meta) && fs.readdirSync(meta).filter(d => /25/.test(d)).sort().pop();
	if (pick) return path.join(meta, pick, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
	return 'java';
}

async function download(url, file, sha) {
	if (fs.existsSync(file)) return file;
	const res = await fetch(url, { headers: UA });
	if (!res.ok) throw new Error(`${res.status} downloading ${url}`);
	const body = Buffer.from(await res.arrayBuffer());
	if (sha && crypto.createHash(sha.algo).update(body).digest('hex') !== sha.hash) throw new Error(`hash mismatch for ${url}`);
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, body);
	return file;
}

async function newestNeoForge() {
	const xml = await (await fetch('https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml', { headers: UA })).text();
	const versions = [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map(m => m[1])
		.filter(v => v.startsWith(MC + '.') && !/beta|alpha/.test(v));
	if (!versions.length) throw new Error(`no NeoForge release for Minecraft ${MC}`);
	return versions.sort((a, b) => a.localeCompare(b, undefined, { numeric: true })).pop();
}

/** Installs the NeoForge server once into tools/.cache/neoforge/<version>/server. */
async function installedServer(version) {
	const base = path.join(CACHE, 'neoforge', version);
	const server = path.join(base, 'server');
	if (fs.existsSync(path.join(server, 'libraries', 'net', 'neoforged', 'neoforge', version))) return server;
	const installer = await download(
		`https://maven.neoforged.net/releases/net/neoforged/neoforge/${version}/neoforge-${version}-installer.jar`,
		path.join(base, `neoforge-${version}-installer.jar`));
	fs.mkdirSync(server, { recursive: true });
	console.log(`· installing NeoForge ${version} (once; cached in tools/.cache)…`);
	execFileSync(java(), ['-jar', installer, '--install-server', server], { stdio: 'ignore' });
	return server;
}

/** Hard-links a directory tree (falls back to copying), so every test folder shares one install. */
function linkTree(from, to) {
	fs.mkdirSync(to, { recursive: true });
	for (const entry of fs.readdirSync(from, { withFileTypes: true })) {
		const a = path.join(from, entry.name), b = path.join(to, entry.name);
		if (entry.isDirectory()) linkTree(a, b);
		else if (!fs.existsSync(b)) {
			try { fs.linkSync(a, b); } catch { fs.copyFileSync(a, b); }
		}
	}
}

/** The newest NeoForge build of a Modrinth project for the Minecraft version, plus its required dependencies. */
async function modrinthJars(slugs) {
	const api = p => fetch('https://api.modrinth.com/v2' + p, { headers: UA }).then(r => { if (!r.ok) throw new Error(`${r.status} ${p}`); return r.json(); });
	const seen = new Set(), files = [];
	const queue = [...slugs];
	while (queue.length) {
		const id = queue.shift();
		if (seen.has(id)) continue;
		seen.add(id);
		const q = `?loaders=${encodeURIComponent('["neoforge"]')}&game_versions=${encodeURIComponent(JSON.stringify([MC]))}`;
		const versions = await api(`/project/${id}/version${q}`);
		const release = versions.find(v => v.version_type === 'release') || versions[0];
		if (!release) throw new Error(`no NeoForge ${MC} build of ${id} on Modrinth`);
		const file = release.files.find(f => f.primary) || release.files[0];
		const local = path.join(CACHE, 'modrinth', file.filename);
		await download(file.url, local, { algo: 'sha512', hash: file.hashes.sha512 });
		files.push(local);
		console.log(`· ${id} ${release.version_number}`);
		for (const d of release.dependencies) if (d.dependency_type === 'required' && d.project_id) queue.push(d.project_id);
	}
	return files;
}

function freePort() {
	return new Promise(resolve => { const s = net.createServer(); s.listen(0, () => { const p = s.address().port; s.close(() => resolve(p)); }); });
}

(async () => {
	const version = opt('neoforge') || await newestNeoForge();
	const server = await installedServer(version);
	linkTree(server, folder);

	// Mods: emptied and refilled every run, so a run only ever has what it names.
	const mods = path.join(folder, 'mods');
	fs.rmSync(mods, { recursive: true, force: true });
	fs.mkdirSync(mods, { recursive: true });
	const jars = [...all('mod').map(j => path.resolve(j)), ...(opt('modrinth') ? await modrinthJars(opt('modrinth').split(',').filter(Boolean)) : [])];
	for (const jar of jars) {
		if (!fs.existsSync(jar)) fail(`no such mod jar: ${jar}`);
		fs.copyFileSync(jar, path.join(mods, path.basename(jar)));
	}

	if (flag('fresh-world')) fs.rmSync(path.join(folder, 'world'), { recursive: true, force: true });
	const packs = path.join(folder, 'world', 'datapacks');
	fs.mkdirSync(packs, { recursive: true });
	for (const p of all('datapack').map(d => path.resolve(d))) {
		if (fs.statSync(p).isDirectory()) fs.cpSync(p, path.join(packs, path.basename(p)), { recursive: true });
		else fs.copyFileSync(p, path.join(packs, path.basename(p)));
	}

	const port = await freePort();
	fs.writeFileSync(path.join(folder, 'eula.txt'), 'eula=true\n');
	fs.writeFileSync(path.join(folder, 'server.properties'), [
		'online-mode=false', `server-port=${port}`, 'level-name=world', 'spawn-protection=0',
		'max-players=2', 'view-distance=4', 'simulation-distance=4', 'motd=NeoForge load test', '',
	].join('\n'));

	const argsFile = path.join('libraries', 'net', 'neoforged', 'neoforge', version, process.platform === 'win32' ? 'win_args.txt' : 'unix_args.txt');
	const jvm = fs.existsSync(path.join(folder, 'user_jvm_args.txt')) ? ['@user_jvm_args.txt'] : [];
	console.log(`· NeoForge ${version} for Minecraft ${MC}, ${jars.length} mod jar(s), port ${port}, in ${folder}`);
	const proc = spawn(java(), ['-Xmx3G', ...jvm, '@' + argsFile, 'nogui'], { cwd: folder });

	const lines = [];
	let done = false, sent = false;
	const timeout = setTimeout(() => { console.error('TIMEOUT: no "Done" in 8 minutes'); proc.kill(); }, 480000);
	const onLine = line => {
		lines.push(line);
		if (!sent && /Done \([\d.,]+s\)!/.test(line)) {
			sent = done = true;
			for (const c of all('command')) proc.stdin.write(c + '\n');
			setTimeout(() => proc.stdin.write('stop\n'), 4000);
		}
	};
	let buf = '';
	for (const s of [proc.stdout, proc.stderr]) s.on('data', d => {
		buf += d;
		const parts = buf.split(/\r?\n/);
		buf = parts.pop();
		parts.forEach(onLine);
	});
	proc.on('exit', code => {
		clearTimeout(timeout);
		// Problems loading data or mods. Offline-mode noise, Windows process-statistics errors (oshi) and
		// netty's macOS/Linux-only network libraries failing to load on Windows aren't problems here.
		const bad = lines.filter(l => /\/(ERROR|FATAL)\]|Errors in element|Failed to load registries|Couldn't parse|Couldn't load|Exception/.test(l)
			&& !/Failed to fetch|realms|Realms|authlib|YggdrasilUserApi|Perflib|Win32Exception|oshi|kqueue|epoll|Only supported on (OSX|Linux)|AppenderLoggingException/.test(l)
			&& !all('ignore').some(re => new RegExp(re).test(l)));
		const missing = lines.filter(l => /requires \S+ .* or above|Currently, \S+ is not installed/.test(l));
		if (missing.length) console.log('MISSING DEPENDENCIES:\n' + missing.map(l => l.trim()).join('\n'));
		const replies = lines.filter(l => /\[minecraft\/MinecraftServer\]: (?!Done|Stopping|Saving|ThreadedAnvil)/.test(l) || /There are \d+/.test(l));
		if (replies.length) console.log(replies.join('\n'));
		if (bad.length) console.log('PROBLEMS:\n' + bad.join('\n'));
		console.log(`--- ${done ? 'loaded' : 'DID NOT LOAD'}; exit ${code}; ${lines.length} log lines; ${bad.length} problem line(s)`);
		process.exit(done && bad.length === 0 ? 0 : 1);
	});
})().catch(e => fail(e.message));
