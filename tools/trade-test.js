// Samples trades in a running test game: summons a merchant in front of the player, opens its
// trade screen through the MCPFabric bridge (offers are only made when a player opens it), reads
// the offers and removes it. Prints how often each item was offered.
//   node tools/trade-test.js "<test folder>" <times> wandering_trader
//   node tools/trade-test.js "<test folder>" <times> villager <profession> <level>
// Needs the bridge's command-line client, mcf.js, from Gabby's local toolkit (not in this repo):
// set MCF to its path if it isn't at ~/mods-dev/tools/mcf.js.
const path = require('path'), { execFileSync } = require('child_process');

const [folder, times = '20', kind = 'wandering_trader', profession, level] = process.argv.slice(2);
const MCF = process.env.MCF || path.join(process.env.USERPROFILE, 'mods-dev', 'tools', 'mcf.js');
const call = (method, params = {}) => execFileSync(process.execPath, [MCF, folder, method, JSON.stringify(params)], { encoding: 'utf8' });
const run = command => call('command.run', { command });
const pause = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);

const me = JSON.parse(call('player.getState').replace(/^[^{]*/, ''));
const x = Math.floor(me.x) + 0.5, y = Math.floor(me.y), z = Math.floor(me.z) + 2.5;
const nbt = kind === 'villager'
	? `{NoAI:1b,Tags:["cozy_test"],VillagerData:{profession:"minecraft:${profession}",level:${level},type:"minecraft:plains"}}`
	: `{NoAI:1b,Tags:["cozy_test"],DespawnDelay:999999}`;

const seen = {}, sizes = {}, slow = [];
run('kill @e[tag=cozy_test]');
call('control.lookAt', { x, y: y + 1.6, z });
for (let i = 0; i < Number(times); i++) {
	run(`summon ${kind} ${x} ${y} ${z} ${nbt}`);
	pause(800); // let the client see the new merchant before using it
	// The query lists every nearby entity (the player too), so pick ours by type.
	const found = JSON.parse(call('entities.query', { radius: 6 }).replace(/^[^{[]*/, ''));
	const list = Array.isArray(found) ? found : found.entities || [];
	const uuid = (list.find(e => e.type === `minecraft:${kind}`) || {}).uuid;
	if (!uuid) throw new Error(`no ${kind} found after summoning`);
	const t = Date.now();
	call('interact.useEntity', { uuid });
	let reply = '';
	for (let tries = 0; tries < 20 && !/following entity data/.test(reply); tries++) reply = run('data get entity @e[tag=cozy_test,limit=1] Offers.Recipes');
	const ms = Date.now() - t;
	if (ms > 4000) slow.push(ms);
	const sold = [...reply.matchAll(/sell: \{[^}]*?id: \\"([a-z_:]+)\\"/g)].map(m => m[1]);
	sizes[sold.length] = (sizes[sold.length] || 0) + 1;
	for (const s of new Set(sold)) seen[s] = (seen[s] || 0) + 1;
	run('kill @e[tag=cozy_test]'); // closes the trade screen
	pause(800); // and let the client close it before the next one
}
console.log(`${times} x ${kind}${profession ? ` ${profession} level ${level}` : ''}: offers per merchant`, sizes);
console.log(Object.entries(seen).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${v} ${k}`).join(', '));
if (slow.length) console.log('slow trade screens (ms):', slow.join(' '));
