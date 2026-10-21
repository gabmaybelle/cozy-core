package com.gabbymaybelle.cozycore.gametest;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HearthLanterns;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Films the YouTube Short with {@link Film}: a screenshot every tick, in a tall 1080x1920 window.
 * Only runs with `./gradlew :fabric:runClientGameTest -PvideoTour`. Frames go in
 * build/run/clientGameTest/video_tour.
 *
 * <p>"Light a hearth. Home is safe.", as an evening at the cottage, sunset into dusk, with a Hearth
 * Lantern hanging from a lamp post on the front lawn (the default 32-block reach). A creeper goes
 * off on the garden path and the cottage, its flowers and the cat on the porch come through it; the
 * lantern is hung and "Home" shows; lightning strikes the garden in a storm and nothing burns, and
 * the pig stays a pig; a quiet wide shot leads back into the first. Each scene sets up what it
 * needs, so any one can be filmed alone. With {@code -PvideoWorld=<zip>} the set sits beside a saved
 * world's kept "cottage" spot; without it, in a plain spot near spawn.
 */
public class VideoTour implements FabricClientGameTest {
	/** Names the system properties: cozycore.videoTour, cozycore.videoScenes... */
	private static final String PREFIX = "cozycore";
	/** What a scene clears away before it starts. */
	private static final List<String> CLEAR = List.of("zombie", "skeleton", "spider", "creeper", "enderman", "witch",
			"zombie_villager", "slime", "cat", "pig", "zombified_piglin", "wandering_trader", "trader_llama", "villager",
			"lightning_bolt", "item");
	/** Sunset, and dusk just after it (the lanterns glow, the sky is still warm), then night. */
	private static final int SUNSET = 12300, DUSK = 13000, NIGHT = 14200;
	/**
	 * The sun just over the horizon (the day's clock wraps at 24000), and an hour or so after it.
	 * At 23300 the shader's sky was still dark as dusk (scout, 2026-10-20).
	 */
	private static final int SUNRISE = 23850, MORNING = 1000;

	private Film film;
	/** Whether walks run at the world's pace: the player gets two of their own ticks per frame otherwise. */
	private boolean realPace;
	/** The cottage's spot: the end of its front path, facing north; y is the ground the player stands on. */
	private int sx, sy, sz;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!Film.on(PREFIX)) {
			return;
		}
		film = new Film(context, PREFIX, "video_tour");
		film.take(() -> film.newWorld("cozy core hearth"), this::stage, this::scenes);
	}

	// ------------------------------------------------------------------ the set

	/** A new world (no saved one given): the set goes a little way from spawn, on its ground. */
	private void stage() {
		var spawn = film.server.computeOnServer(s -> s.overworld().getRespawnData().pos());
		int x = spawn.getX() + 20, z = spawn.getZ();
		film.keep("cottage", x + " " + film.ground(x, z) + " " + z);
	}

	private void readSet() {
		String[] s = film.kept("cottage").split(" ");
		sx = Integer.parseInt(s[0]);
		sy = Integer.parseInt(s[1]);
		sz = Integer.parseInt(s[2]);
	}

	/** A block {@code east} blocks east, {@code up} up and {@code south} blocks south of the spot. */
	private BlockPos at(int east, int up, int south) {
		return new BlockPos(sx + east, sy + up, sz + south);
	}

	private Vec3 point(double east, double up, double south) {
		return new Vec3(sx + east, sy + up, sz + south);
	}

	private void cmd(String format, Object... args) {
		film.server.runCommand(String.format(Locale.ROOT, format, args));
	}

	private void set(BlockPos p, String block) {
		cmd("setblock %d %d %d %s", p.getX(), p.getY(), p.getZ(), block);
	}

	/** The foot of the lamp post on the front lawn, west of the path, out of the porch's view. */
	private BlockPos post() {
		return at(-3, 0, 1);
	}

	/** The chain hanging from the lamp post's arm, reaching toward the path. */
	private BlockPos chain() {
		return post().offset(1, 3, 0);
	}

	/** Where the Hearth Lantern hangs: under the chain. */
	private BlockPos lantern() {
		return chain().below();
	}

	/**
	 * A garden lamp post in the cottage's spruce and stone (after common Minecraft lamp post
	 * designs): chiseled stone bricks and a stone brick wall for the foot, a spruce fence post with
	 * trapdoor hoods, and an arm toward the path with a chain for the lantern to hang from.
	 */
	private void lampPost(boolean lantern) {
		BlockPos p = post();
		set(p, "minecraft:chiseled_stone_bricks");
		set(p.above(1), "minecraft:stone_brick_wall");
		set(p.above(2), "minecraft:spruce_fence");
		set(p.above(3), "minecraft:spruce_fence");
		set(p.above(4), "minecraft:spruce_fence");
		set(chain().above(), "minecraft:spruce_fence");
		// Thin spruce trapdoor hoods over the post and over the lantern (Gabby's pick: lighter than a slab).
		set(p.above(5), "minecraft:spruce_trapdoor[half=bottom]");
		set(chain().above(2), "minecraft:spruce_trapdoor[half=bottom]");
		set(chain(), "minecraft:iron_chain");
		set(lantern(), lantern ? HearthLantern.ID + "[hanging=true]" : "minecraft:air");
	}

	/**
	 * Every scene starts from this: nothing left over from the last scene, Normal difficulty (a
	 * creeper vanishes on Peaceful), clear weather at {@code time}, and the lamp post with its
	 * lantern hanging or not.
	 */
	private void reset(boolean lantern, int time) {
		for (String type : CLEAR) {
			cmd("kill @e[type=minecraft:%s]", type);
		}
		cmd("difficulty normal");
		cmd("gamerule spawn_mobs false");
		cmd("weather clear");
		cmd("time set %d", time);
		// The default reach, after any scene that shortened it.
		cmd("gamerule cozy_core:hearth_lantern_radius 32");
		lampPost(lantern);
		// On the porch first, so a scene that starts at home doesn't open on the "Home" sign: it
		// shows here, off camera, and is cleared below.
		BlockPos porch = at(0, 1, -4);
		cmd("tp @a %d.5 %d %d.5", porch.getX(), porch.getY(), porch.getZ());
		// Off camera: an animal cleared away plays its death for a second, and rain from the storm
		// scene takes a few seconds to fade once the weather clears.
		boolean raining = film.ctx.computeOnClient(mc -> mc.level.getRainLevel(1) > 0);
		film.ctx.waitTicks(raining ? 160 : 30);
		film.ctx.runOnClient(mc -> mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false));
	}

	/** Every block that isn't air in the box: the cottage, its porch, flowers and front garden. */
	private int cottage() {
		BlockPos from = at(-8, -1, -18), to = at(8, 13, 3);
		return film.server.computeOnServer(s -> {
			int n = 0;
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				if (!s.overworld().getBlockState(p).isAir()) {
					n++;
				}
			}
			return n;
		});
	}

	/** The first living thing of this class near {@code at}, or null. */
	private static <T extends LivingEntity> T near(MinecraftServer s, Class<T> type, BlockPos at) {
		List<T> found = s.overworld().getEntitiesOfClass(type, new AABB(at).inflate(6));
		return found.isEmpty() ? null : found.getFirst();
	}

	// ------------------------------------------------------------------ the scenes

	/** The Short's scenes (tall), then the showcase's (wide), in running order. */
	private static final List<String> SHORT = List.of("s_edge", "s_porch", "s_name", "s_roof", "s_dye", "s_cut", "s_wide", "s_home", "s_storm");
	/**
	 * The Short "Light a hearth" (Gabby, 2026-10-21): tall, about 15 s. She hangs the Hearth Lantern
	 * at sunset, night comes and nothing spawns on the lawn, the porch rests quiet, and at sunrise she
	 * looks up at the lantern from the first shot's spot, so the Short loops.
	 */
	private static final List<String> TALL = List.of("t_home", "t_night", "t_calm", "t_morning");
	private static final List<String> DAY = List.of("d_dawn", "d_home", "d_cut", "d_dye", "d_roof", "d_trader", "d_anvil", "d_name", "d_styles",
			"d_night", "d_storm", "d_porch", "d_morning");

	private void scenes() {
		readSet();
		if (SHORT.stream().anyMatch(film::want)) {
			film.window(1080, 1920, 3);
			// The Short, "coming home at dusk": a beat for each proof in the message house.
			film.scene("s_edge", this::edge);
			film.scene("s_porch", () -> porch(DUSK, false));
			film.scene("s_name", this::name);
			film.scene("s_roof", () -> roof(SUNSET));
			film.scene("s_dye", () -> dye(SUNSET));
			film.scene("s_cut", () -> stonecutter(SUNSET));
			film.scene("s_wide", () -> wide(DUSK));
			// Filmed for the second Short.
			film.scene("s_home", () -> home(SUNSET));
			film.scene("s_storm", () -> storm(DUSK));
		}
		if (TALL.stream().anyMatch(film::want)) {
			film.window(1080, 1920, 3);
			// Walks at the world's pace (kit README: the player otherwise moves at double speed in a take).
			realPace = true;
			film.scene("t_home", () -> home(SUNSET));
			film.scene("t_night", this::night);
			film.scene("t_calm", this::calm);
			film.scene("t_morning", this::hearthMorning);
			realPace = false;
		}
		if (DAY.stream().anyMatch(film::want)) {
			showcase();
		}
	}

	/**
	 * The showcase, "a day at the cottage" (Gabby, 2026-10-20): wide, about 1:45, sunrise to sunrise,
	 * no captions that explain features. The lamp post stands empty at dawn; she hangs the Hearth
	 * Lantern first thing (so the morning's building already happens at home), works on the cottage,
	 * names her home in the evening, and the night comes; the next sunrise finds the lantern glowing.
	 */
	private void showcase() {
		film.window(1600, 900, 3);
		// Morning.
		film.scene("d_dawn", () -> dawn(false));
		film.scene("d_home", () -> home(MORNING));
		film.scene("d_cut", () -> stonecutter(MORNING));
		film.scene("d_dye", () -> dye(MORNING));
		film.scene("d_roof", () -> roof(MORNING + 1500));
		film.scene("d_trader", () -> trader(MORNING + 3000));
		// Evening.
		film.scene("d_anvil", () -> anvil(SUNSET));
		film.scene("d_name", this::name);
		film.scene("d_styles", this::styles);
		// Night: clear when it falls, then the storm rolls in.
		film.scene("d_night", this::night);
		film.scene("d_storm", () -> storm(NIGHT + 800));
		film.scene("d_porch", () -> porch(NIGHT + 1300, true));
		// Morning again.
		film.scene("d_morning", () -> dawn(true));
	}

	/** A cat sitting on the porch step, tame and staying put. */
	private void porchCat(BlockPos step) {
		film.server.runOnServer(s -> {
			Cat cat = EntityTypes.CAT.create(s.overworld(), EntitySpawnReason.COMMAND);
			cat.snapTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 180, 0);
			cat.tame(s.getPlayerList().getPlayers().getFirst());
			cat.setOrderedToSit(true);
			cat.setInSittingPose(true);
			cat.setPersistenceRequired();
			s.overworld().addFreshEntity(cat);
		});
	}

	/** The garden end of the dawn shots: south-east on the lawn, the lamp post between it and the porch. */
	private Vec3 dawnFar() {
		return point(11.5, 4.5, 15.5);
	}

	private Vec3 dawnNear() {
		return point(7.5, 3, 10.5);
	}

	private Vec3 dawnLook() {
		return point(-1.5, 2.2, -3.5);
	}

	/**
	 * Sunrise at the cottage, slow and quiet: the cat on the porch step, the lamp post on the lawn.
	 * The opening ({@code lantern} false): the post stands empty and the camera drifts in. The
	 * closing ({@code lantern} true): the night is over, the Hearth Lantern still glows on the post,
	 * the cat gets up, and the camera eases back out for the end card.
	 */
	private void dawn(boolean lantern) {
		reset(lantern, SUNRISE);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		BlockPos step = at(-1, 1, -4);
		porchCat(step);
		int before = cottage();
		Vec3 from = lantern ? dawnNear() : dawnFar(), to = lantern ? dawnFar() : dawnNear();
		film.camera(from, dawnLook());
		film.ctx.waitTicks(10);
		if (lantern) {
			film.dolly(100, from, from.lerp(to, 0.3), dawnLook(), dawnLook());
			// The cat stands up and stretches its legs.
			film.server.runOnServer(s -> {
				Cat c = near(s, Cat.class, step);
				if (c != null) {
					c.setOrderedToSit(false);
					c.setInSittingPose(false);
				}
			});
			film.log("cat", "up");
			film.dolly(160, from.lerp(to, 0.3), to, dawnLook(), dawnLook().add(0, 0.6, 0));
		} else {
			film.dolly(240, from, to, dawnLook(), dawnLook());
		}
		boolean hanging = film.server.computeOnServer(s -> s.overworld().getBlockState(lantern()).is(HearthLantern.BLOCK.get()));
		boolean cat = film.server.computeOnServer(s -> near(s, Cat.class, step) != null);
		film.note(lantern ? "morning" : "dawn", "lantern on the post " + hanging + "; cat " + (cat ? "on the porch" : "gone") + "; cottage " + before + " blocks");
		Film.check(hanging == lantern, lantern ? "the Hearth Lantern hangs on the post" : "the lamp post is empty");
		Film.check(cat, "the cat is on the porch");
	}

	/** The opening shot's camera, which the closing wide shot ends on so the Short loops. */
	private Vec3 porchEye() {
		return point(6.5, 2.6, 7.5);
	}

	private Vec3 porchLook() {
		return point(0, 2, -3);
	}

	/**
	 * Frame 1 is the payoff: dusk at the cottage, a cat on the porch, and a creeper hissing on the
	 * garden path. It goes off; the smoke clears on the same cottage, every block still there and
	 * the cat unhurt.
	 */
	private void porch(int time, boolean rain) {
		reset(true, time);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		BlockPos step = at(-1, 1, -4), path = at(1, 0, -1);
		porchCat(step);
		film.camera(porchEye(), porchLook());
		if (rain) {
			// The showcase's night: the storm has passed into steady rain. Let it set in before filming.
			cmd("weather rain");
			film.ctx.waitTicks(60);
		}
		int before = cottage();
		cmd("summon minecraft:creeper %d %d %d {NoAI:1b,Fuse:30,Rotation:[200f,0f]}", path.getX(), path.getY(), path.getZ());
		// Lit only once filming has begun: the first frame waits for the world to draw.
		film.record(wide(4, 24));
		light(path);
		for (int i = 0; i < 80 && film.server.computeOnServer(s -> near(s, Creeper.class, path) != null); i++) {
			film.record(1);
		}
		film.log("boom", "");
		film.dolly(wide(56, 90), porchEye(), porchEye().lerp(porchLook(), 0.15), porchLook(), porchLook());
		int after = cottage();
		float lost = film.server.computeOnServer(s -> {
			Cat c = near(s, Cat.class, step);
			return c == null ? -1f : c.getMaxHealth() - c.getHealth();
		});
		film.note("porch", "cottage " + before + " -> " + after + " blocks; cat " + (lost < 0 ? "gone" : "lost " + lost + " health"));
		Film.check(after == before, "the blast on the path broke nothing (" + before + " -> " + after + ")");
		Film.check(lost == 0, "the cat is unhurt (" + (lost < 0 ? "gone" : "lost " + lost) + ")");
	}

	/** Where the edge blast goes off: open lawn south-east of the lamp post, clear of the cottage and the path. */
	private BlockPos edgeBlast() {
		return at(5, 0, 9);
	}

	/** The opening shot's camera: south-east of the blast, looking back past it to the lantern and the cottage. */
	private Vec3 edgeEye() {
		return point(9.5, 7, 13.5);
	}

	private Vec3 edgeLook() {
		return point(4.5, 0, 8.5);
	}

	/**
	 * The opening, and the refuge in one picture: dusk on the front lawn, the Hearth Lantern on its
	 * post behind, and a creeper hissing on the very edge of home. It goes off; the wild half of the
	 * lawn is torn out and the crater stops at a line, the home half untouched. The reach is set
	 * with the mod's own rule so the edge passes under the creeper. The lawn is put back afterwards,
	 * off camera, for the scenes after it.
	 */
	private void edge() {
		reset(true, SUNSET + 400);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		BlockPos blast = edgeBlast(), lamp = lantern();
		long dx = blast.getX() - lamp.getX(), dz = blast.getZ() - lamp.getZ();
		// Just inside the creeper, so a little more than half the crater is wild.
		int reach = (int) Math.floor(Math.sqrt(dx * dx + dz * dz));
		cmd("gamerule cozy_core:hearth_lantern_radius %d", reach);
		BlockPos from = blast.offset(-6, -6, -6), to = blast.offset(6, 4, 6);
		List<net.minecraft.world.level.block.state.BlockState> lawn = film.server.computeOnServer(s -> {
			List<net.minecraft.world.level.block.state.BlockState> states = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				states.add(s.overworld().getBlockState(p));
			}
			return states;
		});
		int[] before = edgeCount(from, to, lamp, reach);
		film.camera(edgeEye(), edgeLook());
		cmd("summon minecraft:creeper %d %d %d {NoAI:1b,Fuse:30,Rotation:[150f,0f]}", blast.getX(), blast.getY(), blast.getZ());
		film.record(4);
		light(blast);
		for (int i = 0; i < 80 && film.server.computeOnServer(s -> near(s, Creeper.class, blast) != null); i++) {
			film.record(1);
		}
		film.log("boom", "");
		film.dolly(56, edgeEye(), edgeEye().lerp(edgeLook(), 0.15), edgeLook(), edgeLook());
		int[] after = edgeCount(from, to, lamp, reach);
		film.note("edge", "reach " + reach + "; home side " + before[0] + " -> " + after[0] + " blocks; wild side " + before[1] + " -> " + after[1]);
		// The lawn back as it was, for the scenes after this one.
		film.server.runOnServer(s -> {
			int i = 0;
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				s.overworld().setBlock(p, lawn.get(i++), 2);
			}
		});
		Film.check(after[0] == before[0], "the blast broke nothing at home (" + before[0] + " -> " + after[0] + ")");
		Film.check(before[1] - after[1] >= 8, "the blast tore up the wild side (" + before[1] + " -> " + after[1] + ")");
	}

	/** Blocks that aren't air in the box, as {home side, wild side} of a lantern with this reach (sideways distance, as the mod measures it). */
	private int[] edgeCount(BlockPos from, BlockPos to, BlockPos lamp, int reach) {
		return film.server.computeOnServer(s -> {
			int[] n = new int[2];
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				if (!s.overworld().getBlockState(p).isAir()) {
					long x = p.getX() - lamp.getX(), z = p.getZ() - lamp.getZ();
					n[x * x + z * z <= (long) reach * reach ? 0 : 1]++;
				}
			}
			return n;
		});
	}

	/** Lights the creeper standing at {@code at}, the way flint and steel would. */
	private void light(BlockPos at) {
		film.server.runOnServer(s -> s.overworld().getEntitiesOfClass(Creeper.class, new AABB(at).inflate(2)).forEach(Creeper::ignite));
		film.log("fuse", "");
	}

	/**
	 * Sunset, third person in Gabby's skin: the lantern is hung from the lamp post's chain and
	 * "Home" shows above the hotbar with the fireplace crackle.
	 */
	private void home(int time) {
		reset(false, time);
		film.hud(true);
		film.survival();
		cmd("clear @a");
		cmd("item replace entity @a hotbar.0 with %s", HearthLantern.ID);
		film.ctx.getInput().pressKey(o -> o.keyHotbarSlots[0]);
		BlockPos p = chain();
		// The underside of the chain, near its west side: the camera looks past the head at the crosshair.
		Vec3 top = new Vec3(p.getX() + 0.45, p.getY() + 0.02, p.getZ() + 0.5);
		film.camera(new Vec3(p.getX() + 0.5, sy, p.getZ() + 2.6), top);
		film.ctx.waitTicks(45);
		film.thirdPerson(true, 6);
		// A bigger HUD, so "Home" above the hotbar reads on a phone.
		film.bigGui();
		film.turnHead(top.add(-0.8, -0.6, 0));
		film.ctx.waitTicks(2);
		for (int i = 1; i <= 8; i++) {
			film.turnHead(top.add(-0.8, -0.6, 0).lerp(top, Film.ease(i / 8.0)));
			film.record(1);
		}
		BlockPos on = film.ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : null);
		Film.check(p.equals(on), "the crosshair is on the chain (it is on " + on + ")");
		// A right-click like a player's: the lantern hangs from the chain and the arm swings.
		film.ctx.getInput().pressKey(o -> o.keyUse);
		film.log("place", "hearth lantern");
		film.record(2);
		Film.check(film.server.computeOnServer(s -> s.overworld().getBlockState(lantern()).is(HearthLantern.BLOCK.get())), "the Hearth Lantern hangs from the chain");
		// Then the gaze drops to the foot of the post: the camera rises behind the head, and the
		// lantern now hanging there comes into view beside it.
		Vec3 foot = Vec3.atBottomCenterOf(post()).add(1.2, 0, 0.6);
		for (int i = 1; i <= 14; i++) {
			film.turnHead(top.lerp(foot, Film.ease(i / 14.0)));
			film.record(1);
		}
		film.record(wide(40, 80));
		film.normalGui();
		film.thirdPerson(false, 4);
	}

	/**
	 * Dusk in a thunderstorm: lightning strikes the flower bed by the porch, beside the wooden
	 * cottage. A flame flickers and dies without catching anything, and the pig in the garden stays
	 * a pig (lightning would turn it into a zombified piglin in the wild).
	 */
	private void storm(int time) {
		reset(true, time);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		cmd("weather thunder");
		BlockPos pen = at(4, 0, 0), strike = at(3, 0, -2);
		film.server.runOnServer(s -> {
			Pig pig = EntityTypes.PIG.create(s.overworld(), EntitySpawnReason.COMMAND);
			pig.snapTo(pen.getX() + 0.5, pen.getY(), pen.getZ() + 0.5, 140, 0);
			pig.setNoAi(true);
			pig.setPersistenceRequired();
			s.overworld().addFreshEntity(pig);
		});
		Vec3 eye = point(9.5, 3.2, 5.5), look = point(2.5, 1.5, -2.5);
		film.camera(eye, look);
		// Let the rain set in before filming.
		film.ctx.waitTicks(40);
		int before = cottage();
		film.record(wide(16, 30));
		cmd("summon minecraft:lightning_bolt %d %d %d", strike.getX(), strike.getY(), strike.getZ());
		film.log("thunder", "");
		film.dolly(wide(84, 120), eye, eye.lerp(look, 0.12), look, look);
		int after = cottage();
		boolean pig = film.server.computeOnServer(s -> near(s, Pig.class, pen) != null);
		film.note("storm", "cottage " + before + " -> " + after + " blocks; pig " + (pig ? "still a pig" : "gone"));
		Film.check(after == before, "the lightning burnt nothing (" + before + " -> " + after + ")");
		Film.check(pig, "the pig is still a pig");
	}

	/**
	 * Night at home, quiet: the cat asleep on the porch step, the lanterns glowing, the camera
	 * easing in. Nothing happens, which is the point.
	 */
	private void calm() {
		reset(true, NIGHT + 800);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		BlockPos step = at(-1, 1, -4);
		porchCat(step);
		Vec3 from = porchEye().add(2, 1, 3);
		film.camera(from, porchLook());
		film.ctx.waitTicks(10);
		film.dolly(70, from, porchEye(), porchLook(), porchLook());
		boolean cat = film.server.computeOnServer(s -> near(s, Cat.class, step) != null);
		Film.check(cat, "the cat is on the porch");
	}

	/**
	 * Sunrise, third person, from the first shot's spot by the lamp post: the Hearth Lantern still
	 * hangs there, lit, and her gaze rises from the foot of the post to the chain, ending where the
	 * first shot begins, so the Short loops.
	 */
	private void hearthMorning() {
		reset(true, SUNRISE + 300);
		film.hud(true);
		film.survival();
		cmd("clear @a");
		BlockPos p = chain();
		Vec3 top = new Vec3(p.getX() + 0.45, p.getY() + 0.02, p.getZ() + 0.5);
		film.camera(new Vec3(p.getX() + 0.5, sy, p.getZ() + 2.6), top);
		film.ctx.waitTicks(45);
		film.thirdPerson(true, 6);
		film.bigGui();
		Vec3 foot = Vec3.atBottomCenterOf(post()).add(1.2, 0, 0.6), start = top.add(-0.8, -0.6, 0);
		film.turnHead(foot);
		film.ctx.waitTicks(2);
		film.record(16);
		for (int i = 1; i <= 24; i++) {
			film.turnHead(foot.lerp(start, Film.ease(i / 24.0)));
			film.record(1);
		}
		film.record(20);
		boolean hanging = film.server.computeOnServer(s -> s.overworld().getBlockState(lantern()).is(HearthLantern.BLOCK.get()));
		film.note("morning", "lantern on the post " + hanging);
		Film.check(hanging, "the Hearth Lantern still hangs on the post");
		film.normalGui();
		film.thirdPerson(false, 4);
	}

	/** A quiet wide shot of the cottage, easing in to the opening shot's angle, so the first Short loops. */
	private void wide(int time) {
		reset(true, time);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		Vec3 from = porchEye().add(4, 2.5, 5);
		film.camera(from, porchLook());
		film.dolly(50, from, porchEye(), porchLook(), porchLook());
	}

	// ------------------------------------------------------------------ coming home: comforts and less grind

	/**
	 * Sunset, third person: walking up the garden path into home, the lantern's name shows above
	 * the hotbar ("Gabby's Cottage", as a lantern renamed on an anvil would) with the fireplace crackle.
	 */
	private void name() {
		reset(false, SUNSET);
		// A short reach for this scene, set with the mod's own rule, so the edge of home crosses the
		// garden path and the name appears on camera as she steps in (the default 32 is far out of
		// shot). The lantern goes up after, so it carries the new reach to the game.
		cmd("gamerule cozy_core:hearth_lantern_radius 6");
		set(lantern(), HearthLantern.ID + "[hanging=true]{CustomName:\"Gabby's Cottage\"}");
		film.hud(true);
		film.survival();
		cmd("clear @a");
		// Out of home first, so stepping in counts as arriving.
		film.camera(point(0.5, 0, 60.5), point(0.5, 1.6, 0));
		film.ctx.waitTicks(20);
		Vec3 start = point(0.5, 0, 9.5), door = point(0.5, 1.6, -6);
		film.camera(start, door);
		film.thirdPerson(true, 6);
		film.bigGui();
		film.turnHead(door);
		// Filming first, then the walk: about halfway up the path, short of the porch steps.
		film.rolling();
		if (isWide()) {
			// The wide video holds a moment on the cottage before the walk.
			film.record(40);
		}
		// Stopped by where she is, not by a count of frames (a slow frame lets the walk run on, and 30
		// frames once took her up the steps to the door, out of the short reach): about 8 blocks, then
		// a few frames to come to a stop.
		double halfway = point(0, 0, 1.5).z;
		if (realPace) {
			walkSpeed(0.05);
		}
		film.ctx.getInput().holdKey(o -> o.keyUp);
		film.until(mc -> mc.player.getZ() <= halfway);
		film.ctx.getInput().releaseKey(o -> o.keyUp);
		if (realPace) {
			walkSpeed(0.1);
		}
		film.record(wide(10, 60));
		String shown = film.ctx.computeOnClient(mc -> String.valueOf(HearthLanterns.homeAt(mc.level, mc.player.blockPosition()) != null));
		Film.check(shown.equals("true"), "the walk ended at home");
		film.normalGui();
		film.thirdPerson(false, 4);
	}

	/**
	 * First person on the porch roof: the dormer window is punched out and the pane drops whole;
	 * then a turn and a hop down to the lawn, with no health lost.
	 */
	private void roof(int time) {
		reset(true, time);
		film.hud(true);
		film.survival();
		film.thirdPerson(false, 4);
		cmd("clear @a");
		BlockPos found = film.server.computeOnServer(s -> {
			BlockPos best = null;
			BlockPos near = at(0, 5, -7);
			for (BlockPos p : BlockPos.betweenClosed(at(-4, 3, -12), at(4, 9, -4))) {
				if (s.overworld().getBlockState(p).is(Blocks.GLASS_PANE) && (best == null || p.distSqr(near) < best.distSqr(near))) {
					best = p.immutable();
				}
			}
			return best;
		});
		Film.check(found != null, "the cottage has a glass pane above the porch (the dormer)");
		int roof = film.ground(found.getX(), found.getZ() + 2);
		Film.check(roof >= sy + 4, "the porch roof in front of the dormer is at least 4 blocks up (it is " + (roof - sy) + ")");
		film.camera(new Vec3(found.getX() + 0.5, roof, found.getZ() + 2.5), Vec3.atCenterOf(found));
		// Let the teleport's own turn arrive before aiming, then take whichever pane of the dormer
		// (it is two panes tall) the crosshair is on.
		film.ctx.waitTicks(4);
		film.ctx.getInput().lookAt(found);
		film.ctx.waitTicks(3);
		BlockPos pane = film.ctx.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult hit
				&& mc.level.getBlockState(hit.getBlockPos()).is(Blocks.GLASS_PANE) ? hit.getBlockPos().immutable() : null);
		Film.check(pane != null, "the crosshair is on a dormer pane");
		film.bigGui();
		float before = film.ctx.computeOnClient(mc -> mc.player.getHealth());
		film.record(wide(4, 20));
		film.ctx.getInput().holdKey(o -> o.keyAttack);
		film.until(mc -> mc.level.getBlockState(pane).isAir());
		film.ctx.getInput().releaseKey(o -> o.keyAttack);
		film.log("break", "glass");
		film.record(14);
		// Dropped whole: still lying by the window, or already picked up (she stands right beside it;
		// the inventory was cleared at the start, so a pane there came from the window).
		boolean lying = film.server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(pane).inflate(3))
				.stream().anyMatch(e -> e.getItem().is(Items.GLASS_PANE)));
		boolean picked = film.server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().contains(new ItemStack(Items.GLASS_PANE)));
		boolean dropped = lying || picked;
		// Turn round to the garden and step off the edge of the roof.
		Vec3 garden = point(pane.getX() - sx + 0.5, 0, 8);
		Vec3 from = Vec3.atCenterOf(pane);
		for (int i = 1; i <= 10; i++) {
			film.turnHead(from.lerp(garden, Film.ease(i / 10.0)));
			film.record(1);
		}
		film.ctx.getInput().holdKey(o -> o.keyUp);
		film.record(12);
		film.ctx.getInput().releaseKey(o -> o.keyUp);
		film.record(wide(22, 50));
		float after = film.ctx.computeOnClient(mc -> mc.player.getHealth());
		double y = film.ctx.computeOnClient(mc -> mc.player.getY());
		film.note("roof", (lying ? "the pane dropped" : picked ? "the pane dropped and was picked up" : "nothing dropped") + "; fell " + (roof - y) + " blocks; health " + before + " -> " + after);
		Film.check(dropped, "the glass pane dropped itself");
		Film.check(roof - y >= 3.5, "the hop off the roof was a real fall (" + (roof - y) + " blocks)");
		Film.check(after == before, "the fall cost no health (" + before + " -> " + after + ")");
		film.normalGui();
	}

	/**
	 * Re-dyeing at the kitchen's crafting table (Gabby, 2026-10-20, in place of the stonecutter): eight
	 * red stained glass round a light blue dye, one click on the result, and eight light blue glass
	 * come out. Vanilla only dyes clear glass; this is the mod's recipe.
	 */
	private void dye(int time) {
		// In 26.3 the coloured items are ColorCollections, one accessor per colour.
		ItemStack[] grid = new ItemStack[9];
		for (int i = 0; i < 9; i++) {
			grid[i] = new ItemStack(i == 4 ? Items.DYE.lightBlue() : Items.STAINED_GLASS.red());
		}
		craft("dye", time, grid, Items.STAINED_GLASS.lightBlue(), 8, "8 red stained glass and a light blue dye");
	}

	/**
	 * At the kitchen's crafting table: the grid already holds {@code grid} (slots 1-9), the cursor
	 * glides to the result and takes it, and the test checks {@code count} of {@code result} came out.
	 */
	private void craft(String label, int time, ItemStack[] grid, Item result, int count, String from) {
		reset(true, time);
		film.hud(true);
		film.survival();
		film.thirdPerson(false, 4);
		cmd("clear @a");
		// The cottage's own crafting table, beside the kitchen barrel.
		BlockPos counter = at(4, 1, -9);
		BlockPos table = film.server.computeOnServer(s -> {
			for (BlockPos p : BlockPos.betweenClosed(counter.offset(-1, -1, -3), counter.offset(1, 1, 3))) {
				if (s.overworld().getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
					return p.immutable();
				}
			}
			return null;
		});
		Film.check(table != null, "the kitchen has a crafting table beside the barrel");
		BlockPos stand = table.west(2);
		String room = film.server.computeOnServer(s -> s.overworld().getBlockState(stand).getBlock() + " / " + s.overworld().getBlockState(stand.above()).getBlock());
		Film.check(room.equals("Block{minecraft:air} / Block{minecraft:air}"), "there is room to stand in front of the crafting table (feet / head: " + room + ")");
		film.camera(Vec3.atBottomCenterOf(stand), Vec3.atCenterOf(table));
		film.ctx.waitTicks(4);
		film.aim(table);
		film.bigGui();
		film.ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(table), Direction.UP, table, false)));
		film.ctx.waitForScreen(CraftingScreen.class);
		// The grid filled before the first frame: the ring of glass and the dye in the middle.
		film.server.runOnServer(s -> {
			CraftingMenu menu = (CraftingMenu) s.getPlayerList().getPlayers().getFirst().containerMenu;
			for (int i = 1; i <= 9; i++) {
				menu.getSlot(i).set(grid[i - 1].copy());
			}
			menu.broadcastChanges();
		});
		film.ctx.waitFor(mc -> mc.player.containerMenu instanceof CraftingMenu m && m.getSlot(0).getItem().is(result));
		double[] to = film.ctx.computeOnClient(mc -> slotCentre(mc, 0));
		double[] start = { 0.5, 0.78 };
		cursorAt(start);
		film.record(wide(10, 24));
		film.glide(start, to, 10);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", label);
		film.until(mc -> mc.player.containerMenu.getCarried().is(result));
		int made = film.ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getCount());
		film.note(label, from + " made " + made + " " + result);
		Film.check(made == count, from + " make " + count + " " + result + " (made " + made + ")");
		film.record(wide(26, 70));
		film.ctx.setScreen(() -> null);
		cmd("clear @a");
		film.normalGui();
	}

	/** A stonecutter by the path with a spruce log in it: one click on the stairs, and four come out. */
	private void stonecutter(int time) {
		reset(true, time);
		film.hud(true);
		film.survival();
		film.thirdPerson(false, 4);
		cmd("clear @a");
		// Inside, on the kitchen counter (Gabby, 2026-10-20): the stonecutter takes the potted cactus's
		// place on the cottage's own barrel, beside the crafting table and the smoker, with the
		// player standing in the room two blocks in front of it.
		BlockPos counter = at(4, 1, -9), cutter = counter.above(), stand = at(2, 1, -9);
		String there = film.server.computeOnServer(s -> s.overworld().getBlockState(counter).getBlock() + " / "
				+ s.overworld().getBlockState(stand).getBlock() + " / " + s.overworld().getBlockState(stand.above()).getBlock());
		Film.check(there.equals("Block{minecraft:barrel} / Block{minecraft:air} / Block{minecraft:air}"),
				"the kitchen barrel is there with room to stand in front (barrel / feet / head: " + there + ")");
		set(cutter, "minecraft:stonecutter");
		film.camera(Vec3.atBottomCenterOf(stand), Vec3.atCenterOf(cutter));
		// Let the teleport's own turn arrive before aiming, or it undoes the aim.
		film.ctx.waitTicks(4);
		film.aim(cutter);
		// The bigger GUI first: a screen lays itself out for the scale it opens at.
		film.bigGui();
		film.ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(cutter), Direction.UP, cutter, false)));
		film.ctx.waitForScreen(StonecutterScreen.class);
		film.server.runOnServer(s -> {
			StonecutterMenu menu = (StonecutterMenu) s.getPlayerList().getPlayers().getFirst().containerMenu;
			menu.getSlot(StonecutterMenu.INPUT_SLOT).set(new ItemStack(Items.SPRUCE_LOG));
			menu.broadcastChanges();
		});
		film.ctx.waitFor(mc -> mc.player.containerMenu instanceof StonecutterMenu m && m.getNumberOfVisibleRecipes() > 0);
		int index = film.ctx.computeOnClient(mc -> offered(mc).indexOf(Items.SPRUCE_STAIRS));
		Film.check(index >= 0 && index < 12, "the stonecutter offers spruce stairs on its first page (at " + index + ")");
		double[] to = film.ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
			double scale = mc.getWindow().getGuiScale();
			double x = (field(screen, "leftPos") + 52 + (index % 4) * 16 + 8) * scale;
			double y = (field(screen, "topPos") + 14 + (index / 4) * 18 + 9) * scale;
			return new double[] { x / mc.getWindow().getWidth(), y / mc.getWindow().getHeight() };
		});
		double[] from = { 0.5, 0.78 };
		int[] size = film.ctx.computeOnClient(mc -> new int[] { mc.getWindow().getWidth(), mc.getWindow().getHeight() });
		film.ctx.getInput().setCursorPos(from[0] * size[0], from[1] * size[1]);
		film.record(wide(6, 24));
		film.glide(from, to, 10);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", "spruce_stairs");
		film.until(mc -> ((StonecutterMenu) mc.player.containerMenu).getSlot(StonecutterMenu.RESULT_SLOT).getItem().is(Items.SPRUCE_STAIRS));
		int count = film.ctx.computeOnClient(mc -> ((StonecutterMenu) mc.player.containerMenu).getSlot(StonecutterMenu.RESULT_SLOT).getItem().getCount());
		film.note("cut", "one log made " + count + " spruce stairs");
		Film.check(count == 4, "one log makes 4 spruce stairs (made " + count + ")");
		film.record(wide(26, 70));
		film.ctx.setScreen(() -> null);
		film.normalGui();
	}

	// ------------------------------------------------------------------ the showcase's own scenes

	/**
	 * Sets the lantern's reach with the mod's own rule and hangs the lantern again, so the game
	 * (which only hears the reach when the lantern is sent) draws home's edge where the server has it.
	 */
	private void reach(int blocks) {
		cmd("gamerule cozy_core:hearth_lantern_radius %d", blocks);
		set(lantern(), "minecraft:air");
		set(lantern(), HearthLantern.ID + "[hanging=true]");
		film.ctx.waitFor(mc -> HearthLanterns.lanterns(mc.level).stream().anyMatch(l -> l.reach() == blocks), 100);
	}

	/** The blocks in a box as they are now, and a way to put them back (off camera, after a scene). */
	private Runnable keepBlocks(BlockPos from, BlockPos to) {
		List<net.minecraft.world.level.block.state.BlockState> states = film.server.computeOnServer(s -> {
			List<net.minecraft.world.level.block.state.BlockState> list = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				list.add(s.overworld().getBlockState(p));
			}
			return list;
		});
		return () -> film.server.runOnServer(s -> {
			int i = 0;
			for (BlockPos p : BlockPos.betweenClosed(from, to)) {
				s.overworld().setBlock(p, states.get(i++), 2);
			}
		});
	}

	/** Whether this is the wide showcase (not the tall Short), for a scene both share. */
	private boolean isWide() {
		return film.ctx.computeOnClient(mc -> mc.getWindow().getWidth() > mc.getWindow().getHeight());
	}

	/**
	 * The player's walking speed (vanilla's is 0.1), on the server and on the client, which moves the
	 * player itself; with the speed's field-of-view change off, so a slower walk doesn't narrow the
	 * view mid-shot (kit README, "A slow walk").
	 */
	private void walkSpeed(double speed) {
		cmd("attribute @a minecraft:movement_speed base set %s", speed);
		film.ctx.runOnClient(mc -> {
			mc.options.fovEffectScale().set(0.0);
			mc.player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		});
	}

	/** {@code tall} frames in the Short, {@code wide} in the showcase, which holds each moment longer. */
	private int wide(int tall, int wide) {
		return isWide() ? wide : tall;
	}

	/** The middle of a widget on the open screen, as fractions of the window, for {@link Film#glide}. */
	private static double[] centre(Minecraft mc, net.minecraft.client.gui.components.AbstractWidget w) {
		double scale = mc.getWindow().getGuiScale();
		return new double[] { (w.getX() + w.getWidth() / 2.0) * scale / mc.getWindow().getWidth(),
				(w.getY() + w.getHeight() / 2.0) * scale / mc.getWindow().getHeight() };
	}

	/** The middle of a slot of the open container screen, as fractions of the window. */
	private static double[] slotCentre(Minecraft mc, int slot) {
		AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
		var s = mc.player.containerMenu.getSlot(slot);
		double scale = mc.getWindow().getGuiScale();
		return new double[] { (field(screen, "leftPos") + s.x + 8) * scale / mc.getWindow().getWidth(),
				(field(screen, "topPos") + s.y + 8) * scale / mc.getWindow().getHeight() };
	}

	/** Puts the cursor at a spot on the window (fractions), off camera. */
	private void cursorAt(double[] at) {
		int[] size = film.ctx.computeOnClient(mc -> new int[] { mc.getWindow().getWidth(), mc.getWindow().getHeight() });
		film.ctx.getInput().setCursorPos(at[0] * size[0], at[1] * size[1]);
	}

	/** Types into the focused text box a key at a time, with small uneven pauses, filming as it goes. */
	private void typeText(String text) {
		for (char c : text.toCharArray()) {
			film.ctx.getInput().typeChar(c);
			film.log("key", String.valueOf(c));
			film.record(1 + (film.jitter.nextInt(5) == 0 ? 2 + film.jitter.nextInt(3) : film.jitter.nextInt(2)));
		}
	}

	/**
	 * Sunset at an anvil on the lawn: the Hearth Lantern goes in, "Gabby's Cottage" is typed a letter
	 * at a time, and the renamed lantern comes out. In creative, so no experience level shows.
	 */
	private void anvil(int time) {
		reset(true, time);
		film.hud(true);
		cmd("gamemode creative @a");
		film.thirdPerson(false, 4);
		cmd("clear @a");
		BlockPos anvil = at(-1, 0, 4), stand = at(-1, 0, 6);
		Runnable put = keepBlocks(anvil, anvil);
		set(anvil, "minecraft:anvil[facing=east]");
		try {
			film.camera(Vec3.atBottomCenterOf(stand), Vec3.atCenterOf(anvil));
			film.ctx.waitTicks(4);
			film.aim(anvil);
			film.bigGui();
			film.ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(anvil), Direction.UP, anvil, false)));
			film.ctx.waitForScreen(net.minecraft.client.gui.screens.inventory.AnvilScreen.class);
			film.server.runOnServer(s -> {
				var menu = s.getPlayerList().getPlayers().getFirst().containerMenu;
				menu.getSlot(0).set(new ItemStack(HearthLantern.ITEM.get()));
				menu.broadcastChanges();
			});
			film.ctx.waitFor(mc -> mc.player.containerMenu.getSlot(0).getItem().is(HearthLantern.ITEM.get()));
			// The name box fills with the item's own name; empty it before the first frame.
			film.ctx.runOnClient(mc -> nameBox(mc).setValue(""));
			cursorAt(new double[] { 0.5, 0.82 });
			film.record(24);
			typeText("Gabby's Cottage");
			film.record(8);
			double[] from = { 0.5, 0.82 };
			double[] to = film.ctx.computeOnClient(mc -> slotCentre(mc, 2));
			film.glide(from, to, 10);
			film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			film.log("click", "anvil");
			film.until(mc -> HearthLantern.isHearth(mc.player.containerMenu.getCarried().getItem()));
			String named = film.ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getHoverName().getString());
			film.record(50);
			film.note("anvil", "the lantern came out named " + named);
			Film.check(named.equals("Gabby's Cottage"), "the lantern is named \"Gabby's Cottage\" (it is \"" + named + "\")");
		} finally {
			film.ctx.setScreen(() -> null);
			cmd("clear @a");
			put.run();
			film.normalGui();
		}
	}

	private static net.minecraft.client.gui.components.EditBox nameBox(Minecraft mc) {
		try {
			Field f = net.minecraft.client.gui.screens.inventory.AnvilScreen.class.getDeclaredField("name");
			f.setAccessible(true);
			return (net.minecraft.client.gui.components.EditBox) f.get(mc.gui.screen());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	/**
	 * Dusk, a slow pan along a row of the six Hearth Lantern styles, each on a spruce fence post on
	 * the lawn: classic, soul and the four copper colours.
	 */
	private void styles() {
		reset(true, DUSK);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		BlockPos first = at(1, 0, 3);
		Runnable put = keepBlocks(first, first.offset(HearthLantern.STYLES.size() * 2 - 2, 1, 0));
		try {
			for (int i = 0; i < HearthLantern.STYLES.size(); i++) {
				BlockPos p = first.offset(i * 2, 0, 0);
				set(p, "minecraft:spruce_fence");
				set(p.above(), HearthLantern.STYLES.get(i).id() + "[hanging=false]");
			}
			int shown = film.server.computeOnServer(s -> {
				int n = 0;
				for (int i = 0; i < HearthLantern.STYLES.size(); i++) {
					if (s.overworld().getBlockState(first.offset(i * 2, 1, 0)).is(HearthLantern.STYLES.get(i).block().get())) {
						n++;
					}
				}
				return n;
			});
			Film.check(shown == 6, "all six styles stand in the row (" + shown + ")");
			// A short slide along the row, ending with all six still in shot.
			Vec3 lookFrom = point(4.5, 1.3, 3.5), lookTo = point(7.5, 1.3, 3.5);
			Vec3 eyeFrom = point(2.5, 1.5, 8.5), eyeTo = point(5.5, 1.5, 8.5);
			film.camera(eyeFrom, lookFrom);
			film.dolly(160, eyeFrom, eyeTo, lookFrom, lookTo);
			film.note("styles", "six styles in a row");
		} finally {
			put.run();
		}
	}

	/**
	 * Night falls: vanilla's own natural spawning runs over the lawn (the same code the game uses each
	 * tick, called at chosen spots so it happens in shot), and monsters turn up out in the wild while
	 * none spawn at home. The camera is a flying player high above, so every spot is far enough from
	 * a player to spawn on, and the world's spawn point is moved away for the scene.
	 */
	private void night() {
		reset(true, NIGHT);
		reach(12);
		film.hud(false);
		cmd("gamemode creative @a");
		film.thirdPerson(false, 4);
		film.server.runOnServer(s -> {
			var p = s.getPlayerList().getPlayers().getFirst();
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		});
		BlockPos spawn = film.server.computeOnServer(s -> s.overworld().getRespawnData().pos());
		cmd("setworldspawn %d %d %d", sx + 400, sy, sz + 400);
		int[] count = new int[2];
		// The tall Short: the same high camera (spawning needs every spot 24 blocks from the player,
		// who is the camera) with a narrow view onto the edge of home, and monsters tried only where
		// it can see, so they read as monsters, not specks (scout, 2026-10-21).
		boolean tall = !isWide();
		int fov = film.ctx.computeOnClient(mc -> mc.options.fov().get());
		if (tall) {
			film.ctx.runOnClient(mc -> mc.options.fov().set(62));
		}
		try {
			Vec3 eyeFrom = point(8, 27, 26), eyeTo = point(6, 25.5, 23), look = tall ? point(7, 0, 9) : point(5, 0, 5);
			film.camera(eyeFrom, look);
			film.ctx.waitTicks(10);
			film.rolling();
			java.util.Random random = new java.util.Random(5);
			int frames = 180;
			for (int i = 0; i < frames; i++) {
				// One try every few frames: a scattered few out in the meadow, not a crowd (four a
				// frame filled the lawn with 239 in the scout).
				if (i < 130 && i % (tall ? 3 : 4) == 0) {
					List<BlockPos> spots = new ArrayList<>();
					for (int k = 0; k < 1; k++) {
						spots.add(tall ? new BlockPos(sx - 1 + random.nextInt(18), sy, sz + 2 + random.nextInt(15))
								: new BlockPos(sx - 6 + random.nextInt(37), sy, sz + 1 + random.nextInt(19)));
					}
					film.server.runOnServer(s -> {
						var level = s.overworld();
						for (BlockPos p : spots) {
							net.minecraft.world.level.NaturalSpawner.spawnCategoryForPosition(net.minecraft.world.entity.MobCategory.MONSTER, level,
									level.getChunk(p), p, (type, lvl, pos, chunk) -> true,
									(mob, chunk) -> count[HearthLanterns.protects(level, mob.blockPosition()) ? 0 : 1]++);
						}
					});
				}
				double t = Film.ease(i / (frames - 1.0));
				film.camera(eyeFrom.lerp(eyeTo, t), look);
				film.record(1);
			}
			film.note("night", "reach 12; monsters spawned at home " + count[0] + ", in the wild " + count[1]);
			Film.check(count[0] == 0, "no monster spawned at home (" + count[0] + ")");
			Film.check(count[1] >= 4, "monsters spawned in the wild (" + count[1] + ")");
		} finally {
			cmd("setworldspawn %d %d %d", spawn.getX(), spawn.getY(), spawn.getZ());
			film.ctx.runOnClient(mc -> mc.options.fov().set(fov));
		}
	}

	/** A wandering trader by the garden path, with a builder's trade among its offers. */
	private void trader(int time) {
		merchant("trader", time, List.of(Items.CALCITE, Items.AZALEA, Items.FLOWERING_AZALEA, Items.CRIMSON_FUNGUS, Items.WARPED_FUNGUS), s -> {
			var t = EntityTypes.WANDERING_TRADER.create(s.overworld(), EntitySpawnReason.COMMAND);
			t.setDespawnDelay(48000);
			return t;
		});
	}

	/**
	 * On the lawn: a merchant stands by the path, and among its offers is one of the mod's builder
	 * trades. She opens it, picks that trade and takes what it sells. A merchant's offers are drawn at
	 * random, so new ones are summoned (off camera) until one has a builder's trade.
	 */
	private void merchant(String label, int time, List<Item> ours,
			java.util.function.Function<MinecraftServer, net.minecraft.world.entity.npc.villager.AbstractVillager> make) {
		reset(true, time);
		film.hud(true);
		film.survival();
		film.thirdPerson(false, 4);
		cmd("clear @a");
		cmd("item replace entity @a hotbar.0 with minecraft:emerald 64");
		BlockPos at = at(1, 0, 4), stand = at(1, 0, 6);
		int[] found = film.server.computeOnServer(s -> {
			for (int tries = 0; tries < 60; tries++) {
				var m = make.apply(s);
				m.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
				m.setNoAi(true);
				m.setPersistenceRequired();
				var offers = m.getOffers();
				for (int i = 0; i < offers.size(); i++) {
					Item sold = offers.get(i).getResult().getItem();
					if (ours.contains(sold)) {
						s.overworld().addFreshEntity(m);
						return new int[] { tries, i };
					}
				}
				m.discard();
			}
			return null;
		});
		Film.check(found != null, "a " + label + " with a builder's trade turned up within 60 tries");
		film.camera(Vec3.atBottomCenterOf(stand), Vec3.atBottomCenterOf(at).add(0, 1.6, 0));
		film.ctx.waitTicks(45);
		film.turnHead(Vec3.atBottomCenterOf(at).add(0, 1.4, 0));
		film.ctx.waitTicks(3);
		film.bigGui();
		film.ctx.runOnClient(mc -> {
			var m = mc.level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.AbstractVillager.class, new AABB(at).inflate(2)).getFirst();
			mc.gameMode.interact(mc.player, m, new net.minecraft.world.phys.EntityHitResult(m), InteractionHand.MAIN_HAND);
		});
		film.ctx.waitForScreen(net.minecraft.client.gui.screens.inventory.MerchantScreen.class);
		film.ctx.waitTicks(4);
		int index = found[1];
		double[] offer = film.ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
				.filter(c -> c.getClass().getSimpleName().equals("TradeOfferButton"))
				.map(c -> (net.minecraft.client.gui.components.AbstractWidget) c)
				.sorted(java.util.Comparator.comparingInt(net.minecraft.client.gui.components.AbstractWidget::getY))
				.skip(index).findFirst().map(w -> centre(mc, w)).orElseThrow());
		double[] from = { 0.5, 0.85 };
		cursorAt(from);
		film.record(20);
		film.glide(from, offer, 12);
		film.record(16);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", label + "_offer");
		film.until(mc -> !mc.player.containerMenu.getSlot(2).getItem().isEmpty());
		film.record(8);
		double[] result = film.ctx.computeOnClient(mc -> slotCentre(mc, 2));
		film.glide(offer, result, 10);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", label + "_buy");
		film.until(mc -> !mc.player.containerMenu.getCarried().isEmpty());
		String bought = film.ctx.computeOnClient(mc -> mc.player.containerMenu.getCarried().getCount() + " " + mc.player.containerMenu.getCarried().getItem());
		for (int i = 0; i < 30; i++) {
			// A villager's trade gives experience; keep a level from showing over the hotbar.
			cmd("kill @e[type=minecraft:experience_orb]");
			cmd("xp set @a 0 levels");
			cmd("xp set @a 0 points");
			film.record(1);
		}
		film.note(label, "summoned " + (found[0] + 1) + " time(s); bought " + bought);
		Film.check(ours.stream().anyMatch(i -> bought.endsWith(" " + i)), "she bought a builder's trade (" + bought + ")");
		film.ctx.setScreen(() -> null);
		cmd("clear @a");
		film.normalGui();
	}

	/** The outputs the stonecutter screen is offering, in button order. */
	private static List<Item> offered(Minecraft mc) {
		StonecutterMenu menu = (StonecutterMenu) mc.player.containerMenu;
		List<Item> out = new ArrayList<>();
		for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> entry : menu.getVisibleRecipes().entries()) {
			out.add(entry.recipe().optionDisplay().resolveForFirstStack(SlotDisplayContext.fromLevel(mc.level)).getItem());
		}
		return out;
	}

	private static int field(Object screen, String name) {
		try {
			Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.getInt(screen);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
