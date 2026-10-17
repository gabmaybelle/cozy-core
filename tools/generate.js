// Writes the whole Cozy Core data pack into pack/ (deleted and rebuilt each run).
//   node tools/generate.js [path to a 26.3 server or client jar]
// Wood types, dye colours and recipe clashes are read from the vanilla data, so new wood
// types in a later version are picked up by re-running this against the new jar.
const fs = require('fs'), path = require('path'), { execFileSync } = require('child_process');

const { MC: MC_VERSION, DATA_FORMAT, DESCRIPTION } = require('./pack-info');

const NS = 'cozy_core';
const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'pack');
const VANILLA = path.join(__dirname, `.vanilla-${MC_VERSION}`, 'data', 'minecraft');
const DEFAULT_JAR = path.join(process.env.USERPROFILE || '', 'mods-dev', 'tools', '.cache', 'fabric-server',
	`${MC_VERSION}-0.19.5`, 'versions', MC_VERSION, `server-${MC_VERSION}.jar`);

// ---------- vanilla data ----------
// unzip some patterns from a jar. Info-ZIP exits 11 when a pattern matches nothing (vanilla has no
// item-tag sub-folders, and Linux's unzip lets '*' match '/' so the '*/*' pattern can come up
// empty); that's fine here, any other failure isn't.
function unzipSome(jar, patterns, dest) {
	fs.mkdirSync(dest, { recursive: true }); // unzip makes the last folder only
	const r = require('child_process').spawnSync('unzip', ['-o', '-q', jar, ...patterns, '-d', dest], { encoding: 'utf8' });
	if (r.error) throw r.error;
	if (r.status !== 0 && r.status !== 11) throw new Error(`unzip ${jar} failed (${r.status}): ${r.stderr}`);
}

function extractVanilla(jar) {
	if (fs.existsSync(path.join(VANILLA, 'recipe'))) return;
	if (!fs.existsSync(jar)) throw new Error(`No vanilla data cached and no jar at ${jar}`);
	// Git Bash's unzip doesn't match '/' with '*', so sub-folders get their own '*/*' pattern.
	unzipSome(jar, ['data/minecraft/recipe/*', 'data/minecraft/tags/item/*', 'data/minecraft/tags/item/*/*',
		'data/minecraft/tags/worldgen/structure/*', 'data/minecraft/trade_set/*/*', 'data/minecraft/tags/villager_trade/*/*'],
		path.dirname(path.dirname(VANILLA)));
}

// Some mods (Macaw's Windows) save their JSON with a byte-order mark, which JSON.parse refuses.
const readJson = p => JSON.parse(fs.readFileSync(p, 'utf8').replace(/^﻿/, ''));
const vanillaJson = rel => readJson(path.join(VANILLA, rel));

// Wood mods with a 26.3 Fabric version (checked on Modrinth 2026-10-13). Their recipes are read
// from the jars, so their own wood types are covered the same way as vanilla's. Every recipe made
// for a mod only loads when that mod is installed (Fabric and NeoForge load conditions).
const WOOD_MODS = ['biomesoplenty', 'ecologics', 'wilderwild', 'promenade'];
// Decoration mods (Gabby's picks: Beautify 2026-10-13, Macaw's 2026-10-15): their wooden pieces go
// in the stonecutter at the same cheap rate as vanilla's (see decorCutting). Each mod's Fabric and
// NeoForge versions share the mod id and the recipes. Macaw's Bridges is left out: none of its
// pieces is wood only.
const MACAWS = { mcwroofs: 'mcw-roofs', mcwfences: 'mcw-fences', mcwstairs: 'mcw-stairs', mcwwindows: 'mcw-windows',
	mcwfurnitures: 'mcw-furniture', mcwdoors: 'mcw-doors', mcwtrpdoors: 'mcw-trapdoors' };
const DECOR_MODS = ['beautify', ...Object.keys(MACAWS)];
const JAR_PREFIX = { biomesoplenty: 'BiomesOPlenty', ecologics: 'Ecologics', wilderwild: 'WilderWild', promenade: 'promenade',
	beautify: 'beautify-fabric', ...MACAWS };
const MOD_CACHE = path.join(__dirname, `.mods-${MC_VERSION}`);
const DEFAULT_MODS = path.join(ROOT, '..', 'cozy-core-test', 'mods'); // filled by tools/fetch-test-mods.js

function extractMods(modsDir) {
	const roots = {};
	for (const modid of [...WOOD_MODS, ...DECOR_MODS]) {
		const cached = fs.existsSync(MOD_CACHE) && fs.readdirSync(MOD_CACHE).find(d => d.startsWith(JAR_PREFIX[modid]));
		if (cached) { roots[modid] = path.join(MOD_CACHE, cached, 'data'); continue; }
		const jar = fs.existsSync(modsDir) && fs.readdirSync(modsDir).find(f => f.startsWith(JAR_PREFIX[modid]) && f.endsWith('.jar'));
		if (!jar) throw new Error(`No ${modid} jar in ${modsDir}: run node tools/fetch-test-mods.js "<test folder>" first`);
		const out = path.join(MOD_CACHE, jar.slice(0, -4));
		unzipSome(path.join(modsDir, jar), ['data/*/recipe/*', 'data/*/tags/item/*', 'data/*/tags/item/*/*'], out);
		roots[modid] = path.join(out, 'data');
	}
	return roots;
}

// Every data folder whose tags count: vanilla's plus each mod's (tags with one name merge, as in game).
const dataRoots = [path.dirname(VANILLA)];

function loadRecipes(dataRoot, ns) { // the recipes in <dataRoot>/<ns>/recipe/, by id
	const dir = path.join(dataRoot, ns, 'recipe'), out = {};
	if (!fs.existsSync(dir)) return out;
	for (const f of fs.readdirSync(dir)) if (f.endsWith('.json')) out[`${ns}:${f.slice(0, -5)}`] = readJson(path.join(dir, f));
	return out;
}

// Conventional tags that Fabric API / NeoForge define in code-shipped data, not in the mod jars.
const LOADER_TAGS = { 'c:rods/wooden': ['minecraft:stick'] };
const STICKS = new Set(['minecraft:stick', '#c:rods/wooden']);

const tagCache = {};
function itemTag(id) { // '#minecraft:oak_logs' -> Set of item ids (nested tags expanded)
	const name = id.replace(/^#/, '');
	if (tagCache[name]) return tagCache[name];
	const [ns, p] = name.split(':');
	let values = ns === NS ? ownItemTags[p] : LOADER_TAGS[name];
	if (!values) {
		const files = dataRoots.map(r => path.join(r, ns, 'tags', 'item', `${p}.json`)).filter(f => fs.existsSync(f));
		if (files.length) values = files.flatMap(f => readJson(f).values);
	}
	if (!values) throw new Error(`Unknown item tag ${id}`);
	tagCache[name] = new Set(); // guards against a tag that includes itself
	const set = new Set();
	for (const raw of values) {
		const v = typeof raw === 'string' ? raw : raw.id; // { id, required: false } entries
		if (!v.startsWith('#')) { set.add(v); continue; }
		try { for (const i of itemTag(v)) set.add(i); } catch (e) { if (raw.required !== false) throw e; }
	}
	return (tagCache[name] = set);
}

// An ingredient is an id, '#tag' or a list of ids -> Set of item ids.
function ingredientItems(ing) {
	if (Array.isArray(ing)) return new Set(ing.flatMap(i => [...ingredientItems(i)]));
	if (typeof ing === 'string') return ing.startsWith('#') ? itemTag(ing) : new Set([ing]);
	throw new Error(`Unexpected ingredient ${JSON.stringify(ing)}`);
}

// ---------- writing ----------

const written = [];
function write(rel, obj) {
	const file = path.join(OUT, ...rel.split('/'));
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, typeof obj === 'string' ? obj : JSON.stringify(obj, null, 2) + '\n');
	written.push(rel);
}

const ownRecipes = {}; // id -> recipe, also checked for clashes
const ownItemTags = {};
const unlocks = []; // { name, items (ingredient), recipes: [ids] }

// Fabric and NeoForge skip a file whose mod isn't installed. Plain vanilla doesn't know these
// keys, which is why mod recipes live in their own folder (see the README's vanilla note).
const modCondition = modid => ({
	'fabric:load_conditions': [{ condition: 'fabric:all_mods_loaded', values: [modid] }],
	'neoforge:conditions': [{ type: 'neoforge:mod_loaded', modid }],
});

let currentMod = null; // set while generating one mod's recipes
function recipe(name, obj) {
	const full = currentMod ? `compat/${currentMod}/${name}` : name;
	const id = `${NS}:${full}`;
	if (ownRecipes[id]) {
		if (JSON.stringify(ownRecipes[id]) === JSON.stringify(obj)) return id; // the same recipe reached twice
		throw new Error(`Two different recipes named ${id}`);
	}
	ownRecipes[id] = obj;
	write(`data/${NS}/recipe/${full}.json`, currentMod ? { ...modCondition(currentMod), ...obj } : obj);
	return id;
}

const item = id => id.replace(/^#/, '').replace(/^minecraft:/, '').replace(':', '_');
const cuts = new Set(); // ingredient + result + count, so a mod re-declaring vanilla wood adds no duplicates
function stonecutting(input, result, count) {
	const key = JSON.stringify([input, result, count]);
	if (cuts.has(key)) return null;
	cuts.add(key);
	return recipe(`${item(result).replace(`${currentMod}_`, '')}_from_${item(input).replace(`${currentMod}_`, '')}_stonecutting`,
		{ type: 'minecraft:stonecutting', ingredient: input, result: count > 1 ? { count, id: result } : { id: result } });
}

const recipeInputs = r => r.type === 'minecraft:crafting_shaped' ? Object.values(r.key)
	: r.type === 'minecraft:crafting_shapeless' ? r.ingredients : [];

// ---------- 1. wood in the stonecutter (cheap: 1 plank -> 1 fence, door, sign...) ----------

const PER_PLANK = { // vanilla recipe group -> how many one plank makes in the stonecutter
	wooden_stairs: 1, wooden_slab: 2, wooden_fence: 1, wooden_fence_gate: 1, wooden_door: 1,
	wooden_trapdoor: 1, wooden_pressure_plate: 1, wooden_button: 1, wooden_sign: 1,
	// wooden_hanging_sign is left out on purpose: its recipe needs chains, so a plank-only
	// stonecutter recipe would make iron for free.
};

// recipes: one source's recipes (vanilla's, or one mod's own). Returns the planks it covered.
function woodcutting(recipes) {
	const sources = {}; // planks id -> [{ logs: ingredient, perLog }], e.g. logs and hollowed logs
	for (const r of Object.values(recipes))
		if (r.group === 'planks' && r.type === 'minecraft:crafting_shapeless' && r.ingredients.length === 1)
			(sources[r.result.id] ??= []).push({ logs: r.ingredients[0], perLog: r.result.count || 1 });

	let n = 0;
	for (const [plank, logSources] of Object.entries(sources)) {
		for (const { logs, perLog } of logSources) { stonecutting(logs, plank, perLog); n++; }
		for (const r of Object.values(recipes)) {
			const per = PER_PLANK[r.group];
			// Only recipes made of this plank and sticks (sticks are planks too). Anything needing
			// another material, like a hanging sign's chains, stays a crafting-table recipe.
			if (!per || !recipeInputs(r).every(i => i === plank || STICKS.has(i))) continue;
			if (!recipeInputs(r).includes(plank)) continue;
			stonecutting(plank, r.result.id, per); n++;
			for (const { logs, perLog } of logSources) { stonecutting(logs, r.result.id, per * perLog); n++; }
		}
	}
	// Bark (log -> wood, stem -> hyphae, stripped too) and shelves (stripped log -> shelf):
	// the same rate as crafting, without the 2x2 or the leftover logs.
	for (const r of Object.values(recipes)) {
		if ((r.group !== 'bark' && r.group !== 'shelf') || r.type !== 'minecraft:crafting_shaped') continue;
		const inputs = new Set(recipeInputs(r));
		if (inputs.size !== 1) continue;
		stonecutting([...inputs][0], r.result.id, 1); n++;
	}
	return { n, planks: Object.keys(sources) };
}

// ---------- 1b. decoration mods' wooden pieces, at the same cheap rate as vanilla's ----------
// A piece made of one vanilla wood (its planks, slabs or logs, plus sticks) is cut like a vanilla
// trapdoor or door: one per plank, and a log's worth (four) per log. A piece whose own recipe is
// already cheaper than that keeps its rate per plank. Pieces needing anything that isn't wood (a
// picture frame's wool) or mixing woods stay crafting-table recipes.

const woodOf = {}; // vanilla plank, slab or log item -> { plank, planks }: which wood, and how many planks it's worth
const logSources = {}; // plank -> [{ logs, perLog }]

function learnWoods(recipes) {
	for (const r of Object.values(recipes)) {
		if (r.group === 'planks' && r.type === 'minecraft:crafting_shapeless' && r.ingredients.length === 1) {
			const plank = r.result.id, perLog = r.result.count || 1;
			(logSources[plank] ??= []).push({ logs: r.ingredients[0], perLog });
			woodOf[plank] = { plank, planks: 1 };
			for (const log of ingredientItems(r.ingredients[0])) woodOf[log] ??= { plank, planks: perLog };
		}
		if (r.group === 'wooden_slab' && r.type === 'minecraft:crafting_shaped') woodOf[r.result.id] = { plank: recipeInputs(r)[0], planks: 0.5 };
	}
}

// Pieces worth more than a log (4 planks: Macaw's desks, big gates, the bigger roofs...) are cut
// from logs only, one per log: a 10-plank desk at one per plank would be 10 times cheaper than
// crafting it. Up to a log's worth, the cheap rate holds, so nothing costs more in the stonecutter
// than at the crafting table (Gabby's calls, 2026-10-15).
const BIG_PIECE = 4;

function decorCutting(recipes) {
	let n = 0;
	for (const r of Object.values(recipes)) {
		if (r.type !== 'minecraft:crafting_shaped' && r.type !== 'minecraft:crafting_shapeless') continue;
		// Every slot, counted (a shaped recipe's key letter can be used several times).
		const slots = r.type === 'minecraft:crafting_shaped' ? r.pattern.join('').replace(/ /g, '').split('').map(c => r.key[c]) : r.ingredients;
		if (slots.some(s => typeof s !== 'string' || (!STICKS.has(s) && !woodOf[s]))) continue; // not only wood
		const woods = new Set(slots.filter(s => woodOf[s]).map(s => woodOf[s].plank));
		if (woods.size !== 1) continue; // one wood type, so the piece matches its log
		const plank = [...woods][0];
		const cost = slots.reduce((s, i) => s + (STICKS.has(i) ? 0.5 : woodOf[i].planks), 0) / (r.result.count || 1);
		if (cost > BIG_PIECE + 1e-9) {
			for (const { logs, perLog } of logSources[plank] || []) {
				const count = Math.max(1, Math.floor(perLog / cost));
				if (perLog / count > cost + 1e-9) continue; // dearer than crafting it
				if (stonecutting(logs, r.result.id, count)) n++;
			}
			continue;
		}
		const perPlank = Math.max(1, Math.floor(1 / cost));
		stonecutting(plank, r.result.id, perPlank); n++;
		for (const { logs, perLog } of logSources[plank] || []) { stonecutting(logs, r.result.id, perPlank * perLog); n++; }
	}
	return n;
}

function bambooMosaic() { // bamboo's second plank: 1 bamboo plank -> 1 mosaic, mosaic -> stairs/slab
	stonecutting('minecraft:bamboo_planks', 'minecraft:bamboo_mosaic', 1);
	stonecutting('minecraft:bamboo_mosaic', 'minecraft:bamboo_mosaic_stairs', 1);
	stonecutting('minecraft:bamboo_mosaic', 'minecraft:bamboo_mosaic_slab', 2);
	return 3;
}

// ---------- 2. two wooden slabs -> one plank ----------

function slabsToPlanks(recipes, planks, unlockName) {
	const ids = [], slabs = [];
	for (const r of Object.values(recipes)) {
		if (r.group !== 'wooden_slab' || r.type !== 'minecraft:crafting_shaped') continue;
		const plank = recipeInputs(r)[0], slab = r.result.id;
		if (!planks.includes(plank) || slabs.includes(slab)) continue;
		// Two bamboo slabs stacked make bamboo mosaic in vanilla, so bamboo only works side by side.
		const obj = slab === 'minecraft:bamboo_slab'
			? { type: 'minecraft:crafting_shaped', category: 'building', group: 'planks_from_slabs', key: { '#': slab }, pattern: ['##'], result: { id: plank } }
			: { type: 'minecraft:crafting_shapeless', category: 'building', group: 'planks_from_slabs', ingredients: [slab, slab], result: { id: plank } };
		ids.push(recipe(`${item(plank).replace(`${currentMod}_`, '')}_from_slabs`, obj));
		slabs.push(slab);
	}
	if (ids.length) unlocks.push({ name: unlockName, items: slabs, recipes: ids, mod: currentMod });
	return ids.length;
}

// ---------- 3. re-dyeing ----------

const COLOURS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
	'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'];
const REDYE = [ // family, item id pattern, the vanilla group its dyed recipes use
	{ family: 'terracotta', id: c => `minecraft:${c}_terracotta`, group: 'stained_terracotta' },
	{ family: 'stained_glass', id: c => `minecraft:${c}_stained_glass`, group: 'stained_glass' },
	{ family: 'stained_glass_pane', id: c => `minecraft:${c}_stained_glass_pane`, group: 'stained_glass_pane' },
	{ family: 'concrete_powder', id: c => `minecraft:${c}_concrete_powder`, group: 'concrete_powder' },
];

function redye() {
	let n = 0;
	for (const f of REDYE) {
		const ids = [];
		ownItemTags[`dyed_${f.family}`] = COLOURS.map(f.id);
		write(`data/${NS}/tags/item/dyed_${f.family}.json`, { values: COLOURS.map(f.id) });
		for (const c of COLOURS) {
			// Like vanilla's re-dye wool recipes: the other 15 colours (never plain, which vanilla
			// already dyes, and never the colour itself, which would just eat the dye).
			const others = COLOURS.filter(o => o !== c).map(f.id);
			ids.push(recipe(`dye_${c}_${f.family}`, {
				type: 'minecraft:crafting_shaped', category: 'building', group: f.group,
				key: { '#': others, X: `minecraft:${c}_dye` }, pattern: ['###', '#X#', '###'],
				result: { count: 8, id: f.id(c) },
			}));
			n++;
		}
		unlocks.push({ name: `dye_${f.family}`, items: `#${NS}:dyed_${f.family}`, recipes: ids });
	}
	return n;
}

// ---------- 4. hard-to-get blocks ----------

function hardBlocks() {
	const scaffolding = recipe('scaffolding_from_sticks', {
		type: 'minecraft:crafting_shaped', key: { I: 'minecraft:stick', '~': 'minecraft:string' },
		pattern: ['I~I', 'I I', 'I I'], result: { count: 3, id: 'minecraft:scaffolding' }, // vanilla bamboo makes 6
	});
	unlocks.push({ name: 'scaffolding_from_sticks', items: 'minecraft:string', recipes: [scaffolding] });

	const redSand = recipe('red_sand', {
		type: 'minecraft:crafting_shaped', category: 'building',
		key: { '#': 'minecraft:sand', X: 'minecraft:red_dye' }, pattern: ['###', '#X#', '###'],
		result: { count: 8, id: 'minecraft:red_sand' },
	});
	unlocks.push({ name: 'red_sand', items: 'minecraft:red_dye', recipes: [redSand] });

	const lichen = recipe('glow_lichen', {
		type: 'minecraft:crafting_shapeless', category: 'building',
		ingredients: ['minecraft:vine', 'minecraft:glow_ink_sac'], result: { count: 4, id: 'minecraft:glow_lichen' },
	});
	unlocks.push({ name: 'glow_lichen', items: 'minecraft:glow_ink_sac', recipes: [lichen] });

	const moss = recipe('moss_block_from_leaves', {
		type: 'minecraft:crafting_shapeless', category: 'building',
		ingredients: ['#minecraft:leaves', 'minecraft:bone_meal'], result: { id: 'minecraft:moss_block' },
	});
	unlocks.push({ name: 'moss_block_from_leaves', items: '#minecraft:leaves', recipes: [moss] });
	return 4;
}

// ---------- 5. recipe book unlocks (same shape as vanilla's recipes/ advancements) ----------

function recipeUnlocks() {
	for (const u of unlocks) write(`data/${NS}/advancement/recipes/${u.mod ? `compat/${u.mod}/` : ''}${u.name}.json`, {
		...(u.mod ? modCondition(u.mod) : {}),
		parent: 'minecraft:recipes/root',
		criteria: {
			has_items: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: u.items }] } },
			has_the_recipe: { trigger: 'minecraft:recipe_unlocked', conditions: { recipes: u.recipes[0] } },
		},
		requirements: [['has_the_recipe', 'has_items']],
		rewards: { recipes: u.recipes },
	});
}

// ---------- 6. trades ----------

function trade(pathName, obj, tag) {
	write(`data/${NS}/villager_trade/${pathName}.json`, obj);
	(tradeTags[tag] ??= []).push(`${NS}:${pathName}`);
}
const tradeTags = {};
const emerald = (count = 1) => count > 1 ? { count, id: 'minecraft:emerald' } : { id: 'minecraft:emerald' };
const stack = (id, count = 1) => count > 1 ? { count, id } : { id };

function trades() {
	// Wandering trader: the plants it doesn't sell yet, priced like its saplings (5 emeralds),
	// and calcite priced like its sand.
	for (const plant of ['azalea', 'flowering_azalea', 'crimson_fungus', 'warped_fungus'])
		trade(`wandering_trader/emerald_${plant}`, {
			gives: stack(`minecraft:${plant}`), max_uses: 8, reputation_discount: 0.05, wants: emerald(5),
		}, 'wandering_trader/common');
	trade('wandering_trader/emerald_calcite', {
		gives: stack('minecraft:calcite', 8), max_uses: 8, reputation_discount: 0.05, wants: emerald(),
	}, 'wandering_trader/common');

	// Mason: builder stone at levels 1 and 2, priced between vanilla bricks (10) and chiseled stone bricks (4).
	const mason = [
		[1, 'tuff', 8, 1], [1, 'mud_bricks', 8, 1],
		[2, 'calcite', 6, 5], [2, 'packed_mud', 8, 5],
	];
	for (const [level, block, count, xp] of mason)
		trade(`mason/${level}/emerald_${block}`, {
			gives: stack(`minecraft:${block}`, count), max_uses: 16, reputation_discount: 0.05, wants: emerald(), xp,
		}, `mason/level_${level}`);

	// No cartographer maps: maps can only point at structures, not biomes, and a badlands
	// mineshaft map was tried and dropped (2026-10-13): badlands are usually farther than the
	// 100-chunk search, so the offer rarely appeared, and searching farther freezes the game.

	// Appends to vanilla's pools (no "replace"). Each entry is optional: trades only load when a
	// world opens, so right after installing with /reload they don't exist yet, and a required
	// entry would make the game drop the whole tag, vanilla's trades included, until a restart.
	for (const [tag, values] of Object.entries(tradeTags))
		write(`data/minecraft/tags/villager_trade/${tag}.json`, { values: values.map(id => ({ id, required: false })) });

	// One more slot where Gabby chose "add a slot", so vanilla trades are never pushed out.
	// These replace vanilla files, so copy them and change only the amount.
	for (const [set, extra] of [['wandering_trader/common', 1], ['mason/level_1', 1], ['mason/level_2', 1]]) {
		const v = vanillaJson(`trade_set/${set}.json`);
		write(`data/minecraft/trade_set/${set}.json`, { ...v, amount: v.amount + extra });
	}
}

// ---------- 6b. the Cozy Core advancements tab ----------
// Gabby's picks (2026-10-17): a "Cozy Core" tab that opens on picking up planks, with six steps.
// The Hearth Lantern and the trades are mod-only, so those three steps (MOD_ONLY_ADVANCEMENTS) stay
// out of the data pack, and tools/build.js gives the data pack's tab a vanilla lantern for its icon.
// No rewards. Each title and description has its English text as the fallback, so the data pack
// (which has no language file) reads properly too; the mod's en_us.json carries the same text.
const TAB = 'cozy';
const MOD_ONLY_ADVANCEMENTS = ['the_kettles_on', 'far_flung_flowers', 'built_to_last'];
const ADVANCEMENT_TEXT = {
	root: ['Cozy Core', 'Pick up some planks and start building'],
	the_kettles_on: ["The kettle's on", 'Craft a Hearth Lantern'],
	plank_to_porch: ['Plank to porch', 'Cut wood in a stonecutter'],
	a_fresh_coat: ['A fresh coat', 'Re-dye terracotta, stained glass, panes or concrete powder'],
	green_thumb: ['Green thumb', 'Craft moss from leaves, or glow lichen from vines'],
	far_flung_flowers: ['Far-flung flowers', 'Buy an azalea or a fungus from a wandering trader'],
	built_to_last: ['Built to last', 'Buy tuff or mud bricks from a mason'],
};
const advancementKey = (name, part) => `advancements.${NS}.${TAB}.${name}.${part}`;
const text = (name, part) => ({ translate: advancementKey(name, part), fallback: ADVANCEMENT_TEXT[name][part === 'title' ? 0 : 1] });

function advancementsTab() {
	const ours = id => { if (!ownRecipes[id] && id !== `${NS}:hearth_lantern`) throw new Error(`The advancements tab names a recipe that isn't there: ${id}`); return id; };
	const ids = filter => Object.keys(ownRecipes).filter(id => !id.startsWith(`${NS}:compat/`) && filter(id, ownRecipes[id])).map(ours);
	const crafted = recipes => ({ trigger: 'minecraft:recipe_crafted', conditions: { recipes } });
	const traded = items => ({ trigger: 'minecraft:villager_trade', conditions: { item: { items } } });
	const steps = {
		the_kettles_on: { icon: `${NS}:hearth_lantern`, criterion: crafted([`${NS}:hearth_lantern`]) },
		plank_to_porch: { icon: 'minecraft:stonecutter', criterion: crafted(ids((id, r) => r.type === 'minecraft:stonecutting')) },
		a_fresh_coat: { icon: 'minecraft:magenta_glazed_terracotta', criterion: crafted(ids(id => /^cozy_core:dye_/.test(id))) },
		green_thumb: { icon: 'minecraft:moss_block', criterion: crafted([ours(`${NS}:moss_block_from_leaves`), ours(`${NS}:glow_lichen`)]) },
		far_flung_flowers: { icon: 'minecraft:flowering_azalea', criterion: traded(['minecraft:azalea', 'minecraft:flowering_azalea', 'minecraft:crimson_fungus', 'minecraft:warped_fungus']) },
		built_to_last: { icon: 'minecraft:mud_bricks', criterion: traded(['minecraft:tuff', 'minecraft:mud_bricks']) },
	};
	for (const [name, step] of Object.entries(steps)) {
		if (step.criterion.conditions.recipes?.length === 0) throw new Error(`The advancement ${name} names no recipes`);
	}
	// The mod's language file must say the same as the fallbacks here.
	const lang = readJson(path.join(ROOT, 'common', 'src', 'main', 'resources', 'assets', NS, 'lang', 'en_us.json'));
	for (const name of Object.keys(ADVANCEMENT_TEXT)) for (const part of ['title', 'description']) {
		if (lang[advancementKey(name, part)] !== text(name, part).fallback) throw new Error(`en_us.json's ${advancementKey(name, part)} doesn't match the generator's text`);
	}
	write(`data/${NS}/advancement/${TAB}/root.json`, {
		criteria: { planks: { trigger: 'minecraft:inventory_changed', conditions: { items: [{ items: '#minecraft:planks' }] } } },
		display: {
			announce_to_chat: false, background: 'minecraft:gui/advancements/backgrounds/stone',
			description: text('root', 'description'), icon: { id: `${NS}:hearth_lantern` }, show_toast: false, title: text('root', 'title'),
		},
		requirements: [['planks']],
	});
	for (const [name, step] of Object.entries(steps)) write(`data/${NS}/advancement/${TAB}/${name}.json`, {
		parent: `${NS}:${TAB}/root`,
		criteria: { [name]: step.criterion },
		display: { description: text(name, 'description'), icon: { id: step.icon }, title: text(name, 'title') },
		requirements: [[name]],
	});
	return Object.keys(steps).length + 1;
}

// ---------- 7. checks ----------

// Two crafting recipes clash when one grid can match both; the game then picks one silently.
function shapeOf(r) { // list of rows of Sets (null = empty), or null for shapeless
	if (r.type !== 'minecraft:crafting_shaped') return null;
	return r.pattern.map(row => [...row].map(ch => ch === ' ' ? null : ingredientItems(r.key[ch])));
}
const slotsOf = r => r.type === 'minecraft:crafting_shapeless'
	? r.ingredients.map(ingredientItems) : shapeOf(r).flat().filter(Boolean);
const meets = (a, b) => [...a].some(i => b.has(i));
function multisetCanMatch(a, b) { // can each slot of a pair with a different slot of b, sharing an item?
	if (a.length !== b.length) return false;
	const used = new Array(b.length).fill(false);
	const go = i => i === a.length || b.some((s, j) => !used[j] && meets(a[i], s) && (used[j] = true, go(i + 1) || (used[j] = false)));
	return go(0);
}
function gridsMatch(a, b) {
	const same = (x, y) => x.length === y.length && x.every((row, i) => row.length === y[i].length &&
		row.every((s, j) => (s === null) === (y[i][j] === null) && (s === null || meets(s, y[i][j]))));
	return same(a, b) || same(a, b.map(row => [...row].reverse())); // shaped recipes also match mirrored
}
function clash(a, b) {
	const crafting = t => t === 'minecraft:crafting_shaped' || t === 'minecraft:crafting_shapeless';
	if (!crafting(a.type) || !crafting(b.type)) {
		return a.type === 'minecraft:stonecutting' && b.type === 'minecraft:stonecutting'
			&& meets(ingredientItems(a.ingredient), ingredientItems(b.ingredient))
			&& a.result.id === b.result.id && (a.result.count || 1) !== (b.result.count || 1);
	}
	if (a.type === 'minecraft:crafting_shaped' && b.type === 'minecraft:crafting_shaped') return gridsMatch(shapeOf(a), shapeOf(b));
	return multisetCanMatch(slotsOf(a), slotsOf(b));
}

function check(recipes) {
	const problems = [], unreadable = new Set();
	const ours = Object.entries(ownRecipes);
	const all = [...Object.entries(recipes), ...ours];
	for (const [id, r] of ours) for (const [other, o] of all) {
		if (other === id || (other.startsWith(NS) && other < id)) continue;
		try { if (clash(r, o)) problems.push(`${id} clashes with ${other}`); }
		catch { unreadable.add(other); } // a format or tag this script can't read (e.g. another mod's)
	}
	if (unreadable.size) console.log(`Clash check skipped ${unreadable.size} recipes it can't read:`, [...unreadable].slice(0, 8).join(' '));

	// No loops that make blocks out of nothing: for every stonecutting a -> n b, any recipe
	// turning b back into a must give back at most 1/n of an a.
	for (const [id, r] of ours) {
		if (r.type !== 'minecraft:stonecutting') continue;
		for (const input of ingredientItems(r.ingredient)) for (const [bid, b] of all) {
			if ((b.result?.id) !== input || unreadable.has(bid)) continue;
			const slots = b.type === 'minecraft:stonecutting' ? [ingredientItems(b.ingredient)] : (b.type?.startsWith('minecraft:crafting_s') ? slotsOf(b) : []);
			const used = slots.filter(s => s.has(r.result.id)).length;
			if (!used) continue;
			const back = (b.result.count || 1) / used; // inputs back per one of our outputs
			if (back * (r.result.count || 1) > 1 + 1e-9) problems.push(`loop: ${id} then ${bid} makes more ${input}`);
		}
	}
	return problems;
}

// ---------- main ----------

extractVanilla(process.argv[2] || DEFAULT_JAR);
const modRoots = extractMods(process.argv[3] || DEFAULT_MODS);
dataRoots.push(...Object.values(modRoots));
const recipes = loadRecipes(path.dirname(VANILLA), 'minecraft');
const allRecipes = { ...recipes };
for (const root of Object.values(modRoots)) for (const ns of fs.readdirSync(root)) Object.assign(allRecipes, loadRecipes(root, ns));
fs.rmSync(OUT, { recursive: true, force: true });

write('pack.mcmeta', {
	pack: {
		description: { text: DESCRIPTION, color: 'gray' },
		min_format: DATA_FORMAT, max_format: DATA_FORMAT,
	},
});
const wood = woodcutting(recipes);
const counts = {
	woodcutting: wood.n + bambooMosaic(), planks_from_slabs: slabsToPlanks(recipes, wood.planks, 'planks_from_slabs'),
	redye: redye(), hard_blocks: hardBlocks(),
};
// Which vanilla wood each plank, slab and log belongs to, for the decoration mods' pieces. Vanilla
// only: a decoration recipe's load condition names just its own mod, so it mustn't use another mod's
// logs (Wilder Wild's hollowed logs, say). Beautify only makes vanilla woods.
learnWoods(recipes);
for (const [modid, root] of Object.entries(modRoots)) {
	currentMod = modid;
	if (DECOR_MODS.includes(modid)) { counts[modid] = { decor: decorCutting(loadRecipes(root, modid)) }; continue; }
	const own = loadRecipes(root, modid); // only the mod's own recipe folder
	const modWood = woodcutting(own);
	counts[modid] = { woodcutting: modWood.n, planks_from_slabs: slabsToPlanks(own, modWood.planks, 'planks_from_slabs') };
}
currentMod = null;
recipeUnlocks();
counts.advancements = advancementsTab();
trades();

const problems = check(allRecipes);
console.log(`Wrote ${written.length} files to pack/:`, counts);
if (problems.length) { console.error(problems.join('\n')); process.exit(1); }
console.log('No recipe clashes or block-making loops found.');
