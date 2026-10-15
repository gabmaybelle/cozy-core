// Starts a dedicated server in <folder> (server.properties, eula.txt and world/datapacks already
// set up), waits for "Done", runs the given console commands, stops it and prints every log line
// that mentions an error, cozy_core, or a command's reply. Exits 1 unless it loaded with no problems.
//   node tools/server-test.js "<folder>" "<java.exe>" "<server jar>" ["command" ...]
// The server jar can also be an @arguments file, the way NeoForge starts its server
// ("@libraries/net/neoforged/neoforge/<version>/unix_args.txt"). IGNORE (an environment variable,
// a regular expression) names a mod's known harmless log lines, so they don't count as problems.
// On Windows, when the local toolkit's game-watch.js is there (set GAME_WATCH to its path if it isn't
// at ~/mods-dev/tools/game-watch.js), it looks at the server every 5 s and stops it the moment it
// errors, crashes or goes silent for 2 minutes, saying why, instead of waiting out the timeout.
const { spawn } = require('child_process');
const os = require('os'), path = require('path');

let GW = null;
if (process.platform === 'win32') {
	try { GW = require(process.env.GAME_WATCH || path.join(os.homedir(), 'mods-dev', 'tools', 'game-watch.js')); } catch {}
}

const [folder, java, jar, ...commands] = process.argv.slice(2);
const launch = jar.startsWith('@') ? [jar] : ['-jar', jar];
const started = Date.now();
const server = spawn(java, ['-Xmx2G', ...launch, '--nogui'], { cwd: folder });
const lines = [];
let sent = false, stopped = '';
const timeout = setTimeout(() => { console.error('TIMEOUT: no "Done" in 5 minutes'); server.kill(); }, 300000);
const watch = GW && setInterval(async () => {
	const v = await GW.inspect(folder, { server: true, pid: server.pid, since: started });
	if (['error', 'crashed', 'hung'].includes(v.verdict) && !stopped) {
		stopped = `${v.verdict}: ${v.reason}`;
		console.error(`STOPPED (game-watch) ${stopped}`);
		server.kill();
	}
}, 5000);

function onLine(line) {
	lines.push(line);
	if (!sent && /Done \([\d.,]+s\)!/.test(line)) {
		sent = true;
		for (const c of commands) server.stdin.write(c + '\n');
		setTimeout(() => server.stdin.write('stop\n'), 3000);
	}
}
let buf = '';
for (const s of [server.stdout, server.stderr]) s.on('data', d => {
	buf += d;
	const parts = buf.split(/\r?\n/); buf = parts.pop();
	parts.forEach(onLine);
});
server.on('exit', async code => {
	clearTimeout(timeout);
	if (watch) clearInterval(watch);
	if (GW && !sent && !stopped) { // it quit before "Done": game-watch can usually say why
		const v = await GW.inspect(folder, { server: true, pid: server.pid, since: started });
		if (['error', 'crashed'].includes(v.verdict)) console.error(`STOPPED (game-watch) ${v.verdict}: ${v.reason}`);
	}
	const keep = lines.filter(l => /ERROR|WARN|Exception|cozy_core|Couldn't|Failed|Unknown|There are|Done \(/.test(l));
	console.log(keep.join('\n'));
	// Problems: anything that stops data loading, or an ERROR line that isn't known noise (Windows
	// process statistics, offline mode). Exits 1 if the server never finished loading or had one.
	// netty's network libraries for other systems (kqueue on Linux, epoll on Windows) failing to load
	// is noise too.
	const ignore = process.env.IGNORE ? new RegExp(process.env.IGNORE) : null;
	const problems = lines.filter(l => /ERROR|Errors in element|Failed to load registries/.test(l)
		&& !/Perflib|oshi|Win32Exception|Realms|realms|authlib|Failed to fetch|kqueue|epoll|Only supported on|AppenderLoggingException|exception occurred processing Appender/.test(l)
		&& !(ignore && ignore.test(l)));
	console.log(`--- server exited (${code}); ${lines.length} log lines, ${problems.length} problem line(s)`);
	process.exit(sent && !stopped && problems.length === 0 ? 0 : 1);
});
