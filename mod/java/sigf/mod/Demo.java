package sigf.mod;

import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import sigf.kit.Sigf;
import sigf.mod.Fleet.Ship;

/** The recorded demo. The bot has no keyboard: it aims by script and flies its shuttle by script, everything else is the real mod. */
final class Demo {
	enum Mode { LOOK, SHOOT, NONE }

	static Mode mode = Mode.NONE;
	static Vec3 lookPoint;
	static Vec3 flyGoal;
	static boolean orbitBoss;
	static long t;

	private Demo() {}

	static Vec3 c() { return Fleet.center(); }
	static Vec3 gasGiant() { return c().add(-52, 40, -44); }
	static Vec3 oceanWorld() { return c().add(62, 30, 30); }

	static void look(Vec3 p) { mode = Mode.LOOK; lookPoint = p; }

	static Vec3 ahead(double dist, double up, double side) {
		ServerPlayer p = Sigf.host();
		Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize();
		Vec3 r = new Vec3(-f.z, 0, f.x);
		return p.position().add(f.scale(dist)).add(r.scale(side)).add(0, up, 0);
	}

	static void schedule() {
		Sigf.every(0.05, Demo::tick);
		Sigf.demo(0.2, () -> {
			Sigf.title("ENDLESS SKY TAKEOVER", "Minecraft x Endless Sky", 3.5);
			look(gasGiant());
		});
		Sigf.demo(2.6, () -> look(ahead(30, 14, 0)));
		Sigf.demo(3.4, () -> {
			Sigf.title("KORATH RAIDERS", "Hyperspace jump detected!", 2.5);
			Fleet.jumpInRaider(Models.raider(), ahead(36, 13, -9));
			Fleet.jumpInRaider(Models.raider(), ahead(42, 16, 10));
		});
		Sigf.demo(5.0, () -> mode = Mode.SHOOT);
		Sigf.demo(14.5, () -> {
			for (int i = 0; i < 3; i++) Sigf.spawn(EntityTypes.ZOMBIE, ahead(9 + i * 2, 1, -4 + i * 4));
		});
		Sigf.demo(19.5, () -> { mode = Mode.LOOK; lookPoint = ahead(20, 14, 0); SigfMod.hireEscort(Sigf.host()); });
		Sigf.demo(22.5, () -> {
			Sigf.title("BOARDING YOUR SHUTTLE", "Fly it, shoot from it", 3);
			for (Ship s : Fleet.ships) if (s.ride) Sigf.host().startRiding(s.hit, true, false);
			flyGoal = Sigf.host().position().add(0, 14, 0);
			mode = Mode.SHOOT;
		});
		Sigf.demo(26.5, () -> {
			Sigf.title("KORATH WARLORD", "A flagship jumped in!", 3);
			Fleet.jumpInRaider(Models.warlord(), ahead(55, 22, 12));
			orbitBoss = true;
		});
		Sigf.demo(62, () -> { orbitBoss = false; flyGoal = gasGiant().add(14, 4, 26); look(gasGiant()); });
		Sigf.demo(66, () -> Sigf.title("SECTOR CLEARED", "Spend credits on escorts, go again", 4));
	}

	static void tick() {
		if (!Sigf.isDemo() || !SigfMod.ready) return;
		ServerPlayer p = Sigf.host();
		if (p == null) return;
		t++;
		Vec3 eye = p.getEyePosition();
		Vec3 target = null;
		Ship foe = null;
		if (mode == Mode.LOOK) {
			target = lookPoint;
		} else if (mode == Mode.SHOOT) {
			foe = pickFoe(p);
			if (foe != null) {
				double d = foe.pos.distanceTo(eye);
				target = foe.pos.add(foe.vel.scale(d / 3.2));
			} else {
				Entity m = nearestMob(p);
				if (m != null) target = m.position().add(0, m.getBbHeight() * 0.5, 0);
			}
		}
		if (target != null) {
			Vec3 d = target.subtract(eye);
			float ty = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			float tp = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
			float dy = Mth.wrapDegrees(ty - p.getYRot()), dp = tp - p.getXRot();
			float sy = Mth.clamp(dy * 0.3f, -14f, 14f), sp = Mth.clamp(dp * 0.3f, -10f, 10f);
			p.setYRot(p.getYRot() + sy);
			p.setXRot(Mth.clamp(p.getXRot() + sp, -85f, 85f));
			p.connection.send(new ClientboundPlayerRotationPacket(p.getYRot(), false, p.getXRot(), false));
			if (mode == Mode.SHOOT && Math.abs(dy) < 5 && Math.abs(dp) < 5 && t % 6 == 0) SigfMod.fire(p);
		}
		// flying: the shuttle follows a goal point
		Ship mine = null;
		for (Ship s : Fleet.ships) if (s.ride) mine = s;
		if (mine != null && p.getVehicle() == mine.hit && (flyGoal != null || orbitBoss)) {
			Vec3 goal = flyGoal;
			if (orbitBoss) {
				Ship boss = null;
				for (Ship s : Fleet.ships) if (s.team == 1 && s.spec.scale > 2) boss = s;
				if (boss != null) {
					double a = t * 0.018;
					goal = boss.pos.add(Math.cos(a) * 34, 5, Math.sin(a) * 34);
				}
			}
			if (goal != null) {
				Vec3 d = goal.subtract(mine.pos);
				double len = d.length();
				Fleet.demoVel = len < 0.5 ? Vec3.ZERO : d.scale(1 / len).scale(Math.min(0.45, len * 0.05));
			}
		} else {
			Fleet.demoVel = null;
		}
	}

	static Ship pickFoe(ServerPlayer p) {
		Ship best = null;
		double bd = 1e18;
		for (Ship s : Fleet.ships) {
			if (s.team != 1 || !s.alive()) continue;
			double d = s.pos.distanceToSqr(p.position());
			if (s.spec.scale > 2) d *= 0.5;
			if (d < bd) { bd = d; best = s; }
		}
		return best;
	}

	static Entity nearestMob(ServerPlayer p) {
		Entity best = null;
		double bd = 30 * 30;
		for (Entity e : Sigf.near(p.position(), 30, x -> x instanceof Enemy && x instanceof LivingEntity && !x.entityTags().contains(Fleet.TAG))) {
			double d = e.position().distanceToSqr(p.position());
			if (d < bd) { bd = d; best = e; }
		}
		return best;
	}
}
