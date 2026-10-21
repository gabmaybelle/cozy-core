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
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
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
			"cat", "pig", "zombified_piglin", "lightning_bolt", "item");
	/** Sunset, and dusk just after it (the lanterns glow, the sky is still warm). */
	private static final int SUNSET = 12300, DUSK = 13000;

	private Film film;
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
		// Off camera: an animal cleared away plays its death for a second, and rain from the storm
		// scene takes a few seconds to fade once the weather clears.
		boolean raining = film.ctx.computeOnClient(mc -> mc.level.getRainLevel(1) > 0);
		film.ctx.waitTicks(raining ? 160 : 30);
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

	private void scenes() {
		readSet();
		film.window(1080, 1920, 3);
		// The Short, "coming home at dusk": a beat for each proof in the message house.
		film.scene("s_porch", this::porch);
		film.scene("s_name", this::name);
		film.scene("s_roof", this::roof);
		film.scene("s_cut", this::stonecutter);
		film.scene("s_wide", this::wide);
		// Filmed for the second Short; kept for the full showcase video.
		film.scene("s_home", this::home);
		film.scene("s_storm", this::storm);
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
	private void porch() {
		reset(true, DUSK);
		film.hud(false);
		film.spectator();
		film.thirdPerson(false, 4);
		BlockPos step = at(-1, 1, -4), path = at(1, 0, -1);
		film.server.runOnServer(s -> {
			Cat cat = EntityTypes.CAT.create(s.overworld(), EntitySpawnReason.COMMAND);
			cat.snapTo(step.getX() + 0.5, step.getY(), step.getZ() + 0.5, 180, 0);
			cat.tame(s.getPlayerList().getPlayers().getFirst());
			cat.setOrderedToSit(true);
			cat.setInSittingPose(true);
			cat.setPersistenceRequired();
			s.overworld().addFreshEntity(cat);
		});
		film.camera(porchEye(), porchLook());
		int before = cottage();
		cmd("summon minecraft:creeper %d %d %d {NoAI:1b,Fuse:30,Rotation:[200f,0f]}", path.getX(), path.getY(), path.getZ());
		// Lit only once filming has begun: the first frame waits for the world to draw.
		film.record(4);
		light(path);
		for (int i = 0; i < 80 && film.server.computeOnServer(s -> near(s, Creeper.class, path) != null); i++) {
			film.record(1);
		}
		film.log("boom", "");
		film.dolly(56, porchEye(), porchEye().lerp(porchLook(), 0.15), porchLook(), porchLook());
		int after = cottage();
		float lost = film.server.computeOnServer(s -> {
			Cat c = near(s, Cat.class, step);
			return c == null ? -1f : c.getMaxHealth() - c.getHealth();
		});
		film.note("porch", "cottage " + before + " -> " + after + " blocks; cat " + (lost < 0 ? "gone" : "lost " + lost + " health"));
		Film.check(after == before, "the blast on the path broke nothing (" + before + " -> " + after + ")");
		Film.check(lost == 0, "the cat is unhurt (" + (lost < 0 ? "gone" : "lost " + lost) + ")");
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
	private void home() {
		reset(false, SUNSET);
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
		film.record(40);
		film.normalGui();
		film.thirdPerson(false, 4);
	}

	/**
	 * Dusk in a thunderstorm: lightning strikes the flower bed by the porch, beside the wooden
	 * cottage. A flame flickers and dies without catching anything, and the pig in the garden stays
	 * a pig (lightning would turn it into a zombified piglin in the wild).
	 */
	private void storm() {
		reset(true, DUSK);
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
		film.record(16);
		cmd("summon minecraft:lightning_bolt %d %d %d", strike.getX(), strike.getY(), strike.getZ());
		film.log("thunder", "");
		film.dolly(84, eye, eye.lerp(look, 0.12), look, look);
		int after = cottage();
		boolean pig = film.server.computeOnServer(s -> near(s, Pig.class, pen) != null);
		film.note("storm", "cottage " + before + " -> " + after + " blocks; pig " + (pig ? "still a pig" : "gone"));
		Film.check(after == before, "the lightning burnt nothing (" + before + " -> " + after + ")");
		Film.check(pig, "the pig is still a pig");
	}

	/** A quiet wide shot of the cottage at dusk, easing in to the opening shot's angle, so the Short loops. */
	private void wide() {
		reset(true, DUSK);
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
		// Stopped by where she is, not by a count of frames (a slow frame lets the walk run on, and 30
		// frames once took her up the steps to the door, out of the short reach): about 8 blocks, then
		// a few frames to come to a stop.
		double halfway = point(0, 0, 1.5).z;
		film.ctx.getInput().holdKey(o -> o.keyUp);
		film.until(mc -> mc.player.getZ() <= halfway);
		film.ctx.getInput().releaseKey(o -> o.keyUp);
		film.record(10);
		String shown = film.ctx.computeOnClient(mc -> String.valueOf(HearthLanterns.homeAt(mc.level, mc.player.blockPosition()) != null));
		Film.check(shown.equals("true"), "the walk ended at home");
		film.normalGui();
		film.thirdPerson(false, 4);
	}

	/**
	 * First person on the porch roof: the dormer window is punched out and the pane drops whole;
	 * then a turn and a hop down to the lawn, with no health lost.
	 */
	private void roof() {
		reset(true, SUNSET);
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
		film.record(4);
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
		film.record(22);
		float after = film.ctx.computeOnClient(mc -> mc.player.getHealth());
		double y = film.ctx.computeOnClient(mc -> mc.player.getY());
		film.note("roof", (lying ? "the pane dropped" : picked ? "the pane dropped and was picked up" : "nothing dropped") + "; fell " + (roof - y) + " blocks; health " + before + " -> " + after);
		Film.check(dropped, "the glass pane dropped itself");
		Film.check(roof - y >= 3.5, "the hop off the roof was a real fall (" + (roof - y) + " blocks)");
		Film.check(after == before, "the fall cost no health (" + before + " -> " + after + ")");
		film.normalGui();
	}

	/** A stonecutter by the path with a spruce log in it: one click on the stairs, and four come out. */
	private void stonecutter() {
		reset(true, SUNSET);
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
		film.record(6);
		film.glide(from, to, 10);
		film.ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		film.log("click", "spruce_stairs");
		film.until(mc -> ((StonecutterMenu) mc.player.containerMenu).getSlot(StonecutterMenu.RESULT_SLOT).getItem().is(Items.SPRUCE_STAIRS));
		int count = film.ctx.computeOnClient(mc -> ((StonecutterMenu) mc.player.containerMenu).getSlot(StonecutterMenu.RESULT_SLOT).getItem().getCount());
		film.note("cut", "one log made " + count + " spruce stairs");
		Film.check(count == 4, "one log makes 4 spruce stairs (made " + count + ")");
		film.record(26);
		film.ctx.setScreen(() -> null);
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
