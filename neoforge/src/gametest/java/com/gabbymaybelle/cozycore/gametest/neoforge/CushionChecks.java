package com.gabbymaybelle.cozycore.gametest.neoforge;

import com.gabbymaybelle.cozycore.HearthLantern;
import com.gabbymaybelle.cozycore.HomeLife;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.Cushion;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The checks for 0.9.0, cushion naps: at night a pet cat and a visiting cat each curl up on a free
 * cushion by the lantern, a pet dog sits on one, and a cushion with someone on it is left alone.
 * When a cat's cushion is broken it lies by the lantern instead (no free cushion is left), and in
 * the morning they all get off. A cat found on a cushion by day (a world loaded in the morning) gets
 * off. A visiting fox and a visiting rabbit each have a check of their own: foxes hunt rabbits, cats
 * hunt rabbits and rabbits run from dogs, so they can't share a yard with the others.
 */
final class CushionChecks {
	private CushionChecks() {
	}

	private static void command(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/**
	 * A cushion on the ground at this spot, placed the way the cushion item places one. Keep cushions
	 * inside the test's own area: its edge is a wall of barrier blocks, and a cushion in a block that
	 * fills its space breaks within 100 ticks (found 2026-10-30).
	 */
	private static Cushion cushion(ServerLevel level, BlockPos spot, DyeColor colour) {
		Cushion c = EntityTypes.CUSHION.create(level, EntitySpawnReason.COMMAND);
		c.snapTo(Vec3.atCenterOfWithY(spot, spot.getY()), 0, 0);
		c.setColor(colour);
		level.addFreshEntity(c);
		return c;
	}

	private static double from(Entity e, BlockPos hearth) {
		return Math.sqrt(e.distanceToSqr(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 0.5));
	}

	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: a pet needs a real owner, and "someone home" a player
	static void naps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos hearth = new BlockPos(at.getX(), Math.max(at.getY(), level.getSeaLevel()) + 1, at.getZ());
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		List<Entity> made = new ArrayList<>();
		Runnable tidy = () -> {
			level.getServer().getPlayerList().remove(owner);
			made.forEach(Entity::discard);
			level.getEntitiesOfClass(Entity.class, new AABB(hearth).inflate(24, 4, 24), e -> e instanceof Cushion || e instanceof ItemEntity
					|| e.entityTags().contains(HomeLife.VISITOR_TAG)).forEach(Entity::discard);
			for (BlockPos p : BlockPos.betweenClosed(hearth.offset(-3, -1, -3), hearth.offset(17, 2, 3))) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			command(level, "time set noon");
		};
		Cat[] cats = new Cat[2];
		Wolf[] dog = new Wolf[1];
		Cushion[] cushions = new Cushion[4];
		Entity[] sitter = new Entity[1];
		RuleChecks.orTidy(tidy, () -> {
			for (BlockPos p : BlockPos.betweenClosed(hearth.offset(-3, -1, -3), hearth.offset(17, -1, 3))) {
				level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
			// The owner is home, in creative: a stray cat is shy of a survival player within 16 blocks, and
			// would run off now and then (seen in one run in four).
			owner.setGameMode(GameType.CREATIVE);
			owner.teleportTo(level, hearth.getX() + 16.5, hearth.getY(), hearth.getZ() + 2.5, Set.of(), 0, 0, false);
			command(level, "time set 14000");
			cushions[0] = cushion(level, hearth.offset(2, 0, 0), DyeColor.MAGENTA);
			cushions[1] = cushion(level, hearth.offset(2, 0, 2), DyeColor.MAGENTA);
			// Someone's already on this one.
			cushions[2] = cushion(level, hearth.offset(0, 0, 2), DyeColor.PINK);
			cushions[3] = cushion(level, hearth.offset(4, 0, 0), DyeColor.MAGENTA);
			sitter[0] = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			sitter[0].snapTo(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 2.5, 0, 0);
			level.addFreshEntity(sitter[0]);
			sitter[0].startRiding(cushions[2], true, false);
			cats[0] = EntityTypes.CAT.create(level, EntitySpawnReason.COMMAND);
			cats[0].snapTo(hearth.getX() + 10.5, hearth.getY(), hearth.getZ() + 2.5, 0, 0);
			cats[0].tame(owner);
			cats[0].setOrderedToSit(false);
			cats[0].setPersistenceRequired();
			level.addFreshEntity(cats[0]);
			cats[1] = (Cat) HomeLife.visit(level, hearth.offset(12, 0, 0), EntityTypes.CAT);
			helper.assertTrue(cats[1] != null, "the check's visitor should arrive");
			dog[0] = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
			// Inside the test's own area, as every animal here must start: the game only keeps the chunks
			// under the test's structure running, and the lantern is one block in from its north edge. A
			// dog placed 1.5 blocks north of the lantern stood frozen whenever that edge fell on a chunk
			// border (issue 4).
			dog[0].snapTo(hearth.getX() + 10.5, hearth.getY(), hearth.getZ() + 0.5, 0, 0);
			dog[0].tame(owner);
			dog[0].setOrderedToSit(false);
			dog[0].setPersistenceRequired();
			level.addFreshEntity(dog[0]);
			made.addAll(List.of(sitter[0], cats[0], cats[1], dog[0], cushions[0], cushions[1], cushions[2], cushions[3]));
		});
		helper.runAfterDelay(300, () -> RuleChecks.orTidy(tidy, () -> {
			Cat pet = cats[0], visitor = cats[1];
			boolean petOn = pet.getVehicle() instanceof Cushion, petLying = pet.isLying();
			boolean visitorOn = visitor.getVehicle() instanceof Cushion, visitorLying = visitor.isLying();
			boolean sameCushion = petOn && pet.getVehicle() == visitor.getVehicle();
			boolean takenKept = sitter[0].getVehicle() == cushions[2] && cushions[2].getPassengers().size() == 1;
			System.out.println("[cozy_core_gametest] cushions: at night the pet is on a cushion " + petOn + ", lying " + petLying
					+ "; the visitor on a cushion " + visitorOn + ", lying " + visitorLying + String.format(", %.1f blocks from the lantern", from(visitor, hearth)) + " (a visitor " + HomeLife.isVisitor(visitor) + ", navigating " + !visitor.getNavigation().isDone() + "); the pig kept its cushion " + takenKept);
			helper.assertTrue(petOn && petLying, "at night a pet cat should curl up on a free cushion by the lantern (on " + petOn + ", lying " + petLying + ")");
			helper.assertTrue(visitorOn && visitorLying, "a visiting cat should curl up on a free cushion too (on " + visitorOn + ", lying " + visitorLying + ")");
			helper.assertFalse(sameCushion, "two cats shouldn't share a cushion");
			helper.assertTrue(takenKept, "a cushion with someone on it should be left to them");
			// The dog walks to its cushion in its own time: wait for it (issue 4), then carry on.
			whenDogSits(helper, dog, cushions, hearth, DOG_WAIT, tidy, () -> RuleChecks.orTidy(tidy, () -> {
				// Break the pet's cushion: no free one is left, so it should lie by the lantern instead.
				Cushion petCushion = (Cushion) pet.getVehicle();
				petCushion.hurtServer(level, level.damageSources().generic(), 1.0F);
				helper.runAfterDelay(120, () -> RuleChecks.orTidy(tidy, () -> {
					boolean stillOn = pet.isPassenger();
					double fromHearth = from(pet, hearth);
					boolean lyingByLantern = pet.isLying() && fromHearth < 3.5;
					boolean dropped = !level.getEntitiesOfClass(ItemEntity.class, new AABB(petCushion.blockPosition()).inflate(3)).isEmpty();
					System.out.println("[cozy_core_gametest] cushions: the broken cushion dropped " + dropped + "; the pet riding " + stillOn
							+ ", " + String.format("%.1f", fromHearth) + " blocks from the lantern, lying " + pet.isLying());
					helper.assertFalse(stillOn, "with its cushion broken, the cat should be off it");
					helper.assertTrue(dropped, "a broken cushion should drop as an item");
					helper.assertTrue(lyingByLantern, "with no free cushion, the cat should lie by the lantern (" + String.format("%.1f", fromHearth) + " blocks away, lying " + pet.isLying() + ")");
					helper.assertTrue(pet.isAlive(), "the cat should be fine");
					command(level, "time set 1000");
					helper.runAfterDelay(100, () -> RuleChecks.orTidy(tidy, () -> {
						boolean visitorOff = !visitor.isPassenger() || visitor.isRemoved();
						boolean petUp = !pet.isLying();
						boolean dogUp = !dog[0].isPassenger() && !dog[0].isInSittingPose();
						System.out.println("[cozy_core_gametest] cushions: in the morning the visitor is off its cushion " + visitorOff + ", the pet up " + petUp + ", the dog off and up " + dogUp);
						helper.assertTrue(visitorOff, "in the morning the visitor should get off its cushion");
						helper.assertTrue(petUp, "in the morning the pet should get up");
						helper.assertTrue(dogUp, "in the morning the dog should get off its cushion and up");
						// A cat found curled up on a cushion by day, as after loading a world, gets off.
						pet.startRiding(cushions[1].isAlive() ? cushions[1] : cushions[0], true, false);
						pet.setLying(true);
						boolean put = pet.isPassenger();
						helper.runAfterDelay(80, () -> RuleChecks.orTidy(tidy, () -> {
							boolean off = !pet.isPassenger() && !pet.isLying();
							System.out.println("[cozy_core_gametest] cushions: put on a cushion at 1000 " + put + ", off and up after 80 ticks " + off);
							tidy.run();
							helper.assertTrue(put, "the check should have put the cat on a cushion");
							helper.assertTrue(off, "a cat on a cushion by day should get off and up");
							helper.succeed();
						}));
					}));
				}));
			}));
		}));
	}

	/** How long the dog may still take after tick 300 to get on a cushion and sit. */
	private static final int DOG_WAIT = 300;

	/**
	 * Runs {@code then} once the pet dog sits on a cushion, checking every tick for up to
	 * {@code ticksLeft} ticks, and fails (after {@code tidy}) with where the dog is if it never does.
	 * A dog walks to its cushion in its own time, so a check at one fixed tick could catch it on its
	 * way; and when it never gets there, where it stands says why (issue 4: a dog started outside the
	 * test's area, in a chunk the game wasn't running, and never moved).
	 */
	private static void whenDogSits(GameTestHelper helper, Wolf[] dog, Cushion[] cushions, BlockPos hearth, int ticksLeft, Runnable tidy, Runnable then) {
		Wolf d = dog[0];
		boolean on = d.getVehicle() instanceof Cushion, sitting = d.isInSittingPose();
		if (on && sitting) {
			System.out.println("[cozy_core_gametest] cushions: the dog sits on a cushion " + (DOG_WAIT - ticksLeft) + " ticks after the cats were checked");
			then.run();
		} else if (ticksLeft <= 0) {
			double nearest = Double.MAX_VALUE;
			for (Cushion c : cushions) {
				if (c.isAlive() && c.getPassengers().isEmpty()) {
					nearest = Math.min(nearest, d.distanceTo(c));
				}
			}
			String where = String.format("at %.1f %.1f %.1f, %.1f blocks from the lantern, ", d.getX(), d.getY(), d.getZ(), from(d, hearth))
					+ (nearest == Double.MAX_VALUE ? "no free cushion left" : String.format("%.2f from the nearest free cushion", nearest)) + ", navigating " + !d.getNavigation().isDone() + ", goals " + goals(d);
			System.out.println("[cozy_core_gametest] cushions: the dog never sat on a cushion: on " + on + ", sitting " + sitting + ", " + where);
			RuleChecks.orTidy(tidy, () -> helper.assertTrue(false, "a pet dog should sit on a free cushion too (on " + on + ", sitting " + sitting + ", " + where + ")"));
		} else {
			helper.runAfterDelay(1, () -> whenDogSits(helper, dog, cushions, hearth, ticksLeft - 1, tidy, then));
		}
	}

	/** The goals running in a mob's mind, for when a check fails. */
	private static String goals(Mob mob) {
		try {
			java.lang.reflect.Field f = Mob.class.getDeclaredField("goalSelector");
			f.setAccessible(true);
			var selector = (net.minecraft.world.entity.ai.goal.GoalSelector) f.get(mob);
			return selector.getAvailableGoals().stream().filter(g -> g.isRunning())
					.map(g -> g.getPriority() + ":" + g.getGoal().getClass().getSimpleName()).toList().toString();
		} catch (ReflectiveOperationException e) {
			return "(couldn't read: " + e + ")";
		}
	}

	static void foxNaps(GameTestHelper helper) {
		visitorNaps(helper, EntityTypes.FOX);
	}

	static void rabbitNaps(GameTestHelper helper) {
		visitorNaps(helper, EntityTypes.RABBIT);
	}

	/**
	 * A visiting fox or rabbit at night walks to the free cushion by the lantern and gets on it (a fox
	 * sits; a rabbit has no resting pose). In the morning it gets off.
	 */
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel: "someone home" needs a player
	private static void visitorNaps(GameTestHelper helper, EntityType<? extends Mob> type) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos hearth = new BlockPos(at.getX(), Math.max(at.getY(), level.getSeaLevel()) + 1, at.getZ());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Entity> made = new ArrayList<>();
		String name = type.toShortString();
		Runnable tidy = () -> {
			level.getServer().getPlayerList().remove(player);
			made.forEach(Entity::discard);
			level.getEntitiesOfClass(Entity.class, new AABB(hearth).inflate(24, 4, 24), e -> e instanceof Cushion || e instanceof ItemEntity
					|| e.entityTags().contains(HomeLife.VISITOR_TAG)).forEach(Entity::discard);
			for (BlockPos p : BlockPos.betweenClosed(hearth.offset(-3, -1, -3), hearth.offset(17, 2, 3))) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			command(level, "time set noon");
		};
		Mob[] visitor = new Mob[1];
		Cushion[] cushion = new Cushion[1];
		RuleChecks.orTidy(tidy, () -> {
			for (BlockPos p : BlockPos.betweenClosed(hearth.offset(-3, -1, -3), hearth.offset(17, -1, 3))) {
				level.setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
			}
			level.setBlockAndUpdate(hearth, HearthLantern.BLOCK.get().defaultBlockState());
			// Someone home, in creative, so the visitor isn't shy of them.
			player.setGameMode(GameType.CREATIVE);
			player.teleportTo(level, hearth.getX() + 16.5, hearth.getY(), hearth.getZ() + 2.5, Set.of(), 0, 0, false);
			command(level, "time set 14000");
			cushion[0] = cushion(level, hearth.offset(2, 0, 0), DyeColor.MAGENTA);
			visitor[0] = HomeLife.visit(level, hearth.offset(12, 0, 0), type);
			helper.assertTrue(visitor[0] != null, "the check's " + name + " should arrive");
			made.addAll(List.of(cushion[0], visitor[0]));
		});
		helper.runAfterDelay(300, () -> RuleChecks.orTidy(tidy, () -> {
			Mob mob = visitor[0];
			boolean on = mob.getVehicle() == cushion[0];
			boolean sitting = !(mob instanceof Fox fox) || fox.isSitting();
			System.out.println("[cozy_core_gametest] cushions: at night the visiting " + name + " is on the cushion " + on + ", sitting " + sitting
					+ String.format(", %.1f blocks from the lantern, %.2f from the cushion", from(mob, hearth), mob.distanceTo(cushion[0])) + ", navigating " + !mob.getNavigation().isDone()
					+ ", goals " + goals(mob));
			helper.assertTrue(on && sitting, "a visiting " + name + " should rest on the free cushion (on " + on + ", sitting " + sitting + ")");
			command(level, "time set 1000");
			helper.runAfterDelay(100, () -> RuleChecks.orTidy(tidy, () -> {
				boolean off = !mob.isPassenger() || mob.isRemoved();
				System.out.println("[cozy_core_gametest] cushions: in the morning the " + name + " is off its cushion " + off);
				tidy.run();
				helper.assertTrue(off, "in the morning the " + name + " should get off its cushion");
				helper.succeed();
			}));
		}));
	}
}
