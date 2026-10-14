// Starts a dedicated server in <folder> (server.properties, eula.txt and world/datapacks already
// set up), waits for "Done", runs the given console commands, stops it and prints every log line
// that mentions an error, cozy_core, or a command's reply. Exits 1 unless it loaded with no problems.
//   node tools/server-test.js "<folder>" "<java.exe>" "<server jar>" ["command" ...]
const { spawn } = require('child_process');

const [folder, java, jar, ...commands] = process.argv.slice(2);
const server = spawn(java, ['-Xmx2G', '-jar', jar, '--nogui'], { cwd: folder });
const lines = [];
let sent = false;
const timeout = setTimeout(() => { console.error('TIMEOUT: no "Done" in 5 minutes'); server.kill(); }, 300000);

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
server.on('exit', code => {
	clearTimeout(timeout);
	const keep = lines.filter(l => /ERROR|WARN|Exception|cozy_core|Couldn't|Failed|Unknown|There are|Done \(/.test(l));
	console.log(keep.join('\n'));
	// Problems: anything that stops data loading, or an ERROR line that isn't known noise (Windows
	// process statistics, offline mode). Exits 1 if the server never finished loading or had one.
	const problems = lines.filter(l => /ERROR|Errors in element|Failed to load registries/.test(l)
		&& !/Perflib|oshi|Win32Exception|Realms|realms|authlib|Failed to fetch/.test(l));
	console.log(`--- server exited (${code}); ${lines.length} log lines, ${problems.length} problem line(s)`);
	process.exit(sent && problems.length === 0 ? 0 : 1);
});
