// Checks the crafting recipes in a running test game by letting crafter blocks craft them:
// fills a row of crafters, powers them, and reads what each one dropped.
//   node tools/crafter-test.js "<test folder>" <player name>
// Needs a running test game whose MCPFabric bridge has commands on, and the bridge's
// command-line client, mcf.js: set the MCF environment variable to its path.
const path = require('path'), { execFileSync } = require('child_process');

const [folder, player] = process.argv.slice(2);
if (!folder || !player) {
	console.error('usage: node tools/crafter-test.js "<test folder>" <player name>');
	process.exit(1);
}
const MCF = process.env.MCF;
if (!MCF) {
	console.error('set MCF to the path of the MCPFabric bridge command-line client (mcf.js)');
	process.exit(1);
}
const batch = calls => execFileSync(process.execPath, [MCF, folder, '--batch', JSON.stringify(calls)], { encoding: 'utf8', maxBuffer: 1 << 26 });
const cmd = c => ['command.run', { command: c }];

const C = c => `minecraft:${c}`;
const mixed = f => ['red', 'blue', 'lime', 'black', 'white', 'cyan', 'pink', 'brown'].map(c => C(`${c}_${f}`));
const ring = (edge, middle) => [...edge.slice(0, 4), middle, ...edge.slice(4, 8)];
// [name, 9 slots (null = empty), expected result id or null for "nothing", expected count]
const CASES = [
	['oak slabs -> plank', [C('oak_slab'), C('oak_slab')], C('oak_planks'), 1],
	['bamboo slabs side by side -> plank', [C('bamboo_slab'), C('bamboo_slab')], C('bamboo_planks'), 1],
	['bamboo slabs stacked -> mosaic (vanilla kept)', [C('bamboo_slab'), null, null, C('bamboo_slab')], C('bamboo_mosaic'), 1],
	['stick scaffolding', [C('stick'), C('string'), C('stick'), C('stick'), null, C('stick'), C('stick'), null, C('stick')], C('scaffolding'), 3],
	['red sand', ring(Array(8).fill(C('sand')), C('red_dye')), C('red_sand'), 8],
	['glow lichen', [C('vine'), C('glow_ink_sac')], C('glow_lichen'), 4],
	['moss from oak leaves', [C('oak_leaves'), C('bone_meal')], C('moss_block'), 1],
	['re-dye mixed terracotta yellow', ring(mixed('terracotta'), C('yellow_dye')), C('yellow_terracotta'), 8],
	['re-dye mixed glass purple', ring(mixed('stained_glass'), C('purple_dye')), C('purple_stained_glass'), 8],
	['re-dye mixed panes orange', ring(mixed('stained_glass_pane'), C('orange_dye')), C('orange_stained_glass_pane'), 8],
	['re-dye mixed concrete powder gray', ring(mixed('concrete_powder'), C('gray_dye')), C('gray_concrete_powder'), 8],
	['plain terracotta + dye (vanilla kept)', ring(Array(8).fill(C('terracotta')), C('red_dye')), C('red_terracotta'), 8],
	['same colour + dye does nothing', ring(Array(8).fill(C('red_terracotta')), C('red_dye')), null, 0],
];
const MOD_CASES = { // only run when the mod's items exist
	biomesoplenty: [['BOP fir slabs -> plank', ['biomesoplenty:fir_slab', 'biomesoplenty:fir_slab'], 'biomesoplenty:fir_planks', 1],
		['moss from BOP leaves', ['biomesoplenty:fir_leaves', C('bone_meal')], C('moss_block'), 1]],
	ecologics: [['Ecologics walnut slabs -> plank', ['ecologics:walnut_slab', 'ecologics:walnut_slab'], 'ecologics:walnut_planks', 1]],
	wilderwild: [['Wilder Wild baobab slabs -> plank', ['wilderwild:baobab_slab', 'wilderwild:baobab_slab'], 'wilderwild:baobab_planks', 1]],
	promenade: [['Promenade sakura slabs -> plank', ['promenade:sakura_slab', 'promenade:sakura_slab'], 'promenade:sakura_planks', 1]],
};

const mods = process.argv.slice(4);
const cases = [...CASES, ...mods.flatMap(m => MOD_CASES[m] || [])];
const state = JSON.parse(batch([['player.getState', {}]]).replace(/^[^{]*/, '').replace(/\n\S+:[\s\S]*$/, ''));
const bx = Math.floor(state.x) + 3, by = Math.floor(state.y) + 6, bz = Math.floor(state.z);

const setup = [cmd(`fill ${bx} ${by - 2} ${bz - 1} ${bx + cases.length * 3} ${by + 1} ${bz + 1} air`),
	cmd(`fill ${bx} ${by - 2} ${bz - 1} ${bx + cases.length * 3} ${by - 2} ${bz + 1} glass`),
	cmd(`kill @e[type=item,x=${bx},y=${by - 3},z=${bz - 2},dx=${cases.length * 3 + 1},dy=6,dz=4]`)];
cases.forEach(([, slots], i) => {
	const x = bx + i * 3;
	setup.push(cmd(`setblock ${x} ${by} ${bz} crafter[orientation=down_east]`));
	slots.forEach((id, s) => id && setup.push(cmd(`item replace block ${x} ${by} ${bz} container.${s} with ${id}`)));
});
batch(setup);
batch(cases.map((_, i) => cmd(`setblock ${bx + i * 3} ${by + 1} ${bz} redstone_block`)));
execFileSync(process.execPath, ['-e', 'setTimeout(()=>{},2500)']); // crafters fire 4 ticks after power, items fall

const out = batch(cases.map((_, i) => cmd(`data get entity @e[type=item,limit=1,sort=nearest,x=${bx + i * 3 + 0.5},y=${by - 1},z=${bz + 0.5},distance=..1.5] Item`)));
const replies = out.split(/command\.run:/).slice(1);
let failed = 0;
cases.forEach(([name, , want, count], i) => {
	const reply = replies[i] || '';
	const id = (reply.match(/id: \\?"([^"\\]+)/) || [])[1] || null; // the reply escapes its quotes
	const n = id ? Number((reply.match(/count: (\d+)/) || [, 1])[1]) : 0;
	const ok = id === want && n === count;
	if (!ok) failed++;
	console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}: got ${id ? `${n} ${id}` : 'nothing'}${ok ? '' : `, wanted ${want ? `${count} ${want}` : 'nothing'}`}`);
});
batch([cmd(`fill ${bx} ${by - 2} ${bz - 1} ${bx + cases.length * 3} ${by + 1} ${bz + 1} air`),
	cmd(`kill @e[type=item,x=${bx},y=${by - 3},z=${bz - 2},dx=${cases.length * 3 + 1},dy=6,dz=4]`)]);
console.log(`${cases.length - failed}/${cases.length} passed`);
process.exit(failed ? 1 : 0);
