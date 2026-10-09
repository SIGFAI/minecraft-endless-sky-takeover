package sigf.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;

/** Endless Sky Takeover: the night sky becomes a space lane, with a pilotable shuttle and Korath raiders. */
public final class SigfMod implements ModInitializer {
	public static Item BLASTER, CREDIT;
	static boolean ready;
	static int wave, nextWaveIn = 6, wingmanIn = 0, rank = 0;
	/** The combat ratings of the Endless Sky, by kills. */
	static final String[] RATINGS = {"Harmless", "Mostly Harmless", "Novice", "Competent", "Dangerous", "Deadly", "Legendary"};
	static final int[] RATING_AT = {0, 1, 3, 6, 10, 16, 25};

	@Override
	public void onInitialize() {
		SoundsMod.register();
		BLASTER = Sigf.item("ion_blaster", p -> new Item(p.stacksTo(1)));
		CREDIT = Sigf.item("credit_chip", p -> new Item(p.stacksTo(64)));

		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack st = player.getItemInHand(hand);
			if (player instanceof ServerPlayer sp && st.is(BLASTER)) {
				if (!sp.getCooldowns().isOnCooldown(st)) { fire(sp); sp.getCooldowns().addCooldown(st, 6); }
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer sp && st.is(CREDIT)) {
				if (!sp.getCooldowns().isOnCooldown(st) && st.getCount() >= 3) {
					if (hireEscort(sp)) st.shrink(3);
					sp.getCooldowns().addCooldown(st, 40);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> { if (ready) { Fleet.tick(); magnet(); } });
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			ready = false;
			wave = 0; rank = 0; Fleet.kills = 0; nextWaveIn = 6;
			Fleet.ships.clear(); Fleet.bolts.clear(); Fleet.debris.clear(); Fleet.live.clear();
		});

		Sigf.after(1.0, SigfMod::setup);
		Demo.schedule();
		Sigf.every(1, SigfMod::director);
		Sigf.every(0.5, SigfMod::hud);
		Sigf.every(5, () -> { if (ready) Fleet.sweep(); });
	}

	/** Spends nothing itself: the caller removes the 3 chips when this returns true. */
	static boolean hireEscort(ServerPlayer sp) {
		if (Fleet.count(0, false) >= 6) {
			sp.sendSystemMessage(Component.literal("The fleet is full."), true);
			return false;
		}
		Fleet.jumpInWingman(sp.position().add(sp.getLookAngle().multiply(1, 0, 1).normalize().scale(16)).add(0, 11, 0));
		Sigf.title("ESCORT HIRED", "An escort shuttle jumped in", 2.5);
		return true;
	}

	static void setup() {
		Fleet.sweep();
		Fleet.live.clear();
		Sigf.command("time set 12800");
		Vec3 c = Fleet.center();
		Space.gasGiant(c.add(-52, 40, -44), 6, 3.4f);
		Space.oceanWorld(c.add(62, 30, 30), 3, 3f);
		Space.moon(c.add(26, 50, -62), 2, 2.4f);
		Fleet.spawnCruiser(c.add(42, 24, 0));
		Fleet.jumpInWingman(c.add(-20, 12, 30));
		Fleet.jumpInWingman(c.add(25, 14, -25));
		ServerPlayer p = Sigf.host();
		Vec3 fwd = p.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 at = Fleet.idle() ? p.position() : p.position().add(fwd.scale(7));
		int gy = Sigf.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) at.x, (int) at.z);
		Fleet.spawnPlayerShip(new Vec3(at.x, gy + 2.6, at.z));
		Sigf.command("give @a sigf:ion_blaster");
		Sigf.command("give @a sigf:credit_chip 3");
		ready = true;
		if (Sigf.isPlayer()) {
			Sigf.after(2, () -> Sigf.title("ENDLESS SKY TAKEOVER", "Right-click your shuttle to fly it. Shoot with the Ion Blaster.", 5));
			Sigf.after(6, () -> Sigf.say("Dispatch: Korath raiders are jumping into this sector. Hold the line, pilot!"));
			Sigf.after(10, () -> Sigf.say("Tip: credit chips drop from wrecks. Right-click 3 chips to hire an escort shuttle."));
			Sigf.after(14, () -> Sigf.say("Tip: every third wave brings a Korath Warlord. Fly your shuttle to chase it down."));
		}
		Sigf.log("setup done");
	}

	/** Keeps the sky busy: two escort shuttles on our side, and a new Korath wave when the last one is gone. */
	static void director() {
		if (!ready) return;
		if (Fleet.count(0, false) < 2 && --wingmanIn <= 0) {
			wingmanIn = 12;
			Fleet.jumpInWingman(spot(40, 14));
		}
		if (Fleet.count(1, false) + Fleet.count(1, true) == 0) {
			if (--nextWaveIn <= 0) spawnWave();
		} else {
			nextWaveIn = 10;
		}
	}

	static Vec3 spot(double dist, double up) {
		double a = Fleet.RNG.nextDouble() * Math.PI * 2;
		Vec3 c = Fleet.center();
		return new Vec3(c.x + Math.cos(a) * dist, c.y + up + Fleet.RNG.nextDouble() * 6, c.z + Math.sin(a) * dist);
	}

	static void spawnWave() {
		wave++;
		int n = Math.min(1 + wave / 2, 3);
		for (int i = 0; i < n; i++) Fleet.jumpInRaider(Models.raider(), spot(48, 14));
		if (wave % 3 == 0) {
			Fleet.jumpInRaider(Models.warlord(), spot(60, 18));
			Sigf.title("KORATH WARLORD", "Wave " + wave + ": a flagship jumped in!", 3);
		} else {
			Sigf.title("WAVE " + wave, "Korath raiders jumped in!", 2.5);
		}
		nextWaveIn = 12;
	}

	/** Credit chips are pulled to a nearby pilot by a tractor beam, like loot in the Endless Sky. */
	static void magnet() {
		for (var e : Fleet.level().getAllEntities()) {
			if (!(e instanceof net.minecraft.world.entity.item.ItemEntity it) || !it.getItem().is(CREDIT)) continue;
			ServerPlayer p = Fleet.nearestPlayer(it.position());
			if (p == null || p.isSpectator()) continue;
			double d = p.position().add(0, 1, 0).distanceTo(it.position());
			if (d > 26 || d < 0.6) continue;
			Vec3 pull = p.position().add(0, 1, 0).subtract(it.position()).normalize().scale(Math.min(1.0, 0.25 + (26 - d) * 0.04));
			it.setDeltaMovement(it.getDeltaMovement().scale(0.5).add(pull.scale(0.5)));
			it.setNoGravity(true);
			Fleet.level().sendParticles(ParticleTypes.END_ROD, it.getX(), it.getY(), it.getZ(), 1, 0.05, 0.05, 0.05, 0);
		}
	}

	static void hud() {
		if (!ready) return;
		while (rank + 1 < RATINGS.length && Fleet.kills >= RATING_AT[rank + 1]) {
			rank++;
			Sigf.say("Combat rating raised: " + RATINGS[rank] + "!");
		}
		for (ServerPlayer p : Sigf.players()) {
			int credits = 0;
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				ItemStack s = p.getInventory().getItem(i);
				if (s.is(CREDIT)) credits += s.getCount();
			}
			int raiders = Fleet.count(1, false) + Fleet.count(1, true);
			p.sendSystemMessage(Component.literal("Credits: " + credits + "   |   Wave " + wave + "   |   Raiders: " + raiders + "   |   Combat rating: " + RATINGS[rank]), true);
		}
	}

	static void fire(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 from = p.getEyePosition().add(look.scale(2.2)).add(right.scale(0.55)).add(0, -0.4, 0);
		new Fleet.Bolt(from, look.scale(3.2), 0, 6f, p, 1f);
		Fleet.flash(from);
		SoundsMod.at(SoundsMod.LASER, from, 1.0f, 1.0f + Fleet.RNG.nextFloat() * 0.2f);
	}
}
