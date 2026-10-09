package sigf.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import com.mojang.math.Transformation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import sigf.kit.Sigf;
import sigf.mod.Models.Part;
import sigf.mod.Models.Spec;

/** Runs every ship, laser bolt and piece of wreckage of the mod. All of it is plain vanilla entities moved from the server. */
final class Fleet {
	static final String TAG = "esky";
	static final Random RNG = new Random();
	static final List<Ship> ships = new ArrayList<>();
	static final List<Bolt> bolts = new ArrayList<>();
	static final List<Debris> debris = new ArrayList<>();
	static final Map<UUID, Entity> live = new HashMap<>();
	static int kills;
	/** Demo only: the bot cannot press keys, so its shuttle is flown by script. */
	static Vec3 demoVel;
	/** Ship center relative to the (tiny, hidden) ghast that carries the player. */
	static final double RIDE_OFFSET = -0.6;
	/** The pilot sits near the tail so the chase camera (4 blocks behind) stays outside the hull. */
	static final double RIDE_Z = 2.5;

	private Fleet() {}

	static ServerLevel level() { return Sigf.level(); }
	static Vec3 center() { return sigf.kit.SigfKit.center(); }

	/** On the stream, outside the demo, the host is a spectator circling the stage: the show then plays low over the stage center. */
	static boolean idle() {
		ServerPlayer h = Sigf.host();
		return h != null && h.isSpectator() && !Sigf.isPlayer();
	}

	/** The spot the fleets fight over: the player, or the stage center when the host is only a camera. */
	static Vec3 focus(ServerPlayer p) { return p == null || p.isSpectator() ? center() : p.position(); }

	static void flash(Vec3 p) {
		level().sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), p.x, p.y, p.z, 1, 0, 0, 0, 0);
	}

	// ---------------------------------------------------------------- display boxes

	static Display.BlockDisplay box(Block b, Vec3 pos, Vector3f size, Quaternionf rot, boolean glow, float view) {
		Display.BlockDisplay d = EntityTypes.BLOCK_DISPLAY.create(level(), EntitySpawnReason.COMMAND);
		d.setBlockState(b.defaultBlockState());
		Vector3f t = new Vector3f(size).mul(-0.5f);
		rot.transform(t);
		d.setTransformation(new Transformation(t, rot, size, new Quaternionf()));
		d.setBrightnessOverride(glow ? new Brightness(15, 0) : new Brightness(13, 0));
		d.setPosRotInterpolationDuration(2);
		d.setViewRange(view);
		d.setShadowRadius(0f);
		d.snapTo(pos.x, pos.y, pos.z, 0f, 0f);
		d.addTag(TAG);
		level().addFreshEntity(d);
		live.put(d.getUUID(), d);
		return d;
	}

	static Quaternionf rotation(float ry, float rx, float rz) {
		return new Quaternionf().rotationYXZ((float) Math.toRadians(ry), (float) Math.toRadians(rx), (float) Math.toRadians(rz));
	}

	static void forget(Entity e) {
		if (e == null) return;
		live.remove(e.getUUID());
		e.discard();
	}

	/** Removes anything of ours that the mod no longer tracks (left over in a saved world). */
	static void sweep() {
		List<Entity> stray = new ArrayList<>();
		for (Entity e : level().getAllEntities()) {
			if (e.entityTags().contains(TAG) && !live.containsKey(e.getUUID())) stray.add(e);
		}
		for (Entity e : stray) e.discard();
		live.values().removeIf(Entity::isRemoved);
	}

	// ---------------------------------------------------------------- ships

	static final class Ship {
		final Spec spec;
		final int team;               // 0 = Alliance (player side), 1 = Korath raiders
		final boolean ride;           // the hit box is a happy ghast the player flies
		boolean path;                 // flies a fixed circle (the cruiser)
		final List<Display.BlockDisplay> parts = new ArrayList<>();
		final Block[] normal;
		LivingEntity hit;
		Mob pilot;
		Vec3 pos, vel = Vec3.ZERO;
		float yaw, pitch;
		float lastHp;
		int age, fireCd = 30, flash, gunIx, retarget;
		double orbitA = RNG.nextDouble() * 6.28, orbitDir = RNG.nextBoolean() ? 1 : -1;
		Vec3 aim;                     // where the target is, for firing
		Ship target;
		boolean targetPlayer = true;

		Ship(Spec spec, int team, boolean ride) {
			this.spec = spec; this.team = team; this.ride = ride;
			this.normal = new Block[spec.parts.size()];
		}

		boolean alive() { return hit != null && !hit.isRemoved() && hit.isAlive(); }

		Vec3 rot(double x, double y, double z) {
			double cy = Math.cos(Math.toRadians(yaw)), sy = Math.sin(Math.toRadians(yaw));
			double cp = Math.cos(Math.toRadians(pitch)), sp = Math.sin(Math.toRadians(pitch));
			double y1 = y * cp - z * sp, z1 = z * cp + y * sp;
			return new Vec3(x * cy - z1 * sy, y1, x * sy + z1 * cy);
		}

		Vec3 fwd() { return rot(0, 0, 1); }

		void place() {
			Vec3 shake = flash > 0 ? new Vec3((RNG.nextDouble() - 0.5) * 0.25, (RNG.nextDouble() - 0.5) * 0.2, (RNG.nextDouble() - 0.5) * 0.25) : Vec3.ZERO;
			for (int i = 0; i < parts.size(); i++) {
				Part p = spec.parts.get(i);
				Vec3 w = pos.add(rot(p.x(), p.y(), p.z())).add(shake);
				Display.BlockDisplay d = parts.get(i);
				d.setPos(w.x, w.y, w.z);
				d.setYRot(yaw);
				d.setXRot(pitch);
			}
			if (!ride) {
				hit.setPos(pos.x, pos.y - hit.getBbHeight() / 2, pos.z);
				hit.setDeltaMovement(vel);
			}
			if (pilot != null) {
				Vec3 w = pos.add(rot(spec.cockpit.x, spec.cockpit.y, spec.cockpit.z));
				pilot.setPos(w.x, w.y, w.z);
				pilot.setYRot(yaw);
				pilot.setYHeadRot(yaw);
				pilot.setYBodyRot(yaw);
				pilot.setDeltaMovement(Vec3.ZERO);
			}
		}

		void exhaust() {
			if (age % 2 != 0 && !ride) return;
			ParticleOptions p = team == 1 ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME;
			double speed = vel.length();
			Vec3 back = fwd().scale(-1);
			for (Vec3 e : spec.engines) {
				Vec3 w = pos.add(rot(e.x, e.y, e.z));
				int n = spec.scale > 2 ? 3 : 1;
				level().sendParticles(p, w.x, w.y, w.z, 0, back.x, back.y, back.z, 0.25 + speed * 0.3);
				if (n > 1) level().sendParticles(ParticleTypes.CLOUD, w.x, w.y, w.z, 0, back.x, back.y, back.z, 0.15);
			}
		}

		void tick() {
			age++;
			if (!alive()) { die(); return; }
			float hp = hit.getHealth();
			if (hp < lastHp - 0.01f) hurtFx();
			lastHp = hp;
			if (flash > 0 && --flash == 0) setFlash(false);
			if (ride) rideTick(); else if (path) pathTick(); else ai();
			place();
			exhaust();
		}

		void rideTick() {
			yaw = hit.getYRot();
			pitch = hit.getXRot();
			pos = hit.position().add(rot(0, RIDE_OFFSET, RIDE_Z));
			vel = hit.getDeltaMovement();
			if (hit.getPassengers().isEmpty()) hit.setDeltaMovement(hit.getDeltaMovement().scale(0.8));
			else if (demoVel != null) hit.setDeltaMovement(demoVel);
			if (age % 2 == 0 && !hit.getPassengers().isEmpty()) fireCd = Math.max(0, fireCd - 2);
		}

		void pathTick() {
			Vec3 c = Fleet.center();
			double r = idle() ? 38 : spec.orbit, h = idle() ? 11 : spec.height;
			orbitA += spec.speed / r;
			Vec3 np = new Vec3(c.x + Math.cos(orbitA) * r, c.y + h + Math.sin(age * 0.01) * 2, c.z + Math.sin(orbitA) * r);
			vel = np.subtract(pos);
			pos = np;
			yaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
			pitch = 0;
			fireControl();
		}

		void ai() {
			if (--retarget <= 0) { retarget = 40; pickTarget(); }
			Vec3 want;
			double a = (orbitA += 0.018 * orbitDir);
			Vec3 center = anchor();
			if (aim != null) {
				double r = spec.orbit * (team == 0 ? 0.8 : 1.0) * (idle() ? 0.65 : 1.0);
				want = aim.add(Math.cos(a) * r, spec.height * (idle() ? 0.6 : 1.0), Math.sin(a) * r);
			} else {
				want = center.add(Math.cos(a) * (idle() ? 9 : 14), (idle() ? 6 : 11) + Math.sin(age * 0.03) * 2, Math.sin(a) * (idle() ? 9 : 14));
			}
			Vec3 d = want.subtract(pos);
			double dist = d.length();
			Vec3 desired = dist < 0.01 ? Vec3.ZERO : d.scale(1 / dist).scale(Math.min(spec.speed, 0.12 + dist * 0.05));
			vel = vel.lerp(desired, 0.05);
			pos = pos.add(vel);
			int ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(pos.x), Mth.floor(pos.z));
			if (pos.y < ground + 5) { pos = new Vec3(pos.x, ground + 5, pos.z); vel = new Vec3(vel.x, Math.max(0, vel.y), vel.z); }
			double hs = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
			if (hs > 0.04) {
				float ty = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
				yaw += Mth.wrapDegrees(ty - yaw) * 0.12f;
				float tp = (float) -Math.toDegrees(Math.atan2(vel.y, hs));
				pitch += (Mth.clamp(tp * 1.5f, -25, 25) - pitch) * 0.1f;
			}
			fireControl();
		}

		Vec3 anchor() {
			ServerPlayer p = nearestPlayer(pos);
			return focus(nearestPlayer(pos));
		}

		void pickTarget() {
			target = null; aim = null;
			if (team == 1) {
				Ship ally = nearestShip(0, pos, 90, false);
				targetPlayer = ally == null || RNG.nextInt(100) < 70;
				ServerPlayer p = nearestPlayer(pos);
				if (targetPlayer && p != null) aim = focus(p).add(0, 1, 0);
				else if (ally != null) { target = ally; aim = ally.pos; }
				else aim = Fleet.center();
			} else {
				Ship foe = nearestShip(1, pos, 110, false);
				if (foe != null) { target = foe; aim = foe.pos; }
			}
		}

		void fireControl() {
			if (team == 1 && aim != null && targetPlayer && target == null) {
				ServerPlayer p = nearestPlayer(pos);
				if (p != null) aim = focus(p).add(0, 1, 0);
			}
			if (target != null) { if (!target.alive()) { target = null; aim = null; retarget = 0; } else aim = target.pos; }
			if (--fireCd > 0 || aim == null) return;
			double dist = aim.distanceTo(pos);
			if (dist > 55 || dist < 4) { fireCd = 10; return; }
			fireCd = spec.fireEvery + RNG.nextInt(Math.max(1, spec.fireEvery / 2));
			if (spec.guns.isEmpty()) return;
			Vec3 g = spec.guns.get(gunIx++ % spec.guns.size());
			Vec3 from = pos.add(rot(g.x, g.y, g.z));
			Vec3 lead = aim.add((RNG.nextDouble() - 0.5) * 2.4, (RNG.nextDouble() - 0.5) * 1.6, (RNG.nextDouble() - 0.5) * 2.4);
			Vec3 dir = lead.subtract(from).normalize();
			new Bolt(from, dir.scale(1.9), team, (float) spec.boltDamage, null, spec.scale > 2 ? 2f : 1f);
			SoundsMod.at(SoundsMod.CANNON, from, 1.4f, 0.9f + RNG.nextFloat() * 0.3f);
		}

		void hurtFx() {
			flash = 3;
			setFlash(true);
			level().sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 14, 1.2, 0.6, 1.2, 0.3);
			level().sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 8, 1.0, 0.5, 1.0, 0.4);
			SoundsMod.at(team == 0 && path ? SoundsMod.SHIELD_HIT : SoundsMod.HULL_HIT, pos, 1.2f, 0.9f + RNG.nextFloat() * 0.3f);
		}

		void setFlash(boolean on) {
			for (int i = 0; i < parts.size(); i++) {
				Part p = spec.parts.get(i);
				if (p.glow() || p.block() == Blocks.STAINED_GLASS.lightBlue() || p.block() == Blocks.STAINED_GLASS.red()) continue;
				parts.get(i).setBlockState((on ? Blocks.CONCRETE.white() : p.block()).defaultBlockState());
			}
		}

		void die() {
			ships.remove(this);
			level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
			level().sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 50, 1.6, 0.8, 1.6, 0.25);
			level().sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 18, 1.2, 0.6, 1.2, 0.0);
			level().sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 30, 1.8, 0.8, 1.8, 0.1);
			level().sendParticles(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, 40, 0.5, 0.5, 0.5, 0.5);
			SoundsMod.at(SoundsMod.BOOM, pos, 3.0f, spec.scale > 2 ? 0.6f : 1.0f);
			Sigf.sound(SoundEvents.GENERIC_EXPLODE.value(), pos, 2.0f, 1.2f);
			if (ride) { forgetParts(); return; }
			for (int i = 0; i < parts.size(); i++) {
				Part p = spec.parts.get(i);
				Vec3 off = new Vec3(p.x(), p.y(), p.z());
				Vec3 v = off.lengthSqr() < 0.01 ? new Vec3(0, 0.3, 0) : off.normalize().scale(0.22 + RNG.nextDouble() * 0.2);
				v = v.add(vel.scale(0.5)).add(0, 0.12 + RNG.nextDouble() * 0.15, 0);
				debris.add(new Debris(parts.get(i), v, i == 0, (RNG.nextFloat() - 0.5f) * 24f, spec.scale > 2));
			}
			parts.clear();
			if (team == 1) {
				kills++;
				int n = spec.scale > 2 ? 14 : 2 + RNG.nextInt(3);
				for (int i = 0; i < n; i++) {
					ItemEntity it = new ItemEntity(level(), pos.x, pos.y, pos.z, new ItemStack(SigfMod.CREDIT));
					it.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.5, 0.3 + RNG.nextDouble() * 0.3, (RNG.nextDouble() - 0.5) * 0.5);
					level().addFreshEntity(it);
				}
			}
			if (pilot != null) eject();
			forget(hit);
		}

		void eject() {
			live.remove(pilot.getUUID());
			pilot.removeTag(TAG);
			pilot.setNoAi(false);
			pilot.setPermanentlyInvulnerable(false);
			pilot.setSilent(false);
			pilot.setCustomNameVisible(true);
			pilot.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0));
			pilot.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.6, 0.7, (RNG.nextDouble() - 0.5) * 0.6);
			pilot.setGlowingTag(true);
			if (team == 1) Sigf.say("A Korath pilot ejected! Watch out for the crossbow.");
			pilot = null;
		}

		void forgetParts() {
			for (Display.BlockDisplay d : parts) forget(d);
			parts.clear();
			if (pilot != null) { forget(pilot); pilot = null; }
		}

		void remove() {
			ships.remove(this);
			forgetParts();
			forget(hit);
		}
	}

	// ---------------------------------------------------------------- spawning

	static Ship build(Spec spec, int team, Vec3 pos, boolean ride, Mob existingHit) {
		Ship s = new Ship(spec, team, ride);
		s.pos = pos;
		for (int i = 0; i < spec.parts.size(); i++) {
			Part p = spec.parts.get(i);
			s.normal[i] = p.block();
			s.parts.add(box(p.block(), pos, new Vector3f(p.sx(), p.sy(), p.sz()), rotation(p.ry(), p.rx(), p.rz()), p.glow(), 8f));
		}
		if (existingHit != null) {
			s.hit = existingHit;
		} else {
			Mob m = EntityTypes.PHANTOM.create(level(), EntitySpawnReason.COMMAND);
			m.getAttribute(Attributes.SCALE).setBaseValue(spec.hitbox);
			m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(spec.hp);
			m.setHealth((float) spec.hp);
			m.setNoAi(true);
			m.setNoGravity(true);
			m.setInvisible(true);
			m.setSilent(true);
			m.setPersistenceRequired();
			m.setPermanentlyInvulnerable(spec.name.startsWith("Falcon"));
			m.snapTo(pos.x, pos.y - m.getBbHeight() / 2, pos.z, 0f, 0f);
			m.addTag(TAG);
			level().addFreshEntity(m);
			live.put(m.getUUID(), m);
			s.hit = m;
		}
		s.lastHp = s.hit.getHealth();
		ships.add(s);
		s.place();
		return s;
	}

	static Mob pilot(EntityType<? extends Mob> type, Ship s, String name) {
		Mob m = type.create(level(), EntitySpawnReason.COMMAND);
		m.getAttribute(Attributes.SCALE).setBaseValue(s.spec.pilotScale);
		m.setNoAi(true);
		m.setPermanentlyInvulnerable(true);
		m.setSilent(true);
		m.setPersistenceRequired();
		if (name != null) { m.setCustomName(Component.literal(name)); m.setCustomNameVisible(true); }
		m.addTag(TAG);
		Vec3 w = s.pos.add(s.rot(s.spec.cockpit.x, s.spec.cockpit.y, s.spec.cockpit.z));
		m.snapTo(w.x, w.y, w.z, s.yaw, 0f);
		level().addFreshEntity(m);
		live.put(m.getUUID(), m);
		s.pilot = m;
		return m;
	}

	static final String[] PILOT_NAMES = {"Captain Cluck", "Sgt. Oinkers", "Ensign Moo", "Admiral Feathers", "Lt. Trufflesnout", "Commander Cluckington"};

	/** Hyperspace arrival: a flash and a streak of light, then the ship is simply there, already flying inward. */
	static void jumpFx(Vec3 pos, Vec3 from) {
		flash(pos);
		level().sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 60, 2.5, 1.5, 2.5, 0.25);
		Vec3 d = from.subtract(pos).normalize();
		for (int i = 1; i <= 14; i++) {
			Vec3 q = pos.add(d.scale(i * 1.8));
			level().sendParticles(ParticleTypes.END_ROD, q.x, q.y, q.z, 0, d.x, d.y, d.z, 0.4);
		}
		SoundsMod.at(SoundsMod.JUMP, pos, 2.5f, 1.2f);
	}

	static Ship jumpInRaider(Spec spec, Vec3 pos) {
		Ship s = build(spec, 1, pos, false, null);
		pilot(EntityTypes.PILLAGER, s, null);
		s.yaw = (float) Math.toDegrees(Math.atan2(-(Fleet.center().x - pos.x), Fleet.center().z - pos.z));
		Vec3 inward = Fleet.center().subtract(pos).normalize().scale(0.6);
		s.vel = new Vec3(inward.x, 0, inward.z);
		jumpFx(pos, Fleet.center().add(0, pos.y - Fleet.center().y, 0));
		return s;
	}

	static Ship jumpInWingman(Vec3 pos) {
		Ship s = build(Models.wingman(), 0, pos, false, null);
		EntityType<? extends Mob> t = switch (RNG.nextInt(3)) { case 0 -> EntityTypes.CHICKEN; case 1 -> EntityTypes.PIG; default -> EntityTypes.COW; };
		pilot(t, s, PILOT_NAMES[RNG.nextInt(PILOT_NAMES.length)]);
		jumpFx(pos, Fleet.center());
		return s;
	}

	static Ship spawnCruiser(Vec3 pos) {
		Ship s = build(Models.falcon(), 0, pos, false, null);
		s.path = true;
		s.orbitA = Math.atan2(pos.z - Fleet.center().z, pos.x - Fleet.center().x);
		return s;
	}

	/** The pilot's shuttle: a happy ghast with a harness (so it can be ridden) hidden inside the ship model. */
	static Ship spawnPlayerShip(Vec3 pos) {
		var g = EntityTypes.HAPPY_GHAST.create(level(), EntitySpawnReason.COMMAND);
		g.setItemSlot(EquipmentSlot.BODY, new ItemStack(Items.HARNESS.white()));
		g.setInvisible(true);
		g.getAttribute(Attributes.SCALE).setBaseValue(0.0625);
		g.setPermanentlyInvulnerable(true);
		g.setPersistenceRequired();
		g.setCustomName(Component.literal("Your Shuttle"));
		g.snapTo(pos.x, pos.y, pos.z, Sigf.host() == null ? 0f : Sigf.host().getYRot() + 180f, 0f);
		g.addTag(TAG);
		level().addFreshEntity(g);
		live.put(g.getUUID(), g);
		Ship s = build(Models.playerShuttle(), 0, pos.add(0, RIDE_OFFSET, 0), true, g);
		s.yaw = g.getYRot();
		return s;
	}

	// ---------------------------------------------------------------- queries

	static ServerPlayer nearestPlayer(Vec3 from) {
		ServerPlayer best = null; double bd = 1e18;
		for (ServerPlayer p : Sigf.players()) {
			if (!p.isAlive()) continue;
			double d = p.position().distanceToSqr(from);
			if (d < bd) { bd = d; best = p; }
		}
		return best;
	}

	static Ship nearestShip(int team, Vec3 from, double range, boolean includeCruiser) {
		Ship best = null; double bd = range * range;
		for (Ship s : ships) {
			if (s.team != team || !s.alive() || (s.path && !includeCruiser)) continue;
			double d = s.pos.distanceToSqr(from);
			if (d < bd) { bd = d; best = s; }
		}
		return best;
	}

	static int count(int team, boolean boss) {
		int n = 0;
		for (Ship s : ships) if (s.team == team && !s.path && !s.ride && (s.spec.scale > 2) == boss) n++;
		return n;
	}

	static boolean isPlayerShip(Entity e) {
		for (Ship s : ships) if (s.ride && s.hit == e) return true;
		return false;
	}

	// ---------------------------------------------------------------- bolts

	/** A laser bolt: a glowing rod that flies straight and hurts what it touches. */
	static final class Bolt {
		Vec3 pos, vel;
		final int team;
		final float damage;
		final ServerPlayer owner;
		final Display.BlockDisplay d;
		int life = 70;

		Bolt(Vec3 pos, Vec3 vel, int team, float damage, ServerPlayer owner, float thick) {
			this.pos = pos; this.vel = vel; this.team = team; this.damage = damage; this.owner = owner;
			Block b = owner != null ? Blocks.CONCRETE.lightBlue() : team == 1 ? Blocks.CONCRETE.red() : Blocks.CONCRETE.cyan();
			if (owner == null && team == 0) b = Blocks.CONCRETE.lime();
			d = box(b, pos, new Vector3f(0.22f * thick, 0.22f * thick, 2.2f * thick), new Quaternionf(), true, 6f);
			d.setPosRotInterpolationDuration(1);
			orient();
			bolts.add(this);
		}

		void orient() {
			double hs = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
			d.setYRot((float) Math.toDegrees(Math.atan2(-vel.x, vel.z)));
			d.setXRot((float) -Math.toDegrees(Math.atan2(vel.y, hs)));
		}

		/** Returns false when the bolt is spent. */
		boolean tick() {
			if (--life <= 0) return false;
			int steps = (int) Math.ceil(vel.length() / 0.8);
			Vec3 step = vel.scale(1.0 / steps);
			for (int i = 0; i < steps; i++) {
				pos = pos.add(step);
				BlockPos bp = BlockPos.containing(pos);
				if (!level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty()) { impact(false); return false; }
				if (touch()) return false;
			}
			d.setPos(pos.x, pos.y, pos.z);
			orient();
			ParticleOptions trail = owner != null ? new DustParticleOptions(0x66E8FF, 1.2f) : team == 1 ? new DustParticleOptions(0xFF3322, 1.2f) : new DustParticleOptions(0x77FF55, 1.2f);
			level().sendParticles(trail, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0);
			return true;
		}

		boolean touch() {
			AABB pt = AABB.ofSize(pos, 0.5, 0.5, 0.5);
			for (Ship s : ships) {
				if (s.team == team || !s.alive()) continue;
				if (s.hit.getBoundingBox().inflate(0.3).intersects(pt)) {
					s.hit.hurtServer(level(), owner != null ? level().damageSources().playerAttack(owner) : level().damageSources().magic(), damage);
					s.vel = s.vel.add(vel.normalize().scale(0.08));
					impact(true);
					return true;
				}
			}
			for (Entity e : level().getEntities((Entity) null, pt.inflate(0.4), x -> x instanceof LivingEntity && x.isAlive() && !x.entityTags().contains(TAG))) {
				LivingEntity le = (LivingEntity) e;
				if (le instanceof ServerPlayer sp) {
					if (team == 0 || sp.isSpectator()) continue;
				} else if ((team == 0) != (le instanceof Enemy)) {
					continue;
				}
				if (isPlayerShip(le)) continue;
				le.hurtServer(level(), owner != null ? level().damageSources().playerAttack(owner) : level().damageSources().magic(), damage);
				impact(true);
				return true;
			}
			return false;
		}

		void impact(boolean entity) {
			ParticleOptions c = owner != null ? new DustParticleOptions(0x66E8FF, 1.0f) : team == 1 ? new DustParticleOptions(0xFF3322, 1.0f) : new DustParticleOptions(0x77FF55, 1.0f);
			level().sendParticles(c, pos.x, pos.y, pos.z, 12, 0.3, 0.3, 0.3, 0.05);
			level().sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 8, 0.25, 0.25, 0.25, 0.25);
			flash(pos);
			SoundsMod.at(entity ? SoundsMod.SHIELD_HIT : SoundsMod.HULL_HIT, pos, 0.9f, 1.1f + RNG.nextFloat() * 0.3f);
		}
	}

	// ---------------------------------------------------------------- wreckage

	static final class Debris {
		final Display.BlockDisplay d;
		Vec3 pos, vel;
		final boolean main, big;
		final float spin;
		int life = 70;
		float yaw;

		Debris(Display.BlockDisplay d, Vec3 vel, boolean main, float spin, boolean big) {
			this.d = d; this.vel = vel; this.main = main; this.spin = spin; this.big = big;
			this.pos = d.position();
			this.yaw = d.getYRot();
		}

		boolean tick() {
			vel = vel.add(0, -0.045, 0).scale(0.985);
			pos = pos.add(vel);
			yaw += spin;
			d.setPos(pos.x, pos.y, pos.z);
			d.setYRot(yaw);
			d.setXRot(d.getXRot() + spin * 0.5f);
			if (life % 2 == 0) level().sendParticles(main || big ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 1, 0.1, 0.1, 0.1, 0.01);
			if (life % 5 == 0) level().sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 1, 0.15, 0.15, 0.15, 0.02);
			BlockPos bp = BlockPos.containing(pos);
			boolean ground = !level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty();
			if (ground || --life <= 0) {
				if (main || ground && big) {
					level().explode(null, pos.x, pos.y + 0.5, pos.z, big ? 3.0f : 1.7f, false, Level.ExplosionInteraction.TNT);
				} else {
					level().sendParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 4, 0.2, 0.2, 0.2, 0.03);
				}
				return false;
			}
			return true;
		}
	}

	// ---------------------------------------------------------------- main loop

	static void tick() {
		for (Ship s : new ArrayList<>(ships)) s.tick();
		for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
			Bolt b = it.next();
			if (!b.tick()) { forget(b.d); it.remove(); }
		}
		for (Iterator<Debris> it = debris.iterator(); it.hasNext(); ) {
			Debris x = it.next();
			if (!x.tick()) { forget(x.d); it.remove(); }
		}
	}

	static void clear() {
		for (Ship s : new ArrayList<>(ships)) s.remove();
		for (Bolt b : bolts) forget(b.d);
		for (Debris x : debris) forget(x.d);
		bolts.clear(); debris.clear();
	}
}
